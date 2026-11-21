-- 0001_baseline.sql
-- Ironvellum's whole Supabase schema, as ONE file, for a fresh project.
-- Never reset a project holding real accounts. See supabase/SELF_HOSTING.md.
--
-- Idempotent: every statement guards itself (create ... if not exists, create
-- or replace, drop policy/trigger if exists), so re-applying it to a project
-- that already carries this exact schema is a no-op. It does NOT upgrade an
-- arbitrary older shape. Existing deployments keep their accounts and use
-- the applicable ordered patches in supabase/hosted/. Keep this baseline in
-- sync with each patch and bump schema_version(). Raise Cloud.kt's NEEDED_SCHEMA_VERSION only
-- for changes required by the whole app; optional boards can report an
-- upgrade requirement while older features keep working.
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
    -- The worn crest rides along the same way: allies see it on the avatar.
    -- Opaque to the server (the catalogue lives in the app); an id this build
    -- does not know is drawn as nothing.
    current_crest_id   text check (char_length(current_crest_id) <= 64),
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

-- A project that still has warbands (schema 26 and before) is migrated IN PLACE
-- here, before anything below creates the new names, so no data is lost: the
-- tables, columns, constraints and indexes are renamed (warbands -> circles,
-- warband_members -> circle_members, warband_id -> circle_id, warband_join_log
-- -> circle_join_log), weekly_goal (a total of 5-50 workouts) becomes per_member
-- (days each member aims to train, 1-7: the default 12 is 3, anything else is
-- round(goal / members) clamped to 1..7; a project that never had weekly_goal
-- keeps 3), the Keeper may be null mid-handover and deleting their account
-- hands the circle over, and circle names are trimmed. The old policies and
-- *_warband functions go; the definitions below recreate them under the new
-- names. A fresh project has no warbands and skips all of it. The same block
-- opens supabase/hosted/2026-10-01-circles.sql, which the live project runs once.
do $rename$
declare
    c record;
begin
    if to_regclass('public.warbands') is not null and to_regclass('public.circles') is not null then
        raise exception 'warbands and circles both exist: merge them by hand before applying this file';
    end if;
    if to_regclass('public.warbands') is not null then
        drop policy if exists warbands_read on warbands;
        drop policy if exists warbands_update on warbands;
        drop policy if exists warbands_delete on warbands;
        drop policy if exists warband_members_read on warband_members;
        drop function if exists public.create_warband(text);
        drop function if exists public.join_warband(text);
        drop function if exists public.leave_warband();
        drop function if exists public.set_warband_goal(int);
        drop function if exists public.my_warband();
        drop function if exists public.in_my_warband(uuid);
        drop function if exists public.warband_member(uuid, uuid);

        alter table warbands rename to circles;
        alter table warband_members rename to circle_members;
        alter table circle_members rename column warband_id to circle_id;
        if to_regclass('public.warband_join_log') is not null then
            alter table warband_join_log rename to circle_join_log;
        end if;

        -- Constraints (and the indexes behind the keys) and plain indexes that
        -- carry the old name, so the result matches a fresh project.
        for c in
            select con.conrelid::regclass::text as tbl, con.conname
            from pg_constraint con
            where con.conname like 'warband%'
              and con.conrelid in (
                  select r.oid from pg_class r
                  where r.relnamespace = 'public'::regnamespace
                    and r.relname in ('circles', 'circle_members', 'circle_join_log'))
        loop
            execute format('alter table %s rename constraint %I to %I',
                           c.tbl, c.conname, replace(c.conname, 'warband', 'circle'));
        end loop;
        for c in
            select i.indexname from pg_indexes i
            where i.schemaname = 'public' and i.indexname like 'warband%'
        loop
            execute format('alter index public.%I rename to %I', c.indexname, replace(c.indexname, 'warband', 'circle'));
        end loop;

        -- The goal: a total of workouts becomes days per member.
        alter table circles add column if not exists per_member int not null default 3
            check (per_member between 1 and 7);
        alter table circles add column if not exists pending_per_member int
            check (pending_per_member between 1 and 7);
        if exists (select 1 from information_schema.columns
                   where table_schema = 'public' and table_name = 'circles' and column_name = 'weekly_goal') then
            update circles w
            set per_member = case
                when w.weekly_goal = 12 then 3
                else greatest(1, least(7, round(
                    w.weekly_goal::numeric
                    / greatest(1, (select count(*) from circle_members m where m.circle_id = w.id))
                )::int))
            end;
            alter table circles drop column weekly_goal;
        end if;

        -- The Keeper may be null while a handover is in flight, and the owner's
        -- account going away hands the circle over instead of deleting it.
        alter table circles alter column owner_id drop not null;
        alter table circles drop constraint circles_owner_id_fkey;
        alter table circles add constraint circles_owner_id_fkey
            foreign key (owner_id) references auth.users (id) on delete set null;

        -- Names are stored trimmed.
        alter table circles drop constraint if exists circles_name_check;
        update circles
        set name = case when btrim(name) = '' then 'Circle' else left(btrim(name), 24) end
        where name <> btrim(name);
        alter table circles add constraint circles_name_trimmed
            check (name = btrim(name) and char_length(name) between 1 and 24);
    end if;
end
$rename$;

-- Failed circle-code attempts, capped per lifter like friend requests: the
-- code space is huge, but a throttled door costs nothing to close. RLS is on
-- with no policies: the security-definer RPCs own every write, and no client
-- role may read another lifter's attempts.
create table if not exists circle_join_log (
    user_id  uuid not null references profiles (id) on delete cascade,
    tried_at timestamptz not null default now()
);
create index if not exists circle_join_log_idx on circle_join_log (user_id, tried_at);
alter table circle_join_log enable row level security;
revoke all on circle_join_log from anon, authenticated;

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
    -- Xp: <=405 per hardest set, 265 bonuses incl. quest; amendments can double.
    -- 1M leaves room for >1,200 such sets; activities have no app-wide row cap.
    constraint sessions_xp_awarded_cap check (xp_awarded between 0 and 1000000),
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

-- When the owner amended the sealed workout; null when never amended. The feed
-- marks an amended workout so an ally is not surprised by changed figures.
alter table sessions add column if not exists edited_at timestamptz;

-- The friend-safe card of a completed workout, written only by the phone:
-- {"v":1,"hardSets":n,"volumeKg":n,"prs":[{label,reps,weightKg,durationSec}],"levels":{"LATS":1-3}}.
-- A column on sessions rather than a side table so it inherits sessions_read
-- (can_view_session: the workout's own audience) with no second policy to get
-- wrong. It never holds an e1RM or any figure derived from body weight; the
-- server checks only that it is an object under 2 KiB, the shape is the
-- phone's contract. Older clients ignore it.
alter table sessions add column if not exists feed_card jsonb;
do $fc$
begin
    if not exists (
        select 1 from pg_constraint
        where conrelid = 'public.sessions'::regclass and conname = 'sessions_feed_card_check'
    ) then
        alter table sessions add constraint sessions_feed_card_check check (
            feed_card is null
            or (jsonb_typeof(feed_card) = 'object' and char_length(feed_card::text) <= 2048)
        );
    end if;
end
$fc$;

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
    constraint cloud_archives_size_cap check (size_bytes > 0 and size_bytes <= 8 * 1024 * 1024),
    constraint cloud_archives_payload_cap check (octet_length(archive) <= 8 * 1024 * 1024)
);

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
    -- size_bytes is what the phone measured (the jsonb text form, 4 MiB at most). The text the server holds
    -- gets twice that: jsonb prints a space after every ':' and ',', so a bound equal to the client's would
    -- refuse snapshots the client had measured as fitting.
    constraint web_snapshots_size_cap check (
        size_bytes > 0 and size_bytes <= 4 * 1024 * 1024
        and octet_length(snapshot::text) <= 8 * 1024 * 1024
    )
);

-- Upgrade schema-30 and schema-31 installations as well as fresh databases. Guarded on the current definition so
-- applying the baseline again does not rescan the table under a lock, and added NOT VALID then validated.
do $cap$
begin
    if not exists (
        select 1 from pg_constraint
        where conrelid = 'public.web_snapshots'::regclass and conname = 'web_snapshots_size_cap'
          and pg_get_constraintdef(oid) like '%(8 * 1024)%'
    ) then
        alter table web_snapshots drop constraint if exists web_snapshots_size_cap;
        alter table web_snapshots add constraint web_snapshots_size_cap check (
            size_bytes > 0 and size_bytes <= 4 * 1024 * 1024
            and octet_length(snapshot::text) <= 8 * 1024 * 1024
        ) not valid;
    end if;
    if exists (
        select 1 from pg_constraint
        where conrelid = 'public.web_snapshots'::regclass and conname = 'web_snapshots_size_cap' and not convalidated
    ) then
        alter table web_snapshots validate constraint web_snapshots_size_cap;
    end if;
end
$cap$;

-- One row per lifter per board: the coarse step, worked out on the phone. On a
-- bodyweight-tier board it is the tier (never bodyweight or a ratio, so an ally
-- cannot back-solve a lifter's weight from a public set load); on a skill-ladder
-- board it is the 1-based rung. recent_step is the best qualifying set of the
-- last 7 days and recent_at says when, so the board can show who is on form
-- without a second table.
create table if not exists lift_marks (
    user_id     uuid not null references profiles (id) on delete cascade,
    lift        text not null,
    step        int  not null,
    recent_step int,
    recent_at   timestamptz,
    updated_at  timestamptz not null default now(),
    primary key (user_id, lift),
    constraint lift_marks_step check (step between 0 and 10),
    constraint lift_marks_recent_step check (recent_step is null or recent_step between 0 and 10)
);

alter table lift_marks add column if not exists recent_steps bigint[];
alter table lift_marks drop constraint if exists lift_marks_recent_steps;
alter table lift_marks add constraint lift_marks_recent_steps check (
    recent_steps is null or (cardinality(recent_steps) = 11 and array_position(recent_steps, null) is null)
);

-- Legacy upserts must not leave tier history from an older sync in place.
create or replace function public.clear_rolling_lift_marks() returns trigger
language plpgsql set search_path = public as $$
begin
    new.recent_steps := null;
    return new;
end $$;
drop trigger if exists clear_rolling_lift_marks on lift_marks;
create trigger clear_rolling_lift_marks before update of step, recent_step, recent_at on lift_marks
for each row execute function public.clear_rolling_lift_marks();

-- Resolve the caller without requiring access to the auth schema; writes still run under RLS.
create or replace function public.lift_mark_caller() returns uuid
language sql stable security definer set search_path = pg_catalog
as $$ select auth.uid() $$;
revoke execute on function public.lift_mark_caller() from public, anon;
grant execute on function public.lift_mark_caller() to authenticated;

drop function if exists public.push_lift_marks(jsonb);
create or replace function public.push_lift_marks(expected_user_id uuid, marks jsonb) returns void
language plpgsql security invoker set search_path = public as $$
declare
    caller uuid := public.lift_mark_caller();
    m record;
begin
    if caller is null or caller is distinct from expected_user_id then
        raise exception 'Lift mark account changed' using errcode = '42501';
    end if;
    for m in select * from jsonb_to_recordset(marks) as x(
        lift text, step int, recent_step int, recent_at timestamptz, recent_steps bigint[]
    ) loop
        insert into lift_marks (user_id, lift, step, recent_step, recent_at)
        values (caller, m.lift, m.step, m.recent_step, m.recent_at)
        on conflict (user_id, lift) do update set
            step = excluded.step, recent_step = excluded.recent_step, recent_at = excluded.recent_at;
        update lift_marks set recent_steps = m.recent_steps where user_id = caller and lift = m.lift;
    end loop;
end $$;
revoke execute on function public.push_lift_marks(uuid, jsonb) from public, anon;
grant execute on function public.push_lift_marks(uuid, jsonb) to authenticated;

-- The board list grew at schema 21 (calisthenics ladders: step is the 1-based
-- rung). The lift check is dropped and re-added by name so pasting this file
-- over a live schema-20 database widens it; existing rows all carry old wires
-- and still pass. Keep in step with Lift.wire in LiftBoards.kt.
alter table lift_marks drop constraint if exists lift_marks_lift;
alter table lift_marks add constraint lift_marks_lift check (lift in (
    'pull_up', 'one_arm_pull', 'muscle_up',
    'dip', 'push_up', 'hspu',
    'front_lever', 'back_lever', 'planche', 'handstand', 'l_sit', 'human_flag',
    'pistol',
    'squat', 'bench', 'deadlift', 'ohp'
));

-- ================================================================ circles
-- Invite-code circles of 2-8 allies (the 8 cap is enforced in join_circle, the
-- code is the one way in; a circle needs two members before it has a goal). One
-- circle per lifter: circle_members.user_id is the whole primary key.
--
-- The week. A circle's goal is a number of DAYS TRAINED: per_member (1-7,
-- default 3) days from each member, so goal = per_member x the roster the week
-- OPENED with. Both are frozen when the week opens (circle_weeks, with the
-- roster in circle_week_members), so nothing that happens during the week can
-- move the goal: a change of target is parked in pending_per_member and applies
-- when the next week opens, a lifter who joins mid-week counts from the next
-- Monday, and a lifter who leaves mid-week still counts for the week they left
-- (up to the moment they left). Each member's days are capped at their share, so
-- one lifter cannot carry the circle: a met goal means every member in the
-- roster trained their share. See circle_week_days() for what a day is.
--
-- Settlement. The week is SETTLED on the server, once, by whichever read comes
-- first after the week has closed (circle_roll, called from my_circle,
-- circle_bonuses and the membership triggers): total and met are written to
-- circle_weeks and are the same answer for every member for ever after. The
-- weekly bonus is paid by each phone from that settled row (circle_bonuses).
--
-- Privacy: a fellow member sees the circle row and the member list; nobody else sees
-- either. Membership is written ONLY by the RPCs below — there is no insert
-- policy and no insert grant, so an owner cannot silently add anyone and a
-- stranger cannot join without the code. circle_weeks and circle_week_members
-- have row security on and no policy and no grant: only the definer RPCs read
-- or write them.
create table if not exists circles (
    id          uuid primary key default gen_random_uuid(),
    -- A circle name is a small shouty HUD label, bounded like a title, and
    -- stored trimmed: create_circle and set_circle_name trim, and the check holds
    -- the line for anything else that ever writes the column.
    name        text not null,
    -- 8 chars, no 0/O/1/I/L: a code read aloud off a phone screen must be
    -- unambiguous. Keep in step with InviteCodeAlphabet in domain/Circles.kt.
    invite_code text not null unique check (invite_code ~ '^[2-9A-HJ-NP-Z]{8}$'),
    -- The Keeper. SET NULL, not CASCADE: deleting the owner's account must
    -- hand the circle over (circle_members_handover below), never take every
    -- other member's circle with it. Null only while a handover is in flight.
    owner_id    uuid references auth.users (id) on delete set null,
    -- The weekly target: days each member aims to train, 1-7. Applies to every
    -- week that opens from now on; the Keeper's change parks in
    -- pending_per_member and takes effect when the next week opens.
    per_member  int not null default 3 check (per_member between 1 and 7),
    pending_per_member int check (pending_per_member between 1 and 7),
    created_at  timestamptz default now(),
    constraint circles_name_trimmed
        check (name = btrim(name) and char_length(name) between 1 and 24)
);
-- A project that met circles before the goal model existed still gains the
-- columns when this file is re-applied (a no-op everywhere else).
alter table circles add column if not exists per_member int not null default 3
    check (per_member between 1 and 7);
