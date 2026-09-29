-- Run right after supabase/reset.sql: the project must be empty of everything
-- the baseline creates, and still carry everything Supabase itself provides.
-- The baseline is then applied again and assert_all.sql run on top, so a reset
-- that leaves a stray table, function or type behind fails here, and one that
-- damages the schema or its default grants fails on the re-apply.
\set ON_ERROR_STOP 1

do $$
declare
    stray text;
begin
    if (select count(*) from auth.users) <> 0 then
        raise exception 'ASSERTION FAILED: reset left % row(s) in auth.users', (select count(*) from auth.users);
    end if;

    if exists (
        select 1 from pg_trigger t
        where t.tgrelid = 'auth.users'::regclass and t.tgname = 'on_auth_user_created' and not t.tgisinternal
    ) then
        raise exception 'ASSERTION FAILED: reset left the on_auth_user_created trigger on auth.users';
    end if;

    -- Anything left in public that is not owned by an extension (pgcrypto's
    -- functions live there on a plain Postgres).
    select string_agg(x.what, ', ' order by x.what) into stray
    from (
        select 'relation ' || c.relname as what
        from pg_class c
        join pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public'
          and c.relkind in ('r', 'p', 'v', 'm', 'S', 'f')
          and not exists (select 1 from pg_depend d
                          where d.classid = 'pg_class'::regclass and d.objid = c.oid and d.deptype = 'e')
        union all
        select 'function ' || p.proname
        from pg_proc p
        join pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public'
          and not exists (select 1 from pg_depend d
                          where d.classid = 'pg_proc'::regclass and d.objid = p.oid and d.deptype = 'e')
        union all
        select 'type ' || t.typname
        from pg_type t
        join pg_namespace n on n.oid = t.typnamespace
        where n.nspname = 'public'
          and t.typrelid = 0
          and t.typelem = 0
          and not exists (select 1 from pg_depend d
                          where d.classid = 'pg_type'::regclass and d.objid = t.oid and d.deptype = 'e')
    ) x;
    if stray is not null then
        raise exception 'ASSERTION FAILED: reset left objects behind in public: %', stray;
    end if;

    -- What reset must NOT take: the schema and Supabase's default grants.
    if to_regnamespace('public') is null
       or not has_schema_privilege('anon', 'public', 'usage')
       or not has_schema_privilege('authenticated', 'public', 'usage') then
        raise exception 'ASSERTION FAILED: reset damaged the public schema or its usage grants';
    end if;
    if (select count(*) from pg_default_acl where defaclnamespace = 'public'::regnamespace) < 2 then
        raise exception 'ASSERTION FAILED: reset lost the default privileges Supabase provisions in public';
    end if;
    if to_regclass('auth.users') is null then
        raise exception 'ASSERTION FAILED: reset dropped auth.users';
    end if;
    if not exists (select 1 from pg_extension where extname = 'pgcrypto') then
        raise exception 'ASSERTION FAILED: reset dropped an extension';
    end if;

    raise notice 'RESET LEFT NOTHING BEHIND';
end $$;
