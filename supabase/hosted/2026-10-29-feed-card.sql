-- Hosted patch, 2026-10-29: the friend-safe workout card.
--
-- For the already-deployed project only; a fresh project gets all of this from
-- supabase/migrations/0001_baseline.sql, which stays the source of truth (every
-- definition below is cut verbatim from it, and HostedPatchTest fails the unit
-- gate if one drifts). Paste this file into the SQL editor once. It is
-- idempotent: a second run, or a run on a project that already has it, changes
-- nothing and touches no rows. One transaction: it all lands or none of it does.
--
-- Apply it AFTER supabase/hosted/2026-10-28-web-snapshot-hardening.sql. Builds
-- that expect schema 29, 30 or 31 keep working before and after it: the web
-- viewer's Friends area and the phone's workout card need this patch (schema 32).
--
-- What it changes:
--   1. sessions.feed_card, a jsonb object of at most 2 KiB, readable exactly as
--      the workout is (sessions_read, can_view_session) and writable by its owner
--      through sessions_write. No new policy and no new grant.
--   2. public_feed gains feed_card as its LAST column; every earlier column keeps
--      its name and place.
--   3. schema_version() reports 32.

begin;

-- ---------------------------------------------------------------- 1. the column
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

-- ---------------------------------------------------------------- 2. the feed
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

-- ---------------------------------------------------------------- 3. the beacon
create or replace function public.schema_version() returns int
language sql stable as $$ select 32 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon, authenticated;

commit;