alter table circles add column if not exists pending_per_member int
    check (pending_per_member between 1 and 7);
create index if not exists circles_owner_idx on circles (owner_id);

create table if not exists circle_members (
    circle_id uuid references circles (id) on delete cascade,
    user_id   uuid primary key references auth.users (id) on delete cascade,
    joined_at timestamptz default now()
);
-- Roster reads and handovers walk a circle's members oldest first.
create index if not exists circle_members_circle_joined_idx
    on circle_members (circle_id, joined_at, user_id);

-- One row per circle per week, written when the week opens. week is the UTC
-- Monday that opens it. per_member, members and goal are FROZEN at that moment
-- (goal = per_member x members, the roster the week opened with); total and met
-- are null until the week is settled, and settled_at says when.
create table if not exists circle_weeks (
    -- Keep frozen bonus evidence after the last member leaves and deletes the circle.
    circle_id  uuid not null,
    week       date not null,
    per_member int  not null check (per_member between 1 and 7),
    members    int  not null check (members >= 0),
    goal       int  not null check (goal >= 0),
    total      int  check (total is null or total >= 0),
    met        boolean,
    settled_at timestamptz,
    primary key (circle_id, week),
    constraint circle_weeks_goal_frozen check (goal = per_member * members),
    constraint circle_weeks_settled_together check ((settled_at is null) = (met is null and total is null))
);

-- The roster a week opened with. joined_at is copied so a later rejoin cannot
-- move it; left_at is stamped if the lifter leaves while the week is open, so
-- their days count up to that moment and no further. days is the member's
-- counted days (capped at their share), written at settlement.
create table if not exists circle_week_members (
    circle_id uuid not null,
    week      date not null,
    user_id   uuid not null references auth.users (id) on delete cascade,
    joined_at timestamptz not null,
    left_at   timestamptz,
    days      int check (days is null or days >= 0),
    primary key (circle_id, week, user_id),
    foreign key (circle_id, week) references circle_weeks (circle_id, week) on delete cascade
);
create index if not exists circle_week_members_user_idx on circle_week_members (user_id, week);

-- Missives about the roster: someone left, the keys passed to you, you were
-- removed. One row per recipient, written only by the definer functions below
-- (circle_notify) and read only through my_inbox(). Kept 30 days, the same
-- window the inbox shows; the writer prunes what is older.
create table if not exists circle_events (
    id          bigint generated always as identity primary key,
    user_id     uuid not null references auth.users (id) on delete cascade,
    kind        text not null
        constraint circle_events_kind_check
        check (kind in ('circle_left', 'circle_keeper', 'circle_removed')),
    actor_id    uuid not null references auth.users (id) on delete cascade,
    circle_name text not null,
    occurred_at timestamptz not null default now()
);
create index if not exists circle_events_user_idx on circle_events (user_id, occurred_at desc);
create index if not exists circle_events_actor_idx on circle_events (actor_id);
create index if not exists circle_events_at_idx on circle_events (occurred_at);

-- Membership test used by the circle policies below. SECURITY DEFINER or the two
-- policies recurse through each other's sub-selects on circle_members. Closed
-- to every client role: it answers about ANY (circle, user) pair — an oracle over
-- the whole roster graph, like is_friend().
create or replace function public.circle_member(p_circle uuid, who uuid)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    select exists (
        select 1 from circle_members m
        where m.circle_id = p_circle and m.user_id = who
    );
$$;
revoke execute on function public.circle_member(uuid, uuid) from public, anon, authenticated;

-- The caller-scoped face of circle_member(): answers only about auth.uid(),
-- so it leaks nothing is_ally() does not already leak. The policies call THIS
-- one — a policy is evaluated as the querying role, which needs the grant.
create or replace function public.in_my_circle(p_circle uuid)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $$ select circle_member(p_circle, auth.uid()) $$;
revoke execute on function public.in_my_circle(uuid) from public, anon;
grant execute on function public.in_my_circle(uuid) to authenticated;

-- Members read the circle's roster; nobody reads a circle they are not in.
drop policy if exists circles_read on circles;
create policy circles_read on circles
    for select to authenticated
    using (in_my_circle(id));

-- Nobody writes the circles row directly: rename, code rotation, removal and the
-- goal are Keeper-only RPCs (set_circle_name, rotate_circle_code,
-- kick_circle_member, set_circle_goal), and a circle ends by its last member
-- leaving, never by a delete from a client. These policies existed while the
-- Keeper held update(name) and delete; schema 27 closes both doors.
drop policy if exists circles_update on circles;
drop policy if exists circles_delete on circles;

-- Your own membership row, plus your fellow members'. No write policies at all:
-- create_circle/join_circle/leave_circle below are the only writers.
drop policy if exists circle_members_read on circle_members;
create policy circle_members_read on circle_members
    for select to authenticated
    using (user_id = auth.uid() or in_my_circle(circle_id));

-- Writes one missive per recipient and prunes what is past the inbox's window.
-- The actor must still exist (an account being deleted is leaving, and its
-- missives go with it by cascade). Closed to every client role.
create or replace function public.circle_notify(p_users uuid[], p_kind text, p_actor uuid, p_circle text)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    delete from circle_events e where e.occurred_at < now() - interval '30 days';
    insert into circle_events (user_id, kind, actor_id, circle_name)
    select u.id, p_kind, p_actor, p_circle
    from auth.users u
    where u.id = any (p_users)
      and exists (select 1 from auth.users a where a.id = p_actor);
end;
$$;
revoke execute on function public.circle_notify(uuid[], text, uuid, text) from public, anon, authenticated;

-- One draw of an invite code: five random bytes mapped to eight 5-bit glyphs
-- over the 31-glyph alphabet (31^8 exactly covers 2^40). random() is
-- documented as unfit for secrets. Uniqueness is the caller's: a collision is
-- absorbed by the unique index and another draw. Keep the alphabet in step with
-- InviteCodeAlphabet in domain/Circles.kt and the check on circles.invite_code.
create or replace function public.circle_draw_code()
returns text
language plpgsql
volatile
set search_path = pg_catalog, public
as $$
declare
    alphabet text := '23456789ABCDEFGHJKMNPQRSTUVWXYZ';
    -- 40 random bits from gen_random_uuid(), which is pg_catalog (PG13+). Not
    -- pgcrypto's gen_random_bytes(): Supabase installs pgcrypto in the
    -- `extensions` schema, which this search_path excludes, so that call failed
    -- with 42883 and nobody could form a circle or change a code. The first 12
    -- hex digits of a v4 uuid are all random (the version nibble is the 13th).
    v        bigint := ('x' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 10))::bit(40)::bigint;
    code     text := '';
    i        int;
begin
    for i in 1..8 loop
        code := code || substr(alphabet, 1 + (v % 31)::int, 1);
        v := v >> 5;
    end loop;
    return code;
end;
$$;
revoke execute on function public.circle_draw_code() from public, anon, authenticated;

-- The caller's circle, locked, when they are its Keeper and still a member; any
-- other caller is refused with 42501, the code a revoked grant gives. [what] ends
-- the message: "Only the Keeper <what>". The lock is the one joins and leaves
-- take, so a Keeper action reads the roster as one step.
create or replace function public.circle_keeper_lock(p_what text)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    cid uuid;
begin
    select w.id into cid
    from circles w
    where w.owner_id = auth.uid()
      and exists (select 1 from circle_members m
                  where m.circle_id = w.id and m.user_id = w.owner_id)
    for update;
    if cid is null then
        raise exception 'Only the Keeper %', p_what using errcode = '42501';
    end if;
    return cid;
end;
$$;
revoke execute on function public.circle_keeper_lock(text) from public, anon, authenticated;

-- Before a membership row goes, bring the circle's weeks up to date: settle the
-- weeks that have closed and open this one, so the roster the week opened with
-- is on record while the leaver is still in it, and stamp when they left so
-- their days after that moment never count. When the circle itself is going
-- (cascade) it is already gone here and there is nothing to do.
create or replace function public.circle_members_before_delete()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    if exists (select 1 from circles c where c.id = old.circle_id) then
        perform public.circle_roll(old.circle_id);
        update circle_week_members wm
        set left_at = now()
        where wm.circle_id = old.circle_id
          and wm.user_id = old.user_id
          and wm.week = (circle_week_start(now()) at time zone 'utc')::date
          and wm.left_at is null
          -- An account being deleted takes its roster rows with it (cascade);
          -- touching one here would also trip the foreign key it is leaving.
          and exists (select 1 from auth.users u where u.id = old.user_id);
    end if;
    return old;
end;
$$;
revoke execute on function public.circle_members_before_delete() from public, anon, authenticated;

drop trigger if exists circle_members_before_delete on circle_members;
create trigger circle_members_before_delete
    before delete on circle_members
    for each row execute function public.circle_members_before_delete();

-- A membership row going away, however it goes (leave_circle, a removal, the
-- lifter deleting their account, the cascade from auth.users), must never
-- leave the circle ownerless or empty: the keys pass to the longest-standing
-- remaining member (joined_at, then user id as the tiebreak), and the circle is
-- deleted when nobody remains. A trigger rather than leave_circle's body so
-- every way a membership can end is covered. When the circle row itself is
-- going (cascade), it is already gone here and there is nothing to do.
create or replace function public.circle_members_handover()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    w    circles;
    next uuid;
begin
    select * into w from circles where id = old.circle_id;
    if not found then
        return old;
    end if;
    select m.user_id into next
    from circle_members m
    where m.circle_id = old.circle_id
    order by m.joined_at, m.user_id
    limit 1;
    if next is null then
        delete from circles where id = old.circle_id;
    elsif w.owner_id is null or w.owner_id = old.user_id then
        update circles set owner_id = next where id = old.circle_id;
        -- Tell the new Keeper; the missive names who held the keys before them
        -- (themselves when that account is already gone).
        perform public.circle_notify(array[next], 'circle_keeper', coalesce(w.owner_id, next), w.name);
    end if;
    return old;
end;
$$;
revoke execute on function public.circle_members_handover() from public, anon, authenticated;

drop trigger if exists circle_members_handover on circle_members;
create trigger circle_members_handover
    after delete on circle_members
    for each row execute function public.circle_members_handover();

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
alter table session_set_details enable row level security;
alter table imported_sessions enable row level security;
alter table imported_session_sets enable row level security;
alter table web_snapshots enable row level security;
alter table lift_marks         enable row level security;
alter table circles           enable row level security;
alter table circle_members    enable row level security;
alter table circle_weeks      enable row level security;
alter table circle_week_members enable row level security;
alter table circle_events      enable row level security;

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

-- Foreign keys bypass RLS; check ownership of each referenced parent explicitly.
-- (select auth.uid()) is evaluated once per statement, not once per row.
drop policy if exists session_set_details_owner on session_set_details;
create policy session_set_details_owner on session_set_details
    for all to authenticated
    using (user_id = (select auth.uid()))
    with check (user_id = (select auth.uid()) and exists (
        select 1 from sessions s
        where s.id = session_set_details.session_id and s.user_id = (select auth.uid())
    ));

