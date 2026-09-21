-- 0012_feed_held_seconds.sql
-- A hold (L-sit, plank, hollow hold) stores its figure in `session_sets.duration_sec`
-- with `reps = 0` — schema 24 on the device moved it out of the reps column,
-- because 60 seconds of hollow hold was being counted as sixty repetitions.
--
-- `public_feed` still aggregates `sum(reps)` only, so a session of nothing but
-- holds publishes as `reps_done = 0` and the card claims the hunter did
-- nothing. The share card on the device gets this right ("75s held"); the feed
-- had no field to get it right with.
--
-- There is no `exercises` table in the cloud schema (the metric dimension
-- lives only in the device's Room database), so a hold is identified the same
-- way 0007 identifies a cardio set — from the set row itself: no reps, a
-- positive duration, no distance and no climbing grade. A timed run carries a
-- distance; a timed hold does not.
--
-- Idempotent: the view is dropped and recreated.

drop view if exists public_feed;

create view public_feed with (security_invoker = true) as
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

    -- Seconds held across the session's static holds. 0, never null, so the
    -- client can treat "no holds" and "an older row" identically.
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

    (select count(*) from session_likes sl where sl.session_id = s.id) as like_count,
    exists (select 1 from session_likes sl where sl.session_id = s.id and sl.user_id = auth.uid()) as liked_by_me,

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
    -- outright; an unloaded set counts only when it is a real rep-scored set
    -- (more than one rep, no distance, no climbing grade), which keeps
    -- calisthenics ("8 x BW") and rejects a run's synthetic single rep. A hold
    -- has reps = 0 and so is excluded by both branches, as it should be: a
    -- hold's headline is its time, not a set.
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

    -- Hardest climbing grade attempted, free text by design (V-scale, Font and
    -- YDS all disagree), so it is ranked lexically only as a tie-break of last
    -- resort and simply shown as recorded.
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

    case
      when s.completed_at is not null and s.started_at is not null
        then extract(epoch from (s.completed_at - s.started_at))::int
      else null
    end as duration_sec
from sessions s
join profiles p on p.id = s.user_id
where s.completed_at is not null
order by s.completed_at desc;
