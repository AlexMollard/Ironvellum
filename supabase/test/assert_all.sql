-- Backend assertion suite. Exits non-zero on the first broken guarantee, so it
-- can gate CI the way the instrumented suite gates the app.
--
-- The probes beside this file are for reading: they print values a human
-- compares. This one decides. Every check restates a guarantee the baseline
-- deliberately establishes, so an edit that quietly undoes one fails here
-- instead of in production.
--
--   docker run -d --rm --name pg -e POSTGRES_PASSWORD=probe -p 5432:5432 postgres:16
--   psql -f supabase/test/supabase_stub.sql
--   psql -v ON_ERROR_STOP=1 -f supabase/migrations/0001_baseline.sql
--   psql -v ON_ERROR_STOP=1 -f supabase/test/assert_all.sql
--
-- Order matters: helpers, then a schema-presence guard (so an unapplied
-- baseline fails on its own line rather than as a scatter of denials that look
-- like the guarantees holding), then fixtures, then the checks.
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

-- The refusal's SQLSTATE and message, or null when the statement ran. For the
-- rate limits the message IS the contract: the app shows it verbatim.
create or replace function refusal(uid uuid, stmt text)
returns text language plpgsql as $$
declare
    state text;
    msg text;
begin
    perform set_config('probe.uid', uid::text, true);
    execute 'set local role authenticated';
    begin
        execute stmt;
        execute 'reset role';
        return null;
    exception when others then
        get stacked diagnostics state = returned_sqlstate, msg = message_text;
        execute 'reset role';
        return state || ': ' || msg;
    end;
end;
$$;

-- A statement the lifter is entitled to run; the error text surfaces if not.
create or replace function must_run(uid uuid, stmt text, what text)
returns void language plpgsql as $$
declare
    r text := refusal(uid, stmt);
begin
    if r is not null then
        raise exception 'ASSERTION FAILED: % [%]', what, r;
    end if;
end;
$$;

-- The first column of the first row of [q], read as the lifter.
create or replace function value_as(uid uuid, q text)
returns text language plpgsql as $$
declare
    v text;
begin
    perform set_config('probe.uid', uid::text, true);
    execute 'set local role authenticated';
    execute q into v;
    execute 'reset role';
    return v;
end;
$$;

-- The same, as the shipped publishable key: the anon role.
create or replace function refused_as_anon(stmt text, expected text[])
returns boolean language plpgsql as $$
declare
    state text;
    msg text;
begin
    execute 'set local role anon';
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
        raise exception 'unexpected failure (SQLSTATE %) while checking an anon refusal: % [%]',
            state, msg, stmt;
    end;
end;
$$;

-- Signs a lifter up the way GoTrue does: one auth.users row, with the sign-up
-- metadata, and nothing else. Returns the display_name of the profile the
-- sign-up trigger made for it (null when it made none).
create or replace function sign_up(uid uuid, meta jsonb)
returns text language plpgsql as $$
begin
    insert into auth.users (instance_id, id, aud, role, email, encrypted_password, raw_user_meta_data, created_at, updated_at)
    values ('00000000-0000-0000-0000-000000000000', uid, 'authenticated', 'authenticated',
            uid || '@m.test', '', meta, now(), now());
    return (select p.display_name from profiles p where p.id = uid);
end;
$$;

-- ---------------------------------------------------------- schema is present
do $$
begin
    perform assert_true(to_regclass('auth.users') is not null, 'auth.users missing: apply supabase/test/supabase_stub.sql first');
    perform assert_true(to_regclass('public.profiles') is not null, 'profiles missing: the baseline did not apply');
    perform assert_true(to_regclass('public.session_likes') is not null, 'session_likes missing: the baseline did not apply completely');
    perform assert_true(to_regproc('public.find_hunter') is not null, 'find_hunter missing: the baseline did not apply completely');
    perform assert_true(to_regproc('public.push_aggregates') is not null, 'push_aggregates missing: the baseline did not apply completely');
    perform assert_true(to_regproc('public.monarch_level') is not null, 'monarch_level missing: the baseline did not apply completely');
    perform assert_true(to_regclass('public.session_comments') is not null, 'session_comments missing: the baseline did not apply completely');
    perform assert_true(to_regproc('public.my_inbox') is not null, 'my_inbox missing: the baseline did not apply completely');
    perform assert_true(to_regproc('public.handle_new_user') is not null, 'handle_new_user missing: the baseline did not apply completely');
    perform assert_true(to_regproc('public.display_name_available') is not null, 'display_name_available missing: the baseline did not apply completely');
    perform assert_true(to_regclass('public.warbands') is not null, 'warbands missing: the baseline did not apply completely');
    perform assert_true(to_regclass('public.warband_members') is not null, 'warband_members missing: the baseline did not apply completely');
    perform assert_true(to_regproc('public.create_warband') is not null, 'create_warband missing: the baseline did not apply completely');
    perform assert_true(to_regproc('public.join_warband') is not null, 'join_warband missing: the baseline did not apply completely');
    perform assert_true(to_regproc('public.leave_warband') is not null, 'leave_warband missing: the baseline did not apply completely');
    perform assert_true(to_regproc('public.my_warband') is not null, 'my_warband missing: the baseline did not apply completely');
    perform assert_true(
        exists (select 1 from pg_trigger t where t.tgrelid = 'auth.users'::regclass and t.tgname = 'on_auth_user_created' and not t.tgisinternal),
        'on_auth_user_created is not on auth.users: no profile is made at sign-up');
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

-- 1.4 "Talk" runs on lifters of its own, so a block or mute can never
-- mask a visibility leak the older checks exist to catch. Deleting the
-- identities first cascades everything they touched, so a re-run starts clean.
-- Dara is public and allied with Eli, Gus and Hale; Fenn is a private
-- stranger; Ivo is the rate-limit lifter.
delete from auth.users where id in (
    'a5500000-0000-4000-8000-000000000041','a5500000-0000-4000-8000-000000000042',
    'a5500000-0000-4000-8000-000000000043','a5500000-0000-4000-8000-000000000044',
    'a5500000-0000-4000-8000-000000000045','a5500000-0000-4000-8000-000000000046');
insert into auth.users (instance_id, id, aud, role, email, encrypted_password, created_at, updated_at)
select '00000000-0000-0000-0000-000000000000', ('a5500000-0000-4000-8000-0000000000' || n)::uuid,
       'authenticated', 'authenticated', 'talk' || n || '@m.test', '', now(), now()
from unnest(array['41','42','43','44','45','46']) n;
insert into profiles (id, display_name, visibility) values
    ('a5500000-0000-4000-8000-000000000041','Dara','public'),
    ('a5500000-0000-4000-8000-000000000042','Eli','public'),
    ('a5500000-0000-4000-8000-000000000043','Fenn','private'),
    ('a5500000-0000-4000-8000-000000000044','Gus','public'),
    ('a5500000-0000-4000-8000-000000000045','Hale','public'),
    ('a5500000-0000-4000-8000-000000000046','Ivo','public')
-- The sign-up trigger already made a neutral profile for each identity above.
on conflict (id) do update set
    display_name = excluded.display_name,
    visibility = excluded.visibility;
insert into friendships (requester_id, addressee_id, accepted) values
    ('a5500000-0000-4000-8000-000000000041','a5500000-0000-4000-8000-000000000042', true),
    ('a5500000-0000-4000-8000-000000000041','a5500000-0000-4000-8000-000000000044', true),
    ('a5500000-0000-4000-8000-000000000041','a5500000-0000-4000-8000-000000000045', true);
insert into sessions (id, user_id, local_id, label, title, note, started_at, completed_at, audience) values
    ('a5522222-0000-4000-8000-000000000001','a5500000-0000-4000-8000-000000000041',1,'Pull','Open day','felt strong', now() - interval '3 hour', now() - interval '2 hour','profile'),
    ('a5522222-0000-4000-8000-000000000002','a5500000-0000-4000-8000-000000000041',2,'Push','','', now() - interval '3 hour', now() - interval '2 hour','friends'),
    ('a5522222-0000-4000-8000-000000000003','a5500000-0000-4000-8000-000000000041',3,'Legs','','', now() - interval '3 hour', now() - interval '2 hour','private'),
    ('a5522222-0000-4000-8000-000000000004','a5500000-0000-4000-8000-000000000042',1,'Pull','','', now() - interval '3 hour', now() - interval '2 hour','profile'),
    ('a5522222-0000-4000-8000-000000000005','a5500000-0000-4000-8000-000000000044',1,'Pull','','', now() - interval '3 hour', now() - interval '2 hour','profile'),
    ('a5522222-0000-4000-8000-000000000006','a5500000-0000-4000-8000-000000000045',1,'Pull','','', now() - interval '3 hour', now() - interval '2 hour','profile'),
    ('a5522222-0000-4000-8000-000000000007','a5500000-0000-4000-8000-000000000046',1,'Pull','','', now() - interval '3 hour', now() - interval '2 hour','profile');
insert into session_sets (session_id, exercise_name, set_index, reps, done)
values ('a5522222-0000-4000-8000-000000000003','Squat',0,5,true);
insert into session_likes (session_id, user_id, kind) values
    ('a5522222-0000-4000-8000-000000000003','a5500000-0000-4000-8000-000000000041','salute'),
    ('a5522222-0000-4000-8000-000000000001','a5500000-0000-4000-8000-000000000044','iron'),
    ('a5522222-0000-4000-8000-000000000001','a5500000-0000-4000-8000-000000000045','flame');
insert into session_comments (id, session_id, user_id, body) values
    ('a5533333-0000-4000-8000-000000000001','a5522222-0000-4000-8000-000000000003','a5500000-0000-4000-8000-000000000041','private note'),
    ('a5533333-0000-4000-8000-000000000002','a5522222-0000-4000-8000-000000000001','a5500000-0000-4000-8000-000000000044','gus on pub'),
    ('a5533333-0000-4000-8000-000000000003','a5522222-0000-4000-8000-000000000004','a5500000-0000-4000-8000-000000000041','dara on eli'),
    ('a5533333-0000-4000-8000-000000000004','a5522222-0000-4000-8000-000000000004','a5500000-0000-4000-8000-000000000044','gus on eli'),
    ('a5533333-0000-4000-8000-000000000005','a5522222-0000-4000-8000-000000000001','a5500000-0000-4000-8000-000000000045','hale on pub'),
    ('a5533333-0000-4000-8000-000000000006','a5522222-0000-4000-8000-000000000004','a5500000-0000-4000-8000-000000000045','hale on eli'),
    ('a5533333-0000-4000-8000-000000000007','a5522222-0000-4000-8000-000000000001','a5500000-0000-4000-8000-000000000042','eli on pub');

