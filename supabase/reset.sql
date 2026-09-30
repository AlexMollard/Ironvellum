-- reset.sql
-- Wipes the Ironvellum debug project so supabase/migrations/0001_baseline.sql
-- can be applied to a clean slate. Paste it into the Supabase SQL editor (it
-- runs as postgres), then paste the baseline.
--
--   PASTE ORDER:  1. supabase/reset.sql
--                 2. supabase/migrations/0001_baseline.sql
--
-- WHAT IT DESTROYS. Irreversibly, in one transaction (a failure rolls the whole
-- thing back, so a half-reset is impossible):
--   * EVERY row of auth.users, the owner's own account included. Deleting the
--     identities cascades to profiles and everything keyed on them (workouts,
--     sets, reactions, comments, lift marks, blocks, mutes, inbox, reports) and to
--     cloud_archives, and takes GoTrue's own dependent rows (identities,
--     sessions, refresh tokens) with it. Expect to sign up again.
--   * Every object the baseline creates in the public schema: the sign-up
--     trigger on auth.users, the views, the tables (with their policies,
--     indexes and triggers), the functions and the profile_visibility type.
--
-- WHAT IT LEAVES ALONE. The public schema itself, Supabase's own default
-- grants and roles, every extension, and the rest of auth. The baseline
-- creates no storage buckets or objects, so none are touched.
--
-- FOR WIPING THE DEBUG PROJECT ONLY. The device's Room database is the record
-- of training and is untouched; the cloud is disposable. Never run this against
-- a project holding data anyone cares about.
--
-- Nothing here uses CASCADE, so an object that unexpectedly depends on one of
-- these fails the script loudly instead of being swept away silently.

begin;

-- Identities first: the cascade removes profiles and every table beneath them
-- while their triggers and functions still exist.
delete from auth.users;

drop trigger if exists on_auth_user_created on auth.users;

drop view if exists public.lift_board;
drop view if exists public.public_feed;
drop view if exists public.shadow_board;
drop view if exists public.leaderboard;

drop table if exists
    public.lift_marks,
    public.reports,
    public.inbox_seen,
    public.friend_request_log,
    public.mutes,
    public.blocks,
    public.session_comments,
    public.session_likes,
    public.level_ups,
    public.earned_titles,
    public.session_sets,
    public.sessions,
    public.friendships,
    public.cloud_archives,
    public.profiles;

drop function if exists public.handle_new_user();
drop function if exists public.display_name_available(text);
drop function if exists public.schema_version();
drop function if exists public.my_inbox();
drop function if exists public.mark_inbox_seen();
drop function if exists public.delete_my_account();
drop function if exists public.push_aggregates(bigint, bigint, int, bigint, int, double precision);
drop function if exists public.monarch_level(bigint);
drop function if exists public.find_hunter(text);
drop function if exists public.can_see_author(uuid);
drop function if exists public.can_view_session(uuid, text);
drop function if exists public.can_view(uuid);
drop function if exists public.blocked_between(uuid, uuid);
drop function if exists public.is_ally(uuid);
drop function if exists public.is_friend(uuid, uuid);
drop function if exists public.reports_before_insert();
drop function if exists public.mutes_before_insert();
drop function if exists public.blocks_before_insert();
drop function if exists public.profiles_propagate_name();
drop function if exists public.session_comments_before_insert();
drop function if exists public.session_likes_guard();
drop function if exists public.friendships_stamp_accepted();
drop function if exists public.friendships_before_insert();
drop function if exists public.friendships_no_identity_swap();
drop function if exists public.sessions_no_future_timestamps();
drop function if exists public.touch_updated_at();

drop type if exists public.profile_visibility;

commit;
