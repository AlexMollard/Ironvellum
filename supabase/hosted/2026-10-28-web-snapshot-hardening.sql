-- Hosted patch, 2026-10-28 (second): web snapshot size check, owner-only snapshot delete, cheaper RLS.
-- Idempotent; apply AFTER 2026-10-28-web-snapshot-cap.sql. It changes no function and not the schema beacon,
-- so builds that expect schema 31 keep working before and after it. Definitions are cut from the baseline.
--
--   1. web_snapshots_size_cap: the server's bound on the jsonb text form goes from 4 MiB to 8 MiB. The phone sends
--      at most 4 MiB measured in that same text form (size_bytes still <= 4 MiB), but the old bound left no room
--      for the two spacing differences between the two measures and refused snapshots the phone had measured as
--      fitting. It is added NOT VALID and then validated, and skipped when the constraint already has this
--      definition, so a second run does not rescan the table.
--   2. web_snapshots gets an owner-only DELETE policy and grant (nothing could remove a snapshot before).
--   3. The policies of the four web tables call (select auth.uid()), so Postgres evaluates it once per
--      statement and not once per row.

begin;

do $cap$
begin
    if not exists (
        select 1 from pg_constraint
        where conrelid = 'public.web_snapshots'::regclass and conname = 'web_snapshots_size_cap'
          and pg_get_constraintdef(oid) like '%(8 * 1024)%'
    ) then
        alter table web_snapshots drop constraint if exists web_snapshots_size_cap;
        alter table web_snapshots add constraint web_snapshots_size_cap check (
            size_bytes > 0 and size_bytes <= 4 * 1024 * 1024
            and octet_length(snapshot::text) <= 8 * 1024 * 1024
        ) not valid;
    end if;
    if exists (
        select 1 from pg_constraint
        where conrelid = 'public.web_snapshots'::regclass and conname = 'web_snapshots_size_cap' and not convalidated
    ) then
        alter table web_snapshots validate constraint web_snapshots_size_cap;
    end if;
end
$cap$;

drop policy if exists session_set_details_owner on session_set_details;
create policy session_set_details_owner on session_set_details
    for all to authenticated
    using (user_id = (select auth.uid()))
    with check (user_id = (select auth.uid()) and exists (
        select 1 from sessions s
        where s.id = session_set_details.session_id and s.user_id = (select auth.uid())
    ));

drop policy if exists imported_sessions_owner on imported_sessions;
create policy imported_sessions_owner on imported_sessions
    for all to authenticated
    using (user_id = (select auth.uid()))
    with check (user_id = (select auth.uid()));

drop policy if exists imported_session_sets_owner on imported_session_sets;
create policy imported_session_sets_owner on imported_session_sets
    for all to authenticated
    using (user_id = (select auth.uid()))
    with check (user_id = (select auth.uid()) and exists (
        select 1 from imported_sessions s
        where s.id = imported_session_sets.session_id and s.user_id = (select auth.uid())
    ));

drop policy if exists web_snapshots_read on web_snapshots;
create policy web_snapshots_read on web_snapshots
    for select to authenticated using (user_id = (select auth.uid()));

drop policy if exists web_snapshots_insert on web_snapshots;
create policy web_snapshots_insert on web_snapshots
    for insert to authenticated with check (user_id = (select auth.uid()));

drop policy if exists web_snapshots_update on web_snapshots;
create policy web_snapshots_update on web_snapshots
    for update to authenticated using (user_id = (select auth.uid()))
    with check (user_id = (select auth.uid()));

-- The owner can remove their own snapshot (nobody else's: the policy is the same ownership test).
drop policy if exists web_snapshots_delete on web_snapshots;
create policy web_snapshots_delete on web_snapshots
    for delete to authenticated using (user_id = (select auth.uid()));

revoke all on session_set_details, imported_sessions, imported_session_sets, web_snapshots from public, anon, authenticated;
grant select, insert, update, delete on session_set_details, imported_sessions, imported_session_sets to authenticated;
grant select, insert, update, delete on web_snapshots to authenticated;

commit;
