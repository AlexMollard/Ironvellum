-- Aggregate-forgery probe for finding F1. Run after the stub and every migration.
--
-- Expected results, in order:
--   forged_patch            ERROR: permission denied for column total_xp
--   forged_insert           ERROR: permission denied for column total_xp
--   rename_still_allowed    1     the columns a hunter really owns still write
--   xp_after_push           100   3 done sets x15 + 30 done reps + 25, from the SET rows
--   level_after_push        2     100 cumulative XP is level 2
--   titles_after_push       1
--   never_walks_backwards   100   a partial sync cannot lower a real total
--   curve_matches_kotlin    t     the SQL curve agrees at every threshold
\set ON_ERROR_STOP 0

insert into auth.users (instance_id, id, aud, role, email, encrypted_password, created_at, updated_at)
values ('ffff0000-0000-4000-8000-00000000000f','ffff0000-0000-4000-8000-00000000000f','authenticated','authenticated','f@m.test','',now(),now())
on conflict (id) do nothing;
insert into profiles (id, display_name) values ('ffff0000-0000-4000-8000-00000000000f','Fenn')
on conflict (id) do nothing;

set role authenticated;
set probe.uid = 'ffff0000-0000-4000-8000-00000000000f';

\echo '--- the one-PATCH forgery must be refused outright'
update profiles set total_xp = 9223372036854775807, level = 2147483647
where id = 'ffff0000-0000-4000-8000-00000000000f';

\echo '--- and it must not be possible at creation time either'
insert into profiles (id, display_name, total_xp, level)
values ('ffff0000-0000-4000-8000-0000000000ff','Cheat', 9223372036854775807, 2147483647);

\echo '--- while the columns a hunter genuinely owns still write'
update profiles set display_name = 'Fenn the Sure', visibility = 'public'
where id = 'ffff0000-0000-4000-8000-00000000000f';
select count(*) as rename_still_allowed from profiles
where id = 'ffff0000-0000-4000-8000-00000000000f' and display_name = 'Fenn the Sure';

-- The fixture: one completed session with four sets, three of them done and
-- ten reps each. Xp.award() counts only done sets and their reps, so the
-- derivation is 15*3 + 30 + 25 = 100 - which is exactly level 2. The
-- not-done fourth set is there to prove the filter is applied.
insert into sessions (id, user_id, local_id, label, started_at, completed_at, strength_score)
values ('f5555555-0000-4000-8000-000000000001','ffff0000-0000-4000-8000-00000000000f',
        1, 'Pull', now() - interval '2 hour', now() - interval '1 hour', 42)
on conflict (id) do nothing;
insert into session_sets (session_id, exercise_name, set_index, reps, done) values
    ('f5555555-0000-4000-8000-000000000001','Pull-up', 0, 10, true),
    ('f5555555-0000-4000-8000-000000000001','Pull-up', 1, 10, true),
    ('f5555555-0000-4000-8000-000000000001','Pull-up', 2, 10, true),
    ('f5555555-0000-4000-8000-000000000001','Pull-up', 3, 10, false)
on conflict do nothing;
insert into earned_titles (user_id, title_id, unlocked_at) values
    ('ffff0000-0000-4000-8000-00000000000f','first-blood', now())
on conflict do nothing;

\echo '--- the RPC derives the numbers from the rows'
select push_aggregates(5, 1200, 3, 12.5);
select total_xp as xp_after_push, level as level_after_push, titles_count as titles_after_push,
       streak_days, shadow_essence
from profiles where id = 'ffff0000-0000-4000-8000-00000000000f';

\echo '--- a second push with the sets gone must not walk the total backwards'
delete from session_sets where session_id = 'f5555555-0000-4000-8000-000000000001';
select push_aggregates(0, 0, 0, 0);
select total_xp as never_walks_backwards from profiles
where id = 'ffff0000-0000-4000-8000-00000000000f';

reset role;

\echo '--- the SQL curve must agree with Xp.progress() at every threshold'
-- Kotlin: level n requires a cumulative 50*n*(n-1). Check both sides of each
-- boundary for the first 40 levels: one XP below must still be the lower level.
select bool_and(monarch_level(50::bigint * n * (n - 1)) = n
                and monarch_level(50::bigint * n * (n - 1) - 1) = n - 1)
    as curve_matches_kotlin
from generate_series(2, 40) as g(n);
