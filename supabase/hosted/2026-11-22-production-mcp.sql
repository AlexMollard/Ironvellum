-- MCP schema 36. Keep this transaction identical in the fresh baseline.
begin;
create schema if not exists ironvellum_mcp;
revoke all on schema ironvellum_mcp from public, anon, authenticated, service_role;
set local search_path = pg_catalog, ironvellum_mcp;

create table if not exists ironvellum_mcp.config (
  singleton boolean primary key default true check(singleton), resource_url text not null
);
create table if not exists ironvellum_mcp.clients (
  id text primary key, name text not null, ciphertext text not null, redirects text[] not null,
  created_at timestamptz not null default now()
);
create index if not exists mcp_registrations on ironvellum_mcp.clients(created_at);
create table if not exists ironvellum_mcp.requests (
  id uuid primary key default gen_random_uuid(), client_id text not null references clients(id) on delete cascade,
  resource_url text not null, redirect_uri text not null, redirect_explicit boolean not null,
  scopes text[] not null, challenge text not null, state text, decided boolean not null default false,
  expires_at timestamptz not null default now()+interval '10 minutes'
);
create index if not exists mcp_request_expiry on ironvellum_mcp.requests(expires_at,client_id);
create table if not exists ironvellum_mcp.grants (
  id uuid primary key default gen_random_uuid(), owner_id uuid not null references auth.users(id) on delete cascade,
  client_id text not null references clients(id), resource_url text not null, scopes text[] not null,
  created_at timestamptz not null default now(), expires_at timestamptz not null default now()+interval '90 days',
  revoked_at timestamptz
);
create index if not exists mcp_owner_grants on ironvellum_mcp.grants(owner_id,created_at desc);
create table if not exists ironvellum_mcp.tokens (
  hash text primary key check(hash ~ '^[a-f0-9]{64}$'), kind text not null check(kind in ('code','access','refresh')),
  grant_id uuid not null references grants(id) on delete cascade, scopes text[] not null,
  expires_at timestamptz not null, used boolean not null default false,
  challenge text, redirect_uri text, redirect_explicit boolean
);
create index if not exists mcp_grant_tokens on ironvellum_mcp.tokens(grant_id);
create table if not exists ironvellum_mcp.movements (
  key text primary key, name text not null, metric text not null
);
create table if not exists ironvellum_mcp.drafts (
  id uuid primary key default gen_random_uuid(), owner_id uuid not null references auth.users(id) on delete cascade,
  client_id text not null references clients(id), grant_id uuid references grants(id) on delete set null,
  idempotency_key text not null, content jsonb not null, content_hash text not null,
  created_at timestamptz not null default now(), expires_at timestamptz not null default now()+interval '30 days',
  status text not null default 'pending' check(status in ('pending','approved','accepted','rejected','expired')),
  changed_at timestamptz not null default now(), unique(owner_id,client_id,idempotency_key)
);
create index if not exists mcp_draft_quota on ironvellum_mcp.drafts(owner_id,client_id,created_at desc);
create index if not exists mcp_draft_expiry on ironvellum_mcp.drafts(expires_at) where status='pending';
create table if not exists ironvellum_mcp.audit (
  id uuid primary key default gen_random_uuid(), owner_id uuid not null references auth.users(id) on delete cascade,
  client_id text not null references clients(id), tool text not null, at_time timestamptz not null default clock_timestamp(),
  result text not null, draft_id uuid
);
create index if not exists mcp_tool_quota on ironvellum_mcp.audit(owner_id,client_id,at_time desc);
create index if not exists mcp_recent_access on ironvellum_mcp.audit(owner_id,at_time desc);
create index if not exists mcp_audit_retention on ironvellum_mcp.audit(at_time);
do $$
declare t regclass;
begin
  for t in select oid::regclass from pg_class where relnamespace='ironvellum_mcp'::regnamespace and relkind='r' loop
    execute format('alter table %s enable row level security',t);
  end loop;
end $$;
revoke all on all tables in schema ironvellum_mcp from public, anon, authenticated, service_role;

create or replace function ironvellum_mcp.ok(value jsonb default '{}'::jsonb) returns jsonb
language sql immutable set search_path=pg_catalog as $$
 select value || '{"schemaVersion":1,"ok":true}'::jsonb
$$;
create or replace function ironvellum_mcp.fail(reason text) returns jsonb
language sql immutable set search_path=pg_catalog as $$
 select jsonb_build_object('schemaVersion',1,'ok',false,'error',reason)
$$;
create or replace function ironvellum_mcp.ms(value timestamptz) returns bigint
language sql immutable strict set search_path=pg_catalog as $$ select floor(extract(epoch from value)*1000)::bigint $$;
create or replace function ironvellum_mcp.shape(obj jsonb, required text[], optional text[] default '{}') returns boolean
language sql immutable set search_path=pg_catalog as $$
 select coalesce(jsonb_typeof(obj)='object' and obj ?& required and obj - (required || optional) = '{}',false)
$$;
create or replace function ironvellum_mcp.str(obj jsonb, k text, lo int, hi int) returns boolean
language sql immutable set search_path=pg_catalog as $$
 select coalesce(jsonb_typeof(obj->k)='string' and char_length(obj->>k) between lo and hi,false)