-- Ayla holds a row in every 1.4 table, on both sides of each pair, so the
-- erase-account check at the end covers them.
insert into blocks (blocker_id, blocked_id)
values ('a5500000-0000-4000-8000-000000000001','a5500000-0000-4000-8000-000000000046')
on conflict do nothing;
insert into mutes (muter_id, muted_id) values
    ('a5500000-0000-4000-8000-000000000001','a5500000-0000-4000-8000-000000000043'),
    ('a5500000-0000-4000-8000-000000000045','a5500000-0000-4000-8000-000000000001')
on conflict do nothing;
insert into session_comments (session_id, user_id, body)
values ('a5511111-0000-4000-8000-000000000001','a5500000-0000-4000-8000-000000000001','ayla note');
insert into session_likes (session_id, user_id, kind)
values ('a5511111-0000-4000-8000-000000000001','a5500000-0000-4000-8000-000000000001','iron')
on conflict do nothing;
insert into inbox_seen (user_id) values ('a5500000-0000-4000-8000-000000000001') on conflict do nothing;
insert into reports (reporter_id, target_user_id, reason) values
    ('a5500000-0000-4000-8000-000000000001','a5500000-0000-4000-8000-000000000003','spam'),
    ('a5500000-0000-4000-8000-000000000003','a5500000-0000-4000-8000-000000000001','other');

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
      and t.tablename in ('profiles','friendships','sessions','session_sets','earned_titles','level_ups','session_likes',
                          'blocks','mutes','session_comments','inbox_seen','reports','friend_request_log','cloud_archives','lift_marks',
                          'warbands','warband_members')
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

-- ------------------------------------------------------------------ 1.4 talk
do $$
declare
    dara uuid := 'a5500000-0000-4000-8000-000000000041';
    eli  uuid := 'a5500000-0000-4000-8000-000000000042';
    fenn uuid := 'a5500000-0000-4000-8000-000000000043';
    gus  uuid := 'a5500000-0000-4000-8000-000000000044';
    hale uuid := 'a5500000-0000-4000-8000-000000000045';
    ivo  uuid := 'a5500000-0000-4000-8000-000000000046';
    w_pub  uuid := 'a5522222-0000-4000-8000-000000000001';
    w_fr   uuid := 'a5522222-0000-4000-8000-000000000002';
    w_priv uuid := 'a5522222-0000-4000-8000-000000000003';
    w_eli  uuid := 'a5522222-0000-4000-8000-000000000004';
    w_ivo  uuid := 'a5522222-0000-4000-8000-000000000007';
    c_hale_pub uuid := 'a5533333-0000-4000-8000-000000000005';
    c_hale_eli uuid := 'a5533333-0000-4000-8000-000000000006';
    r text;
    t text;
    i int;
    missing text;
