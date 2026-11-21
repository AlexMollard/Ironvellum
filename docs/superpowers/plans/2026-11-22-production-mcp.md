# Production MCP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a person grant their AI narrow read access and create workout drafts
which they explicitly review and save in Ironvellum.

**Architecture:** A separate Python ASGI service handles standards-based OAuth
and modern/legacy HTTP MCP using the official SDK. Narrow Supabase RPCs persist
clients, grants, token digests and drafts and derive account ownership. Android
reviews drafts and commits new presets plus durable receipts atomically.

**Tech Stack:** Python 3.11+, mcp==2.3.0, SDK ASGI/OAuth APIs, encrypted client
metadata, Supabase/Postgres, Kotlin/Room/Compose. PGlite fallback for disposable
SQL tests; production remains Supabase/Postgres. No new paid service assumed.

**Spec:** docs/superpowers/specs/2026-11-22-production-mcp-design.md

## Global Constraints

- Scopes: training:read, progress:read, savedworkouts:read, workoutdrafts:create.
- Opaque 256-bit secrets; SHA-256 token/code digests only; client secrets encrypted.
- Auth request 10 min, code 2 min, access 10 min, refresh inactivity 30 days,
  absolute grant 90 days; draft expiry 30 days; audit retention 30 days/latest 50.
- Strict DraftV1: one unscheduled new workout, name 1..80, entries 1..20, built-in
  REPS only, sets 1..50, reps 1..1000, finite nullable load 0..1000 kg, AUTO/NONE
  preparation. Reject unsupported/unknown fields and ambiguous names.
- Max request 64 KiB/result 1 MiB/backend deadline 10 sec/history page 50/search 20.
- Persisted quotas: 120 tool calls per user/client/min; 20 new drafts per
  user/client/day; registration 20/min and 1,000/day service-wide.
- Additive schema 36; existing normal app/social access unchanged. App global
  minimum schema stays 31. No development MCP imports or arbitrary SQL/URL tools.
- Show actual-app-style mockups before UI source. Copy in string resources,
  plain/themed wording and existing components. No extra Settings hub row.
- All shell commands rtk. No phone instrumentation/clear/uninstall. Emulator
  access only through main-checkout lock; full local gate alone and <600 sec.
- Commit/push verified units to main authorized by AGENTS. No force/tags/releases,
  workflow triggers, account creation, subscriptions or unreviewed cloud changes.

## Review Focus

1. Consent/resource/scope and direct-RPC bypass: revoked or ordinary app tokens
   cannot call tools, MCP credentials cannot review or alter unrelated data.
2. Concurrent code/refresh exchanges: one winner, committed replay revocation,
   no scope expansion, failure after database commit does not mint duplicates.
3. Unknown/colliding/timed/custom movements and future draft versions: reject
   the entire proposed import; never silently drop or coerce unsupported content.
4. Account A->B->A/backend changes and network loss after local save: no stale
   publication/import, no duplicate presets, receipts survive preset deletion.
5. Partial/stale cloud data and nested private canaries: explicit freshness and
   absence, bounded projections, no archive/notes/health/social leakage.

## Ownership and sequence

Serial implementers, fresh scoped review after each task. Coordinator alone owns
Git mutations except the active implementer's explicit verified commit. Root owns
final gate/device work. Reports/briefs/review packages belong under this plan's
ignored .superpowers/sdd workspace. Hosting answer can change deployment files,
not the permission/draft contracts. Do not pause between implementation tasks.

### Task 1: Durable capability boundary and shared wire contracts

**Files:**
- Create: supabase/hosted/upgrade-35-to-36.sql (follow actual house naming if needed).
- Modify: supabase/migrations/0001_baseline.sql, supabase/test/assert_all.sql,
  relevant hosted replay checks, supabase/SELF_HOSTING.md.
- Create: server/production_mcp/contracts/README.md, fixtures.json,
  server/production_mcp/tests/sql/{package.json,package-lock.json,capabilities.test.mjs}.
- Create: server/production_mcp/README.md with the exact local SQL check command.

**Interfaces:**
- Produce private `ironvellum_mcp_gateway(p_operation text,p_args jsonb) -> jsonb`
  and the six app RPCs named exactly in the spec. Publish versioned JSON shapes
  and the entire operation dictionary before commit; it is the later tasks' API.
- Gateway operations: configuration.get/configure; client.get/register;
  authorization.create/get; code.load/exchange; access.load; refresh.load/exchange;
  token.revoke; tool.call; catalogue.put; prune. Explicitly document each input
  and safe success/failure JSON; errors from replay must preserve revocation.
- App envelope camelCase: draft id/revision/contentHash/clientName/createdAtMs/
  expiresAtMs/status/draft, connections resourceUrl/connections/recentAccess.
  Common outer schemaVersion=1. Status pending/approved/accepted/rejected/expired.
  Review actions approve/accept/reject: pending approval and rejection race under
  row lock; approval wins before local commit, accepted ack wins afterward. A
  pending expired/rejected draft cannot approve; approved never expires/rejects.
