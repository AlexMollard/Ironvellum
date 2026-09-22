-- 0013_cloud_archives.sql
-- One row per hunter holding that hunter's whole save as a JSON export
-- archive, so a lost phone does not mean a lost training history.
--
-- Why this exists now: cloud sync is push-only (it feeds the leaderboards and
-- the feed, it never decodes back into Room), and `ironvellum.db` is excluded
-- from Android auto-backup and device-to-device transfer. Until this table,
-- the only complete restore was the hunter manually tapping EXPORT ARCHIVE
-- before the phone died.
--
-- Privacy: the uploaded archive DELIBERATELY omits the private note
-- (`Repository.exportArchive(includePrivateNotes = false)`). The note's
-- promise — "never leaves this device" (SessionScreen) — holds here too.
-- Nothing about this table is shareable: unlike `public_feed`, no friend
-- needs to read it, so the only policies are owner-keyed on
-- `auth.uid() = user_id`. There is no delete policy on purpose: deleting a
-- backup is wiping the only cloud copy of a history; the client can always
-- overwrite the row with a newer archive, and account deletion (the cloud
-- wipe path) cascades the row via the auth.users foreign key.
--
-- Idempotent in the 0009 style: safe to re-apply to the live database.
create table if not exists cloud_archives (
    user_id    uuid primary key references auth.users (id) on delete cascade,
    archive    text not null,
    size_bytes int  not null,
    updated_at timestamptz not null default now(),
    -- The archive is the hunter's whole history as JSON; 8 MiB covers years
    -- of training with room to spare. When a real archive outgrows this the
    -- insert/update fails loudly (Postgres constraint violation, surfaced to
    -- the hunter) rather than silently truncating a partial save.
    constraint cloud_archives_size_cap check (size_bytes > 0 and size_bytes <= 8 * 1024 * 1024)
);

alter table cloud_archives enable row level security;

drop policy if exists cloud_archives_read on cloud_archives;
drop policy if exists cloud_archives_insert on cloud_archives;
drop policy if exists cloud_archives_update on cloud_archives;

-- One row per hunter, always the latest. Upsert on user_id, so insert AND
-- update both need `with check`: an update policy without it would let a row
-- be rewritten under another user's id — the hole 0009 exists to close.
create policy cloud_archives_read on cloud_archives
    for select to authenticated
    using (user_id = auth.uid());

create policy cloud_archives_insert on cloud_archives
    for insert to authenticated
    with check (user_id = auth.uid());

create policy cloud_archives_update on cloud_archives
    for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- No delete policy: see the header. No public/anon policy: an archive is
-- private to its owner.
--
-- No index beyond the primary key: user_id IS the key, and every policy is
-- `user_id = auth.uid()` — one row is reachable per call, nothing else scans
-- this table.
