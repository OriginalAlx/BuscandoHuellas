-- ============================================================================
-- BuscandoHuellas: RLS y estados para la confirmación desde el chat
-- ============================================================================
-- Ejecutar en el SQL Editor de Supabase. Idempotente.
--
-- Cubre tres cosas:
--   1. `report_matches` con `confirmed_by_owner` y `rejected_by_owner`.
--   2. Políticas que permiten al DUEÑO del reporte perdido actualizar el match
--      y el estado de su reporte (LOST -> SIGHTED -> RESOLVED).
--   3. `reports.status` admite RESOLVED tanto si es TEXT con CHECK como si es
--      un enum de PostgreSQL.
-- ============================================================================

-- 0) Diagnóstico: ejecuta estas consultas antes y después para comparar --------
-- select tablename, policyname, cmd, roles, qual, with_check
-- from pg_policies
-- where schemaname = 'public'
--   and tablename in ('reports', 'report_matches')
-- order by tablename, policyname;
--
-- select c.relname, c.relrowsecurity
-- from pg_class c
-- join pg_namespace n on n.oid = c.relnamespace
-- where n.nspname = 'public' and c.relname in ('reports', 'report_matches');
--
-- select column_name, data_type, column_default
-- from information_schema.columns
-- where table_schema = 'public' and table_name = 'report_matches'
-- order by ordinal_position;
--
-- select conname, pg_get_constraintdef(oid)
-- from pg_constraint
-- where conrelid = 'public.reports'::regclass and contype = 'c';

-- 1) Estructura ---------------------------------------------------------------
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
    add column if not exists confirmed_by_owner boolean not null default false;

alter table public.report_matches
    add column if not exists rejected_by_owner boolean not null default false;

alter table public.report_matches
    alter column confirmed_by_owner set default false;

alter table public.report_matches
    alter column rejected_by_owner set default false;

create index if not exists idx_report_matches_pending
    on public.report_matches (lost_report_id, confirmed_by_owner, rejected_by_owner);

alter table public.report_matches enable row level security;
alter table public.reports enable row level security;

-- Permisos de tabla: RLS no sustituye a GRANT.
grant usage on schema public to authenticated;
grant select, insert, update on public.report_matches to authenticated;
grant select, update on public.reports to authenticated;

-- 2) `reports.status` debe aceptar RESOLVED ----------------------------------
-- Si el valor es un tipo enum, añade el valor (ignora el error si ya existe).
do $$
begin
    if exists (
        select 1
        from pg_type t
        join pg_namespace n on n.oid = t.typnamespace
        where n.nspname = 'public'
          and t.typname = 'report_status'
          and not exists (
              select 1 from pg_enum e where e.enumtypid = t.oid and e.enumlabel = 'RESOLVED'
          )
    ) then
        alter type public.report_status add value 'RESOLVED';
    end if;
end $$;

-- Si es TEXT con CHECK, se reconstruye la restricción con los cuatro estados.
do $$
declare
    constraint_name text;
begin
    for constraint_name in
        select conname
        from pg_constraint
        where conrelid = 'public.reports'::regclass
          and contype = 'c'
          and pg_get_constraintdef(oid) ilike '%status%'
    loop
        execute format('alter table public.reports drop constraint %I', constraint_name);
    end loop;
end $$;

alter table public.reports
    add constraint reports_status_check
    check (status in ('LOST', 'SIGHTED', 'FOUND', 'RESOLVED'));

-- 3) Políticas de report_matches ---------------------------------------------
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

drop policy if exists "matches_update" on public.report_matches;
create policy "matches_update"
    on public.report_matches
    for update
    to authenticated
    using (
        auth.uid() = matched_by
        or exists (
            select 1 from public.reports r
            where r.id = report_matches.lost_report_id
              and r.user_id = auth.uid()
        )
    )
    with check (
        auth.uid() = matched_by
        or exists (
            select 1 from public.reports r
            where r.id = report_matches.lost_report_id
              and r.user_id = auth.uid()
        )
    );

-- 4) Política de UPDATE en reports para el dueño -----------------------------
-- Sin esta política el UPDATE a SIGHTED/RESOLVED devuelve 0 filas y el
-- ViewModel revierte el banner optimista.
drop policy if exists "reports_update_owner" on public.reports;
create policy "reports_update_owner"
    on public.reports
    for update
    to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

-- 5) Recargar la caché de PostgREST ------------------------------------------
notify pgrst, 'reload schema';
