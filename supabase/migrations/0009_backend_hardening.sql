-- 0009_backend_hardening.sql
-- Forward fixes from the second backend security review. Every statement is
-- idempotent: this file may be re-applied to the live database without error.
-- 0001-0008 are immutable (already applied); all hardening lands here.
--
-- Not addressed here, deliberately, because both need a client change in the
-- same release and are tracked separately:
--   * client-supplied aggregates (level/xp/essence are trusted from the device;
--     a cheat vector on the boards, not a data breach);
--   * friend discovery by name, which the visibility model currently blocks.

-- ---------------------------------------------------------------- Finding F2
-- The friendship graph was a queryable oracle. `is_friend(a, b)` is SECURITY
-- DEFINER, so it runs as the owner and bypasses RLS on `friendships` — that is
-- required, or `can_view()` would recurse on `profiles`. But Postgres grants
-- EXECUTE on a new function to PUBLIC, and Supabase exposes public-schema
-- functions at /rest/v1/rpc/, so anyone holding the shipped publishable key
-- could ask whether ANY two user ids are friends — the exact fact
-- `friendships_read` exists to keep between the two parties. UUIDs are
-- harvestable from the leaderboard and the feed, so the whole accepted graph
-- was enumerable pairwise, including for hunters who chose 'private'.
--
-- `can_view()` must stay callable: it is evaluated inside `profiles_read` as
-- the querying role. `is_friend()` is only ever called from inside
-- `can_view()`'s SECURITY DEFINER body, where the effective user is the owner,
-- so no client needs it at all.
revoke execute on function public.is_friend(uuid, uuid) from public;
revoke execute on function public.can_view(uuid) from public;
grant execute on function public.can_view(uuid) to authenticated;

-- ---------------------------------------------------------------- Finding F3
-- 0005 bounded `sessions.label` at 80 because it surfaces as a feed headline,
-- but stopped one table short. `session_sets.exercise_name` is aggregated into
-- `public_feed.top_movements` by string_agg, so one row with a multi-megabyte
-- name is re-serialised into EVERY reader's feed page, on mobile data, for as
-- long as that session sits in the window. Bound the free-text columns the
-- same way. Truncate first or the ALTER fails on existing rows.
update session_sets set exercise_name = left(exercise_name, 64)
    where char_length(exercise_name) > 64;
update session_sets set modifiers = left(modifiers, 64)
    where char_length(modifiers) > 64;
update earned_titles set title_id = left(title_id, 64)
    where char_length(title_id) > 64;

alter table session_sets drop constraint if exists session_sets_name_len;
alter table session_sets add constraint session_sets_name_len
    check (char_length(exercise_name) <= 64);
alter table session_sets drop constraint if exists session_sets_mods_len;
alter table session_sets add constraint session_sets_mods_len
    check (char_length(modifiers) <= 64);
alter table earned_titles drop constraint if exists earned_titles_id_len;
alter table earned_titles add constraint earned_titles_id_len
    check (char_length(title_id) <= 64);

-- ---------------------------------------------------------------- Finding F4
-- `public_feed` orders by `completed_at desc` with no bound on the column, so a
-- session dated year 9999 pinned attacker-chosen text to slot one of every
-- user's feed permanently — and the client pages with `lt(completed_at, ...)`,
-- making the pinned row the anchor every reader must scroll past. It also
-- inflated `leaderboard.sessions_last_7d`, otherwise the one honest
-- server-computed metric.
--
-- This cannot be a CHECK constraint: Postgres requires IMMUTABLE functions
-- there and `now()` is STABLE. A BEFORE trigger is the only option, following
-- the pattern 0005 established for `friendships_guard_update`. A day of slack
-- absorbs client clock skew and timezone error without allowing a pin.
create or replace function sessions_no_future_timestamps()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
    if new.started_at > now() + interval '1 day'
       or (new.completed_at is not null and new.completed_at > now() + interval '1 day') then
        raise exception 'session timestamps cannot be in the future';
    end if;
    return new;
end;
$$;

drop trigger if exists sessions_guard_time on sessions;
create trigger sessions_guard_time
    before insert or update on sessions
    for each row execute function sessions_no_future_timestamps();

-- ---------------------------------------------------------------- Finding F8
-- `touch_updated_at()` was the one function in the schema without a pinned
-- search_path. Not exploitable today (it is not SECURITY DEFINER, and
-- `authenticated` holds no CREATE on public), but it is the sole deviation from
-- the file's own convention, and pinning it means no future reviewer has to
-- re-derive that.
create or replace function touch_updated_at()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

-- --------------------------------------------------------------- Finding F10
-- `feed_events` (0004) is queried by no client code and was left at its 0004
-- shape while `public_feed` was rebuilt twice (0006, 0007). It leaks nothing —
-- it is security_invoker — but a live-looking second feed view that nothing
-- reads will either rot into a divergent implementation or waste the next
-- reviewer's time. Drop it; the milestone stream, if it returns, belongs on the
-- 0007 shape.
drop view if exists feed_events;
