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

-- ---------------------------------------------------------- schema is present
do $$
begin
    perform assert_true(to_regclass('auth.users') is not null, 'auth.users missing: apply supabase/test/supabase_stub.sql first');
    perform assert_true(to_regclass('public.profiles') is not null, 'profiles missing: the migration chain did not apply');
    perform assert_true(to_regclass('public.session_likes') is not null, 'session_likes missing: the chain stopped before 0004');
    perform assert_true(to_regproc('public.find_hunter') is not null, 'find_hunter missing: the chain stopped before 0010');
    perform assert_true(to_regproc('public.push_aggregates') is not null, 'push_aggregates missing: the chain stopped before 0011');
    perform assert_true(to_regproc('public.monarch_level') is not null, 'monarch_level missing: the chain stopped before 0011');
    perform assert_true(to_regclass('public.session_comments') is not null, 'session_comments missing: the chain stopped before 0018');
    perform assert_true(to_regproc('public.my_inbox') is not null, 'my_inbox missing: the chain stopped before 0018');
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

-- 1.4 "Talk" (0018) runs on lifters of its own, so a block or mute can never
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
    ('a5500000-0000-4000-8000-000000000046','Ivo','public');
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
                          'blocks','mutes','session_comments','inbox_seen','reports','friend_request_log')
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

-- ------------------------------------------------------------ 1.4 talk (0018)
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
        (select public.schema_version()) = 18,
        format('schema_version() reports %s, not 18 — bump the literal with the migration', public.schema_version())
    );
    set local role anon;
    perform assert_true(
        (select public.schema_version()) = 18,
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

drop function if exists assert_true(boolean, text);
drop function if exists refused_as(uuid, text, text[]);
drop function if exists must_run(uuid, text, text);
drop function if exists refusal(uuid, text);
drop function if exists value_as(uuid, text);
