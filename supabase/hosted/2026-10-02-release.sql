-- Hosted patch, 2026-10-02: the release fixes.
--
-- For the already-deployed project only; a fresh project gets all of this from
-- supabase/migrations/0001_baseline.sql, which stays the source of truth (every
-- definition below is cut verbatim from it, and HostedPatchTest fails the unit
-- gate if one drifts). Paste this file into the SQL editor once. It is
-- idempotent: a second run, or a run on a project that already has it, changes
-- nothing and touches no rows. One transaction: it all lands or none of it does.
--
-- Apply it AFTER supabase/hosted/2026-10-01-circles.sql and BEFORE shipping the
-- app build that expects schema 28.
--
-- What it changes:
--   1. circle_draw_code() drew its bytes with pgcrypto's gen_random_bytes(),
--      which Supabase keeps in the `extensions` schema, outside the function's
--      pinned search_path. create_circle and rotate_circle_code failed with
--      42883, so nobody could form a circle or change a code. It now draws from
--      gen_random_uuid(), which lives in pg_catalog.
--   2. sessions.edited_at and public_feed.edited_at. The circles patch left them
--      out, so the sessions upsert of an amended trial was rejected (PGRST204)
--      and every later push failed with it.
--   3. New accounts are seeded 'Ironbound' + four digits instead of 'Lifter'
--      (handle_new_user), and a blocked ally request says "this Ironbound"
--      (friendships_before_insert). docs/GLOSSARY.md records both as needing the
--      hosted project updated. Accounts already named 'Lifter####' keep the
--      name; the app still treats it as unclaimed.
--   4. The remark rate-limit messages (session_comments_before_insert) say
--      "remark" and "trial", the words the app uses, because the app shows them
--      word for word.
--   5. my_inbox() keeps the circle's goal-met missive for a member who muted or
--      blocked whoever trained the day it fell (the actor is only left unnamed).
--   6. The future-timestamp guard (sessions_no_future_timestamps) says "trial"
--      where it said "session", because the app shows the message word for word.
--   7. schema_version() reports 28, so an app build that needs this patch
--      refuses a project that has not had it.
--
-- What an older installed build does against the patched project: nothing
-- changes for it; every function here keeps its signature and columns, and
-- public_feed only gains a column at the end.

begin;

-- ---------------------------------------------------------------- 1. circle codes
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

-- ---------------------------------------------------------------- 2. amended trials
-- When the owner amended the sealed workout; null when never amended. The feed
-- marks an amended workout so an ally is not surprised by changed figures.
alter table sessions add column if not exists edited_at timestamptz;

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
    s.edited_at
from sessions s
join profiles p on p.id = s.user_id
where s.completed_at is not null
  -- A mute hides the lifter's workouts from the muter's feed, silently.
  and not exists (select 1 from mutes m where m.muter_id = auth.uid() and m.muted_id = s.user_id)
order by s.completed_at desc;

-- ---------------------------------------------------------------- 3. Ironbound handles and ally requests
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

-- ---------------------------------------------------------------- 4. remark limit messages
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

-- ---------------------------------------------------------------- 5. the circle's goal-met missive
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

-- ---------------------------------------------------------------- 6. future-timestamp message
-- A phone clock more than a day ahead trips this guard, and the app shows its
-- text as is. Only the message changes; the trigger already points at it.
create or replace function sessions_no_future_timestamps()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
    if new.started_at > now() + interval '1 day'
       or (new.completed_at is not null and new.completed_at > now() + interval '1 day') then
        raise exception 'trial timestamps cannot be in the future';
    end if;
    return new;
end;
$$;

-- ---------------------------------------------------------------- 7. the version beacon
-- The version beacon. The app probes schema_version() as anon before pointing a
-- lifter's training at a custom backend (Settings -> CLOUD, TEST), so a
-- half-set-up project is reported before any data is sent to it. The anon
-- grant is load-bearing. EVERY SCHEMA CHANGE BUMPS THIS LITERAL and
-- Cloud.kt's NEEDED_SCHEMA_VERSION with it.
create or replace function public.schema_version() returns int
language sql stable as $$ select 28 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon, authenticated;

commit;
