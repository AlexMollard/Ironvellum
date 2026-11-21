# Deploying the production MCP service

An operator needs an approved Linux/amd64 container host, a DNS name and certificate for its canonical HTTPS origin, an HTTPS Supabase backend at schema 36, a secret store and a private scheduled-job facility. Allocate one service origin per backend. Record the source revision, image ID/digest, schema migration, catalogue fingerprint and secret **version references** in the deployment record. The public client address becomes the chosen origin plus `/mcp`.

## Build and provision

From `server/production_mcp`, on a Docker-compatible Linux builder:

```sh
docker build --platform linux/amd64 -t ironvellum-mcp:verified .
docker image inspect --format '{{.Id}} {{.Config.User}} {{json .Config.Healthcheck.Test}}' ironvellum-mcp:verified
```

The Dockerfile pins the Python 3.13.18 Debian base by digest. The build fetches the exact runtime and setuptools wheels, verifies every filename and SHA-256 against `linux-amd64-wheels.sha256`, builds the service wheel offline and checks the installed dependencies. The final stage copies a virtual environment and the Seed manifest. The entry point runs as UID/GID 65532. Tests, node modules and operator environment files are excluded from the build context. When updating a pin, review the base and dependencies, refresh the Linux wheel hashes from the published artifacts and repeat the build, image scan and checks. Publish by immutable image digest after verification on the target host.

1. Back up the backend, including the private MCP schema and grants, and confirm that the backup can be restored in isolation. On an approved schema-35 database, apply the hosted 35→36 migration. For a fresh backend use the repository's normal baseline installation. The app's core sync minimum remains 31. Preserve existing app and social access. [Hosted migration](../../supabase/hosted/2026-11-22-production-mcp.sql)
2. Fill the settings from the environment example in the host's secret manager. For a local operator using Docker, place the filled env file **outside the checkout**, readable only by the operator, and set `MCP_ENV_FILE` to its path. Keep inspection of process environments, service manifests and containers restricted. Use the manager's generator for 32 random bytes, base64url encoded with padding, as the persistent Fernet key. Share the same key version across all replicas and retain a recoverable encrypted backup. [Environment example](.env.example)
3. Supply the backend's publishable key or legacy anon JWT for human sign-in. Use a separate private server credential accepted by the fixed gateway. A default Supabase service-role/secret key has broad project authority if stolen. Where supported, provision a dedicated PostgREST role with public schema usage and execute on `public.ironvellum_mcp_gateway(text,jsonb)`. Keep private credentials out of browser, mobile, AI tools, URLs, arguments and logs. The browser signs in directly to the one configured backend. Permit that issuer origin on the backend for the intended browser requests. For optional Google sign-in, enable its provider there, allowlist exactly the issuer plus `/signin/callback`, then set `IRONVELLUM_MCP_GOOGLE_CALLBACK` to that value.
4. Run the one-shot configuration job with the same image and environment as the service. The job atomically binds the resource and persistent key marker and installs the full movement catalogue. A second identical run verifies it safely. The initial manifest contains 584 movements with fingerprint `6b8bea45f8781884088d99fe8fc76c0df4c5755a8c2d1e9914629710bfa18a66`.

```sh
docker run --rm --read-only --cap-drop=ALL --security-opt no-new-privileges \
  --env-file "$MCP_ENV_FILE" ironvellum-mcp:verified configure-backend
docker run --rm --read-only --cap-drop=ALL --security-opt no-new-privileges \
  --env-file "$MCP_ENV_FILE" ironvellum-mcp:verified catalogue-status
docker run -d --name ironvellum-mcp --restart unless-stopped --read-only \
  --cap-drop=ALL --security-opt no-new-privileges --env-file "$MCP_ENV_FILE" \
  -p 127.0.0.1:8000:8000 ironvellum-mcp:verified
docker inspect --format '{{.State.Health.Status}}' ironvellum-mcp
```

Keep `IRONVELLUM_MCP_LOOPBACK=0` in production. Only the private proxy reaches the published loopback port in this layout. Startup and readiness attest the configured resource, key marker, catalogue count and fingerprint against the backend. The container probe sends the canonical Host to `127.0.0.1:8000/healthz` over HTTP. Allow at least 12 seconds for a probe and mark the replica unavailable on repeated failure. Liveness can monitor the service process independently during a backend outage.

## HTTPS ingress and smoke

Route only the configured public Host over TLS. Reject unknown hosts and cleartext requests without forwarding them. Retain the unmodified path/query and send the exact configured Host, including its port if non-default. Stream MCP responses. Drop incoming forwarded headers. The process does not consume proxy headers to decide its issuer, allowed origin or redirect. Preserve Authorization, Cookie and Origin on intended routes and enforce a 64 KiB request body limit. Only the issuer is an allowed browser Origin at the MCP transport. Native clients may omit Origin. Preserve the application's CSP, no-referrer and no-store responses. Disable caching on every route.

Canonical HTTPS origins and callbacks can use a bracketed IPv6 address, such as `https://[2001:db8::17]:8443`. Use the literal ASCII path for each service endpoint. The service rejects encoded path aliases before routing. Query parameters retain their normal percent encoding.

Registration admission has a hard ceiling of 10,000 stored clients and rolling limits of 20 per minute and 1,000 per day. Run the bounded prune operation regularly. Each pass can reclaim up to 200 registrations at least 24 hours old that have never consented and have no retained requests, grants, drafts or audit records. An abandoned client removed this way needs to register again. Existing connections keep working at capacity. Previously consented clients and pre-upgrade clients with unknown history remain until operator-reviewed maintenance.

