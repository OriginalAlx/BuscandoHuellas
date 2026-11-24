-- ============================================================================
-- Roles de moderación y permisos RLS para eliminar reportes
-- ============================================================================
-- Ejecutar en el SQL Editor de Supabase (o `supabase db push`).
--
-- Motivo: el panel de moderación puede LEER los reportes pero el DELETE era
-- rechazado porque las políticas de `public.reports` solo contemplaban al
-- propietario (`auth.uid() = user_id`).
--
-- Objetivo:
--   1. Crear `public.user_roles` con roles en minúsculas: user|moderator|admin.
--   2. Crear los helpers `public.is_moderator()` y `public.is_admin()`.
--   3. Permitir DELETE/UPDATE de reportes al propietario O a moderadores/admins.
--   4. Permitir a moderadores gestionar denuncias y escribir la bitácora.
--   5. Migrar los roles existentes de `public.users.role` (USER/MODERATOR/ADMIN)
--      y asignar el rol `user` por defecto a los usuarios nuevos.
--
-- NOTA: este script es idempotente; se puede volver a ejecutar sin duplicar
--       políticas ni perder roles asignados manualmente.
-- ============================================================================

-- 1) Tabla de roles ----------------------------------------------------------
create table if not exists public.user_roles (
    user_id uuid primary key references auth.users (id) on delete cascade,
    role text not null check (role in ('user', 'moderator', 'admin')),
    created_at timestamptz not null default now()
);

comment on table public.user_roles is
    'Roles de la app: user, moderator y admin. Un rol por usuario.';

create index if not exists idx_user_roles_user_id on public.user_roles (user_id);
create index if not exists idx_user_roles_role on public.user_roles (role);

alter table public.user_roles enable row level security;

grant select, insert, update, delete on public.user_roles to authenticated;

-- 2) Funciones helper --------------------------------------------------------
-- SECURITY DEFINER + search_path fijo evita la recursión al consultar
-- `user_roles` desde una política de la propia tabla `user_roles`.
create or replace function public.is_moderator()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.user_roles ur
        where ur.user_id = auth.uid()
          and ur.role in ('moderator', 'admin')
    );
$$;

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.user_roles ur
        where ur.user_id = auth.uid()
          and ur.role = 'admin'
    );
$$;

revoke all on function public.is_moderator() from public, anon;
revoke all on function public.is_admin() from public, anon;
grant execute on function public.is_moderator() to authenticated;
grant execute on function public.is_admin() to authenticated;

-- 3) Políticas de user_roles -------------------------------------------------
-- Lectura abierta a autenticados: la app necesita consultar el rol del usuario.
drop policy if exists "user_roles_select" on public.user_roles;
create policy "user_roles_select"
    on public.user_roles
    for select
    to authenticated
    using (true);

drop policy if exists "user_roles_insert" on public.user_roles;
create policy "user_roles_insert"
    on public.user_roles
    for insert
    to authenticated
    with check (public.is_admin());

drop policy if exists "user_roles_update" on public.user_roles;
create policy "user_roles_update"
    on public.user_roles
    for update
    to authenticated
    using (public.is_admin())
    with check (public.is_admin());

drop policy if exists "user_roles_delete" on public.user_roles;
create policy "user_roles_delete"
    on public.user_roles
    for delete
    to authenticated
    using (public.is_admin());

-- 4) Rol por defecto para usuarios nuevos ------------------------------------
create or replace function public.handle_new_user_role()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.user_roles (user_id, role)
    values (new.id, 'user')
    on conflict (user_id) do nothing;
    return new;
end;
$$;

revoke all on function public.handle_new_user_role() from public, anon;

drop trigger if exists on_auth_user_created_role on auth.users;
create trigger on_auth_user_created_role
    after insert on auth.users
    for each row
    execute function public.handle_new_user_role();

