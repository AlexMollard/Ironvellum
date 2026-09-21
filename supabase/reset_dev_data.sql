-- reset_dev_data.sql
-- Operational script, NOT a migration. Deliberately kept out of
-- supabase/migrations/ so it can never run as part of a deploy: it destroys
-- every row of training data in the project.
--
-- Why this exists: the cloud accumulated debug sessions and fake hunters while
-- the feed and leaderboard were built. The device's Room database is the real
-- record of training and is untouched by this script; the cloud is a derived
-- copy that can be rebuilt by pushing from the device.
--
-- ---------------------------------------------------------------------------
-- RUN ORDER - all three steps, or the cloud ends up empty and stays empty:
--
--   1. Run this script in the Supabase SQL editor.
--   2. On the device: Guild -> Account -> RE-UPLOAD EVERYTHING.
--   3. Confirm the feed shows the real session.
--
-- Step 2 is NOT optional. The push watermark lives in the DEVICE's Room
-- database (`sync_state`) and records a fingerprint per uploaded session. An
-- ordinary "Sync Now" skips any session whose fingerprint still matches, and a
-- server-side wipe does not change any fingerprint - so every session would be
-- skipped forever. RE-UPLOAD EVERYTHING forgets the watermark first.
-- ---------------------------------------------------------------------------

begin;

-- Order is irrelevant under `cascade`, but they are listed leaf-first anyway so
-- the intent is readable: likes and level-ups hang off sessions, sessions and
-- earned titles hang off profiles.
truncate table
    session_likes,
    level_ups,
    earned_titles,
    session_sets,
    sessions,
    friendships,
    profiles
restart identity cascade;

-- The fake hunters are auth users, not just profile rows: truncating `profiles`
-- alone leaves them able to sign in and re-create a profile.
--
-- Replace the address below with the owner's own sign-in email. Keeping that
-- one auth row preserves the account, the claimed display name and the need to
-- sign in again; the profile row it points at is rebuilt by the re-upload in
-- step 2.
delete from auth.users
where email is distinct from 'REPLACE_WITH_YOUR_EMAIL';

-- To remove EVERY account including the owner's, comment out the statement
-- above and uncomment this one. Expect to sign up again and re-claim the
-- display name.
-- delete from auth.users;

commit;

-- Verification: every count must be 0, and auth.users must hold only the
-- account that was deliberately kept.
select 'profiles'      as table_name, count(*) from profiles
union all select 'sessions',      count(*) from sessions
union all select 'session_sets',  count(*) from session_sets
union all select 'earned_titles', count(*) from earned_titles
union all select 'friendships',   count(*) from friendships
union all select 'session_likes', count(*) from session_likes
union all select 'level_ups',     count(*) from level_ups
union all select 'auth.users',    count(*) from auth.users;
