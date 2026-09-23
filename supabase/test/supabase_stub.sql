-- Minimal stand-in for the parts of a hosted Supabase project that the
-- migrations assume exist. Applying this to a throwaway Postgres lets the whole
-- migration chain be EXECUTED before it ever reaches the live project, which is
-- otherwise the last unverified crash surface in the app: a migration can only
-- be proven by running it.
--
--   docker run -d --rm --name pg -e POSTGRES_PASSWORD=probe postgres:16
--   psql -f supabase/test/supabase_stub.sql
--   psql -f supabase/migrations/0001_init.sql   # ... through the newest
--   psql -f supabase/test/rls_probe.sql
--
-- What this does NOT cover: PostgREST itself, GoTrue, and the real `anon` /
-- `authenticated` grant set that Supabase provisions. Policy logic, constraints,
-- triggers and function privileges are all exercised faithfully; HTTP-level
-- behaviour is not.
create extension if not exists pgcrypto;

create schema if not exists auth;

create table if not exists auth.users (
    instance_id uuid,
    id uuid primary key,
    aud text,
    role text,
    email text,
    encrypted_password text,
    created_at timestamptz,
    updated_at timestamptz
);

-- The policies call auth.uid(). A settable stub lets a probe impersonate a
-- hunter without GoTrue issuing real JWTs.
create or replace function auth.uid() returns uuid
language sql stable as $$
    select nullif(current_setting('probe.uid', true), '')::uuid;
$$;

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'anon') then
        create role anon nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'authenticated') then
        create role authenticated nologin;
    end if;
end $$;

grant usage on schema public to anon, authenticated;
alter default privileges in schema public
    grant select, insert, update, delete on tables to anon, authenticated;
-- Supabase also grants EXECUTE on every new public-schema FUNCTION directly
-- to anon and authenticated. Without this line a `revoke ... from public`
-- looks sufficient here while the live project still answers the shipped
-- publishable key: exactly how 0009's friendship-oracle fix passed this suite
-- and stayed open in production.
alter default privileges in schema public
    grant execute on functions to anon, authenticated;
