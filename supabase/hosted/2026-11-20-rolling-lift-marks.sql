-- Apply after schema 34; no hosted changes are made by adding this file.
begin;
alter table lift_marks add column if not exists recent_steps bigint[];
alter table lift_marks drop constraint if exists lift_marks_recent_steps;
alter table lift_marks add constraint lift_marks_recent_steps check (
    recent_steps is null or (cardinality(recent_steps) = 11 and array_position(recent_steps, null) is null)
);
grant select on lift_marks to authenticated;
grant update (recent_steps) on lift_marks to authenticated;
create or replace function public.clear_rolling_lift_marks() returns trigger
language plpgsql set search_path = public as $$
begin
    new.recent_steps := null;
    return new;
end $$;
drop trigger if exists clear_rolling_lift_marks on lift_marks;
create trigger clear_rolling_lift_marks before update of step, recent_step, recent_at on lift_marks
for each row execute function public.clear_rolling_lift_marks();
drop function if exists public.push_lift_marks(jsonb);
create or replace function public.push_lift_marks(expected_user_id uuid, marks jsonb) returns void
language plpgsql security invoker set search_path = public as $$
declare m record;
begin
    if auth.uid() is distinct from expected_user_id or expected_user_id is null then
        raise exception 'Lift mark account changed' using errcode = '42501';
    end if;
    for m in select * from jsonb_to_recordset(marks) as x(
        lift text, step int, recent_step int, recent_at timestamptz, recent_steps bigint[]
    ) loop
        insert into lift_marks (user_id, lift, step, recent_step, recent_at)
        values (auth.uid(), m.lift, m.step, m.recent_step, m.recent_at)
        on conflict (user_id, lift) do update set
            step = excluded.step, recent_step = excluded.recent_step, recent_at = excluded.recent_at;
        update lift_marks set recent_steps = m.recent_steps where user_id = auth.uid() and lift = m.lift;
    end loop;
end $$;
revoke execute on function public.push_lift_marks(uuid, jsonb) from public, anon;
grant execute on function public.push_lift_marks(uuid, jsonb) to authenticated;
create or replace view lift_board with (security_invoker = true) as
select
    m.user_id,
    p.display_name,
    p.current_title_id,
    p.level,
    m.lift,
    m.step,
    case when m.recent_steps is null then
        case when m.recent_at > now() - interval '7 days' and m.recent_at <= now() then m.recent_step end
    else (
        select max((s.ordinality - 1)::int)
        from unnest(m.recent_steps) with ordinality as s(at_ms, ordinality)
        where s.at_ms > extract(epoch from now() - interval '168 hours') * 1000
          and s.at_ms <= extract(epoch from now()) * 1000
          and s.ordinality - 1 <= m.step
    ) end as recent_step,
    p.current_crest_id
from lift_marks m
join profiles p on p.id = m.user_id;
grant select on lift_board to authenticated;
create or replace function public.schema_version() returns int
language sql stable as $$ select 35 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon, authenticated;
commit;