begin
    -- ------------------------------------------------ grants
    -- blocked_between is an oracle for who blocked whom: no client role at all.
    perform assert_true(
        not has_function_privilege('public', 'public.blocked_between(uuid, uuid)', 'execute')
            and not has_function_privilege('anon', 'public.blocked_between(uuid, uuid)', 'execute')
            and not has_function_privilege('authenticated', 'public.blocked_between(uuid, uuid)', 'execute'),
        'blocked_between() is callable by a client role: anyone can ask who blocked whom'
    );
    perform assert_true(
        refused_as(eli, format('select blocked_between(%L, %L)', dara, gus), array['42501']),
        'blocked_between() answers a signed-in lifter'
    );
    -- The shipped publishable key is anon: none of the 1.4 RPCs may answer it,
    -- and the ones the app or the policies call must answer authenticated.
    foreach t in array array['public.mark_inbox_seen()', 'public.my_inbox()',
                             'public.can_view_session(uuid, text)', 'public.can_see_author(uuid)'] loop
        perform assert_true(not has_function_privilege('anon', t, 'execute'),
            format('anon can execute %s with the shipped key', t));
        perform assert_true(has_function_privilege('authenticated', t, 'execute'),
            format('authenticated cannot execute %s: the app or a read policy breaks', t));
    end loop;
    -- Every 1.4 table is closed to the anon key outright.
    foreach t in array array['blocks', 'mutes', 'session_comments', 'inbox_seen', 'reports', 'friend_request_log'] loop
        perform assert_true(
            not has_table_privilege('anon', t, 'select') and not has_table_privilege('anon', t, 'insert'),
            format('anon holds privileges on %s', t));
    end loop;

    -- ------------------------------------------------ audience
    -- A private workout is the owner's alone, even for an ally, and so is
    -- everything hanging off it.
    perform assert_true(value_as(eli, format('select count(*) from sessions where id = %L', w_priv))::int = 0,
        'a private workout is served to an ally');
    perform assert_true(value_as(eli, format('select count(*) from session_sets where session_id = %L', w_priv))::int = 0,
        'the sets of a private workout are served to an ally');
    perform assert_true(value_as(eli, format('select count(*) from session_likes where session_id = %L', w_priv))::int = 0,
        'the reactions on a private workout are served to an ally');
    perform assert_true(value_as(eli, format('select count(*) from session_comments where session_id = %L', w_priv))::int = 0,
        'the comments on a private workout are served to an ally');
    perform assert_true(value_as(eli, format('select count(*) from public_feed where session_id = %L', w_priv))::int = 0,
        'a private workout reaches an ally''s feed');
    perform assert_true(
        refused_as(eli, format('insert into session_comments (session_id, user_id, body) values (%L, %L, ''hi'')', w_priv, eli), array['42501']),
        'an ally can comment on a private workout they cannot see');
    perform assert_true(
        refused_as(eli, format('insert into session_likes (session_id, user_id) values (%L, %L)', w_priv, eli), array['42501']),
        'an ally can react to a private workout they cannot see');
    -- ...but the owner still sees it all, or the zeros above prove nothing.
    perform assert_true(value_as(dara, format('select count(*) from sessions where id = %L', w_priv))::int = 1
            and value_as(dara, format('select count(*) from session_sets where session_id = %L', w_priv))::int = 1
            and value_as(dara, format('select count(*) from session_comments where session_id = %L', w_priv))::int = 1,
        'the owner cannot see their own private workout');

    -- 'friends' narrows a public profile to allies.
    perform assert_true(value_as(fenn, format('select count(*) from sessions where id = %L', w_fr))::int = 0,
        'a friends-audience workout is served to a stranger because the profile is public');
    perform assert_true(value_as(fenn, format('select count(*) from public_feed where session_id = %L', w_fr))::int = 0,
        'a friends-audience workout reaches a stranger''s feed');
    perform assert_true(value_as(eli, format('select count(*) from sessions where id = %L', w_fr))::int = 1,
        'a friends-audience workout is hidden from an ally');
    perform assert_true(value_as(fenn, format('select count(*) from sessions where id = %L', w_pub))::int = 1,
        'a profile-audience workout on a public profile is hidden from a stranger (the 1.3 behaviour broke)');

    -- ------------------------------------------------ reactions
    -- A 1.3 client inserts without a kind, with ignoreDuplicates.
    perform must_run(eli, format('insert into session_likes (session_id, user_id) values (%L, %L) on conflict do nothing', w_pub, eli),
        'a 1.3 like (no kind) is refused');
    perform must_run(eli, format('insert into session_likes (session_id, user_id) values (%L, %L) on conflict do nothing', w_pub, eli),
        'a repeated 1.3 like is refused');
    perform assert_true((select kind from session_likes where session_id = w_pub and user_id = eli) = 'salute',
        'a like without a kind does not read as salute');
    -- 1.4 upserts the kind; PostgREST SETs every sent column on conflict.
    perform must_run(eli, format(
        'insert into session_likes (session_id, user_id, kind) values (%L, %L, ''flame'') '
        'on conflict (session_id, user_id) do update set session_id = excluded.session_id, '
        'user_id = excluded.user_id, kind = excluded.kind', w_pub, eli),
        'the reaction upsert is refused: the kind can never change');
    perform assert_true((select kind from session_likes where session_id = w_pub and user_id = eli) = 'flame',
        'the reaction upsert did not change the kind');
    perform must_run(fenn, format('update session_likes set kind = ''iron'' where session_id = %L and user_id = %L', w_pub, eli),
        'a no-op update by a stranger errored');
    perform assert_true((select kind from session_likes where session_id = w_pub and user_id = eli) = 'flame',
        'another lifter changed someone else''s reaction');
    perform assert_true(
        refused_as(eli, format('update session_likes set session_id = %L where session_id = %L and user_id = %L', w_fr, w_pub, eli), array['P0001']),
        'a reaction can be moved onto another workout');
    perform assert_true(
        refused_as(eli, format('insert into session_likes (session_id, user_id, kind) values (%L, %L, ''heart'')', w_fr, eli), array['23514']),
        'session_likes accepts an unknown kind');

    -- ------------------------------------------------ comments
    perform assert_true(
        refused_as(eli, format('insert into session_comments (session_id, user_id, body) values (%L, %L, '''')', w_pub, eli), array['23514'])
            and refused_as(eli, format('insert into session_comments (session_id, user_id, body) values (%L, %L, E''  \n '')', w_pub, eli), array['23514'])
            and refused_as(eli, format('insert into session_comments (session_id, user_id, body) values (%L, %L, repeat(''x'', 281))', w_pub, eli), array['23514']),
        'session_comments.body accepts an empty, blank or over-280 body');
    perform must_run(eli, format('insert into session_comments (session_id, user_id, body) values (%L, %L, repeat(''y'', 280))', w_pub, eli),
        'a 280-character comment is refused');
    perform must_run(eli, format('delete from session_comments where user_id = %L and body = repeat(''y'', 280)', eli),
        'an author cannot delete their own comment');
    perform assert_true(not exists (select 1 from session_comments where user_id = eli and body = repeat('y', 280)),
        'an author''s delete of their own comment removed nothing');

    -- The name on a comment is the author's, never the request's.
    perform assert_true(
        refused_as(eli, format('insert into session_comments (session_id, user_id, author_name, body) values (%L, %L, ''Dara'', ''hi'')', w_pub, eli), array['42501']),
        'a client can write session_comments.author_name: a comment can be signed with an ally''s name');
    perform assert_true(
        refused_as(eli, format('insert into session_comments (session_id, user_id, body) values (%L, %L, ''hi'')', w_pub, dara), array['42501']),
        'a comment can be posted under another lifter''s id');
    insert into session_comments (session_id, user_id, author_name, body, created_at)
    values (w_pub, eli, 'Dara', 'forged', '2000-01-01')
    returning author_name, created_at::text into r, t;
    delete from session_comments where user_id = eli and body = 'forged';
    perform assert_true(r = 'Eli', format('author_name is taken from the insert (stored %s), not the profile', r));
    perform assert_true(t::timestamptz > now() - interval '1 minute',
        format('created_at is taken from the insert (stored %s): a comment can be backdated', t));
    -- A rename reaches every old comment.
    update profiles set display_name = 'Halden' where id = hale;
    select author_name into r from session_comments where id = c_hale_pub;
    update profiles set display_name = 'Hale' where id = hale;
    perform assert_true(r = 'Halden', format('a rename left the old name on a comment (%s)', r));

    -- ------------------------------------------------ mute
    perform must_run(eli, format('insert into mutes (muter_id, muted_id) values (%L, %L)', eli, hale), 'a lifter cannot mute');
    perform assert_true((select muted_name from mutes where muter_id = eli and muted_id = hale) = 'Hale',
        'muted_name is not filled from the profile');
    perform assert_true(value_as(eli, format('select count(*) from session_comments where id = %L', c_hale_pub))::int = 0,
        'a muted lifter''s comment is still shown to the muter');
    perform assert_true(value_as(fenn, format('select count(*) from session_comments where id = %L', c_hale_pub))::int = 1
            and value_as(dara, format('select count(*) from session_comments where id = %L', c_hale_pub))::int = 1,
        'a mute hid the comment from everyone, not only the muter');
    perform assert_true(value_as(eli, format('select count(*) from public_feed where user_id = %L', hale))::int = 0,
        'a muted lifter''s workouts still reach the muter''s feed');
    perform assert_true(value_as(fenn, format('select count(*) from public_feed where user_id = %L', hale))::int = 1,
        'a mute hid the lifter''s workouts from someone who did not mute them');
    perform assert_true(value_as(eli, format('select count(*) from my_inbox() where actor_id = %L', hale))::int = 0,
        'a muted lifter''s comment still reaches the muter''s inbox');
    perform assert_true(value_as(eli, format('select count(*) from my_inbox() where actor_id = %L and kind = ''comment''', dara))::int = 1,
        'the inbox drops a comment by a lifter nobody muted');
    perform assert_true(value_as(hale, format('select count(*) from mutes where muted_id = %L', hale))::int = 0,
        'the muted lifter can see that they were muted');

    -- ------------------------------------------------ block
    -- Before: Gus is Dara's ally and both see each other, so every zero after
    -- the block is the block's doing.
    perform assert_true(value_as(gus, format('select count(*) from sessions where id = %L', w_pub))::int = 1
            and value_as(dara, format('select count(*) from session_comments where user_id = %L', gus))::int = 2
            and value_as(dara, format('select count(*) from my_inbox() where actor_id = %L', gus))::int = 3,
        'block fixtures are not visible before the block, so its checks would be vacuous');
    perform must_run(dara, format('insert into blocks (blocker_id, blocked_id) values (%L, %L)', dara, gus), 'a lifter cannot block');
    perform assert_true((select blocked_name from blocks where blocker_id = dara and blocked_id = gus) = 'Gus',
        'blocked_name is not filled from the profile (the BLOCKED list would show nothing)');
    perform assert_true(not exists (select 1 from friendships
                                    where least(requester_id, addressee_id) = least(dara, gus)
                                      and greatest(requester_id, addressee_id) = greatest(dara, gus)),
        'blocking left the friendship in place');
    perform assert_true(value_as(gus, format('select count(*) from profiles where id = %L', dara))::int = 0
            and value_as(dara, format('select count(*) from profiles where id = %L', gus))::int = 0,
        'a block does not hide both public profiles from each other');
    perform assert_true(value_as(gus, format('select count(*) from sessions where user_id = %L', dara))::int = 0
            and value_as(dara, format('select count(*) from sessions where user_id = %L', gus))::int = 0,
        'a block does not hide the workouts both ways');
    -- Both on a workout the reader can still see: Dara's own, and Eli's.
    perform assert_true(value_as(dara, format('select count(*) from session_comments where user_id = %L', gus))::int = 0,
        'the blocked lifter''s comments are still shown to the blocker');
    perform assert_true(value_as(gus, format('select count(*) from session_comments where user_id = %L', dara))::int = 0,
        'the blocker''s comments are still shown to the blocked lifter');
    perform assert_true(value_as(eli, format('select count(*) from session_comments where user_id in (%L, %L)', dara, gus))::int = 3,
        'a block between two lifters hid their comments from a third');
    perform assert_true(value_as(dara, format('select count(*) from session_likes where user_id = %L', gus))::int = 0,
        'the blocked lifter''s reaction is still shown to the blocker');
    perform assert_true(value_as(dara, format('select count(*) from my_inbox() where actor_id = %L', gus))::int = 0,
        'the blocked lifter still reaches the blocker''s inbox');
    perform assert_true(value_as(gus, 'select count(*) from blocks')::int = 0,
        'the blocked lifter can see who blocked them');
    r := refusal(gus, format('insert into friendships (requester_id, addressee_id) values (%L, %L)', gus, dara));
    perform assert_true(r = 'P0001: You can’t send an ally request to this lifter.',
        format('the blocked lifter can send the blocker an ally request (%s)', r));
    r := refusal(dara, format('insert into friendships (requester_id, addressee_id) values (%L, %L)', dara, gus));
    perform assert_true(r = 'P0001: You can’t send an ally request to this lifter.',
        format('the blocker can send the blocked lifter an ally request (%s)', r));

    -- ------------------------------------------------ friend request rate
    insert into friend_request_log (requester_id, sent_at)
    select ivo, now() - interval '1 hour' from generate_series(1, 19);
    perform must_run(ivo, format('insert into friendships (requester_id, addressee_id) values (%L, %L)', ivo, hale),
        'the 20th ally request of the day is refused');
    r := refusal(ivo, format('insert into friendships (requester_id, addressee_id) values (%L, %L)', ivo, eli));
    perform assert_true(r = 'P0001: Too many ally requests today — try again tomorrow.',
        format('the 21st ally request of the day is not refused (%s)', r));

    -- ------------------------------------------------ inbox
    -- Fenn is private, so Dara cannot read the profile; the request still
    -- has to say who is asking.
    perform must_run(fenn, format('insert into friendships (requester_id, addressee_id) values (%L, %L)', fenn, dara),
        'a stranger cannot send an ally request');
    perform assert_true(value_as(dara, format('select count(*) from profiles where id = %L', fenn))::int = 0,
        'fixture: the requester''s private profile is readable');
    perform assert_true(value_as(dara, format('select actor_name from my_inbox() where kind = ''request'' and actor_id = %L', fenn)) = 'Fenn',
        'an ally request in the inbox does not name the requester');
    perform assert_true(value_as(dara, 'select count(*) from my_inbox() where kind = ''comment''')::int = 2
            and value_as(dara, format('select count(*) from my_inbox() where kind = ''reaction'' and actor_id = %L and reaction = ''flame''', hale))::int = 1
            and value_as(dara, format('select count(*) from my_inbox() where kind = ''reaction'' and actor_id = %L and reaction = ''flame''', eli))::int = 1,
        'the inbox is missing a comment or reaction on the caller''s workout');
    -- Only the caller's items: nothing about anyone else's workouts or requests.
    perform assert_true(value_as(dara, format('select count(*) from my_inbox() where comment_id = %L', c_hale_eli))::int = 0
            and value_as(dara, format(
                'select count(*) from my_inbox() i where i.session_id is not null '
                'and not exists (select 1 from sessions s where s.id = i.session_id and s.user_id = %L)', dara))::int = 0
            and value_as(dara, format('select count(*) from my_inbox() where kind = ''request'' and actor_id <> %L', fenn))::int = 0,
        'my_inbox() returns another lifter''s comments, reactions or requests');
    -- Accepting stamps the server's clock, whatever the client sends.
    perform must_run(fenn, format('insert into friendships (requester_id, addressee_id) values (%L, %L)', fenn, eli),
        'a second ally request is refused');
    perform must_run(eli, format('update friendships set accepted = true, accepted_at = ''2000-01-01'' where requester_id = %L and addressee_id = %L', fenn, eli),
        'the addressee cannot accept');
    perform assert_true((select accepted_at from friendships where requester_id = fenn and addressee_id = eli) > now() - interval '1 minute',
        'accepted_at is not the server''s clock when a request is accepted');
    perform assert_true(value_as(fenn, format('select count(*) from my_inbox() where kind = ''accepted'' and actor_id = %L', eli))::int = 1,
        'an accepted request does not reach the requester''s inbox');
    perform assert_true(value_as(dara, 'select count(*) from friendships where accepted_at is null and accepted')::int = 0,
        'an accepted friendship has no accepted_at');
    -- Seen is the server's clock and the lifter's own.
    perform must_run(dara, 'select mark_inbox_seen()', 'mark_inbox_seen() is refused');
    perform assert_true((select seen_at from inbox_seen where user_id = dara) > now() - interval '1 minute',
        'mark_inbox_seen() did not record the server time');
    perform assert_true(refused_as(dara, format('insert into inbox_seen (user_id, seen_at) values (%L, ''2999-01-01'')', eli), array['42501'])
            and refused_as(dara, format('update inbox_seen set seen_at = ''2999-01-01'' where user_id = %L', dara), array['42501']),
        'inbox_seen is directly writable: the unread count can be forged');
    perform assert_true(value_as(eli, format('select count(*) from inbox_seen where user_id = %L', dara))::int = 0,
        'another lifter can read when someone last opened their inbox');

    -- ------------------------------------------------ feed
    select string_agg(c, ', ') into missing
    from unnest(array['session_id', 'user_id', 'display_name', 'current_title_id', 'level', 'label', 'title',
                      'note', 'completed_at', 'started_at', 'xp_awarded', 'strength_score', 'sets_done',
                      'reps_done', 'held_seconds', 'like_count', 'liked_by_me', 'top_movements', 'best_set',
                      'distance_m', 'hardest_grade', 'movement_count', 'duration_sec',
                      'comment_count', 'reactions', 'my_reaction']) c
    where not exists (select 1 from information_schema.columns ic
                      where ic.table_schema = 'public' and ic.table_name = 'public_feed' and ic.column_name = c);
    perform assert_true(missing is null, format('public_feed lost column(s) the app reads: %s', missing));
    -- As Eli on Dara's workout: Gus's iron and Eli's own flame count, muted
    -- Hale's flame and comment do not.
    r := value_as(eli, format(
        'select concat_ws(''|'', like_count, liked_by_me, my_reaction, reactions::text, comment_count) '
        'from public_feed where session_id = %L', w_pub));
    perform assert_true(r = '2|t|flame|{"iron": 1, "flame": 1}|2',
        format('public_feed counts, reactions or my_reaction are wrong for the reader: %s', r));
    perform assert_true(value_as(eli, format('select reactions::text from public_feed where session_id = %L', w_fr)) = '{}',
        'public_feed.reactions is not {} for a workout with no reactions');

    -- ------------------------------------------------ reports
    perform must_run(fenn, format(
        'insert into reports (reporter_id, target_user_id, comment_id, reason, note) values (%L, %L, %L, ''abuse'', ''rude'')',
        fenn, hale, c_hale_pub), 'a lifter cannot report a comment');
    perform assert_true(
        (select excerpt = 'hale on pub' and status = 'open' from reports where reporter_id = fenn and comment_id = c_hale_pub),
        'a report does not keep the comment body as evidence');
    perform must_run(fenn, format(
        'insert into reports (reporter_id, target_user_id, session_id, reason) values (%L, %L, %L, ''cheating'')',
        fenn, dara, w_pub), 'a lifter cannot report a workout');
    perform assert_true(
        (select excerpt from reports where reporter_id = fenn and session_id = w_pub) = 'Open day · felt strong',
        'a report does not keep the workout title and note as evidence');
    perform assert_true(
        refused_as(fenn, 'select count(*) from reports', array['42501'])
            and refused_as(fenn, 'update reports set status = ''dismissed''', array['42501'])
            and refused_as(fenn, 'delete from reports', array['42501']),
        'a client can read, edit or delete reports');
    perform assert_true(
        refused_as(fenn, format('insert into reports (reporter_id, target_user_id, reason) values (%L, %L, ''spam'')', fenn, fenn), array['23514']),
        'a lifter can report themselves');
    perform assert_true(
        refused_as(fenn, format('insert into reports (reporter_id, target_user_id, reason) values (%L, %L, ''spam'')', eli, hale), array['42501']),
        'a report can be filed under another lifter''s id');
    perform assert_true(
        refused_as(fenn, format('insert into reports (reporter_id, target_user_id, reason, status) values (%L, %L, ''spam'', ''dismissed'')', fenn, hale), array['42501']),
        'a reporter can set the status of their own report');
    perform assert_true(
        refused_as(fenn, format('insert into reports (reporter_id, target_user_id, reason, note) values (%L, %L, ''spam'', repeat(''n'', 281))', fenn, hale), array['23514'])
            and refused_as(fenn, format('insert into reports (reporter_id, target_user_id, reason) values (%L, %L, ''bored'')', fenn, hale), array['23514']),
        'reports accepts an unbounded note or an unknown reason');
    insert into reports (reporter_id, target_user_id, reason)
    select ivo, fenn, 'spam' from generate_series(1, 19);
    perform must_run(ivo, format('insert into reports (reporter_id, target_user_id, reason) values (%L, %L, ''spam'')', ivo, fenn),
        'the 20th report of the day is refused');
    r := refusal(ivo, format('insert into reports (reporter_id, target_user_id, reason) values (%L, %L, ''spam'')', ivo, fenn));
    perform assert_true(r = 'P0001: Too many reports today — try again tomorrow.',
        format('the 21st report of the day is not refused (%s)', r));

    -- ------------------------------------------------ comment rate limits
    for i in 1..10 loop
        perform must_run(ivo, format('insert into session_comments (session_id, user_id, body) values (%L, %L, ''c%s'')', w_ivo, ivo, i),
            format('comment %s of the minute is refused', i));
    end loop;
    r := refusal(ivo, format('insert into session_comments (session_id, user_id, body) values (%L, %L, ''eleven'')', w_ivo, ivo));
    perform assert_true(r = 'P0001: Too many comments — wait a minute.',
        format('the 11th comment in a minute is not refused (%s)', r));
    -- Move those out of the minute and fill the day to 199. Replica mode
    -- skips the trigger, which would otherwise stamp now() on every row.
    update session_comments set created_at = now() - interval '2 hour' where user_id = ivo;
    perform set_config('session_replication_role', 'replica', true);
    insert into session_comments (session_id, user_id, author_name, body, created_at)
    select w_ivo, ivo, 'Ivo', 'fill ' || g, now() - interval '2 hour' from generate_series(1, 189) g;
    perform set_config('session_replication_role', 'origin', true);
    perform must_run(ivo, format('insert into session_comments (session_id, user_id, body) values (%L, %L, ''two hundred'')', w_ivo, ivo),
        'the 200th comment of the day is refused');
    r := refusal(ivo, format('insert into session_comments (session_id, user_id, body) values (%L, %L, ''too many'')', w_ivo, ivo));
    perform assert_true(r = 'P0001: Daily comment limit reached — try again tomorrow.',
        format('the 201st comment of the day is not refused (%s)', r));
    -- Ivo's workout now holds 200: the thread is full for everyone.
    r := refusal(hale, format('insert into session_comments (session_id, user_id, body) values (%L, %L, ''late'')', w_ivo, hale));
    perform assert_true(r = 'P0001: This workout has reached its comment limit.',
        format('the 201st comment on one workout is not refused (%s)', r));

    -- ------------------------------------------------ comment delete
    -- Fenn can read Hale's comment on Dara's workout but owns neither.
    perform must_run(fenn, format('delete from session_comments where id = %L', c_hale_pub), 'a no-op delete errored');
    perform assert_true(exists (select 1 from session_comments where id = c_hale_pub),
        'a lifter who neither wrote a comment nor owns the workout deleted it');
    perform must_run(dara, format('delete from session_comments where id = %L', c_hale_pub), 'the owner''s delete errored');
    perform assert_true(not exists (select 1 from session_comments where id = c_hale_pub),
        'the workout owner cannot delete a comment on their own workout');
    perform assert_true((select excerpt from reports where reporter_id = fenn and target_user_id = hale and reason = 'abuse') = 'hale on pub',
        'deleting a reported comment deleted the evidence');
    perform assert_true(
        refused_as(hale, format('update session_comments set body = ''edited'' where id = %L', c_hale_eli), array['42501']),
        'a comment can be edited after it was posted');

    perform set_config('probe.uid', '', true);