drop policy if exists imported_sessions_owner on imported_sessions;
create policy imported_sessions_owner on imported_sessions
    for all to authenticated
    using (user_id = (select auth.uid()))
    with check (user_id = (select auth.uid()));

drop policy if exists imported_session_sets_owner on imported_session_sets;
create policy imported_session_sets_owner on imported_session_sets
    for all to authenticated
    using (user_id = (select auth.uid()))
    with check (user_id = (select auth.uid()) and exists (
        select 1 from imported_sessions s
        where s.id = imported_session_sets.session_id and s.user_id = (select auth.uid())
    ));

drop policy if exists web_snapshots_read on web_snapshots;
create policy web_snapshots_read on web_snapshots
    for select to authenticated using (user_id = (select auth.uid()));

drop policy if exists web_snapshots_insert on web_snapshots;
create policy web_snapshots_insert on web_snapshots
    for insert to authenticated with check (user_id = (select auth.uid()));

drop policy if exists web_snapshots_update on web_snapshots;
create policy web_snapshots_update on web_snapshots
    for update to authenticated using (user_id = (select auth.uid()))
    with check (user_id = (select auth.uid()));

-- The owner can remove their own snapshot (nobody else's: the policy is the same ownership test).
drop policy if exists web_snapshots_delete on web_snapshots;
create policy web_snapshots_delete on web_snapshots
    for delete to authenticated using (user_id = (select auth.uid()));

revoke all on session_set_details, imported_sessions, imported_session_sets, web_snapshots from public, anon, authenticated;
grant select, insert, update, delete on session_set_details, imported_sessions, imported_session_sets to authenticated;
grant select, insert, update, delete on web_snapshots to authenticated;

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
grant update (display_name, visibility, current_title_id, current_crest_id) on profiles to authenticated;
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

-- lift_marks: updated_at is server-owned; tier history follows the existing RLS policies.
revoke all on lift_marks from anon, authenticated;
grant select, delete on lift_marks to authenticated;
grant insert (user_id, lift, step, recent_step, recent_at) on lift_marks to authenticated;
grant update (user_id, lift, step, recent_step, recent_at, recent_steps) on lift_marks to authenticated;

-- circles: created only by create_circle(), membership only by the RPCs, so
-- no insert grant on either table. Members read; every write (rename, code,
-- goal, removal, the circle itself) is a definer RPC.
revoke all on circles from anon, authenticated;
grant select on circles to authenticated;
revoke all on circle_members from anon, authenticated;
grant select on circle_members to authenticated;
-- The week records are the definer RPCs' alone: row security on, no policy, no grant.
revoke all on circle_weeks from anon, authenticated;
revoke all on circle_week_members from anon, authenticated;
revoke all on circle_events from anon, authenticated;

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
    if new.started_at < timestamptz '2000-01-01 00:00:00+00'
       or new.completed_at < timestamptz '2000-01-01 00:00:00+00' then
        raise exception 'trial timestamps cannot be before 2000';
    end if;
    if new.started_at > now() + interval '1 day'
       or (new.completed_at is not null and new.completed_at > now() + interval '1 day') then
        raise exception 'trial timestamps cannot be in the future';
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
        raise exception 'You can’t send an ally request to this Ironbound.';
    end if;

    perform pg_advisory_xact_lock(hashtext('friend_requests:' || new.requester_id::text));
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

    -- Serialize both author limits and the shared thread before counting.
    perform pg_advisory_xact_lock(hashtext('comment_author:' || new.user_id::text));
    perform pg_advisory_xact_lock(hashtext('comment_thread:' || new.session_id::text));
    select count(*) into n from session_comments c
    where c.user_id = new.user_id and c.created_at > now() - interval '1 minute';
    if n >= 10 then
        raise exception 'Too many remarks — wait a minute.';
    end if;

    select count(*) into n from session_comments c
    where c.user_id = new.user_id and c.created_at > now() - interval '1 day';
    if n >= 200 then
        raise exception 'Daily remark limit reached — try again tomorrow.';
    end if;

    -- Bounds the one thing a crowd could grow without limit: a single thread.
    select count(*) into n from session_comments c where c.session_id = new.session_id;
    if n >= 200 then
        raise exception 'This trial has reached its remark limit.';
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
    perform pg_advisory_xact_lock(hashtext('reports:' || new.reporter_id::text));
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
        and s.completed_at > now() - interval '7 days') as sessions_last_7d,
    -- Appended last: create or replace view can only add columns at the end.
    p.current_crest_id
from profiles p;

-- A separate population from lifetime XP: inactivity must not reserve the top
-- places forever. Both profiles and session awards remain caller-RLS filtered.
-- Hours, not calendar days, keep this an elapsed seven-day window through DST.
create or replace view workout_xp_leaderboard with (security_invoker = true) as
select
    p.id,
    p.display_name,
    p.level,
    p.total_xp,
    p.streak_days,
    p.titles_count,
    p.lifetime_strength,
    recent.sessions_last_7d,
    p.current_title_id,
    p.current_crest_id,
    recent.workout_xp_last_7d
from profiles p
join (
    select
        s.user_id,
        count(*) as sessions_last_7d,
        -- 100 maximum-sized sessions per week, matching the lifetime XP ceiling.
        least(sum(s.xp_awarded), 100000000)::bigint as workout_xp_last_7d
    from sessions s
    where s.completed_at > now() - interval '168 hours'
      and s.completed_at <= now()
    group by s.user_id
    having sum(s.xp_awarded) > 0
) recent on recent.user_id = p.id;

revoke all on public.workout_xp_leaderboard from public, anon, authenticated;
grant select on public.workout_xp_leaderboard to authenticated;

-- The Shadow Army board: same visibility rules as every other social surface.
create or replace view shadow_board with (security_invoker = true) as
select
    p.id,
    p.display_name,
    p.current_title_id,
    p.level,
    p.shadow_essence,
    p.shadow_count,
    p.shadow_rate,
    p.current_crest_id
from profiles p
order by p.shadow_essence desc;

-- The strength board respects lift_marks and profile RLS; each tier expires independently.
create or replace view lift_board with (security_invoker = true) as
select
    m.user_id,
    p.display_name,
    p.current_title_id,
    p.level,
    m.lift,
    m.step,
    case when m.recent_steps is null then
        case when m.recent_at > now() - interval '7 days' and m.recent_at <= now() then m.recent_step end
    else (
        select max((s.ordinality - 1)::int)
        from unnest(m.recent_steps) with ordinality as s(at_ms, ordinality)
        where s.at_ms > extract(epoch from now() - interval '168 hours') * 1000
          and s.at_ms <= extract(epoch from now()) * 1000
          and s.ordinality - 1 <= m.step
    ) end as recent_step,
    p.current_crest_id
from lift_marks m
join profiles p on p.id = m.user_id;
grant select on lift_board to authenticated;

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

    (select sl.kind from session_likes sl where sl.session_id = s.id and sl.user_id = auth.uid()) as my_reaction,

    -- Appended last: create or replace view can only add columns at the end.
    s.edited_at,
    p.current_crest_id,
    s.feed_card
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
    -- Leave the circle first, while the account still exists: the circle is handed
    -- to its longest-standing member (or ends with its last one) by an
    -- ordinary leave, not left to the cascade below.
    if exists (select 1 from circle_members m where m.user_id = me) then
        perform public.leave_circle();
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

-- ---------------------------------------------------------------- counting
-- The ONE definition of what a circle's week is made of, shared by the roster
-- (my_circle), the settlement (circle_roll) and the goal missive (my_inbox), so
-- the banner, the bonus and the inbox can never disagree about a week.
--
-- All of these are internal: SECURITY DEFINER because they read other lifters'
-- sessions, which a plain member's RLS refuses, and CLOSED to every client
-- role, because asked directly they would be an oracle on anyone's training
-- days. Only the definer RPCs and triggers call them.

-- The UTC Monday that opens the week containing p_at, as a timestamptz. UTC is
-- spelled out so a moved server timezone cannot shift everyone's week; the
-- client shows the reset in the lifter's own clock from the same anchor
-- (circleResetLabel in ui/social/CircleUi.kt).
create or replace function public.circle_week_start(p_at timestamptz)
returns timestamptz
language sql
immutable
as $$ select (date_trunc('week', p_at at time zone 'utc')) at time zone 'utc' $$;
revoke execute on function public.circle_week_start(timestamptz) from public, anon, authenticated;

-- When a week may be settled: a day after it closes. Settling the instant the
-- week rolled over would count a trial that a phone has sealed but not yet
-- pushed (the daily sync is the slowest path) as no trial at all, and a
-- settled week never reopens. A phone offline for longer than the grace misses
-- the week.
-- // shortcut: 24 hours; lengthen it if offline lifters are being missed.
create or replace function public.circle_week_settles_at(p_week date)
returns timestamptz
language sql
immutable
as $$ select ((p_week + 7)::timestamp at time zone 'utc') + interval '24 hours' $$;
revoke execute on function public.circle_week_settles_at(date) from public, anon, authenticated;

-- The days one lifter trained inside [p_from, p_to): the distinct UTC days on
-- which they sealed at least one trial, one row per day however many trials
-- they sealed (at = the first of that day). A trial counts only when it has at
-- least one row in session_sets (an empty trial is not training) and is not in
-- the future (the guard trigger lets a client stamp a day ahead). Visibility is
-- deliberately NOT a condition: the count is one canonical number, the same
-- for every viewer, so two members can never read different totals and
-- disagree about whether the goal was met. It is a bare count of days; the
-- trial, its contents and its audience are never exposed.
create or replace function public.circle_member_days(p_user uuid, p_from timestamptz, p_to timestamptz)
returns table (day date, at timestamptz)
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    select (s.completed_at at time zone 'utc')::date as day,
           min(s.completed_at) as at
    from sessions s
    where s.user_id = p_user
      and s.completed_at is not null
      and s.completed_at >= p_from
      and s.completed_at <  p_to
      and s.completed_at <= now()
      and exists (select 1 from session_sets x where x.session_id = s.id)
    group by 1;
$$;
revoke execute on function public.circle_member_days(uuid, timestamptz, timestamptz) from public, anon, authenticated;

-- The days a circle's week counts: for each lifter on the roster the week
-- OPENED with (circle_week_members), their days from the later of Monday and
-- the day they joined to the earlier of next Monday and the moment they left,
-- the earliest per_member of them (a member's contribution is capped at their
-- share). n numbers the counted days across the whole circle in the order they
-- happened, so "the day that crossed the goal" is the row where n equals goal.
-- Empty for a week that was never opened.
create or replace function public.circle_week_days(p_circle uuid, p_week date)
returns table (user_id uuid, day date, at timestamptz, n int)
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    with cw as (
        select w.per_member, (w.week::timestamp at time zone 'utc') as ws
        from circle_weeks w
        where w.circle_id = p_circle and w.week = p_week
    ),
    ranked as (
        select wm.user_id, d.day, d.at,
               row_number() over (partition by wm.user_id order by d.at) as rk,
               cw.per_member
        from cw
        join circle_week_members wm on wm.circle_id = p_circle and wm.week = p_week
        cross join lateral circle_member_days(
            wm.user_id,
            greatest(cw.ws, wm.joined_at),
            least(cw.ws + interval '7 days', coalesce(wm.left_at, 'infinity'::timestamptz))
        ) d
    )
    select r.user_id, r.day, r.at,
           (row_number() over (order by r.at, r.user_id))::int
    from ranked r
    where r.rk <= r.per_member;
$$;
revoke execute on function public.circle_week_days(uuid, date) from public, anon, authenticated;

