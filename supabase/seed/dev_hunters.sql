-- Dev-only fake hunters, so the Guild screens (feed, board, allies) can be
-- tested with more than one account. NOT part of the schema migrations: this is
-- disposable data, applied by hand against the dev project and removable with
-- supabase/seed/dev_hunters_down.sql.
--
-- Fixed UUIDs on purpose: re-running this file updates the same rows instead of
-- minting a second set of ghosts. Emails use @monarch.test so real signups can
-- never collide with them.
--
-- Visibility spread is deliberate:
--   public  -> must appear in the global feed and board for anyone
--   friends -> must appear only once a friendship is accepted
--   private -> must never appear anywhere but its own account

\set alex '0ce6bdfe-0aec-47f6-859e-e79f24ead0df'

-- ---------------------------------------------------------------- accounts

insert into auth.users (instance_id, id, aud, role, email, encrypted_password, created_at, updated_at)
values
    ('00000000-0000-0000-0000-000000000000', 'aaaa1111-0000-4000-8000-000000000001', 'authenticated', 'authenticated', 'kaida@monarch.test',  '', now() - interval '90 days', now()),
    ('00000000-0000-0000-0000-000000000000', 'aaaa1111-0000-4000-8000-000000000002', 'authenticated', 'authenticated', 'rurik@monarch.test',  '', now() - interval '60 days', now()),
    ('00000000-0000-0000-0000-000000000000', 'aaaa1111-0000-4000-8000-000000000003', 'authenticated', 'authenticated', 'sera@monarch.test',   '', now() - interval '45 days', now()),
    ('00000000-0000-0000-0000-000000000000', 'aaaa1111-0000-4000-8000-000000000004', 'authenticated', 'authenticated', 'volkov@monarch.test', '', now() - interval '20 days', now()),
    ('00000000-0000-0000-0000-000000000000', 'aaaa1111-0000-4000-8000-000000000005', 'authenticated', 'authenticated', 'noor@monarch.test',   '', now() - interval '10 days', now())
on conflict (id) do nothing;

insert into profiles (id, display_name, visibility, level, total_xp, streak_days, titles_count, lifetime_strength)
values
    ('aaaa1111-0000-4000-8000-000000000001', 'Kaida',      'public',  31, 48200, 14, 41, 18400),
    ('aaaa1111-0000-4000-8000-000000000002', 'Rurik',      'public',  22, 24100,  5, 28,  9600),
    ('aaaa1111-0000-4000-8000-000000000003', 'Sera Vex',   'friends', 17, 13400, 21, 19,  7300),
    ('aaaa1111-0000-4000-8000-000000000004', 'Volkov',     'public',   9,  3100,  2, 11,  2400),
    ('aaaa1111-0000-4000-8000-000000000005', 'Noor',       'private', 12,  6200,  0, 14,  3900)
on conflict (id) do update set
    display_name = excluded.display_name,
    visibility = excluded.visibility,
    level = excluded.level,
    total_xp = excluded.total_xp,
    streak_days = excluded.streak_days,
    titles_count = excluded.titles_count,
    lifetime_strength = excluded.lifetime_strength;

-- ---------------------------------------------------------------- friendships

-- Sera is an accepted ally (so her 'friends' visibility becomes readable), and
-- Volkov has a PENDING incoming request so the ALLIES accept flow has something
-- real to act on.
insert into friendships (requester_id, addressee_id, accepted, created_at)
values
    (:'alex', 'aaaa1111-0000-4000-8000-000000000003', true,  now() - interval '30 days'),
    ('aaaa1111-0000-4000-8000-000000000004', :'alex', false, now() - interval '2 days')
on conflict (requester_id, addressee_id) do update set accepted = excluded.accepted;

-- ---------------------------------------------------------------- sessions

delete from sessions where user_id in (
    'aaaa1111-0000-4000-8000-000000000001',
    'aaaa1111-0000-4000-8000-000000000002',
    'aaaa1111-0000-4000-8000-000000000003',
    'aaaa1111-0000-4000-8000-000000000004',
    'aaaa1111-0000-4000-8000-000000000005'
);

