/** Disposable loopback stand-in for PostgREST and identity verification. The RPCs are real PostgreSQL. */
import { createServer } from 'node:http'
import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { createInterface } from 'node:readline'
import { disposableNative } from './disposable-native.mjs'

const db = await disposableNative()
const file = (path) => readFile(new URL('../../../../' + path, import.meta.url), 'utf8')
const users = new Map([['human.test.session', randomUUID()], ['other.test.session', randomUUID()]])
const resourceUrl = process.argv[2]
// The bridge always listens on loopback. The documentation IPv6 address is an
// advertised TLS origin in the reverse-proxy fixture, never a network target.
if (!/^http:\/\/127\.0\.0\.1:\d+\/mcp$/.test(resourceUrl) && resourceUrl !== 'https://[2001:db8::17]:8443/mcp')
  throw new Error('Expected a local fixture resource')
let server
try {
  await db.exec(await file('supabase/test/supabase_stub.sql'))
  await db.exec(await file('supabase/migrations/0001_baseline.sql'))
  for (const [identity,uid] of users) {
    await db.query("insert into auth.users(id,raw_user_meta_data) values ($1,'{}')", [uid])
    await db.query("insert into sessions(id,user_id,local_id,label,note,started_at,completed_at) values(gen_random_uuid(),$1,1,$2,'private-canary-do-not-return',now(),now())",
      [uid, identity === 'human.test.session' ? 'Training' : 'Other workout'])
  }

  server = createServer(async (req,res) => {
    const chunks = []
    for await (const chunk of req) chunks.push(chunk)
    const body = JSON.parse(Buffer.concat(chunks))
    // Always isolate and commit one RPC, even when SQL returns an error classification.
    const conn = db.cluster.getPgClient('postgres', '127.0.0.1')
    await conn.connect()
    try {
      await conn.query('begin')
      let result
      if (req.method === 'POST' && req.url === '/rest/v1/rpc/ironvellum_mcp_gateway'
          && req.headers.apikey === 'private-test' && req.headers.authorization === 'Bearer private-test') {
        await conn.query('set local role service_role')
        result = await conn.query("select public.ironvellum_mcp_gateway($1,$2::jsonb) v", [body.p_operation,JSON.stringify(body.p_args)])
      } else if (req.method === 'POST' && req.url === '/rest/v1/rpc/ironvellum_mcp_consent'
          && req.headers.apikey === 'sb_publishable_test' && users.has(req.headers.authorization?.slice(7))) {
        const uid = users.get(req.headers.authorization.slice(7))
        await conn.query("select set_config('probe.uid',$1,true), set_config('request.jwt.claims',$2,true)", [uid,JSON.stringify({sub:uid})])
        await conn.query('set local role authenticated')
        result = await conn.query("select public.ironvellum_mcp_consent($1,$2,$3,$4) v",
          [body.p_request_id,body.p_approved,body.p_scopes,body.p_code_hash])
      } else {
        res.writeHead(403).end()
        return
      }
      await conn.query('commit')
      res.writeHead(200,{'Content-Type':'application/json'}).end(JSON.stringify(result.rows[0].v))
    } catch {
      await conn.query('rollback')
      res.writeHead(500).end()
    } finally {
      await conn.end()
    }
  })
  await new Promise((resolve) => server.listen(0,'127.0.0.1',resolve))
  console.log('http://127.0.0.1:' + server.address().port)
  for await (const command of createInterface({input:process.stdin})) {
    if (command === 'expire-codes') {
      await db.query("update ironvellum_mcp.tokens set expires_at=now()-interval '1 minute' where kind='code' and not used")
      console.log('expired')
    } else {
      // Host-only fixture exchange. Run only the two fixed phone RPCs as an ordinary test owner.
      const { actor, action, body } = JSON.parse(command)
      if (!['owner','other'].includes(actor)) throw new Error('Unknown fixture identity')
      const uid = users.get(actor === 'owner' ? 'human.test.session' : 'other.test.session')
      if (action === 'identity') {
        console.log(JSON.stringify({ userId: uid }))
        continue
      }
      await db.query('begin')
      try {
        await db.query("select set_config('probe.uid',$1,true), set_config('request.jwt.claims',$2,true)",
          [uid, JSON.stringify({ sub: uid })])
        await db.query('set local role authenticated')
        let result
        if (action === 'detail' && Object.keys(body).sort().join() === 'p_draft_id') {
          result = await db.query("select public.ironvellum_mcp_draft($1) v", [body.p_draft_id])
        } else if (action === 'review' && ['approve','accept'].includes(body.p_action)
            && Object.keys(body).sort().join() === 'p_action,p_draft_id,p_hash') {
          result = await db.query("select public.ironvellum_mcp_review_draft($1,$2,$3) v",
            [body.p_draft_id, body.p_hash, body.p_action])
        } else {
          throw new Error('Unsupported fixture command')
        }
        await db.query('commit')
        console.log(JSON.stringify(result.rows[0].v))
      } catch (error) {
        await db.query('rollback')
        throw error
      }
    }
  }
} finally {
  if (server) {
    server.closeAllConnections()
    await new Promise((resolve) => server.close(resolve))
  }
  await db.close()
}
