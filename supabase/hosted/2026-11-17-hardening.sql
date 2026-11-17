-- Hosted patch, 2026-11-17: schema 34 input bounds, retained circle bonuses and serialized limits.
-- Apply after 2026-11-12-workout-xp.sql (schema 33). Safe to reapply.
begin;

-- Repair broken clocks together so neither timestamp repair trips the new guard on reapply.
-- Preserve completed history when possible, without dropping sessions or their social rows.
update public.sessions
set started_at = case when started_at < timestamptz '2000-01-01 00:00:00+00'
                      then greatest(coalesce(completed_at, now()), timestamptz '2000-01-01 00:00:00+00') else started_at end,
    completed_at = case when completed_at < timestamptz '2000-01-01 00:00:00+00'
                        then now() else completed_at end,
    xp_awarded = greatest(0, least(xp_awarded, 1000000))
where started_at < timestamptz '2000-01-01 00:00:00+00'
   or completed_at < timestamptz '2000-01-01 00:00:00+00'
   or xp_awarded not between 0 and 1000000;

-- Xp: <=405 per hardest set, 265 bonuses incl. quest; amendments can double.
-- 1M leaves room for >1,200 such sets; activities have no app-wide row cap.
do $bounds$
begin
    if not exists (select 1 from pg_constraint where conrelid = 'public.sessions'::regclass and conname = 'sessions_xp_awarded_cap') then
        alter table public.sessions add constraint sessions_xp_awarded_cap check (xp_awarded between 0 and 1000000);
    end if;
    if not exists (select 1 from pg_constraint where conrelid = 'public.cloud_archives'::regclass and conname = 'cloud_archives_payload_cap') then
        -- Keep any oversized existing backup intact and readable; enforce the cap on every new write.
        alter table public.cloud_archives add constraint cloud_archives_payload_cap
            check (octet_length(archive) <= 8 * 1024 * 1024) not valid;
    end if;
end $bounds$;

-- Frozen weeks outlive an empty circle for the two-week bonus collection window.
alter table public.circle_weeks drop constraint if exists circle_weeks_circle_id_fkey;

create or replace function sessions_no_future_timestamps()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
    if new.started_at < timestamptz '2000-01-01 00:00:00+00'
       or new.completed_at < timestamptz '2000-01-01 00:00:00+00' then
        raise exception 'trial timestamps cannot be before 2000';
    end if;
    if new.started_at > now() + interval '1 day'
       or (new.completed_at is not null and new.completed_at > now() + interval '1 day') then
        raise exception 'trial timestamps cannot be in the future';
    end if;
    return new;
end;
$$;

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
        raise exception 'You can’t send an ally request to this Ironbound.';
    end if;

    perform pg_advisory_xact_lock(hashtext('friend_requests:' || new.requester_id::text));
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

    -- Serialize both author limits and the shared thread before counting.
    perform pg_advisory_xact_lock(hashtext('comment_author:' || new.user_id::text));
    perform pg_advisory_xact_lock(hashtext('comment_thread:' || new.session_id::text));
    select count(*) into n from session_comments c
    where c.user_id = new.user_id and c.created_at > now() - interval '1 minute';
    if n >= 10 then
        raise exception 'Too many remarks — wait a minute.';
    end if;

    select count(*) into n from session_comments c
    where c.user_id = new.user_id and c.created_at > now() - interval '1 day';
    if n >= 200 then
        raise exception 'Daily remark limit reached — try again tomorrow.';
    end if;

    -- Bounds the one thing a crowd could grow without limit: a single thread.
    select count(*) into n from session_comments c where c.session_id = new.session_id;
    if n >= 200 then
        raise exception 'This trial has reached its remark limit.';
    end if;
    return new;
end;
$$;

create or replace function public.reports_before_insert()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    n int;
begin
    perform pg_advisory_xact_lock(hashtext('reports:' || new.reporter_id::text));
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

create or replace function public.join_circle(p_code text)
returns text
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    me uuid := auth.uid();
    w  circles;
    n  int;
