-- 0018_social_talk.sql
-- Social 1.4 "Talk": comments, themed reactions, an inbox, per-workout
-- audience, block / mute / report, remove ally, and server rate limits.
-- docs/SOCIAL_PLAN.md ("1.4 contract") is the spec; every name below is read
-- by the app verbatim, so none of them may be renamed.
--
-- 1.3 clients stay on this schema until they update: every existing
-- public_feed column keeps its name and meaning, and a like inserted without a
-- kind (upsert with ignoreDuplicates) still lands as a 'salute'.
--
-- Idempotent in the 0009 style: safe to re-apply to the live database.

-- ================================================================ audience
-- 'profile' follows the profile visibility (the 1.3 behaviour, so every
-- existing row keeps its meaning); 'friends' narrows a public profile to
-- allies; 'private' is owner-only. The stricter of profile and workout wins.
alter table sessions add column if not exists audience text not null default 'profile';
alter table sessions drop constraint if exists sessions_audience_check;
alter table sessions add constraint sessions_audience_check
    check (audience in ('profile', 'friends', 'private'));

-- ================================================================ blocks, mutes
-- The name is stored because a block makes the blocked profile unreadable
-- (can_view below), and the BLOCKED list still has to say who it is.
create table if not exists blocks (
    blocker_id   uuid not null references profiles (id) on delete cascade,
    blocked_id   uuid not null references profiles (id) on delete cascade,
    blocked_name text not null default '',
    created_at   timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    constraint blocks_not_self check (blocker_id <> blocked_id)
);
-- blocked_between() looks the pair up in both directions; the PK covers one.
create index if not exists blocks_blocked_idx on blocks (blocked_id, blocker_id);

create table if not exists mutes (
    muter_id   uuid not null references profiles (id) on delete cascade,
    muted_id   uuid not null references profiles (id) on delete cascade,
    muted_name text not null default '',
    created_at timestamptz not null default now(),
    primary key (muter_id, muted_id),
    constraint mutes_not_self check (muter_id <> muted_id)
);
create index if not exists mutes_muted_idx on mutes (muted_id);

alter table blocks enable row level security;
alter table mutes  enable row level security;

-- Owner-only in every direction: the blocked lifter must never learn who
-- blocked them, and a mute is silent by design.
drop policy if exists blocks_read on blocks;
drop policy if exists blocks_insert on blocks;
drop policy if exists blocks_delete on blocks;
create policy blocks_read on blocks
    for select to authenticated using (blocker_id = auth.uid());
create policy blocks_insert on blocks
    for insert to authenticated with check (blocker_id = auth.uid());
create policy blocks_delete on blocks
    for delete to authenticated using (blocker_id = auth.uid());

drop policy if exists mutes_read on mutes;
drop policy if exists mutes_insert on mutes;
drop policy if exists mutes_delete on mutes;
create policy mutes_read on mutes
    for select to authenticated using (muter_id = auth.uid());
create policy mutes_insert on mutes
    for insert to authenticated with check (muter_id = auth.uid());
create policy mutes_delete on mutes
    for delete to authenticated using (muter_id = auth.uid());

