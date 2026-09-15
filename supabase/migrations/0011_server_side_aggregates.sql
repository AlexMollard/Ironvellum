-- 0011_server_side_aggregates.sql
-- Forward fix for review finding F1. Idempotent: may be re-applied.
--
-- THE HOLE: `profiles_update` constrains WHOSE row is written, never WHAT, so
-- one PATCH with a normal account topped both boards permanently — and the
-- client's own max() merge made it sticky, so no honest push could lower it.
--
-- WHAT THIS DOES, and the line it deliberately does not cross.
--
-- A first draft of this migration recomputed total_xp server-side from the set
-- rows. That was wrong, and the reason is worth recording so nobody tries it
-- again: the device's award is
--
--     lifting XP (15/set + 1/rep + 25)          -- Xp.award()
--   + activity XP (duration, distance, grade)   -- ActivityScore.xp()
--   + quest bonus when the day's preset is the one completed
--
-- Only the first term is derivable from what the server holds. Recomputing
-- would have UNDER-REPORTED every hunter whose training is running, skipping
-- or bouldering — a correctness regression worse than the cheat it prevents.
-- Making the server authoritative over XP means moving the whole economy
-- (activity curves, quest detection, offline reconciliation) server-side. That
-- is a product decision, not hardening, so it is not taken here.
--
-- Instead this closes every hole that does NOT require a second economy:
--
--   * the client loses the privilege to write any ranked column directly, so
--     a crafted PATCH is refused rather than merged;
--   * `titles_count` is DERIVED — earned_titles rows are server-visible, so
--     there is no reason to trust a claim about them;
--   * `level` is DERIVED from total_xp by the same curve as Xp.progress(), so
--     it can never be stated independently of the XP that justifies it;
--   * total_xp, lifetime_strength and the shadow figures remain CLAIMS, but
--     monotonic (a reinstall cannot walk them back) and bounded by ceilings, so
--     the worst case is a plausible-looking number rather than bigint max.
--
-- RESIDUAL, stated plainly: a determined cheat can still claim XP and strength
-- up to those ceilings. Closing that needs the server-authoritative economy
-- above. What is dead is the unbounded one-request forgery.

-- ------------------------------------------------- the level curve, in SQL
-- Closed form with an exact integer correction: floating sqrt picks the
-- candidate, integer arithmetic settles the boundary, so this cannot disagree
-- with Xp.progress() at a level threshold. Level n needs a cumulative
-- 50*n*(n-1), because xpForNextLevel(level) = 100 * level.
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

-- ------------------------------------------------- take the write privilege
-- Supabase grants table-level UPDATE/INSERT to `authenticated`, which covers
-- every column, so a column-level REVOKE alone does not bite: the table-level
-- privilege has to go first, then only the genuinely user-chosen columns come
-- back. RLS and column privileges are ANDed, so `profiles_update` keeps doing
-- its job for what remains. INSERT is narrowed for the same reason — a profile
-- could otherwise be CREATED pre-inflated.
revoke update on profiles from authenticated;
grant update (display_name, visibility, current_title_id) on profiles to authenticated;

revoke insert on profiles from authenticated;
grant insert (id, display_name, visibility, current_title_id) on profiles to authenticated;

-- ------------------------------------------------- the one way in
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

    -- Derived: the rows are right here, so a claim about them is never needed.
    select count(*) into derived_titles from earned_titles where user_id = uid;

    -- Claimed, but bounded. The ceilings are far above any real hunter and far
    -- below the bigint range a forgery wants.
    claimed_xp := least(greatest(coalesce(p_total_xp, 0), 0), 100000000);
    claimed_strength := least(greatest(coalesce(p_lifetime_strength, 0), 0), 100000000);

    -- greatest(), not assignment: a fresh install or a local database reset
    -- reports LV 1 / 0 XP, and blindly writing that would destroy the only
    -- server-side copy. This is the monotonic rule that used to live in the
    -- client's mergeAggregates, now somewhere a crafted request cannot skip it.
    update profiles p set
        total_xp          = greatest(p.total_xp, claimed_xp),
        -- level follows the XP, always. It is never a separate claim.
        level             = monarch_level(greatest(p.total_xp, claimed_xp)),
        titles_count      = derived_titles,
        lifetime_strength = greatest(p.lifetime_strength, claimed_strength),
        -- streak legitimately falls to zero after missed days, so it is bounded
        -- rather than monotonic.
        streak_days       = least(greatest(coalesce(p_streak_days, 0), 0), 3650),
        -- Idle state never leaves the device by design, so the server has
        -- nothing to derive these from. Essence and count are cumulative;
        -- rate is a current reading that legitimately falls.
        shadow_essence    = greatest(p.shadow_essence, least(greatest(coalesce(p_shadow_essence, 0), 0), 1000000000)),
        shadow_count      = greatest(p.shadow_count, least(greatest(coalesce(p_shadow_count, 0), 0), 100000)),
        shadow_rate       = least(greatest(coalesce(p_shadow_rate, 0), 0), 1000000)
    where p.id = uid;
end;
$$;

revoke execute on function public.push_aggregates(bigint, bigint, int, bigint, int, double precision) from public;
grant execute on function public.push_aggregates(bigint, bigint, int, bigint, int, double precision) to authenticated;