-- Opens one week of a circle, if it is not open: freezes the target (applying
-- the Keeper's pending change first, since this is the week it was parked for)
-- and the roster. The roster is whoever was in the circle before the week
-- began, so a circle formed mid-week has a warm-up week with nobody on it and
-- a lifter who joins mid-week counts from the next Monday: neither can be used
-- to collect a bonus the week they arrive. The caller holds the circle's lock.
-- A lifter whose account is being deleted is not put on the roster (the row
-- would reference an auth.users row that is already gone).
create or replace function public.circle_open_week(p_circle uuid, p_week date)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
#variable_conflict use_column
declare
    c  circles;
    ws timestamptz := (p_week::timestamp at time zone 'utc');
    n  int;
begin
    select * into c from circles where id = p_circle;
    if not found then
        return;
    end if;
    if exists (select 1 from circle_weeks w where w.circle_id = p_circle and w.week = p_week) then
        return;
    end if;
    if c.pending_per_member is not null then
        update circles
        set per_member = pending_per_member, pending_per_member = null
        where id = p_circle
        returning * into c;
    end if;
    select count(*) into n
    from circle_members m
    where m.circle_id = p_circle and m.joined_at < ws
      and exists (select 1 from auth.users u where u.id = m.user_id);
    insert into circle_weeks (circle_id, week, per_member, members, goal)
    values (p_circle, p_week, c.per_member, n, n * c.per_member);
    insert into circle_week_members (circle_id, week, user_id, joined_at)
    select p_circle, p_week, m.user_id, m.joined_at
    from circle_members m
    where m.circle_id = p_circle and m.joined_at < ws
      and exists (select 1 from auth.users u where u.id = m.user_id);
end;
$$;
revoke execute on function public.circle_open_week(uuid, date) from public, anon, authenticated;

-- Settles one closed week, once: writes each rostered lifter's counted days and
-- the week's total and met. met needs a roster of at least two, the total at
-- the goal and at least two lifters who trained (with every lifter capped at
-- their share and the goal their shares summed, a met week already has both;
-- the floor is stated here so a change to the cap cannot quietly pay a circle
-- of one). A week already settled is left exactly as it is: the answer is the
-- same for every member for ever.
create or replace function public.circle_settle_week(p_circle uuid, p_week date)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
#variable_conflict use_column
declare
    cw           circle_weeks;
    v_total      int;
    v_contributors int;
begin
    select * into cw from circle_weeks w where w.circle_id = p_circle and w.week = p_week for update;
    if not found or cw.settled_at is not null then
        return;
    end if;
    update circle_week_members wm
    set days = (select count(*) from circle_week_days(p_circle, p_week) d where d.user_id = wm.user_id)
    where wm.circle_id = p_circle and wm.week = p_week;
    select coalesce(sum(wm.days), 0), count(*) filter (where wm.days >= 1)
    into v_total, v_contributors
    from circle_week_members wm
    where wm.circle_id = p_circle and wm.week = p_week;
    update circle_weeks w
    set total = v_total,
        met = cw.members >= 2 and cw.goal > 0 and v_total >= cw.goal and v_contributors >= 2,
        settled_at = now()
    where w.circle_id = p_circle and w.week = p_week;
end;
$$;
revoke execute on function public.circle_settle_week(uuid, date) from public, anon, authenticated;

-- Brings a circle's weeks up to date: opens every week from the last one on
-- record (or the week the circle was formed) to this one, then settles every
-- closed week that is due. Idempotent and cheap when there is nothing to do
-- (that check takes no lock, so a plain read never queues behind another). It
-- is called before anything that changes membership and from every read, so a
-- week settles on the first look after it closes without anyone having to open
-- a particular screen. A circle unread for more than eight weeks resumes from
-- eight weeks back: older weeks stay unsettled.
-- // shortcut: eight weeks of catch-up; raise it if circles go unread longer.
create or replace function public.circle_roll(p_circle uuid)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
#variable_conflict use_column
declare
    cur    date := (circle_week_start(now()) at time zone 'utc')::date;
    c      circles;
    latest date;
    wk     date;
begin
    if exists (select 1 from circle_weeks w where w.circle_id = p_circle and w.week = cur)
       and not exists (select 1 from circle_weeks w
                       where w.circle_id = p_circle and w.settled_at is null and w.week < cur
                         and circle_week_settles_at(w.week) <= now()) then
        return;
    end if;
    select * into c from circles where id = p_circle for update;
    if found then
        select max(w.week) into latest from circle_weeks w where w.circle_id = p_circle;
        wk := greatest(
            coalesce(latest + 7, (circle_week_start(coalesce(c.created_at, now())) at time zone 'utc')::date),
            cur - 56
        );
        while wk <= cur loop
            perform public.circle_open_week(p_circle, wk);
            wk := wk + 7;
        end loop;
    end if;
    for wk in
        select w.week from circle_weeks w
        where w.circle_id = p_circle and w.settled_at is null and w.week < cur
          and circle_week_settles_at(w.week) <= now()
        order by w.week
    loop
        perform public.circle_settle_week(p_circle, wk);
    end loop;
end;
$$;
revoke execute on function public.circle_roll(uuid) from public, anon, authenticated;

-- SECURITY DEFINER because a request comes from a lifter whose profile the
-- caller usually cannot read yet (that is why they are asking), and the name
-- is no secret: find_hunter already resolves it. Every branch is pinned to
-- auth.uid(), so it can only ever answer about the caller. Definer rights also
-- mean RLS does not filter these rows, so a branch that reads someone else's
-- workout restates the read policy (can_view_session) itself.
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

        -- A remark in a thread the caller joined on someone else's workout:
        -- anything said after the caller's first remark there, by anyone but
        -- the caller. Only while the workout is still visible to the caller,
        -- exactly as the comments read policy would allow. Driven from the
        -- caller's own remarks (session_comments_user_idx), then each thread
        -- (session_comments_session_idx), never a scan of everyone's.
        select 'reply', c.created_at, c.user_id, c.author_name,
               s.id, coalesce(nullif(s.title, ''), s.label), c.id, c.body, null
        from me
        join lateral (
            select mine.session_id, min(mine.created_at) as first_at
            from session_comments mine
            where mine.user_id = me.id
            group by mine.session_id
        ) joined on true
        join sessions s on s.id = joined.session_id and s.user_id <> me.id
        join session_comments c on c.session_id = joined.session_id
                               and c.created_at > joined.first_at
                               and c.user_id <> me.id
        where can_view_session(s.user_id, s.audience)

        union all

        select 'reaction', l.created_at, l.user_id, p.display_name,
               s.id, coalesce(nullif(s.title, ''), s.label), null, null, l.kind
        from session_likes l
        join sessions s on s.id = l.session_id
        join profiles p on p.id = l.user_id
        join me on s.user_id = me.id
        where l.user_id <> me.id

        union all

        -- A lifter joined the caller's circle: the roster is the feed's peer,
        -- so its door opening is inbox-worthy like a request or an acceptance.
        -- Only a TRUE join: someone who arrived after the caller did. Without
        -- that, a lifter who has just joined would be told that everyone
        -- already in the circle had "just joined". Identity follows profile
        -- visibility, as the roster shows it.
        select 'band_join', m.joined_at, m.user_id,
               case when can_view(m.user_id) then p.display_name
                    else 'Ironbound' || right(m.user_id::text, 4) end,
               null, null, null, w.name, null
        from circle_members m
        join circles w on w.id = m.circle_id
        left join profiles p on p.id = m.user_id
        join me on true
        join circle_members mine on mine.circle_id = m.circle_id and mine.user_id = me.id
        where m.user_id <> me.id
          and m.joined_at > mine.joined_at

        union all

        -- The caller's circle met its weekly goal: one row per week, at the day
        -- that crossed it, attributed to whoever trained that day. Read from the
        -- weeks the circle has opened (so the goal and roster are the ones the
        -- week FROZE) and counted by circle_week_days(), exactly as my_circle()
        -- counts the banner, so the inbox and the banner agree on when the goal
        -- fell. The current week and the five before it: far enough back to
        -- cover the inbox's 30 days and a whole week more. A week the circle
        -- has not opened yet cannot have been crossed; the next read opens it.
        -- Muted or blocked members are not filtered out of THIS row (see the
        -- final where): the circle met its goal and the bonus is paid whoever
        -- trained that day. They are only not named: a mute or a block turns
        -- the name into the neutral handle.
        select 'band_goal', g.at, g.user_id,
               case when g.user_id = me.id
                         or (can_view(g.user_id)
                             and not exists (select 1 from mutes mm
                                             where mm.muter_id = me.id and mm.muted_id = g.user_id))
                    then coalesce(p.display_name, 'Ironbound' || right(g.user_id::text, 4))
                    else 'Ironbound' || right(g.user_id::text, 4) end,
               null, null, null, c.name, null
        from me
        join circle_members mine on mine.user_id = me.id
        join circles c on c.id = mine.circle_id
        join circle_weeks cw on cw.circle_id = c.id
                            and cw.week >= (circle_week_start(now()) at time zone 'utc')::date - 35
                            and cw.members >= 2 and cw.goal > 0
        join lateral circle_week_days(c.id, cw.week) g on g.n = cw.goal
        left join profiles p on p.id = g.user_id

        union all

        -- Roster missives (circle_events): someone left, the keys passed to the
        -- caller, the caller was removed. The circle's name rides in body.
        select e.kind, e.occurred_at, e.actor_id,
               case when e.actor_id = me.id or can_view(e.actor_id)
                    then coalesce(p.display_name, 'Ironbound' || right(e.actor_id::text, 4))
                    else 'Ironbound' || right(e.actor_id::text, 4) end,
               null, null, null, e.circle_name, null
        from circle_events e
        join me on e.user_id = me.id
        left join profiles p on p.id = e.actor_id
    )
    select i.kind, i.occurred_at, i.actor_id, i.actor_name, i.session_id,
           i.session_headline, i.comment_id, i.body, i.reaction
    from items i, me
    where i.occurred_at > now() - interval '30 days'
      and (i.kind = 'band_goal'
           or (not blocked_between(me.id, i.actor_id)
               and not exists (select 1 from mutes m where m.muter_id = me.id and m.muted_id = i.actor_id)))
    order by i.occurred_at desc
    limit 100;
$$;
revoke execute on function public.my_inbox() from public, anon;
grant execute on function public.my_inbox() to authenticated;

-- ---------------------------------------------------------------- circles
-- The invite-code alphabet circle_draw_code() draws from: digits 2-9 and letters
-- minus I, L and O — 31 unambiguous glyphs. Keep in step with
-- InviteCodeAlphabet in domain/Circles.kt and the check on circles.invite_code.

-- Creates a circle with the caller as owner and only member, atomically. A code
-- collision is absorbed by the unique index and another draw. SECURITY DEFINER
-- because it writes two tables in one statement; every branch is pinned to
-- auth.uid(), so it can only ever act on the caller.
--
-- The messages the lifter can read are written in the app's own words (see
-- docs/GLOSSARY.md): the client shows a P0001 message as it is, and GlossaryTest
-- scans these bodies for the words the glossary retired.
create or replace function public.create_circle(p_name text)
returns circles
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    me       uuid := auth.uid();
    w        circles;
begin
    if me is null then
        raise exception 'Sign in to use a circle' using errcode = '42501';
    end if;
    if p_name is null or char_length(trim(p_name)) not between 1 and 24 then
        raise exception 'A circle name is 1-24 characters';
    end if;
    -- One circle per lifter: an existing membership wins before any insert.
    if exists (select 1 from circle_members m where m.user_id = me) then
        raise exception 'You are already in a circle — leave it first';
    end if;
    loop
        begin
            insert into circles (name, invite_code, owner_id)
            values (trim(p_name), public.circle_draw_code(), me)
            returning * into w;
            exit;
        exception when unique_violation then
            null; -- code already drawn: try another
        end;
    end loop;
    insert into circle_members (circle_id, user_id) values (w.id, me);
    return w;
end;
$$;
revoke execute on function public.create_circle(text) from public, anon;
grant execute on function public.create_circle(text) to authenticated;

-- Joins by code, case-insensitively, while there is room (max 8), and answers
-- with a STATUS rather than raising: 'joined', 'no_such_code', 'full', 'closed'
-- (a member is blocked either way with the caller) or 'throttled'. A refusal that raised would roll back the attempt it had just
-- logged, and the throttle below would never count anything. Only misuse that
-- is not a guess (signed out, already in a circle) still raises. SECURITY
-- DEFINER for the same reason as create_circle.
--
-- Return type changed in schema 27 (it returned the circles row): create or
-- replace cannot change a return type, so the old shape goes first. The
-- function holds no data.
drop function if exists public.join_circle(text);
create or replace function public.join_circle(p_code text)
returns text
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    me uuid := auth.uid();
    w  circles;
    n  int;
begin
    if me is null then
        raise exception 'Sign in to use a circle' using errcode = '42501';
    end if;
    if exists (select 1 from circle_members m where m.user_id = me) then
        raise exception 'You are already in a circle — leave it first';
    end if;
    -- Throttle the one guessable door: failed attempts only, 50 a day, the
    -- same rolling-window pattern as ally requests.
    perform pg_advisory_xact_lock(hashtext('circle_guesses:' || me::text));
    delete from circle_join_log l where l.user_id = me and l.tried_at <= now() - interval '1 day';
    select count(*) into n from circle_join_log l where l.user_id = me;
    if n >= 50 then
        return 'throttled';
    end if;
    -- Lock the circle row: the cap check and the insert must read as one step,
    -- or two simultaneous joins at seven can land both at nine, and a leave
    -- cannot delete the circle between the lookup and the insert. Leaves take the
    -- same lock first.
    select * into w from circles where invite_code = upper(trim(p_code)) for update;
    if not found then
        insert into circle_join_log (user_id) values (me);
        return 'no_such_code';
    end if;
    -- A circle holding someone the caller blocked, or who blocked the caller, is
    -- closed to them: the same neutral answer either way, so it says nothing of
    -- who blocked whom. It counts as an attempt, like any other refusal.
    if exists (select 1 from circle_members m
               where m.circle_id = w.id and blocked_between(me, m.user_id)) then
        insert into circle_join_log (user_id) values (me);
        return 'closed';
    end if;
    select count(*) into n from circle_members where circle_id = w.id;
    if n >= 8 then
        insert into circle_join_log (user_id) values (me);
        return 'full';
    end if;
    insert into circle_members (circle_id, user_id) values (w.id, me);
    return 'joined';
end;
$$;
revoke execute on function public.join_circle(text) from public, anon;
grant execute on function public.join_circle(text) to authenticated;

