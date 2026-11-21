# Production MCP wire contract v1

Schema 36 exposes seven RPCs through PostgREST. Every successful JSON object includes `"schemaVersion":1,"ok":true` plus the fields below. Errors contain exactly `{"schemaVersion":1,"ok":false,"error":"<classification>"}`. A forbidden SQL role or a delegated app session gets insufficient privilege (SQLSTATE 42501). Timestamps ending in Ms are Unix milliseconds, IDs are UUID strings except the opaque client ID and source-qualified workout ID. All names, workout labels, exercise names and OAuth state are untrusted data. Treat them as text. [Wire examples](fixtures.json)

## Private gateway

POST `/rest/v1/rpc/ironvellum_mcp_gateway` with `{"p_operation":"...","p_args":{...}}` using the server's private role. Every key shown in the following input column is required. Each operation rejects additional keys. The database rejects oversized input above 65,536 bytes measured in PostgreSQL's JSONB text form. The private full-manifest installation accepts up to 1 MiB. Externally enforce the request byte limit before parsing.

| Operation | Exact input keys | Additional success fields |
| --- | --- | --- |
| configuration.get | none | resourceUrl, keyVerification (both null before initialization), catalogueCount, catalogueHash |
| configuration.configure | resourceUrl, keyVerification, catalogue, previousCatalogueHash | resourceUrl, keyVerification, catalogueCount, catalogueHash |
| client.register | clientId, clientName, metadataCiphertext, redirectUris, keyVerification | clientId |
| client.get | clientId | clientId, clientName, metadataCiphertext, redirectUris |
| authorization.create | clientId, resourceUrl, redirectUri, redirectUriExplicit, scopes, codeChallenge, state | requestId, expiresAtMs |
| authorization.get | requestId | requestId, clientId, clientName, resourceUrl, redirectUri, redirectUriExplicit, scopes, codeChallenge, state, expiresAtMs |
| code.load | clientId, codeHash | grantId, clientId, resourceUrl, scopes, expiresAtMs, codeChallenge, redirectUri, redirectUriExplicit |
| code.exchange | clientId, resourceUrl, redirectUri, codeChallenge, codeHash, accessHash, refreshHash | grantId, clientId, resourceUrl, scopes, accessExpiresAtMs, refreshExpiresAtMs |
| access.load | accessHash | grantId, clientId, resourceUrl, scopes, expiresAtMs |
| refresh.load | clientId, refreshHash | grantId, clientId, resourceUrl, scopes, expiresAtMs |
| refresh.exchange | clientId, resourceUrl, refreshHash, newRefreshHash, accessHash, scopes | grantId, clientId, resourceUrl, scopes, accessExpiresAtMs, refreshExpiresAtMs |
| token.revoke | clientId, tokenHash | none |
| tool.call | accessHash, tool, args | tool-specific fields below |
| prune | none | affected |

The operator's configuration call validates the resource, key marker and entire catalogue in one serialized transaction. It pins the resource and key marker and installs the catalogue together. The catalogue object contains exactly `schemaVersion` and `movements`. Send JSON null for `previousCatalogueHash` on initialization or an idempotent retry. To replace different existing contents, supply the exact installed `catalogueHash` observed before the operator's intended update. A stale expected hash, different resource or different key marker returns `conflict` and preserves the installed configuration. A retry that already matches the desired configuration succeeds.

The service derives `keyVerification` as lowercase hexadecimal HMAC-SHA256. The HMAC key is the 32 raw bytes obtained by base64url-decoding the persistent Fernet key. Its message is UTF-8 `ironvellum-mcp-key-verification-v1`, a NUL byte, and the exact canonical resource URL in UTF-8. The private database stores this verification value. It has no OAuth, encryption or RPC authorization capability. Startup, readiness and registration each check the marker. Unbound legacy registrations cause setup to return `conflict` pending explicit recovery.

Registration accepts 1–16 exact HTTPS or HTTP loopback redirect URIs, a 1–128 character ASCII identifier from `A-Za-z0-9._~-`, a 1–100 character unverified name and 1–32,768 characters of encrypted SDK metadata. The service authenticates the encrypted metadata on read. Registration is insert-only. SQL enforces 20 registrations per rolling minute and 1,000 per rolling 24 hours service-wide.