-- 5) Migración de roles existentes -------------------------------------------
-- `public.users.role` guarda los valores en mayúsculas (USER, MODERATOR, ADMIN).
-- `on conflict do nothing` respeta roles ya asignados en `user_roles`.
insert into public.user_roles (user_id, role)
select
    pu.id,
    case upper(coalesce(pu.role, 'USER'))
        when 'ADMIN' then 'admin'
        when 'MODERATOR' then 'moderator'
        else 'user'
    end
from public.users pu
where pu.id is not null
on conflict (user_id) do nothing;

-- Usuarios de auth.users que aún no tengan fila: rol `user` por defecto.
insert into public.user_roles (user_id, role)
select au.id, 'user'
from auth.users au
left join public.user_roles ur on ur.user_id = au.id
where ur.user_id is null
on conflict (user_id) do nothing;

-- Moderador conocido del proyecto. Solo se fuerza si su rol actual es `user`
-- para no sobrescribir un rol asignado manualmente.
insert into public.user_roles (user_id, role)
values ('86f849da-00d5-4da8-9d5e-d043f80a75dd', 'moderator')
on conflict (user_id) do update
    set role = 'moderator'
    where public.user_roles.role = 'user';

-- Para promover a otro usuario (en el SQL Editor):
-- insert into public.user_roles (user_id, role)
-- values ('UUID_DEL_USUARIO', 'moderator')
-- on conflict (user_id) do update set role = excluded.role;

-- 6) Políticas de reports ----------------------------------------------------
-- DELETE: dueño O moderador/admin. Se conservan nombres antiguos por si
-- existieran en el proyecto y se eliminan antes de recrearlos.
drop policy if exists "reports_delete_own" on public.reports;
drop policy if exists "reports_delete_owner" on public.reports;
drop policy if exists "reports_delete" on public.reports;
create policy "reports_delete"
    on public.reports
    for delete
    to authenticated
    using (
        auth.uid() = user_id
        or public.is_moderator()
    );

-- UPDATE: misma regla. Esto preserva los cambios de estado del dueño
-- (confirmación de match, "ya está en casa") y añade edición de moderadores.
-- Se usa un nombre distinto al de `reports_update_owner` para que volver a
-- ejecutar el script de confirmación de chat no revoque este permiso.
drop policy if exists "reports_update" on public.reports;
create policy "reports_update"
    on public.reports
    for update
    to authenticated
    using (
        auth.uid() = user_id
        or public.is_moderator()
    )
    with check (
        auth.uid() = user_id
        or public.is_moderator()
    );

-- 7) Denuncias de la comunidad -----------------------------------------------
-- Los moderadores deben poder ver TODAS las denuncias pendientes y cambiar
-- su estado (DISMISS/RESOLVED). El usuario sigue viendo las suyas.
drop policy if exists "report_flags_select_moderator" on public.report_flags;
create policy "report_flags_select_moderator"
    on public.report_flags
    for select
    to authenticated
    using (auth.uid() = reporter_id or public.is_moderator());

drop policy if exists "report_flags_update_moderator" on public.report_flags;
create policy "report_flags_update_moderator"
    on public.report_flags
    for update
    to authenticated
    using (public.is_moderator())
    with check (public.is_moderator());

-- 8) Bitácora de moderación --------------------------------------------------
create table if not exists public.moderation_logs (
    id uuid primary key default gen_random_uuid(),
    report_id uuid not null,
    moderator_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    action text not null check (action in ('EDIT', 'DELETE', 'DISMISS')),
    reason text,
    created_at timestamptz not null default now()
);

alter table public.moderation_logs enable row level security;

grant insert, select on public.moderation_logs to authenticated;

drop policy if exists "moderation_logs_insert_moderator" on public.moderation_logs;
create policy "moderation_logs_insert_moderator"
    on public.moderation_logs
    for insert
    to authenticated
    with check (public.is_moderator());

drop policy if exists "moderation_logs_select_moderator" on public.moderation_logs;
create policy "moderation_logs_select_moderator"
    on public.moderation_logs
    for select
    to authenticated
    using (public.is_moderator());

-- 9) Recargar la caché del esquema para PostgREST ----------------------------
notify pgrst, 'reload schema';
