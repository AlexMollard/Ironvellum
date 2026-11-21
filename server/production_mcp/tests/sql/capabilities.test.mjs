import { PGlite } from '@electric-sql/pglite'
import { pgcrypto } from '@electric-sql/pglite/contrib/pgcrypto'
import assert from 'node:assert/strict'
import { before, after, test } from 'node:test'
import { createHash, randomBytes, randomUUID } from 'node:crypto'
import { mkdtemp, readFile, realpath, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { basename, dirname, join } from 'node:path'
import { createServer } from 'node:net'

const native = process.argv.includes('--native')
let db, cluster
async function disposableNative() {
  const { default: Postgres } = await import('embedded-postgres')
  const parent = await realpath(tmpdir())
  const directory = await mkdtemp(join(parent, 'ironvellum-mcp-test-'))
  const socket = createServer()
  await new Promise((resolve) => socket.listen(0, '127.0.0.1', resolve))
  const port = socket.address().port
  await new Promise((resolve) => socket.close(resolve))
  cluster = new Postgres({
    databaseDir: directory,
    port,
    user: 'postgres',
    password: randomBytes(32).toString('hex'),
    persistent: true,
    createPostgresUser: false,
    authMethod: 'scram-sha-256',
    initdbFlags: ['--encoding=UTF8', '--locale=C'],
    postgresFlags: ['-c', 'listen_addresses=127.0.0.1'],
    onLog: () => {},
    onError: () => {},
  })
  await cluster.initialise()
  await cluster.start()
  const connection = cluster.getPgClient('postgres', '127.0.0.1')
  await connection.connect()
  const query = connection.query.bind(connection)
  return {
    query,
    exec: query,
    close: async () => {
      await connection.end()
      await cluster.stop()
      const resolved = await realpath(directory)
      assert.equal(dirname(resolved), parent)
      assert.ok(basename(resolved).startsWith('ironvellum-mcp-test-'))
      await rm(resolved, { recursive: true, force: true, maxRetries: 3 })
    },
  }
}
const file = (path) => readFile(new URL('../../../../' + path, import.meta.url), 'utf8')
const upgrade = 'supabase/hosted/2026-11-22-production-mcp.sql'
const baseline = await file('supabase/migrations/0001_baseline.sql')
const user = randomUUID(),
  other = randomUUID()
const resourceUrl = 'https://ironvellum.example/mcp',
  redirectUri = 'https://client.example/callback'
const scopes = ['training:read', 'progress:read', 'savedworkouts:read', 'workoutdrafts:create']
const movements = [{ key: 'bench press', name: 'Bench Press', metric: 'REPS' }]
const hash = (value = randomUUID()) => createHash('sha256').update(value).digest('hex')
const challenge = createHash('sha256').update('v'.repeat(64)).digest('base64url')
const draft = {
  schemaVersion: 1,
  name: 'Upper A',
  warmup: 'AUTO',
  cooldown: 'NONE',
  entries: [{ exerciseKey: 'bench press', sets: 3, reps: 8, loadKg: 40 }],
}
const sql = async (q, args = []) => (await db.query(q, args)).rows[0]?.v
async function as(role, uid, client = null) {
  await db.exec('reset role')
  await db.query("select set_config('probe.uid',$1,false), set_config('request.jwt.claims',$2,false)", [
    uid ?? '',
    JSON.stringify(client ? { sub: uid, client_id: client } : { sub: uid }),
  ])
  await db.exec('set role ' + role)
}
async function gateway(operation, args = {}) {
  await as('service_role')
  return sql('select public.ironvellum_mcp_gateway($1,$2::jsonb) v', [operation, JSON.stringify(args)])
}
function ok(value) {
  assert.equal(value.ok, true, JSON.stringify(value))
  assert.equal(value.schemaVersion, 1)
  return value
}
function err(value, error) {
  assert.deepEqual(value, { schemaVersion: 1, ok: false, error })
  return value
}
async function register() {
  const clientId = randomUUID()
  ok(
    await gateway('client.register', {
      clientId,
      clientName: 'Example',
      metadataCiphertext: 'encrypted-test-fixture',
      redirectUris: [redirectUri],
    }),
  )
  return clientId
}
async function request(clientId, requested = scopes) {
  return ok(
    await gateway('authorization.create', {
      clientId,
      redirectUri,
      redirectUriExplicit: true,
      resourceUrl,
      scopes: requested,
      codeChallenge: challenge,
      state: 'client state',
    }),
  ).requestId
}
async function consent(requestId, approved, selected, codeHash, uid = user) {
  await as('authenticated', uid)
  return sql('select public.ironvellum_mcp_consent($1,$2,$3,$4) v', [requestId, approved, selected, codeHash])
}
async function issue(selected = scopes, clientId = null, uid = user) {
  clientId ??= await register()
  const requestId = await request(clientId)
  const codeHash = hash(),
    accessHash = hash(),
    refreshHash = hash()
  ok(await consent(requestId, true, selected, codeHash, uid))
  const exchange = {
    clientId,
    resourceUrl,
    redirectUri,
    codeChallenge: challenge,
    codeHash,
    accessHash,
    refreshHash,
  }
  const val = ok(await gateway('code.exchange', exchange))
  return { clientId, accessHash, refreshHash, exchange, grantId: val.grantId }
}
const call = (a, tool, args = {}) => gateway('tool.call', { accessHash: a.accessHash, tool, args })
async function review(id, digest, action, uid = user) {
  await as('authenticated', uid)
  return sql('select public.ironvellum_mcp_review_draft($1,$2,$3) v', [id, digest, action])
}
async function admin(statement, args = []) {
  await db.exec('reset role')
  return db.query(statement, args)
}
before(async () => {
  db = native ? await disposableNative() : new PGlite({ extensions: { pgcrypto } })
  await db.exec(await file('supabase/test/supabase_stub.sql'))
  await db.exec(baseline)
  await db.query("insert into auth.users(id,raw_user_meta_data) values ($1,'{}'),($2,'{}')", [user, other])
})
after(() => db?.close())

test('gateway privilege and direct app claims', async () => {
  for (const role of ['anon', 'authenticated']) {
    await as(role, other)
    await assert.rejects(sql("select public.ironvellum_mcp_gateway('configuration.get','{}') v"), {
      code: '42501',
    })
  }
  await as('authenticated', user, 'delegated-client')
  await assert.rejects(sql('select public.ironvellum_mcp_connections() v'), { code: '42501' })
  ok(await gateway('configuration.configure', { resourceUrl }))
  ok(await gateway('catalogue.put', { schemaVersion: 1, movements }))
  await as('authenticated', user)
  assert.equal(ok(await sql('select public.ironvellum_mcp_connections() v')).resourceUrl, resourceUrl)
})
test('consent, binding and committed code replay revocation', async () => {
  const clientId = await register(),
    requestId = await request(clientId, ['training:read'])
  err(await consent(requestId, true, scopes, hash()), 'invalid_scope')
  const codeHash = hash()
  ok(await consent(requestId, true, ['training:read'], codeHash))
  err(await consent(requestId, true, ['training:read'], hash()), 'invalid_request')
  const ex = {
    clientId,
    resourceUrl,
    redirectUri,
    codeHash,
    codeChallenge: challenge,
    accessHash: hash(),
    refreshHash: hash(),
  }
  for (const wrong of [
    { clientId: randomUUID() },
    { resourceUrl: 'https://other.example/mcp' },
    { redirectUri: 'https://other.example/' },
    { codeChallenge: 'X'.repeat(43) },
  ])
    err(await gateway('code.exchange', { ...ex, ...wrong }), 'invalid_grant')
  ok(await gateway('code.exchange', ex))
  err(await call(ex, 'get_progress'), 'insufficient_scope')
  ok(await call(ex, 'list_workouts'))
  err(await gateway('code.exchange', { ...ex, accessHash: hash(), refreshHash: hash() }), 'invalid_grant')
  err(await gateway('access.load', { accessHash: ex.accessHash }), 'invalid_token')
})
test('rotating refresh narrows scopes, replay invalidates fresh access and refresh', async () => {
  const a = await issue(),
    next = {
      clientId: a.clientId,
      resourceUrl,
      refreshHash: a.refreshHash,
      newRefreshHash: hash(),
      accessHash: hash(),
      scopes: ['training:read'],
    }
  ok(await gateway('refresh.exchange', next))
  err(await call(next, 'get_progress'), 'insufficient_scope')
  err(
    await gateway('refresh.exchange', {
      ...next,
      refreshHash: next.newRefreshHash,
      newRefreshHash: hash(),
      accessHash: hash(),
      scopes,
    }),
    'invalid_scope',
  )
  err(
    await gateway('refresh.exchange', { ...next, newRefreshHash: hash(), accessHash: hash() }),
    'invalid_grant',
  )
  err(await gateway('access.load', { accessHash: next.accessHash }), 'invalid_token')
  err(
    await gateway('refresh.load', { clientId: a.clientId, refreshHash: next.newRefreshHash }),
    'invalid_grant',
  )
})
test('draft creation, conflict, owner/client restrictions and durable review state', async () => {
  const a = await issue(),
    b = await issue(),
    args = { idempotencyKey: 'one', draft }
  const d = ok(await call(a, 'create_workout_draft', args)).draft
  assert.deepEqual(ok(await call(a, 'create_workout_draft', args)).draft, d)
  const reconnected = await issue(scopes, a.clientId)
  assert.deepEqual(ok(await call(reconnected, 'create_workout_draft', args)).draft, d)
  const foreign = await issue(scopes, a.clientId, other)
  err(await call(foreign, 'get_workout_draft', { id: d.id }), 'not_found')
  err(await call(a, 'create_workout_draft', { ...args, draft: { ...draft, name: 'Another' } }), 'conflict')
  err(
    await call(a, 'create_workout_draft', { ...args, draft: { ...draft, note: 'private' } }),
    'invalid_draft',
  )
  err(await call(b, 'get_workout_draft', { id: d.id }), 'not_found')
  err(await review(d.id, d.contentHash, 'approve', other), 'not_found')
  err(await review(d.id, hash(), 'approve'), 'conflict')
  err(await review(d.id, d.contentHash, 'accept'), 'conflict')
  assert.equal(ok(await review(d.id, d.contentHash, 'approve')).draft.status, 'approved')
  await admin("update ironvellum_mcp.drafts set expires_at=now()-interval '1 day' where id=$1", [d.id])
  err(await review(d.id, d.contentHash, 'reject'), 'conflict')
  assert.equal(ok(await review(d.id, d.contentHash, 'accept')).draft.status, 'accepted')
  assert.equal(ok(await review(d.id, d.contentHash, 'approve')).draft.status, 'accepted')
  for (const state of ['expired', 'rejected']) {
    const e = ok(await call(a, 'create_workout_draft', { draft, idempotencyKey: state })).draft
    if (state === 'expired')
      await admin('update ironvellum_mcp.drafts set expires_at=now() where id=$1', [e.id])
    else {
      ok(await review(e.id, e.contentHash, 'reject'))
      err(await review(e.id, e.contentHash, 'reject'), 'conflict')
    }
    err(await review(e.id, e.contentHash, 'approve'), 'conflict')
    assert.equal(ok(await call(a, 'get_workout_draft', { id: e.id })).draft.status, state)
  }
  await as('authenticated', other)
  err(await sql('select public.ironvellum_mcp_revoke_connection($1) v', [a.grantId]), 'not_found')
  await as('authenticated', user)
  ok(await sql('select public.ironvellum_mcp_revoke_connection($1) v', [a.grantId]))
  err(await call(a, 'list_workouts'), 'invalid_token')
})
test('quotas count calls and distinct drafts independently', async () => {
  const a = await issue()
  for (let i = 0; i < 20; i++) ok(await call(a, 'create_workout_draft', { draft, idempotencyKey: String(i) }))
  ok(await call(a, 'create_workout_draft', { draft, idempotencyKey: '1' }))
  err(await call(a, 'create_workout_draft', { draft, idempotencyKey: 'new' }), 'rate_limited')
  for (let i = 22; i < 120; i++) ok(await call(a, 'search_movements', { query: 'bench' }))
  err(await call(a, 'search_movements', { query: 'bench' }), 'rate_limited')
})
test('snapshot privacy, age, version and missing state', async () => {
  const a = await issue(),
    privateValue = 'CANARY-DO-NOT-RETURN'
  assert.equal(ok(await call(a, 'get_progress')).freshness, 'absent')
  const payload = {
    v: 1,
    zone: 'Australia/Sydney',
    builtAtMs: Date.now(),
    profile: { figure: privateValue },
    week: { workouts: 2, hardSets: 4, volumeKg: 100, xp: privateValue },
    lifts: [
      {
        name: 'Bench Press',
        metric: 'reps',
        bodyweight: privateValue,
        variations: [
          {
            label: 'Standard',
            sessions: 4,
            sets: 12,
            lastMs: 100,
            points: [privateValue],
            peak: {
              atMs: 100,
              reps: 8,
              weightKg: 40,
              e1rmKg: privateValue,
              durationSec: { private: privateValue },
            },
          },
        ],
      },
    ],
    trends: {
      weeks: [
        { start: '2026-11-16', hardSets: 4, workouts: 2, volumeKg: 100, groups: { private: privateValue } },
      ],
      daysPerWeek: 2,
      records30d: 1,
      recent: [privateValue],
    },
    routines: {
      presets: [
        {
          id: 1,
          name: 'Upper A',
          note: privateValue,
          entries: [
            {
              name: 'Bench Press',
              sets: 3,
              reps: 8,
              loadKg: 40,
              scheme: '8 / 6 / 4',
              modifiers: 'paused',
              supersetGroup: 1,
              warmup: false,
              figure: privateValue,
              label: privateValue,
            },
          ],
        },
      ],
      custom: [privateValue],
    },
  }
  await admin(
    "insert into web_snapshots(user_id,version,snapshot,content_hash,size_bytes) values($1,1,$2,'fixture',500)",
    [user, JSON.stringify(payload)],
  )
  const progress = ok(await call(a, 'get_progress')),
    saved = ok(await call(a, 'list_saved_workouts'))
  assert.equal(progress.week.workouts, 2)
  assert.equal(saved.presets[0].entries[0].name, 'Bench Press')
  assert.equal(progress.lifts[0].variations[0].peak.weightKg, 40)
  assert.equal(progress.lifts[0].variations[0].peak.durationSec, null)
  assert.equal(progress.trends.weeks[0].hardSets, 4)
  assert.equal(saved.presets[0].entries[0].scheme, '8 / 6 / 4')
  assert.equal(saved.presets[0].entries[0].modifiers, 'paused')
  assert.equal(saved.presets[0].entries[0].supersetGroup, 1)
  assert.equal(saved.presets[0].entries[0].warmup, false)
  assert.ok(!JSON.stringify([progress, saved]).includes(privateValue))
  const absentSections = { ...payload, week: undefined, lifts: undefined, trends: { daysPerWeek: 2 } }
  await admin('update web_snapshots set snapshot=$2 where user_id=$1', [user, JSON.stringify(absentSections)])
  const partial = ok(await call(a, 'get_progress'))
  assert.equal(partial.week, null)
  assert.equal(partial.lifts, null)
  assert.equal(partial.trends.weeks, null)
  const many = {
    ...payload,
    lifts: Array(42).fill({
      ...payload.lifts[0],
      variations: Array(10).fill(payload.lifts[0].variations[0]),
    }),
    trends: { ...payload.trends, weeks: Array.from({ length: 15 }, (_, i) => ({ workouts: i })) },
    routines: {
      presets: Array(52).fill({
        ...payload.routines.presets[0],
        entries: Array(52).fill(payload.routines.presets[0].entries[0]),
      }),
    },
  }
  await admin('update web_snapshots set snapshot=$2 where user_id=$1', [user, JSON.stringify(many)])
  const bounded = ok(await call(a, 'get_progress')),
    routines = ok(await call(a, 'list_saved_workouts'))
  assert.equal(bounded.lifts.length, 40)
  assert.equal(bounded.liftsTruncated, true)
  assert.equal(bounded.lifts[0].variations.length, 8)
  assert.equal(bounded.lifts[0].variationsTruncated, true)
  assert.equal(bounded.trends.weeks[0].workouts, 3)
  assert.equal(bounded.trends.weeks.length, 12)
  assert.equal(bounded.trends.weeksTruncated, true)
  assert.equal(routines.presets.length, 50)
  assert.equal(routines.truncated, true)
  assert.equal(routines.presets[0].entries.length, 50)
  assert.equal(routines.presets[0].entriesTruncated, true)
  await admin("update web_snapshots set snapshot=jsonb_set(snapshot,'{builtAtMs}','1000') where user_id=$1", [
    user,
  ])
  assert.equal(ok(await call(a, 'get_progress')).freshness, 'stale')
  await admin('update web_snapshots set version=2 where user_id=$1', [user])
  assert.equal(ok(await call(a, 'list_saved_workouts')).freshness, 'unsupported_version')
})
test('history orders ties, excludes incomplete/foreign rows, strips free text', async () => {
  const a = await issue(),
    foreign = randomUUID(),
    synced = randomUUID(),
    imported = randomUUID()
  await admin(
    "insert into sessions(id,user_id,local_id,label,note,started_at,completed_at) values($1,$2,1,'Training','CANARY',now(),'2025-01-02'),($3,$4,2,'Private','',now(),'2025-01-02'),(gen_random_uuid(),$2,3,'Incomplete','',now(),null)",
    [synced, user, foreign, other],
  )
  await admin(
    "insert into imported_sessions(id,user_id,local_id,label,started_at,completed_at) values($1,$2,4,'Imported',now(),'2025-01-02')",
    [imported, user],
  )
  await admin(
    "insert into session_sets(session_id,exercise_name,set_index,reps,modifiers,done) values($1,'Bench Press',0,8,'CANARY',true)",
    [synced],
  )
  const p = ok(await call(a, 'list_workouts', { limit: 1 })),
    p2 = ok(await call(a, 'list_workouts', { limit: 1, cursor: p.nextCursor }))
  assert.equal(p.workouts.length, 1)
  assert.equal(p2.nextCursor, null)
  assert.deepEqual(
    new Set([p.workouts[0].id, p2.workouts[0].id]),
    new Set(['synced:' + synced, 'imported:' + imported]),
  )
  err(await call(a, 'list_workouts', { cursor: 'synced:' + foreign }), 'invalid_cursor')
  err(await call(a, 'get_workout', { id: 'synced:' + foreign }), 'not_found')
  const w = ok(await call(a, 'get_workout', { id: 'synced:' + synced }))
  assert.equal(w.sets[0].reps, 8)
  assert.ok(!JSON.stringify(w).includes('CANARY'))
  err(await call(a, 'list_workouts', { userId: other }), 'invalid_arguments')
  const foreignGrant = await issue(scopes, a.clientId, other)
  assert.equal(ok(await call(foreignGrant, 'list_workouts')).workouts[0].id, 'synced:' + foreign)
  err(await call(foreignGrant, 'get_workout', { id: 'synced:' + synced }), 'not_found')
  assert.equal(ok(await call(foreignGrant, 'get_progress')).freshness, 'absent')
  for (const tool of ['delete_account', 'backup', 'public_feed', 'accept_workout_draft'])
    err(await call(a, tool), 'unknown_tool')
})
test('credential expiry, denied requests, private tables and safe error classes', async () => {
  const a = await issue()
  const c = ok(await gateway('client.get', { clientId: a.clientId }))
  assert.equal(c.metadataCiphertext, 'encrypted-test-fixture')
  err(await gateway('configuration.configure', { resourceUrl: 'https://another.example/mcp' }), 'conflict')
  const r = await request(a.clientId)
  assert.deepEqual(ok(await gateway('authorization.get', { requestId: r })).scopes, scopes)
  ok(await consent(r, false, [], null))
  err(await gateway('authorization.get', { requestId: r }), 'invalid_request')
  const old = await request(a.clientId)
  await admin('update ironvellum_mcp.requests set expires_at=now() where id=$1', [old])
  err(await consent(old, true, scopes, hash()), 'invalid_request')
  const id = await request(a.clientId),
    codeHash = hash()
  ok(await consent(id, true, scopes, codeHash))
  assert.equal(ok(await gateway('code.load', { clientId: a.clientId, codeHash })).codeChallenge, challenge)
  await admin('update ironvellum_mcp.tokens set expires_at=now() where hash=$1', [codeHash])
  err(await gateway('code.load', { clientId: a.clientId, codeHash }), 'invalid_grant')
  const access = ok(await gateway('access.load', { accessHash: a.accessHash }))
  assert.ok(access.expiresAtMs <= Date.now() + 601000)
  await admin('update ironvellum_mcp.tokens set expires_at=now() where hash=$1', [a.accessHash])
  err(await call(a, 'list_workouts'), 'invalid_token')
  ok(await gateway('refresh.load', { clientId: a.clientId, refreshHash: a.refreshHash }))
  await admin('update ironvellum_mcp.grants set expires_at=now() where id=$1', [a.grantId])
  err(await gateway('refresh.load', { clientId: a.clientId, refreshHash: a.refreshHash }), 'invalid_grant')
  for (const role of ['anon', 'authenticated', 'service_role']) {
    await as(role, user)
    await assert.rejects(sql('select count(*) v from ironvellum_mcp.tokens'), { code: '42501' })
  }
  for (const role of ['anon', 'authenticated']) {
    await as(role, other)
    await assert.rejects(sql("select public.ironvellum_mcp_gateway('prune','{}') v"), { code: '42501' })
  }
  await as('authenticated', user, 'delegated')
  for (const q of [
    'connections()',
    'drafts(10)',
    'draft(null)',
    "review_draft(null,null,'approve')",
    'revoke_connection(null)',
    "consent(null,false,'{}',null)",
  ])
    await assert.rejects(sql('select public.ironvellum_mcp_' + q + ' v'), { code: '42501' })
  for (const op of ['delete_my_account', 'my_social_feed', 'archive.get'])
    err(await gateway(op), 'invalid_operation')
})
test('fixed fixtures, actionable app list, pruning and draft validation', async () => {
  const fixtures = JSON.parse(await file('server/production_mcp/contracts/fixtures.json')),
    a = await issue()
  const make = (key) => call(a, 'create_workout_draft', { idempotencyKey: key, draft })
  const d = ok(await make('contracts')).draft
  assert.deepEqual(Object.keys(d).sort(), Object.keys(fixtures.app.pending.draft).sort())
  assert.equal(d.contentHash, fixtures.app.pending.draft.contentHash)
  assert.deepEqual(d.draft, fixtures.app.pending.draft.draft)
  await as('authenticated', user)
  assert.ok(ok(await sql('select public.ironvellum_mcp_drafts(50) v')).drafts.some((v) => v.id === d.id))
  assert.deepEqual(ok(await sql('select public.ironvellum_mcp_draft($1) v', [d.id])).draft, d)
  const connections = ok(await sql('select public.ironvellum_mcp_connections() v'))
  assert.equal(connections.recentAccess.length, 50)
  assert.deepEqual(
    Object.keys(connections.connections[0]).sort(),
    Object.keys(fixtures.app.connections.connections[0]).sort(),
  )
  assert.deepEqual(
    Object.keys(connections.recentAccess[0]).sort(),
    Object.keys(fixtures.app.connections.recentAccess[0]).sort(),
  )
  for (const entry of [
    { sets: 0 },
    { sets: 1.5 },
    { reps: 1001 },
    { loadKg: -1 },
    { loadKg: '40' },
    { note: 'CANARY' },
  ])
    err(
      await call(a, 'create_workout_draft', {
        idempotencyKey: 'bad',
        draft: { ...draft, entries: [{ ...draft.entries[0], ...entry }] },
      }),
      'invalid_draft',
    )
  err(
    await call(a, 'create_workout_draft', {
      idempotencyKey: 'bad',
      draft: { ...draft, entries: [{ ...draft.entries[0], exerciseKey: 'unknown' }] },
    }),
    'unsupported_movement',
  )
  err(
    await gateway('catalogue.put', { schemaVersion: 1, movements: [...movements, ...movements] }),
    'conflict',
  )
  assert.deepEqual(ok(await call(a, 'search_movements', { query: 'Bench' })).movements, movements)
  ok(await review(d.id, d.contentHash, 'reject'))
  await as('authenticated', user)
  assert.ok(!ok(await sql('select public.ironvellum_mcp_drafts(50) v')).drafts.some((v) => v.id === d.id))
  await admin("update ironvellum_mcp.drafts set changed_at=now()-interval '89 days' where id=$1", [d.id])
  ok(await gateway('prune'))
  assert.equal(ok(await make('contracts')).draft.status, 'rejected')
  await admin("update ironvellum_mcp.drafts set changed_at=now()-interval '91 days' where id=$1", [d.id])
  ok(await gateway('prune'))
  err(await call(a, 'get_workout_draft', { id: d.id }), 'not_found')
  ok(await gateway('token.revoke', { clientId: a.clientId, tokenHash: a.refreshHash }))
  err(await call(a, 'get_progress'), 'invalid_token')
})
test('service-wide rolling registration limits and authorization binding', async () => {
  const clientId = await register()
  const fields = {
    clientId,
    resourceUrl,
    redirectUri,
    redirectUriExplicit: true,
    scopes,
    codeChallenge: challenge,
    state: null,
  }
  for (const replacement of [
    { resourceUrl: 'https://other.example/mcp' },
    { redirectUri: redirectUri + '?' },
    { codeChallenge: 'plain' },
  ])
    err(await gateway('authorization.create', { ...fields, ...replacement }), 'invalid_request')
  err(await gateway('authorization.create', { ...fields, scopes: ['admin'] }), 'invalid_scope')
  const reg = () =>
    gateway('client.register', {
      clientId: randomUUID(),
      clientName: 'Bounded',
      metadataCiphertext: 'encrypted-fixture',
      redirectUris: [redirectUri],
    })
  await admin("update ironvellum_mcp.clients set created_at=now()-interval '2 days'")
  for (let i = 0; i < 20; i++) ok(await reg())
  err(await reg(), 'rate_limited')
  await admin(
    "insert into ironvellum_mcp.clients(id,name,ciphertext,redirects,created_at) select gen_random_uuid()::text,'fixture','encrypted','{}',now()-interval '2 hours' from generate_series(1,980)",
  )
  await admin(
    "update ironvellum_mcp.clients set created_at=now()-interval '2 minutes' where created_at>now()-interval '1 minute'",
  )
  err(await reg(), 'rate_limited')
  await admin("update ironvellum_mcp.clients set created_at=now()-interval '2 days'")
})
test('grant cleanup preserves approved and terminal drafts, account erasure removes private owner state', async () => {
  const uid = randomUUID()
  await admin("insert into auth.users(id,raw_user_meta_data) values ($1,'{}')", [uid])
  const a = await issue(scopes, null, uid)
  const d = ok(await call(a, 'create_workout_draft', { idempotencyKey: 'approved', draft })).draft
  const tombstone = ok(await call(a, 'create_workout_draft', { idempotencyKey: 'accepted', draft })).draft
  ok(await review(d.id, d.contentHash, 'approve', uid))
  ok(await review(tombstone.id, tombstone.contentHash, 'approve', uid))
  ok(await review(tombstone.id, tombstone.contentHash, 'accept', uid))
  await admin(
    "update ironvellum_mcp.drafts set created_at=now()-interval '93 days',expires_at=now()-interval '63 days',changed_at=case when status='accepted' then now()-interval '20 days' else now()-interval '92 days' end where owner_id=$1",
    [uid],
  )
  await admin(
    "update ironvellum_mcp.grants set created_at=now()-interval '182 days',expires_at=now()-interval '92 days' where id=$1",
    [a.grantId],
  )
  ok(await gateway('prune'))
  await db.exec('reset role')
  assert.equal(await sql('select count(*)::int v from ironvellum_mcp.grants where id=$1', [a.grantId]), 0)
  await as('authenticated', uid)
  assert.equal(ok(await sql('select public.ironvellum_mcp_draft($1) v', [d.id])).draft.status, 'approved')
  assert.equal(
    ok(await sql('select public.ironvellum_mcp_draft($1) v', [tombstone.id])).draft.status,
    'accepted',
  )
  ok(await review(d.id, d.contentHash, 'accept', uid))
  const active = await issue(scopes, a.clientId, uid)
  ok(await call(active, 'list_workouts'))
  await as('authenticated', uid)
  await db.query('select public.delete_my_account()')
  await db.exec('reset role')
  for (const table of ['grants', 'drafts', 'audit'])
    assert.equal(
      await sql('select count(*)::int v from ironvellum_mcp.' + table + ' where owner_id=$1', [uid]),
      0,
    )
  assert.equal(
    await sql('select count(*)::int v from ironvellum_mcp.tokens where grant_id=$1', [active.grantId]),
    0,
  )
  err(await gateway('access.load', { accessHash: active.accessHash }), 'invalid_token')
})
async function race(role, statement, firstArgs, secondArgs) {
  const first = cluster.getPgClient('postgres', '127.0.0.1'),
    second = cluster.getPgClient('postgres', '127.0.0.1')
  try {
    await Promise.all([first.connect(), second.connect()])
    for (const connection of [first, second]) {
      await connection.query(
        "set lock_timeout='4s'; set statement_timeout='6s'; set idle_in_transaction_session_timeout='8s'",
      )
      await connection.query('set role ' + role)
      if (role === 'authenticated')
        await connection.query(
          "select set_config('probe.uid',$1,false), set_config('request.jwt.claims',$2,false)",
          [user, JSON.stringify({ sub: user })],
        )
    }
    await first.query('begin')
    ok((await first.query(statement, firstArgs)).rows[0].v)
    const contender = second.query(statement, secondArgs).then(
      (value) => ({ value }),
      (error) => ({ error }),
    )
    let blocked = false
    await db.exec('reset role')
    for (let i = 0; i < 100 && !blocked; i++) {
      blocked = await sql("select wait_event_type='Lock' v from pg_stat_activity where pid=$1", [
        second.processID,
      ])
      if (!blocked) await new Promise((r) => setTimeout(r, 10))
    }
    assert.equal(blocked, true, 'second independent backend must actually wait on the first transaction')
    await first.query('commit')
    const result = await contender
    if (result.error) throw result.error
    return result.value.rows[0].v
  } finally {
    try {
      if (first.processID) await first.query('rollback')
    } finally {
      await Promise.all([first.end(), second.end()])
    }
  }
}
if (native)
  test('the winning review transaction determines pending draft approval or rejection', async () => {
    const a = await issue()
    for (const first of ['approve', 'reject']) {
      const d = ok(await call(a, 'create_workout_draft', { draft, idempotencyKey: first })).draft
      const second = first === 'approve' ? 'reject' : 'approve'
      const result = await race(
        'authenticated',
        'select public.ironvellum_mcp_review_draft($1,$2,$3) v',
        [d.id, d.contentHash, first],
        [d.id, d.contentHash, second],
      )
      err(result, 'conflict')
      assert.equal(
        ok(await call(a, 'get_workout_draft', { id: d.id })).draft.status,
        first === 'approve' ? 'approved' : 'rejected',
      )
    }
  })
if (native)
  test('two live PostgreSQL connections serialize code and refresh replays and commit family revocation', async () => {
    const clientId = await register()
    for (const kind of ['code', 'refresh']) {
      let operation, initial
      if (kind === 'code') {
        const id = await request(clientId),
          codeHash = hash()
        ok(await consent(id, true, scopes, codeHash))
        operation = 'code.exchange'
        initial = {
          clientId,
          codeHash,
          resourceUrl,
          redirectUri,
          codeChallenge: challenge,
          accessHash: hash(),
          refreshHash: hash(),
        }
      } else {
        const a = await issue(scopes, clientId)
        operation = 'refresh.exchange'
        initial = {
          clientId,
          resourceUrl,
          refreshHash: a.refreshHash,
          newRefreshHash: hash(),
          accessHash: hash(),
          scopes,
        }
      }
      const repeated = {
        ...initial,
        accessHash: hash(),
        [kind === 'code' ? 'refreshHash' : 'newRefreshHash']: hash(),
      }
      const result = await race(
        'service_role',
        'select public.ironvellum_mcp_gateway($1,$2) v',
        [operation, initial],
        [operation, repeated],
      )
      err(result, 'invalid_grant')
      err(await gateway('access.load', { accessHash: initial.accessHash }), 'invalid_token')
    }
  })
test('replays baseline and hosted patch, runs existing schema/social assertions', async () => {
  await db.exec('reset role')
  await db.exec(baseline)
  const patch = await file(upgrade)
  assert.ok(baseline.replaceAll('\r\n','\n').endsWith(patch.replaceAll('\r\n','\n')))
  await db.exec(patch)
  await db.exec(patch)
  const assertions = await file('supabase/test/assert_all.sql')
  await db.exec(assertions.replace(/^\\set ON_ERROR_STOP 1\r?$/m, ''))
  assert.equal(await sql('select public.schema_version() v'), 36)
  for (const path of [
    'supabase/test/reset_seed.sql',
    'supabase/reset.sql',
    'supabase/test/assert_reset.sql',
  ]) {
    await db.exec((await file(path)).replace(/^\\set ON_ERROR_STOP 1\r?$/m, ''))
  }
  assert.equal(await sql("select to_regnamespace('ironvellum_mcp') v"), null)
  const oldSchema = baseline.split('-- MCP schema 36.')[0]
  await db.exec(oldSchema)
  await db.exec(await file('supabase/hosted/2026-11-20-rolling-lift-marks.sql'))
  assert.equal(await sql('select public.schema_version() v'), 35)
  await db.exec(patch)
  assert.equal(await sql('select public.schema_version() v'), 36)
  await db.exec(assertions.replace(/^\\set ON_ERROR_STOP 1\r?$/m, ''))
})
