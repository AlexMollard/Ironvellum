# Ironvellum production MCP

The standalone Python service provides seven tools for synced training reads and proposed saved workouts. User consent selects from `training:read`, `progress:read`, `savedworkouts:read` and `workoutdrafts:create`. The [wire contract](contracts/README.md) defines each tool, scope, error, quota and result.

## Install and run

For the non-root Linux/amd64 image, private operator jobs, HTTPS ingress, credential-free smoke checks and recovery procedures, see the [deployment guide](DEPLOYMENT.md).

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
| `IRONVELLUM_MCP_MANIFEST` | Path to the generated, version-1 Seed movement manifest with schemaVersion and movements |
| `IRONVELLUM_MCP_LOOPBACK` | Set to `1` explicitly to permit HTTP origins on localhost, 127.0.0.1 or [::1] during development |
| `IRONVELLUM_MCP_GOOGLE_CALLBACK` | Optional. Set to the issuer plus `/signin/callback` after enabling Google and allowlisting this callback on the backend. |

After applying schema 36 to an approved backend, use explicit setup to pin the canonical resource and encryption-key marker and install the manifest in one database transaction. Repeating the same configuration is safe. Startup and readiness compare the installed resource, key marker, catalogue count and catalogue fingerprint with the local settings.

```sh
ironvellum-production-mcp configure-backend
ironvellum-production-mcp serve --host 127.0.0.1 --port 8000
```

For a container, bind to its internal interface and terminate HTTPS at a trusted proxy. Preserve the canonical Host. The service rejects trailing-slash aliases for its endpoints. Keep query strings, headers and bodies out of proxy and application telemetry. The CLI disables HTTP access logging and SDK diagnostics. Starting the server performs read-only checks. A missing manifest or mismatch in the resource, key or catalogue prevents startup.

To change the catalogue deliberately, read `catalogueHash` from the private `configuration.get` response, verify the intended changes, point the manifest setting to the replacement, then run `ironvellum-production-mcp configure-backend --previous-catalogue-hash <observed-sha256>`. A concurrent change causes a conflict. Update serving replicas to the matching manifest.

Back up the Fernet key in a secret manager. If a replica has a different key, restore the established key. Both setup and readiness refuse a replacement key. For a database with legacy encrypted registrations and an unset verification marker, stop admission and drain old replicas. A reviewed recovery must authenticate every existing client record with the backed-up key and verify its client identity before binding that key. Rotation requires a coordinated offline re-encryption and marker update across all replicas. If the key is lost, old client secrets are unrecoverable. A separate operator-approved recovery must revoke affected grants, preserve user data, and have clients register and users reconnect. The configuration command cannot perform a destructive reset.

## Protocol and browser integration

- The resource is `<issuer>/mcp`. Unauthorized requests receive the protected-resource metadata challenge. Discovery lives at `/.well-known/oauth-protected-resource/mcp` and `/.well-known/oauth-authorization-server`.
- The official MCP 2.3.0 clients use the same streamable HTTP endpoint for the 2026-07-28 protocol and 2025-11-25 legacy handshake. The service also tests the SDK's OAuth client discovery, dynamic registration, S256 authorization and authenticated tool call.
- `POST /register` accepts public (`none`), `client_secret_basic` and `client_secret_post` clients. Omitted authentication method defaults to Post. Register 1–16 distinct canonical HTTPS or HTTP loopback callback URLs. Use a path such as `/callback`; the comparison covers the exact URL, including the port and query. Reserved OAuth response parameters, userinfo and fragments are rejected. Registration accepts optional name, software ID/version, native/web application type, scope, code response type and authorization_code/refresh_token grant types. URL, contact, key and logo metadata are rejected. Client names are unverified text.
- Use `GET` or form `POST /authorize` with `response_type=code`, explicit `resource=<issuer>/mcp`, S256 challenge and the exact redirect. A single registered redirect can be omitted. Authorization with omitted scopes requests the registered scopes, which default to the four supported scopes. The person chooses a subset before any grant is issued. `POST /token` checks PKCE; an explicit resource must match the bound resource exactly. Refresh can narrow the scope. `POST /revoke` authenticates the registered client and revokes its grant family. Token and revocation accept Basic with or without a matching form client ID.
- Authorization redirects the human browser to `/consent/{requestId}` and sets a 10-minute, HttpOnly, SameSite=Lax binding cookie. HTTPS uses a Secure, host-only cookie. Keep consent in the same browser and complete one outstanding consent at a time.
- The consent page reads `GET /consent/api/{requestId}` with same-origin cookies. Its response contains `requestId`, `clientName`, `scopes`, `expiresAtMs`, `backendUrl`, `publicKey` and `csrf`. Render client names as untrusted text. The browser signs in directly with the configured Supabase public key. The UI asset and route are supplied by the web consent implementation.
- On the person's decision, POST JSON to that same API path with exact Origin, the cookie, and `Authorization: Bearer <human session JWT>`. The body has exactly `approved` (boolean), `scopes` (a distinct requested subset, empty on denial) and `csrf`. On success, navigate the top-level browser to the returned `redirectUrl`. Treat it as sensitive. The callback contains the one-use code or access_denied and the original state. The human bearer goes only to the fixed consent RPC.
- `GET /healthz` returns 200 and `{"ready":true}` when the backend matches. Error responses contain stable classifications. Admission quota failures return HTTP 429. Tool errors return a structured MCP result with `isError`, `ok:false` and a safe classification. External bodies are capped at 64 KiB, backend calls at 10 seconds, and rendered tool results at 1 MiB.

