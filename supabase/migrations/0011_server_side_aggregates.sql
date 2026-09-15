-- 0011_server_side_aggregates.sql
-- Forward fix for review finding F1. Idempotent: may be re-applied.
--
-- Every ranked number was a client-supplied value. `profiles_update` constrains
-- WHOSE row is written, never WHAT, so one PATCH with a normal account topped
-- both boards permanently — and the client's own max() merge made it sticky, so
-- no honest push could ever lower it again.
--
-- Two halves, and they only work together:
--
--   1. The client loses the privilege to state the numbers at all. Supabase
--      grants table-level UPDATE/INSERT to `authenticated`, which covers every
--      column, so a column-level REVOKE alone does not bite: the table-level
--      privilege has to go first, then only the genuinely user-chosen columns
--      are granted back. RLS and column privileges are ANDed, so
--      `profiles_update` keeps doing its job for what remains.
--
--   2. A definer RPC recomputes the aggregates from rows the server can see.
--
-- What is derived, and from what:
--   total_xp          from the SET ROWS, not sessions.xp_awarded - that column
--                     is client-supplied too, so trusting it would move the
--                     forgery one table across. Mirrors Xp.award():
--                     15/set + 1/rep + 25 per completed session.
--   level             from total_xp by the same curve as Xp.progress(): level n
--                     needs a cumulative 50*n*(n-1).
--   titles_count      count of earned_titles rows.
--   lifetime_strength sum of sessions.strength_score.
--
-- What stays client-asserted, and why: the shadow figures. Idle state never
-- leaves the device by design, so the server has nothing to derive them from.
-- They are clamped to sane ceilings instead, which bounds the damage to a
-- plausible-looking number rather than bigint max.
--
-- Residual, stated honestly: a determined cheat can still INSERT fabricated
-- sessions and set rows and be scored on them. That is a far higher bar than
-- one PATCH, and it is bounded by the same constraints real training is.

-- ------------------------------------------------- the level curve, in SQL
-- Closed form with an exact integer correction: floating sqrt gets the
-- candidate, integer arithmetic settles the boundary, so this cannot disagree
-- with the Kotlin curve at a level threshold.
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

revoke execute on function public.monarch_level(bigint) from public;

-- ------------------------------------------------- half 1: take the privilege
revoke update on profiles from authenticated;
grant update (display_name, visibility, current_title_id) on profiles to authenticated;

-- Creation is the same hole: a profile could be INSERTed with the aggregates
-- already inflated. Only the identity columns may be supplied; the rest take
-- their column defaults.
revoke insert on profiles from authenticated;
grant insert (id, display_name, visibility, current_title_id) on profiles to authenticated;

-- ------------------------------------------------- half 2: derive them
create or replace function push_aggregates(
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
    derived_xp bigint;
    derived_strength bigint;
    derived_titles int;
begin
    if uid is null then
        raise exception 'push_aggregates requires a signed-in hunter';
    end if;

    -- XP from the set rows, exactly as Xp.award() computes it on the device.
    select
        coalesce(sum(s.per_session_xp), 0),
        coalesce(sum(s.strength_score), 0)
    into derived_xp, derived_strength
    from (
        select
            se.strength_score,
            15 * count(ss.id) filter (where ss.done)
                + coalesce(sum(ss.reps) filter (where ss.done), 0)
                + 25 as per_session_xp
        from sessions se
        left join session_sets ss on ss.session_id = se.id
        where se.user_id = uid and se.completed_at is not null
        group by se.id, se.strength_score
    ) s;

    select count(*) into derived_titles from earned_titles where user_id = uid;

    -- greatest(), not assignment: a partial sync (sessions pushed, sets still
    -- queued) would otherwise walk a real hunter's totals backwards. The same
    -- monotonic rule the client used to apply, now where it cannot be bypassed.
    update profiles p set
        total_xp          = greatest(p.total_xp, derived_xp),
        level             = greatest(p.level, monarch_level(greatest(p.total_xp, derived_xp))),
        titles_count      = greatest(p.titles_count, derived_titles),
        lifetime_strength = greatest(p.lifetime_strength, derived_strength),
        -- streak legitimately falls to zero after missed days, so it is NOT
        -- monotonic - it is simply bounded.
        streak_days       = least(greatest(coalesce(p_streak_days, 0), 0), 3650),
        shadow_essence    = least(greatest(coalesce(p_shadow_essence, 0), 0), 1000000000),
        shadow_count      = least(greatest(coalesce(p_shadow_count, 0), 0), 100000),
        shadow_rate       = least(greatest(coalesce(p_shadow_rate, 0), 0), 1000000)
    where p.id = uid;
end;
$$;

revoke execute on function public.push_aggregates(int, bigint, int, double precision) from public;
grant execute on function public.push_aggregates(int, bigint, int, double precision) to authenticated;
