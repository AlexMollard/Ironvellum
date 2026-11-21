# Ironvellum production MCP

## Local SQL verification

From the repository root, install the locked test dependencies, then run the disposable SQL suites.

```sh
npm ci --prefix server/production_mcp/tests/sql
npm test --prefix server/production_mcp/tests/sql
npm run test:native --prefix server/production_mcp/tests/sql
npm audit --prefix server/production_mcp/tests/sql
```

The first suite executes the real baseline, hosted upgrade, reset and existing app/social assertions in in-memory PGlite 0.5.17 with pgcrypto and the repository's auth stub. The native suite starts its own PostgreSQL 16.16 cluster on a temporary directory and a random IPv4 loopback port. It additionally drives parallel code and refresh exchanges through separate backend connections, observes an actual lock wait and verifies the second committed result has revoked the first credential family. Both use generated test identities. Neither command accepts a production database URL. The native binary is pinned to the repository's PostgreSQL 16 major through embedded-postgres 16.16.0-beta.19. [PGlite documentation](https://pglite.dev/docs/) and [Embedded PostgreSQL](https://github.com/leinelissen/embedded-postgres)

The SQL suite verifies role privileges, PostgREST-style claims, projections, limits, consent, opaque digest exchange and draft state changes. Hosted GoTrue, PostgREST and HTTPS client interoperability require integration checks in the service environment.

## Operator prerequisites

Apply the [schema 35 to 36 migration](../../supabase/hosted/2026-11-22-production-mcp.sql) on an operator-approved backend. Configure the canonical resource and install the versioned built-in movement catalogue through the [private gateway](contracts/README.md). The fresh database contains no catalogue entries. The service needs a persistent encryption key for client metadata and a private gateway credential. PostgreSQL grants gateway execution to service_role by default. A Supabase service_role credential carries broad project privileges. Where a narrower PostgREST service identity is available, grant only schema public usage and execution of public.ironvellum_mcp_gateway(text,jsonb) to that role.

The unauthenticated compatibility beacon advertises schema 36. Production MCP and workout-draft review need schema 36. Existing core app sync continues to require schema 31. Public hosting, service origin, browser human sign-in and scheduled calls to prune must be configured for the deployment.
