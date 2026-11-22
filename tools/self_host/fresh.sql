-- Everything below runs in the same transaction and connection as the fresh baseline.
BEGIN;
SELECT pg_advisory_xact_lock(782301419);
LOCK TABLE auth.users, storage.objects, storage.buckets IN SHARE MODE;
DO $fresh$
BEGIN
  IF EXISTS (SELECT FROM auth.users) OR EXISTS (SELECT FROM storage.objects)
     OR EXISTS (SELECT FROM storage.buckets) OR to_regnamespace('ironvellum_mcp') IS NOT NULL
     OR EXISTS (SELECT FROM pg_trigger WHERE tgrelid = 'auth.users'::regclass AND tgname = 'on_auth_user_created')
     OR EXISTS (
       -- Check objects that depend on an application namespace, including private
       -- tables, routines and types. The pinned stack maintains its own schemas.
       SELECT FROM pg_depend obj JOIN pg_namespace n ON n.oid = obj.refobjid
       WHERE obj.refclassid = 'pg_namespace'::regclass AND obj.deptype = 'n'
         AND n.nspname !~ '^pg_' AND n.nspname NOT IN (
           'information_schema', 'auth', 'storage', 'extensions', 'realtime', '_realtime',
           '_analytics', '_supavisor', 'supabase_functions', 'pgbouncer', 'graphql', 'graphql_public', 'vault',
           'net', 'cron', 'pgsodium', 'pgsodium_masks'
         )
         AND obj.classid NOT IN ('pg_extension'::regclass, 'pg_default_acl'::regclass)
         AND NOT EXISTS (
           SELECT FROM pg_depend member WHERE member.classid = obj.classid AND member.objid = obj.objid
             AND member.refclassid = 'pg_extension'::regclass AND member.deptype = 'e'
         )
     ) THEN
    RAISE EXCEPTION 'Fresh setup requires an empty application database. Back up and follow the upgrade guide.';
  END IF;
END
$fresh$;
