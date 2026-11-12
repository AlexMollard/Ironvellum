-- Hosted patch, 2026-11-12: rolling seven-day workout XP rankings.
-- Additive and idempotent: no training rows, accounts, policies or old views change.
-- Lifetime rankings and sync still work on the app's minimum schema 31.
-- The new board requires this patch (schema 33). Apply after the feed-card patch.
-- Fresh/self-hosted projects get the same view from 0001_baseline.sql.

begin;

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
        sum(s.xp_awarded)::bigint as workout_xp_last_7d
    from sessions s
    where s.completed_at > now() - interval '168 hours'
      and s.completed_at <= now()
    group by s.user_id
    having sum(s.xp_awarded) > 0
) recent on recent.user_id = p.id;

revoke all on public.workout_xp_leaderboard from public, anon, authenticated;
grant select on public.workout_xp_leaderboard to authenticated;

create or replace function public.schema_version() returns int
language sql stable as $$ select 33 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon, authenticated;

commit;
