-- Two additions:
-- Immutable: already applied to the live project. Never edit; land changes
-- in a later migration instead.
--
-- 1. The feed shows more than workouts: title unlocks and level-ups too.
--    Level-ups need their own table because the app only ever stores CURRENT
--    xp — once you pass a threshold the moment is gone and cannot be derived
--    after the fact. Titles already carry unlocked_at, so they need no table.
--
-- 2. Activity training (running, skipping, bouldering, swimming) does not fit
--    reps x weight. Sets gain optional duration/distance/grade so a 5 km run
--    and a V4 problem can be recorded without lying about reps.

-- ---------------------------------------------------------------- level-ups

create table level_ups (
    user_id    uuid not null references profiles (id) on delete cascade,
    level      int  not null check (level > 1),
    reached_at timestamptz not null,
    primary key (user_id, level)
);

alter table level_ups enable row level security;

create policy level_ups_read on level_ups
    for select to authenticated
    using (can_view(user_id));

create policy level_ups_write on level_ups
    for all to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- ---------------------------------------------------------------- activities

alter table session_sets
    add column duration_sec int    check (duration_sec is null or duration_sec >= 0),
    add column distance_m   numeric(9, 2) check (distance_m is null or distance_m >= 0),
    -- Free text on purpose: grade systems differ (V4, 6C+, 5.11a) and forcing
    -- one would make the app wrong for half its users.
    add column grade        text check (grade is null or char_length(grade) <= 12);

-- ---------------------------------------------------------------- feed events

-- One stream, three kinds. security_invoker keeps each branch inside the
-- caller's RLS, so a private profile leaks through none of them.
create view feed_events with (security_invoker = true) as
select
    'workout'::text                as kind,
    s.completed_at                  as occurred_at,
    s.user_id,
    p.display_name,
    p.level                         as profile_level,
    s.id::text                      as ref_id,
    coalesce(nullif(s.title, ''), s.label) as headline,
    s.note,
    s.xp_awarded,
    s.strength_score,
    (select count(*) from session_sets ss where ss.session_id = s.id and ss.done) as sets_done,
    (select coalesce(sum(ss.reps), 0) from session_sets ss where ss.session_id = s.id and ss.done) as reps_done,
    null::int                       as new_level
from sessions s
join profiles p on p.id = s.user_id
where s.completed_at is not null

union all

select
    'title'::text, t.unlocked_at, t.user_id, p.display_name, p.level,
    t.title_id, t.title_id, '', 0, 0, 0::bigint, 0::bigint, null::int
from earned_titles t
join profiles p on p.id = t.user_id

union all

select
    'level'::text, l.reached_at, l.user_id, p.display_name, p.level,
    l.level::text, 'Level ' || l.level, '', 0, 0, 0::bigint, 0::bigint, l.level
from level_ups l
join profiles p on p.id = l.user_id;
