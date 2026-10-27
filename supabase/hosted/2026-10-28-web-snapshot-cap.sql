-- Hosted patch, 2026-10-28: allow larger owner-only web snapshots.
-- Idempotent; apply AFTER 2026-10-23-web-viewer.sql and BEFORE shipping
-- the build that expects schema 31. Definitions are cut from the baseline.

begin;

-- Upgrade schema-30 installations as well as fresh databases.
alter table web_snapshots drop constraint if exists web_snapshots_size_cap;
alter table web_snapshots add constraint web_snapshots_size_cap check (
    size_bytes > 0 and size_bytes <= 4 * 1024 * 1024
    and octet_length(snapshot::text) <= 4 * 1024 * 1024
);

create or replace function public.schema_version() returns int
language sql stable as $$ select 31 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon, authenticated;

commit;
