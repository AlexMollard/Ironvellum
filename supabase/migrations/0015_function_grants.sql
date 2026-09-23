-- 0015_function_grants.sql
-- Make 0009 and 0010's function lock-downs actually hold on Supabase.
--
-- Both ran `revoke execute ... from public`. On plain Postgres that removes
-- the only grant. Supabase additionally grants EXECUTE on every new
-- public-schema function DIRECTLY to anon and authenticated (default
-- privileges), and a revoke from PUBLIC does not touch a direct grant. So
-- after 0009 the live project still answered `is_friend(a, b)` for anyone
-- holding the publishable key shipped in every APK: the friendship oracle
-- 0009 exists to close stayed open. The backend suite missed it because its
-- stub did not emulate those default grants; it does now.
--
-- Every read policy is `to authenticated`, so anon never evaluates can_view()
-- and loses nothing it legitimately used.
--
-- Idempotent in the 0009 style: safe to re-apply to the live database.

-- is_friend() is only ever called inside can_view()'s SECURITY DEFINER body,
-- where the effective user is the owner. No client role needs it.
revoke execute on function public.is_friend(uuid, uuid) from public, anon, authenticated;

-- can_view() is evaluated by the read policies as the querying role, which
-- is always `authenticated`.
revoke execute on function public.can_view(uuid) from public, anon;
grant execute on function public.can_view(uuid) to authenticated;

-- find_hunter() is the by-name friend lookup; only a signed-in lifter asks.
revoke execute on function public.find_hunter(text) from public, anon;
grant execute on function public.find_hunter(text) to authenticated;

-- The version beacon (see 0014). EVERY FUTURE MIGRATION BUMPS THIS LITERAL and
-- Cloud.kt's NEEDED_SCHEMA_VERSION with it.
create or replace function public.schema_version() returns int
language sql stable as $$ select 15 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon, authenticated;
