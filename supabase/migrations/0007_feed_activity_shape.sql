-- 0007_feed_activity_shape.sql
-- 0006 gave every hunt a `best_set`, falling back to "<reps> x BW" when nothing
-- was loaded. That fallback was written for calisthenics (a real "6 x BW" set)
-- but it also fires for cardio, where a run became the meaningless "1 x BW".
--
-- Fix it at the source instead of hiding it in the client: a hunt's shape is
-- decidable from its own set rows. There is no `exercises` table in the cloud
-- schema (the metric dimension lives only in the device's Room database), but
-- `session_sets` carries `distance_m`, `duration_sec` and `grade`, which is
-- enough: a set with a distance or a climbing grade is not a loaded set.
--
-- `best_set` is therefore NULL for a hunt with no load-bearing set, and the
-- distance/grade a cardio or climbing hunt DOES have is exposed instead, so the
-- card has something true to show rather than nothing.
--
-- `top_movements` is deliberately KEPT for every shape: "Running" or
-- "Bouldering" still answers "what did they train", which is the whole point.
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
    -- calisthenics ("8 x BW") and rejects a run's synthetic single rep.
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
