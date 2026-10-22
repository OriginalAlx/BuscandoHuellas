
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.users (id, email, role, password_hash)
    values (new.id, new.email, 'USER', '')
    on conflict (id) do nothing;
    return new;
end;
$$;

revoke execute on function public.handle_new_user() from public, anon, authenticated;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row
    execute function public.handle_new_user();

insert into public.users (id, email, role, password_hash)
select au.id, au.email, 'USER', ''
from auth.users au
left join public.users pu on pu.id = au.id
where pu.id is null
  and au.email is not null;

alter table public.users enable row level security;

drop policy if exists "users_select_own" on public.users;
create policy "users_select_own"
    on public.users
    for select
    to authenticated
    using (auth.uid() = id);

drop policy if exists "users_update_own" on public.users;
create policy "users_update_own"
    on public.users
    for update
    to authenticated
    using (auth.uid() = id)
    with check (auth.uid() = id);
