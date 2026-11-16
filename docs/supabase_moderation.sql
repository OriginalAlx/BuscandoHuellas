-- ============================================================================
-- BUSCANDOHUELLAS · PANEL DE MODERACIÓN
-- ============================================================================
-- Ejecutar manualmente en el SQL Editor de Supabase.
--
-- Tabla de denuncias detectada en el proyecto: public.report_flags
--   - report_id     -> public.reports(id) ON DELETE CASCADE
--   - reporter_id   -> auth.users(id) ON DELETE CASCADE
--   - reason / custom_reason_text / created_at
--   - unique (report_id, reporter_id)
--
-- Esta migración añade el estado de la denuncia y las políticas de moderador.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 0) ESTADO DE LAS DENUNCIAS
-- ----------------------------------------------------------------------------
-- La tabla original no tenía columna de estado. Los valores permitidos son:
--   PENDING   -> denuncia pendiente de revisión
--   DISMISSED -> denuncia descartada por un moderador
--   RESOLVED  -> denuncia atendida al editar/eliminar el reporte
-- ----------------------------------------------------------------------------
alter table public.report_flags
    add column if not exists status text not null default 'PENDING';

alter table public.report_flags
    drop constraint if exists report_flags_status_check;

alter table public.report_flags
    add constraint report_flags_status_check
    check (status in ('PENDING', 'DISMISSED', 'RESOLVED'));

create index if not exists report_flags_status_created_idx
    on public.report_flags (status, created_at desc);

-- ----------------------------------------------------------------------------
-- 1) HELPER: ¿el usuario autenticado es moderador?
-- ----------------------------------------------------------------------------
create or replace function public.is_moderator() returns boolean
language sql stable security definer set search_path = public as $$
    select exists (
        select 1
        from public.users
        where id = auth.uid()
          and role = 'MODERATOR'
    );
$$;

-- ----------------------------------------------------------------------------
-- 2) POLÍTICAS DE MODERACIÓN SOBRE REPORTS
-- ----------------------------------------------------------------------------
drop policy if exists "moderators_can_update_all_reports" on public.reports;
create policy "moderators_can_update_all_reports"
    on public.reports
    for update
    to authenticated
    using (public.is_moderator());

drop policy if exists "moderators_can_delete_all_reports" on public.reports;
create policy "moderators_can_delete_all_reports"
    on public.reports
    for delete
    to authenticated
    using (public.is_moderator());

-- ----------------------------------------------------------------------------
-- 3) POLÍTICAS DE MODERACIÓN SOBRE STORAGE
-- ----------------------------------------------------------------------------
drop policy if exists "moderators_can_delete_report_images" on storage.objects;
create policy "moderators_can_delete_report_images"
    on storage.objects
    for delete
    to authenticated
    using (
        bucket_id = 'pet-reports'
        and public.is_moderator()
    );

-- ----------------------------------------------------------------------------
-- 4) POLÍTICAS DE MODERACIÓN SOBRE REPORT_FLAGS
-- ----------------------------------------------------------------------------
drop policy if exists "moderators_can_read_flags" on public.report_flags;
create policy "moderators_can_read_flags"
    on public.report_flags
    for select
    to authenticated
    using (public.is_moderator());

drop policy if exists "moderators_can_update_flags" on public.report_flags;
create policy "moderators_can_update_flags"
    on public.report_flags
    for update
    to authenticated
    using (public.is_moderator());

-- ----------------------------------------------------------------------------
-- 5) BITÁCORA DE MODERACIÓN
-- ----------------------------------------------------------------------------
-- No se crea FK hacia reports: la bitácora debe sobrevivir al borrado del
-- reporte para conservar la trazabilidad de la acción.
-- ----------------------------------------------------------------------------
create table if not exists public.moderation_logs (
    id uuid primary key default gen_random_uuid(),
    report_id uuid not null,
    moderator_id uuid not null default auth.uid(),
    action text not null,
    reason text,
    created_at timestamptz not null default now()
);

create index if not exists moderation_logs_report_id_idx
    on public.moderation_logs (report_id);

alter table public.moderation_logs enable row level security;

drop policy if exists "moderators_can_insert_logs" on public.moderation_logs;
create policy "moderators_can_insert_logs"
    on public.moderation_logs
    for insert
    to authenticated
    with check (public.is_moderator());

drop policy if exists "moderators_can_read_logs" on public.moderation_logs;
create policy "moderators_can_read_logs"
    on public.moderation_logs
    for select
    to authenticated
    using (public.is_moderator());

-- ----------------------------------------------------------------------------
-- 6) RECARGAR LA CACHÉ DE ESQUEMA DE POSTGREST
-- ----------------------------------------------------------------------------
notify pgrst, 'reload schema';

-- ============================================================================
-- SEGURIDAD IMPORTANTE · REVISAR MANUALMENTE
-- ============================================================================
-- Verifica que ningún usuario pueda modificar su propio `role` en public.users.
-- La política actual "users_update_own" permite UPDATE de la propia fila y,
-- tal como está definida, también permitiría cambiar `role` a 'MODERATOR'.
-- Restringe las columnas actualizables (GRANT UPDATE por columnas) o añade un
-- trigger/with-check que impida cambiar `role` desde el cliente.
-- ============================================================================