- Produce fixed tool projections for seven names in spec. `tool.call` takes
  access-token digest plus tool name/typed args; DB derives principal itself.

- [ ] Write SQL tests first: anonymous/authenticated/foreign-owner/delegated
  client denied on private gateway; direct-app owner operations succeed; exact
  consent scopes and code binding; expiry; replay/refresh race and committed
  family revocation; no related account deletion/social/archive permissions.
- [ ] Add quota/idempotency tests, private canaries at nested fields, missing/
  stale/unsupported snapshots and history cursor ordering/owner checks. Use at
  least two users and service-role/auth claim fixtures. Pin transaction rollback
  behavior: a reported invalid reuse still leaves its grant revoked.
- [ ] Run new tests RED, documenting the missing schema/API. Use real disposable
  SQL via existing Docker harness when available; otherwise pinned PGlite and
  auth stub, reporting differences honestly. Do not query live rows.
- [ ] Implement private schema, RLS/grants/search_path, atomic gateway and app
  RPCs, bounds, schema 36 baseline + idempotent hosted upgrade. Include cleanup
  retention. Catalogue test fixture may use a few real built-ins; production
  catalogue is generated from Seed in Task 3, never a fake fallback.
- [ ] Run focused SQL GREEN, baseline replay + upgrade replay + old app/social
  assertions. Review direct table/function grants. Commit explicit files and
  report exact APIs, commands/counts and remaining deployment prerequisites.

### Task 2: Production OAuth and HTTP MCP service

**Files:**
- Create: server/production_mcp/{pyproject.toml,.gitignore,.env.example}.
- Create: src/ironvellum_production_mcp/{__init__,__main__,config,backend,oauth,
  models,tools,server,consent_api}.py and focused tests. Split further if needed.
- Modify: server/production_mcp/README.md; consume Task 1 contracts.

**Interfaces:**
- `create_app(settings, backend) -> ASGI app`; `SupabaseBackend` calls only the
  configured auth/RPC endpoints with deadlines/bounds; no caller-supplied URL.
- Official SDK OAuthAuthorizationServerProvider methods use the gateway
  operations. Backend consent delegates the human bearer only to the configured
  consent RPC; AI opaque bearer is never forwarded to Supabase.
- `/mcp` modern/legacy HTTP, root RFC8414 and protected-resource discovery,
  SDK authorize/token/register/revoke routes; `/healthz`; consent request view
  and POST decision APIs. UI assets are added in Task 4, not placeholder success.
- Strict Python DraftV1/envelope/tool models mirror fixtures. CLI serve and
  explicit configure-backend command; startup never silently mutates production.

- [ ] Write failing tests for actual SDK HTTP clients modern+legacy, unauthenticated
  challenge/discovery, all seven tools and scopes, app JWT denied, revoked access,
  wrong resource, unknown args, safe structured errors and canary redaction.
- [ ] Test actual SDK OAuth routes: S256/exact redirects, code expiry/reuse,
  resource binding, narrower consent, refresh scope escalation/replay, public and
  supported confidential clients, registration quotas/unsafe metadata/no fetch,
  no-store/redaction and consent origin/CSRF/request tampering. Use deterministic
  fake backend for transport tests; do not present it as SQL verification.
- [ ] Run RED; implement SDK-backed provider + fixed-RPC adapter, encrypted client
  metadata, token hashing, strict configured origins and bounds. Test encryption
  key absence/wrong key and ensure tokens do not appear in captured logs/errors.
- [ ] Add real SQL-backed integration using Task 1 fixture/harness for issue ->
  read -> draft -> revoke, two users and a persisted provider restart. No
  in-memory production grant store. Run GREEN and dependency checks.
- [ ] Commit and report exact consent/web integration contract, config variables,
  protocol versions, verification and supported client registration behavior.

### Task 3: Android draft domain, durable receipts and account-scoped access

**Files:**
- Create: domain/WorkoutDraft.kt, data/cloud/McpRepository.kt, DTOs and focused tests.
- Modify: actual data entities/DAO/AppDatabase/Repository migration files discovered
  from graph; AccountRepository only where needed for current-account boundaries.
- Create: room schema export and transaction/migration/receipt tests under
  app/src/androidTest (never overwrite an existing test).
- Create: generated server/production_mcp/contracts/movements.json and a golden
  exporter test from Seed.exercises using existing generated-web-asset patterns.

**Interfaces:**
- Strict shared DraftV1 parser/validator and canonical content hash agree with
  server fixtures. Built-in normalized-name keys must be unique and REPS.
