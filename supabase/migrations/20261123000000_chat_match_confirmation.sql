-- ============================================================================
-- Confirmación de mascotas encontradas desde el chat
-- ============================================================================
-- Ejecutar en el SQL Editor de Supabase (o `supabase db push`). Idempotente.
--
-- Objetivo:
--   1. Permitir al dueño del reporte perdido actualizar `report_matches` y
--      marcar el match como confirmado o rechazado.
--   2. Permitir al dueño actualizar el `status` de su reporte:
--      LOST -> SIGHTED al confirmar y SIGHTED -> RESOLVED al recoger la mascota.
--   3. Distinguir "pendiente" de "rechazado" con `rejected_by_owner`, para que
--      el banner del chat no reaparezca después de rechazar un match.
-- ============================================================================

-- 1) Tabla de emparejamientos -------------------------------------------------
create table if not exists public.report_matches (
    id uuid primary key default gen_random_uuid(),
    found_report_id uuid references public.reports (id) on delete cascade,
    lost_report_id uuid not null references public.reports (id) on delete cascade,
    matched_by uuid not null references auth.users (id),
    confirmed_by_owner boolean not null default false,
    rejected_by_owner boolean not null default false,
    similarity_score double precision,
    created_at timestamptz not null default now()
);

alter table public.report_matches
    add column if not exists rejected_by_owner boolean not null default false;

alter table public.report_matches
    alter column confirmed_by_owner set default false;

create index if not exists idx_report_matches_pending
    on public.report_matches (lost_report_id, confirmed_by_owner, rejected_by_owner);

alter table public.report_matches enable row level security;
alter table public.reports enable row level security;

-- RLS no sustituye a GRANT: el rol authenticated necesita UPDATE en ambas
-- tablas para que los UPDATE devuelvan las filas afectadas.
grant usage on schema public to authenticated;
grant select, insert, update on public.report_matches to authenticated;
grant select, update on public.reports to authenticated;

-- 2) Políticas de report_matches ---------------------------------------------
drop policy if exists "matches_select" on public.report_matches;
create policy "matches_select"
    on public.report_matches
    for select
    to authenticated
    using (true);

drop policy if exists "matches_insert" on public.report_matches;
create policy "matches_insert"
    on public.report_matches
    for insert
    to authenticated
    with check (auth.uid() = matched_by);

-- Dueño del reporte perdido. La comprobación se repite en WITH CHECK para que
-- no pueda escribir sobre un reporte ajeno.
drop policy if exists "matches_update" on public.report_matches;
create policy "matches_update"
    on public.report_matches
    for update
    to authenticated
    using (
        auth.uid() = matched_by
        or exists (
            select 1 from public.reports
            where reports.id = report_matches.lost_report_id
              and reports.user_id = auth.uid()
        )
    )
    with check (
        auth.uid() = matched_by
        or exists (
            select 1 from public.reports
            where reports.id = report_matches.lost_report_id
              and reports.user_id = auth.uid()
        )
    );

-- 3) Política de UPDATE en reports para el dueño -----------------------------
-- Sin esta política el dueño no puede marcar su reporte como SIGHTED al
-- confirmar, ni como RESOLVED cuando indica que ya está en casa.
drop policy if exists "reports_update_owner" on public.reports;
create policy "reports_update_owner"
    on public.reports
    for update
    to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- 4) Recargar la caché de esquema de PostgREST -------------------------------
notify pgrst, 'reload schema';
