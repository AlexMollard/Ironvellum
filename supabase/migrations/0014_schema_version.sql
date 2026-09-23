-- 0014_schema_version.sql
-- The version beacon. The app probes schema_version() as anon before pointing
-- a lifter's training at a custom backend (Settings → CLOUD, TEST), so a
-- half-migrated project is reported before any data is sent to it.
--
-- Idempotent in the 0009 style: safe to re-apply to any live database.
--
-- EVERY FUTURE MIGRATION BUMPS THE LITERAL in the select below, and the same
-- number is mirrored in app/src/main/kotlin/com/ironvellum/app/data/cloud/Cloud.kt
-- (NEEDED_SCHEMA_VERSION). A migration applied without bumping the literal
-- reads to the app as "schema out of date" forever.

create or replace function public.schema_version() returns int
language sql stable as $$ select 14 $$;

-- Postgres grants EXECUTE on a new function to PUBLIC by default, and
-- Supabase exposes public-schema functions at /rest/v1/rpc/. The probe runs
-- as anon, so the grant must survive — but make it explicit (house style
-- since 0009/F2) instead of relying on the default.
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon, authenticated;
