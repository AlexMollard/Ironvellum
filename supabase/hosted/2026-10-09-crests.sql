-- Hosted patch, 2026-10-09: the worn crest travels with a lifter.
--
-- For the already-deployed project only; a fresh project gets all of this from
-- supabase/migrations/0001_baseline.sql, which stays the source of truth (every
-- definition below is cut verbatim from it, and HostedPatchTest fails the unit
-- gate if one drifts). Paste this file into the SQL editor once. It is
-- idempotent: a second run, or a run on a project that already has it, changes
-- nothing and touches no rows. One transaction: it all lands or none of it does.
--
-- Apply it AFTER supabase/hosted/2026-10-02-release.sql and BEFORE shipping the
-- app build that expects schema 29.
--
-- What it changes:
--   1. profiles.current_crest_id, the crest a lifter wears, and the right to
--      update it (beside display_name, visibility and current_title_id).
--   2. leaderboard, shadow_board, lift_board and public_feed each gain
--      current_crest_id as their LAST column, and my_circle() lists it per
--      member, only for a member the caller may view, as it does the title.
--   3. schema_version() reports 29, so an app build that needs this patch
--      refuses a project that has not had it.
--
-- What an older installed build does against the patched project: nothing
-- changes for it; every view only gains a column at the end and my_circle()
-- only gains a key.

begin;

-- ---------------------------------------------------------------- 1. the column
alter table profiles add column if not exists current_crest_id text
    check (char_length(current_crest_id) <= 64);

grant update (display_name, visibility, current_title_id, current_crest_id) on profiles to authenticated;

-- ---------------------------------------------------------------- 2. the boards, the feed and the circle
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

create or replace view lift_board with (security_invoker = true) as
select
    m.user_id,
    p.display_name,
    p.current_title_id,
    p.level,
    m.lift,
    m.step,
    case when m.recent_at > now() - interval '7 days' then m.recent_step end as recent_step,
    p.current_crest_id
from lift_marks m
join profiles p on p.id = m.user_id;

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
    p.current_crest_id
from sessions s
join profiles p on p.id = s.user_id
where s.completed_at is not null
  -- A mute hides the lifter's workouts from the muter's feed, silently.
  and not exists (select 1 from mutes m where m.muter_id = auth.uid() and m.muted_id = s.user_id)
order by s.completed_at desc;

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

-- ---------------------------------------------------------------- 3. the version beacon
create or replace function public.schema_version() returns int
language sql stable as $$ select 29 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon, authenticated;

commit;
