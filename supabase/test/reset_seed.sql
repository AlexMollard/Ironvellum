-- Seeds a little of everything through the real sign-up path, so the reset
-- round trip in tools/gate.py has something in every corner to destroy: a
-- profile made by the sign-up trigger, and a row in each table that hangs off
-- it. Runs against the baseline, right before supabase/reset.sql.
\set ON_ERROR_STOP 1

insert into auth.users (instance_id, id, aud, role, email, encrypted_password, raw_user_meta_data, created_at, updated_at)
values ('00000000-0000-0000-0000-000000000000', 'a5500000-0000-4000-8000-0000000000a1',
        'authenticated', 'authenticated', 'seed1@m.test', '', '{"display_name": "Seedling"}', now(), now()),
       ('00000000-0000-0000-0000-000000000000', 'a5500000-0000-4000-8000-0000000000a2',
        'authenticated', 'authenticated', 'seed2@m.test', '', null, now(), now());

insert into friendships (requester_id, addressee_id)
values ('a5500000-0000-4000-8000-0000000000a1', 'a5500000-0000-4000-8000-0000000000a2');
insert into sessions (id, user_id, local_id, label, started_at, completed_at)
values ('a5544444-0000-4000-8000-000000000001', 'a5500000-0000-4000-8000-0000000000a1',
        1, 'Pull', now() - interval '2 hour', now() - interval '1 hour');
insert into session_sets (session_id, exercise_name, set_index, reps, done)
values ('a5544444-0000-4000-8000-000000000001', 'Pull-up', 0, 8, true);
insert into session_likes (session_id, user_id)
values ('a5544444-0000-4000-8000-000000000001', 'a5500000-0000-4000-8000-0000000000a2');
insert into session_comments (session_id, user_id, body)
values ('a5544444-0000-4000-8000-000000000001', 'a5500000-0000-4000-8000-0000000000a2', 'nice');
insert into earned_titles (user_id, title_id, unlocked_at)
values ('a5500000-0000-4000-8000-0000000000a1', 'first-blood', now());
insert into level_ups (user_id, level, reached_at)
values ('a5500000-0000-4000-8000-0000000000a1', 2, now());
insert into blocks (blocker_id, blocked_id)
values ('a5500000-0000-4000-8000-0000000000a2', 'a5500000-0000-4000-8000-0000000000a1');
insert into mutes (muter_id, muted_id)
values ('a5500000-0000-4000-8000-0000000000a1', 'a5500000-0000-4000-8000-0000000000a2');
insert into inbox_seen (user_id) values ('a5500000-0000-4000-8000-0000000000a1');
insert into reports (reporter_id, target_user_id, reason)
values ('a5500000-0000-4000-8000-0000000000a1', 'a5500000-0000-4000-8000-0000000000a2', 'spam');
insert into cloud_archives (user_id, archive, size_bytes)
values ('a5500000-0000-4000-8000-0000000000a1', '{}', 2);
insert into lift_marks (user_id, lift, step, recent_step, recent_at)
values ('a5500000-0000-4000-8000-0000000000a1', 'pull_up', 6, 6, now());

-- Without these the reset check below would pass for a reset that does nothing.
do $$
begin
    if (select count(*) from auth.users) = 0
       or (select count(*) from profiles) = 0
       or (select display_name from profiles where id = 'a5500000-0000-4000-8000-0000000000a1') <> 'Seedling'
       or (select count(*) from sessions) = 0
       or (select count(*) from cloud_archives) = 0
       or (select count(*) from lift_marks) = 0
       or (select count(*) from reports) = 0 then
        raise exception 'ASSERTION FAILED: the reset fixtures did not land, so the reset check would be vacuous';
    end if;
end $$;