$$;
create or replace function ironvellum_mcp.intval(obj jsonb, k text, lo int, hi int) returns boolean
language sql immutable set search_path=pg_catalog as $$
 select case when jsonb_typeof(obj->k)='number' then (obj->>k)::numeric between lo and hi and trunc((obj->>k)::numeric)=(obj->>k)::numeric else false end
$$;
create or replace function ironvellum_mcp.valid_scopes(s text[]) returns boolean
language sql immutable set search_path=pg_catalog as $$
 select coalesce(cardinality(s) between 1 and 4 and array_position(s,null) is null and
   s <@ array['training:read','progress:read','savedworkouts:read','workoutdrafts:create'] and
   cardinality(s)=(select count(distinct x) from unnest(s) x),false)
$$;
create or replace function ironvellum_mcp.app_owner() returns uuid
language plpgsql stable set search_path=pg_catalog as $$
declare uid uuid := auth.uid(); claims jsonb := nullif(current_setting('request.jwt.claims',true),'')::jsonb;
begin
 if uid is null or coalesce(claims ? 'client_id',false) or nullif(current_setting('request.jwt.claim.client_id',true),'') is not null then
   raise exception 'Direct sign-in required' using errcode='42501';
 end if;
 return uid;
end $$;
create or replace function ironvellum_mcp.envelope(d ironvellum_mcp.drafts) returns jsonb
language sql stable set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
 select jsonb_build_object('id',d.id,'revision',1,'contentHash',d.content_hash,'clientName',c.name,
  'createdAtMs',ms(d.created_at),'expiresAtMs',ms(d.expires_at),'status',
  case when d.status='pending' and d.expires_at<=clock_timestamp() then 'expired' else d.status end,'draft',d.content)
 from clients c where c.id=d.client_id
$$;
create or replace function public.ironvellum_mcp_consent(p_request_id uuid,p_approved boolean,p_scopes text[],p_code_hash text)
returns jsonb language plpgsql security definer set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare uid uuid:=app_owner(); r requests; gid uuid;
begin
 select * into r from requests where id=p_request_id for update;
 if not found or r.decided or r.expires_at<=clock_timestamp() or p_approved is null then return fail('invalid_request'); end if;
 if p_approved then
   if not valid_scopes(p_scopes) or not p_scopes <@ r.scopes then return fail('invalid_scope'); end if;
   if p_code_hash is null or p_code_hash !~ '^[a-f0-9]{64}$' then return fail('invalid_request'); end if;
   insert into grants(owner_id,client_id,resource_url,scopes) values(uid,r.client_id,r.resource_url,p_scopes) returning id into gid;
   insert into tokens(hash,kind,grant_id,scopes,expires_at,challenge,redirect_uri,redirect_explicit)
     values(p_code_hash,'code',gid,p_scopes,clock_timestamp()+interval '2 minutes',r.challenge,r.redirect_uri,r.redirect_explicit);
 elsif cardinality(p_scopes)>0 or p_code_hash is not null then return fail('invalid_request');
 end if;
 update requests set decided=true where id=r.id;
 return ok(jsonb_build_object('approved',p_approved));
exception when unique_violation then return fail('invalid_request');
end $$;
create or replace function public.ironvellum_mcp_connections() returns jsonb
language plpgsql security definer set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare uid uuid:=app_owner();
begin
 return ok(jsonb_build_object('resourceUrl',(select resource_url from config),'connections',coalesce((
   select jsonb_agg(jsonb_build_object('id',g.id,'clientName',c.name,'scopes',g.scopes,
     'createdAtMs',ms(g.created_at),'expiresAtMs',ms(g.expires_at),'revokedAtMs',ms(g.revoked_at)) order by g.created_at desc,g.id)
   from grants g join clients c on c.id=g.client_id where g.owner_id=uid),'[]'::jsonb),
   'recentAccess',coalesce((select jsonb_agg(jsonb_build_object('requestId',a.id,'clientName',c.name,
     'tool',a.tool,'atMs',ms(a.at_time),'result',a.result,'draftId',a.draft_id) order by a.at_time desc,a.id)
     from (select * from audit where owner_id=uid and at_time>now()-interval '30 days' order by at_time desc,id limit 50) a
     join clients c on c.id=a.client_id),'[]'::jsonb)));
end $$;
create or replace function public.ironvellum_mcp_revoke_connection(p_grant_id uuid) returns jsonb
language plpgsql security definer set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare uid uuid:=app_owner();
begin
 update grants set revoked_at=coalesce(revoked_at,clock_timestamp()) where id=p_grant_id and owner_id=uid;
 if not found then return fail('not_found'); end if;
 return ok(jsonb_build_object('revoked',true));
end $$;
create or replace function public.ironvellum_mcp_drafts(p_limit integer) returns jsonb
language plpgsql security definer set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare uid uuid:=app_owner();
begin
 if p_limit is null or p_limit not between 1 and 50 then return fail('invalid_arguments'); end if;
 return ok(jsonb_build_object('drafts',coalesce((select jsonb_agg(envelope(d) order by d.created_at desc,d.id)
   from (select * from drafts where owner_id=uid and (status='approved' or status='pending' and expires_at>now())
         order by created_at desc,id limit p_limit) d),'[]'::jsonb)));
