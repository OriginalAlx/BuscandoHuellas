-- ============================================================================
-- BuscandoHuellas: soporte de imágenes y ubicaciones en el chat
-- Ejecutar en Supabase SQL Editor. El script es idempotente.
-- ============================================================================

-- 1. Columnas nuevas en public.messages --------------------------------------
alter table public.messages
    add column if not exists message_type text not null default 'text';

alter table public.messages
    add column if not exists image_url text;

alter table public.messages
    add column if not exists latitude double precision;

alter table public.messages
    add column if not exists longitude double precision;

alter table public.messages
    add column if not exists location_label text;

-- Restringe los valores permitidos del tipo de mensaje. Si por algún motivo
-- existen filas con valores distintos, el check no se crea hasta corregirlas.
do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'messages_message_type_check'
    ) then
        alter table public.messages
            add constraint messages_message_type_check
            check (message_type in ('text', 'image', 'location'));
    end if;
end $$;

-- Los mensajes de texto siguen llenando `content`. Los de imagen/ubicación
-- pueden tener content vacío, así que se relaja el NOT NULL (sin borrar datos).
alter table public.messages alter column content drop not null;
alter table public.messages alter column content set default '';

-- Índice útil para filtrar por tipo si se necesita en el futuro.
create index if not exists messages_type_idx on public.messages (message_type);

-- 2. Políticas RLS existentes ------------------------------------------------
-- Las políticas `messages_select_participant` y `messages_insert_sender_participant`
-- de chat_rls.sql siguen siendo válidas: no dependen de las columnas nuevas y
-- los campos adicionales se insertan en el mismo INSERT del mensaje, por lo que
-- WITH CHECK evalúa únicamente sender_id / conversation_id.
-- Si aún no aplicaste chat_rls.sql, ejecútalo primero.

-- 3. Bucket de Storage para imágenes del chat --------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'chat_images',
    'chat_images',
    true,
    5242880, -- 5 MB
    array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update set
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- 4. Políticas de Storage ----------------------------------------------------
-- SELECT público: cualquiera puede ver la imagen con su URL pública.
drop policy if exists "chat_images_public_read" on storage.objects;
create policy "chat_images_public_read"
    on storage.objects for select
    using (bucket_id = 'chat_images');

-- INSERT: solo usuarios autenticados, y la carpeta raíz debe ser su user id
-- (convención de ruta: chat_images/{sender_id}/{timestamp}.jpg).
drop policy if exists "chat_images_authenticated_insert" on storage.objects;
create policy "chat_images_authenticated_insert"
    on storage.objects for insert
    to authenticated
    with check (
        bucket_id = 'chat_images'
        and auth.uid() is not null
        and (storage.foldername(name))[1] = auth.uid()::text
    );

-- DELETE: solo el dueño de la imagen (mismo dueño de la carpeta).
drop policy if exists "chat_images_owner_delete" on storage.objects;
create policy "chat_images_owner_delete"
    on storage.objects for delete
    to authenticated
    using (
        bucket_id = 'chat_images'
        and (storage.foldername(name))[1] = auth.uid()::text
    );

-- NOTA: no se crea política UPDATE; las imágenes son inmutables y se reemplazan
-- subiendo un archivo nuevo.

-- 5. Recargar la caché de esquema de PostgREST --------------------------------
-- Sin esto, PostgREST puede seguir respondiendo "Could not find the
-- 'message_type' column of 'messages' in the schema cache" aunque la columna ya
-- exista en la base de datos.
notify pgrst, 'reload schema';
