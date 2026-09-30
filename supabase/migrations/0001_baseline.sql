-- 0001_baseline.sql
-- Ironvellum's whole Supabase schema, as ONE file, for a freshly reset project.
-- Paste order on the hosted project: supabase/reset.sql, then this file.
--
-- Idempotent: every statement guards itself (create ... if not exists, create
-- or replace, drop policy/trigger if exists), so re-applying it to a project
-- that already carries this exact schema is a no-op. It does NOT upgrade an
-- older shape: it replaces the eighteen incremental migrations this project
-- used to carry, and the cloud is disposable debug data (the device's Room
-- database is the record), so a schema change is made by editing THIS file,
-- bumping the schema_version() literal at the bottom together with
-- app/src/main/kotlin/com/ironvellum/app/data/cloud/Cloud.kt
-- (NEEDED_SCHEMA_VERSION), and running reset.sql + this file again.
--
-- Privacy model, deliberately narrow:
--   * Body measurements (weight, height, body fat, BMI/FFMI) NEVER leave the
--     device. There is no table for them here on purpose.
--   * Private session notes NEVER leave the device either: "private" has to
--     mean the server cannot read it, not that the UI hides it.
--   * Aggregates (level, XP, streak, titles) are shareable.
--   * Session detail is friends-only by default.
-- Every table is row-level secured; the anon/publishable key is public, so RLS
-- is the only thing standing between accounts.
--
-- Supabase grants every table privilege and EXECUTE on every new public
-- function DIRECTLY to anon and authenticated (default privileges), and a
-- revoke from PUBLIC does not touch a direct grant. So every revoke below
-- names public, anon[, authenticated] explicitly, and a column a client may
-- not write is taken back with a table-level revoke before the writable
-- columns are granted.
--
-- Nothing outside the public schema is created except the sign-up trigger on
-- auth.users at the very end. No storage buckets or objects are used.
--
-- Layout: types, tables, helper functions, row security, column grants,
-- triggers, views, RPCs, sign-up trigger.

create extension if not exists pgcrypto;

-- ================================================================ types

do $$
begin
    if not exists (
        select 1 from pg_type t
        join pg_namespace n on n.oid = t.typnamespace
        where n.nspname = 'public' and t.typname = 'profile_visibility'
    ) then
        create type public.profile_visibility as enum ('public', 'friends', 'private');
    end if;
end $$;

-- ================================================================ tables

-- ---------------------------------------------------------------- profiles
-- Rows are created ONLY by handle_new_user() on sign-up (see the bottom of the
-- file): clients hold no INSERT on this table.
create table if not exists profiles (
    id                 uuid primary key references auth.users (id) on delete cascade,
    display_name       text not null check (char_length(trim(display_name)) between 2 and 24),
    visibility         profile_visibility not null default 'friends',
    -- The worn title travels with a hunter, so the feed and board can show it.
    current_title_id   text,
    level              int    not null default 1 check (level >= 1),
    total_xp           bigint not null default 0 check (total_xp >= 0),
    streak_days        int    not null default 0 check (streak_days >= 0),
    titles_count       int    not null default 0 check (titles_count >= 0),
    lifetime_strength  bigint not null default 0 check (lifetime_strength >= 0),
    -- The Shadow Army board is separate from the training board on purpose:
    -- idle progress must never rank beside strength. The idle state itself
    -- stays on the device, only these shareable aggregates are pushed.
    shadow_essence     bigint not null default 0 check (shadow_essence >= 0),
    shadow_count       int    not null default 0 check (shadow_count >= 0),
    -- Derived from training, so it doubles as "how hard is this hunter working".
    shadow_rate        double precision not null default 0 check (shadow_rate >= 0),
    updated_at         timestamptz not null default now()
);

-- Display names are how friends find each other, so they must be unique, but
-- case-insensitively: "Monarch" and "monarch" are the same handle.
create unique index if not exists profiles_display_name_key on profiles (lower(display_name));

-- ---------------------------------------------------------------- friendships
-- One row per unordered pair (friendships_pair_uniq): (a,b) and (b,a) cannot
-- both exist, or a delete would remove only one side and duplicates could pile up.
create table if not exists friendships (
    requester_id uuid not null references profiles (id) on delete cascade,
    addressee_id uuid not null references profiles (id) on delete cascade,
    accepted     boolean not null default false,
    created_at   timestamptz not null default now(),
    -- Stamped by triggers, never by the client.
    accepted_at  timestamptz,
    primary key (requester_id, addressee_id),
    constraint no_self_friendship check (requester_id <> addressee_id)
);

create index if not exists friendships_addressee_idx on friendships (addressee_id);
create unique index if not exists friendships_pair_uniq
    on friendships (least(requester_id, addressee_id), greatest(requester_id, addressee_id));

-- Counting live friendships rows would let a lifter send, cancel and resend
-- forever, each one a fresh request in someone's inbox. This log survives the
-- cancel. It is pruned to the last day per requester on every request, so it
-- holds at most 20 rows per lifter: it grows with people, not with time.
create table if not exists friend_request_log (
    requester_id uuid not null references profiles (id) on delete cascade,
    sent_at      timestamptz not null default now()
);
create index if not exists friend_request_log_idx on friend_request_log (requester_id, sent_at);

-- ---------------------------------------------------------------- sessions
-- Private notes are deliberately absent from this table. They live only in the
-- device's Room database and are never uploaded — "private" has to mean the
-- server cannot read it, not that the UI hides it.
create table if not exists sessions (
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid not null references profiles (id) on delete cascade,
    -- Room's local row id, so a re-sync updates instead of duplicating.
    local_id         bigint not null,
    -- label surfaces as the feed headline fallback, so it is bounded like title:
    -- an unbounded free-text column floods every reader's feed page.
    label            text not null,
    title            text not null default '' check (char_length(title) <= 80),
    note             text not null default '' check (char_length(note) <= 500),
    started_at       timestamptz not null,
    completed_at     timestamptz,
    xp_awarded       int not null default 0,
    strength_score   int not null default 0,
    -- 'profile' follows the profile visibility. 'friends' narrows a public
    -- profile to allies. 'private' is owner-only. The stricter of profile and
    -- workout wins.
    audience         text not null default 'profile',
    updated_at       timestamptz not null default now(),
    unique (user_id, local_id),
    constraint sessions_label_len check (char_length(label) <= 80),
    constraint sessions_audience_check check (audience in ('profile', 'friends', 'private'))
);

create index if not exists sessions_user_completed_idx on sessions (user_id, completed_at desc);
-- The feed orders by recency across ALL users, so the per-user index is no help.
create index if not exists sessions_completed_idx on sessions (completed_at desc)
    where completed_at is not null;

