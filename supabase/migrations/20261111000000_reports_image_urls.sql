-- Soporte para múltiples fotos por reporte.
--
-- Ejecutar este archivo en el SQL Editor de Supabase (o con `supabase db push`)
-- antes de publicar reportes con más de una foto. La app funciona sin esta
-- migración, pero solo guardará la primera imagen.
--
-- 1) Agrega la columna que almacena el arreglo de URLs.
alter table public.reports
    add column if not exists image_urls text[];

-- 2) Recrea la vista para exponer image_urls. Se usa drop + create porque
--    `create or replace view` no permite cambiar el orden de las columnas.
--    Si tu reports_view original tenía filtros (por ejemplo, solo reportes
--    activos), vuelve a añadirlos en el SELECT de abajo.
drop view if exists public.reports_view;

create view public.reports_view as
select
    id,
    user_id,
    pet_name,
    description,
    status,
    image_url,
    created_at,
    st_astext(location) as location_text,
    image_urls
from public.reports;

-- 3) Restaura los permisos de lectura que usa la app (rol anon y authenticated).
grant select on public.reports_view to anon, authenticated;