Scopes are a nonempty array of distinct strings from `training:read`, `progress:read`, `savedworkouts:read`, `workoutdrafts:create`. Explicit redirect is a boolean. State is a string up to 2,048 characters or JSON null. Only S256 is supported. The challenge is 43 base64url characters. The service verifies PKCE and passes the challenge it computed from the supplied verifier at exchange. An omitted protocol redirect resolves to the original effective redirect under SDK checks, while redirectUriExplicit preserves its origin. SQL admits up to 20 new authorization requests per client and 120 service-wide per rolling minute. Decided requests still count until the minute passes. A full window returns rate_limited. Authorization records expire after 10 minutes and disappear from lookup once decided.

Generate each opaque credential from at least 32 random bytes. Each Hash field is a lowercase 64-character SHA-256 hex digest. The service supplies new digests at exchange and only returns the corresponding raw credentials to the token endpoint client. The database issues no Supabase session. Code, access and refresh lifetimes are 2 minutes, 10 minutes and 30 days respectively, with all tokens capped by the 90-day grant. A refresh request that omits scopes on the protocol supplies the current refresh token's scopes to SQL. `grantId` is a pseudonymous connection identifier, not a Supabase user ID.

Both code and refresh lookups **and** exchanges detect a consumed credential for its registered client. They revoke the whole grant and return `invalid_grant` as a normal committed result. Never wrap this response in an application rollback. Explicit revocation by this client of its access or refresh digest revokes the whole grant. Revocation returns the same success if the token is absent or belongs to another client. Credential lookup does not authorize tool execution. Each tool invocation rechecks digest, expiry, revocation, consent, resource and token scope inside the database transaction.

Catalogue installation atomically replaces the version-1 manifest with 1-5,000 unique objects of exactly `{key,name,metric}`. A key is the lowercase trimmed canonical name. Metrics match the phone enum REPS, HOLD, DURATION, DISTANCE_TIME, ATTEMPTS_GRADE. Install the generated Seed manifest before serving traffic. Duplicate keys reject the entire install. Pruning processes up to 200 rows per category per invocation. Call periodically and repeat when backlogged. Audits last 30 days. Terminal drafts remain for at least 90 days after transition. Approved drafts remain resumable.

The catalogue fingerprint is lowercase SHA-256 of UTF-8 `ironvellum-movements-v1` followed by a NUL byte and each record in ascending UTF-8 key byte order. Each record contains the key, name and metric in this order, each UTF-8 string followed by a NUL byte. PostgreSQL text cannot contain NUL. The count and fingerprint are calculated from the installed rows.

## Direct app RPCs

These calls require the ordinary authenticated user's session and derive its owner from auth.uid(). A client_id claim is refused. PostgREST arguments use the indicated SQL names. Responses and all five draft states are illustrated in the fixtures.

| RPC | POST body | Additional success fields |
| --- | --- | --- |
| ironvellum_mcp_connections | {} | resourceUrl, connections[], recentAccess[] |
| ironvellum_mcp_revoke_connection | p_grant_id | revoked (true) |
| ironvellum_mcp_drafts | p_limit (1-50) | drafts[] |
| ironvellum_mcp_drafts_page | p_limit (1–50), p_cursor (null on first call) | drafts[], nextCursor (opaque string or null) |
| ironvellum_mcp_draft | p_draft_id | draft (envelope) |
| ironvellum_mcp_review_draft | p_draft_id, p_hash, p_action | draft (envelope) |
| ironvellum_mcp_consent | p_request_id, p_approved, p_scopes, p_code_hash | approved (boolean) |

A connection is `{id,clientName,scopes,createdAtMs,expiresAtMs,revokedAtMs}`. The latest 50 access records are `{requestId,clientName,tool,atMs,result,draftId}`. The optional timestamp and audit draft ID use JSON null. Only known tool names are recorded, with unknown names grouped under `unknown`. The result is `ok` or a safe classification. Empty lists are arrays.

The envelope is `{id,revision,contentHash,clientName,createdAtMs,expiresAtMs,status,draft}`. Revision is 1. The draft field is DraftV1. SQL normalizes numeric targets and hashes its immutable JSONB text as UTF-8 SHA-256. Treat the returned hash as opaque and echo it on review. The list returns the newest actionable pending or approved rows, and lookup by ID includes terminal states. Pending lasts 30 days. Approve and reject compete for the same locked pending row. Approval is idempotent in approved and accepted states. Only approved can transition to accepted after a durable local save. Reject is valid only while pending. An approved item remains approved until acknowledgment. App data access and draft review do not depend on the AI connection remaining active.

Consent approval requires a nonempty subset of the request's scopes and a fresh code digest. Denial uses false, an empty scope array and null digest. The browser must validate origin, CSRF and request binding before invoking consent or delivering the registered redirect. SQL makes a request single-use.

