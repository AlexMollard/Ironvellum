# Ironvellum production MCP: reads and workout drafts

## Authority and purpose

The owner approved building the previously described production MCP with read
access and workout drafts, using agents. A person connects their own compatible
AI client, signs into Ironvellum, chooses permissions, asks about synced training
and sends proposed workouts to the phone for explicit review. This document
records that scope and the implementation choices below; those choices are agent
decisions, not additional owner requirements. Hosting information is pending an
asynchronous question. Complete and verify the service before requesting a
deployment decision. No new paid account or subscription is assumed.

## Existing system

- Kotlin/Compose Android app; optional Supabase Auth/PostgREST. Cloud identity
  includes the backend, account and identity generation. Sign-out preserves local
  training. Custom backends are supported by the existing app.
- Cloud completed/imported sessions, private set details and owner web snapshots
  already supply training history, derived progress and saved routines. Snapshot
  time/version/zone matter; an offline phone's latest changes may be absent.
- Private notes, raw health/body measurements, photos, archives and unfinished
  sessions are outside this feature. Never return a raw snapshot or backup.
- Supabase OAuth tokens currently have broader Auth/Data/Storage authority than
  these features. Custom OAuth scopes are not supported by its native server.
  Ordinary user tokens must never be issued to an AI client.
- Existing routine import can discard unknown movements or replace the plan. It
  cannot be used unchanged. Saved workouts are local presets; web snapshots are
  uploaded read views, not a multi-device preset synchronizer.
- A separate local development MCP exists under tools/development_mcp. Do not
  import or expose its ADB, screenshots, shell, notification or device tools.

Sources: .tmp/production-mcp-{backend-recon,app-recon,auth-research}.md.

## Approaches and decision

1. Native Supabase OAuth would reuse hosting, but exposes ordinary account
   tokens and requires changes across existing Auth/RLS/RPC access. Rejected.
2. A Deno Edge implementation can keep state in Supabase, but current SDK OAuth
   server helpers are frozen and project-function paths cannot serve root OAuth
   discovery without another public router. Rejected for this first version.
3. **Chosen:** a standalone Python HTTPS/ASGI service using the official MCP SDK
   2.3.0 OAuth provider interface and transport, with durable Supabase operations
   behind fixed RPCs. It serves discovery at its own origin, consent UI and MCP.
   This uses maintained protocol handling and requires a container-capable host.
   Prepare a container and deployment guide; do not assume a public deployment
   or new bill is available. Browser consent reuses normal Supabase human sign-in.

A server-owned backend credential remains private and is used only for fixed
gateway RPCs. If initially provisioned as Supabase service_role it has broad
authority: do not describe it as least-privileged. Gateway functions independently
derive users from tokens/grants and check every operation, including after a
transport-level token check. Public clients cannot call the gateway. Prefer a
dedicated gateway role where an operator can provision one safely.

## Authentication and permissions

Production requires a canonical HTTPS issuer origin and resource `<origin>/mcp`.
Only an explicit localhost development option permits HTTP loopback. Backend URL
and public/private keys are server configuration, never request arguments. V1 is
one configured backend; custom-backend operators deploy their own service.

Use authorization code with S256 PKCE and exact registered redirect matching.
Use the SDK's OAuth routes, checks and errors, extending/validating resource and
scope binding as necessary. Accept public and confidential registered clients
supported by the SDK. Controlled dynamic registration is bounded, rejects unsafe
redirects and never fetches arbitrary client metadata/logo URLs. Document actual
tested protocol/client compatibility; do not claim every AI product works.

Scopes: `training:read`, `progress:read`, `savedworkouts:read`,
`workoutdrafts:create`. Reject unknown or escalated scopes. Consent can grant a
subset. The backend checks the live user/client/resource/scopes grant on every
tool operation. Normal Supabase sign-in JWTs are rejected at MCP. Opaque MCP
tokens are rejected by app management/review and normal Supabase endpoints.

Generate high-entropy random secrets with at least 256 bits. Store only SHA-256
digests of authorization/access/refresh tokens. Encrypt SDK client metadata
containing client secrets using a persistent server encryption key. Never store
plaintext client secrets or bearer credentials in SQL, logs, URLs or drafts.
Client names are unverified text, escaped and bounded; never fetch client logos.

Defaults: authorize request 10 minutes, one-use code 2 minutes, access 10 minutes,
refresh inactivity 30 days and grant absolute lifetime 90 days. Code exchange and
refresh rotation are transactional. Reuse detection revokes the affected grant
and token family **and commits** before returning an error; do not throw and roll
the revocation back. Explicit revocation invalidates active access immediately.
No implicit permission expansion when reconnecting or refreshing.