-- Leaves the circle. The circle row is locked first (joins take the same lock), then
-- the membership row goes, and circle_members_handover passes the keys to the
-- longest-standing remaining member, or deletes the circle with its last one.
create or replace function public.leave_circle()
returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    me   uuid := auth.uid();
    circle uuid;
begin
    if me is null then
        raise exception 'Sign in to use a circle' using errcode = '42501';
    end if;
    select circle_id into circle from circle_members where user_id = me;
    if circle is null then
        raise exception 'You are not in a circle';
    end if;
    perform 1 from circles where id = circle for update;
    perform public.circle_notify(
        array(select m.user_id from circle_members m where m.circle_id = circle and m.user_id <> me),
        'circle_left', me, (select c.name from circles c where c.id = circle));
    delete from circle_members where user_id = me;
end;
$$;
revoke execute on function public.leave_circle() from public, anon;
grant execute on function public.leave_circle() to authenticated;

-- The Keeper sets the circle's weekly target: how many days each member aims to
-- train (1-7). The change applies from the NEXT week: this week's goal is
-- frozen, so the target cannot be lowered on a Sunday to collect a bonus.
-- Setting the value the week already has clears a parked change. Keeper-only,
-- and only while a member (anyone else is refused with 42501, the same code a
-- revoked grant gives). SECURITY DEFINER like the other circle RPCs; it writes
-- the circles row, which has no update grant. The circle is brought up to date
-- first, so a change parked last week has already been applied to this one.
--
-- The argument changed meaning in schema 27 (it was a total of 5-50 trials for
-- the whole circle) and name with it; a different argument list is a different
-- function, so the old one goes first. The function holds no data.
drop function if exists public.set_circle_goal(int);
create or replace function public.set_circle_goal(p_per_member int)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    cid uuid;
    cur int;
begin
    cid := public.circle_keeper_lock('sets the circle''s goal');
    if p_per_member is null or p_per_member not between 1 and 7 then
        raise exception 'A weekly goal is 1-7 days';
    end if;
    perform public.circle_roll(cid);
    select w.per_member into cur from circles w where w.id = cid;
    update circles
    set pending_per_member = case when p_per_member = cur then null else p_per_member end
    where id = cid;
end;
$$;
revoke execute on function public.set_circle_goal(int) from public, anon;
grant execute on function public.set_circle_goal(int) to authenticated;

-- Renames the circle. Keeper-only; the name is stored trimmed, 1-24 characters.
create or replace function public.set_circle_name(p_name text)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    cid uuid;
begin
    cid := public.circle_keeper_lock('renames the circle');
    if p_name is null or char_length(trim(p_name)) not between 1 and 24 then
        raise exception 'A circle name is 1-24 characters';
    end if;
    update circles set name = trim(p_name) where id = cid;
end;
$$;
revoke execute on function public.set_circle_name(text) from public, anon;
grant execute on function public.set_circle_name(text) to authenticated;

-- Draws a fresh invite code and retires the old one: the way to close the door
-- on a code that travelled further than intended. Keeper-only; answers the new
-- code. Existing members are untouched.
create or replace function public.rotate_circle_code()
returns text
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    cid  uuid;
    code text;
begin
    cid := public.circle_keeper_lock('changes the circle code');
    loop
        begin
            update circles set invite_code = public.circle_draw_code()
            where id = cid
            returning invite_code into code;
            exit;
        exception when unique_violation then
            null; -- code already drawn: try another
        end;
    end loop;
    return code;
end;
$$;
revoke execute on function public.rotate_circle_code() from public, anon;
grant execute on function public.rotate_circle_code() to authenticated;

-- Removes a member. Keeper-only, and never the Keeper themselves (they leave).
-- The removed lifter is told, and may rejoin with the code: rotate it first to
-- keep them out. Their days this week still count, as for anyone who leaves.
create or replace function public.kick_circle_member(p_user uuid)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    me  uuid := auth.uid();
    cid uuid;
begin
    cid := public.circle_keeper_lock('removes members');
    if p_user is null or p_user = me then
        raise exception 'The Keeper cannot be removed — leave the circle instead';
    end if;
    if not exists (select 1 from circle_members m where m.circle_id = cid and m.user_id = p_user) then
        raise exception 'That Ironbound is not in your circle';
    end if;
    perform public.circle_notify(array[p_user], 'circle_removed', me,
                                 (select c.name from circles c where c.id = cid));
    delete from circle_members where user_id = p_user;
end;
$$;
revoke execute on function public.kick_circle_member(uuid) from public, anon;
grant execute on function public.kick_circle_member(uuid) to authenticated;

-- The caller's circle and roster, or no rows when they are in none. VOLATILE:
-- reading the circle brings its weeks up to date (circle_roll), which is how a
-- closed week gets settled by whoever looks first. SECURITY DEFINER because
-- the count reads fellow members' sessions, which a plain member's RLS refuses.
--
-- The week is the CURRENT Monday-start week (UTC). week, per_member, goal and
-- roster are what this week OPENED with (frozen); pending_per_member is the
-- Keeper's parked change for next week. goal is 0 while the roster holds fewer
-- than two members: a circle is a circle of two or more. circle_total is the
-- circle's counted days so far, ONE canonical number for every viewer; each
-- member's days_this_week is their own counted days (capped at their share),
-- and counts says whether they are on this week's roster (a lifter who joined
-- this week is not, and counts from Monday). A member who has left still
-- counts in circle_total, so the listed days can add up to less than it.
-- Only last_workout_at, which names a moment of a particular trial, follows the
-- feed's visibility (can_view_session): a private trial's time is never handed
-- over. weeks_met is how many weeks the circle has settled as met.
--
-- Identity follows the profile's own visibility: a member whose profile the
-- caller cannot view lists under the neutral handle, with level and worn
-- title withheld, exactly as anywhere else in the app.
--
-- The return row was weekly_goal/band_total until schema 27, and create or
-- replace cannot change a row type in place: drop the old shape first.
-- Idempotent, and the function holds no data.
drop function if exists public.my_circle();
create or replace function public.my_circle()
returns table (
    id uuid, name text, code text, owner_id uuid,
    week date, per_member int, pending_per_member int,
    goal int, circle_total int, roster int, weeks_met int,
    members jsonb
)
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
#variable_conflict use_column
declare
    me  uuid := auth.uid();
    cid uuid;
    cur date := (circle_week_start(now()) at time zone 'utc')::date;
begin
    select m.circle_id into cid from circle_members m where m.user_id = me;
    if cid is null then
        return;
    end if;
    perform public.circle_roll(cid);
    return query
    with cw as (
        select w.* from circle_weeks w where w.circle_id = cid and w.week = cur
    ),
    days as (
        select d.user_id, d.day from circle_week_days(cid, cur) d
    )
    select c.id, c.name, c.invite_code, c.owner_id,
           cur,
           coalesce(cw.per_member, c.per_member),
           c.pending_per_member,
           case when coalesce(cw.members, 0) >= 2 then cw.goal else 0 end,
           (select count(*)::int from days),
           coalesce(cw.members, 0),
           (select count(*)::int from circle_weeks w where w.circle_id = cid and w.met),
           (
            select jsonb_agg(jsonb_build_object(
                       'user_id', m.user_id,
                       -- A member whose profile row is missing still lists,
                       -- under the neutral handle shape the sign-up trigger uses.
                       'display_name', case when m.user_id = me or can_view(m.user_id)
                                            then coalesce(p.display_name, 'Ironbound' || right(m.user_id::text, 4))
                                            else 'Ironbound' || right(m.user_id::text, 4) end,
                       'level', case when m.user_id = me or can_view(m.user_id)
                                     then coalesce(p.level, 1) end,
                       'current_title_id', case when m.user_id = me or can_view(m.user_id)
                                                then p.current_title_id end,
                       'current_crest_id', case when m.user_id = me or can_view(m.user_id)
                                                then p.current_crest_id end,
                       'counts', r.counts,
                       'days_this_week', least(
                           case when r.counts
                                then (select count(*) from days dd where dd.user_id = m.user_id)
                                else (select count(*) from circle_member_days(
                                          m.user_id,
                                          greatest(circle_week_start(now()), m.joined_at),
                                          circle_week_start(now()) + interval '7 days'))
                           end,
                           coalesce(cw.per_member, c.per_member)),
                       'last_workout_at', (
                           select max(s.completed_at)
                           from sessions s
                           where s.user_id = m.user_id
                             and s.completed_at is not null
                             and s.completed_at >= circle_week_start(now())
                             and s.completed_at <= now()
                             and can_view_session(s.user_id, s.audience)
                       )
                   ) order by m.joined_at, m.user_id)
            from circle_members m
            left join profiles p on p.id = m.user_id
            cross join lateral (
                select exists (
                    select 1 from circle_week_members wm
                    where wm.circle_id = cid and wm.week = cur and wm.user_id = m.user_id
                      and wm.left_at is null
                ) as counts
            ) r
            where m.circle_id = cid
           )
    from circles c
    left join cw on true
    where c.id = cid;
end;
$$;
revoke execute on function public.my_circle() from public, anon;
grant execute on function public.my_circle() to authenticated;

-- The weekly bonus a lifter is owed, from the SETTLED record: one row per week
-- the lifter was on the roster of a circle that met its goal, for the two most
-- recent weeks that have closed, with the lifter's own counted days. It is the
-- server's answer and the same for everyone; each phone pays it once per lifter
-- and week (CirclePayout). Reading it settles whatever is due first, in the
-- circle the lifter is in and in any circle they have left since, so a lifter
-- who leaves straight after a met week still collects it, and nobody has to
-- open a particular screen before the Monday after to be paid. A lifter who
-- joined the circle after the week opened is not on that week's roster and is
-- owed nothing for it.
create or replace function public.circle_bonuses()
returns table (week date, days int, circle_name text)
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
#variable_conflict use_column
declare
    me  uuid := auth.uid();
    cid uuid;
    cur date := (circle_week_start(now()) at time zone 'utc')::date;
begin
    if me is null then
        raise exception 'Sign in to use a circle' using errcode = '42501';
    end if;
    -- Deleted circles have no future weeks; retain only the payable bonus window.
    delete from circle_weeks w
    where w.week < cur - 14
      and not exists (select 1 from circles c where c.id = w.circle_id);
    for cid in
        select distinct wm.circle_id from circle_week_members wm
        where wm.user_id = me and wm.week >= cur - 28
    loop
        perform public.circle_roll(cid);
    end loop;
    select m.circle_id into cid from circle_members m where m.user_id = me;
    if cid is not null then
        perform public.circle_roll(cid);
    end if;
    return query
    select wm.week, wm.days, c.name
    from circle_week_members wm
    join circle_weeks cw on cw.circle_id = wm.circle_id and cw.week = wm.week
    left join circles c on c.id = wm.circle_id
    where wm.user_id = me
      and cw.met is true
      and wm.days >= 1
      and wm.week >= cur - 14
    order by wm.week;
end;
$$;
revoke execute on function public.circle_bonuses() from public, anon;
grant execute on function public.circle_bonuses() to authenticated;

-- History starts now. A circle that predates the week records (a migrated
-- warband, or one never read since) opens only the CURRENT week, so the first
-- read after an upgrade cannot back-fill past weeks under today's roster and
-- settle or pay them under rules nobody trained against. A circle with a week
-- on record is left alone: this is a no-op on every run after the first.
do $history$
begin
    perform public.circle_open_week(c.id, (public.circle_week_start(now()) at time zone 'utc')::date)
    from circles c
    where not exists (select 1 from circle_weeks w where w.circle_id = c.id);
end
$history$;

-- ================================================================ sign-up

-- Creates the profiles row when GoTrue creates the auth user. The app used to
-- insert its own row right after sign-up, but with email confirmation ON there
-- is no session yet, so that insert ran as anon and failed 42501.
--
-- Name: the sign-up metadata key display_name, trimmed, when it is 2..24 chars
-- and not taken (case-insensitively, the unique index on lower(display_name)).
-- Otherwise a neutral handle 'Ironbound' plus four digits, which is exactly the
-- shape the app's claim-your-name panel recognises (^(?:Hunter|Lifter|Ironbound)\d{4}$).
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
        handle := 'Ironbound' || lpad(((start_at + i) % 10000)::text, 4, '0');
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

-- MCP schema 36. Keep this transaction identical in the fresh baseline.
begin;
create schema if not exists ironvellum_mcp;
revoke all on schema ironvellum_mcp from public, anon, authenticated, service_role;
set local search_path = pg_catalog, ironvellum_mcp;

