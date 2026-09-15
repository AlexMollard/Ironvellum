-- 0010_hunter_discovery.sql
-- Forward fix for review finding F6. Idempotent: may be re-applied.
--
-- The friend system could not bootstrap. New profiles default to
-- `visibility = 'friends'` (0001:20), and the by-name lookup selected straight
-- from `profiles`, which is governed by `profiles_read using (can_view(id))`
-- (0001:130). For a stranger, `can_view` is false: not self, not public, and
-- `is_friend` is false precisely BECAUSE they are trying to become friends.
-- The select returned nothing and the hunter was told "No hunter is named X"
-- about someone who exists. Two people could only become friends if one first
-- switched their profile to public.
--
-- The obvious field fix — widening `profiles_read` — would dismantle the whole
-- visibility model, so instead this adds the narrowest possible discovery
-- surface:
--
--   * EXACT, case-insensitive, trimmed name match only. No prefix, no ilike,
--     no wildcards, so it cannot be walked to enumerate the roster.
--   * Returns ONLY (id, display_name). Level, XP, titles, streak and the
--     session history all stay behind `profiles_read`; finding someone tells
--     you nothing about them beyond the name you already typed.
--   * LIMIT 1, so a duplicate display name cannot be used to fan out.
--   * `authenticated` only: revoked from PUBLIC so the shipped publishable key
--     cannot ask on its own, which was finding F2's whole lesson.
--
-- It must be SECURITY DEFINER: the point is to see past `profiles_read` for
-- this one column pair. `set search_path` is pinned for the same reason every
-- other definer function in the schema pins it.
create or replace function find_hunter(name text)
returns table (id uuid, display_name text)
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    select p.id, p.display_name
    from profiles p
    where lower(p.display_name) = lower(trim(name))
    limit 1;
$$;

revoke execute on function public.find_hunter(text) from public;
grant execute on function public.find_hunter(text) to authenticated;