end $$;

-- ------------------------------------------------------------------ sign-up
-- The profile is made by the trigger on auth.users, never by the client: with
-- email confirmation ON there is no session at sign-up, so a client insert
-- would run as anon.
do $$
declare
    -- Sign-up identities, all removed at the end of this block.
    u1 uuid := 'a5500000-0000-4000-8000-000000000051';
    u2 uuid := 'a5500000-0000-4000-8000-000000000052';
    u3 uuid := 'a5500000-0000-4000-8000-000000000053';
    u4 uuid := 'a5500000-0000-4000-8000-000000000054';
    u5 uuid := 'a5500000-0000-4000-8000-000000000055';
    u6 uuid := 'a5500000-0000-4000-8000-000000000056';
    u7 uuid := 'a5500000-0000-4000-8000-000000000057';
    u8 uuid := 'a5500000-0000-4000-8000-000000000058';
    u9 uuid := 'a5500000-0000-4000-8000-000000000059';
    ux uuid := 'a5500000-0000-4000-8000-00000000005a';
    neutral constant text := '^Lifter[0-9]{4}$';
    r text;
    ok boolean;
    n int;
    g int;
begin
    -- Idempotent re-runs: earlier runs' identities and any neutral handles they
    -- left behind must not collide with what is checked here.
    delete from auth.users where id::text like 'a5500000-0000-4000-8000-00000000005_';
    delete from auth.users where id::text like 'a5500000-0000-4000-9000-%';
    delete from profiles where display_name ~ neutral;

    -- ------------------------------------------------ the chosen name
    r := sign_up(u1, '{"display_name": "  Kestrel  "}');
    perform assert_true(r = 'Kestrel',
        format('sign-up did not create the profile with the trimmed display_name metadata (got %s)', r));
    perform assert_true((select count(*) from profiles where id = u1) = 1, 'sign-up made more than one profile');

    -- Exactly at the bounds, both ends.
    r := sign_up(u2, '{"display_name": "Ab"}');
    perform assert_true(r = 'Ab', format('a 2-character display_name was not used (got %s)', r));
    delete from auth.users where id = u2;
    r := sign_up(u2, jsonb_build_object('display_name', repeat('y', 24)));
    perform assert_true(r = repeat('y', 24), format('a 24-character display_name was not used (got %s)', r));
    delete from auth.users where id = u2;

    -- Google-style metadata alongside a chosen name: the chosen name wins.
    r := sign_up(u2, '{"display_name": "Corvid", "full_name": "Real Legal Name", "name": "Real Legal"}');
    perform assert_true(r = 'Corvid', format('display_name was ignored when full_name was present (got %s)', r));

    -- ------------------------------------------------ taken or out of bounds
    -- Taken in any case: the sign-up must SUCCEED with a neutral handle.
    r := sign_up(u3, '{"display_name": "KESTREL"}');
    perform assert_true(r ~ neutral, format('a taken name (other case) did not fall back to a neutral handle (got %s)', r));
    r := sign_up(u4, '{"display_name": "kestrel"}');
    perform assert_true(r ~ neutral, format('a taken name (lower case) did not fall back to a neutral handle (got %s)', r));
    r := sign_up(u5, '{"display_name": " Ayla "}');
    perform assert_true(r ~ neutral, format('a name taken by an existing lifter did not fall back (got %s)', r));
    perform assert_true((select display_name from profiles where id = u1) = 'Kestrel',
        'a taken name took the name from the lifter who owned it');
    delete from auth.users where id in (u3, u4, u5);

    foreach r in array array['A', repeat('x', 25), '   ', ''] loop
        ok := sign_up(u6, jsonb_build_object('display_name', r)) ~ neutral;
        delete from auth.users where id = u6;
        perform assert_true(ok, format('an out-of-bounds display_name (%s characters) did not fall back to a neutral handle', char_length(r)));
    end loop;

    -- ------------------------------------------------ never a legal name
    -- Google supplies full_name / name (and often email). None of it may become
    -- a public handle.
    r := sign_up(u6, '{"full_name": "Jane Q Public", "name": "Jane Public", "email": "jane@m.test", "avatar_url": "x"}');
    perform assert_true(r ~ neutral, format('Google metadata (full_name/name only) did not yield a neutral handle (got %s)', r));
    delete from auth.users where id = u6;
    r := sign_up(u6, null);
    perform assert_true(r ~ neutral, format('sign-up without any metadata did not yield a neutral handle (got %s)', r));
    delete from auth.users where id = u6;
    r := sign_up(u6, '{}');
    perform assert_true(r ~ neutral, format('sign-up with empty metadata did not yield a neutral handle (got %s)', r));
    delete from auth.users where id = u6;

    -- ------------------------------------------------ neutral handle collisions
    -- All but one of the 10000 handles are taken (rows written directly: the
    -- replica role skips the trigger, and is switched back before anything is
    -- deleted, or the cascades would be skipped too). The probe must walk past
    -- every collision to the single free one, wherever it starts.
    delete from auth.users where id in (u1, u2);
    perform set_config('session_replication_role', 'replica', true);
    insert into auth.users (instance_id, id, aud, role, email, encrypted_password, created_at, updated_at)
    select '00000000-0000-0000-0000-000000000000', ('a5500000-0000-4000-9000-' || lpad(to_hex(x), 12, '0'))::uuid,
           'authenticated', 'authenticated', 'fill' || x || '@m.test', '', now(), now()
    from generate_series(0, 9998) x;
    insert into profiles (id, display_name)
    select ('a5500000-0000-4000-9000-' || lpad(to_hex(x), 12, '0'))::uuid, 'Lifter' || lpad(x::text, 4, '0')
    from generate_series(0, 9998) x;
    perform set_config('session_replication_role', 'origin', true);
    for g in 1..3 loop
        r := sign_up(u7, '{}');
        perform assert_true(r = 'Lifter9999', format('the neutral handle probe did not find the one free handle (got %s)', r));
        delete from auth.users where id = u7;
    end loop;
    -- Every neutral handle taken: sign-up itself must still succeed.
    perform set_config('session_replication_role', 'replica', true);
    insert into auth.users (instance_id, id, aud, role, email, encrypted_password, created_at, updated_at)
    values ('00000000-0000-0000-0000-000000000000', u8, 'authenticated', 'authenticated', 'last@m.test', '', now(), now());
    insert into profiles (id, display_name) values (u8, 'Lifter9999');
    perform set_config('session_replication_role', 'origin', true);
    r := sign_up(u7, '{}');
    perform assert_true(r is null and exists (select 1 from auth.users where id = u7),
        'sign-up failed, or invented a handle, when every neutral handle was taken');
    delete from auth.users where id in (u7, u8);
    delete from auth.users where id::text like 'a5500000-0000-4000-9000-%';
    perform assert_true(not exists (select 1 from profiles where display_name ~ neutral),
        'the neutral-handle fixtures were not cleaned up');

    -- ------------------------------------------------ display_name_available
    perform assert_true(
        has_function_privilege('anon', 'public.display_name_available(text)', 'execute')
            and has_function_privilege('authenticated', 'public.display_name_available(text)', 'execute')
            and not has_function_privilege('public', 'public.display_name_available(text)', 'execute'),
        'display_name_available() is not callable by anon and authenticated only: the app asks before sign-up');
    r := sign_up(u1, '{"display_name": "Kestrel"}');
    set local role anon;
    perform assert_true(display_name_available('Nobody Yet') is true, 'a free name reads as taken');
    perform assert_true(display_name_available('Kestrel') is false, 'a taken name reads as free');
    perform assert_true(display_name_available('kESTREL') is false, 'a taken name (other case) reads as free');
    perform assert_true(display_name_available('  Kestrel  ') is false, 'a taken name (padded) reads as free');
    perform assert_true(display_name_available('Ab') is true and display_name_available(repeat('z', 24)) is true,
        'a name exactly at the bounds reads as unavailable');
    perform assert_true(
        display_name_available('A') is false
            and display_name_available(repeat('z', 25)) is false
            and display_name_available('   ') is false
            and display_name_available('') is false
            and display_name_available(null) is false,
        'an out-of-bounds name reads as available');
    reset role;
    perform set_config('probe.uid', u1::text, true);
    set local role authenticated;
    perform assert_true(display_name_available('Nobody Yet') is true and display_name_available('Kestrel') is false,
        'display_name_available() answers wrongly for a signed-in lifter');
    reset role;
    perform set_config('probe.uid', '', true);

    -- ------------------------------------------------ clients never insert profiles
    delete from profiles where id = u1;   -- a lifter with no profile: the case a client insert used to serve
    perform assert_true(
        not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'profiles' and cmd = 'INSERT'),
        'profiles still has an insert policy');
    perform assert_true(
        not has_table_privilege('anon', 'public.profiles', 'insert')
            and not has_table_privilege('authenticated', 'public.profiles', 'insert')
            and not has_column_privilege('authenticated', 'public.profiles', 'display_name', 'insert')
            and not has_column_privilege('anon', 'public.profiles', 'display_name', 'insert'),
        'a client role still holds INSERT on profiles');
    perform assert_true(
        refused_as(u1, format('insert into profiles (id, display_name) values (%L, ''Sneaky'')', u1), array['42501']),
        'an authenticated client can insert its own profile');
    perform assert_true(
        refused_as_anon(format('insert into profiles (id, display_name) values (%L, ''Sneaky'')', u1), array['42501']),
        'the anon key can insert a profile: sign-up with email confirmation on would need this, and so could anyone');
    delete from auth.users where id = u1;

    -- ------------------------------------------------ the trigger function
    perform assert_true(
        not has_function_privilege('public', 'public.handle_new_user()', 'execute')
            and not has_function_privilege('anon', 'public.handle_new_user()', 'execute')
            and not has_function_privilege('authenticated', 'public.handle_new_user()', 'execute'),
        'handle_new_user() is executable by a client role');
    perform assert_true(
        refused_as(u9, 'select public.handle_new_user()', array['42501']),
        'a signed-in lifter can call handle_new_user()');
    perform assert_true(
        (select prosecdef from pg_proc where oid = 'public.handle_new_user()'::regprocedure)
            and exists (select 1 from pg_proc p, unnest(p.proconfig) c
                        where p.oid = 'public.handle_new_user()'::regprocedure and c like 'search_path=%'),
        'handle_new_user() is not SECURITY DEFINER with a pinned search_path');

    -- Nothing this block made survives it.
    perform assert_true(not exists (select 1 from auth.users where id::text like 'a5500000-0000-4000-8000-00000000005_'),
        'the sign-up fixtures were not cleaned up');