begin
    if me is null then
        raise exception 'Sign in to use a circle' using errcode = '42501';
    end if;
    if exists (select 1 from circle_members m where m.user_id = me) then
        raise exception 'You are already in a circle — leave it first';
    end if;
    -- Throttle the one guessable door: failed attempts only, 50 a day, the
    -- same rolling-window pattern as ally requests.
    perform pg_advisory_xact_lock(hashtext('circle_guesses:' || me::text));
    delete from circle_join_log l where l.user_id = me and l.tried_at <= now() - interval '1 day';
    select count(*) into n from circle_join_log l where l.user_id = me;
    if n >= 50 then
        return 'throttled';
    end if;
    -- Lock the circle row: the cap check and the insert must read as one step,
    -- or two simultaneous joins at seven can land both at nine, and a leave
    -- cannot delete the circle between the lookup and the insert. Leaves take the
    -- same lock first.
    select * into w from circles where invite_code = upper(trim(p_code)) for update;
    if not found then
        insert into circle_join_log (user_id) values (me);
        return 'no_such_code';
    end if;
    -- A circle holding someone the caller blocked, or who blocked the caller, is
    -- closed to them: the same neutral answer either way, so it says nothing of
    -- who blocked whom. It counts as an attempt, like any other refusal.
    if exists (select 1 from circle_members m
               where m.circle_id = w.id and blocked_between(me, m.user_id)) then
        insert into circle_join_log (user_id) values (me);
        return 'closed';
    end if;
    select count(*) into n from circle_members where circle_id = w.id;
    if n >= 8 then
        insert into circle_join_log (user_id) values (me);
        return 'full';
    end if;
    insert into circle_members (circle_id, user_id) values (w.id, me);
    return 'joined';
end;
$$;

create or replace function public.circle_roll(p_circle uuid)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
#variable_conflict use_column
declare
    cur    date := (circle_week_start(now()) at time zone 'utc')::date;
    c      circles;
    latest date;
    wk     date;
begin
    if exists (select 1 from circle_weeks w where w.circle_id = p_circle and w.week = cur)
       and not exists (select 1 from circle_weeks w
                       where w.circle_id = p_circle and w.settled_at is null and w.week < cur
                         and circle_week_settles_at(w.week) <= now()) then
        return;
    end if;
    select * into c from circles where id = p_circle for update;
    if found then
        select max(w.week) into latest from circle_weeks w where w.circle_id = p_circle;
        wk := greatest(
            coalesce(latest + 7, (circle_week_start(coalesce(c.created_at, now())) at time zone 'utc')::date),
            cur - 56
        );
        while wk <= cur loop
            perform public.circle_open_week(p_circle, wk);
            wk := wk + 7;
        end loop;
    end if;
    for wk in
        select w.week from circle_weeks w
        where w.circle_id = p_circle and w.settled_at is null and w.week < cur
          and circle_week_settles_at(w.week) <= now()
        order by w.week
    loop
        perform public.circle_settle_week(p_circle, wk);
    end loop;
end;
$$;

create or replace function public.circle_bonuses()
returns table (week date, days int, circle_name text)
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
#variable_conflict use_column
declare
    me  uuid := auth.uid();
    cid uuid;
    cur date := (circle_week_start(now()) at time zone 'utc')::date;
begin
    if me is null then
        raise exception 'Sign in to use a circle' using errcode = '42501';
    end if;
    -- Deleted circles have no future weeks; retain only the payable bonus window.
    delete from circle_weeks w
    where w.week < cur - 14
      and not exists (select 1 from circles c where c.id = w.circle_id);
    for cid in
        select distinct wm.circle_id from circle_week_members wm
        where wm.user_id = me and wm.week >= cur - 28
    loop
        perform public.circle_roll(cid);
    end loop;
    select m.circle_id into cid from circle_members m where m.user_id = me;
    if cid is not null then
        perform public.circle_roll(cid);
    end if;
    return query
    select wm.week, wm.days, c.name
    from circle_week_members wm
    join circle_weeks cw on cw.circle_id = wm.circle_id and cw.week = wm.week
    left join circles c on c.id = wm.circle_id
    where wm.user_id = me
      and cw.met is true
      and wm.days >= 1
      and wm.week >= cur - 14
    order by wm.week;
end;
$$;

create or replace view workout_xp_leaderboard with (security_invoker = true) as
select
    p.id,
    p.display_name,
    p.level,
    p.total_xp,
    p.streak_days,
    p.titles_count,
    p.lifetime_strength,
    recent.sessions_last_7d,
    p.current_title_id,
    p.current_crest_id,
    recent.workout_xp_last_7d
from profiles p
join (
    select
        s.user_id,
        count(*) as sessions_last_7d,
        -- 100 maximum-sized sessions per week, matching the lifetime XP ceiling.
        least(sum(s.xp_awarded), 100000000)::bigint as workout_xp_last_7d
    from sessions s
    where s.completed_at > now() - interval '168 hours'
      and s.completed_at <= now()
    group by s.user_id
    having sum(s.xp_awarded) > 0
) recent on recent.user_id = p.id;

create or replace function public.schema_version() returns int
language sql stable as $$ select 34 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon, authenticated;

commit;