The bundled pages also provide human connection management at `/connections`. The normal Supabase session stays in browser memory. Management calls the configured backend’s authenticated connections and revoke RPCs. It does not store a session across reloads.

Email/password sign-in uses the configured backend. For Google, configure its provider on that backend, allowlist exactly `<issuer>/signin/callback` there, then set `IRONVELLUM_MCP_GOOGLE_CALLBACK` to that URL. The Google button is hidden by default. The original tab holds an S256 verifier and the reference to the provider popup. Before exchanging its authorization code, consent checks that the original request binding is still current. Do not log callback query strings in the reverse proxy. Hosted provider allowlists, TLS and browser popup policies need deployment verification.

## Local HTTP verification

Install Node's locked SQL test dependencies as below and use the isolated service environment.

```sh
python -m pip install pytest==9.0.2
python -m pytest -q tests
python -m pip check
```

Transport tests exercise the actual SDK using ASGI and a deterministic gateway double. The separate network suite launches Uvicorn, the official SDK clients and a disposable native PostgreSQL cluster. A loopback HTTP bridge maps fixed PostgREST requests and synthetic human identities to the real SQL functions. It verifies two-owner isolation, durable restart, grants, rotation, quotas at the HTTP adapter, draft creation and revocation. Hosted Supabase Auth, hosted PostgREST, a public TLS deployment and third-party AI products require separate deployment verification.

For the browser check, build a wheel, install it and its runtime dependencies into a separate browser test environment, then install pytest and Playwright 1.65.2 there. With Microsoft Edge installed, run `python -I tests/browser/verify_ui.py --channel msedge` and `python -I tests/browser/verify_ui.py --channel msedge --management-race` from this directory. Pass `--captures <directory>` for screenshots. The check serves the installed wheel on loopback and supplies synthetic Auth, RPC and AI callback responses. It exercises keyboard consent, bundled fonts, expiry, denial, Google PKCE, credential handling and confirmed revocation, including late responses from a superseded view.

### SQL to Android receipt

With the service and pytest installed in the active test environment, the locked SQL dependencies installed, and emulator-5554 running, use these commands from the repository root on Windows. Run separately from the full gate.

```powershell
$env:ANDROID_SERIAL = "emulator-5554"
.\gradlew.bat -PmcpReceiptHost=true :app:assembleReceiptFixtureDebug :app:assembleReceiptFixtureDebugAndroidTest
python server/production_mcp/tests/verify_android_receipt.py
```

The standalone check uses a dedicated test-only package and UID on the emulator. Its target has the plain Android Application, no startup providers or other app components and no Internet permission. The test verifies those properties on-device before opening its isolated database. The ordinary Android gate runs the same test with its checked-in SQL recording and usual app runner.

The host configures the full catalogue, restarts the local service and creates a draft through an official SDK client. It passes the actual SQL pending and approved envelopes to the test under the shared emulator lock. A still-pending approval reply must leave zero new presets, zero receipts and zero acknowledgement calls. At the first accept request a separate read-only SQLite connection must see the committed, unscheduled preset and its matching pending receipt. The test interrupts acknowledgement, deletes the saved preset, reopens the database and retries. The host sends the captured app SDK body to the app review RPC twice and reads the accepted state through MCP. Output includes SHA-256 digests of the exchanged bytes. Hosted Auth, PostgREST and device-to-host HTTPS are separate checks.

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