end $$;

-- ------------------------------------------------------------ strength boards
-- Lifters of their own again, so no earlier block, mute or friendship can mask a
-- lift_marks leak. Kit is the reader; Lux is an accepted ally; Moe only asked;
-- Nia is an ally Kit later blocked; Oz is an ally who blocked Kit; Pax is a
-- PUBLIC stranger (a public profile must still not open the strength board).
delete from auth.users where id::text like 'a5500000-0000-4000-8000-00000000006_';
insert into auth.users (instance_id, id, aud, role, email, encrypted_password, created_at, updated_at)
select '00000000-0000-0000-0000-000000000000', ('a5500000-0000-4000-8000-0000000000' || n)::uuid,
       'authenticated', 'authenticated', 'lift' || n || '@m.test', '', now(), now()
from unnest(array['61','62','63','64','65','66']) n;
insert into profiles (id, display_name, visibility) values
    ('a5500000-0000-4000-8000-000000000061','Kit','public'),
    ('a5500000-0000-4000-8000-000000000062','Lux','public'),
    ('a5500000-0000-4000-8000-000000000063','Moe','public'),
    ('a5500000-0000-4000-8000-000000000064','Nia','public'),
    ('a5500000-0000-4000-8000-000000000065','Oz','public'),
    ('a5500000-0000-4000-8000-000000000066','Pax','public')
on conflict (id) do update set display_name = excluded.display_name, visibility = excluded.visibility;
-- A block deletes the friendship and friendships_before_insert refuses an
-- allied pair across a block, so the "accepted ally, then blocked" rows are
-- written with triggers off (psql autocommits, so a transaction-local setting
-- would already be gone by the insert), the way a block landing after an accept leaves them.
insert into blocks (blocker_id, blocked_id) values
    ('a5500000-0000-4000-8000-000000000061','a5500000-0000-4000-8000-000000000064'),
    ('a5500000-0000-4000-8000-000000000065','a5500000-0000-4000-8000-000000000061');
set session_replication_role = replica;
insert into friendships (requester_id, addressee_id, accepted) values
    ('a5500000-0000-4000-8000-000000000061','a5500000-0000-4000-8000-000000000062', true),
    ('a5500000-0000-4000-8000-000000000063','a5500000-0000-4000-8000-000000000061', false),
    ('a5500000-0000-4000-8000-000000000061','a5500000-0000-4000-8000-000000000064', true),
    ('a5500000-0000-4000-8000-000000000065','a5500000-0000-4000-8000-000000000061', true);
set session_replication_role = origin;
insert into sessions (id, user_id, local_id, label, started_at, completed_at)
values ('a5566666-0000-4000-8000-000000000001','a5500000-0000-4000-8000-000000000061',1,'Pull',
        now() - interval '2 hour', now() - interval '1 hour');

