-- 0006_feed_depth.sql
-- The feed reads as "shallow" because public_feed (0004:62-83) exposes only
-- COUNTERS: sets_done, reps_done, xp, strength_score, like_count. A post can
-- say how many sets happened but never WHAT was trained, so every card looks
-- the same regardless of whether it was a 5x5 pull day or a 10 km run.
--
-- This adds the movement content, aggregated server-side so a feed page still
-- costs ONE request regardless of length (same discipline as the like counts).
-- No new table, no new column on sessions: everything is derived from
-- session_sets, which is already RLS-protected via can_view(), and the views
-- stay security_invoker so the caller's RLS decides what they may read.
--
-- Idempotent: the views are dropped and recreated.

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

    -- WHAT was trained: the three movements carrying the most work in this
    -- session, heaviest first. Ordered by loaded volume so the headline lift
    -- leads rather than whichever row happened to be inserted first.
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

    -- The single most impressive set, for a headline chip: heaviest loaded set,
    -- falling back to the highest-rep bodyweight set when nothing was loaded
    -- (a calisthenics session must not render a blank headline).
    (
        select case
                 when ss.weight_kg is not null and ss.weight_kg > 0
                   then ss.reps || ' x ' || trim(to_char(ss.weight_kg, 'FM999990.0')) || ' kg'
                 else ss.reps || ' x BW'
               end
        from session_sets ss
        where ss.session_id = s.id and ss.done
        order by coalesce(ss.weight_kg, 0) desc, ss.reps desc
        limit 1
    ) as best_set,

    -- How many distinct movements: lets a card say "4 movements" instead of
    -- repeating a set count that means nothing on its own.
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
    end as duration_sec
from sessions s
join profiles p on p.id = s.user_id
where s.completed_at is not null
order by s.completed_at desc;
