-- Discovery probe for finding F6. Run after the stub and every migration.
--
-- The bug: two hunters could not become friends. A by-name lookup selected from
-- `profiles`, which `profiles_read using (can_view(id))` filters, and for a
-- stranger can_view is false because they are not friends YET. The app reported
-- "No hunter is named X" about a real person.
--
-- Expected results, in order:
--   stranger_sees_profile   0   the direct select is still correctly blocked
--   found_by_exact_name     1   discovery works anyway, through the RPC
--   found_id_matches        t   and it returns the right hunter
--   found_by_prefix         0   a prefix must NOT match (no roster walking)
--   found_by_wildcard       0   nor an ilike wildcard
--   rpc_returns   TABLE(id uuid, display_name text)   nothing else leaks
--   anon_denied             ERROR: permission denied for function find_hunter
\set ON_ERROR_STOP 0

insert into auth.users (instance_id, id, aud, role, email, encrypted_password, created_at, updated_at)
values ('00000000-0000-0000-0000-000000000000','dddd0000-0000-4000-8000-00000000000d','authenticated','authenticated','d@m.test','',now(),now()),
       ('00000000-0000-0000-0000-000000000000','eeee0000-0000-4000-8000-00000000000e','authenticated','authenticated','e@m.test','',now(),now())
on conflict (id) do nothing;

-- Both on the default visibility, which is the state that broke discovery.
insert into profiles (id, display_name, visibility) values
    ('dddd0000-0000-4000-8000-00000000000d','Dorian','friends'),
    ('eeee0000-0000-4000-8000-00000000000e','Elowen','friends')
on conflict (id) do nothing;

set role authenticated;
set probe.uid = 'dddd0000-0000-4000-8000-00000000000d';

\echo '--- the direct select must still be blocked (visibility intact)'
select count(*) as stranger_sees_profile from profiles
where display_name = 'Elowen';

\echo '--- discovery by exact name must work for a stranger'
select count(*) as found_by_exact_name from find_hunter('Elowen');

\echo '--- and must return the right hunter'
select (select id from find_hunter('  eLoWeN  ')) = 'eeee0000-0000-4000-8000-00000000000e'
    as found_id_matches;

\echo '--- a prefix must not match: this is not a roster walker'
select count(*) as found_by_prefix from find_hunter('Elo');

\echo '--- nor an ilike wildcard'
select count(*) as found_by_wildcard from find_hunter('%');

\echo '--- the signature exposes nothing but id and display_name'
select pg_get_function_result(p.oid) as rpc_returns
from pg_proc p join pg_namespace ns on ns.oid = p.pronamespace
where ns.nspname = 'public' and p.proname = 'find_hunter';

reset role;

\echo '--- the shipped publishable key must not be able to ask'
set role anon;
select count(*) from find_hunter('Elowen');
reset role;