do $$
declare
    kit uuid := 'a5500000-0000-4000-8000-000000000061';
    lux uuid := 'a5500000-0000-4000-8000-000000000062';
    moe uuid := 'a5500000-0000-4000-8000-000000000063';
    nia uuid := 'a5500000-0000-4000-8000-000000000064';
    oz  uuid := 'a5500000-0000-4000-8000-000000000065';
    pax uuid := 'a5500000-0000-4000-8000-000000000066';
    n int;
    r text;
    t timestamptz;
begin
    -- 20: the beacon of this schema level, and the new column.
    perform assert_true(
        exists (select 1 from information_schema.columns
                where table_schema = 'public' and table_name = 'session_sets' and column_name = 'exercise_position'
                  and is_nullable = 'YES'),
        'session_sets.exercise_position is missing or NOT NULL: the ally workout view cannot order exercises, or 1.4 clients cannot insert sets');
    perform assert_true(
        to_regclass('public.lift_marks') is not null and to_regclass('public.lift_board') is not null
            and to_regproc('public.is_ally') is not null,
        'lift_marks, lift_board or is_ally() is missing: the baseline did not apply completely');
    perform assert_true(
        (select rowsecurity from pg_tables where schemaname = 'public' and tablename = 'lift_marks'),
        'lift_marks has RLS disabled: every lifter''s tiers are readable by anyone');

    -- A 1.4-shaped set insert (no exercise_position) must still work, and the
    -- new column is bounded like the other client-sent numbers.
    perform must_run(kit,
        'insert into session_sets (session_id, exercise_name, set_index, reps, done) values (''a5566666-0000-4000-8000-000000000001'', ''Pull-up'', 0, 5, true)',
        'a 1.4 client set insert without exercise_position is refused: every old app would fail to sync');
    perform must_run(kit,
        'insert into session_sets (session_id, exercise_name, set_index, reps, done, exercise_position) values (''a5566666-0000-4000-8000-000000000001'', ''Dip'', 0, 5, true, 1)',
        'a set insert carrying exercise_position is refused');
    perform assert_true(
        refused_as(kit,
            'insert into session_sets (session_id, exercise_name, set_index, reps, exercise_position) values (''a5566666-0000-4000-8000-000000000001'', ''Dip'', 1, 5, -1)',
            array['23514']),
        'session_sets.exercise_position accepts a negative position');

    -- The lifters write their own marks (this is also the write path the app uses).
    perform must_run(kit, format(
        'insert into lift_marks (user_id, lift, step, recent_step, recent_at) values (%L, ''pull_up'', 6, 6, now())', kit),
        'a lifter cannot write their own lift mark');
    perform must_run(lux, format(
        'insert into lift_marks (user_id, lift, step, recent_step, recent_at) values (%L, ''pull_up'', 7, 7, now() - interval ''1 day''), (%L, ''bench'', 5, 5, now() - interval ''8 days''), (%L, ''squat'', 3, null, null)',
        lux, lux, lux),
        'an ally cannot write their own lift marks');
    insert into lift_marks (user_id, lift, step) values (moe, 'dip', 4), (nia, 'dip', 4), (oz, 'dip', 4), (pax, 'dip', 4);

    -- Reads. Each zero is paired with the ally's positive read below, so a
    -- table that simply reads empty for everyone cannot pass.
    perform set_config('probe.uid', kit::text, true);
    set local role authenticated;
    select count(*) into n from lift_marks where user_id = lux;
    perform assert_true(n = 3, format('an accepted ally''s lift marks are hidden from their ally (saw %s of 3): the board would always read empty', n));
    select count(*) into n from lift_marks where user_id = kit;
    perform assert_true(n = 1, 'a lifter cannot read their own lift marks');
    select count(*) into n from lift_marks where user_id = pax;
    perform assert_true(n = 0, 'a stranger with a PUBLIC profile can read my lift marks: the strength board is not allies-only');
    select count(*) into n from lift_marks where user_id = moe;
    perform assert_true(n = 0, 'a PENDING ally request already opens the requester''s lift marks');
    select count(*) into n from lift_marks where user_id = nia;
    perform assert_true(n = 0, 'a lifter I blocked can still read and be read through lift_marks (block ignored)');
    select count(*) into n from lift_marks where user_id = oz;
    perform assert_true(n = 0, 'a lifter who blocked me still shows on my strength board (block ignored the other way)');
    select count(*) into n from lift_board where user_id in (lux, kit);
    perform assert_true(n = 4, format('lift_board misses the reader or their ally (saw %s of 4)', n));
    select count(*) into n from lift_board where user_id in (pax, moe, nia, oz);
    perform assert_true(n = 0, 'lift_board shows a stranger, a pending requester or a blocked lifter: the view is not security_invoker over lift_marks');
    reset role;

    -- The other side of each block and the stranger's view of me.
    perform set_config('probe.uid', nia::text, true);
    set local role authenticated;
    select count(*) into n from lift_marks where user_id = kit;
    perform assert_true(n = 0, 'a lifter blocked by me can still read MY lift marks (block ignored)');
    reset role;
    perform set_config('probe.uid', pax::text, true);
    set local role authenticated;
    select count(*) into n from lift_marks where user_id in (kit, lux);
    perform assert_true(n = 0, 'a public stranger reads an ally group''s lift marks');
    reset role;
    perform set_config('probe.uid', lux::text, true);
    set local role authenticated;
    select count(*) into n from lift_marks where user_id = kit;
    perform assert_true(n = 1, 'an accepted ally cannot read the requester''s marks: is_ally() must hold in both directions of a friendship');
    reset role;

    -- recent window: Lux's bench set is 8 days old, pull-up 1 day.
    perform set_config('probe.uid', kit::text, true);
    set local role authenticated;
    select recent_step::text into r from lift_board where user_id = lux and lift = 'bench';
    perform assert_true(r is null, format('lift_board.recent_step still reads %s for a set 8 days old: the 7-day window is not applied', r));
    select recent_step::text into r from lift_board where user_id = lux and lift = 'pull_up';
    perform assert_true(r = '7', format('lift_board.recent_step hides a set from yesterday (read %s)', coalesce(r, 'null')));
    select step::text into r from lift_board where user_id = lux and lift = 'bench';
    perform assert_true(r = '5', 'lift_board dropped the row when its recent set aged out: the overall step must stay');
    reset role;

    -- Writes across lifters and out of range.
    perform assert_true(
        refused_as(kit, format('insert into lift_marks (user_id, lift, step) values (%L, ''ohp'', 3)', lux), array['42501']),
        'a lifter can write a mark under another lifter''s id');
    perform assert_true(
        refused_as(kit, format('update lift_marks set user_id = %L where user_id = %L and lift = ''pull_up''', lux, kit), array['42501']),
        'a lifter can re-home their mark onto another lifter''s id (update with-check missing)');
    perform must_run(kit, format('update lift_marks set step = 10 where user_id = %L', lux), 'update of another lifter''s mark errored instead of matching nothing');
    perform must_run(kit, format('delete from lift_marks where user_id = %L', lux), 'delete of another lifter''s marks errored instead of matching nothing');
    perform assert_true(
        (select count(*) from lift_marks where user_id = lux) = 3 and (select max(step) from lift_marks where user_id = lux) = 7,
        'a lifter can update or delete another lifter''s lift marks');
    perform assert_true(
        refused_as(kit, format('insert into lift_marks (user_id, lift, step) values (%L, ''curl'', 3)', kit), array['23514']),
        'lift_marks accepts an unknown lift');
    perform must_run(kit, format(
        'insert into lift_marks (user_id, lift, step, recent_step, recent_at) values '
        '(%L, ''one_arm_pull'', 3, 3, now()), (%L, ''muscle_up'', 1, null, null), (%L, ''push_up'', 6, 6, now()), '
        '(%L, ''hspu'', 2, null, null), (%L, ''front_lever'', 5, 5, now()), (%L, ''back_lever'', 4, null, null), '
        '(%L, ''planche'', 6, null, null), (%L, ''handstand'', 4, null, null), (%L, ''l_sit'', 2, null, null), '
        '(%L, ''human_flag'', 3, null, null), (%L, ''pistol'', 5, 5, now())',
        kit, kit, kit, kit, kit, kit, kit, kit, kit, kit, kit),
        'a ladder board wire is refused by the lift check: the schema-21 boards cannot sync');
    perform assert_true(
        (select count(*) from lift_marks where user_id = kit and lift in ('one_arm_pull','muscle_up','push_up','hspu','front_lever','back_lever','planche','handstand','l_sit','human_flag','pistol')) = 11,
        'not every ladder board wire landed');
    perform assert_true(
        refused_as(kit, format('insert into lift_marks (user_id, lift, step) values (%L, ''front_lever_2'', 3)', kit), array['23514']),
        'lift_marks accepts a near-miss ladder wire');
    delete from lift_marks where user_id = kit and lift <> 'pull_up';
    perform assert_true(
        refused_as(kit, format('insert into lift_marks (user_id, lift, step) values (%L, ''ohp'', 11)', kit), array['23514']),
        'lift_marks accepts a step above 10: one PATCH tops the board');
    perform assert_true(
        refused_as(kit, format('insert into lift_marks (user_id, lift, step) values (%L, ''ohp'', -1)', kit), array['23514']),
        'lift_marks accepts a negative step');
    perform assert_true(
        refused_as(kit, format('insert into lift_marks (user_id, lift, step, recent_step) values (%L, ''ohp'', 3, 11)', kit), array['23514']),
        'lift_marks accepts a recent_step above 10');
    perform assert_true(
        refused_as(kit, format('insert into lift_marks (user_id, lift, step, updated_at) values (%L, ''ohp'', 3, now() + interval ''1 year'')', kit), array['42501']),
        'a lifter can set lift_marks.updated_at from the phone');

    -- updated_at is the server's: age the row, let the lifter update it, and it
    -- must have moved to now().
    update lift_marks set updated_at = now() - interval '30 days' where user_id = kit and lift = 'pull_up';
    perform must_run(kit, format('update lift_marks set step = 7 where user_id = %L and lift = ''pull_up''', kit),
        'a lifter cannot update their own mark');
    select updated_at into t from lift_marks where user_id = kit and lift = 'pull_up';
    perform assert_true(t > now() - interval '1 minute', 'lift_marks.updated_at is not server-set on update: the touch trigger is missing');

    -- is_ally: answers only about the caller, and never to the shipped key.
    perform assert_true(
        not has_function_privilege('anon', 'public.is_ally(uuid)', 'execute')
            and not has_function_privilege('public', 'public.is_ally(uuid)', 'execute'),
        'anon can execute is_ally(): the shipped key can probe the ally graph');
    perform assert_true(
        refused_as_anon(format('select public.is_ally(%L)', lux), array['42501']),
        'the anon key can call is_ally()');
    perform assert_true(
        not has_table_privilege('anon', 'public.lift_marks', 'select')
            and not has_table_privilege('anon', 'public.lift_marks', 'insert')
            and not has_column_privilege('anon', 'public.lift_marks', 'step', 'insert'),
        'anon holds a privilege on lift_marks');
    perform assert_true(
        (select prosecdef from pg_proc where oid = 'public.is_ally(uuid)'::regprocedure)
            and exists (select 1 from pg_proc p, unnest(p.proconfig) c
                        where p.oid = 'public.is_ally(uuid)'::regprocedure and c like 'search_path=%'),
        'is_ally() is not SECURITY DEFINER with a pinned search_path');
    perform set_config('probe.uid', kit::text, true);
    set local role authenticated;
    perform assert_true(public.is_ally(lux) and not public.is_ally(moe) and not public.is_ally(nia)
                            and not public.is_ally(oz) and not public.is_ally(pax),
        'is_ally() is wrong for an ally, a pending request, a blocked lifter or a stranger');
    reset role;

    -- Erased with the account, and only that account.
    perform set_config('probe.uid', lux::text, true);
    set local role authenticated;
    perform delete_my_account();
    reset role;
    perform assert_true(
        not exists (select 1 from lift_marks where user_id = lux),
        'delete_my_account() left lift_marks behind: the erase promise is broken');
    perform assert_true(
        exists (select 1 from lift_marks where user_id = kit),
        'delete_my_account() erased another lifter''s lift marks');
    perform set_config('probe.uid', '', true);

    delete from auth.users where id::text like 'a5500000-0000-4000-8000-00000000006_';