end $$;
create or replace function public.ironvellum_mcp_draft(p_draft_id uuid) returns jsonb
language plpgsql security definer set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare uid uuid:=app_owner(); d drafts;
begin
 select * into d from drafts where owner_id=uid and id=p_draft_id;
 if not found then return fail('not_found'); end if;
 return ok(jsonb_build_object('draft',envelope(d)));
end $$;
create or replace function public.ironvellum_mcp_review_draft(p_draft_id uuid,p_hash text,p_action text) returns jsonb
language plpgsql security definer set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare uid uuid:=app_owner(); d drafts; dest text;
begin
 select * into d from drafts where owner_id=uid and id=p_draft_id for update;
 if not found then return fail('not_found'); end if;
 if p_hash is distinct from d.content_hash then return fail('conflict'); end if;
 if p_action is null or p_action not in ('approve','accept','reject') then return fail('invalid_arguments'); end if;
 if d.status='pending' and d.expires_at<=clock_timestamp() then
   update drafts set status='expired',changed_at=clock_timestamp() where id=d.id;
   return fail('conflict');
 end if;
 dest:=case when p_action='approve' and d.status='pending' then 'approved'
            when p_action='approve' and d.status in ('approved','accepted') then d.status
            when p_action='accept' and d.status in ('approved','accepted') then 'accepted'
            when p_action='reject' and d.status='pending' then 'rejected' end;
 if dest is null then return fail('conflict'); end if;
 if dest<>d.status then update drafts set status=dest,changed_at=clock_timestamp() where id=d.id returning * into d; end if;
 return ok(jsonb_build_object('draft',envelope(d)));
end $$;

-- A scalar type guard is essential: a JSON object in a permitted leaf must never
-- smuggle other fields through a projection.
create or replace function ironvellum_mcp.leaf(obj jsonb,k text,typ text) returns jsonb
language sql immutable set search_path=pg_catalog as $$
 select case when jsonb_typeof(obj->k)=typ and octet_length((obj->k)::text)<=512 then obj->k end
$$;
-- Keys and expected scalar types are fixed by the private callers below.
create or replace function ironvellum_mcp.project_fields(obj jsonb, fields jsonb) returns jsonb
language sql immutable set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
 select case when jsonb_typeof(obj)='object' then
   (select jsonb_object_agg(k,leaf(obj,k,t)) from jsonb_each_text(fields) f(k,t)) end
$$;
create or replace function ironvellum_mcp.progress(content jsonb) returns jsonb
language plpgsql immutable set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare week jsonb; trends jsonb; weeks jsonb; lifts jsonb; lift jsonb; variants jsonb; v jsonb; projected jsonb;
        n int; excess boolean;
begin
 week:=project_fields(content->'week','{"startDay":"string","endDay":"string","workouts":"number","hardSets":"number","volumeKg":"number","prevVolumeKg":"number"}');
 trends:=project_fields(content->'trends','{"daysPerWeek":"number","records30d":"number"}');
 if trends is not null then
   weeks:=content#>'{trends,weeks}';
   if jsonb_typeof(weeks)='array' then
     n:=jsonb_array_length(weeks);
     select coalesce(jsonb_agg(project_fields(w,'{"start":"string","hardSets":"number","volumeKg":"number","workouts":"number"}') order by i),'[]'::jsonb)
     into weeks from jsonb_array_elements(weeks) with ordinality ws(w,i) where i>n-12;
   else weeks:=null; end if;
   trends:=trends || jsonb_build_object('weeks',weeks,'weeksTruncated',n>12);
 end if;
 if jsonb_typeof(content->'lifts')='array' then
   lifts:='[]'; excess:=jsonb_array_length(content->'lifts')>40;
   for lift in select value from jsonb_array_elements(content->'lifts') limit 40 loop
     if jsonb_typeof(lift->'variations')='array' then
       variants:='[]'; n:=jsonb_array_length(lift->'variations');
       for v in select value from jsonb_array_elements(lift->'variations') limit 8 loop
         projected:=project_fields(v,'{"label":"string","sessions":"number","sets":"number","lastMs":"number"}');
         variants:=variants || jsonb_build_array(projected || jsonb_build_object('peak',
           project_fields(v->'peak','{"atMs":"number","reps":"number","durationSec":"number","weightKg":"number"}')));
       end loop;
     else variants:=null; n:=null; end if;
     lifts:=lifts || jsonb_build_array(project_fields(lift,'{"name":"string","metric":"string"}') ||
       jsonb_build_object('variations',variants,'variationsTruncated',n>8));
   end loop;
 end if;
 if week is null and trends is null and lifts is null then return null; end if;
 return jsonb_build_object('week',week,'trends',trends,'lifts',lifts,'liftsTruncated',excess);
