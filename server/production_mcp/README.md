# Ironvellum production MCP

The standalone Python service provides seven tools for synced training reads and proposed saved workouts. User consent selects from `training:read`, `progress:read`, `savedworkouts:read` and `workoutdrafts:create`. The [wire contract](contracts/README.md) defines each tool, scope, error, quota and result.

## Install and run

Use CPython 3.11 or later and a separate virtual environment. From `server/production_mcp`, activate that environment and install the pinned runtime and package.

```sh
python -m pip install -r requirements.lock
python -m pip install --no-deps .
```

Set the [example environment variables](.env.example) through your process environment or host secret manager. The command reads process variables only.

| Variable | Value |
| --- | --- |
| `IRONVELLUM_MCP_ISSUER` | Canonical public HTTPS origin, without a trailing slash or path |
| `IRONVELLUM_MCP_BACKEND_URL` | Single configured Supabase HTTPS origin |
| `IRONVELLUM_MCP_PUBLIC_KEY` | Browser-safe Supabase publishable key or legacy anon-role JWT |
| `IRONVELLUM_MCP_GATEWAY_KEY` | Private gateway-role credential. The default schema grant uses service_role. |
| `IRONVELLUM_MCP_ENCRYPTION_KEY` | Persistent Fernet key, generated from 32 random bytes, base64url encoded. Store in the host secret manager and retain across restarts and replicas. |
| `IRONVELLUM_MCP_MANIFEST` | Path to the generated, version-1 Seed movement manifest in the contract's catalogue.put format |
| `IRONVELLUM_MCP_LOOPBACK` | Set to `1` explicitly to permit HTTP origins on localhost, 127.0.0.1 or [::1] during development |

After applying schema 36 to an approved backend, install the manifest and pin the canonical resource. Repeating configuration with the same deployment is supported. The service and health check compare both the count and fingerprint of the installed catalogue against the local manifest.

```sh
ironvellum-production-mcp configure-backend
ironvellum-production-mcp serve --host 127.0.0.1 --port 8000
```

For a container, bind to its internal interface and terminate HTTPS at a trusted proxy. Preserve the canonical Host. Keep query strings, headers and bodies out of proxy and application telemetry. The CLI disables HTTP access logging and SDK diagnostics. Starting the server performs read-only checks. A missing manifest or mismatched database configuration prevents startup. Keep the encryption key stable. A different key prevents previously registered clients from authenticating and invalidates browser consent cookies.

## Protocol and browser integration

- The resource is `<issuer>/mcp`. Unauthorized requests receive the protected-resource metadata challenge. Discovery lives at `/.well-known/oauth-protected-resource/mcp` and `/.well-known/oauth-authorization-server`.
- The official MCP 2.3.0 clients use the same streamable HTTP endpoint for the 2026-07-28 protocol and 2025-11-25 legacy handshake. The service also tests the SDK's OAuth client discovery, dynamic registration, S256 authorization and authenticated tool call.
- `POST /register` accepts public (`none`), `client_secret_basic` and `client_secret_post` clients. Omitted authentication method defaults to Post. Register 1–16 distinct canonical HTTPS or HTTP loopback callback URLs. Use a path such as `/callback`; the comparison covers the exact URL, including the port and query. Reserved OAuth response parameters, userinfo and fragments are rejected. Registration accepts optional name, software ID/version, native/web application type, scope, code response type and authorization_code/refresh_token grant types. URL, contact, key and logo metadata are rejected. Client names are unverified text.
- Use `GET` or form `POST /authorize` with `response_type=code`, explicit `resource=<issuer>/mcp`, S256 challenge and the exact redirect. A single registered redirect can be omitted. Authorization with omitted scopes requests the registered scopes, which default to the four supported scopes. The person chooses a subset before any grant is issued. `POST /token` checks PKCE; an explicit resource must match the bound resource exactly. Refresh can narrow the scope. `POST /revoke` authenticates the registered client and revokes its grant family. Token and revocation accept Basic with or without a matching form client ID.
- Authorization redirects the human browser to `/consent/{requestId}` and sets a 10-minute, HttpOnly, SameSite=Lax binding cookie. HTTPS uses a Secure, host-only cookie. Keep consent in the same browser and complete one outstanding consent at a time.
- The consent page reads `GET /consent/api/{requestId}` with same-origin cookies. Its response contains `requestId`, `clientName`, `scopes`, `expiresAtMs`, `backendUrl`, `publicKey` and `csrf`. Render client names as untrusted text. The browser signs in directly with the configured Supabase public key. The UI asset and route are supplied by the web consent implementation.
- On the person's decision, POST JSON to that same API path with exact Origin, the cookie, and `Authorization: Bearer <human session JWT>`. The body has exactly `approved` (boolean), `scopes` (a distinct requested subset, empty on denial) and `csrf`. On success, navigate the top-level browser to the returned `redirectUrl`. Treat it as sensitive. The callback contains the one-use code or access_denied and the original state. The human bearer goes only to the fixed consent RPC.
- `GET /healthz` returns 200 and `{"ready":true}` when the backend matches. Error responses contain stable classifications. Admission quota failures return HTTP 429. Tool errors return a structured MCP result with `isError`, `ok:false` and a safe classification. External bodies are capped at 64 KiB, backend calls at 10 seconds, and rendered tool results at 1 MiB.

## Local HTTP verification

Install Node's locked SQL test dependencies as below and use the isolated service environment.

```sh
python -m pip install pytest==9.0.2
python -m pytest -q tests
python -m pip check
```

Transport tests exercise the actual SDK using ASGI and a deterministic gateway double. The separate network suite launches Uvicorn, the official SDK clients and a disposable native PostgreSQL cluster. A loopback HTTP bridge maps fixed PostgREST requests and synthetic human identities to the real SQL functions. It verifies two-owner isolation, durable restart, grants, rotation, quotas at the HTTP adapter, draft creation and revocation. Hosted Supabase Auth, hosted PostgREST, a public TLS deployment and third-party AI products require separate deployment verification.

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
