-- ============================================================================
-- Buscando Huellas — Alertas persistentes + tokens FCM
-- ============================================================================
-- Ejecutar completo en el SQL Editor de Supabase (o con `supabase db push`).
-- El script es idempotente: puede volver a ejecutarse sin duplicar objetos.
--
-- Qué resuelve:
--   1. Crea/actualiza `public.user_notification_tokens` con UNIQUE(user_id) y
--      UNIQUE(fcm_token). El upsert del cliente usa `on_conflict=user_id`.
--   2. Añade la función `register_notification_token`, SECURITY DEFINER, que
--      reasigna un token de un usuario anterior al usuario autenticado sin
--      chocar con RLS.
--   3. Crea `public.alerts` con índices, RLS e INSERT reservado al service_role
--      (la Edge Function `generate-alerts` inserta con la service key).
--   4. Añade `alerts` a la publicación de Realtime para que la app reciba los
--      INSERT/UPDATE/DELETE en vivo.
--
-- IMPORTANTE: la app guarda también preferencias y ubicación en la fila del
-- token; la Edge Function las usa para filtrar por radio y tipo de reporte.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) Tokens FCM por usuario
-- ----------------------------------------------------------------------------
-- `notifications_enabled` nace en false porque la app también arranca con las
-- notificaciones desactivadas; al guardar el token, el cliente sincroniza sus
-- preferencias reales (DataStore) en esta misma fila.
create table if not exists public.user_notification_tokens (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    fcm_token text not null,
    notifications_enabled boolean not null default false,
    radius_km double precision,
    notify_lost boolean not null default true,
    notify_sighted boolean not null default true,
    notify_found boolean not null default true,
    latitude double precision,
    longitude double precision,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Si la tabla ya existía con el esquema antiguo, se completan las columnas.
alter table public.user_notification_tokens
    add column if not exists notifications_enabled boolean not null default false,
    add column if not exists radius_km double precision,
    add column if not exists notify_lost boolean not null default true,
    add column if not exists notify_sighted boolean not null default true,
    add column if not exists notify_found boolean not null default true,
    add column if not exists latitude double precision,
    add column if not exists longitude double precision,
    add column if not exists created_at timestamptz not null default now(),
    add column if not exists updated_at timestamptz not null default now();

-- Limpieza previa: si había varias filas por usuario o por token, se conserva
-- la más reciente. Sin esto, los UNIQUE de abajo fallarían.
delete from public.user_notification_tokens t
using (
    select ctid,
           row_number() over (
               partition by user_id
               order by updated_at desc nulls last, ctid desc
           ) as position
    from public.user_notification_tokens
) duplicates
where t.ctid = duplicates.ctid
  and duplicates.position > 1;

delete from public.user_notification_tokens t
using (
    select ctid,
           row_number() over (
               partition by fcm_token
               order by updated_at desc nulls last, ctid desc
           ) as position
    from public.user_notification_tokens
) duplicates
where t.ctid = duplicates.ctid
  and duplicates.position > 1;

-- Un usuario = un dispositivo/token activo. Un token = un único usuario.
alter table public.user_notification_tokens
    drop constraint if exists user_notification_tokens_user_id_key;
alter table public.user_notification_tokens
    add constraint user_notification_tokens_user_id_key unique (user_id);

alter table public.user_notification_tokens
    drop constraint if exists user_notification_tokens_fcm_token_key;
alter table public.user_notification_tokens
    add constraint user_notification_tokens_fcm_token_key unique (fcm_token);

create index if not exists user_notification_tokens_user_id_idx
    on public.user_notification_tokens (user_id);

-- Mantiene `updated_at` al día en cada UPDATE.
create or replace function public.touch_user_notification_token()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists touch_user_notification_token on public.user_notification_tokens;
create trigger touch_user_notification_token
    before update on public.user_notification_tokens
    for each row
    execute function public.touch_user_notification_token();

-- RLS: cada usuario solo ve/edita su propia fila. El INSERT directo queda
-- permitido para el dueño; la reasignación entre cuentas la hace la RPC.
alter table public.user_notification_tokens enable row level security;

drop policy if exists "tokens_select_own" on public.user_notification_tokens;
create policy "tokens_select_own"
    on public.user_notification_tokens
    for select
    to authenticated
    using (auth.uid() = user_id);

drop policy if exists "tokens_insert_own" on public.user_notification_tokens;
create policy "tokens_insert_own"
    on public.user_notification_tokens
    for insert
    to authenticated
    with check (auth.uid() = user_id);

drop policy if exists "tokens_update_own" on public.user_notification_tokens;
create policy "tokens_update_own"
    on public.user_notification_tokens
    for update
    to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

drop policy if exists "tokens_delete_own" on public.user_notification_tokens;
create policy "tokens_delete_own"
    on public.user_notification_tokens
    for delete
    to authenticated
    using (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- 2) RPC atómica de registro/reasignación del token
-- ----------------------------------------------------------------------------
-- SECURITY DEFINER permite borrar la fila antigua del token aunque pertenezca
-- a otra cuenta (caso: logout sin conexión y login con otro usuario en el
-- mismo dispositivo). Después hace UPSERT por user_id.
create or replace function public.register_notification_token(p_fcm_token text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id uuid := auth.uid();
begin
    if v_user_id is null then
        raise exception 'No hay usuario autenticado';
    end if;

    if p_fcm_token is null or btrim(p_fcm_token) = '' then
        raise exception 'El token FCM no puede estar vacío';
    end if;

    -- Libera el token de cualquier otra cuenta.
    delete from public.user_notification_tokens
    where fcm_token = p_fcm_token
      and user_id <> v_user_id;

    insert into public.user_notification_tokens (user_id, fcm_token, updated_at)
    values (v_user_id, p_fcm_token, now())
    on conflict (user_id) do update
        set fcm_token = excluded.fcm_token,
            updated_at = now();
end;
$$;

revoke all on function public.register_notification_token(text) from public, anon;
grant execute on function public.register_notification_token(text) to authenticated;

-- ----------------------------------------------------------------------------
-- 3) Tabla de alertas persistentes
-- ----------------------------------------------------------------------------
create table if not exists public.alerts (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users (id) on delete cascade,
    report_id uuid not null references public.reports (id) on delete cascade,
    title text not null,
    message text not null,
    is_read boolean not null default false,
    created_at timestamptz not null default now()
);

create index if not exists alerts_user_id_idx on public.alerts (user_id);
create index if not exists alerts_report_id_idx on public.alerts (report_id);
create index if not exists alerts_is_read_idx on public.alerts (is_read);
create index if not exists alerts_user_created_idx
    on public.alerts (user_id, created_at desc);

-- Evita duplicados si el webhook se reintenta.
create unique index if not exists alerts_user_report_unique_idx
    on public.alerts (user_id, report_id);

alter table public.alerts enable row level security;

-- El usuario solo puede leer, marcar como leída o borrar sus alertas.
drop policy if exists "alerts_select_own" on public.alerts;
create policy "alerts_select_own"
    on public.alerts
    for select
    to authenticated
    using (auth.uid() = user_id);

drop policy if exists "alerts_update_own" on public.alerts;
create policy "alerts_update_own"
    on public.alerts
    for update
    to authenticated
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

drop policy if exists "alerts_delete_own" on public.alerts;
create policy "alerts_delete_own"
    on public.alerts
    for delete
    to authenticated
    using (auth.uid() = user_id);

-- No se crea política de INSERT para `authenticated`: solo el service_role
-- (Edge Function) puede insertar alertas. service_role omite RLS.

-- ----------------------------------------------------------------------------
-- 4) Realtime para actualizar la campana y la lista sin recargar
-- ----------------------------------------------------------------------------
-- REPLICA IDENTITY FULL permite que Realtime evalúe la RLS también en UPDATE
-- y DELETE (sin esto, los eventos de borrado pueden descartarse en silencio).
alter table public.alerts replica identity full;

do $$
begin
    if not exists (
        select 1
        from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'alerts'
    ) then
        alter publication supabase_realtime add table public.alerts;
    end if;
end;
$$;

-- Comprobación rápida (opcional):
-- select * from pg_publication_tables where pubname = 'supabase_realtime';
-- select policyname, cmd from pg_policies where tablename = 'alerts';

-- ----------------------------------------------------------------------------
-- 5) Permisos explícitos de la Edge Function (service_role)
-- ----------------------------------------------------------------------------
-- service_role omite RLS, pero PostgreSQL sigue comprobando privilegios de
-- tabla. En Supabase suele tenerlos por defecto; estos GRANT evitan depender
-- de esa configuración y hacen el script autocontenido.
grant select, delete on public.user_notification_tokens to service_role;
grant select, insert, update, delete on public.alerts to service_role;

-- `reports_view` se recrea en las migraciones anteriores; se comprueba su
-- existencia para que este script también funcione en una BD vacía.
do $$
begin
    if to_regclass('public.reports_view') is not null then
        grant select on public.reports_view to service_role;
    end if;
end;
$$;
