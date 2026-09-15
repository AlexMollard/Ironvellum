-- Backend assertion suite. Exits non-zero on the first broken guarantee, so it
-- can gate CI the way the instrumented suite gates the app.
--
-- The probes beside this file are for reading: they print values a human
-- compares. This one decides. Every check restates a guarantee some migration
-- deliberately established, so a later migration that quietly undoes one fails
-- here instead of in production.
--
--   docker run -d --rm --name pg -e POSTGRES_PASSWORD=probe -p 5432:5432 postgres:16
--   psql -f supabase/test/supabase_stub.sql
--   for f in supabase/migrations/*.sql; do psql -v ON_ERROR_STOP=1 -f "$f"; done
--   psql -v ON_ERROR_STOP=1 -f supabase/test/assert_all.sql
--
-- Order matters: helpers, then a schema-presence guard (so an unapplied chain
-- fails on its own line rather than as a scatter of denials that look like the
-- guarantees holding), then fixtures, then the checks.
\set ON_ERROR_STOP 1

-- ------------------------------------------------------------------- helpers
create or replace function assert_true(ok boolean, what text)
returns void language plpgsql as $$
begin
    if ok is not true then
        raise exception 'ASSERTION FAILED: %', what;
    end if;
end;
$$;

-- Runs a statement as a hunter and reports whether it was refused FOR THE
-- EXPECTED REASON.
--
-- An earlier version caught `when others` and returned true, which meant any
-- error read as "refused": a typo, a missing table, or a migration that never
-- applied would have turned most of this suite vacuously green. The SQLSTATE
-- must match; anything else is re-raised so it surfaces as a failure.
--
--   42501  insufficient_privilege  - a revoked grant, or an RLS refusal
--   23514  check_violation         - a bounding CHECK constraint
--   P0001  raise_exception         - a guard trigger saying no
create or replace function refused_as(uid uuid, stmt text, expected text[])
returns boolean language plpgsql as $$
declare
    state text;
    msg text;
begin
    perform set_config('probe.uid', uid::text, true);
    execute 'set local role authenticated';
    begin
        execute stmt;
        execute 'reset role';
        return false;
    exception when others then
        get stacked diagnostics state = returned_sqlstate, msg = message_text;
        execute 'reset role';
        if state = any (expected) then
            return true;
        end if;
        raise exception 'unexpected failure (SQLSTATE %) while checking a refusal: % [%]',
            state, msg, stmt;
    end;
end;
$$;

-- ---------------------------------------------------------- schema is present
do $$
begin
    perform assert_true(to_regclass('auth.users') is not null, 'auth.users missing: apply supabase/test/supabase_stub.sql first');
    perform assert_true(to_regclass('public.profiles') is not null, 'profiles missing: the migration chain did not apply');
    perform assert_true(to_regclass('public.session_likes') is not null, 'session_likes missing: the chain stopped before 0004');
    perform assert_true(to_regproc('public.find_hunter') is not null, 'find_hunter missing: the chain stopped before 0010');
    perform assert_true(to_regproc('public.push_aggregates') is not null, 'push_aggregates missing: the chain stopped before 0011');
    perform assert_true(to_regproc('public.monarch_level') is not null, 'monarch_level missing: the chain stopped before 0011');
end $$;

-- ------------------------------------------------------------------ fixtures
-- do UPDATE, not nothing: the checks below mutate these rows, so a second run
-- has to start from the same place or it fails on its own leftovers.
insert into auth.users (instance_id, id, aud, role, email, encrypted_password, created_at, updated_at)
values ('00000000-0000-0000-0000-000000000000','a5500000-0000-4000-8000-000000000001','authenticated','authenticated','one@m.test','',now(),now()),
       ('00000000-0000-0000-0000-000000000000','a5500000-0000-4000-8000-000000000002','authenticated','authenticated','two@m.test','',now(),now()),
       ('00000000-0000-0000-0000-000000000000','a5500000-0000-4000-8000-000000000003','authenticated','authenticated','three@m.test','',now(),now())
on conflict (id) do nothing;

insert into profiles (id, display_name, visibility) values
    ('a5500000-0000-4000-8000-000000000001','Ayla','friends'),
    ('a5500000-0000-4000-8000-000000000002','Borin','friends'),
    ('a5500000-0000-4000-8000-000000000003','Cass','friends')
on conflict (id) do update set
    display_name = excluded.display_name,
    visibility = excluded.visibility;

insert into friendships (requester_id, addressee_id, accepted)
values ('a5500000-0000-4000-8000-000000000001','a5500000-0000-4000-8000-000000000002', true)
on conflict do nothing;

insert into sessions (id, user_id, local_id, label, started_at, completed_at, strength_score)
values ('a5511111-0000-4000-8000-000000000001','a5500000-0000-4000-8000-000000000001',
        1,'Pull', now() - interval '2 hour', now() - interval '1 hour', 42)
on conflict (id) do nothing;

insert into earned_titles (user_id, title_id, unlocked_at)
values ('a5500000-0000-4000-8000-000000000001','first-blood', now())
on conflict do nothing;

-- -------------------------------------------------------------- row security
do $$
declare
    n int;
    leaky text;
begin
    -- 0001: RLS is the only gate in this schema, so every table must have it on.
    select count(*) into n
    from pg_tables t
    where t.schemaname = 'public'
      and t.tablename in ('profiles','friendships','sessions','session_sets','earned_titles','level_ups','session_likes')
      and not t.rowsecurity;
    perform assert_true(n = 0, format('%s public table(s) have RLS disabled', n));

    -- 0004/0007/0008: a view without security_invoker bypasses the RLS of the
    -- tables beneath it.
    select string_agg(c.relname, ', ') into leaky
    from pg_class c
    join pg_namespace ns on ns.oid = c.relnamespace
    where ns.nspname = 'public'
      and c.relkind = 'v'
      and coalesce((
            select o.option_value
            from pg_options_to_table(c.reloptions) o
            where o.option_name = 'security_invoker'
      ), 'false') <> 'true';
    perform assert_true(leaky is null, format('view(s) without security_invoker: %s', leaky));
end $$;

-- -------------------------------------------------------------- the guarantees
do $$
declare
    ayla uuid := 'a5500000-0000-4000-8000-000000000001';
    cass uuid := 'a5500000-0000-4000-8000-000000000003';
    n int;
    xp bigint;
begin
    -- 0009 F2: the friendship oracle must not be callable by a hunter.
    perform assert_true(
        refused_as(cass, format('select is_friend(%L, %L)', ayla, cass), array['42501']),
        'is_friend() is still callable by authenticated (the friendship oracle is open)'
    );

    -- 0009 F2: but can_view must remain callable, or every profile read breaks.
    perform assert_true(
        not refused_as(cass, format('select can_view(%L)', ayla), array['42501']),
        'can_view() is no longer callable, which breaks profiles_read'
    );

    -- 0009 F3: free text a feed row re-serves must be bounded. The session
    -- belongs to Ayla, so RLS permits the write and the CHECK is what refuses.
    perform assert_true(
        refused_as(ayla,
            'insert into session_sets (session_id, exercise_name, set_index, reps) values (''a5511111-0000-4000-8000-000000000001'', repeat(''x'', 5000), 90, 1)',
            array['23514']),
        'session_sets.exercise_name accepts unbounded text (one row floods every feed)'
    );

    -- 0009 F4: a future session would pin itself to the top of every feed.
    perform assert_true(
        refused_as(ayla, format(
            'insert into sessions (user_id, local_id, label, started_at, completed_at) values (%L, 999, ''pin'', ''9999-12-30'', ''9999-12-31'')',
            ayla), array['P0001']),
        'sessions accepts a future completed_at (a row can pin itself to every feed)'
    );

    -- 0010 F6: discovery must work for a stranger...
    perform set_config('probe.uid', cass::text, true);
    set local role authenticated;
    select count(*) into n from find_hunter('Ayla');
    perform assert_true(n = 1, 'find_hunter() cannot see a friends-only hunter: friend requests cannot bootstrap');
    -- ...without becoming a roster walker...
    select count(*) into n from find_hunter('Ay');
    perform assert_true(n = 0, 'find_hunter() matches a prefix, so the roster can be enumerated');
    -- ...and without leaking the profile behind the name.
    select count(*) into n from profiles where id = ayla;
    perform assert_true(n = 0, 'a stranger can read a friends-only profile');
    reset role;

    -- 0011 F1: no ranked column may be written directly.
    perform assert_true(
        refused_as(ayla, format('update profiles set total_xp = 9223372036854775807 where id = %L', ayla), array['42501']),
        'total_xp is directly writable: one PATCH tops the leaderboard'
    );
    perform assert_true(
        refused_as(ayla, format('update profiles set level = 99999 where id = %L', ayla), array['42501']),
        'level is directly writable'
    );

    -- 0011: the columns a hunter genuinely owns must still be writable. The
    -- values match the fixtures so the suite stays re-runnable.
    perform assert_true(
        not refused_as(ayla, format('update profiles set display_name = ''Ayla'', visibility = ''friends'' where id = %L', ayla), array['42501']),
        'a hunter can no longer rename themselves'
    );

    -- 0011: the RPC bounds a claim and derives what the server can know.
    perform set_config('probe.uid', ayla::text, true);
    set local role authenticated;
    perform push_aggregates(9223372036854775807, 9223372036854775807, 99999, 9223372036854775807, 99999, 1e12);
    reset role;
    select total_xp, titles_count into xp, n from profiles where id = ayla;
    perform assert_true(xp <= 100000000, format('total_xp ceiling does not hold (stored %s)', xp));
    perform assert_true(n = 1, format('titles_count is not derived from earned_titles (got %s)', n));

    -- 0011: and the monotonic rule protects a reinstall.
    perform set_config('probe.uid', ayla::text, true);
    set local role authenticated;
    perform push_aggregates(0, 0, 0, 0, 0, 0);
    reset role;
    select total_xp into xp from profiles where id = ayla;
    perform assert_true(xp > 0, 'a fresh install reporting zero walked a real total backwards');

    -- 0011: level always follows the XP that justifies it.
    select level into n from profiles where id = ayla;
    perform assert_true(n = monarch_level(xp), 'level and total_xp disagree');

    -- Xp.progress() parity, both sides of every threshold.
    perform assert_true(
        (select bool_and(monarch_level(50::bigint * g * (g - 1)) = g
                     and monarch_level(50::bigint * g * (g - 1) - 1) = g - 1)
         from generate_series(2, 60) g),
        'the SQL level curve disagrees with Xp.progress()'
    );

    raise notice 'ALL BACKEND ASSERTIONS PASSED';
end $$;

drop function if exists assert_true(boolean, text);
drop function if exists refused_as(uuid, text, text[]);
