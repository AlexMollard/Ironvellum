-- Hosted patch, 2026-10-01: the inbox grows replies and circle goals.
--
-- For the already-deployed project only; a fresh project gets all of this
-- from supabase/migrations/0001_baseline.sql, which stays the source of truth
-- (this file repeats its definitions verbatim). Paste it into the SQL editor
-- once. It is idempotent and touches no rows: every statement is an
-- `if not exists` or a `create or replace` of a function that holds no data,
-- so running it twice, or on a project that already has it, changes nothing.
--
-- What it changes:
--   1. warbands.weekly_goal, if this project met warbands before the goal
--      existed (my_inbox below reads it). Existing bands get the default 12.
--   2. my_inbox() gains two kinds, with the same return columns as before so
--      older app builds keep decoding it (they skip kinds they do not know):
--        'reply'     - a remark after the caller's own, in a thread on someone
--                      else's workout the caller can still see;
--        'band_goal' - the caller's warband met its weekly goal, at the
--                      workout that crossed it.
--   3. schema_version() reports 26, the version this app build expects of a
--      custom backend. Apply this before shipping the build.

begin;

alter table warbands
    add column if not exists weekly_goal int not null default 12
        check (weekly_goal between 5 and 50);

-- SECURITY DEFINER because a request comes from a lifter whose profile the
-- caller usually cannot read yet (that is why they are asking), and the name
-- is no secret: find_hunter already resolves it. Every branch is pinned to
-- auth.uid(), so it can only ever answer about the caller. Definer rights also
-- mean RLS does not filter these rows, so a branch that reads someone else's
-- workout restates the read policy (can_view_session) itself.
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

        -- A remark in a thread the caller joined on someone else's workout:
        -- anything said after the caller's first remark there, by anyone but
        -- the caller. Only while the workout is still visible to the caller,
        -- exactly as the comments read policy would allow.
        select 'reply', c.created_at, c.user_id, c.author_name,
               s.id, coalesce(nullif(s.title, ''), s.label), c.id, c.body, null
        from session_comments c
        join sessions s on s.id = c.session_id
        join me on s.user_id <> me.id
        where c.user_id <> me.id
          and can_view_session(s.user_id, s.audience)
          and exists (
              select 1 from session_comments mine
              where mine.session_id = c.session_id
                and mine.user_id = me.id
                and mine.created_at < c.created_at
          )

        union all

        select 'reaction', l.created_at, l.user_id, p.display_name,
               s.id, coalesce(nullif(s.title, ''), s.label), null, null, l.kind
        from session_likes l
        join sessions s on s.id = l.session_id
        join profiles p on p.id = l.user_id
        join me on s.user_id = me.id
        where l.user_id <> me.id

        union all

        -- A lifter joined the caller's warband: the roster is the feed's peer,
        -- so its door opening is inbox-worthy like a request or an acceptance.
        -- Identity follows profile visibility, as the roster shows it.
        select 'band_join', m.joined_at, m.user_id,
               case when can_view(m.user_id) then p.display_name
                    else 'Ironbound' || right(m.user_id::text, 4) end,
               null, null, null, w.name, null
        from warband_members m
        join warbands w on w.id = m.warband_id
        left join profiles p on p.id = m.user_id
        join me on exists (
            select 1 from warband_members mine
            where mine.warband_id = m.warband_id and mine.user_id = me.id
        )
        where m.user_id <> me.id

        union all

        -- The caller's warband met its weekly goal: one row per week, at the
        -- workout that crossed it, attributed to whoever completed that
        -- workout. Counted exactly as my_warband() counts the banner (current
        -- members, completed, UTC Monday weeks, can_view_session), so the
        -- inbox and the banner agree on when the goal fell. The 37 days cover
        -- the inbox's 30 plus a whole week, so a week that began before the
        -- window still counts from its Monday.
        select 'band_goal', g.completed_at, g.user_id,
               case when g.user_id = me.id or can_view(g.user_id)
                    then coalesce(p.display_name, 'Ironbound' || right(g.user_id::text, 4))
                    else 'Ironbound' || right(g.user_id::text, 4) end,
               null, null, null, w.name, null
        from me
        join warband_members mine on mine.user_id = me.id
        join warbands w on w.id = mine.warband_id
        join lateral (
            select s.user_id, s.completed_at,
                   row_number() over (
                       partition by date_trunc('week', s.completed_at at time zone 'utc')
                       order by s.completed_at, s.id
                   ) as n
            from sessions s
            join warband_members m on m.user_id = s.user_id and m.warband_id = w.id
            where s.completed_at is not null
              and s.completed_at > now() - interval '37 days'
              and can_view_session(s.user_id, s.audience)
        ) g on g.n = w.weekly_goal
        left join profiles p on p.id = g.user_id
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

create or replace function public.schema_version() returns int
language sql stable as $$ select 26 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon, authenticated;

commit;