create table if not exists ironvellum_mcp.config (
  singleton boolean primary key default true check(singleton), resource_url text not null
);
create table if not exists ironvellum_mcp.clients (
  id text primary key, name text not null, ciphertext text not null, redirects text[] not null,
  created_at timestamptz not null default now()
);
create index if not exists mcp_registrations on ironvellum_mcp.clients(created_at);
create table if not exists ironvellum_mcp.requests (
  id uuid primary key default gen_random_uuid(), client_id text not null references clients(id) on delete cascade,
  resource_url text not null, redirect_uri text not null, redirect_explicit boolean not null,
  scopes text[] not null, challenge text not null, state text, decided boolean not null default false,
  expires_at timestamptz not null default now()+interval '10 minutes'
);
create table if not exists ironvellum_mcp.grants (
  id uuid primary key default gen_random_uuid(), owner_id uuid not null references auth.users(id) on delete cascade,
  client_id text not null references clients(id), resource_url text not null, scopes text[] not null,
  created_at timestamptz not null default now(), expires_at timestamptz not null default now()+interval '90 days',
  revoked_at timestamptz
);
create index if not exists mcp_owner_grants on ironvellum_mcp.grants(owner_id,created_at desc);
create table if not exists ironvellum_mcp.tokens (
  hash text primary key check(hash ~ '^[a-f0-9]{64}$'), kind text not null check(kind in ('code','access','refresh')),
  grant_id uuid not null references grants(id) on delete cascade, scopes text[] not null,
  expires_at timestamptz not null, used boolean not null default false,
  challenge text, redirect_uri text, redirect_explicit boolean
);
create index if not exists mcp_grant_tokens on ironvellum_mcp.tokens(grant_id);
create table if not exists ironvellum_mcp.movements (
  key text primary key, name text not null, metric text not null
);
create table if not exists ironvellum_mcp.drafts (
  id uuid primary key default gen_random_uuid(), owner_id uuid not null references auth.users(id) on delete cascade,
  client_id text not null references clients(id), grant_id uuid references grants(id) on delete set null,
  idempotency_key text not null, content jsonb not null, content_hash text not null,
  created_at timestamptz not null default now(), expires_at timestamptz not null default now()+interval '30 days',
  status text not null default 'pending' check(status in ('pending','approved','accepted','rejected','expired')),
  changed_at timestamptz not null default now(), unique(owner_id,client_id,idempotency_key)
);
create index if not exists mcp_draft_quota on ironvellum_mcp.drafts(owner_id,client_id,created_at desc);
create index if not exists mcp_draft_expiry on ironvellum_mcp.drafts(expires_at) where status='pending';
create table if not exists ironvellum_mcp.audit (
  id uuid primary key default gen_random_uuid(), owner_id uuid not null references auth.users(id) on delete cascade,
  client_id text not null references clients(id), tool text not null, at_time timestamptz not null default clock_timestamp(),
  result text not null, draft_id uuid
);
create index if not exists mcp_tool_quota on ironvellum_mcp.audit(owner_id,client_id,at_time desc);
create index if not exists mcp_recent_access on ironvellum_mcp.audit(owner_id,at_time desc);
create index if not exists mcp_audit_retention on ironvellum_mcp.audit(at_time);
do $$
declare t regclass;
begin
  for t in select oid::regclass from pg_class where relnamespace='ironvellum_mcp'::regnamespace and relkind='r' loop
    execute format('alter table %s enable row level security',t);
  end loop;
end $$;
revoke all on all tables in schema ironvellum_mcp from public, anon, authenticated, service_role;

create or replace function ironvellum_mcp.ok(value jsonb default '{}'::jsonb) returns jsonb
language sql immutable set search_path=pg_catalog as $$
 select value || '{"schemaVersion":1,"ok":true}'::jsonb
$$;
create or replace function ironvellum_mcp.fail(reason text) returns jsonb
language sql immutable set search_path=pg_catalog as $$
 select jsonb_build_object('schemaVersion',1,'ok',false,'error',reason)
$$;
create or replace function ironvellum_mcp.ms(value timestamptz) returns bigint
language sql immutable strict set search_path=pg_catalog as $$ select floor(extract(epoch from value)*1000)::bigint $$;
create or replace function ironvellum_mcp.shape(obj jsonb, required text[], optional text[] default '{}') returns boolean
language sql immutable set search_path=pg_catalog as $$
 select coalesce(jsonb_typeof(obj)='object' and obj ?& required and obj - (required || optional) = '{}',false)
$$;
create or replace function ironvellum_mcp.str(obj jsonb, k text, lo int, hi int) returns boolean
language sql immutable set search_path=pg_catalog as $$
 select coalesce(jsonb_typeof(obj->k)='string' and char_length(obj->>k) between lo and hi,false)
$$;
create or replace function ironvellum_mcp.intval(obj jsonb, k text, lo int, hi int) returns boolean
language sql immutable set search_path=pg_catalog as $$
 select case when jsonb_typeof(obj->k)='number' then (obj->>k)::numeric between lo and hi and trunc((obj->>k)::numeric)=(obj->>k)::numeric else false end
$$;
create or replace function ironvellum_mcp.valid_scopes(s text[]) returns boolean
language sql immutable set search_path=pg_catalog as $$
 select coalesce(cardinality(s) between 1 and 4 and array_position(s,null) is null and
   s <@ array['training:read','progress:read','savedworkouts:read','workoutdrafts:create'] and
   cardinality(s)=(select count(distinct x) from unnest(s) x),false)
$$;
create or replace function ironvellum_mcp.app_owner() returns uuid
language plpgsql stable set search_path=pg_catalog as $$
declare uid uuid := auth.uid(); claims jsonb := nullif(current_setting('request.jwt.claims',true),'')::jsonb;
begin
 if uid is null or coalesce(claims ? 'client_id',false) or nullif(current_setting('request.jwt.claim.client_id',true),'') is not null then
   raise exception 'Direct sign-in required' using errcode='42501';
 end if;
 return uid;
end $$;
create or replace function ironvellum_mcp.envelope(d ironvellum_mcp.drafts) returns jsonb
language sql stable set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
 select jsonb_build_object('id',d.id,'revision',1,'contentHash',d.content_hash,'clientName',c.name,
  'createdAtMs',ms(d.created_at),'expiresAtMs',ms(d.expires_at),'status',
  case when d.status='pending' and d.expires_at<=clock_timestamp() then 'expired' else d.status end,'draft',d.content)
 from clients c where c.id=d.client_id
$$;
create or replace function public.ironvellum_mcp_consent(p_request_id uuid,p_approved boolean,p_scopes text[],p_code_hash text)
returns jsonb language plpgsql security definer set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare uid uuid:=app_owner(); r requests; gid uuid;
begin
 select * into r from requests where id=p_request_id for update;
 if not found or r.decided or r.expires_at<=clock_timestamp() or p_approved is null then return fail('invalid_request'); end if;
 if p_approved then
   if not valid_scopes(p_scopes) or not p_scopes <@ r.scopes then return fail('invalid_scope'); end if;
   if p_code_hash is null or p_code_hash !~ '^[a-f0-9]{64}$' then return fail('invalid_request'); end if;
   insert into grants(owner_id,client_id,resource_url,scopes) values(uid,r.client_id,r.resource_url,p_scopes) returning id into gid;
   insert into tokens(hash,kind,grant_id,scopes,expires_at,challenge,redirect_uri,redirect_explicit)
     values(p_code_hash,'code',gid,p_scopes,clock_timestamp()+interval '2 minutes',r.challenge,r.redirect_uri,r.redirect_explicit);
 elsif cardinality(p_scopes)>0 or p_code_hash is not null then return fail('invalid_request');
 end if;
 update requests set decided=true where id=r.id;
 return ok(jsonb_build_object('approved',p_approved));
exception when unique_violation then return fail('invalid_request');
end $$;
create or replace function public.ironvellum_mcp_connections() returns jsonb
language plpgsql security definer set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare uid uuid:=app_owner();
begin
 return ok(jsonb_build_object('resourceUrl',(select resource_url from config),'connections',coalesce((
   select jsonb_agg(jsonb_build_object('id',g.id,'clientName',c.name,'scopes',g.scopes,
     'createdAtMs',ms(g.created_at),'expiresAtMs',ms(g.expires_at),'revokedAtMs',ms(g.revoked_at)) order by g.created_at desc,g.id)
   from grants g join clients c on c.id=g.client_id where g.owner_id=uid),'[]'::jsonb),
   'recentAccess',coalesce((select jsonb_agg(jsonb_build_object('requestId',a.id,'clientName',c.name,
     'tool',a.tool,'atMs',ms(a.at_time),'result',a.result,'draftId',a.draft_id) order by a.at_time desc,a.id)
     from (select * from audit where owner_id=uid and at_time>now()-interval '30 days' order by at_time desc,id limit 50) a
     join clients c on c.id=a.client_id),'[]'::jsonb)));
end $$;
create or replace function public.ironvellum_mcp_revoke_connection(p_grant_id uuid) returns jsonb
language plpgsql security definer set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare uid uuid:=app_owner();
begin
 update grants set revoked_at=coalesce(revoked_at,clock_timestamp()) where id=p_grant_id and owner_id=uid;
 if not found then return fail('not_found'); end if;
 return ok(jsonb_build_object('revoked',true));
end $$;
create or replace function public.ironvellum_mcp_drafts(p_limit integer) returns jsonb
language plpgsql security definer set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare uid uuid:=app_owner();
begin
 if p_limit is null or p_limit not between 1 and 50 then return fail('invalid_arguments'); end if;
 return ok(jsonb_build_object('drafts',coalesce((select jsonb_agg(envelope(d) order by d.created_at desc,d.id)
   from (select * from drafts where owner_id=uid and (status='approved' or status='pending' and expires_at>now())
         order by created_at desc,id limit p_limit) d),'[]'::jsonb)));
end $$;
create or replace function public.ironvellum_mcp_draft(p_draft_id uuid) returns jsonb
language plpgsql security definer set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare uid uuid:=app_owner(); d drafts;
begin
 select * into d from drafts where owner_id=uid and id=p_draft_id;
 if not found then return fail('not_found'); end if;
 return ok(jsonb_build_object('draft',envelope(d)));
end $$;
create or replace function public.ironvellum_mcp_review_draft(p_draft_id uuid,p_hash text,p_action text) returns jsonb
language plpgsql security definer set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare uid uuid:=app_owner(); d drafts; dest text;
begin
 select * into d from drafts where owner_id=uid and id=p_draft_id for update;
 if not found then return fail('not_found'); end if;
 if p_hash is distinct from d.content_hash then return fail('conflict'); end if;
 if p_action is null or p_action not in ('approve','accept','reject') then return fail('invalid_arguments'); end if;
 if d.status='pending' and d.expires_at<=clock_timestamp() then
   update drafts set status='expired',changed_at=clock_timestamp() where id=d.id;
   return fail('conflict');
 end if;
 dest:=case when p_action='approve' and d.status='pending' then 'approved'
            when p_action='approve' and d.status in ('approved','accepted') then d.status
            when p_action='accept' and d.status in ('approved','accepted') then 'accepted'
            when p_action='reject' and d.status='pending' then 'rejected' end;
 if dest is null then return fail('conflict'); end if;
 if dest<>d.status then update drafts set status=dest,changed_at=clock_timestamp() where id=d.id returning * into d; end if;
 return ok(jsonb_build_object('draft',envelope(d)));
end $$;

-- A scalar type guard is essential: a JSON object in a permitted leaf must never
-- smuggle other fields through a projection.
create or replace function ironvellum_mcp.leaf(obj jsonb,k text,typ text) returns jsonb
language sql immutable set search_path=pg_catalog as $$
 select case when jsonb_typeof(obj->k)=typ and octet_length((obj->k)::text)<=512 then obj->k end
$$;
-- Keys and expected scalar types are fixed by the private callers below.
create or replace function ironvellum_mcp.project_fields(obj jsonb, fields jsonb) returns jsonb
language sql immutable set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
 select case when jsonb_typeof(obj)='object' then
   (select jsonb_object_agg(k,leaf(obj,k,t)) from jsonb_each_text(fields) f(k,t)) end
$$;
create or replace function ironvellum_mcp.progress(content jsonb) returns jsonb
language plpgsql immutable set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare week jsonb; trends jsonb; weeks jsonb; lifts jsonb; lift jsonb; variants jsonb; v jsonb; projected jsonb;
        n int; excess boolean;
begin
 week:=project_fields(content->'week','{"startDay":"string","endDay":"string","workouts":"number","hardSets":"number","volumeKg":"number","prevVolumeKg":"number"}');
 trends:=project_fields(content->'trends','{"daysPerWeek":"number","records30d":"number"}');
 if trends is not null then
   weeks:=content#>'{trends,weeks}';
   if jsonb_typeof(weeks)='array' then
     n:=jsonb_array_length(weeks);
     select coalesce(jsonb_agg(project_fields(w,'{"start":"string","hardSets":"number","volumeKg":"number","workouts":"number"}') order by i),'[]'::jsonb)
     into weeks from jsonb_array_elements(weeks) with ordinality ws(w,i) where i>n-12;
   else weeks:=null; end if;
   trends:=trends || jsonb_build_object('weeks',weeks,'weeksTruncated',n>12);
 end if;
 if jsonb_typeof(content->'lifts')='array' then
   lifts:='[]'; excess:=jsonb_array_length(content->'lifts')>40;
   for lift in select value from jsonb_array_elements(content->'lifts') limit 40 loop
     if jsonb_typeof(lift->'variations')='array' then
       variants:='[]'; n:=jsonb_array_length(lift->'variations');
       for v in select value from jsonb_array_elements(lift->'variations') limit 8 loop
         projected:=project_fields(v,'{"label":"string","sessions":"number","sets":"number","lastMs":"number"}');
         variants:=variants || jsonb_build_array(projected || jsonb_build_object('peak',
           project_fields(v->'peak','{"atMs":"number","reps":"number","durationSec":"number","weightKg":"number"}')));
       end loop;
     else variants:=null; n:=null; end if;
     lifts:=lifts || jsonb_build_array(project_fields(lift,'{"name":"string","metric":"string"}') ||
       jsonb_build_object('variations',variants,'variationsTruncated',n>8));
   end loop;
 end if;
 if week is null and trends is null and lifts is null then return null; end if;
 return jsonb_build_object('week',week,'trends',trends,'lifts',lifts,'liftsTruncated',excess);
