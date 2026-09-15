-- 0008_shadow_board.sql
-- Idempotent: every statement guards itself, so this may be re-applied.
--
-- STAGED, NOT APPLIED. Run only on an explicit go-ahead: this is the first
-- migration in the set that ALTERs a live table rather than replacing a view.
--
-- The Shadow Army gets its OWN board, deliberately separate from the training
-- leaderboard. Idle progress must never rank beside strength: the training
-- board measures what a hunter lifted, and mixing in a number that grows while
-- the phone is in a pocket would make it measure patience instead. Two boards,
-- two meanings.
--
-- Idle state itself stays on the device (Room `idle_state`). Only the shareable
-- aggregate is pushed, so the cloud never holds the accrual clock and cannot be
-- used to reconstruct when someone opened the app.

-- ---------------------------------------------------------------- columns

alter table profiles add column if not exists shadow_essence bigint not null default 0
    check (shadow_essence >= 0);
alter table profiles add column if not exists shadow_count int not null default 0
    check (shadow_count >= 0);
-- The idle rate is derived from training, so it doubles as "how hard is this
-- hunter actually working" — worth ranking, unlike raw banked essence alone.
alter table profiles add column if not exists shadow_rate double precision not null default 0
    check (shadow_rate >= 0);

-- ---------------------------------------------------------------- view

-- Same visibility rules as every other social surface: security_invoker means
-- the caller's RLS decides who they can see, so a private profile stays unseen
-- here exactly as it does on the training board.
drop view if exists shadow_board;

create view shadow_board with (security_invoker = true) as
select
    p.id,
    p.display_name,
    p.current_title_id,
    p.level,
    p.shadow_essence,
    p.shadow_count,
    p.shadow_rate
from profiles p
order by p.shadow_essence desc;

-- No new policies required: `profiles` already carries RLS with read governed by
-- can_view(id) and writes restricted to the owner, so a hunter can only ever
-- push their OWN shadow figures and can only read those they are allowed to see.
