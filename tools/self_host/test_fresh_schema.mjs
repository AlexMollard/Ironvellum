// Run with node --test tools/self_host/test_fresh_schema.mjs after installing
// server/production_mcp's development dependencies. Each case owns a native cluster.
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { disposableNative } from '../../server/production_mcp/tests/sql/disposable-native.mjs'

const sqlFile = path => readFile(new URL(path, import.meta.url), 'utf8')
const python = process.platform === 'win32' ? 'python' : 'python3'
const sql = execFileSync(python, ['-c', 'import stack, sys; sys.stdout.buffer.write(stack.fresh_schema_sql().encode())'],
  { cwd: new URL('.', import.meta.url), encoding: 'utf8' })
const stub = await sqlFile('../../supabase/test/supabase_stub.sql')

async function freshDatabase(t) {
  const db = await disposableNative()
  t.after(() => db.close())
  await db.query(stub)
  await db.query('CREATE SCHEMA storage; CREATE TABLE storage.objects(id int); CREATE TABLE storage.buckets(id int)')
  return db
}

async function expectRefusal(db) {
  await assert.rejects(db.query(sql), /Fresh setup requires an empty application database/)
  await db.query('ROLLBACK')
  assert.equal((await db.query("SELECT to_regclass('public.profiles') AS app")).rows[0].app, null)
}

test('a clean stack preserves service and extension members across baseline installation', async t => {
  const db = await freshDatabase(t)
  await db.query(`
    CREATE SCHEMA _realtime;
    CREATE TABLE _realtime.schema_migrations(id int);
    INSERT INTO _realtime.schema_migrations VALUES (7);
    CREATE SCHEMA pgbouncer;
    CREATE FUNCTION pgbouncer.get_auth(text) RETURNS int LANGUAGE sql AS $$SELECT 11$$;
    CREATE FUNCTION public.platform_member() RETURNS int LANGUAGE sql AS $$SELECT 42$$;
    ALTER EXTENSION pgcrypto ADD FUNCTION public.platform_member();
  `)
  await db.query(sql)
  assert.deepEqual((await db.query(`
    SELECT schema_version() AS version, platform_member() AS member, pgbouncer.get_auth('test') AS pooler, id
    FROM _realtime.schema_migrations
  `)).rows, [{ version: 36, member: 42, pooler: 11, id: 7 }])
  await assert.rejects(db.query(sql), /Fresh setup requires an empty application database/)
  await db.query('ROLLBACK')
  assert.equal((await db.query('SELECT schema_version() AS version')).rows[0].version, 36)
})

test('refuses a populated private application table and keeps its rows', async t => {
  const db = await freshDatabase(t)
  await db.query('CREATE SCHEMA personal; CREATE TABLE personal.training(id int); INSERT INTO personal.training VALUES (17)')
  await expectRefusal(db)
  assert.deepEqual((await db.query('SELECT id FROM personal.training')).rows, [{ id: 17 }])
})

test('refuses an existing signup trigger even when its routine is in a service schema', async t => {
  const db = await freshDatabase(t)
  await db.query(`
    CREATE FUNCTION auth.original_signup() RETURNS trigger LANGUAGE plpgsql AS $$BEGIN RETURN NEW; END$$;
    CREATE TRIGGER on_auth_user_created AFTER INSERT ON auth.users FOR EACH ROW EXECUTE FUNCTION auth.original_signup()
  `)
  const definitions = `
    SELECT pg_get_triggerdef(oid) AS trigger, pg_get_functiondef(tgfoid) AS routine FROM pg_trigger
    WHERE tgrelid = 'auth.users'::regclass AND tgname = 'on_auth_user_created'
  `
  async function checkOriginalSignup() {
    const before = (await db.query(definitions)).rows
    assert.equal(before.length, 1)
    await expectRefusal(db)
    assert.deepEqual((await db.query(definitions)).rows, before)
  }
  await checkOriginalSignup()
  await db.query('CREATE SCHEMA personal; ALTER FUNCTION auth.original_signup() SET SCHEMA personal')
  await checkOriginalSignup()
})

test('existing users, storage or public application objects still prevent a fresh install', async t => {
  const db = await freshDatabase(t)
  for (const [setup, cleanup] of [
    ['INSERT INTO auth.users(id) VALUES(gen_random_uuid())', 'DELETE FROM auth.users'],
    ['INSERT INTO storage.objects VALUES (1)', 'DELETE FROM storage.objects'],
    ['INSERT INTO storage.buckets VALUES (1)', 'DELETE FROM storage.buckets'],
    ['CREATE TABLE public.existing(id int)', 'DROP TABLE public.existing'],
    ['CREATE FUNCTION public.existing() RETURNS int LANGUAGE sql AS $$SELECT 1$$', 'DROP FUNCTION public.existing()'],
    ["CREATE TYPE public.existing AS ENUM ('original')", 'DROP TYPE public.existing'],
  ]) {
    await db.query(setup)
    await expectRefusal(db)
    await db.query(cleanup)
  }
})