end $$;
create or replace function ironvellum_mcp.snapshot(uid uuid, tool text) returns jsonb
language plpgsql stable set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare s public.web_snapshots; content jsonb; meta jsonb; payload jsonb; built bigint; state text;
begin
 select * into s from public.web_snapshots where user_id=uid;
 if not found then return ok('{"freshness":"absent"}'); end if;
 meta:=jsonb_build_object('snapshotVersion',s.version,'syncedAtMs',ms(s.updated_at));
 content:=s.snapshot;
 if s.version<>1 or content->'v' is distinct from '1'::jsonb then
   return ok(meta || '{"freshness":"unsupported_version"}');
 end if;
 if jsonb_typeof(content->'builtAtMs') is distinct from 'number' or content->>'builtAtMs' !~ '^[0-9]{1,15}$'
    or not str(content,'zone',1,80) then return ok(meta || '{"freshness":"absent"}'); end if;
 built:=(content->>'builtAtMs')::bigint;
 state:=case when least(built,ms(s.updated_at))<ms(now()-interval '24 hours') then 'stale' else 'fresh' end;
 meta:=meta || jsonb_build_object('builtAtMs',built,'zone',content->>'zone');
 if tool='get_progress' then
   payload:=progress(content);
   if payload is null then return ok(meta || '{"freshness":"absent"}'); end if;
 else
   content:=content#>'{routines,presets}';
   if jsonb_typeof(content) is distinct from 'array' then return ok(meta || '{"freshness":"absent"}'); end if;
   select jsonb_build_object('presets',coalesce(jsonb_agg(jsonb_build_object('id',leaf(p,'id','number'),
     'name',leaf(p,'name','string'),'entries',coalesce((select jsonb_agg(jsonb_build_object(
       'name',leaf(e,'name','string'),'sets',leaf(e,'sets','number'),'reps',leaf(e,'reps','number'),
       'loadKg',leaf(e,'loadKg','number'),'scheme',leaf(e,'scheme','string'),'modifiers',leaf(e,'modifiers','string'),
       'supersetGroup',leaf(e,'supersetGroup','number'),'warmup',leaf(e,'warmup','boolean')) order by ei)
       from jsonb_array_elements(case when jsonb_typeof(p->'entries')='array' then p->'entries' else '[]' end)
       with ordinality entries(e,ei) where ei<=50),'[]'::jsonb),
     'entriesTruncated',case when jsonb_typeof(p->'entries')='array' then jsonb_array_length(p->'entries')>50 else false end
     ) order by pi),'[]'::jsonb),'truncated',jsonb_array_length(content)>50) into payload
   from jsonb_array_elements(content) with ordinality presets(p,pi) where pi<=50;
 end if;
 return ok(meta || jsonb_build_object('freshness',state) || payload);
end $$;
create or replace function ironvellum_mcp.history(uid uuid)
returns table(id text, started timestamptz, ended timestamptz, label text)
language sql stable set search_path=pg_catalog as $$
 select 'synced:'||s.id,s.started_at,s.completed_at,s.label from public.sessions s where s.user_id=uid and s.completed_at is not null
 union all select 'imported:'||s.id,s.started_at,s.completed_at,s.label from public.imported_sessions s where s.user_id=uid and s.completed_at is not null
$$;
create or replace function ironvellum_mcp.workouts(uid uuid, tool text, args jsonb) returns jsonb
language plpgsql stable set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare ref text; cutoff timestamptz; n int:=20; rows jsonb; summary jsonb; workout_id uuid;
begin
 if tool='list_workouts' then
   if not shape(args,'{}','{limit,cursor}') or (args ? 'limit' and not intval(args,'limit',1,50)) then return fail('invalid_arguments'); end if;
   n:=coalesce((args->>'limit')::numeric::int,20);
   if args ? 'cursor' then
     ref:=args->>'cursor';
     select ended into cutoff from history(uid) h where h.id=ref;
     if not found then return fail('invalid_cursor'); end if;
   end if;
   select coalesce(jsonb_agg(jsonb_build_object('id',h.id,'label',h.label,'startedAtMs',ms(h.started),
     'completedAtMs',ms(h.ended)) order by h.ended desc,h.id desc),'[]'::jsonb) into rows
   from (select * from history(uid) where cutoff is null or (ended,id)<(cutoff,ref) order by ended desc,id desc limit n+1) h;
   if jsonb_array_length(rows)>n then return ok(jsonb_build_object('workouts',rows-n,'nextCursor',rows->(n-1)->>'id')); end if;
   return ok(jsonb_build_object('workouts',rows,'nextCursor',null));
 end if;
 if not shape(args,'{id}') or not str(args,'id',1,48) then return fail('invalid_arguments'); end if;
 ref:=args->>'id';
 select jsonb_build_object('id',id,'label',label,'startedAtMs',ms(started),'completedAtMs',ms(ended)) into summary from history(uid) where id=ref;
 if not found then return fail('not_found'); end if;
 workout_id:=split_part(ref,':',2)::uuid;
 select coalesce(jsonb_agg(jsonb_build_object('exerciseName',s.exercise_name,'setIndex',s.set_index,
   'reps',s.reps,'loadKg',s.weight_kg,'durationSec',s.duration_sec,'distanceM',s.distance_m,'done',s.done)
   order by s.exercise_position nulls last,s.exercise_name,s.set_index,s.id),'[]'::jsonb) into rows
 from (select * from (
   select id,exercise_position,exercise_name,set_index,reps,weight_kg,duration_sec,distance_m,done from public.session_sets
    where ref like 'synced:%' and session_id=workout_id
   union all select id,exercise_position,exercise_name,set_index,reps,weight_kg,duration_sec,distance_m,done from public.imported_session_sets
    where ref like 'imported:%' and session_id=workout_id and user_id=uid
 ) ss order by exercise_position nulls last,exercise_name,set_index,id limit 201) s;
 return ok(jsonb_build_object('workout',summary,'sets',rows-200,'truncated',jsonb_array_length(rows)>200));
end $$;

create or replace function ironvellum_mcp.create_draft(g ironvellum_mcp.grants,args jsonb) returns jsonb
language plpgsql set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare body jsonb:=args->'draft'; entry jsonb; d drafts; digest text; ident text:=args->>'idempotencyKey'; normalized jsonb:='[]';
begin
 if not shape(args,'{idempotencyKey,draft}') or not str(args,'idempotencyKey',1,80) or ident !~ '^[A-Za-z0-9._~-]+$' then return fail('invalid_arguments'); end if;
 if not shape(body,'{schemaVersion,name,warmup,cooldown,entries}') or body->'schemaVersion'<>'1'::jsonb
    or not str(body,'name',1,80) or body->>'name'<>btrim(body->>'name') or body->>'name' ~ '[[:cntrl:]]'
    or (body->>'warmup' in ('AUTO','NONE') and body->>'cooldown' in ('AUTO','NONE')) is not true
    or jsonb_typeof(body->'entries') is distinct from 'array' then return fail('invalid_draft'); end if;
 if jsonb_array_length(body->'entries') not between 1 and 20 then return fail('invalid_draft'); end if;
 for entry in select value from jsonb_array_elements(body->'entries') loop
   if not shape(entry,'{exerciseKey,sets,reps,loadKg}') or not str(entry,'exerciseKey',1,128)
      or not intval(entry,'sets',1,50) or not intval(entry,'reps',1,1000)
      or jsonb_typeof(entry->'loadKg') not in ('number','null') then return fail('invalid_draft'); end if;
   if (entry->>'loadKg')::numeric not between 0 and 1000 then return fail('invalid_draft'); end if;
   normalized:=normalized || jsonb_build_array(jsonb_build_object('exerciseKey',entry->>'exerciseKey','sets',(entry->>'sets')::numeric::int,
     'reps',(entry->>'reps')::numeric::int,'loadKg',trim_scale((entry->>'loadKg')::numeric)));
 end loop;
 body:=jsonb_set(jsonb_set(body,'{entries}',normalized),'{schemaVersion}','1');
 digest:=encode(sha256(convert_to(body::text,'UTF8')),'hex');
 select * into d from drafts where owner_id=g.owner_id and client_id=g.client_id and idempotency_key=ident;
 if found then
   if d.content_hash<>digest then return fail('conflict'); end if;
   return ok(jsonb_build_object('draft',envelope(d)));
 end if;
 if exists(select from jsonb_array_elements(normalized) e where not exists(select from movements m where m.key=e->>'exerciseKey' and m.metric='REPS')) then
   return fail('unsupported_movement');
 end if;
 if (select count(*) from drafts where owner_id=g.owner_id and client_id=g.client_id and created_at>clock_timestamp()-interval '24 hours')>=20 then return fail('rate_limited'); end if;
 insert into drafts(owner_id,client_id,grant_id,idempotency_key,content,content_hash) values(g.owner_id,g.client_id,g.id,ident,body,digest) returning * into d;
 return ok(jsonb_build_object('draft',envelope(d)));
end $$;