-- Supabase grants every table privilege to anon and authenticated by default
-- (0011's lesson), so a column the client may not write has to be taken back
-- explicitly: otherwise blocked_name could be set to anything the blocker
-- likes. No update at all: a block is added or removed, never edited.
revoke all on blocks from anon, authenticated;
grant select, delete on blocks to authenticated;
grant insert (blocker_id, blocked_id) on blocks to authenticated;

revoke all on mutes from anon, authenticated;
grant select, delete on mutes to authenticated;
grant insert (muter_id, muted_id) on mutes to authenticated;

-- ================================================================ visibility
-- An oracle for who blocked whom if a client could call it, so it is only
-- ever evaluated inside the SECURITY DEFINER bodies below.
create or replace function public.blocked_between(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    select exists (
        select 1 from blocks bl
        where (bl.blocker_id = a and bl.blocked_id = b)
           or (bl.blocker_id = b and bl.blocked_id = a)
    );
$$;
revoke execute on function public.blocked_between(uuid, uuid) from public, anon, authenticated;

-- 0001's can_view plus "not blocked either way". Profiles, titles, level-ups
-- and both boards read through it, so a block hides all of them at once.
create or replace function public.can_view(owner uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select owner = auth.uid()
        or (
            not blocked_between(auth.uid(), owner)
            and exists (
                select 1 from profiles p
                where p.id = owner
                  and (p.visibility = 'public'
                    or (p.visibility = 'friends' and is_friend(auth.uid(), owner)))
            )
        );
$$;
-- Same lock-down as 0015: the read policies evaluate it as authenticated.
revoke execute on function public.can_view(uuid) from public, anon;
grant execute on function public.can_view(uuid) to authenticated;

-- The effective audience is the stricter of the profile and the workout.
create or replace function public.can_view_session(owner uuid, audience text)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    select owner = auth.uid()
        or (
            audience <> 'private'
            and can_view(owner)
            and (audience <> 'friends' or is_friend(auth.uid(), owner))
        );
$$;
revoke execute on function public.can_view_session(uuid, text) from public, anon;
grant execute on function public.can_view_session(uuid, text) to authenticated;

-- Answers only about the caller: whether auth.uid() may see what [author]
-- wrote. Mutes are owner-only under RLS and blocks go both ways, so the policy
-- needs a definer to see the other side of a block.
create or replace function public.can_see_author(author uuid)
returns boolean
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    select author = auth.uid()
        or (
            not blocked_between(auth.uid(), author)
            and not exists (
                select 1 from mutes m
                where m.muter_id = auth.uid() and m.muted_id = author
            )
        );
$$;
revoke execute on function public.can_see_author(uuid) from public, anon;
grant execute on function public.can_see_author(uuid) to authenticated;

-- Read policies move from can_view(user_id) to can_view_session, or a
-- 'private' workout would still be served to every ally.
drop policy if exists sessions_read on sessions;
create policy sessions_read on sessions
    for select to authenticated
    using (can_view_session(user_id, audience));

drop policy if exists session_sets_read on session_sets;
create policy session_sets_read on session_sets
    for select to authenticated
    using (exists (select 1 from sessions s
                   where s.id = session_id and can_view_session(s.user_id, s.audience)));

-- ================================================================ reactions
alter table session_likes add column if not exists kind text not null default 'salute';
alter table session_likes drop constraint if exists session_likes_kind_check;
alter table session_likes add constraint session_likes_kind_check
    check (kind in ('salute', 'iron', 'flame'));

-- A reaction by a lifter the reader blocked or muted is not shown to them,
-- the same rule as their comments.
drop policy if exists session_likes_read on session_likes;
create policy session_likes_read on session_likes
    for select to authenticated
    using (
        exists (select 1 from sessions s
                where s.id = session_id and can_view_session(s.user_id, s.audience))
        and can_see_author(user_id)
    );

drop policy if exists session_likes_insert on session_likes;
create policy session_likes_insert on session_likes
    for insert to authenticated
    with check (
        user_id = auth.uid()
        and exists (select 1 from sessions s
                    where s.id = session_id and can_view_session(s.user_id, s.audience))
    );

-- Changing the kind is an upsert (on conflict do update), and PostgREST's
-- upsert SETs every sent column, session_id and user_id included, so a column
-- grant cannot narrow it. The trigger below is what keeps identity fixed.
drop policy if exists session_likes_update on session_likes;
create policy session_likes_update on session_likes
    for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- A `with check` only sees the new row, so moving a reaction onto another
-- workout (one the lifter cannot see) would pass it. created_at orders the
-- owner's inbox, so it is the server's clock, and a kind change does not bump
-- the reaction back to the top of that inbox.
create or replace function public.session_likes_guard()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
    if tg_op = 'INSERT' then
        new.created_at := now();
    else
        if new.session_id is distinct from old.session_id
           or new.user_id is distinct from old.user_id then
            raise exception 'reaction rows are immutable except for kind';
        end if;
        new.created_at := old.created_at;
    end if;
    return new;
end;
$$;
revoke execute on function public.session_likes_guard() from public, anon, authenticated;

drop trigger if exists session_likes_guard on session_likes;
create trigger session_likes_guard
    before insert or update on session_likes
    for each row execute function session_likes_guard();

-- ================================================================ comments
create table if not exists session_comments (
    id          uuid primary key default gen_random_uuid(),
    session_id  uuid not null references sessions (id) on delete cascade,
    user_id     uuid not null references profiles (id) on delete cascade,
    author_name text not null default '',
    body        text not null,
    created_at  timestamptz not null default now()
);
alter table session_comments drop constraint if exists session_comments_body_len;
alter table session_comments add constraint session_comments_body_len
    check (char_length(btrim(body, E' \t\r\n')) between 1 and 280);

create index if not exists session_comments_session_idx on session_comments (session_id, created_at);
create index if not exists session_comments_user_idx on session_comments (user_id, created_at);

alter table session_comments enable row level security;

drop policy if exists session_comments_read on session_comments;
drop policy if exists session_comments_insert on session_comments;
drop policy if exists session_comments_delete on session_comments;

create policy session_comments_read on session_comments
    for select to authenticated
    using (
        exists (select 1 from sessions s
                where s.id = session_id and can_view_session(s.user_id, s.audience))
        and can_see_author(user_id)
    );

create policy session_comments_insert on session_comments
    for insert to authenticated
    with check (
        user_id = auth.uid()
        and exists (select 1 from sessions s
                    where s.id = session_id and can_view_session(s.user_id, s.audience))
    );

-- The author, or the owner of the workout it sits on: a lifter moderates
-- their own page without asking the owner of the app.
create policy session_comments_delete on session_comments
    for delete to authenticated
    using (
        user_id = auth.uid()
        or exists (select 1 from sessions s where s.id = session_id and s.user_id = auth.uid())
    );

-- No update policy and no update grant: a comment is posted or deleted.
-- Insert names only what the lifter chooses; author_name and created_at are
-- the server's, or a comment could be signed with an ally's name.
revoke all on session_comments from anon, authenticated;
grant select, delete on session_comments to authenticated;
grant insert (session_id, user_id, body) on session_comments to authenticated;

-- SECURITY DEFINER because the limits count rows the author cannot read
-- (comments on workouts that have since gone private), and the name comes
-- from the profile, never the request.
create or replace function public.session_comments_before_insert()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    n int;
begin
    new.body := btrim(new.body, E' \t\r\n');
    new.created_at := now();
    new.author_name := coalesce((select p.display_name from profiles p where p.id = new.user_id), '');

    select count(*) into n from session_comments c
    where c.user_id = new.user_id and c.created_at > now() - interval '1 minute';
    if n >= 10 then
        raise exception 'Too many comments — wait a minute.';
    end if;

    select count(*) into n from session_comments c
    where c.user_id = new.user_id and c.created_at > now() - interval '1 day';
    if n >= 200 then
        raise exception 'Daily comment limit reached — try again tomorrow.';
    end if;

    -- Bounds the one thing a crowd could grow without limit: a single thread.
    select count(*) into n from session_comments c where c.session_id = new.session_id;
    if n >= 200 then
        raise exception 'This workout has reached its comment limit.';
    end if;
    return new;
end;
$$;
revoke execute on function public.session_comments_before_insert() from public, anon, authenticated;

drop trigger if exists session_comments_before_insert on session_comments;
create trigger session_comments_before_insert
    before insert on session_comments
    for each row execute function session_comments_before_insert();

-- ================================================================ names
-- A block or mute stores the name at the time; a comment shows the author's
-- name. A rename would otherwise leave a stale name on every old comment.
create or replace function public.profiles_propagate_name()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    if new.display_name is distinct from old.display_name then
        update session_comments set author_name = new.display_name where user_id = new.id;
        update blocks set blocked_name = new.display_name where blocked_id = new.id;
        update mutes set muted_name = new.display_name where muted_id = new.id;
    end if;
    return null;
end;
$$;
revoke execute on function public.profiles_propagate_name() from public, anon, authenticated;

drop trigger if exists profiles_propagate_name on profiles;
create trigger profiles_propagate_name
    after update of display_name on profiles
    for each row execute function profiles_propagate_name();

-- SECURITY DEFINER: the blocked profile may already be unreadable to the
-- blocker (a friends-only stranger), and the friendship row is deleted in
-- either direction regardless of who requested it.
create or replace function public.blocks_before_insert()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    new.blocked_name := coalesce((select p.display_name from profiles p where p.id = new.blocked_id), '');
    new.created_at := now();
    delete from friendships f
    where (f.requester_id = new.blocker_id and f.addressee_id = new.blocked_id)
       or (f.requester_id = new.blocked_id and f.addressee_id = new.blocker_id);
    return new;
end;
$$;
revoke execute on function public.blocks_before_insert() from public, anon, authenticated;

drop trigger if exists blocks_before_insert on blocks;
create trigger blocks_before_insert
    before insert on blocks
    for each row execute function blocks_before_insert();

create or replace function public.mutes_before_insert()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    new.muted_name := coalesce((select p.display_name from profiles p where p.id = new.muted_id), '');
    new.created_at := now();
    return new;
end;
$$;
revoke execute on function public.mutes_before_insert() from public, anon, authenticated;

drop trigger if exists mutes_before_insert on mutes;
create trigger mutes_before_insert
    before insert on mutes
    for each row execute function mutes_before_insert();

-- ================================================================ allies
alter table friendships add column if not exists accepted_at timestamptz;

-- Counting live friendships rows would let a lifter send, cancel and resend
-- forever, each one a fresh request in someone's inbox. This log survives the
-- cancel. It is pruned to the last day per requester on every request, so it
-- holds at most 20 rows per lifter: it grows with people, not with time.
create table if not exists friend_request_log (
    requester_id uuid not null references profiles (id) on delete cascade,
    sent_at      timestamptz not null default now()
);
create index if not exists friend_request_log_idx on friend_request_log (requester_id, sent_at);
alter table friend_request_log enable row level security;
-- No policy and no grant: only the trigger below touches it.
revoke all on friend_request_log from anon, authenticated;

create or replace function public.friendships_before_insert()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    n int;
begin
    if blocked_between(new.requester_id, new.addressee_id) then
        raise exception 'You can’t send an ally request to this lifter.';
    end if;

    delete from friend_request_log l
    where l.requester_id = new.requester_id and l.sent_at <= now() - interval '1 day';
    select count(*) into n from friend_request_log l where l.requester_id = new.requester_id;
    if n >= 20 then
        raise exception 'Too many ally requests today — try again tomorrow.';
    end if;
    insert into friend_request_log (requester_id) values (new.requester_id);

    new.accepted_at := case when new.accepted then now() end;
    return new;
end;
$$;
revoke execute on function public.friendships_before_insert() from public, anon, authenticated;

drop trigger if exists friendships_before_insert on friendships;
create trigger friendships_before_insert
    before insert on friendships
    for each row execute function friendships_before_insert();

-- Runs beside 0005's friendships_guard_update, which still refuses an
-- identity swap; this one only owns accepted_at, so the addressee cannot
-- backdate or forge the moment they accepted.
create or replace function public.friendships_stamp_accepted()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
    if new.accepted and not old.accepted then
        new.accepted_at := now();
    elsif not new.accepted then
        new.accepted_at := null;
    else
        new.accepted_at := old.accepted_at;
    end if;
    return new;
end;
$$;
revoke execute on function public.friendships_stamp_accepted() from public, anon, authenticated;

drop trigger if exists friendships_stamp_accepted on friendships;
create trigger friendships_stamp_accepted
    before update on friendships
    for each row execute function friendships_stamp_accepted();

-- ================================================================ inbox
-- The inbox is derived (my_inbox below), never stored; this is the only row
-- it costs per lifter.
create table if not exists inbox_seen (
    user_id uuid primary key references profiles (id) on delete cascade,
    seen_at timestamptz not null default now()
);
alter table inbox_seen enable row level security;

drop policy if exists inbox_seen_read on inbox_seen;
create policy inbox_seen_read on inbox_seen
    for select to authenticated using (user_id = auth.uid());

-- Written only through mark_inbox_seen(), so seen_at is the server's clock and
-- a skewed phone cannot mark the future as read.
revoke all on inbox_seen from anon, authenticated;
grant select on inbox_seen to authenticated;

create or replace function public.mark_inbox_seen()
returns void
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
    insert into inbox_seen (user_id, seen_at) values (me, now())
    on conflict (user_id) do update set seen_at = excluded.seen_at;
end;
$$;
revoke execute on function public.mark_inbox_seen() from public, anon;
grant execute on function public.mark_inbox_seen() to authenticated;

-- SECURITY DEFINER because a request comes from a lifter whose profile the
-- caller usually cannot read yet (that is why they are asking), and the name
-- is no secret: find_hunter already resolves it. Every branch is pinned to
-- auth.uid(), so it can only ever answer about the caller.
create or replace function public.my_inbox()
returns table (
    kind             text,
    occurred_at      timestamptz,
    actor_id         uuid,
    actor_name       text,
    session_id       uuid,
    session_headline text,
    comment_id       uuid,
    body             text,
    reaction         text
)
language sql
stable
security definer
set search_path = pg_catalog, public
as $$
    with me as (select auth.uid() as id),
    items as (
        select 'request'::text as kind, f.created_at as occurred_at, f.requester_id as actor_id,
               p.display_name as actor_name, null::uuid as session_id, null::text as session_headline,
               null::uuid as comment_id, null::text as body, null::text as reaction
        from friendships f
        join profiles p on p.id = f.requester_id
        join me on f.addressee_id = me.id
        where not f.accepted

        union all

        select 'accepted', f.accepted_at, f.addressee_id, p.display_name,
               null, null, null, null, null
        from friendships f
        join profiles p on p.id = f.addressee_id
        join me on f.requester_id = me.id
        where f.accepted and f.accepted_at is not null

        union all

        select 'comment', c.created_at, c.user_id, c.author_name,
               s.id, coalesce(nullif(s.title, ''), s.label), c.id, c.body, null
        from session_comments c
        join sessions s on s.id = c.session_id
        join me on s.user_id = me.id
        where c.user_id <> me.id

        union all

        select 'reaction', l.created_at, l.user_id, p.display_name,
               s.id, coalesce(nullif(s.title, ''), s.label), null, null, l.kind
        from session_likes l
        join sessions s on s.id = l.session_id
        join profiles p on p.id = l.user_id
        join me on s.user_id = me.id
        where l.user_id <> me.id
    )
    select i.kind, i.occurred_at, i.actor_id, i.actor_name, i.session_id,
           i.session_headline, i.comment_id, i.body, i.reaction
    from items i, me
    where i.occurred_at > now() - interval '30 days'
      and not blocked_between(me.id, i.actor_id)
      and not exists (select 1 from mutes m where m.muter_id = me.id and m.muted_id = i.actor_id)
    order by i.occurred_at desc
    limit 100;
$$;
revoke execute on function public.my_inbox() from public, anon;
grant execute on function public.my_inbox() to authenticated;

-- ================================================================ reports
-- Evidence for the owner, read only with the service role in the dashboard
-- (supabase/moderation.sql). session_id and comment_id are set null when the
-- thing is deleted, and excerpt keeps what was said, so deleting the comment
-- does not delete the evidence.
create table if not exists reports (
    id             uuid primary key default gen_random_uuid(),
    reporter_id    uuid not null references profiles (id) on delete cascade,
    target_user_id uuid not null references profiles (id) on delete cascade,
    session_id     uuid references sessions (id) on delete set null,
    comment_id     uuid references session_comments (id) on delete set null,
    reason         text not null,
    note           text not null default '',
    excerpt        text not null default '',
    status         text not null default 'open',
    created_at     timestamptz not null default now()
);
alter table reports drop constraint if exists reports_reason_check;
alter table reports add constraint reports_reason_check
    check (reason in ('spam', 'abuse', 'cheating', 'other'));
alter table reports drop constraint if exists reports_note_len;
alter table reports add constraint reports_note_len check (char_length(note) <= 280);
alter table reports drop constraint if exists reports_status_check;
alter table reports add constraint reports_status_check
    check (status in ('open', 'actioned', 'dismissed'));
alter table reports drop constraint if exists reports_not_self;
alter table reports add constraint reports_not_self check (reporter_id <> target_user_id);

create index if not exists reports_reporter_idx on reports (reporter_id, created_at);
create index if not exists reports_target_idx on reports (target_user_id);

alter table reports enable row level security;

drop policy if exists reports_insert on reports;
create policy reports_insert on reports
    for insert to authenticated with check (reporter_id = auth.uid());

-- No select grant at all: a report is invisible to every client, including
-- its author, so an insert must not ask for the row back.
revoke all on reports from anon, authenticated;
grant insert (reporter_id, target_user_id, session_id, comment_id, reason, note)
    on reports to authenticated;

create or replace function public.reports_before_insert()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    n int;
begin
    select count(*) into n from reports r
    where r.reporter_id = new.reporter_id and r.created_at > now() - interval '1 day';
    if n >= 20 then
        raise exception 'Too many reports today — try again tomorrow.';
    end if;

    new.status := 'open';
    new.created_at := now();
    new.excerpt := coalesce(
        (select c.body from session_comments c where c.id = new.comment_id),
        (select concat_ws(' · ', coalesce(nullif(s.title, ''), s.label), nullif(s.note, ''))
         from sessions s where s.id = new.session_id),
        '');
    return new;
end;
$$;
revoke execute on function public.reports_before_insert() from public, anon, authenticated;

drop trigger if exists reports_before_insert on reports;
create trigger reports_before_insert
    before insert on reports
    for each row execute function reports_before_insert();

-- ================================================================ feed
-- Every 0012 column, in order and unchanged (1.3 reads them), then the 1.4
-- additions. security_invoker keeps the sessions/likes/comments policies above
-- in force, so a private or blocked workout never reaches a feed page.
drop view if exists public_feed;

create view public_feed with (security_invoker = true) as
select
    s.id                             as session_id,
    s.user_id,
    p.display_name,
    p.current_title_id,
    p.level,
    s.label,
    s.title,
    s.note,
    s.completed_at,
    s.started_at,
    s.xp_awarded,
    s.strength_score,
    (select count(*) from session_sets ss where ss.session_id = s.id and ss.done) as sets_done,
    (select coalesce(sum(ss.reps), 0) from session_sets ss where ss.session_id = s.id and ss.done) as reps_done,

    (
        select coalesce(sum(ss.duration_sec), 0)
        from session_sets ss
        where ss.session_id = s.id
          and ss.done
          and ss.reps = 0
          and ss.duration_sec is not null
          and ss.duration_sec > 0
          and ss.distance_m is null
          and ss.grade is null
    ) as held_seconds,

    -- Total reactions of every kind: a 1.3 client shows this as its like count.
    (select count(*) from session_likes sl where sl.session_id = s.id) as like_count,
    exists (select 1 from session_likes sl where sl.session_id = s.id and sl.user_id = auth.uid()) as liked_by_me,

    (
        select string_agg(m.exercise_name, ' · ' order by m.volume desc)
        from (
            select ss.exercise_name,
                   sum(ss.reps * coalesce(nullif(ss.weight_kg, 0), 1)) as volume
            from session_sets ss
            where ss.session_id = s.id and ss.done
            group by ss.exercise_name
            order by volume desc
            limit 3
        ) m
    ) as top_movements,

    (
        select case
                 when ss.weight_kg is not null and ss.weight_kg > 0
                   then ss.reps || ' x ' || trim(to_char(ss.weight_kg, 'FM999990.0')) || ' kg'
                 else ss.reps || ' x BW'
               end
        from session_sets ss
        where ss.session_id = s.id
          and ss.done
          and (
                (ss.weight_kg is not null and ss.weight_kg > 0)
             or (ss.reps > 1 and ss.distance_m is null and ss.grade is null)
          )
        order by coalesce(ss.weight_kg, 0) desc, ss.reps desc
        limit 1
    ) as best_set,

    (
        select nullif(sum(ss.distance_m), 0)
        from session_sets ss
        where ss.session_id = s.id and ss.done
    ) as distance_m,

    (
        select ss.grade
        from session_sets ss
        where ss.session_id = s.id and ss.done and ss.grade is not null and ss.grade <> ''
        order by length(ss.grade) desc, ss.grade desc
        limit 1
    ) as hardest_grade,

    (
        select count(distinct ss.exercise_name)
        from session_sets ss
        where ss.session_id = s.id and ss.done
    ) as movement_count,

    case
      when s.completed_at is not null and s.started_at is not null
        then extract(epoch from (s.completed_at - s.started_at))::int
      else null
    end as duration_sec,

    -- Counted under the reader's policies, so a muted or blocked author's
    -- comment is not counted for a reader who cannot open it.
    (select count(*) from session_comments sc where sc.session_id = s.id) as comment_count,

    coalesce((
        select jsonb_object_agg(r.kind, r.n)
        from (
            select sl.kind, count(*) as n
            from session_likes sl
            where sl.session_id = s.id
            group by sl.kind
        ) r
    ), '{}'::jsonb) as reactions,

    (select sl.kind from session_likes sl where sl.session_id = s.id and sl.user_id = auth.uid()) as my_reaction
from sessions s
join profiles p on p.id = s.user_id
where s.completed_at is not null
  -- A mute hides the lifter's workouts from the muter's feed, silently.
  and not exists (select 1 from mutes m where m.muter_id = auth.uid() and m.muted_id = s.user_id)
order by s.completed_at desc;

-- The version beacon (see 0014). EVERY FUTURE MIGRATION BUMPS THIS LITERAL and
-- Cloud.kt's NEEDED_SCHEMA_VERSION with it.
create or replace function public.schema_version() returns int
language sql stable as $$ select 18 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon, authenticated;
