-- Hosted patch, 2026-10-23: owner-only web viewer data.
-- Idempotent; apply AFTER 2026-10-09-crests.sql and BEFORE shipping the
-- build that expects schema 30. Definitions are cut from the baseline.

begin;

-- Private web-viewer data never participates in social or ranking paths.
create table if not exists session_set_details (
    user_id uuid not null references profiles(id) on delete cascade,
    session_id uuid not null,
    exercise_name text not null,
    set_index int not null,
    kind text check (kind in ('working', 'warmup', 'top', 'backoff', 'drop', 'failure', 'rest_pause')),
    rpe int check (rpe between 5 and 10),
    superset_group int check (superset_group between 0 and 100),
    primary key (session_id, exercise_name, set_index),
    foreign key (session_id, exercise_name, set_index)
        references session_sets (session_id, exercise_name, set_index) on delete cascade
);

create table if not exists imported_sessions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references profiles(id) on delete cascade,
    local_id bigint not null,
    label text not null check (char_length(label) <= 80),
    title text not null default '' check (char_length(title) <= 80),
    note text not null default '' check (char_length(note) <= 500),
    started_at timestamptz not null,
    completed_at timestamptz,
    edited_at timestamptz,
    unique (user_id, local_id)
);

create table if not exists imported_session_sets (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references profiles(id) on delete cascade,
    session_id uuid not null references imported_sessions(id) on delete cascade,
    exercise_name text check (char_length(exercise_name) <= 64),
    exercise_position int,
    set_index int,
    reps int check (reps >= 0),
    weight_kg numeric(6,2),
    modifiers text check (char_length(modifiers) <= 64),
    done boolean,
    duration_sec int,
    distance_m numeric(9,2),
    grade text check (char_length(grade) <= 12),
    kind text check (kind in ('working', 'warmup', 'top', 'backoff', 'drop', 'failure', 'rest_pause')),
    rpe int check (rpe between 5 and 10),
    superset_group int check (superset_group between 0 and 100),
    unique (session_id, exercise_name, set_index)
);

create table if not exists web_snapshots (
    user_id uuid primary key references auth.users(id) on delete cascade,
    version int not null check (version >= 1),
    snapshot jsonb not null,
    content_hash text not null,
    size_bytes int not null,
    updated_at timestamptz not null default now(),
    constraint web_snapshots_size_cap check (
        size_bytes > 0 and size_bytes <= 2 * 1024 * 1024
        and octet_length(snapshot::text) <= 2 * 1024 * 1024
    )
);

alter table session_set_details enable row level security;
alter table imported_sessions enable row level security;
alter table imported_session_sets enable row level security;
alter table web_snapshots enable row level security;

-- Foreign keys bypass RLS; check ownership of each referenced parent explicitly.
drop policy if exists session_set_details_owner on session_set_details;
create policy session_set_details_owner on session_set_details
    for all to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid() and exists (
        select 1 from sessions s
        where s.id = session_set_details.session_id and s.user_id = auth.uid()
    ));

drop policy if exists imported_sessions_owner on imported_sessions;
create policy imported_sessions_owner on imported_sessions
    for all to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists imported_session_sets_owner on imported_session_sets;
create policy imported_session_sets_owner on imported_session_sets
    for all to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid() and exists (
        select 1 from imported_sessions s
        where s.id = imported_session_sets.session_id and s.user_id = auth.uid()
    ));

drop policy if exists web_snapshots_read on web_snapshots;
create policy web_snapshots_read on web_snapshots
    for select to authenticated using (user_id = auth.uid());

drop policy if exists web_snapshots_insert on web_snapshots;
create policy web_snapshots_insert on web_snapshots
    for insert to authenticated with check (user_id = auth.uid());

drop policy if exists web_snapshots_update on web_snapshots;
create policy web_snapshots_update on web_snapshots
    for update to authenticated using (user_id = auth.uid())
    with check (user_id = auth.uid());

revoke all on session_set_details, imported_sessions, imported_session_sets, web_snapshots from public, anon, authenticated;
grant select, insert, update, delete on session_set_details, imported_sessions, imported_session_sets to authenticated;
grant select, insert, update on web_snapshots to authenticated;

create or replace function public.schema_version() returns int
language sql stable as $$ select 30 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon, authenticated;

commit;