end $$;

-- -------------------------------------------------------------- the guarantees
do $$
declare
    ayla uuid := 'a5500000-0000-4000-8000-000000000001';
    borin uuid := 'a5500000-0000-4000-8000-000000000002';
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

    -- The boards are VIEWS over profiles, and the suite already asserts they
    -- carry security_invoker = true. That setting is necessary but not
    -- sufficient: a board wrapped in a SECURITY DEFINER function leaks every
    -- row while the setting still reads true (verified — that mutation is
    -- caught only here), and a narrowed can_view() makes the boards read empty
    -- for everyone while every refusal check still passes. So read the boards
    -- as a stranger, as a friend, and as the reader themselves.
    perform set_config('probe.uid', cass::text, true);
    set local role authenticated;

    select count(*) into n from leaderboard where id = ayla;
    perform assert_true(n = 0, 'leaderboard leaks a friends-only hunter to a stranger: the view is not security_invoker');
    select count(*) into n from shadow_board where id = ayla;
    perform assert_true(n = 0, 'shadow_board leaks a friends-only hunter to a stranger');

    -- ...and the same reader must still see their own row, or the boards are
    -- simply broken rather than private.
    select count(*) into n from leaderboard where id = cass;
    perform assert_true(n = 1, 'leaderboard hides the reader from themselves');
    select count(*) into n from shadow_board where id = cass;
    perform assert_true(n = 1, 'shadow_board hides the reader from themselves');
    reset role;

    -- A friend MUST be visible, otherwise the zero above would pass for the
    -- wrong reason (a board that shows nobody anything).
    perform set_config('probe.uid', borin::text, true);
    set local role authenticated;
    select count(*) into n from leaderboard where id = ayla;
    perform assert_true(n = 1, 'leaderboard hides an accepted friend: the board would always read empty');
    select count(*) into n from shadow_board where id = ayla;
    perform assert_true(n = 1, 'shadow_board hides an accepted friend');
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

    -- Without rows to erase, the cascade check below passes for any schema.
    perform assert_true(
        (select count(*) from blocks where blocker_id = ayla) > 0
            and (select count(*) from mutes where muter_id = ayla) > 0
            and (select count(*) from mutes where muted_id = ayla) > 0
            and (select count(*) from session_comments where user_id = ayla) > 0
            and (select count(*) from session_likes where user_id = ayla) > 0
            and (select count(*) from inbox_seen where user_id = ayla) > 0
            and (select count(*) from reports where reporter_id = ayla) > 0
            and (select count(*) from reports where target_user_id = ayla) > 0
            and (select count(*) from friend_request_log where requester_id = ayla) > 0,
        'the 1.4 cascade fixtures are missing, so the erase check would be vacuous'
    );

    -- The data-safety sheet promises in-app erasure: deleting the caller's
    -- profiles row must take every other row with it. A table added later
    -- without `on delete cascade` would leave a hunter's training behind
    -- while the app reported success.
    set local role authenticated;
    perform set_config('request.jwt.claims', json_build_object('sub', ayla)::text, true);
    delete from profiles where id = ayla;
    reset role;
    perform assert_true(
        (select count(*) from sessions where user_id = ayla) = 0
            and (select count(*) from session_sets s
                 where exists (select 1 from sessions x
                               where x.id = s.session_id and x.user_id = ayla)) = 0
            and (select count(*) from earned_titles where user_id = ayla) = 0
            and (select count(*) from friendships
                 where requester_id = ayla or addressee_id = ayla) = 0
            and (select count(*) from session_likes where user_id = ayla) = 0
            and (select count(*) from level_ups where user_id = ayla) = 0
            -- 0018: every 1.4 table, on both sides of every pair.
            and (select count(*) from blocks where blocker_id = ayla or blocked_id = ayla) = 0
            and (select count(*) from mutes where muter_id = ayla or muted_id = ayla) = 0
            and (select count(*) from session_comments where user_id = ayla) = 0
            and (select count(*) from inbox_seen where user_id = ayla) = 0
            and (select count(*) from reports where reporter_id = ayla or target_user_id = ayla) = 0
            and (select count(*) from friend_request_log where requester_id = ayla) = 0,
        'erasing a profile left rows behind in another table'
    );

    -- 0017: the cloud wipe deletes the whole account. Two hunters, each with
    -- a profile and a cloud archive; one erases themself.
    -- The other must be untouched, and nothing of the first may survive -
    -- least of all the archive, which hangs off auth.users, not profiles.
    insert into auth.users (instance_id, id, aud, role, email, encrypted_password, created_at, updated_at)
    values ('00000000-0000-0000-0000-000000000000','a5500000-0000-4000-8000-000000000017','authenticated','authenticated','gone@m.test','',now(),now()),
           ('00000000-0000-0000-0000-000000000000','a5500000-0000-4000-8000-000000000018','authenticated','authenticated','stays@m.test','',now(),now())
    on conflict (id) do update set email = excluded.email;
    insert into profiles (id, display_name)
    values ('a5500000-0000-4000-8000-000000000017','GoneHunter'),
           ('a5500000-0000-4000-8000-000000000018','StaysHunter')
    on conflict (id) do update set display_name = excluded.display_name;
    insert into cloud_archives (user_id, archive, size_bytes)
    values ('a5500000-0000-4000-8000-000000000017','{}',2),
           ('a5500000-0000-4000-8000-000000000018','{}',2)
    on conflict (user_id) do update set archive = excluded.archive;

    -- anon has no self to delete; the shipped key must not reach it at all.
    perform assert_true(
        not has_function_privilege('anon', 'public.delete_my_account()', 'execute')
            and has_function_privilege('authenticated', 'public.delete_my_account()', 'execute'),
        'delete_my_account() is callable by the wrong roles'
    );

    perform set_config('probe.uid', 'a5500000-0000-4000-8000-000000000017', true);
    set local role authenticated;
    perform delete_my_account();
    reset role;
    perform assert_true(
        not exists (select 1 from auth.users where id = 'a5500000-0000-4000-8000-000000000017')
            and not exists (select 1 from profiles where id = 'a5500000-0000-4000-8000-000000000017')
            and not exists (select 1 from cloud_archives where user_id = 'a5500000-0000-4000-8000-000000000017'),
        'delete_my_account() left the identity, the profile or the cloud archive behind'
    );
    perform assert_true(
        exists (select 1 from auth.users where id = 'a5500000-0000-4000-8000-000000000018')
            and exists (select 1 from profiles where id = 'a5500000-0000-4000-8000-000000000018')
            and exists (select 1 from cloud_archives where user_id = 'a5500000-0000-4000-8000-000000000018'),
        'delete_my_account() deleted another hunter'
    );
    perform set_config('probe.uid', '', true);

    -- 0014: the version beacon is public and tells the truth. The app probes
    -- it as anon (Settings → CLOUD, TEST) before pointing a lifter's training
    -- at a custom backend, so both the number and the grant are load-bearing.
    perform assert_true(
        (select public.schema_version()) = 22,
        format('schema_version() reports %s, not 22 — bump the literal with the schema change', public.schema_version())
    );
    set local role anon;
    perform assert_true(
        (select public.schema_version()) = 22,
        'anon cannot execute schema_version() — the app probe would read 401'
    );
    reset role;

    -- 0015: the shipped publishable key is the `anon` role. Supabase grants it
    -- EXECUTE on every public function directly, so a `revoke ... from public`
    -- alone leaves each of these callable by anyone holding an APK.
    perform assert_true(
        not has_function_privilege('anon', 'public.is_friend(uuid, uuid)', 'execute'),
        'anon can execute is_friend(): the friendship oracle is open to the shipped key'
    );
    perform assert_true(
        not has_function_privilege('anon', 'public.can_view(uuid)', 'execute'),
        'anon can execute can_view(): any visibility setting can be probed without an account'
    );
    perform assert_true(
        not has_function_privilege('anon', 'public.find_hunter(text)', 'execute'),
        'anon can execute find_hunter(): names resolve to ids without an account'
    );
    perform assert_true(
        not has_function_privilege('anon', 'public.push_aggregates(bigint, bigint, int, bigint, int, double precision)', 'execute')
            and not has_function_privilege('anon', 'public.monarch_level(bigint)', 'execute')
            and not has_function_privilege('authenticated', 'public.monarch_level(bigint)', 'execute'),
        '0011 functions are callable beyond their intended callers'
    );
    reset role;

    raise notice 'ALL BACKEND ASSERTIONS PASSED';