Consent uses the human's authenticated Supabase session only between their
browser and Ironvellum. Strict origin/CSRF protection and immutable request binding
cover approve/deny; tokens are exchanged only at the token endpoint. The browser
never gives its Supabase session to the AI. Do not capture passwords in server
logs or analytics. Return no-store and no-referrer on authorization pages/results.

## Production tools

Each call derives its principal from authentication, never a supplied user ID.
Use typed strict inputs with unknown fields rejected and structured safe errors.

| Tool | Permission | Meaning |
| --- | --- | --- |
| list_workouts | training:read | Own synced/imported completed history, stable cursor, max 50 per page |
| get_workout | training:read | Own completed workout and bounded sets; no private/public free-text notes |
| get_progress | progress:read | Allowlisted phone-computed training progress and explicit freshness |
| list_saved_workouts | savedworkouts:read | Allowlisted routine read view and freshness |
| search_movements | savedworkouts:read OR workoutdrafts:create | Built-in movement keys/name/metric, max 20 matches |
| create_workout_draft | workoutdrafts:create | Validate and persist an owned immutable draft; no preset/session mutation |
| get_workout_draft | workoutdrafts:create | State/content of this owner's draft from this client only |

Return explicit absent/stale/unsupported-version states; never invent zeroes or
recompute a different progress system. Treat retrieved text as untrusted data.
Do not expose arbitrary table/query/URL/shell arguments or accept/apply tools.
Do not expose body measurements, profile/body figures, bodyweight ratios, raw
health, rewards, social data, private notes, archives or credentials. DTO field
allowlists must exclude canary private fields at every nesting level.

Requests max 64 KiB, tool results max 1 MiB, backend call deadline 10 seconds,
bounded page/result counts. Quotas are persisted and atomic: 120 tool calls per
user/client per minute, 20 new drafts per user/client per day, 20 registrations
per minute and 1,000 per day service-wide. Retry/idempotent draft reads do not
consume new-draft quota. Authorization request creation is additionally capped at
20 per client per rolling minute and 120 service-wide per rolling minute, so a
registered public client cannot fill storage with pending requests. Do not trust
arbitrary forwarded IP headers. Audit only
owner/client/tool/time/request/draft ID and safe result classification; no payload
or token. Show the latest 50 audit entries; retain 30 days and bound pruning.

## Draft contract and acceptance

DraftV1 is exactly one new, unscheduled saved workout:

```json
{
  "schemaVersion": 1,
  "name": "Upper A",
  "warmup": "AUTO",
  "cooldown": "NONE",
  "entries": [{"exerciseKey": "bench press", "sets": 3, "reps": 8, "loadKg": 40}]
}
```

Name 1..80 trimmed characters; 1..20 entries; integer sets 1..50 and reps
1..1000; load null or finite 0..1000 kg. Both preparation choices are explicit
AUTO/NONE. No schedule, note, modifier, superset, scheme, variable targets or
explicit preparation steps in v1. Unknown fields/unsupported metrics are errors,
never silently discarded. Only built-in REPS movements are supported initially;
custom/timed/distance/climbing entries are rejected clearly.

Publish a versioned movement manifest from actual Seed.exercises with canonical
name and metric. Key is the exact lowercase/trimmed canonical name, not Room ID,
leaderboard lift wire or drawing ID. Reject collisions. The phone revalidates
every key against its current built-in REPS catalogue before acceptance. To keep
identity-blocking validation bounded, v1 inspects at most 5,000 local movement
rows plus one overflow sentinel. If the complete local catalogue exceeds that
budget, reject the whole draft before approval and again during transactional
revalidation. Never infer uniqueness from a truncated scan. Existing manual
workouts and movements remain usable; this limit applies to AI draft import.


Envelope adds immutable id/revision/content hash, owner/client/grant, timestamps,
status and expiry. Pending draft expires after 30 days. Creation idempotency key is 1..80
ASCII safe characters, bound to owner/client and canonical content hash. Identical
retry returns original draft; conflicting content is an error. Preserve review
tombstones/receipts. Drafts never create completed history, rewards or sessions.

App fetch/review is scoped to backend/account/generation and cleared on changes.
An explicit Save workout first validates every local movement, then obtains an
app-only server approval for the exact hash before writing. Approval is an atomic
transition pending -> approved; rejection or expiry that wins first blocks it.
Approve is idempotent for approved/accepted drafts. Rejection is only allowed
while pending; approved/accepted drafts cannot later become rejected or expired.
Thus an already-approved local save can finish and acknowledge after the original
expiry. An open review alone is not approval. Show approved-but-not-locally-saved
drafts as resumable; an offline device must reconnect to obtain first approval.

