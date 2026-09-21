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
-- RUN ORDER:
--
--   1. Run this script in the Supabase SQL editor.
--   2. Read the verification SELECT at the bottom. Every count MUST be 0.
--      If any is not, the run did not take - do not skip this check.
--   3. On the device: sign up again, then Guild -> Account ->
--      RE-UPLOAD EVERYTHING.
--
-- Step 3 is NOT optional. The push watermark lives in the DEVICE's Room
-- database (`sync_state`) and records a fingerprint per uploaded session. An
-- ordinary "Sync Now" skips any session whose fingerprint still matches, and a
-- server-side wipe does not change any fingerprint - so every session would be
-- skipped forever. RE-UPLOAD EVERYTHING forgets the watermark first.
--
-- NOTE: pushing to the cloud is NOT a backup. There is no path that reads
-- cloud rows back into the device database, so the feed is a publication, not
-- a restore. The only complete restore today is EXPORT ARCHIVE.
-- ---------------------------------------------------------------------------

-- The two halves are SEPARATE transactions on purpose. They were one, and if
-- the `auth.users` delete failed - it needs elevated privilege and can trip on
-- other `auth` tables referencing it - the whole block rolled back and the
-- training data survived while the run still looked like it had happened.
-- Independent transactions mean a failure in step 2 leaves step 1 committed.

-- ---- 1. training data (this is the part that matters) --------------------
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

commit;

-- ---- 2. the accounts themselves -------------------------------------------
-- The fake hunters are auth users, not just profile rows: truncating `profiles`
-- alone leaves them able to sign in and re-create a profile.
--
-- This removes EVERY account, the owner's included. Expect to sign up again.
-- If it errors on privilege, step 1 above has still committed.
begin;

delete from auth.users;

commit;

-- ---- 3. verification - every count MUST be 0 ------------------------------
select 'profiles'      as table_name, count(*) from profiles
union all select 'sessions',      count(*) from sessions
union all select 'session_sets',  count(*) from session_sets
union all select 'earned_titles', count(*) from earned_titles
union all select 'friendships',   count(*) from friendships
union all select 'session_likes', count(*) from session_likes
union all select 'level_ups',     count(*) from level_ups
union all select 'auth.users',    count(*) from auth.users;
