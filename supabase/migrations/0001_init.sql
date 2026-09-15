-- Monarch v1.5 backend: friend leaderboards and shared session history.
-- Immutable: already applied to the live project. Never edit; land changes
-- in a later migration instead.
--
-- Privacy model, deliberately narrow:
--   * Body measurements (weight, height, body fat, BMI/FFMI) NEVER leave the
--     device. There is no table for them here on purpose.
--   * Aggregates (level, XP, streak, titles) are shareable.
--   * Session detail is friends-only by default.
-- Every table is row-level secured; the anon/publishable key is public, so RLS
-- is the only thing standing between accounts.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------- profiles

create type profile_visibility as enum ('public', 'friends', 'private');

create table profiles (
    id                 uuid primary key references auth.users (id) on delete cascade,
    display_name       text not null check (char_length(trim(display_name)) between 2 and 24),
    visibility         profile_visibility not null default 'friends',
    level              int    not null default 1 check (level >= 1),
    total_xp           bigint not null default 0 check (total_xp >= 0),
    streak_days        int    not null default 0 check (streak_days >= 0),
    titles_count       int    not null default 0 check (titles_count >= 0),
    lifetime_strength  bigint not null default 0 check (lifetime_strength >= 0),
    updated_at         timestamptz not null default now()
);

-- Display names are how friends find each other, so they must be unique, but
-- case-insensitively: "Monarch" and "monarch" are the same handle.
create unique index profiles_display_name_key on profiles (lower(display_name));

-- ---------------------------------------------------------------- friendships

-- One row per pair, ordered so (a,b) and (b,a) cannot both exist.
create table friendships (
    requester_id uuid not null references profiles (id) on delete cascade,
    addressee_id uuid not null references profiles (id) on delete cascade,
    accepted     boolean not null default false,
    created_at   timestamptz not null default now(),
    primary key (requester_id, addressee_id),
    constraint no_self_friendship check (requester_id <> addressee_id)
);

create index friendships_addressee_idx on friendships (addressee_id);

-- ---------------------------------------------------------------- sessions

create table sessions (
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid not null references profiles (id) on delete cascade,
    -- Room's local row id, so a re-sync updates instead of duplicating.
    local_id         bigint not null,
    label            text not null,
    started_at       timestamptz not null,
    completed_at     timestamptz,
    xp_awarded       int not null default 0,
    strength_score   int not null default 0,
    updated_at       timestamptz not null default now(),
    unique (user_id, local_id)
);

create index sessions_user_completed_idx on sessions (user_id, completed_at desc);

create table session_sets (
    id             uuid primary key default gen_random_uuid(),
    session_id     uuid not null references sessions (id) on delete cascade,
    exercise_name  text not null,
    set_index      int  not null,
    reps           int  not null check (reps >= 0),
    weight_kg      numeric(6, 2),
    modifiers      text not null default '',
    done           boolean not null default false,
    unique (session_id, exercise_name, set_index)
);

-- ---------------------------------------------------------------- titles

create table earned_titles (
    user_id     uuid not null references profiles (id) on delete cascade,
    title_id    text not null,
    unlocked_at timestamptz not null,
    primary key (user_id, title_id)
);

-- ---------------------------------------------------------------- visibility

-- Accepted friendship in either direction.
create or replace function is_friend(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from friendships f
        where f.accepted
          and ((f.requester_id = a and f.addressee_id = b)
            or (f.requester_id = b and f.addressee_id = a))
    );
$$;

-- Whether the caller may read [owner]'s shared training data.
create or replace function can_view(owner uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select owner = auth.uid()
        or exists (
            select 1 from profiles p
            where p.id = owner
              and (p.visibility = 'public'
                or (p.visibility = 'friends' and is_friend(auth.uid(), owner)))
        );
$$;

-- ---------------------------------------------------------------- RLS

alter table profiles      enable row level security;
alter table friendships   enable row level security;
alter table sessions      enable row level security;
alter table session_sets  enable row level security;
alter table earned_titles enable row level security;

-- profiles: readable per visibility, writable only by the owner.
create policy profiles_read on profiles
    for select to authenticated
    using (can_view(id));

create policy profiles_insert on profiles
    for insert to authenticated
    with check (id = auth.uid());

create policy profiles_update on profiles
    for update to authenticated
    using (id = auth.uid())
    with check (id = auth.uid());

create policy profiles_delete on profiles
    for delete to authenticated
    using (id = auth.uid());

-- friendships: either party can see the row; only the requester creates it,
-- and only the addressee can accept it.
create policy friendships_read on friendships
    for select to authenticated
    using (requester_id = auth.uid() or addressee_id = auth.uid());

create policy friendships_insert on friendships
    for insert to authenticated
    with check (requester_id = auth.uid() and not accepted);

create policy friendships_accept on friendships
    for update to authenticated
    using (addressee_id = auth.uid())
    with check (addressee_id = auth.uid());

create policy friendships_delete on friendships
    for delete to authenticated
    using (requester_id = auth.uid() or addressee_id = auth.uid());

-- sessions and sets: read per visibility, write only your own.
create policy sessions_read on sessions
    for select to authenticated
    using (can_view(user_id));

create policy sessions_write on sessions
    for all to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

create policy session_sets_read on session_sets
    for select to authenticated
    using (exists (select 1 from sessions s where s.id = session_id and can_view(s.user_id)));

create policy session_sets_write on session_sets
    for all to authenticated
    using (exists (select 1 from sessions s where s.id = session_id and s.user_id = auth.uid()))
    with check (exists (select 1 from sessions s where s.id = session_id and s.user_id = auth.uid()));

create policy titles_read on earned_titles
    for select to authenticated
    using (can_view(user_id));

create policy titles_write on earned_titles
    for all to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- ---------------------------------------------------------------- leaderboard

-- security_invoker makes the view obey the caller's RLS, so a leaderboard can
-- never leak a profile the caller is not allowed to read.
create view leaderboard with (security_invoker = true) as
select
    p.id,
    p.display_name,
    p.level,
    p.total_xp,
    p.streak_days,
    p.titles_count,
    p.lifetime_strength,
    (select count(*) from sessions s
      where s.user_id = p.id
        and s.completed_at > now() - interval '7 days') as sessions_last_7d
from profiles p;

-- Keeps updated_at honest without the client having to remember.
create or replace function touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger profiles_touch before update on profiles
    for each row execute function touch_updated_at();

create trigger sessions_touch before update on sessions
    for each row execute function touch_updated_at();