end $$;

-- ---------------------------------------------------------------- 1.6 warbands
-- The band path: anon is blind, membership is the only key, the code is the
-- only way in, the band caps at 8, and leaving hands the band over or ends it.
do $wb$
declare
    nova uuid := 'a5500000-0000-4000-8000-000000000051';
    rey  uuid := 'a5500000-0000-4000-8000-000000000052';
    sol  uuid := 'a5500000-0000-4000-8000-000000000053';
    ron  uuid := 'a5500000-0000-4000-8000-000000000054';
    finn uuid := 'a5500000-0000-4000-8000-000000000055';
    ivo  uuid := 'a5500000-0000-4000-8000-000000000046';
    filler uuid;
    band text;
    code text;
    n int;
begin
    -- Fixtures: five bandmates plus the outsider Ivo (already signed up above).
    -- Names deliberately differ from the 1.4 block's roster: the display-name
    -- unique index is global, not per-fixture-family.
    delete from auth.users where id in (
        nova, rey, sol, ron, finn,
        'a5500000-0000-4000-8000-000000000056',
        'a5500000-0000-4000-8000-000000000057',
        'a5500000-0000-4000-8000-000000000058');
    insert into auth.users (instance_id, id, aud, role, email, encrypted_password, created_at, updated_at)
    select '00000000-0000-0000-0000-000000000000', u, 'authenticated', 'authenticated', u || '@m.test', '', now(), now()
    from unnest(array[nova, rey, sol, ron, finn,
        'a5500000-0000-4000-8000-000000000056',
        'a5500000-0000-4000-8000-000000000057',
        'a5500000-0000-4000-8000-000000000058']::uuid[]) u;
    insert into profiles (id, display_name, visibility) values
        (nova, 'Nova', 'public'),
        (rey,  'Rey',  'public'),
        (sol,  'Sol',  'public'),
        (ron,  'Ron',  'public'),
        (finn, 'Finn', 'public'),
        ('a5500000-0000-4000-8000-000000000056', 'Pax',  'public'),
        ('a5500000-0000-4000-8000-000000000057', 'Tess', 'public'),
        ('a5500000-0000-4000-8000-000000000058', 'Wade', 'public')
    on conflict (id) do update set display_name = excluded.display_name, visibility = excluded.visibility;
    delete from sessions where id in (
        'a5566666-0000-4000-8000-000000000001', 'a5566666-0000-4000-8000-000000000002');
    delete from warbands where id in (select warband_id from warband_members where user_id in (nova, rey, sol, ron, finn));

    -- Grants: the four RPCs answer authenticated only; the tables answer no
    -- client write at all (membership moves only through the RPCs).
    perform assert_true(
        not has_function_privilege('anon', 'public.create_warband(text)', 'execute')
            and not has_function_privilege('anon', 'public.join_warband(text)', 'execute')
            and not has_function_privilege('anon', 'public.leave_warband()', 'execute')
            and not has_function_privilege('anon', 'public.my_warband()', 'execute')
            and not has_function_privilege('anon', 'public.warband_member(uuid, uuid)', 'execute')
            and not has_function_privilege('authenticated', 'public.warband_member(uuid, uuid)', 'execute')
            and has_function_privilege('authenticated', 'public.in_my_warband(uuid)', 'execute')
            and has_function_privilege('authenticated', 'public.create_warband(text)', 'execute')
            and has_function_privilege('authenticated', 'public.join_warband(text)', 'execute')
            and has_function_privilege('authenticated', 'public.leave_warband()', 'execute')
            and has_function_privilege('authenticated', 'public.my_warband()', 'execute'),
        'warband functions are callable beyond their intended callers'
    );
    perform assert_true(
        refused_as(rey, format('select public.warband_member(%L, %L)', 'a5500000-0000-4000-8000-000000000051', rey), array['42501']),
        'warband_member() answers a signed-in lifter'
    );
    perform assert_true(
        refused_as_anon('select count(*) from warbands', array['42501'])
            and refused_as_anon('select count(*) from warband_members', array['42501']),
        'the shipped publishable key can read a warband table'
    );

    -- Create: happy path, code in the unambiguous alphabet, roster of one.
    perform must_run(nova, 'select public.create_warband(''North Gate'')', 'create_warband refuses its own owner');
    code := value_as(nova, 'select code from my_warband()');
    perform assert_true(
        code ~ '^[2-9A-HJ-NP-Z]{8}$',
        format('create_warband drew a code outside the alphabet: %s', coalesce(code, '(null)'))
    );
    perform assert_true(
        value_as(nova, 'select jsonb_array_length(members) from my_warband()') = '1',
        'a fresh warband does not list exactly its owner'
    );

    -- One band per lifter.
    perform assert_true(
        refused_as(nova, 'select public.create_warband(''Other'')', array['P0001']),
        'a lifter in a band created a second one'
    );

    -- Join: by code, case-insensitively; unknown codes refuse.
    perform must_run(rey, format('select public.join_warband(%L)', lower(code)), 'join_warband refused a valid code');
    perform must_run(sol, format('select public.join_warband(%L)', code), 'join_warband refused a second joiner');
    perform assert_true(
        value_as(nova, 'select jsonb_array_length(members) from my_warband()') = '3',
        'the roster did not grow to the two joiners'
    );
    perform assert_true(
        refused_as(ron, 'select public.join_warband(''ZZZZZZZZ'')', array['P0001']),
        'an unknown code joined a band'
    );

    -- trained-this-week: the count follows the feed's own visibility — Rey's
    -- public workout counts, Sol's private one never does. Completed NOW, so
    -- the Monday-start anchor cannot drift across a week boundary mid-run.
    insert into sessions (id, user_id, local_id, label, started_at, completed_at, audience) values
        ('a5566666-0000-4000-8000-000000000001', rey, 1, 'Pull', now() - interval '1 hour', now(), 'profile'),
        ('a5566666-0000-4000-8000-000000000002', sol, 1, 'Legs', now() - interval '1 hour', now(), 'private');
    perform assert_true(
        value_as(nova, 'select members->1->>''workouts_this_week'' from my_warband()') = '1'
            and value_as(nova, 'select members->2->>''workouts_this_week'' from my_warband()') = '0',
        'workouts_this_week counted a workout the feed would hide, or hid one it shows'
    );
    perform assert_true(
        value_as(nova, 'select (members->1->>''last_workout_at'') is not null from my_warband()') = 'true',
        'last_workout_at is null behind a visible workout'
    );

    -- The cap: fill to 8 server-side, the ninth joiner refuses. Ivo is still
    -- bandless here, so the refusal can only come from the cap, never from the
    -- one-band rule.
    band := value_as(nova, 'select id::text from my_warband()');
    perform assert_true(
        refused_as(nova, format('insert into warband_members (warband_id, user_id) values (%L, %L)', band, ron), array['42501']),
        'an insert grant lets a client add a bandmate silently'
    );
    insert into warband_members (warband_id, user_id)
    select band::uuid, u from unnest(array[ron, finn,
        'a5500000-0000-4000-8000-000000000056',
        'a5500000-0000-4000-8000-000000000057',
        'a5500000-0000-4000-8000-000000000058']::uuid[]) u;
    perform assert_true(
        refused_as(ivo, format('select public.join_warband(%L)', code), array['P0001']),
        'a ninth lifter joined a full warband'
    );

    -- RLS: a member reads their band and roster; an outsider reads nothing.
    perform must_run(ivo, 'select public.create_warband(''Ivo Cell'')', 'ivo could not create his own band');
    perform assert_true(
        value_as(nova, 'select count(*) from warbands') = '1'
            and value_as(ivo, 'select count(*) from warbands') = '1',
        'a warband is readable from outside its roster'
    );
    perform assert_true(
        value_as(ivo, 'select count(*) from warband_members') = '1',
        'warband_members leaks rows to a non-member'
    );

    -- Leaving: ownership hands to the oldest remaining member...
    perform must_run(nova, 'select public.leave_warband()', 'the owner could not leave');
    perform assert_true(
        value_as(rey, 'select owner_id::text from my_warband()') = rey::text,
        'leaving did not hand ownership to the oldest remaining member'
    );
    -- ...and the last member leaving deletes the band.
    perform must_run(rey, 'select public.leave_warband()', 'rey could not leave');
    perform must_run(sol, 'select public.leave_warband()', 'sol could not leave');
    perform must_run(ron, 'select public.leave_warband()', 'ron could not leave');
    perform must_run(finn, 'select public.leave_warband()', 'finn could not leave');
    foreach filler in array array['a5500000-0000-4000-8000-000000000056',
                                  'a5500000-0000-4000-8000-000000000057',
                                  'a5500000-0000-4000-8000-000000000058']::uuid[] loop
        perform must_run(filler, 'select public.leave_warband()', 'a filler could not leave');
    end loop;
    perform assert_true(
        value_as(ron, 'select count(*) from warbands') = '0',
        'the band outlived its last member'
    );
    -- Ivo's separate band is untouched by all of the above.
    perform must_run(ivo, 'select public.leave_warband()', 'ivo could not leave');
    select count(*) into n from warbands;
    perform assert_true(n = 0, format('%s warband(s) survived the fixture teardown', n));

    delete from sessions where id in (
        'a5566666-0000-4000-8000-000000000001', 'a5566666-0000-4000-8000-000000000002');
    delete from auth.users where id in (
        nova, rey, sol, ron, finn,
        'a5500000-0000-4000-8000-000000000056',
        'a5500000-0000-4000-8000-000000000057',
        'a5500000-0000-4000-8000-000000000058');
end $wb$;

drop function if exists assert_true(boolean, text);
drop function if exists refused_as(uuid, text, text[]);
drop function if exists must_run(uuid, text, text);
drop function if exists refusal(uuid, text);
drop function if exists refused_as_anon(text, text[]);
drop function if exists sign_up(uuid, jsonb);
drop function if exists value_as(uuid, text);