end $$;
create or replace function ironvellum_mcp.snapshot(uid uuid, tool text) returns jsonb
language plpgsql stable set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare s public.web_snapshots; content jsonb; meta jsonb; payload jsonb; built bigint; state text;
begin
 select * into s from public.web_snapshots where user_id=uid;
 if not found then return ok('{"freshness":"absent"}'); end if;
 meta:=jsonb_build_object('snapshotVersion',s.version,'syncedAtMs',ms(s.updated_at));
 content:=s.snapshot;
 if s.version<>1 or content->'v' is distinct from '1'::jsonb then
   return ok(meta || '{"freshness":"unsupported_version"}');
 end if;
 if jsonb_typeof(content->'builtAtMs') is distinct from 'number' or content->>'builtAtMs' !~ '^[0-9]{1,15}$'
    or not str(content,'zone',1,80) then return ok(meta || '{"freshness":"absent"}'); end if;
 built:=(content->>'builtAtMs')::bigint;
 state:=case when least(built,ms(s.updated_at))<ms(now()-interval '24 hours') then 'stale' else 'fresh' end;
 meta:=meta || jsonb_build_object('builtAtMs',built,'zone',content->>'zone');
 if tool='get_progress' then
   payload:=progress(content);
   if payload is null then return ok(meta || '{"freshness":"absent"}'); end if;
 else
   content:=content#>'{routines,presets}';
   if jsonb_typeof(content) is distinct from 'array' then return ok(meta || '{"freshness":"absent"}'); end if;
   select jsonb_build_object('presets',coalesce(jsonb_agg(jsonb_build_object('id',leaf(p,'id','number'),
     'name',leaf(p,'name','string'),'entries',coalesce((select jsonb_agg(jsonb_build_object(
       'name',leaf(e,'name','string'),'sets',leaf(e,'sets','number'),'reps',leaf(e,'reps','number'),
       'loadKg',leaf(e,'loadKg','number'),'scheme',leaf(e,'scheme','string'),'modifiers',leaf(e,'modifiers','string'),
       'supersetGroup',leaf(e,'supersetGroup','number'),'warmup',leaf(e,'warmup','boolean')) order by ei)
       from jsonb_array_elements(case when jsonb_typeof(p->'entries')='array' then p->'entries' else '[]' end)
       with ordinality entries(e,ei) where ei<=50),'[]'::jsonb),
     'entriesTruncated',case when jsonb_typeof(p->'entries')='array' then jsonb_array_length(p->'entries')>50 else false end
     ) order by pi),'[]'::jsonb),'truncated',jsonb_array_length(content)>50) into payload
   from jsonb_array_elements(content) with ordinality presets(p,pi) where pi<=50;
 end if;
 return ok(meta || jsonb_build_object('freshness',state) || payload);
end $$;
create or replace function ironvellum_mcp.history(uid uuid)
returns table(id text, started timestamptz, ended timestamptz, label text)
language sql stable set search_path=pg_catalog as $$
 select 'synced:'||s.id,s.started_at,s.completed_at,s.label from public.sessions s where s.user_id=uid and s.completed_at is not null
 union all select 'imported:'||s.id,s.started_at,s.completed_at,s.label from public.imported_sessions s where s.user_id=uid and s.completed_at is not null
$$;
create or replace function ironvellum_mcp.workouts(uid uuid, tool text, args jsonb) returns jsonb
language plpgsql stable set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare ref text; cutoff timestamptz; n int:=20; rows jsonb; summary jsonb; workout_id uuid;
begin
 if tool='list_workouts' then
   if not shape(args,'{}','{limit,cursor}') or (args ? 'limit' and not intval(args,'limit',1,50)) then return fail('invalid_arguments'); end if;
   n:=coalesce((args->>'limit')::numeric::int,20);
   if args ? 'cursor' then
     ref:=args->>'cursor';
     select ended into cutoff from history(uid) h where h.id=ref;
     if not found then return fail('invalid_cursor'); end if;
   end if;
   select coalesce(jsonb_agg(jsonb_build_object('id',h.id,'label',h.label,'startedAtMs',ms(h.started),
     'completedAtMs',ms(h.ended)) order by h.ended desc,h.id desc),'[]'::jsonb) into rows
   from (select * from history(uid) where cutoff is null or (ended,id)<(cutoff,ref) order by ended desc,id desc limit n+1) h;
   if jsonb_array_length(rows)>n then return ok(jsonb_build_object('workouts',rows-n,'nextCursor',rows->(n-1)->>'id')); end if;
   return ok(jsonb_build_object('workouts',rows,'nextCursor',null));
 end if;
 if not shape(args,'{id}') or not str(args,'id',1,48) then return fail('invalid_arguments'); end if;
 ref:=args->>'id';
 select jsonb_build_object('id',id,'label',label,'startedAtMs',ms(started),'completedAtMs',ms(ended)) into summary from history(uid) where id=ref;
 if not found then return fail('not_found'); end if;
 workout_id:=split_part(ref,':',2)::uuid;
 select coalesce(jsonb_agg(jsonb_build_object('exerciseName',s.exercise_name,'setIndex',s.set_index,
   'reps',s.reps,'loadKg',s.weight_kg,'durationSec',s.duration_sec,'distanceM',s.distance_m,'done',s.done)
   order by s.exercise_position nulls last,s.exercise_name,s.set_index,s.id),'[]'::jsonb) into rows
 from (select * from (
   select id,exercise_position,exercise_name,set_index,reps,weight_kg,duration_sec,distance_m,done from public.session_sets
    where ref like 'synced:%' and session_id=workout_id
   union all select id,exercise_position,exercise_name,set_index,reps,weight_kg,duration_sec,distance_m,done from public.imported_session_sets
    where ref like 'imported:%' and session_id=workout_id and user_id=uid
 ) ss order by exercise_position nulls last,exercise_name,set_index,id limit 201) s;
 return ok(jsonb_build_object('workout',summary,'sets',rows-200,'truncated',jsonb_array_length(rows)>200));
