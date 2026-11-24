-- ============================================================================
-- BuscandoHuellas: políticas RLS para el chat
-- Ejecutar en Supabase SQL Editor. El script es idempotente.
-- ============================================================================

-- 0. Estructura esperada (solo se crea si no existe; no altera columnas ya
--    presentes). La app inserta sender_name en cada mensaje.
create table if not exists public.conversations (
    id uuid primary key default gen_random_uuid(),
    report_id uuid not null,
    user_one_id uuid not null references auth.users(id) on delete cascade,
    user_two_id uuid not null references auth.users(id) on delete cascade,
    created_at timestamptz not null default now()
);

create table if not exists public.messages (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null references public.conversations(id) on delete cascade,
    sender_id uuid not null references auth.users(id) on delete cascade,
    sender_name text not null default 'Usuario',
    content text not null,
    created_at timestamptz not null default now()
);

-- Si la columna sender_name ya existe pero es nullable o no tiene default,
-- estas sentencias la dejan lista. Si tu esquema ya la tiene NOT NULL, no hacen
-- nada (el default es seguro).
alter table public.messages alter column sender_name set default 'Usuario';
alter table public.messages alter column sender_name set not null;

create index if not exists messages_conversation_created_idx
    on public.messages (conversation_id, created_at);

-- 1. Habilitar RLS.
alter table public.conversations enable row level security;
alter table public.messages enable row level security;

-- 2. Permisos base para el rol que usa la app.
grant select, insert on public.conversations to authenticated;
grant select, insert on public.messages to authenticated;
grant usage on schema public to authenticated;

-- 3. Políticas de conversaciones: solo participantes pueden ver/crear.
drop policy if exists "conversations_select_participant" on public.conversations;
create policy "conversations_select_participant"
    on public.conversations for select
    to authenticated
    using (
        auth.uid() = user_one_id
        or auth.uid() = user_two_id
    );

drop policy if exists "conversations_insert_participant" on public.conversations;
create policy "conversations_insert_participant"
    on public.conversations for insert
    to authenticated
    with check (
        auth.uid() = user_one_id
        or auth.uid() = user_two_id
    );

-- 4. Políticas de mensajes: el remitente debe ser la sesión activa y además
--    pertenecer a la conversación. SELECT lo pueden hacer ambos participantes.
drop policy if exists "messages_select_participant" on public.messages;
create policy "messages_select_participant"
    on public.messages for select
    to authenticated
    using (
        exists (
            select 1
            from public.conversations c
            where c.id = messages.conversation_id
              and (auth.uid() = c.user_one_id or auth.uid() = c.user_two_id)
        )
    );

drop policy if exists "messages_insert_sender_participant" on public.messages;
create policy "messages_insert_sender_participant"
    on public.messages for insert
    to authenticated
    with check (
        auth.uid() = sender_id
        and exists (
            select 1
            from public.conversations c
            where c.id = messages.conversation_id
              and (auth.uid() = c.user_one_id or auth.uid() = c.user_two_id)
        )
    );

-- 5. Opcional: si en el futuro se añade `read_at`, esta política permite
--    marcar como leídos solo los mensajes recibidos por el usuario.
-- alter table public.messages add column if not exists read_at timestamptz;
-- drop policy if exists "messages_update_mark_read" on public.messages;
-- create policy "messages_update_mark_read"
--     on public.messages for update
--     to authenticated
--     using (sender_id <> auth.uid())
--     with check (sender_id <> auth.uid());

-- 6. Recargar la caché de esquema de PostgREST -------------------------------
notify pgrst, 'reload schema';
