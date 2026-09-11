-- Three additions the Guild screens need:
--   1. The worn title travels with a hunter, so the feed and board can show
--      "Kaida · Shadow Marcher" instead of a bare name.
--   2. Likes on a session, with the owner able to see WHO liked it.
--   3. Counts exposed through the views so the client needs one request, not
--      one request per card.

alter table profiles add column current_title_id text;

-- ---------------------------------------------------------------- likes

create table session_likes (
    session_id uuid not null references sessions (id) on delete cascade,
    user_id    uuid not null references profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (session_id, user_id)
);

create index session_likes_session_idx on session_likes (session_id);

alter table session_likes enable row level security;

-- Readable by anyone who may read the session itself: that is what lets the
-- owner see their likers, and stops a private session's likes leaking.
create policy session_likes_read on session_likes
    for select to authenticated
    using (exists (select 1 from sessions s where s.id = session_id and can_view(s.user_id)));

-- You may only add or remove YOUR OWN like, and only on a session you can see.
create policy session_likes_insert on session_likes
    for insert to authenticated
    with check (
        user_id = auth.uid()
        and exists (select 1 from sessions s where s.id = session_id and can_view(s.user_id))
    );

create policy session_likes_delete on session_likes
    for delete to authenticated
    using (user_id = auth.uid());

-- ---------------------------------------------------------------- views

drop view if exists public_feed;
drop view if exists feed_events;
drop view if exists leaderboard;

create view leaderboard with (security_invoker = true) as
select
    p.id,
    p.display_name,
    p.current_title_id,
    p.level,
    p.total_xp,
    p.streak_days,
    p.titles_count,
    p.lifetime_strength,
    (select count(*) from sessions s
      where s.user_id = p.id
        and s.completed_at > now() - interval '7 days') as sessions_last_7d
from profiles p;

create view public_feed with (security_invoker = true) as
select
    s.id                             as session_id,
    s.user_id,
    p.display_name,
    p.current_title_id,
    p.level,
    s.title,
    s.note,
    s.label,
    s.completed_at,
    s.xp_awarded,
    s.strength_score,
    (select count(*) from session_sets ss where ss.session_id = s.id and ss.done) as sets_done,
    (select coalesce(sum(ss.reps), 0) from session_sets ss where ss.session_id = s.id and ss.done) as reps_done,
    -- Counted server-side so a feed page costs ONE request regardless of length.
    (select count(*) from session_likes sl where sl.session_id = s.id) as like_count,
    exists (select 1 from session_likes sl where sl.session_id = s.id and sl.user_id = auth.uid()) as liked_by_me
from sessions s
join profiles p on p.id = s.user_id
where s.completed_at is not null
order by s.completed_at desc;

create view feed_events with (security_invoker = true) as
select
    'workout'::text                        as kind,
    s.completed_at                          as occurred_at,
    s.user_id,
    p.display_name,
    p.current_title_id,
    p.level                                 as profile_level,
    s.id::text                              as ref_id,
    coalesce(nullif(s.title, ''), s.label)  as headline,
    s.note,
    s.xp_awarded,
    s.strength_score,
    (select count(*) from session_sets ss where ss.session_id = s.id and ss.done) as sets_done,
    (select coalesce(sum(ss.reps), 0) from session_sets ss where ss.session_id = s.id and ss.done) as reps_done,
    null::int                               as new_level,
    (select count(*) from session_likes sl where sl.session_id = s.id) as like_count,
    exists (select 1 from session_likes sl where sl.session_id = s.id and sl.user_id = auth.uid()) as liked_by_me
from sessions s
join profiles p on p.id = s.user_id
where s.completed_at is not null

union all

select
    'title'::text, t.unlocked_at, t.user_id, p.display_name, p.current_title_id, p.level,
    t.title_id, t.title_id, '', 0, 0, 0::bigint, 0::bigint, null::int, 0::bigint, false
from earned_titles t
join profiles p on p.id = t.user_id

union all

select
    'level'::text, l.reached_at, l.user_id, p.display_name, p.current_title_id, p.level,
    l.level::text, 'Level ' || l.level, '', 0, 0, 0::bigint, 0::bigint, l.level, 0::bigint, false
from level_ups l
join profiles p on p.id = l.user_id;