end $$;

create or replace function ironvellum_mcp.create_draft(g ironvellum_mcp.grants,args jsonb) returns jsonb
language plpgsql set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare body jsonb:=args->'draft'; entry jsonb; d drafts; digest text; ident text:=args->>'idempotencyKey'; normalized jsonb:='[]';
begin
 if not shape(args,'{idempotencyKey,draft}') or not str(args,'idempotencyKey',1,80) or ident !~ '^[A-Za-z0-9._~-]+$' then return fail('invalid_arguments'); end if;
 if not shape(body,'{schemaVersion,name,warmup,cooldown,entries}') or body->'schemaVersion'<>'1'::jsonb
    or not str(body,'name',1,80) or body->>'name'<>btrim(body->>'name') or body->>'name' ~ '[[:cntrl:]]'
    or (body->>'warmup' in ('AUTO','NONE') and body->>'cooldown' in ('AUTO','NONE')) is not true
    or jsonb_typeof(body->'entries') is distinct from 'array' then return fail('invalid_draft'); end if;
 if jsonb_array_length(body->'entries') not between 1 and 20 then return fail('invalid_draft'); end if;
 for entry in select value from jsonb_array_elements(body->'entries') loop
   if not shape(entry,'{exerciseKey,sets,reps,loadKg}') or not str(entry,'exerciseKey',1,128)
      or not intval(entry,'sets',1,50) or not intval(entry,'reps',1,1000)
      or jsonb_typeof(entry->'loadKg') not in ('number','null') then return fail('invalid_draft'); end if;
   if (entry->>'loadKg')::numeric not between 0 and 1000 then return fail('invalid_draft'); end if;
   normalized:=normalized || jsonb_build_array(jsonb_build_object('exerciseKey',entry->>'exerciseKey','sets',(entry->>'sets')::numeric::int,
     'reps',(entry->>'reps')::numeric::int,'loadKg',trim_scale((entry->>'loadKg')::numeric)));
 end loop;
 body:=jsonb_set(jsonb_set(body,'{entries}',normalized),'{schemaVersion}','1');
 digest:=encode(sha256(convert_to(body::text,'UTF8')),'hex');
 select * into d from drafts where owner_id=g.owner_id and client_id=g.client_id and idempotency_key=ident;
 if found then
   if d.content_hash<>digest then return fail('conflict'); end if;
   return ok(jsonb_build_object('draft',envelope(d)));
 end if;
 if exists(select from jsonb_array_elements(normalized) e where not exists(select from movements m where m.key=e->>'exerciseKey' and m.metric='REPS')) then
   return fail('unsupported_movement');
 end if;
 if (select count(*) from drafts where owner_id=g.owner_id and client_id=g.client_id and created_at>clock_timestamp()-interval '24 hours')>=20 then return fail('rate_limited'); end if;
 insert into drafts(owner_id,client_id,grant_id,idempotency_key,content,content_hash) values(g.owner_id,g.client_id,g.id,ident,body,digest) returning * into d;
 return ok(jsonb_build_object('draft',envelope(d)));
end $$;

