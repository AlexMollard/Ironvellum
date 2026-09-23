-- 0016_aggregate_grants.sql
-- Re-assert 0011's function grants on a live database that did not get them.
--
-- 0011 as committed revokes both functions from anon (fixed in place in the
-- same change as 0015). The live project was measured on 2026-09-23 after
-- 0011 was applied, with the publishable key shipped in every APK:
--   - monarch_level(bigint) answered 200: anon could execute it;
--   - push_aggregates(...) answered P0001 "requires a signed-in hunter" from
--     its own body, not 42501: anon could execute it too.
-- Neither leaks data (the first is pure arithmetic, the second refuses a null
-- auth.uid()), but both were meant to be closed, and a SECURITY DEFINER body
-- reachable by anyone is one refactor away from a hole.
--
-- Idempotent in the 0009 style: safe to re-apply to the live database.

-- monarch_level() is only ever called inside push_aggregates' SECURITY DEFINER
-- body, where the effective user is the owner. No client role needs it.
revoke execute on function public.monarch_level(bigint) from public, anon, authenticated;

-- push_aggregates() is the one way a signed-in lifter writes ranked numbers.
revoke execute on function public.push_aggregates(bigint, bigint, int, bigint, int, double precision) from public, anon;
grant execute on function public.push_aggregates(bigint, bigint, int, bigint, int, double precision) to authenticated;

-- The version beacon (see 0014). EVERY FUTURE MIGRATION BUMPS THIS LITERAL and
-- Cloud.kt's NEEDED_SCHEMA_VERSION with it.
create or replace function public.schema_version() returns int
language sql stable as $$ select 16 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon, authenticated;