-- Sets carry optional duration/distance/grade so a hold, a 5 km run and a V4
-- problem can be recorded without lying about reps. A hold is reps = 0 with a
-- positive duration_sec.
create table if not exists session_sets (
    id             uuid primary key default gen_random_uuid(),
    session_id     uuid not null references sessions (id) on delete cascade,
    exercise_name  text not null,
    set_index      int  not null,
    reps           int  not null check (reps >= 0),
    weight_kg      numeric(6, 2),
    modifiers      text not null default '',
    done           boolean not null default false,
    duration_sec   int check (duration_sec is null or duration_sec >= 0),
    distance_m     numeric(9, 2) check (distance_m is null or distance_m >= 0),
    -- Free text on purpose: grade systems differ (V4, 6C+, 5.11a) and forcing
    -- one would make the app wrong for half its users.
    grade          text check (grade is null or char_length(grade) <= 12),
    unique (session_id, exercise_name, set_index),
    -- exercise_name is aggregated into public_feed.top_movements by string_agg,
    -- so a multi-megabyte name would be re-serialised into EVERY reader's feed.
    constraint session_sets_name_len check (char_length(exercise_name) <= 64),
    constraint session_sets_mods_len check (char_length(modifiers) <= 64)
);

-- The order of the exercise within its workout, sent by the client so an ally
-- viewing the workout sees exercises as they were performed. Without it the
-- viewer can only order by name and a push day reads alphabetically. Nullable:
-- a 1.4 client (and every row written before this column) sends none, and a
-- required column would refuse its whole set insert.
alter table session_sets
    add column if not exists exercise_position int
        check (exercise_position is null or exercise_position between 0 and 500);

-- ---------------------------------------------------------------- titles, levels
create table if not exists earned_titles (
    user_id     uuid not null references profiles (id) on delete cascade,
    title_id    text not null,
    unlocked_at timestamptz not null,
    primary key (user_id, title_id),
    constraint earned_titles_id_len check (char_length(title_id) <= 64)
);

-- The app only ever stores CURRENT xp, so once a threshold is passed the moment
-- cannot be derived after the fact: level-ups need their own table.
create table if not exists level_ups (
    user_id    uuid not null references profiles (id) on delete cascade,
    level      int  not null check (level > 1),
    reached_at timestamptz not null,
    primary key (user_id, level)
);

-- ---------------------------------------------------------------- reactions, comments
create table if not exists session_likes (
    session_id uuid not null references sessions (id) on delete cascade,
    user_id    uuid not null references profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    -- A like inserted without a kind (a 1.3 client) still lands as a 'salute'.
    kind       text not null default 'salute',
    primary key (session_id, user_id),
    constraint session_likes_kind_check check (kind in ('salute', 'iron', 'flame'))
);

create index if not exists session_likes_session_idx on session_likes (session_id);

create table if not exists session_comments (
    id          uuid primary key default gen_random_uuid(),
    session_id  uuid not null references sessions (id) on delete cascade,
    user_id     uuid not null references profiles (id) on delete cascade,
    author_name text not null default '',
    body        text not null,
    created_at  timestamptz not null default now(),
    constraint session_comments_body_len
        check (char_length(btrim(body, E' \t\r\n')) between 1 and 280)
);

create index if not exists session_comments_session_idx on session_comments (session_id, created_at);
create index if not exists session_comments_user_idx on session_comments (user_id, created_at);

-- ---------------------------------------------------------------- blocks, mutes
-- The name is stored because a block makes the blocked profile unreadable
-- (can_view below), and the BLOCKED list still has to say who it is.
create table if not exists blocks (
    blocker_id   uuid not null references profiles (id) on delete cascade,
    blocked_id   uuid not null references profiles (id) on delete cascade,
    blocked_name text not null default '',
    created_at   timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    constraint blocks_not_self check (blocker_id <> blocked_id)
);
-- blocked_between() looks the pair up in both directions, the PK covers one.
create index if not exists blocks_blocked_idx on blocks (blocked_id, blocker_id);

create table if not exists mutes (
    muter_id   uuid not null references profiles (id) on delete cascade,
    muted_id   uuid not null references profiles (id) on delete cascade,
    muted_name text not null default '',
    created_at timestamptz not null default now(),
    primary key (muter_id, muted_id),
    constraint mutes_not_self check (muter_id <> muted_id)
);
create index if not exists mutes_muted_idx on mutes (muted_id);

-- ---------------------------------------------------------------- inbox, reports
-- The inbox is derived (my_inbox below), never stored: this is the only row it
-- costs per lifter.
create table if not exists inbox_seen (
    user_id uuid primary key references profiles (id) on delete cascade,
    seen_at timestamptz not null default now()
);

-- Evidence for the owner, read only with the service role in the dashboard
-- (supabase/moderation.sql). session_id and comment_id are set null when the
-- thing is deleted, and excerpt keeps what was said, so deleting the comment
-- does not delete the evidence.
create table if not exists reports (
    id             uuid primary key default gen_random_uuid(),
    reporter_id    uuid not null references profiles (id) on delete cascade,
    target_user_id uuid not null references profiles (id) on delete cascade,
    session_id     uuid references sessions (id) on delete set null,
    comment_id     uuid references session_comments (id) on delete set null,
    reason         text not null,
    note           text not null default '',
    excerpt        text not null default '',
    status         text not null default 'open',
    created_at     timestamptz not null default now(),
    constraint reports_reason_check check (reason in ('spam', 'abuse', 'cheating', 'other')),
    constraint reports_note_len check (char_length(note) <= 280),
    constraint reports_status_check check (status in ('open', 'actioned', 'dismissed')),
    constraint reports_not_self check (reporter_id <> target_user_id)
);

create index if not exists reports_reporter_idx on reports (reporter_id, created_at);
create index if not exists reports_target_idx on reports (target_user_id);

-- ---------------------------------------------------------------- cloud backup
-- One row per hunter holding that hunter's whole save as a JSON export
-- archive, so a lost phone does not mean a lost training history.
--
-- The uploaded archive DELIBERATELY omits the private note. Nothing about this
-- table is shareable: the only policies are owner-keyed. There is no delete
-- policy on purpose: deleting a backup is wiping the only cloud copy of a
-- history, the client can overwrite the row with a newer archive, and account
-- deletion cascades the row via the auth.users foreign key.
create table if not exists cloud_archives (
    user_id    uuid primary key references auth.users (id) on delete cascade,
    archive    text not null,
    size_bytes int  not null,
    updated_at timestamptz not null default now(),
    -- 8 MiB covers years of training. When a real archive outgrows this the
    -- write fails loudly rather than silently truncating a partial save.
    constraint cloud_archives_size_cap check (size_bytes > 0 and size_bytes <= 8 * 1024 * 1024)
);

