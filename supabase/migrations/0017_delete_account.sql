-- 0017_delete_account.sql
-- Cloud wipe deletes the whole cloud account, not just the training rows.
--
-- Until now the app deleted the caller's `profiles` row. Everything keyed on
-- profiles cascaded, but two things survived: the auth identity (signing in
-- again quietly re-created an empty profile under the old id) and
-- `cloud_archives`, which references auth.users directly, so the lifter's
-- whole history archive outlived an "erase everything" request.
--
-- Deleting the auth.users row takes both, and profiles cascades from it
-- (0001: profiles.id references auth.users on delete cascade; 0013:
-- cloud_archives.user_id likewise). The client cannot touch auth.users, so the
-- delete runs here as the function owner, and only ever for auth.uid(): the
-- caller can erase themself and no one else.
--
-- Idempotent in the 0009 style: safe to re-apply to the live database.

create or replace function public.delete_my_account() returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    me uuid := auth.uid();
begin
    if me is null then
        raise exception 'requires a signed-in lifter' using errcode = '42501';
    end if;
    delete from auth.users where id = me;
end;
$$;

-- Supabase grants EXECUTE on new public functions to anon directly, so the
-- revoke names it (see 0015). Only a signed-in caller has a self to delete.
revoke execute on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;

-- The version beacon (see 0014). EVERY FUTURE MIGRATION BUMPS THIS LITERAL and
-- Cloud.kt's NEEDED_SCHEMA_VERSION with it.
create or replace function public.schema_version() returns int
language sql stable as $$ select 17 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon, authenticated;