For example, in a dedicated Nginx HTTPS virtual host with certificates and exact server name already set by the operator:

```nginx
access_log off;
error_log /dev/null;
client_max_body_size 64k;
location / {
    proxy_pass http://127.0.0.1:8000;
    proxy_http_version 1.1;
    proxy_set_header Host $http_host;
    proxy_set_header Connection "";
    proxy_set_header Forwarded "";
    proxy_set_header X-Forwarded-For "";
    proxy_set_header X-Forwarded-Host "";
    proxy_set_header X-Forwarded-Proto "";
    proxy_buffering off;
    proxy_cache off;
}
```

Configure a separate rejecting default virtual host. Disable request, upstream-error, header, query and body capture at the load balancer, CDN, tracing agent and error-reporting layers, including rejected requests and sign-in callbacks. Use aggregate response classes and availability metrics. The CLI disables access logs. The SQL audit records safe IDs, operation, time and classification. Do not enable SDK/HTTP debug logging.

On a client network with trusted public DNS and CA roots, set only `IRONVELLUM_MCP_ISSUER` to the approved HTTPS origin. Run the installed CLI or use the same image on that network:

```sh
docker run --rm --read-only --cap-drop=ALL --security-opt no-new-privileges \
  -e IRONVELLUM_MCP_ISSUER ironvellum-mcp:verified smoke
```

Exit zero checks readiness, both root discovery documents, the exact resource and token endpoint, and the unauthenticated MCP challenge. The probe issues no grant and follows no redirect. Separately verify approved test accounts through the hosted Auth/PostgREST flow, Google popup if enabled, and the specific AI client the deployment intends to support. Record the client/version and protocol it actually negotiates. Official SDK 2.3.0 modern and legacy streamable HTTP are exercised locally. [Local verification](README.md#local-http-verification)

## Maintenance and recovery

- **Cleanup.** Schedule one private `prune` command, for example every five minutes. Use the same image/environment and restrictions as the configure job. Each call checks deployment identity and the catalogue first, then invokes the fixed `prune` gateway operation once. It prints only `{"affected":N}`. SQL processes at most 200 rows per category per invocation. If batches remain full, schedule bounded extra runs with a delay. Monitor repeated failures and backlog. Audits last 30 days and expose the latest 50 entries. Terminal draft tombstones last at least 90 days after transition. Approved drafts remain resumable.
- **Traffic.** SQL limits registrations to 20/minute and 1,000/day. Authorization admission is 20/client/minute and 120/service/minute. Each owner/client receives 120 tool calls/minute and 20 new drafts/day. Use 429 and Retry-After to back off. Time and size limits are 10 seconds for backend calls and 1 MiB for results. Protect ingress with host-level connection/time limits sized for the deployment. [Contract](contracts/README.md)
- **Catalogue update.** Drain serving replicas. Obtain the current count/hash with `catalogue-status` using the established resource and key. Inspect the replacement generated by the app. Run the replacement image's `configure-backend --previous-catalogue-hash <observed-sha256>` against the same backend. Verify the new count/hash and readiness, then switch traffic. A concurrent different update causes a conflict. An already-applied retry succeeds. Startup is read-only. Switching to an older catalogue also requires an intentional CAS from the observed installed hash.
- **Keys.** Rotate a backend gateway credential through the provider, deploy the new secret to all operators and replicas, verify readiness and retire the old credential. If a broad service credential was exposed, handle it as a backend-wide incident. A different Fernet key or an unbound legacy database with existing encrypted registrations fails closed. Restore the matching key from the secret manager. Change it only in a reviewed maintenance window with admission stopped and all replicas drained. Offline recovery must authenticate existing ciphertext and client identities, re-encrypt with the new key and update the marker atomically before starting replicas. No key-replacement or legacy-adoption command is exposed. On key loss, retain owner data and receipts, revoke affected grants under a reviewed recovery, and arrange client re-registration and new consent.
- **Revocation.** Owners can disconnect in Account → AI connections or at `/connections`. Registered clients can revoke at the token endpoint's companion `/revoke` route. Revocation blocks future access immediately. It cannot recall data the client has received. One-use code/refresh replay revokes the grant family. Access lasts up to 10 minutes, refresh inactivity up to 30 days, and the grant up to 90 days.
- **Backup and restore.** Back up schema, role grants, encrypted client metadata, key-version references, grants, token-family digests, audit, drafts and tombstones together. Retain the matching manifest and image digest. Restrict and encrypt backups, including restore copies. Test in isolation, with outbound access and public traffic disabled. A point-in-time restore can revive a revoked connection, spent code or pending draft. Reconcile later revocations, consumed credentials and terminal review states from trusted recovery evidence before reopening. If credential history is uncertain, invalidate restored grants and pending authorizations and require fresh consent. Keep draft review closed where a safe state cannot be established. Device-local receipts stay with their local database and survive deletion of their preset.
- **Rollback.** Stop public routing first. An earlier known-good image can resume if its manifest, schema, issuer and key match current state. Use the catalogue CAS procedure for a deliberate manifest rollback. Leave additive schema 36 and owner rows in place when disabling the service or rolling the app back. Never use the repository's disposable SQL reset on a real backend. Verify readiness, discovery, revocation state and a scoped test account before restoring traffic.
