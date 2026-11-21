import assert from 'node:assert/strict'
import { randomBytes } from 'node:crypto'
import { mkdtemp, realpath, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { basename, dirname, join } from 'node:path'
import { createServer } from 'node:net'

export async function disposableNative() {
  const { default: Postgres } = await import('embedded-postgres')
  const parent = await realpath(tmpdir())
  const directory = await mkdtemp(join(parent, 'ironvellum-mcp-test-'))
  const socket = createServer()
  await new Promise((resolve) => socket.listen(0, '127.0.0.1', resolve))
  const port = socket.address().port
  await new Promise((resolve) => socket.close(resolve))
  const cluster = new Postgres({
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
    cluster,
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