After approval, Save writes all validated entries, choices and a durable receipt
in one Room transaction. Receipt key `(backend, userId, draftId)` plus hash
survives preset deletion, so retry cannot recreate a deleted accepted workout.
Never replace existing presets. Add the new workout unscheduled. Acknowledge
server status after the local commit and retry acknowledgements from the receipt;
network failure must not reinsert presets. Reject is an explicit app action.

Exactly-once means **per local device database**: existing presets are not shared
live between devices. An already-open review on another device can save its own
copy once. UI says saved on this device; do not claim cross-device atomic import.
Server acknowledgement transitions approved -> accepted and is idempotent.
AI state approved means user permission to save; accepted means at least one
local database reported a successful save, not a global preset sync guarantee.
Payload/status tombstones persist for at least 90 days after final state so a
delayed acknowledgement cannot resurrect or contradict rejection. Approval is
the authorization boundary; acknowledgement does not authorize new local writes.

## Interfaces and persistence

Private service RPC:
`ironvellum_mcp_gateway(p_operation text, p_args jsonb) returns jsonb`.
Its allowlisted operations cover configuration, encrypted client metadata,
authorization requests/codes, token lookup/exchange/rotation/revocation, fixed
tool execution and bounded pruning. Task 1 writes the exact operation dictionary
and shared fixtures in `server/production_mcp/contracts/`; later tasks consume it.
Every tool operation takes only a token digest plus its allowlisted arguments;
the database resolves owner and checks live grants itself.

App RPCs use the ordinary authenticated session and derive auth.uid(), rejecting
any delegated `client_id` claim: `ironvellum_mcp_connections()`,
`ironvellum_mcp_revoke_connection(p_grant_id uuid)`,
`ironvellum_mcp_drafts(p_limit integer)`,
`ironvellum_mcp_draft(p_draft_id uuid)`,
`ironvellum_mcp_review_draft(p_draft_id uuid,p_hash text,p_action text)`,
`ironvellum_mcp_consent(p_request_id uuid,p_approved boolean,p_scopes text[],p_code_hash text)`.
Review actions are approve/accept/reject with the transition rules above.
Responses are JSON objects, not unversioned implicit SQL row shapes. New tables
are RLS enabled with no direct app writes. Add idempotent schema 36 migration and
hosted upgrade; do not raise the app's global minimum schema requirement.

## UI and copy

Before changing UI source, show mockups matching real Ironvellum typography,
colors, cut corners and existing row/workout layouts. Keep the Settings hub
fitting: place AI connections inside Account, not another hub row. Include empty,
connected, revoked, loading/error, consent allow/deny, pending drafts, full draft
review, validation failure and saved/rejected states. Use existing components,
plain/themed string resources and accessibility semantics. Add a quiet pending
drafts entry to Train; review is a pushed page with movement rows and one save CTA.
Web consent/management follows the same brand and existing human sign-in pattern.
State that approved data is sent to the selected AI provider; revoke stops future
access, not recall of already shared data. Display synced timestamp/freshness.

## Verification and deployment

First test SQL with a real disposable PostgreSQL-compatible runtime (PGlite is an
acceptable local fallback when Docker is unavailable) plus auth stub. Test two
owners, anonymous/direct/delegated sessions, service-only gateway, revocation,
one-use code races, refresh replay, quota/idempotency, canary privacy projections,
and unchanged normal app/social behavior. Never use live customer rows as fixtures.
Use official MCP clients for modern and legacy HTTP/protocol and OAuth checks.
Mock backend transport tests alone do not certify SQL or real hosted Auth.

Test Android receipt migration, duplicate acceptance/deletion/retry, invalid
movement rollback, account/backend/generation changes and acknowledgement recovery.
Verify actual running screens in both wording modes. Use only emulator-5554 under
the main-checkout lock. Never instrument/clear/uninstall owner phone R5GL14GXV3J.
Run the full Android/web gate alone, strictly under 600 seconds; preserve test
count checks. Run targeted server/schema tests separately, and report Docker vs
PGlite coverage honestly. Pin dependencies, scan them and run appropriate checks.

Final independent security review covers the whole public boundary and fixes,
not just tool handlers. No paid hosting, account creation, production token grants
or unreviewed schema deployment. Supply a complete runnable service/container,
environment example containing no secrets, upgrade/rollback/deploy instructions,
compatibility evidence and a concrete deployment decision if a public host is
still unavailable. Commit/push verified units to main as already authorized.
