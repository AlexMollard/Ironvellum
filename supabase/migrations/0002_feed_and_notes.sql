-- Public feed: a scrollable board of everyone's workouts, with a user-authored
-- Immutable: already applied to the live project. Never edit; land changes
-- in a later migration instead.
-- title and a public note per session.
--
-- Private notes are deliberately absent from this schema. They live only in the
-- device's Room database and are never uploaded — "private" has to mean the
-- server cannot read it, not that the UI hides it.

alter table sessions
    add column title text not null default '' check (char_length(title) <= 80),
    add column note  text not null default '' check (char_length(note) <= 500);

-- The feed orders by recency across ALL users, so the per-user index is no help.
create index sessions_completed_idx on sessions (completed_at desc)
    where completed_at is not null;

-- security_invoker keeps the caller's RLS in force: a row only appears if
-- can_view(owner) passes, so 'friends' and 'private' profiles never leak into
-- a global feed.
create view public_feed with (security_invoker = true) as
select
    s.id             as session_id,
    s.user_id,
    p.display_name,
    p.level,
    s.title,
    s.note,
    s.label,
    s.completed_at,
    s.xp_awarded,
    s.strength_score,
    (select count(*) from session_sets ss where ss.session_id = s.id and ss.done) as sets_done,
    (select coalesce(sum(ss.reps), 0) from session_sets ss where ss.session_id = s.id and ss.done) as reps_done
from sessions s
join profiles p on p.id = s.user_id
where s.completed_at is not null
order by s.completed_at desc;