insert into sessions (id, user_id, local_id, label, started_at, completed_at, xp_awarded, strength_score, title, note)
values
    ('bbbb2222-0000-4000-8000-000000000001', 'aaaa1111-0000-4000-8000-000000000001', 1, 'Heavy Pull',   now() - interval '3 hours',  now() - interval '2 hours',  620, 412, 'Weighted pull-up PR',    'Belt at 32.5 kg finally moved. Grip gave out before the back did.'),
    ('bbbb2222-0000-4000-8000-000000000002', 'aaaa1111-0000-4000-8000-000000000002', 1, 'Push',         now() - interval '9 hours',  now() - interval '8 hours',  430, 268, 'Handstand work',         'Wall-assisted negatives, shoulders cooked.'),
    ('bbbb2222-0000-4000-8000-000000000003', 'aaaa1111-0000-4000-8000-000000000003', 1, 'Legs',         now() - interval '1 day',    now() - interval '1 day' + interval '70 minutes', 510, 330, 'Pistol squat ladder', 'Left side still lags. Slow eccentrics next week.'),
    ('bbbb2222-0000-4000-8000-000000000004', 'aaaa1111-0000-4000-8000-000000000001', 2, 'Running',      now() - interval '2 days',   now() - interval '2 days' + interval '52 minutes', 380, 0,   '10 km before work',      'Cold morning, even splits.'),
    ('bbbb2222-0000-4000-8000-000000000005', 'aaaa1111-0000-4000-8000-000000000004', 1, 'Bouldering',   now() - interval '4 days',   now() - interval '4 days' + interval '95 minutes', 290, 0,   'First V5 send',          'Heel hook beta clicked on the seventh go.'),
    ('bbbb2222-0000-4000-8000-000000000006', 'aaaa1111-0000-4000-8000-000000000005', 1, 'Volume Pull',  now() - interval '5 days',   now() - interval '5 days' + interval '64 minutes', 460, 301, 'Quiet session',          'This one should never be visible to anyone else.');

insert into session_sets (session_id, exercise_name, set_index, reps, weight_kg, modifiers, done, duration_sec, distance_m, grade)
values
    ('bbbb2222-0000-4000-8000-000000000001', 'Weighted Pull-up', 0, 5, 32.5, '',       true, null, null, null),
    ('bbbb2222-0000-4000-8000-000000000001', 'Weighted Pull-up', 1, 4, 32.5, '',       true, null, null, null),
    ('bbbb2222-0000-4000-8000-000000000001', 'Barbell Row',      0, 8, 80.0, '',       true, null, null, null),
    ('bbbb2222-0000-4000-8000-000000000002', 'Handstand Push-up',0, 6, null, 'wall',   true, null, null, null),
    ('bbbb2222-0000-4000-8000-000000000002', 'Dip',              0, 12, 20.0, '',      true, null, null, null),
    ('bbbb2222-0000-4000-8000-000000000003', 'Pistol Squat',     0, 6, null, '',       true, null, null, null),
    ('bbbb2222-0000-4000-8000-000000000003', 'Pistol Squat',     1, 5, null, 'deficit',true, null, null, null),
    ('bbbb2222-0000-4000-8000-000000000004', 'Running',          0, 1, null, '',       true, 3120, 10000, null),
    ('bbbb2222-0000-4000-8000-000000000005', 'Bouldering',       0, 7, null, '',       true, 5700, null, 'V5'),
    ('bbbb2222-0000-4000-8000-000000000006', 'Pull-up',          0, 12, null, '',      true, null, null, null);

-- ---------------------------------------------------------------- events

insert into earned_titles (user_id, title_id, unlocked_at)
values
    ('aaaa1111-0000-4000-8000-000000000001', 'awakened',        now() - interval '80 days'),
    ('aaaa1111-0000-4000-8000-000000000001', 'iron_discipline', now() - interval '40 days'),
    ('aaaa1111-0000-4000-8000-000000000001', 'shadow_marcher',  now() - interval '6 hours'),
    ('aaaa1111-0000-4000-8000-000000000002', 'awakened',        now() - interval '55 days'),
    ('aaaa1111-0000-4000-8000-000000000003', 'tireless',        now() - interval '12 hours'),
    ('aaaa1111-0000-4000-8000-000000000004', 'first_send',      now() - interval '4 days')
on conflict (user_id, title_id) do nothing;

insert into level_ups (user_id, level, reached_at)
values
    ('aaaa1111-0000-4000-8000-000000000001', 31, now() - interval '5 hours'),
    ('aaaa1111-0000-4000-8000-000000000002', 22, now() - interval '2 days'),
    ('aaaa1111-0000-4000-8000-000000000004',  9, now() - interval '4 days')
on conflict (user_id, level) do nothing;