## Fixed tools

| Tool and permission | Exact args | Additional success fields |
| --- | --- | --- |
| list_workouts / training:read | optional limit (1-50, default 20), optional cursor string | workouts[], nextCursor (string or null) |
| get_workout / training:read | id | workout, sets[], truncated |
| get_progress / progress:read | none | freshness and available metadata, week, lifts[], liftsTruncated, trends |
| list_saved_workouts / savedworkouts:read | none | freshness and available metadata, presets[], truncated |
| search_movements / savedworkouts:read or workoutdrafts:create | query (1-80 characters), optional limit (1-20, default 20) | movements[] |
| create_workout_draft / workoutdrafts:create | idempotencyKey, draft | draft (envelope) |
| get_workout_draft / workoutdrafts:create | id | draft (envelope) |

Completed owned sessions and imports share a descending (completion time, source-qualified ID) ordering. IDs use `synced:<uuid>` or `imported:<uuid>`. The cursor is an opaque string binding the last returned source-qualified workout ID and its database-precision completion timestamp. Echo the string unchanged. The anchor must still resolve to an owned completed row at that exact timestamp. A missing or moved anchor requires restarting pagination. Summary fields are id, label, startedAtMs and completedAtMs. Detail adds at most 200 ordered sets with exerciseName, setIndex, reps, loadKg, durationSec, distanceM and done. Missing stored values use null.

Progress and saved workouts use version-1 phone snapshots. Freshness is fresh or stale, using the older of device build time and server sync time and a 24-hour threshold. Return builtAtMs, syncedAtMs, zone and snapshotVersion where available. An absent row returns only freshness=absent. A missing section returns absent alongside available metadata. A version mismatch returns unsupported_version, snapshotVersion and syncedAtMs, without the section. Missing or wrongly typed scalar values use null. Progress reports a week summary (startDay, endDay, workouts, hardSets, volumeKg, prevVolumeKg), up to 40 lifts with name, metric and up to eight variations per lift (label, sessions, sets, lastMs and the recorded peak's atMs, reps, durationSec, weightKg). The liftsTruncated and variationsTruncated flags indicate omitted items. Trends include daysPerWeek, records30d and the last 12 source weeks (start, hardSets, volumeKg, workouts) in source order with weeksTruncated. Missing progress sections are null if another section is present. The lift peak exposes the logged external weight, and computed body-adjusted estimates are excluded.

Saved workouts contain up to 50 presets with id, name, entries, entriesTruncated, each with up to 50 entries containing name, sets, reps, loadKg, scheme, modifiers, supersetGroup and warmup. Scheme and modifiers preserve the phone-provided descriptive strings. Source order is preserved. The outer truncated flag covers presets.

DraftV1 requires the exact fixture keys, including explicit warmup and cooldown AUTO/NONE and loadKg (number or null). Name is trimmed, 1-80 characters. Supply 1-20 ordered entries, each with a built-in REPS key, integer sets 1-50, integer reps 1-1,000 and load 0-1,000 kg or null. An idempotency key uses 1-80 characters from `A-Za-z0-9._~-`. An identical retry returns the original draft for the same owner/client even from a new connection. Different content conflicts. Clients see drafts for their own client and owner only. A rolling quota permits 120 tool attempts per minute and 20 new drafts per 24 hours across all grants for the same owner/client. Draft retries consume a tool call and no new-draft slot. Tool results are bounded to 1 MiB.

## Failure classifications

| Operations | Possible classifications |
| --- | --- |
| All gateway and tool inputs | invalid_arguments for incorrect shape or size, invalid_operation for an unknown gateway operation |
| Configuration/registration/catalogue | conflict for a changed resource, duplicate registration or duplicate catalogue key, rate_limited for registration |
| Client lookup | not_found |
| Authorization create/get, direct consent | invalid_request for missing, expired, spent or unbound request, invalid_scope for an invalid grant selection, rate_limited on authorization creation |
| Code/refresh load/exchange | invalid_grant for missing, expired, consumed, revoked or unbound credentials, invalid_scope for permission expansion |
| Access lookup/tool call | invalid_token for missing, expired or revoked access |
| Tools | insufficient_scope, unknown_tool, rate_limited, not_found, invalid_cursor, invalid_draft, unsupported_movement, conflict, result_too_large |
| Direct app actions | not_found for missing/foreign records, conflict for mismatched hash or state, invalid_arguments for invalid action/limit |