create or replace function ironvellum_mcp.run_tool(g ironvellum_mcp.grants, scopes text[], tool text, args jsonb) returns jsonb
language plpgsql set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare required text[]; result jsonb; d drafts; n int; needle text;
begin
 required:=case tool when 'list_workouts' then array['training:read'] when 'get_workout' then array['training:read']
   when 'get_progress' then array['progress:read'] when 'list_saved_workouts' then array['savedworkouts:read']
   when 'search_movements' then array['savedworkouts:read','workoutdrafts:create']
   when 'create_workout_draft' then array['workoutdrafts:create'] when 'get_workout_draft' then array['workoutdrafts:create'] end;
 -- Serialize across separate grants issued to the same owner/client.
 perform pg_advisory_xact_lock(hashtextextended(g.owner_id::text || ':' || g.client_id, 36));
 if (select count(*) from audit where owner_id=g.owner_id and client_id=g.client_id and at_time>clock_timestamp()-interval '1 minute')>=120 then return fail('rate_limited'); end if;
 if required is null then result:=fail('unknown_tool');
 elsif not (required && scopes and required && g.scopes) then result:=fail('insufficient_scope');
 elsif tool in ('get_workout','list_workouts') then result:=workouts(g.owner_id,tool,args);
 elsif tool in ('get_progress','list_saved_workouts') then
   result:=case when shape(args,'{}') then snapshot(g.owner_id,tool) else fail('invalid_arguments') end;
 elsif tool='create_workout_draft' then result:=create_draft(g,args);
 elsif tool='get_workout_draft' then
   if not shape(args,'{id}') or not str(args,'id',36,36) then result:=fail('invalid_arguments');
   else
     select * into d from drafts where id::text=args->>'id' and owner_id=g.owner_id and client_id=g.client_id;
     result:=case when found then ok(jsonb_build_object('draft',envelope(d))) else fail('not_found') end;
   end if;
 else
   if not shape(args,'{query}','{limit}') or not str(args,'query',1,80) or (args ? 'limit' and not intval(args,'limit',1,20)) then result:=fail('invalid_arguments');
   else
     n:=coalesce((args->>'limit')::numeric::int,20); needle:=lower(btrim(args->>'query'));
     select ok(jsonb_build_object('movements',coalesce(jsonb_agg(to_jsonb(m) order by m.key),'[]'::jsonb))) into result
     from (select m.key,m.name,m.metric from movements m where strpos(m.key,needle)>0 and needle<>'' order by m.key limit n) m;
   end if;
 end if;
 if octet_length(result::text)>1048576 then result:=fail('result_too_large'); end if;
 insert into audit(owner_id,client_id,tool,result,draft_id) values(g.owner_id,g.client_id,case when required is null then 'unknown' else tool end,
   coalesce(result->>'error','ok'),(result#>>'{draft,id}')::uuid);
 return result;
end $$;

create or replace function ironvellum_mcp.oauth(op text,a jsonb) returns jsonb
language plpgsql set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare t tokens; g grants; expected text; digest text; bound_client text:=a->>'clientId'; selected text[];
        access_end timestamptz; refresh_end timestamptz; result jsonb;
begin
 case op
 when 'code.load','code.exchange' then expected:='code'; digest:=a->>'codeHash';
 when 'refresh.load','refresh.exchange' then expected:='refresh'; digest:=a->>'refreshHash';
 when 'access.load','tool.call' then expected:='access'; digest:=a->>'accessHash';
 when 'token.revoke' then digest:=a->>'tokenHash';
 else return fail('invalid_operation');
 end case;
 select * into t from tokens where hash=digest and (kind=expected or op='token.revoke' and kind in ('access','refresh'));
 if found then
   -- Lock the family first for every operation, then re-read the token after
   -- waiting so a racing exchange observes consumption in READ COMMITTED.
   select * into g from grants where id=t.grant_id for update;
   select * into t from tokens where hash=digest;
 end if;
 if op='token.revoke' then
   if g.id is not null and g.client_id=bound_client then update grants set revoked_at=coalesce(revoked_at,clock_timestamp()) where id=g.id; end if;
   return ok();
 end if;
 if t.hash is null or g.id is null or g.revoked_at is not null or g.expires_at<=clock_timestamp()
    or (expected in ('code','refresh') and g.client_id is distinct from bound_client)
    or g.resource_url is distinct from (select resource_url from config)
    or not t.scopes <@ g.scopes then return fail(case when expected='access' then 'invalid_token' else 'invalid_grant' end); end if;
 if t.used and expected in ('refresh','code') then
   update grants set revoked_at=clock_timestamp() where id=g.id;
   return fail('invalid_grant');
 end if;
 if t.expires_at<=clock_timestamp() then return fail(case when expected='access' then 'invalid_token' else 'invalid_grant' end); end if;
 result:=jsonb_build_object('grantId',g.id,'clientId',g.client_id,'resourceUrl',g.resource_url,'scopes',t.scopes,'expiresAtMs',ms(t.expires_at));
 if op='tool.call' then return run_tool(g,t.scopes,a->>'tool',a->'args'); end if;
 if op='code.load' then return ok(result || jsonb_build_object('codeChallenge',t.challenge,'redirectUri',t.redirect_uri,'redirectUriExplicit',t.redirect_explicit)); end if;
 if op in ('access.load','refresh.load') then return ok(result); end if;
 if a->>'resourceUrl' is distinct from g.resource_url then return fail('invalid_grant'); end if;
 if op='code.exchange' and (a->>'redirectUri' is distinct from t.redirect_uri or a->>'codeChallenge' is distinct from t.challenge) then return fail('invalid_grant'); end if;
 selected:=t.scopes;
 if op='refresh.exchange' then
   if jsonb_typeof(a->'scopes') is distinct from 'array' then return fail('invalid_scope'); end if;
   selected:=array(select jsonb_array_elements_text(a->'scopes'));
   if not valid_scopes(selected) or not selected <@ t.scopes then return fail('invalid_scope'); end if;
 end if;
 if not str(a,'accessHash',64,64) or a->>'accessHash' !~ '^[a-f0-9]{64}$'
    or coalesce(a->>case when expected='code' then 'refreshHash' else 'newRefreshHash' end,'') !~ '^[a-f0-9]{64}$' then return fail('invalid_arguments'); end if;
 access_end:=least(g.expires_at,clock_timestamp()+interval '10 minutes');
 refresh_end:=least(g.expires_at,clock_timestamp()+interval '30 days');
 update tokens set used=true where hash=t.hash;
 insert into tokens(hash,kind,grant_id,scopes,expires_at) values
   (a->>'accessHash','access',g.id,selected,access_end),
   (a->>case when expected='code' then 'refreshHash' else 'newRefreshHash' end,'refresh',g.id,selected,refresh_end);
 return ok(jsonb_build_object('grantId',g.id,'clientId',g.client_id,'scopes',selected,'resourceUrl',g.resource_url,
   'accessExpiresAtMs',ms(access_end),'refreshExpiresAtMs',ms(refresh_end)));
exception when unique_violation then return fail('invalid_arguments');
end $$;

create or replace function ironvellum_mcp.redirect_ok(uri text) returns boolean
language sql immutable set search_path=pg_catalog as $$
 select coalesce(length(uri) between 1 and 2048 and uri !~ '[[:space:][:cntrl:]#\\]' and
 (uri ~ '^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?([/?]|$)' or
  uri ~ '^http://(127[.]0[.]0[.]1|localhost|\[::1\])(:[0-9]{1,5})?([/?]|$)'),false)
$$;
create or replace function public.ironvellum_mcp_gateway(p_operation text,p_args jsonb) returns jsonb
language plpgsql security definer set search_path=pg_catalog,ironvellum_mcp,pg_temp as $$
declare a alias for p_args; k text[]; c clients; r requests; urls text[]; selected text[]; ru text;
        current_url text; item jsonb; cleaned int:=0; affected int; admitted_at timestamptz;
begin
 if jsonb_typeof(a) is distinct from 'object' or octet_length(a::text)>(case when p_operation='catalogue.put' then 1048576 else 65536 end) then return fail('invalid_arguments'); end if;
 k:=case p_operation
 when 'configuration.get' then '{}'::text[] when 'configuration.configure' then '{resourceUrl}'::text[]
 when 'client.get' then '{clientId}'::text[] when 'client.register' then '{clientId,clientName,metadataCiphertext,redirectUris}'::text[]
 when 'authorization.create' then '{clientId,redirectUri,redirectUriExplicit,resourceUrl,scopes,codeChallenge,state}'::text[]
 when 'authorization.get' then '{requestId}'::text[] when 'code.load' then '{clientId,codeHash}'::text[]
 when 'code.exchange' then '{clientId,resourceUrl,redirectUri,codeChallenge,codeHash,accessHash,refreshHash}'::text[]
 when 'access.load' then '{accessHash}'::text[] when 'refresh.load' then '{clientId,refreshHash}'::text[]
 when 'refresh.exchange' then '{clientId,resourceUrl,refreshHash,newRefreshHash,accessHash,scopes}'::text[]
 when 'token.revoke' then '{clientId,tokenHash}'::text[] when 'tool.call' then '{accessHash,tool,args}'::text[]
 when 'catalogue.put' then '{schemaVersion,movements}'::text[] when 'prune' then '{}'::text[] end;
 if k is null then return fail('invalid_operation'); end if;
 if not shape(a,k) then return fail('invalid_arguments'); end if;
 select resource_url into current_url from config;
 ru:=a->>'resourceUrl';
 case p_operation
 when 'configuration.get' then return ok(jsonb_build_object('resourceUrl',current_url));
 when 'configuration.configure' then
   if not redirect_ok(ru) or ru !~ '^https?://[^/?#]+/mcp$' then return fail('invalid_arguments'); end if;
   insert into config(resource_url) values(ru) on conflict(singleton) do nothing;
   if (select resource_url from config)<>ru then return fail('conflict'); end if;
   return ok(jsonb_build_object('resourceUrl',ru));
 when 'client.register' then
   if not str(a,'clientId',1,128) or a->>'clientId' !~ '^[A-Za-z0-9._~-]+$' or not str(a,'clientName',1,100)
      or a->>'clientName' ~ '[[:cntrl:]]' or not str(a,'metadataCiphertext',1,32768)
      or jsonb_typeof(a->'redirectUris') is distinct from 'array' then return fail('invalid_arguments'); end if;
   urls:=array(select jsonb_array_elements_text(a->'redirectUris'));
   if cardinality(urls) not between 1 and 16 or exists(select from unnest(urls) u where not redirect_ok(u)) then return fail('invalid_arguments'); end if;
   perform pg_advisory_xact_lock(36,1);
   if (select count(*) from clients where created_at>clock_timestamp()-interval '1 minute')>=20
      or (select count(*) from clients where created_at>clock_timestamp()-interval '24 hours')>=1000 then return fail('rate_limited'); end if;
   insert into clients(id,name,ciphertext,redirects) values(a->>'clientId',a->>'clientName',a->>'metadataCiphertext',urls);
   return ok(jsonb_build_object('clientId',a->>'clientId'));
 when 'client.get' then
   select * into c from clients where id=a->>'clientId';
   if not found then return fail('not_found'); end if;
   return ok(jsonb_build_object('clientId',c.id,'clientName',c.name,'metadataCiphertext',c.ciphertext,'redirectUris',c.redirects));
 when 'authorization.create' then
   select * into c from clients where id=a->>'clientId';
   if not found or current_url is null or ru is distinct from current_url or not str(a,'redirectUri',1,2048)
      or not (a->>'redirectUri'=any(c.redirects)) or not str(a,'codeChallenge',43,43) or a->>'codeChallenge' !~ '^[A-Za-z0-9_-]{43}$'
      or jsonb_typeof(a->'redirectUriExplicit') is distinct from 'boolean' or not (a->'state'='null' or str(a,'state',0,2048)) then return fail('invalid_request'); end if;
   if jsonb_typeof(a->'scopes') is distinct from 'array' then return fail('invalid_scope'); end if;
   selected:=array(select jsonb_array_elements_text(a->'scopes'));
   if not valid_scopes(selected) then return fail('invalid_scope'); end if;
   -- Expiry is immutable and exactly ten minutes after admission. This index
   -- window includes already-decided requests until their admission is a minute old.
   perform pg_advisory_xact_lock(36,2);
   admitted_at:=clock_timestamp();
   if (select count(*)>=120 or count(*) filter(where client_id=c.id)>=20
       from requests where expires_at>admitted_at+interval '9 minutes') then return fail('rate_limited'); end if;
   insert into requests(client_id,resource_url,redirect_uri,redirect_explicit,scopes,challenge,state,expires_at)
     values(c.id,ru,a->>'redirectUri',(a->>'redirectUriExplicit')::boolean,selected,a->>'codeChallenge',a->>'state',
       admitted_at+interval '10 minutes') returning * into r;
   return ok(jsonb_build_object('requestId',r.id,'expiresAtMs',ms(r.expires_at)));
 when 'authorization.get' then
   select * into r from requests where id::text=a->>'requestId' and expires_at>clock_timestamp() and not decided;
   if not found then return fail('invalid_request'); end if;
   return ok(jsonb_build_object('requestId',r.id,'clientId',r.client_id,'clientName',(select name from clients where id=r.client_id),
     'resourceUrl',r.resource_url,'redirectUri',r.redirect_uri,'redirectUriExplicit',r.redirect_explicit,'scopes',r.scopes,
     'state',r.state,'codeChallenge',r.challenge,'expiresAtMs',ms(r.expires_at)));
 when 'catalogue.put' then
   if a->'schemaVersion' is distinct from '1'::jsonb or jsonb_typeof(a->'movements') is distinct from 'array' then return fail('invalid_arguments'); end if;
   if jsonb_array_length(a->'movements') not between 1 and 5000 then return fail('invalid_arguments'); end if;
   for item in select value from jsonb_array_elements(a->'movements') loop
     if not shape(item,'{key,name,metric}') or not str(item,'key',1,128) or not str(item,'name',1,128)
        or item->>'key'<>lower(btrim(item->>'name')) or not str(item,'metric',1,32)
        or item->>'metric' not in ('REPS','HOLD','DURATION','DISTANCE_TIME','ATTEMPTS_GRADE') then return fail('invalid_arguments'); end if;
   end loop;
   lock table movements in exclusive mode;
   delete from movements;
   insert into movements select x->>'key',x->>'name',x->>'metric' from jsonb_array_elements(a->'movements') x;
   return ok(jsonb_build_object('count',jsonb_array_length(a->'movements')));
 when 'prune' then
   update drafts set status='expired',changed_at=clock_timestamp() where id in
    (select id from drafts where status='pending' and expires_at<=now() limit 200 for update skip locked);
   get diagnostics affected=row_count; cleaned:=cleaned+affected;
   delete from drafts where id in (select id from drafts where status in ('accepted','expired','rejected')
    and changed_at<now()-interval '90 days' limit 200 for update skip locked);
   get diagnostics affected=row_count; cleaned:=cleaned+affected;
   delete from audit where id in (select id from audit where at_time<now()-interval '30 days' limit 200 for update skip locked);
   get diagnostics affected=row_count; cleaned:=cleaned+affected;
   delete from requests where id in (select id from requests where expires_at<now() limit 200 for update skip locked);
   get diagnostics affected=row_count; cleaned:=cleaned+affected;
   delete from tokens where hash in (select hash from tokens where expires_at<=now() and (not used or kind='access') limit 200 for update skip locked);
   get diagnostics affected=row_count; cleaned:=cleaned+affected;
   -- Used refresh/code digests remain detectable until the family can no longer authorize any call.
   delete from tokens where hash in (select t.hash from tokens t join grants g on g.id=t.grant_id where
     (g.expires_at<now() or g.revoked_at is not null) limit 200 for update of t skip locked);
   get diagnostics affected=row_count; cleaned:=cleaned+affected;
   delete from grants where id in (select g.id from grants g where least(g.expires_at,g.revoked_at)<now()-interval '90 days'
     and not exists(select from tokens t where t.grant_id=g.id) limit 200 for update skip locked);
   get diagnostics affected=row_count; cleaned:=cleaned+affected;
   return ok(jsonb_build_object('affected',cleaned));
 else return oauth(p_operation,a);
 end case;
exception when unique_violation then return fail('conflict');
end $$;

revoke all on all functions in schema ironvellum_mcp from public, anon, authenticated, service_role;
-- Hosted Supabase provisions direct EXECUTE grants in addition to PUBLIC.
revoke all on function public.ironvellum_mcp_gateway(text,jsonb) from public,anon,authenticated;
grant execute on function public.ironvellum_mcp_gateway(text,jsonb) to service_role;
do $$
declare f regprocedure;
begin
 foreach f in array array[
   'public.ironvellum_mcp_connections()'::regprocedure,'public.ironvellum_mcp_revoke_connection(uuid)'::regprocedure,
   'public.ironvellum_mcp_drafts(integer)'::regprocedure,'public.ironvellum_mcp_draft(uuid)'::regprocedure,
   'public.ironvellum_mcp_review_draft(uuid,text,text)'::regprocedure,'public.ironvellum_mcp_consent(uuid,boolean,text[],text)'::regprocedure
 ] loop
   execute format('revoke all on function %s from public,anon,service_role',f);
   execute format('grant execute on function %s to authenticated',f);
 end loop;
end $$;
create or replace function public.schema_version() returns int language sql stable as $$ select 36 $$;
revoke execute on function public.schema_version() from public;
grant execute on function public.schema_version() to anon,authenticated;
commit;
