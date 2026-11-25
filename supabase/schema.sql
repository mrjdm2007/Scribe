-- Scribe cross-device record sync
-- Run this migration in the Supabase SQL editor for your project.
create table if not exists public.scribe_cloud_items (
    user_id uuid not null references auth.users(id) on delete cascade,
    cloud_id uuid not null,
    payload jsonb not null,
    updated_at bigint not null,
    primary key (user_id, cloud_id)
);

alter table public.scribe_cloud_items enable row level security;

drop policy if exists "Users can read their own Scribe records" on public.scribe_cloud_items;
create policy "Users can read their own Scribe records"
    on public.scribe_cloud_items for select
    using (auth.uid() = user_id);

drop policy if exists "Users can insert their own Scribe records" on public.scribe_cloud_items;
create policy "Users can insert their own Scribe records"
    on public.scribe_cloud_items for insert
    with check (auth.uid() = user_id);

drop policy if exists "Users can update their own Scribe records" on public.scribe_cloud_items;
create policy "Users can update their own Scribe records"
    on public.scribe_cloud_items for update
    using (auth.uid() = user_id)
    with check (auth.uid() = user_id);

drop policy if exists "Users can delete their own Scribe records" on public.scribe_cloud_items;
create policy "Users can delete their own Scribe records"
    on public.scribe_cloud_items for delete
    using (auth.uid() = user_id);

create index if not exists scribe_cloud_items_updated_at_idx
    on public.scribe_cloud_items (user_id, updated_at desc);

-- Permanent deletion tombstones prevent another offline device from resurrecting
-- records that were explicitly deleted on a different device.
create table if not exists public.scribe_cloud_tombstones (
    user_id uuid not null references auth.users(id) on delete cascade,
    cloud_id uuid not null,
    deleted_at bigint not null,
    primary key (user_id, cloud_id)
);
alter table public.scribe_cloud_tombstones enable row level security;
drop policy if exists "Users can read their own Scribe tombstones" on public.scribe_cloud_tombstones;
create policy "Users can read their own Scribe tombstones"
    on public.scribe_cloud_tombstones for select using (auth.uid() = user_id);
drop policy if exists "Users can insert their own Scribe tombstones" on public.scribe_cloud_tombstones;
create policy "Users can insert their own Scribe tombstones"
    on public.scribe_cloud_tombstones for insert with check (auth.uid() = user_id);
drop policy if exists "Users can delete their own Scribe tombstones" on public.scribe_cloud_tombstones;
create policy "Users can delete their own Scribe tombstones"
    on public.scribe_cloud_tombstones for delete using (auth.uid() = user_id);

create or replace function public.scribe_delete_item(p_cloud_id uuid, p_deleted_at bigint)
returns void language plpgsql security invoker set search_path = public as $
begin
    insert into public.scribe_cloud_tombstones(user_id, cloud_id, deleted_at)
    values (auth.uid(), p_cloud_id, p_deleted_at)
    on conflict (user_id, cloud_id) do update
      set deleted_at = greatest(public.scribe_cloud_tombstones.deleted_at, excluded.deleted_at);
    delete from public.scribe_cloud_items
      where user_id = auth.uid() and cloud_id = p_cloud_id;
end;
$;
grant execute on function public.scribe_delete_item(uuid, bigint) to authenticated;

-- Atomic last-writer-wins upsert. This prevents an older offline copy from
-- overwriting a newer record during concurrent device syncs.
create or replace function public.scribe_upsert_item(
    p_cloud_id uuid,
    p_payload jsonb,
    p_updated_at bigint
) returns void
language plpgsql
security invoker
set search_path = public
as $$
begin
    -- A permanent-deletion tombstone wins over stale/offline uploads.
    if exists (
        select 1 from public.scribe_cloud_tombstones
        where user_id = auth.uid() and cloud_id = p_cloud_id
    ) then
        return;
    end if;
    insert into public.scribe_cloud_items (user_id, cloud_id, payload, updated_at)
    values (auth.uid(), p_cloud_id, p_payload, p_updated_at)
    on conflict (user_id, cloud_id) do update
       set payload = excluded.payload,
           updated_at = excluded.updated_at
     where excluded.updated_at > public.scribe_cloud_items.updated_at;
end;
$$;

grant execute on function public.scribe_upsert_item(uuid, jsonb, bigint) to authenticated;


-- Private object storage for attachments larger than the JSON sync threshold.
insert into storage.buckets (id, name, public, file_size_limit)
values ('scribe-attachments', 'scribe-attachments', false, 52428800)
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit;

drop policy if exists "Scribe users read own attachments" on storage.objects;
create policy "Scribe users read own attachments" on storage.objects
for select to authenticated
using (bucket_id = 'scribe-attachments' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "Scribe users upload own attachments" on storage.objects;
create policy "Scribe users upload own attachments" on storage.objects
for insert to authenticated
with check (bucket_id = 'scribe-attachments' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "Scribe users update own attachments" on storage.objects;
create policy "Scribe users update own attachments" on storage.objects
for update to authenticated
using (bucket_id = 'scribe-attachments' and (storage.foldername(name))[1] = auth.uid()::text)
with check (bucket_id = 'scribe-attachments' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "Scribe users delete own attachments" on storage.objects;
create policy "Scribe users delete own attachments" on storage.objects
for delete to authenticated
using (bucket_id = 'scribe-attachments' and (storage.foldername(name))[1] = auth.uid()::text);
