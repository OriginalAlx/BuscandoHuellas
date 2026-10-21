-- =============================================================================
-- Buscando Huellas - Tarea 1: perfil automático en public.users
-- =============================================================================
-- PROBLEMA
--   Al registrar una cuenta, Supabase Auth crea el usuario en auth.users, pero
--   la app no puede insertar la fila correspondiente en public.users ("new row
--   violates row-level security policy", HTTP 403). La cuenta existe en Auth
--   pero se queda sin perfil.
--
-- SOLUCIÓN
--   Un trigger en auth.users que crea el perfil automáticamente con el rol fijo
--   'USER'. El trigger es SECURITY DEFINER, así que se ejecuta con permisos
--   elevados y NO necesita una policy de INSERT. Esto además evita que un
--   cliente pueda insertar roles arbitrarios (p. ej. MODERATOR) directamente.
--
-- EJECUCIÓN
--   Supabase Dashboard -> SQL Editor -> pegar y "Run".
--   El script es idempotente: puede ejecutarse varias veces sin efectos dobles.
-- =============================================================================

-- 1) Función que crea el perfil al alta de un usuario en Auth -----------------
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.users (id, email, role, password_hash)
    values (new.id, new.email, 'USER', '')
    on conflict (id) do nothing;
    return new;
end;
$$;

-- Solo el trigger debe poder invocarla (no exponerla como RPC).
revoke execute on function public.handle_new_user() from public, anon, authenticated;

-- 2) Trigger asociado al alta en auth.users ----------------------------------
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row
    execute function public.handle_new_user();

-- 3) Backfill: crea el perfil de los usuarios de Auth que aún no lo tengan ----
--    No toca a los que ya existen (p. ej. el moderador conserva su rol).
insert into public.users (id, email, role, password_hash)
select au.id, au.email, 'USER', ''
from auth.users au
left join public.users pu on pu.id = au.id
where pu.id is null
  and au.email is not null;

-- 4) RLS de public.users: cada usuario lee/actualiza solo su propio perfil -----
--    (El INSERT lo cubre el trigger; no se expone una policy de INSERT.)
alter table public.users enable row level security;

drop policy if exists "users_select_own" on public.users;
create policy "users_select_own"
    on public.users
    for select
    to authenticated
    using (auth.uid() = id);

drop policy if exists "users_update_own" on public.users;
create policy "users_update_own"
    on public.users
    for update
    to authenticated
    using (auth.uid() = id)
    with check (auth.uid() = id);
