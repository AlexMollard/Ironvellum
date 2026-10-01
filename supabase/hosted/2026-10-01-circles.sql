-- Hosted patch, 2026-10-01: warbands become circles.
--
-- For the already-deployed project only (schema 26, after
-- supabase/hosted/2026-10-01-inbox.sql); a fresh project gets all of this from
-- supabase/migrations/0001_baseline.sql, which stays the source of truth (every
-- definition below is cut verbatim from it). Paste this file into the SQL
-- editor once. It is idempotent: a second run, or a run on a project that
-- already has circles, changes nothing. One transaction: it all lands or none of it does.
--
-- Apply it BEFORE shipping the app build that expects schema 27, and do not
-- re-run 2026-10-01-inbox.sql afterwards (it alters the old warbands table).
-- It can also be run before re-applying the baseline on any project that still
-- has warbands: re-applying the baseline first would create empty circles
-- beside the old tables and strand their data.
--
-- What it changes:
--   1. The tables, columns, constraints, indexes, policies and functions are
--      RENAMED in place (warbands -> circles, warband_members -> circle_members,
--      warband_id -> circle_id, warband_join_log -> circle_join_log, every
--      *_warband RPC -> *_circle), so no data is copied or lost.
--   2. weekly_goal (a total of 5-50 workouts) becomes per_member (days each
--      member aims to train, 1-7). The default 12 maps to 3; any other goal
--      becomes round(goal / members) clamped to 1..7. A project that never had
--      weekly_goal keeps the default 3. Nothing is parked for next week.
--   3. owner_id may be null while a handover is in flight, and deleting the
--      owner's account hands the circle over instead of deleting it. Circle
--      names are trimmed (a blank one becomes "Circle").
--   4. New: circle_weeks and circle_week_members (the frozen goal and roster of
--      each week, settled once on the server), circle_events (roster missives),
--      the counting and settlement functions, circle_bonuses(), and the
--      Keeper-only set_circle_name, rotate_circle_code and kick_circle_member.
--      join_circle now answers with a status instead of raising; the client
--      can no longer update or delete the circles row directly.
--   5. my_inbox() keeps its columns and its 'band_join' / 'band_goal' kinds
--      (builds already installed parse those) and gains 'circle_left',
--      'circle_keeper' and 'circle_removed', which older builds skip.
--   6. schema_version() reports 27.
--
-- What an older installed build does against the patched project: sessions,
-- allies, the feed and the inbox keep working. Its circle panel and Veil
-- banner call functions that no longer exist (my_warband and friends) and show
-- a failed read; it still connects, because the app only refuses a backend
-- whose schema_version is LOWER than the one it needs.

begin;

-- ---------------------------------------------------------------- 1. rename in place
do $rename$
declare
    c record;
begin
    if to_regclass('public.warbands') is not null and to_regclass('public.circles') is null then
        -- The policies and functions that name the old tables go first; the
        -- new ones are created below, from the baseline.
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
        -- carry the old name, so the result matches a fresh baseline.
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

-- ---------------------------------------------------------------- 2. definitions, verbatim from the baseline
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
    circle_id  uuid not null references circles (id) on delete cascade,
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
    v        bigint := ('x' || encode(gen_random_bytes(5), 'hex'))::bit(40)::bigint;
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

alter table circle_join_log enable row level security;
alter table circles           enable row level security;
alter table circle_members    enable row level security;
alter table circle_weeks      enable row level security;
alter table circle_week_members enable row level security;
alter table circle_events      enable row level security;

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
-- client keeps the same anchor (CirclePayout.weekKey).
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
    if not found then
        return;
    end if;
    select max(w.week) into latest from circle_weeks w where w.circle_id = p_circle;
    wk := greatest(
        coalesce(latest + 7, (circle_week_start(coalesce(c.created_at, now())) at time zone 'utc')::date),
        cur - 56
    );
    while wk <= cur loop
        perform public.circle_open_week(p_circle, wk);
        wk := wk + 7;
    end loop;
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
        select 'band_goal', g.at, g.user_id,
               case when g.user_id = me.id or can_view(g.user_id)
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
      and not blocked_between(me.id, i.actor_id)
      and not exists (select 1 from mutes m where m.muter_id = me.id and m.muted_id = i.actor_id)
    order by i.occurred_at desc
    limit 100;
$$;
revoke execute on function public.my_inbox() from public, anon;
grant execute on function public.my_inbox() to authenticated;

-- ---------------------------------------------------------------- circles
-- The invite-code alphabet create_circle() draws from: digits 2-9 and letters
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

create or replace function public.schema_version() returns int
language sql stable as $$ select 27 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon, authenticated;

commit;
