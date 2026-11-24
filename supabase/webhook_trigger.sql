-- ============================================================================
-- Equivalente al Database Webhook "on_report_created" (INSERT en public.reports).
-- Pegar y ejecutar en Supabase → SQL Editor. Es idempotente: se puede repetir.
--
-- OJO: este archivo contiene el WEBHOOK_SECRET. No lo commitees; si lo haces,
-- rota el secreto en Supabase → Edge Functions → Secrets.
-- ============================================================================

-- 1) Asegurar pg_net (si ya está activa, no hace nada)
create schema if not exists extensions;
create extension if not exists pg_net with schema extensions;

-- 2) Guardar el secreto en Vault solo si no existe ya
do $$
begin
  if not exists (
    select 1 from vault.decrypted_secrets
    where name = 'generate_alerts_webhook_secret'
  ) then
    perform vault.create_secret(
      '6340d009a9d99ba13e5eb4beeaaeb12267a37f15d98db6ba26fd204062ee9dd5',
      'generate_alerts_webhook_secret'
    );
  end if;
end $$;

-- 3) Función que llama a la Edge Function
create or replace function public.on_report_created()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform net.http_post(
    url := 'https://jebvukcmluaaarjumyws.supabase.co/functions/v1/generate-alerts',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || (
        select decrypted_secret
        from vault.decrypted_secrets
        where name = 'generate_alerts_webhook_secret'
        limit 1
      )
    ),
    body := jsonb_build_object(
      'type', tg_op,
      'table', tg_table_name,
      'schema', tg_table_schema,
      'record', to_jsonb(new),
      'old_record', null
    ),
    timeout_milliseconds := 5000
  );
  return new;
end;
$$;

-- 4) Trigger AFTER INSERT sobre reports
drop trigger if exists on_report_created on public.reports;
create trigger on_report_created
after insert on public.reports
for each row
execute function public.on_report_created();

-- Verificación:
-- select tgname, pg_get_triggerdef(oid)
-- from pg_trigger
-- where tgrelid = 'public.reports'::regclass and not tgisinternal;
