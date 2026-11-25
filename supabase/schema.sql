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
