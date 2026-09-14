-- 0005_friendship_hardening.sql
-- Forward fixes for three verified audit findings. Every statement is
-- idempotent: this file may be re-applied to the live database without error.
-- 0001-0004 are immutable (already applied); all hardening lands here.

-- ---------------------------------------------------------------- Finding 1
-- Privacy bypass: policy `friendships_accept` (0001_init.sql:157-160) pins the
-- addressee but not `requester_id`, so anyone who ever sent you a request could
-- UPDATE that row and rewrite `requester_id` to any user id with
-- `accepted = true`. `is_friend()` matches either direction, so the attacker
-- then passes `can_view()` and reads a stranger's friends-only profile and
-- their entire session history via `sessions_read`.
-- Rule: an addressee may flip `accepted` and nothing else.

drop policy if exists friendships_accept on friendships;
create policy friendships_accept on friendships
    for update to authenticated
    using (addressee_id = auth.uid())
    with check (addressee_id = auth.uid());

-- A `with check` predicate only sees the NEW row, so it can never compare
-- against OLD to detect a rewritten identity column; a row-level `before update`
-- trigger that compares OLD to NEW is the actual enforcement.
create or replace function friendships_no_identity_swap()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    -- Even if RLS were misconfigured later, the row identity is immutable.
    if new.requester_id is distinct from old.requester_id
       or new.addressee_id is distinct from old.addressee_id then
        raise exception 'friendship rows are immutable except for accepted';
    end if;
    return new;
end;
$$;

drop trigger if exists friendships_guard_update on friendships;
create trigger friendships_guard_update
    before update on friendships
    for each row
    execute function friendships_no_identity_swap();

-- ---------------------------------------------------------------- Finding 2
-- 0001_init.sql:35-42 claims "one row per pair" but the primary key is
-- (requester_id, addressee_id), so both directions are insertable: duplicate
-- pending requests, and a delete removes only one side. Enforce one row per
-- unordered pair, cleaning existing duplicates first or the index creation
-- would fail on the live database.

-- Keep rule per pair: any accepted row beats a pending one; among ties, the
-- oldest created_at wins (deterministic: created_at then full PK direction).
delete from friendships f
using friendships g
where least(f.requester_id, f.addressee_id) = least(g.requester_id, g.addressee_id)
  and greatest(f.requester_id, f.addressee_id) = greatest(g.requester_id, g.addressee_id)
  and (f.requester_id, f.addressee_id) <> (g.requester_id, g.addressee_id)
  -- `not accepted` sorts pending above accepted, so the "greater" row is the
  -- worse one: pending loses to accepted, then the newer row loses to the older.
  and (not f.accepted, f.created_at, f.requester_id, f.addressee_id)
    > (not g.accepted, g.created_at, g.requester_id, g.addressee_id);

create unique index if not exists friendships_pair_uniq
    on friendships (least(requester_id, addressee_id), greatest(requester_id, addressee_id));

-- ---------------------------------------------------------------- Finding 3
-- sessions.label is user-controlled with no length bound, and it surfaces as
-- the feed headline fallback (0004: `coalesce(nullif(s.title, ''), s.label)`),
-- so an unbounded label floods feed rows. Match the 80-char bound that 0002
-- already enforces on sessions.title
-- (`add column title text not null default '' check (char_length(title) <= 80)`).

-- Truncate offenders first or the constraint add fails on existing rows.
update sessions
set label = left(label, 80)
where char_length(label) > 80;

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conrelid = 'sessions'::regclass
          and conname = 'sessions_label_len'
    ) then
        alter table sessions
            add constraint sessions_label_len check (char_length(label) <= 80);
    end if;
end;
$$;