- `McpRepository` exposes account/backend-scoped connection/draft/read/revoke/
  review operations over app RPCs and suppresses stale generation results.
- Repository acceptance takes reviewed immutable envelope + backend/user scope,
  atomically inserts all new preset entries and receipt, returns local preset ID
  on identical retry, rejects hash conflicts, and keeps receipt after deletion.
  First validate local entries, then online server approve for exact hash, then
  local transaction. Pending ack in receipt retries without inserting again.

- [ ] RED tests for strict future/unknown/invalid draft fields, all bounds, actual
  catalogue key/metric/collision checks, full rollback on last invalid movement,
  duplicate taps, deletion then retry and unchanged existing workouts.
- [ ] RED tests for account/backend/A->B->A transitions, network failure after
  approval or commit, reject-vs-approve, expiry-vs-approve, resumed approved draft,
  ack retry after original expiry, and idempotence. Clarify per-device acceptance.
- [ ] Implement add-only Room migration/receipt, strict resolver/transaction and
  scoped cloud adapter. AUTO/NONE choices remain exact, no fallback coercion.
  Existing unsupported backend/schema shows unavailable, not success/empty data.
- [ ] Generate full manifest from actual Seed, compare fixture hash/normalization
  across Python/Kotlin. No hand-maintained 580-name second catalogue.
- [ ] Run focused unit tests and scoped instrumented tests on emulator-5554 via
  lock, no other build in parallel. Commit and report source contracts/counts.

### Task 4: Real-app connection, consent and draft-review UI

**Files:**
- Modify: Account/Settings subsection, Train pending draft entry, IronvellumNav.
- Create: ui/ai connection/draft list/review screens/viewmodels as appropriate.
- Modify: strings.xml plain/themed resources and relevant accessibility/UI tests.
- Create: server/production_mcp browser consent/sign-in/management assets/tests;
  reuse existing web auth/brand patterns or a local bundled implementation.
- Modify: PRIVACY.md and docs/DEVELOPMENT.md for the actual optional data flow.

**Interfaces:**
- Consume Task 2 consent APIs and Task 3 scoped repository/domain models. Default
  signed-out/unconfigured/old backend states are explicit. Empty is distinct from
  loading/error. Never show success before confirmed revoke/save.
- Navigation: Account -> AI connections; Train -> workout drafts -> review.
  Save adds unscheduled workout; reject explicit; source/freshness/scope labels.
- Web consent shows the actual requesting client, scope choices, data disclosure,
  deny path, login/error/expiry. No provider-specific connect claims unverified.

- [ ] Coordinator must show all proposed UI states in faithful mockups before any
  UI source. This task cannot start before that recorded condition.
- [ ] Write meaningful RED tests: revoked connection disappears/stops access,
  review content matches hash/revision, duplicate save only one preset, invalid
  movement blocks whole save, stale account shows no prior draft, deny/expiry.
- [ ] Implement using existing PushedHeader, SettingsValueRow, Ink rows/buttons,
  actual Chakra Petch theme and string resources. Preserve Settings hub fit.
- [ ] Verify both wording modes and Vellum/dark in actual running emulator; record
  screenshots opt-in and inspect. Browser consent tests include keyboard/XSS,
  credential redaction and no persistent AI/app secrets. Run scoped GREEN.
- [ ] Commit and report exact user flow/screens, strings/tests and captures.

### Task 5: Deployment package, complete integration and independent review

**Files:**
- Create: server/production_mcp/Dockerfile, deployment/security/operations guide,
  exact environment example and reproducible smoke/check scripts.
- Modify: service README, docs/DEVELOPMENT.md and relevant release evidence.
- Coordinator owns final gate/device checks and public deployment decisions.

- [ ] Package non-root container, production TLS/proxy/canonical issuer settings,
  secret provisioning, health/readiness, data/schema/catalogue configure step,
  backup/migration/rollback, cleanup, quotas, key rotation/revocation, and allowed
  origins. Disable untrusted proxy/header assumptions and debug logs.
- [ ] Run actual OAuth + HTTP MCP + real disposable SQL end-to-end with two users,
  restart, draft creation/app review receipt/revoke and replay cases. Distinguish
  emulator/PGlite coverage from live hosted Auth or named AI product integration.
- [ ] Run final independent whole-change security review; address combined findings
  with scoped re-review. No unresolved privacy/token-isolation findings ship.
- [ ] Run Android/web full gate alone with counts and <600 sec budget, server/schema
  checks separately, dependency checks. Record source HEAD and actual evidence.
- [ ] If an approved public host exists, make deployment concrete/reviewable and
  execute within authorized scope; otherwise provide the fully tested deployment
  package and precise remaining host/config requirement, not a fake public URL.
- [ ] Push verified main, confirm remote equality/clean tree, update persistent
  memory and complete ledger. Report working capabilities, validation and any
  genuine public deployment limitation plainly.