-- One row per lifter per lift: the bodyweight-relative TIER step, worked out on
-- the phone. Only the coarse step integer leaves the device, never bodyweight or
-- a ratio, so an ally cannot back-solve a lifter's weight from a public set
-- load. recent_step is the best qualifying set of the last 7 days and recent_at
-- says when, so the board can show who is on form without a second table.
create table if not exists lift_marks (
    user_id     uuid not null references profiles (id) on delete cascade,
    lift        text not null,
    step        int  not null,
    recent_step int,
    recent_at   timestamptz,
    updated_at  timestamptz not null default now(),
    primary key (user_id, lift),
    constraint lift_marks_lift check (lift in ('pull_up', 'dip', 'squat', 'bench', 'deadlift', 'ohp')),
    constraint lift_marks_step check (step between 0 and 10),
    constraint lift_marks_recent_step check (recent_step is null or recent_step between 0 and 10)
);

-- ================================================================ visibility functions

-- Accepted friendship in either direction. SECURITY DEFINER because it must
-- bypass RLS on friendships, or can_view() would recurse on profiles. Only ever
-- called from inside can_view()'s definer body, so no client role needs it: it
-- would otherwise be an oracle over the whole accepted friendship graph.
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
revoke execute on function public.is_friend(uuid, uuid) from public, anon, authenticated;