create or replace function ironvellum_mcp.run_tool(g ironvellum_mcp.grants, scopes text[], tool text, args jsonb) returns jsonb
language plpgsql set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare required text[]; result jsonb; d drafts; n int; needle text;
begin
 required:=case tool when 'list_workouts' then array['training:read'] when 'get_workout' then array['training:read']
   when 'get_progress' then array['progress:read'] when 'list_saved_workouts' then array['savedworkouts:read']
   when 'search_movements' then array['savedworkouts:read','workoutdrafts:create']
   when 'create_workout_draft' then array['workoutdrafts:create'] when 'get_workout_draft' then array['workoutdrafts:create'] end;
 -- Serialize across separate grants issued to the same owner/client.
 perform pg_advisory_xact_lock(hashtextextended(g.owner_id::text || ':' || g.client_id, 36));
 if (select count(*) from audit where owner_id=g.owner_id and client_id=g.client_id and at_time>clock_timestamp()-interval '1 minute')>=120 then return fail('rate_limited'); end if;
 if required is null then result:=fail('unknown_tool');
 elsif not (required && scopes and required && g.scopes) then result:=fail('insufficient_scope');
 elsif tool in ('get_workout','list_workouts') then result:=workouts(g.owner_id,tool,args);
 elsif tool in ('get_progress','list_saved_workouts') then
   result:=case when shape(args,'{}') then snapshot(g.owner_id,tool) else fail('invalid_arguments') end;
 elsif tool='create_workout_draft' then result:=create_draft(g,args);
 elsif tool='get_workout_draft' then
   if not shape(args,'{id}') or not str(args,'id',36,36) then result:=fail('invalid_arguments');
   else
     select * into d from drafts where id::text=args->>'id' and owner_id=g.owner_id and client_id=g.client_id;
     result:=case when found then ok(jsonb_build_object('draft',envelope(d))) else fail('not_found') end;
   end if;
 else
   if not shape(args,'{query}','{limit}') or not str(args,'query',1,80) or (args ? 'limit' and not intval(args,'limit',1,20)) then result:=fail('invalid_arguments');
   else
     n:=coalesce((args->>'limit')::numeric::int,20); needle:=lower(btrim(args->>'query'));
     select ok(jsonb_build_object('movements',coalesce(jsonb_agg(to_jsonb(m) order by m.key),'[]'::jsonb))) into result
     from (select m.key,m.name,m.metric from movements m where strpos(m.key,needle)>0 and needle<>'' order by m.key limit n) m;
   end if;
 end if;
 if octet_length(result::text)>1048576 then result:=fail('result_too_large'); end if;
 insert into audit(owner_id,client_id,tool,result,draft_id) values(g.owner_id,g.client_id,case when required is null then 'unknown' else tool end,
   coalesce(result->>'error','ok'),(result#>>'{draft,id}')::uuid);
 return result;
end $$;

create or replace function ironvellum_mcp.oauth(op text,a jsonb) returns jsonb
language plpgsql set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare t tokens; g grants; expected text; digest text; bound_client text:=a->>'clientId'; selected text[];
        access_end timestamptz; refresh_end timestamptz; result jsonb;
begin
 case op
 when 'code.load','code.exchange' then expected:='code'; digest:=a->>'codeHash';
 when 'refresh.load','refresh.exchange' then expected:='refresh'; digest:=a->>'refreshHash';
 when 'access.load','tool.call' then expected:='access'; digest:=a->>'accessHash';
 when 'token.revoke' then digest:=a->>'tokenHash';
 else return fail('invalid_operation');
 end case;
 select * into t from tokens where hash=digest and (kind=expected or op='token.revoke' and kind in ('access','refresh'));
 if found then
   -- Lock the family first for every operation, then re-read the token after
   -- waiting so a racing exchange observes consumption in READ COMMITTED.
   select * into g from grants where id=t.grant_id for update;
   select * into t from tokens where hash=digest;
 end if;
 if op='token.revoke' then
   if g.id is not null and g.client_id=bound_client then update grants set revoked_at=coalesce(revoked_at,clock_timestamp()) where id=g.id; end if;
   return ok();
 end if;
 if t.hash is null or g.id is null or g.revoked_at is not null or g.expires_at<=clock_timestamp()
    or (expected in ('code','refresh') and g.client_id is distinct from bound_client)
    or g.resource_url is distinct from (select resource_url from config)
    or not t.scopes <@ g.scopes then return fail(case when expected='access' then 'invalid_token' else 'invalid_grant' end); end if;
 if t.used and expected in ('refresh','code') then
   update grants set revoked_at=clock_timestamp() where id=g.id;
   return fail('invalid_grant');
 end if;
 if t.expires_at<=clock_timestamp() then return fail(case when expected='access' then 'invalid_token' else 'invalid_grant' end); end if;
 result:=jsonb_build_object('grantId',g.id,'clientId',g.client_id,'resourceUrl',g.resource_url,'scopes',t.scopes,'expiresAtMs',ms(t.expires_at));
 if op='tool.call' then return run_tool(g,t.scopes,a->>'tool',a->'args'); end if;
 if op='code.load' then return ok(result || jsonb_build_object('codeChallenge',t.challenge,'redirectUri',t.redirect_uri,'redirectUriExplicit',t.redirect_explicit)); end if;
 if op in ('access.load','refresh.load') then return ok(result); end if;
 if a->>'resourceUrl' is distinct from g.resource_url then return fail('invalid_grant'); end if;
 if op='code.exchange' and (a->>'redirectUri' is distinct from t.redirect_uri or a->>'codeChallenge' is distinct from t.challenge) then return fail('invalid_grant'); end if;
 selected:=t.scopes;
 if op='refresh.exchange' then
   if jsonb_typeof(a->'scopes') is distinct from 'array' then return fail('invalid_scope'); end if;
   selected:=array(select jsonb_array_elements_text(a->'scopes'));
   if not valid_scopes(selected) or not selected <@ t.scopes then return fail('invalid_scope'); end if;
 end if;
 if not str(a,'accessHash',64,64) or a->>'accessHash' !~ '^[a-f0-9]{64}$'
    or coalesce(a->>case when expected='code' then 'refreshHash' else 'newRefreshHash' end,'') !~ '^[a-f0-9]{64}$' then return fail('invalid_arguments'); end if;
 access_end:=least(g.expires_at,clock_timestamp()+interval '10 minutes');
 refresh_end:=least(g.expires_at,clock_timestamp()+interval '30 days');
 update tokens set used=true where hash=t.hash;
 insert into tokens(hash,kind,grant_id,scopes,expires_at) values
   (a->>'accessHash','access',g.id,selected,access_end),
   (a->>case when expected='code' then 'refreshHash' else 'newRefreshHash' end,'refresh',g.id,selected,refresh_end);
 return ok(jsonb_build_object('grantId',g.id,'clientId',g.client_id,'scopes',selected,'resourceUrl',g.resource_url,
   'accessExpiresAtMs',ms(access_end),'refreshExpiresAtMs',ms(refresh_end)));
exception when unique_violation then return fail('invalid_arguments');
end $$;

create or replace function ironvellum_mcp.redirect_ok(uri text) returns boolean
language sql immutable set search_path=pg_catalog as $$
 select coalesce(length(uri) between 1 and 2048 and uri !~ '[[:space:][:cntrl:]#\\]' and
 (uri ~ '^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?([/?]|$)' or
  uri ~ '^http://(127[.]0[.]0[.]1|localhost|\[::1\])(:[0-9]{1,5})?([/?]|$)'),false)
$$;
create or replace function public.ironvellum_mcp_gateway(p_operation text,p_args jsonb) returns jsonb
language plpgsql security definer set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare a alias for p_args; k text[]; c clients; r requests; urls text[]; selected text[]; ru text;
        current_url text; item jsonb; cleaned int:=0; affected int;
begin
 if jsonb_typeof(a) is distinct from 'object' or octet_length(a::text)>(case when p_operation='catalogue.put' then 1048576 else 65536 end) then return fail('invalid_arguments'); end if;
 k:=case p_operation
 when 'configuration.get' then '{}'::text[] when 'configuration.configure' then '{resourceUrl}'::text[]
 when 'client.get' then '{clientId}'::text[] when 'client.register' then '{clientId,clientName,metadataCiphertext,redirectUris}'::text[]
 when 'authorization.create' then '{clientId,redirectUri,redirectUriExplicit,resourceUrl,scopes,codeChallenge,state}'::text[]
 when 'authorization.get' then '{requestId}'::text[] when 'code.load' then '{clientId,codeHash}'::text[]
 when 'code.exchange' then '{clientId,resourceUrl,redirectUri,codeChallenge,codeHash,accessHash,refreshHash}'::text[]
 when 'access.load' then '{accessHash}'::text[] when 'refresh.load' then '{clientId,refreshHash}'::text[]
 when 'refresh.exchange' then '{clientId,resourceUrl,refreshHash,newRefreshHash,accessHash,scopes}'::text[]
 when 'token.revoke' then '{clientId,tokenHash}'::text[] when 'tool.call' then '{accessHash,tool,args}'::text[]
 when 'catalogue.put' then '{schemaVersion,movements}'::text[] when 'prune' then '{}'::text[] end;
 if k is null then return fail('invalid_operation'); end if;
 if not shape(a,k) then return fail('invalid_arguments'); end if;
 select resource_url into current_url from config;
 ru:=a->>'resourceUrl';
 case p_operation
 when 'configuration.get' then return ok(jsonb_build_object('resourceUrl',current_url));
 when 'configuration.configure' then
   if not redirect_ok(ru) or ru !~ '^https?://[^/?#]+/mcp$' then return fail('invalid_arguments'); end if;
   insert into config(resource_url) values(ru) on conflict(singleton) do nothing;
   if (select resource_url from config)<>ru then return fail('conflict'); end if;
   return ok(jsonb_build_object('resourceUrl',ru));
 when 'client.register' then
   if not str(a,'clientId',1,128) or a->>'clientId' !~ '^[A-Za-z0-9._~-]+$' or not str(a,'clientName',1,100)
      or a->>'clientName' ~ '[[:cntrl:]]' or not str(a,'metadataCiphertext',1,32768)
      or jsonb_typeof(a->'redirectUris') is distinct from 'array' then return fail('invalid_arguments'); end if;
   urls:=array(select jsonb_array_elements_text(a->'redirectUris'));
   if cardinality(urls) not between 1 and 16 or exists(select from unnest(urls) u where not redirect_ok(u)) then return fail('invalid_arguments'); end if;
   perform pg_advisory_xact_lock(36,1);
   if (select count(*) from clients where created_at>clock_timestamp()-interval '1 minute')>=20
      or (select count(*) from clients where created_at>clock_timestamp()-interval '24 hours')>=1000 then return fail('rate_limited'); end if;
   insert into clients(id,name,ciphertext,redirects) values(a->>'clientId',a->>'clientName',a->>'metadataCiphertext',urls);
   return ok(jsonb_build_object('clientId',a->>'clientId'));
 when 'client.get' then
   select * into c from clients where id=a->>'clientId';
   if not found then return fail('not_found'); end if;
   return ok(jsonb_build_object('clientId',c.id,'clientName',c.name,'metadataCiphertext',c.ciphertext,'redirectUris',c.redirects));
 when 'authorization.create' then
   select * into c from clients where id=a->>'clientId';
   if not found or current_url is null or ru is distinct from current_url or not str(a,'redirectUri',1,2048)
      or not (a->>'redirectUri'=any(c.redirects)) or not str(a,'codeChallenge',43,43) or a->>'codeChallenge' !~ '^[A-Za-z0-9_-]{43}$'
      or jsonb_typeof(a->'redirectUriExplicit') is distinct from 'boolean' or not (a->'state'='null' or str(a,'state',0,2048)) then return fail('invalid_request'); end if;
   if jsonb_typeof(a->'scopes') is distinct from 'array' then return fail('invalid_scope'); end if;
   selected:=array(select jsonb_array_elements_text(a->'scopes'));
   if not valid_scopes(selected) then return fail('invalid_scope'); end if;
   insert into requests(client_id,resource_url,redirect_uri,redirect_explicit,scopes,challenge,state)
     values(c.id,ru,a->>'redirectUri',(a->>'redirectUriExplicit')::boolean,selected,a->>'codeChallenge',a->>'state') returning * into r;
   return ok(jsonb_build_object('requestId',r.id,'expiresAtMs',ms(r.expires_at)));
 when 'authorization.get' then
   select * into r from requests where id::text=a->>'requestId' and expires_at>clock_timestamp() and not decided;
   if not found then return fail('invalid_request'); end if;
   return ok(jsonb_build_object('requestId',r.id,'clientId',r.client_id,'clientName',(select name from clients where id=r.client_id),
     'resourceUrl',r.resource_url,'redirectUri',r.redirect_uri,'redirectUriExplicit',r.redirect_explicit,'scopes',r.scopes,
     'state',r.state,'codeChallenge',r.challenge,'expiresAtMs',ms(r.expires_at)));
 when 'catalogue.put' then
   if a->'schemaVersion' is distinct from '1'::jsonb or jsonb_typeof(a->'movements') is distinct from 'array' then return fail('invalid_arguments'); end if;
   if jsonb_array_length(a->'movements') not between 1 and 5000 then return fail('invalid_arguments'); end if;
   for item in select value from jsonb_array_elements(a->'movements') loop
     if not shape(item,'{key,name,metric}') or not str(item,'key',1,128) or not str(item,'name',1,128)
        or item->>'key'<>lower(btrim(item->>'name')) or not str(item,'metric',1,32)
        or item->>'metric' not in ('REPS','HOLD','DURATION','DISTANCE_TIME','ATTEMPTS_GRADE') then return fail('invalid_arguments'); end if;
   end loop;
   lock table movements in exclusive mode;
   delete from movements;
   insert into movements select x->>'key',x->>'name',x->>'metric' from jsonb_array_elements(a->'movements') x;
   return ok(jsonb_build_object('count',jsonb_array_length(a->'movements')));
 when 'prune' then
   update drafts set status='expired',changed_at=clock_timestamp() where id in
    (select id from drafts where status='pending' and expires_at<=now() limit 200 for update skip locked);
   get diagnostics affected=row_count; cleaned:=cleaned+affected;
   delete from drafts where id in (select id from drafts where status in ('accepted','expired','rejected')
    and changed_at<now()-interval '90 days' limit 200 for update skip locked);
   get diagnostics affected=row_count; cleaned:=cleaned+affected;
   delete from audit where id in (select id from audit where at_time<now()-interval '30 days' limit 200 for update skip locked);
   get diagnostics affected=row_count; cleaned:=cleaned+affected;
   delete from requests where id in (select id from requests where expires_at<now() limit 200 for update skip locked);
   get diagnostics affected=row_count; cleaned:=cleaned+affected;
   delete from tokens where hash in (select hash from tokens where expires_at<=now() and (not used or kind='access') limit 200 for update skip locked);
   get diagnostics affected=row_count; cleaned:=cleaned+affected;
   -- Used refresh/code digests remain detectable until the family can no longer authorize any call.
   delete from tokens where hash in (select t.hash from tokens t join grants g on g.id=t.grant_id where
     (g.expires_at<now() or g.revoked_at is not null) limit 200 for update of t skip locked);
   get diagnostics affected=row_count; cleaned:=cleaned+affected;
   delete from grants where id in (select g.id from grants g where least(g.expires_at,g.revoked_at)<now()-interval '90 days'
     and not exists(select from tokens t where t.grant_id=g.id) limit 200 for update skip locked);
   get diagnostics affected=row_count; cleaned:=cleaned+affected;
   return ok(jsonb_build_object('affected',cleaned));
 else return oauth(p_operation,a);
 end case;
exception when unique_violation then return fail('conflict');
end $$;

revoke all on all functions in schema ironvellum_mcp from public, anon, authenticated, service_role;
-- Hosted Supabase provisions direct EXECUTE grants in addition to PUBLIC.
revoke all on function public.ironvellum_mcp_gateway(text,jsonb) from public,anon,authenticated;
grant execute on function public.ironvellum_mcp_gateway(text,jsonb) to service_role;
do $$
declare f regprocedure;
begin
 foreach f in array array[
   'public.ironvellum_mcp_connections()'::regprocedure,'public.ironvellum_mcp_revoke_connection(uuid)'::regprocedure,
   'public.ironvellum_mcp_drafts(integer)'::regprocedure,'public.ironvellum_mcp_draft(uuid)'::regprocedure,
   'public.ironvellum_mcp_review_draft(uuid,text,text)'::regprocedure,'public.ironvellum_mcp_consent(uuid,boolean,text[],text)'::regprocedure
 ] loop
   execute format('revoke all on function %s from public,anon,service_role',f);
   execute format('grant execute on function %s to authenticated',f);
 end loop;
end $$;
create or replace function public.schema_version() returns int language sql stable as $$ select 36 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon,authenticated;
commit;
