-- RLS regression probe. Run against a throwaway Postgres after applying
-- supabase/test/supabase_stub.sql and every migration in order.
--
-- It exists because the 0009 hardening revokes EXECUTE on is_friend() from
-- PUBLIC, and the frightening question is not whether that closes the oracle
-- (it does) but whether it breaks the paths that legitimately depend on it:
-- can_view() calls is_friend() from inside a SECURITY DEFINER body, and
-- profiles_read calls can_view() as the querying role. If the revoke were one
-- step too broad, every signed-in hunter would lose their own profile and their
-- friend list — a worse outcome than the leak.
--
-- Expected results, in order:
--   own_profile_rows       1   her own profile is readable (login-critical)
--   friendship_rows        1   her friendship is listable despite the revoke
--   friend_profile_rows    1   can_view still resolves an accepted friend
--   stranger_profile_rows  0   a friends-only stranger stays hidden
--   own_session_rows       1   her own training is readable
--   visible_friendships    0   a stranger sees none of it
--   visible_sessions       0
--
\set ON_ERROR_STOP 0
-- Two hunters, an accepted friendship, and one friends-only profile.
insert into auth.users (instance_id, id, aud, role, email, encrypted_password, created_at, updated_at)
values ('00000000-0000-0000-0000-000000000000','aaaa0000-0000-4000-8000-00000000000a','authenticated','authenticated','a@m.test','',now(),now()),
       ('00000000-0000-0000-0000-000000000000','bbbb0000-0000-4000-8000-00000000000b','authenticated','authenticated','b@m.test','',now(),now()),
       ('00000000-0000-0000-0000-000000000000','cccc0000-0000-4000-8000-00000000000c','authenticated','authenticated','c@m.test','',now(),now())
on conflict (id) do nothing;

insert into profiles (id, display_name, visibility) values
    ('aaaa0000-0000-4000-8000-00000000000a','Ayla','friends'),
    ('bbbb0000-0000-4000-8000-00000000000b','Borin','friends'),
    ('cccc0000-0000-4000-8000-00000000000c','Cass','friends')
on conflict (id) do nothing;

insert into friendships (requester_id, addressee_id, accepted)
values ('aaaa0000-0000-4000-8000-00000000000a','bbbb0000-0000-4000-8000-00000000000b', true)
on conflict do nothing;

insert into sessions (user_id, local_id, label, started_at, completed_at)
values ('aaaa0000-0000-4000-8000-00000000000a', 7, 'Pull', now() - interval '2 hour', now() - interval '1 hour')
on conflict do nothing;

\echo '=== as Ayla (signed in, authenticated role) ==='
set role authenticated;
set probe.uid = 'aaaa0000-0000-4000-8000-00000000000a';

\echo '--- her own profile must be readable (login-critical path)'
select count(*) as own_profile_rows from profiles where id = 'aaaa0000-0000-4000-8000-00000000000a';

\echo '--- her friendship row must be listable (revoke must not break this)'
select count(*) as friendship_rows from friendships;

\echo '--- her friend Borin must be visible through can_view'
select count(*) as friend_profile_rows from profiles where id = 'bbbb0000-0000-4000-8000-00000000000b';

\echo '--- the stranger Cass must NOT be visible'
select count(*) as stranger_profile_rows from profiles where id = 'cccc0000-0000-4000-8000-00000000000c';

\echo '--- her own session must be readable'
select count(*) as own_session_rows from sessions;

reset role;

\echo '=== as Cass (a stranger to both) ==='
set role authenticated;
set probe.uid = 'cccc0000-0000-4000-8000-00000000000c';
\echo '--- must not see Ayla and Borin friendship'
select count(*) as visible_friendships from friendships;
\echo '--- must not see Ayla session'
select count(*) as visible_sessions from sessions;
reset role;