-- An oracle for who blocked whom if a client could call it, so it is only
-- ever evaluated inside the SECURITY DEFINER bodies below.
create or replace function public.blocked_between(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    select exists (
        select 1 from blocks bl
        where (bl.blocker_id = a and bl.blocked_id = b)
           or (bl.blocker_id = b and bl.blocked_id = a)
    );
$$;
revoke execute on function public.blocked_between(uuid, uuid) from public, anon, authenticated;

-- Whether the caller may read [owner]'s shared training data: themselves, or a
-- profile that is public / friends-only-and-allied, and never across a block.
-- Profiles, titles, level-ups and both boards read through it, so a block hides
-- all of them at once. Evaluated by the read policies as the querying role,
-- which is always authenticated.
create or replace function public.can_view(owner uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select owner = auth.uid()
        or (
            not blocked_between(auth.uid(), owner)
            and exists (
                select 1 from profiles p
                where p.id = owner
                  and (p.visibility = 'public'
                    or (p.visibility = 'friends' and is_friend(auth.uid(), owner)))
            )
        );
$$;
revoke execute on function public.can_view(uuid) from public, anon;
grant execute on function public.can_view(uuid) to authenticated;

-- The effective audience is the stricter of the profile and the workout.
create or replace function public.can_view_session(owner uuid, audience text)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    select owner = auth.uid()
        or (
            audience <> 'private'
            and can_view(owner)
            and (audience <> 'friends' or is_friend(auth.uid(), owner))
        );
$$;
revoke execute on function public.can_view_session(uuid, text) from public, anon;
grant execute on function public.can_view_session(uuid, text) to authenticated;

-- Answers only about the caller: whether auth.uid() may see what [author]
-- wrote. Mutes are owner-only under RLS and blocks go both ways, so the policy
-- needs a definer to see the other side of a block.
create or replace function public.can_see_author(author uuid)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    select author = auth.uid()
        or (
            not blocked_between(auth.uid(), author)
            and not exists (
                select 1 from mutes m
                where m.muter_id = auth.uid() and m.muted_id = author
            )
        );
$$;
revoke execute on function public.can_see_author(uuid) from public, anon;
grant execute on function public.can_see_author(uuid) to authenticated;

-- Whether [other] is an accepted ally of the caller and neither has blocked the
-- other. is_friend() stays closed because it answers about ANY pair of users (an
-- oracle over the whole friendship graph); this one only ever answers about
-- auth.uid(), so a lifter learns nothing they could not already read from their
-- own friendships rows. lift_marks_read needs it: friendships is RLS-limited to
-- the two parties, and a block is invisible to the blocked side.
create or replace function public.is_ally(other uuid)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    select auth.uid() is not null
        and is_friend(auth.uid(), other)
        and not blocked_between(auth.uid(), other);
$$;
revoke execute on function public.is_ally(uuid) from public, anon;
grant execute on function public.is_ally(uuid) to authenticated;

-- ================================================================ row security

alter table profiles           enable row level security;
alter table friendships        enable row level security;
alter table friend_request_log enable row level security;
alter table sessions           enable row level security;
alter table session_sets       enable row level security;
alter table earned_titles      enable row level security;
alter table level_ups          enable row level security;
alter table session_likes      enable row level security;
alter table session_comments   enable row level security;
alter table blocks             enable row level security;
alter table mutes              enable row level security;
alter table inbox_seen         enable row level security;
alter table reports            enable row level security;
alter table cloud_archives     enable row level security;
alter table lift_marks         enable row level security;

-- profiles: readable per visibility, writable only by the owner. There is NO
-- insert policy: the row is created server-side on sign-up, never by a client.
drop policy if exists profiles_read on profiles;
create policy profiles_read on profiles
    for select to authenticated
    using (can_view(id));

drop policy if exists profiles_update on profiles;
create policy profiles_update on profiles
    for update to authenticated
    using (id = auth.uid())
    with check (id = auth.uid());

drop policy if exists profiles_delete on profiles;
create policy profiles_delete on profiles
    for delete to authenticated
    using (id = auth.uid());

-- friendships: either party can see the row, only the requester creates it,
-- and only the addressee can accept it. An addressee may flip `accepted` and
-- nothing else: a with-check only sees the NEW row, so the identity columns
-- are pinned by friendships_guard_update below.
drop policy if exists friendships_read on friendships;
create policy friendships_read on friendships
    for select to authenticated
    using (requester_id = auth.uid() or addressee_id = auth.uid());

drop policy if exists friendships_insert on friendships;
create policy friendships_insert on friendships
    for insert to authenticated
    with check (requester_id = auth.uid() and not accepted);

drop policy if exists friendships_accept on friendships;
create policy friendships_accept on friendships
    for update to authenticated
    using (addressee_id = auth.uid())
    with check (addressee_id = auth.uid());

drop policy if exists friendships_delete on friendships;
create policy friendships_delete on friendships
    for delete to authenticated
    using (requester_id = auth.uid() or addressee_id = auth.uid());

-- friend_request_log: no policy and no grant, only its trigger touches it.

-- sessions and sets: read per the effective audience (can_view_session, or a
-- 'private' workout would still be served to every ally), write only your own.
drop policy if exists sessions_read on sessions;
create policy sessions_read on sessions
    for select to authenticated
    using (can_view_session(user_id, audience));

drop policy if exists sessions_write on sessions;
create policy sessions_write on sessions
    for all to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists session_sets_read on session_sets;
create policy session_sets_read on session_sets
    for select to authenticated
    using (exists (select 1 from sessions s
                   where s.id = session_id and can_view_session(s.user_id, s.audience)));

drop policy if exists session_sets_write on session_sets;
create policy session_sets_write on session_sets
    for all to authenticated
    using (exists (select 1 from sessions s where s.id = session_id and s.user_id = auth.uid()))
    with check (exists (select 1 from sessions s where s.id = session_id and s.user_id = auth.uid()));

drop policy if exists titles_read on earned_titles;
create policy titles_read on earned_titles
    for select to authenticated
    using (can_view(user_id));

drop policy if exists titles_write on earned_titles;
create policy titles_write on earned_titles
    for all to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists level_ups_read on level_ups;
create policy level_ups_read on level_ups
    for select to authenticated
    using (can_view(user_id));

drop policy if exists level_ups_write on level_ups;
create policy level_ups_write on level_ups
    for all to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- Reactions: a reaction by a lifter the reader blocked or muted is not shown
-- to them, the same rule as their comments. You may only add YOUR OWN, on a
-- session you can see.
drop policy if exists session_likes_read on session_likes;
create policy session_likes_read on session_likes
    for select to authenticated
    using (
        exists (select 1 from sessions s
                where s.id = session_id and can_view_session(s.user_id, s.audience))
        and can_see_author(user_id)
    );

drop policy if exists session_likes_insert on session_likes;
create policy session_likes_insert on session_likes
    for insert to authenticated
    with check (
        user_id = auth.uid()
        and exists (select 1 from sessions s
                    where s.id = session_id and can_view_session(s.user_id, s.audience))
    );

-- Changing the kind is an upsert (on conflict do update), and PostgREST's
-- upsert SETs every sent column, session_id and user_id included, so a column
-- grant cannot narrow it. session_likes_guard below keeps identity fixed.
drop policy if exists session_likes_update on session_likes;
create policy session_likes_update on session_likes
    for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists session_likes_delete on session_likes;
create policy session_likes_delete on session_likes
    for delete to authenticated
    using (user_id = auth.uid());

drop policy if exists session_comments_read on session_comments;
create policy session_comments_read on session_comments
    for select to authenticated
    using (
        exists (select 1 from sessions s
                where s.id = session_id and can_view_session(s.user_id, s.audience))
        and can_see_author(user_id)
    );

drop policy if exists session_comments_insert on session_comments;
create policy session_comments_insert on session_comments
    for insert to authenticated
    with check (
        user_id = auth.uid()
        and exists (select 1 from sessions s
                    where s.id = session_id and can_view_session(s.user_id, s.audience))
    );

-- The author, or the owner of the workout it sits on: a lifter moderates
-- their own page without asking the owner of the app.
drop policy if exists session_comments_delete on session_comments;
create policy session_comments_delete on session_comments
    for delete to authenticated
    using (
        user_id = auth.uid()
        or exists (select 1 from sessions s where s.id = session_id and s.user_id = auth.uid())
    );

-- Blocks and mutes are owner-only in every direction: the blocked lifter must
-- never learn who blocked them, and a mute is silent by design.
drop policy if exists blocks_read on blocks;
create policy blocks_read on blocks
    for select to authenticated using (blocker_id = auth.uid());

drop policy if exists blocks_insert on blocks;
create policy blocks_insert on blocks
    for insert to authenticated with check (blocker_id = auth.uid());

drop policy if exists blocks_delete on blocks;
create policy blocks_delete on blocks
    for delete to authenticated using (blocker_id = auth.uid());

drop policy if exists mutes_read on mutes;
create policy mutes_read on mutes
    for select to authenticated using (muter_id = auth.uid());

drop policy if exists mutes_insert on mutes;
create policy mutes_insert on mutes
    for insert to authenticated with check (muter_id = auth.uid());

drop policy if exists mutes_delete on mutes;
create policy mutes_delete on mutes
    for delete to authenticated using (muter_id = auth.uid());

drop policy if exists inbox_seen_read on inbox_seen;
create policy inbox_seen_read on inbox_seen
    for select to authenticated using (user_id = auth.uid());

drop policy if exists reports_insert on reports;
create policy reports_insert on reports
    for insert to authenticated with check (reporter_id = auth.uid());

-- One row per hunter, always the latest. Upsert on user_id, so insert AND
-- update both need `with check`: an update policy without it would let a row
-- be rewritten under another user's id. No delete policy (see the table
-- comment) and no anon policy: an archive is private to its owner.
drop policy if exists cloud_archives_read on cloud_archives;
create policy cloud_archives_read on cloud_archives
    for select to authenticated
    using (user_id = auth.uid());

drop policy if exists cloud_archives_insert on cloud_archives;
create policy cloud_archives_insert on cloud_archives
    for insert to authenticated
    with check (user_id = auth.uid());

drop policy if exists cloud_archives_update on cloud_archives;
create policy cloud_archives_update on cloud_archives
    for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- lift_marks: your own, plus an accepted ally's. Deliberately NOT can_view():
-- a public profile is not an ally, and strength tiers are an allies-only board.
-- Writes are yours alone; the with-check stops a row being rewritten under
-- another lifter's id.
drop policy if exists lift_marks_read on lift_marks;
create policy lift_marks_read on lift_marks
    for select to authenticated
    using (user_id = auth.uid() or is_ally(user_id));

drop policy if exists lift_marks_insert on lift_marks;
create policy lift_marks_insert on lift_marks
    for insert to authenticated
    with check (user_id = auth.uid());

drop policy if exists lift_marks_update on lift_marks;
create policy lift_marks_update on lift_marks
    for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists lift_marks_delete on lift_marks;
create policy lift_marks_delete on lift_marks
    for delete to authenticated
    using (user_id = auth.uid());

-- ================================================================ column grants

-- profiles: RLS and column privileges are ANDed. The client loses every ranked
-- column (level, xp, streak, titles, strength, shadow figures: push_aggregates
-- below is the one way in) and keeps only what a hunter genuinely chooses. The
-- table-level UPDATE has to go first, or a column-level revoke would not bite.
-- INSERT goes entirely, for anon and authenticated: a profile is created only
-- by handle_new_user(), which also makes sign-up work while email confirmation
-- is on (there is no session yet, so a client insert would run as anon).
revoke update on profiles from authenticated;
grant update (display_name, visibility, current_title_id) on profiles to authenticated;
revoke insert on profiles from anon, authenticated;

-- blocks and mutes: no update at all (a block is added or removed, never
-- edited), and the stored name is the server's, or blocked_name could be set
-- to anything the blocker likes.
revoke all on blocks from anon, authenticated;
grant select, delete on blocks to authenticated;
grant insert (blocker_id, blocked_id) on blocks to authenticated;

revoke all on mutes from anon, authenticated;
grant select, delete on mutes to authenticated;
grant insert (muter_id, muted_id) on mutes to authenticated;

-- session_comments: no update policy and no update grant, a comment is posted
-- or deleted. Insert names only what the lifter chooses: author_name and
-- created_at are the server's, or a comment could be signed with an ally's name.
revoke all on session_comments from anon, authenticated;
grant select, delete on session_comments to authenticated;
grant insert (session_id, user_id, body) on session_comments to authenticated;

-- friend_request_log: closed to clients outright.
revoke all on friend_request_log from anon, authenticated;

-- inbox_seen: written only through mark_inbox_seen(), so seen_at is the
-- server's clock and a skewed phone cannot mark the future as read.
revoke all on inbox_seen from anon, authenticated;
grant select on inbox_seen to authenticated;

-- reports: no select grant at all, a report is invisible to every client,
-- including its author, so an insert must not ask for the row back.
revoke all on reports from anon, authenticated;
grant insert (reporter_id, target_user_id, session_id, comment_id, reason, note)
    on reports to authenticated;

-- lift_marks: updated_at is the server's (touch trigger), so a phone cannot
-- date a mark into the future; only the five columns a lifter owns are writable.
revoke all on lift_marks from anon, authenticated;
grant select, delete on lift_marks to authenticated;
grant insert (user_id, lift, step, recent_step, recent_at) on lift_marks to authenticated;
grant update (user_id, lift, step, recent_step, recent_at) on lift_marks to authenticated;

-- ================================================================ triggers

-- Keeps updated_at honest without the client having to remember.
create or replace function touch_updated_at()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists profiles_touch on profiles;
create trigger profiles_touch before update on profiles
    for each row execute function touch_updated_at();

drop trigger if exists sessions_touch on sessions;
create trigger sessions_touch before update on sessions
    for each row execute function touch_updated_at();

drop trigger if exists lift_marks_touch on lift_marks;
create trigger lift_marks_touch before update on lift_marks
    for each row execute function touch_updated_at();

-- A session dated year 9999 would pin attacker-chosen text to slot one of every
-- user's feed (the client pages with lt(completed_at, ...)) and inflate
-- leaderboard.sessions_last_7d. This cannot be a CHECK: Postgres requires
-- IMMUTABLE functions there and now() is STABLE. A day of slack absorbs client
-- clock skew and timezone error.
create or replace function sessions_no_future_timestamps()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
    if new.started_at > now() + interval '1 day'
       or (new.completed_at is not null and new.completed_at > now() + interval '1 day') then
        raise exception 'session timestamps cannot be in the future';
    end if;
    return new;
end;
$$;

drop trigger if exists sessions_guard_time on sessions;
create trigger sessions_guard_time
    before insert or update on sessions
    for each row execute function sessions_no_future_timestamps();

-- friendships: a with-check predicate only sees the NEW row, so it can never
-- compare against OLD to detect a rewritten identity column. Without this an
-- addressee could rewrite requester_id to any user id with accepted = true and,
-- since is_friend() matches either direction, read a stranger's friends-only
-- profile and sessions. The row identity is immutable.
create or replace function friendships_no_identity_swap()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if new.requester_id is distinct from old.requester_id
       or new.addressee_id is distinct from old.addressee_id then
        raise exception 'friendship rows are immutable except for accepted';
    end if;
    return new;
end;
$$;

drop trigger if exists friendships_guard_update on friendships;
create trigger friendships_guard_update
    before update on friendships
    for each row
    execute function friendships_no_identity_swap();

-- SECURITY DEFINER because the request log and the block lookup are not the
-- caller's to read.
create or replace function public.friendships_before_insert()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    n int;
begin
    if blocked_between(new.requester_id, new.addressee_id) then
        raise exception 'You can’t send an ally request to this lifter.';
    end if;

    delete from friend_request_log l
    where l.requester_id = new.requester_id and l.sent_at <= now() - interval '1 day';
    select count(*) into n from friend_request_log l where l.requester_id = new.requester_id;
    if n >= 20 then
        raise exception 'Too many ally requests today — try again tomorrow.';
    end if;
    insert into friend_request_log (requester_id) values (new.requester_id);

    new.accepted_at := case when new.accepted then now() end;
    return new;
end;
$$;
revoke execute on function public.friendships_before_insert() from public, anon, authenticated;

drop trigger if exists friendships_before_insert on friendships;
create trigger friendships_before_insert
    before insert on friendships
    for each row execute function friendships_before_insert();

-- Runs beside friendships_guard_update, which refuses an identity swap. This
-- one only owns accepted_at, so the addressee cannot backdate or forge the
-- moment they accepted.
create or replace function public.friendships_stamp_accepted()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
    if new.accepted and not old.accepted then
        new.accepted_at := now();
    elsif not new.accepted then
        new.accepted_at := null;
    else
        new.accepted_at := old.accepted_at;
    end if;
    return new;
end;
$$;
revoke execute on function public.friendships_stamp_accepted() from public, anon, authenticated;

drop trigger if exists friendships_stamp_accepted on friendships;
create trigger friendships_stamp_accepted
    before update on friendships
    for each row execute function friendships_stamp_accepted();

-- A with-check only sees the new row, so moving a reaction onto another
-- workout (one the lifter cannot see) would pass it. created_at orders the
-- owner's inbox, so it is the server's clock, and a kind change does not bump
-- the reaction back to the top of that inbox.
create or replace function public.session_likes_guard()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
    if tg_op = 'INSERT' then
        new.created_at := now();
    else
        if new.session_id is distinct from old.session_id
           or new.user_id is distinct from old.user_id then
            raise exception 'reaction rows are immutable except for kind';
        end if;
        new.created_at := old.created_at;
    end if;
    return new;
end;
$$;
revoke execute on function public.session_likes_guard() from public, anon, authenticated;

drop trigger if exists session_likes_guard on session_likes;
create trigger session_likes_guard
    before insert or update on session_likes
    for each row execute function session_likes_guard();

-- SECURITY DEFINER because the limits count rows the author cannot read
-- (comments on workouts that have since gone private), and the name comes
-- from the profile, never the request.
create or replace function public.session_comments_before_insert()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    n int;
begin
    new.body := btrim(new.body, E' \t\r\n');
    new.created_at := now();
    new.author_name := coalesce((select p.display_name from profiles p where p.id = new.user_id), '');

    select count(*) into n from session_comments c
    where c.user_id = new.user_id and c.created_at > now() - interval '1 minute';
    if n >= 10 then
        raise exception 'Too many comments — wait a minute.';
    end if;

    select count(*) into n from session_comments c
    where c.user_id = new.user_id and c.created_at > now() - interval '1 day';
    if n >= 200 then
        raise exception 'Daily comment limit reached — try again tomorrow.';
    end if;

    -- Bounds the one thing a crowd could grow without limit: a single thread.
    select count(*) into n from session_comments c where c.session_id = new.session_id;
    if n >= 200 then
        raise exception 'This workout has reached its comment limit.';
    end if;
    return new;
end;
$$;
revoke execute on function public.session_comments_before_insert() from public, anon, authenticated;

drop trigger if exists session_comments_before_insert on session_comments;
create trigger session_comments_before_insert
    before insert on session_comments
    for each row execute function session_comments_before_insert();

-- A block or mute stores the name at the time and a comment shows the author's
-- name: a rename would otherwise leave a stale name on every old row.
create or replace function public.profiles_propagate_name()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    if new.display_name is distinct from old.display_name then
        update session_comments set author_name = new.display_name where user_id = new.id;
        update blocks set blocked_name = new.display_name where blocked_id = new.id;
        update mutes set muted_name = new.display_name where muted_id = new.id;
    end if;
    return null;
end;
$$;
revoke execute on function public.profiles_propagate_name() from public, anon, authenticated;

drop trigger if exists profiles_propagate_name on profiles;
create trigger profiles_propagate_name
    after update of display_name on profiles
    for each row execute function profiles_propagate_name();

-- SECURITY DEFINER: the blocked profile may already be unreadable to the
-- blocker (a friends-only stranger), and the friendship row is deleted in
-- either direction regardless of who requested it.
create or replace function public.blocks_before_insert()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    new.blocked_name := coalesce((select p.display_name from profiles p where p.id = new.blocked_id), '');
    new.created_at := now();
    delete from friendships f
    where (f.requester_id = new.blocker_id and f.addressee_id = new.blocked_id)
       or (f.requester_id = new.blocked_id and f.addressee_id = new.blocker_id);
    return new;
end;
$$;
revoke execute on function public.blocks_before_insert() from public, anon, authenticated;

drop trigger if exists blocks_before_insert on blocks;
create trigger blocks_before_insert
    before insert on blocks
    for each row execute function blocks_before_insert();

create or replace function public.mutes_before_insert()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    new.muted_name := coalesce((select p.display_name from profiles p where p.id = new.muted_id), '');
    new.created_at := now();
    return new;
end;
$$;
revoke execute on function public.mutes_before_insert() from public, anon, authenticated;

drop trigger if exists mutes_before_insert on mutes;
create trigger mutes_before_insert
    before insert on mutes
    for each row execute function mutes_before_insert();

create or replace function public.reports_before_insert()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    n int;
begin
    select count(*) into n from reports r
    where r.reporter_id = new.reporter_id and r.created_at > now() - interval '1 day';
    if n >= 20 then
        raise exception 'Too many reports today — try again tomorrow.';
    end if;

    new.status := 'open';
    new.created_at := now();
    new.excerpt := coalesce(
        (select c.body from session_comments c where c.id = new.comment_id),
        (select concat_ws(' · ', coalesce(nullif(s.title, ''), s.label), nullif(s.note, ''))
         from sessions s where s.id = new.session_id),
        '');
    return new;
end;
$$;
revoke execute on function public.reports_before_insert() from public, anon, authenticated;

drop trigger if exists reports_before_insert on reports;
create trigger reports_before_insert
    before insert on reports
    for each row execute function reports_before_insert();

-- ================================================================ views
-- Every view is security_invoker: it obeys the caller's RLS, so a board or feed
-- can never leak a profile, workout, reaction or comment the caller may not
-- read. (A board wrapped in a SECURITY DEFINER function would leak every row
-- while the option still reads true, which supabase/test/assert_all.sql reads
-- the boards as a stranger to catch.)

create or replace view leaderboard with (security_invoker = true) as
select
    p.id,
    p.display_name,
    p.current_title_id,
    p.level,
    p.total_xp,
    p.streak_days,
    p.titles_count,
    p.lifetime_strength,
    (select count(*) from sessions s
      where s.user_id = p.id
        and s.completed_at > now() - interval '7 days') as sessions_last_7d
from profiles p;

-- The Shadow Army board: same visibility rules as every other social surface.
create or replace view shadow_board with (security_invoker = true) as
select
    p.id,
    p.display_name,
    p.current_title_id,
    p.level,
    p.shadow_essence,
    p.shadow_count,
    p.shadow_rate
from profiles p
order by p.shadow_essence desc;

-- The strength board: one row per lifter per lift, for the caller and their
-- accepted allies only (lift_marks_read), joined to profiles so a lifter whose
-- profile the caller cannot read stays off the board. recent_step is nulled
-- once the best set is older than 7 days so a stale mark never reads as form.
create or replace view lift_board with (security_invoker = true) as
select
    m.user_id,
    p.display_name,
    p.current_title_id,
    p.level,
    m.lift,
    m.step,
    case when m.recent_at > now() - interval '7 days' then m.recent_step end as recent_step
from lift_marks m
join profiles p on p.id = m.user_id;

-- One request per feed page regardless of length: counts, reactions and the
-- movement content are all aggregated server-side from session_sets, which is
-- already RLS-protected. Every column name and meaning is read by the app
-- verbatim, so none may be renamed.
create or replace view public_feed with (security_invoker = true) as
select
    s.id                             as session_id,
    s.user_id,
    p.display_name,
    p.current_title_id,
    p.level,
    s.label,
    s.title,
    s.note,
    s.completed_at,
    s.started_at,
    s.xp_awarded,
    s.strength_score,
    (select count(*) from session_sets ss where ss.session_id = s.id and ss.done) as sets_done,
    (select coalesce(sum(ss.reps), 0) from session_sets ss where ss.session_id = s.id and ss.done) as reps_done,

    -- Seconds held across the session's static holds: no reps, a positive
    -- duration, no distance and no climbing grade (a timed run carries a
    -- distance, a timed hold does not). 0, never null, so the client treats
    -- no holds and an older row identically.
    (
        select coalesce(sum(ss.duration_sec), 0)
        from session_sets ss
        where ss.session_id = s.id
          and ss.done
          and ss.reps = 0
          and ss.duration_sec is not null
          and ss.duration_sec > 0
          and ss.distance_m is null
          and ss.grade is null
    ) as held_seconds,

    -- Total reactions of every kind: a 1.3 client shows this as its like count.
    (select count(*) from session_likes sl where sl.session_id = s.id) as like_count,
    exists (select 1 from session_likes sl where sl.session_id = s.id and sl.user_id = auth.uid()) as liked_by_me,

    -- WHAT was trained: the three movements carrying the most work, heaviest
    -- first, ordered by loaded volume so the headline lift leads.
    (
        select string_agg(m.exercise_name, ' · ' order by m.volume desc)
        from (
            select ss.exercise_name,
                   sum(ss.reps * coalesce(nullif(ss.weight_kg, 0), 1)) as volume
            from session_sets ss
            where ss.session_id = s.id and ss.done
            group by ss.exercise_name
            order by volume desc
            limit 3
        ) m
    ) as top_movements,

    -- Only a genuinely load-bearing set earns the headline. A loaded set counts
    -- outright, an unloaded set counts only when it is a real rep-scored set
    -- (more than one rep, no distance, no climbing grade), which keeps
    -- calisthenics (8 x BW) and rejects a run's synthetic single rep. A hold
    -- has reps = 0 and is excluded by both branches: its headline is its time.
    (
        select case
                 when ss.weight_kg is not null and ss.weight_kg > 0
                   then ss.reps || ' x ' || trim(to_char(ss.weight_kg, 'FM999990.0')) || ' kg'
                 else ss.reps || ' x BW'
               end
        from session_sets ss
        where ss.session_id = s.id
          and ss.done
          and (
                (ss.weight_kg is not null and ss.weight_kg > 0)
             or (ss.reps > 1 and ss.distance_m is null and ss.grade is null)
          )
        order by coalesce(ss.weight_kg, 0) desc, ss.reps desc
        limit 1
    ) as best_set,

    -- What a cardio hunt actually did: metres covered, null when it covered none.
    (
        select nullif(sum(ss.distance_m), 0)
        from session_sets ss
        where ss.session_id = s.id and ss.done
    ) as distance_m,

    -- Hardest climbing grade attempted. Free text by design (V-scale, Font and
    -- YDS all disagree), so it is ranked lexically as a tie-break of last
    -- resort and shown as recorded.
    (
        select ss.grade
        from session_sets ss
        where ss.session_id = s.id and ss.done and ss.grade is not null and ss.grade <> ''
        order by length(ss.grade) desc, ss.grade desc
        limit 1
    ) as hardest_grade,

    (
        select count(distinct ss.exercise_name)
        from session_sets ss
        where ss.session_id = s.id and ss.done
    ) as movement_count,

    -- Elapsed training time in seconds, null when either stamp is missing.
    case
      when s.completed_at is not null and s.started_at is not null
        then extract(epoch from (s.completed_at - s.started_at))::int
      else null
    end as duration_sec,

    -- Counted under the reader's policies, so a muted or blocked author's
    -- comment is not counted for a reader who cannot open it.
    (select count(*) from session_comments sc where sc.session_id = s.id) as comment_count,

    coalesce((
        select jsonb_object_agg(r.kind, r.n)
        from (
            select sl.kind, count(*) as n
            from session_likes sl
            where sl.session_id = s.id
            group by sl.kind
        ) r
    ), '{}'::jsonb) as reactions,

    (select sl.kind from session_likes sl where sl.session_id = s.id and sl.user_id = auth.uid()) as my_reaction
from sessions s
join profiles p on p.id = s.user_id
where s.completed_at is not null
  -- A mute hides the lifter's workouts from the muter's feed, silently.
  and not exists (select 1 from mutes m where m.muter_id = auth.uid() and m.muted_id = s.user_id)
order by s.completed_at desc;

-- ================================================================ RPCs

-- Exact, case-insensitive, trimmed name match only: no prefix, no ilike, no
-- wildcards, so it cannot be walked to enumerate the roster. Returns ONLY
-- (id, display_name): level, XP, titles, streak and session history stay
-- behind profiles_read. LIMIT 1, so a duplicate name cannot fan out.
-- SECURITY DEFINER because the point is to see past profiles_read for this one
-- column pair (a friends-only stranger is unreadable, which is why they need to
-- be found). Signed-in lifters only.
create or replace function find_hunter(name text)
returns table (id uuid, display_name text)
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    select p.id, p.display_name
    from profiles p
    where lower(p.display_name) = lower(trim(name))
    limit 1;
$$;
revoke execute on function public.find_hunter(text) from public, anon;
grant execute on function public.find_hunter(text) to authenticated;

-- The app asks BEFORE sign-up (no session yet, so anon), which discloses
-- nothing find_hunter does not already. Same trim and case-fold as the unique
-- index, and the same 2..24 bound as profiles.display_name.
create or replace function public.display_name_available(name text)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    select coalesce(char_length(trim(name)) between 2 and 24, false)
       and not exists (
            select 1 from profiles p
            where lower(p.display_name) = lower(trim(name))
       );
$$;
revoke execute on function public.display_name_available(text) from public, anon;
grant execute on function public.display_name_available(text) to anon, authenticated;

-- The level curve in SQL, the same as Xp.progress(): level n needs a
-- cumulative 50*n*(n-1), because xpForNextLevel(level) = 100 * level. Closed
-- form with an exact integer correction: floating sqrt picks the candidate,
-- integer arithmetic settles the boundary, so it cannot disagree at a threshold.
-- Only push_aggregates' definer body calls it.
create or replace function monarch_level(total_xp bigint)
returns int
language plpgsql
immutable
set search_path = pg_catalog, public
as $$
declare
    n int;
begin
    if total_xp is null or total_xp <= 0 then
        return 1;
    end if;
    n := floor(0.5 + sqrt(0.25 + total_xp / 50.0))::int;
    while n > 1 and 50::bigint * n * (n - 1) > total_xp loop
        n := n - 1;
    end loop;
    while 50::bigint * (n + 1) * n <= total_xp loop
        n := n + 1;
    end loop;
    return greatest(n, 1);
end;
$$;
revoke execute on function public.monarch_level(bigint) from public, anon, authenticated;

-- The one way a signed-in lifter writes ranked numbers. Server-authoritative
-- XP would mean moving the whole economy (activity curves, quest detection,
-- offline reconciliation) server-side, which is a product decision, so this
-- closes every hole that does not need a second economy:
--   * titles_count is DERIVED from earned_titles rows;
--   * level is DERIVED from total_xp, never a separate claim;
--   * total_xp, lifetime_strength and the shadow figures remain CLAIMS, but
--     monotonic (a reinstall cannot walk them back) and bounded by ceilings.
-- Residual: a determined cheat can still claim up to those ceilings.
create or replace function push_aggregates(
    p_total_xp bigint,
    p_lifetime_strength bigint,
    p_streak_days int,
    p_shadow_essence bigint,
    p_shadow_count int,
    p_shadow_rate double precision
)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    uid uuid := auth.uid();
    derived_titles int;
    claimed_xp bigint;
    claimed_strength bigint;
begin
    if uid is null then
        raise exception 'push_aggregates requires a signed-in hunter';
    end if;

    select count(*) into derived_titles from earned_titles where user_id = uid;

    -- The ceilings are far above any real hunter and far below the bigint range
    -- a forgery wants.
    claimed_xp := least(greatest(coalesce(p_total_xp, 0), 0), 100000000);
    claimed_strength := least(greatest(coalesce(p_lifetime_strength, 0), 0), 100000000);

    -- greatest(), not assignment: a fresh install or a local database reset
    -- reports LV 1 / 0 XP, and blindly writing that would destroy the only
    -- server-side copy.
    update profiles p set
        total_xp          = greatest(p.total_xp, claimed_xp),
        level             = monarch_level(greatest(p.total_xp, claimed_xp)),
        titles_count      = derived_titles,
        lifetime_strength = greatest(p.lifetime_strength, claimed_strength),
        -- streak legitimately falls to zero after missed days, so it is bounded
        -- rather than monotonic.
        streak_days       = least(greatest(coalesce(p_streak_days, 0), 0), 3650),
        -- Idle state never leaves the device, so the server has nothing to
        -- derive these from. Essence and count are cumulative, rate is a
        -- current reading that legitimately falls.
        shadow_essence    = greatest(p.shadow_essence, least(greatest(coalesce(p_shadow_essence, 0), 0), 1000000000)),
        shadow_count      = greatest(p.shadow_count, least(greatest(coalesce(p_shadow_count, 0), 0), 100000)),
        shadow_rate       = least(greatest(coalesce(p_shadow_rate, 0), 0), 1000000)
    where p.id = uid;
end;
$$;
revoke execute on function public.push_aggregates(bigint, bigint, int, bigint, int, double precision) from public, anon;
grant execute on function public.push_aggregates(bigint, bigint, int, bigint, int, double precision) to authenticated;

-- Cloud wipe deletes the whole cloud account, not just the training rows.
-- Deleting the auth.users row takes profiles (and everything keyed on it) and
-- cloud_archives with it. The client cannot touch auth.users, so the delete
-- runs here as the function owner, and only ever for auth.uid().
create or replace function public.delete_my_account() returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    me uuid := auth.uid();
begin
    if me is null then
        raise exception 'requires a signed-in lifter' using errcode = '42501';
    end if;
    delete from auth.users where id = me;
end;
$$;
revoke execute on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;

create or replace function public.mark_inbox_seen()
returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    me uuid := auth.uid();
begin
    if me is null then
        raise exception 'requires a signed-in lifter' using errcode = '42501';
    end if;
    insert into inbox_seen (user_id, seen_at) values (me, now())
    on conflict (user_id) do update set seen_at = excluded.seen_at;
end;
$$;
revoke execute on function public.mark_inbox_seen() from public, anon;
grant execute on function public.mark_inbox_seen() to authenticated;

-- SECURITY DEFINER because a request comes from a lifter whose profile the
-- caller usually cannot read yet (that is why they are asking), and the name
-- is no secret: find_hunter already resolves it. Every branch is pinned to
-- auth.uid(), so it can only ever answer about the caller.
create or replace function public.my_inbox()
returns table (
    kind             text,
    occurred_at      timestamptz,
    actor_id         uuid,
    actor_name       text,
    session_id       uuid,
    session_headline text,
    comment_id       uuid,
    body             text,
    reaction         text
)
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    with me as (select auth.uid() as id),
    items as (
        select 'request'::text as kind, f.created_at as occurred_at, f.requester_id as actor_id,
               p.display_name as actor_name, null::uuid as session_id, null::text as session_headline,
               null::uuid as comment_id, null::text as body, null::text as reaction
        from friendships f
        join profiles p on p.id = f.requester_id
        join me on f.addressee_id = me.id
        where not f.accepted

        union all

        select 'accepted', f.accepted_at, f.addressee_id, p.display_name,
               null, null, null, null, null
        from friendships f
        join profiles p on p.id = f.addressee_id
        join me on f.requester_id = me.id
        where f.accepted and f.accepted_at is not null

        union all

        select 'comment', c.created_at, c.user_id, c.author_name,
               s.id, coalesce(nullif(s.title, ''), s.label), c.id, c.body, null
        from session_comments c
        join sessions s on s.id = c.session_id
        join me on s.user_id = me.id
        where c.user_id <> me.id

        union all

        select 'reaction', l.created_at, l.user_id, p.display_name,
               s.id, coalesce(nullif(s.title, ''), s.label), null, null, l.kind
        from session_likes l
        join sessions s on s.id = l.session_id
        join profiles p on p.id = l.user_id
        join me on s.user_id = me.id
        where l.user_id <> me.id
    )
    select i.kind, i.occurred_at, i.actor_id, i.actor_name, i.session_id,
           i.session_headline, i.comment_id, i.body, i.reaction
    from items i, me
    where i.occurred_at > now() - interval '30 days'
      and not blocked_between(me.id, i.actor_id)
      and not exists (select 1 from mutes m where m.muter_id = me.id and m.muted_id = i.actor_id)
    order by i.occurred_at desc
    limit 100;
$$;
revoke execute on function public.my_inbox() from public, anon;
grant execute on function public.my_inbox() to authenticated;

-- The version beacon. The app probes schema_version() as anon before pointing a
-- lifter's training at a custom backend (Settings -> CLOUD, TEST), so a
-- half-set-up project is reported before any data is sent to it. The anon
-- grant is load-bearing. EVERY SCHEMA CHANGE BUMPS THIS LITERAL and
-- Cloud.kt's NEEDED_SCHEMA_VERSION with it.
create or replace function public.schema_version() returns int
language sql stable as $$ select 20 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon, authenticated;

-- ================================================================ sign-up

-- Creates the profiles row when GoTrue creates the auth user. The app used to
-- insert its own row right after sign-up, but with email confirmation ON there
-- is no session yet, so that insert ran as anon and failed 42501.
--
-- Name: the sign-up metadata key display_name, trimmed, when it is 2..24 chars
-- and not taken (case-insensitively, the unique index on lower(display_name)).
-- Otherwise a neutral handle 'Lifter' plus four digits, which is exactly the
-- shape the app's claim-your-name panel recognises (^(?:Hunter|Lifter)\d{4}$).
-- The probe starts at a random number and walks forward, so it finds a free
-- handle whenever one exists. It NEVER reads full_name / name (Google's
-- metadata is a legal name and must not become a public handle).
--
-- It must never make sign-up fail: a lost unique race falls back, and a
-- profile that already exists is left alone. Only when all 10000 neutral
-- handles are taken does the user get no profile, with a warning.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    wanted text := nullif(trim(new.raw_user_meta_data ->> 'display_name'), '');
    start_at int := floor(random() * 10000)::int;
    handle text;
begin
    if exists (select 1 from profiles p where p.id = new.id) then
        return new;
    end if;

    if wanted is not null and char_length(wanted) between 2 and 24 then
        begin
            insert into profiles (id, display_name) values (new.id, wanted);
            return new;
        exception when unique_violation then
            null; -- taken: fall through to a neutral handle
        end;
    end if;

    for i in 0..9999 loop
        handle := 'Lifter' || lpad(((start_at + i) % 10000)::text, 4, '0');
        begin
            insert into profiles (id, display_name) values (new.id, handle);
            return new;
        exception when unique_violation then
            null; -- taken: try the next digits
        end;
    end loop;

    raise warning 'handle_new_user: every neutral handle is taken, no profile for %', new.id;
    return new;
end;
$$;
revoke execute on function public.handle_new_user() from public, anon, authenticated;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();
