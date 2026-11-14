-- ============================================================================
-- Reportes de publicaciones (report_flags)
-- ============================================================================
-- Ejecutar en el SQL Editor de Supabase (o `supabase db push`).
--
-- Objetivo:
--   1. Crear la tabla `public.report_flags` que usa la app al reportar una
--      publicación desde la pantalla de detalle.
--   2. Evitar que un mismo usuario reporte dos veces la misma publicación.
--   3. Permitir que cada usuario autenticado inserte su propio reporte.
-- ============================================================================

create table if not exists public.report_flags (
    id uuid primary key default gen_random_uuid(),
    report_id uuid not null references public.reports (id) on delete cascade,
    reporter_id uuid not null references auth.users (id) on delete cascade,
    reason text not null check (
        reason in (
            'SPAM_DUPLICATE',
            'FAKE_SCAM',
            'INCORRECT_INFO',
            'INAPPROPRIATE',
            'PRIVATE_INFO',
            'OTHER'
        )
    ),
    custom_reason_text text,
    created_at timestamptz not null default now(),
    constraint report_flags_unique_reporter unique (report_id, reporter_id),
    constraint report_flags_custom_reason_required check (
        reason <> 'OTHER'
        or (
            custom_reason_text is not null
            and length(trim(custom_reason_text)) > 0
        )
    )
);

comment on table public.report_flags is
    'Reportes de publicaciones enviados por los usuarios para revisión.';

create index if not exists report_flags_report_id_idx
    on public.report_flags (report_id);

alter table public.report_flags enable row level security;

drop policy if exists "report_flags_insert_own" on public.report_flags;
create policy "report_flags_insert_own"
    on public.report_flags
    for insert
    to authenticated
    with check (auth.uid() = reporter_id);

drop policy if exists "report_flags_select_own" on public.report_flags;
create policy "report_flags_select_own"
    on public.report_flags
    for select
    to authenticated
    using (auth.uid() = reporter_id);
