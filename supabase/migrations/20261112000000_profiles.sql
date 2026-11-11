-- ============================================================================
-- Perfiles públicos de usuarios + relación con reports
-- ============================================================================
-- Ejecutar en el SQL Editor de Supabase (o `supabase db push`).
--
-- Objetivo:
--   1. Crear `public.profiles` con los datos públicos del usuario.
--   2. Rellenar los perfiles existentes desde `auth.users`.
--   3. Mantener el perfil sincronizado para los usuarios nuevos.
--   4. Permitir lectura pública de perfiles (anon y authenticated).
--   5. Crear la FK `reports.user_id -> profiles.id` que necesita PostgREST
--      para resolver `select("*, profiles(full_name, avatar_url)")`.
--   6. Exponer `user_name` y `user_avatar_url` en `reports_view` como
--      respaldo para instalaciones donde la FK aún no esté disponible.
-- ============================================================================

-- 1) Tabla de perfiles -------------------------------------------------------
create table if not exists public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    full_name text,
    avatar_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

comment on table public.profiles is
    'Datos públicos de cada usuario. Se leen desde la app para mostrar al reportante.';

-- 2) Rellenar perfiles existentes -------------------------------------------
insert into public.profiles as p (id, full_name, avatar_url)
select
    au.id,
    coalesce(
        nullif(trim(au.raw_user_meta_data ->> 'full_name'), ''),
        nullif(trim(au.raw_user_meta_data ->> 'name'), ''),
        nullif(trim(split_part(coalesce(au.email, ''), '@', 1)), '')
    ),
    nullif(trim(au.raw_user_meta_data ->> 'avatar_url'), '')
from auth.users au
on conflict (id) do update
set full_name = coalesce(p.full_name, excluded.full_name),
    avatar_url = coalesce(p.avatar_url, excluded.avatar_url),
    updated_at = now();

-- Si la tabla pública `users` ya almacena el nombre, úsalo como respaldo.
do $$
begin
    if exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'users'
          and column_name = 'full_name'
    ) then
        execute $sql$
            update public.profiles p
            set full_name = coalesce(p.full_name, nullif(trim(u.full_name), ''))
            from public.users u
            where u.id = p.id
              and p.full_name is null
        $sql$;
    end if;
end;
$$;

-- 3) Sincronización de usuarios nuevos --------------------------------------
create or replace function public.handle_new_user_profile()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles as p (id, full_name, avatar_url)
    values (
        new.id,
        coalesce(
            nullif(trim(new.raw_user_meta_data ->> 'full_name'), ''),
            nullif(trim(new.raw_user_meta_data ->> 'name'), ''),
            nullif(trim(split_part(coalesce(new.email, ''), '@', 1)), '')
        ),
        nullif(trim(new.raw_user_meta_data ->> 'avatar_url'), '')
    )
    on conflict (id) do update
    set full_name = coalesce(p.full_name, excluded.full_name),
        avatar_url = coalesce(p.avatar_url, excluded.avatar_url),
        updated_at = now();

    return new;
end;
$$;

revoke execute on function public.handle_new_user_profile() from public, anon, authenticated;

drop trigger if exists on_auth_user_created_profile on auth.users;
create trigger on_auth_user_created_profile
    after insert on auth.users
    for each row
    execute function public.handle_new_user_profile();

-- 4) RLS: lectura pública y edición del propio perfil ------------------------
alter table public.profiles enable row level security;

drop policy if exists "profiles_public_read" on public.profiles;
create policy "profiles_public_read"
    on public.profiles
    for select
    to anon, authenticated
    using (true);

drop policy if exists "profiles_insert_own" on public.profiles;
create policy "profiles_insert_own"
    on public.profiles
    for insert
    to authenticated
    with check (auth.uid() = id);

drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own"
    on public.profiles
    for update
    to authenticated
    using (auth.uid() = id)
    with check (auth.uid() = id);

grant select on public.profiles to anon, authenticated;
grant insert, update on public.profiles to authenticated;

-- 5) Relación reports.user_id -> profiles.id --------------------------------
-- Se crea como NOT VALID para no romper la migración si existieran reportes
-- huérfanos de versiones antiguas. PostgREST igualmente detecta la relación y
-- las filas nuevas sí se validan.
create index if not exists reports_user_id_idx on public.reports (user_id);

alter table public.reports
    drop constraint if exists reports_user_id_profiles_fkey;

alter table public.reports
    add constraint reports_user_id_profiles_fkey
    foreign key (user_id)
    references public.profiles (id)
    on update cascade
    on delete cascade
    not valid;

-- Si todos los reportes existentes tienen perfil, validamos la FK para que
-- PostgREST la publique como una relación normal.
do $$
begin
    if not exists (
        select 1
        from public.reports r
        left join public.profiles p on p.id = r.user_id
        where p.id is null
    ) then
        execute 'alter table public.reports validate constraint reports_user_id_profiles_fkey';
    end if;
end;
$$;

-- 6) Vista de respaldo con los datos del reportante -------------------------
-- Se recrea porque `create or replace view` no permite añadir/renombrar
-- columnas ni cambiar el tipo de `image_urls`.
drop view if exists public.reports_view;

create view public.reports_view as
select
    r.id,
    r.user_id,
    r.pet_name,
    r.description,
    r.status,
    r.image_url,
    r.created_at,
    st_astext(r.location) as location_text,
    to_jsonb(r) -> 'image_urls' as image_urls,
    p.full_name as user_name,
    p.avatar_url as user_avatar_url
from public.reports r
left join public.profiles p on p.id = r.user_id;

grant select on public.reports_view to anon, authenticated;

-- 7) Recargar la caché de esquema de PostgREST ------------------------------
notify pgrst, 'reload schema';
