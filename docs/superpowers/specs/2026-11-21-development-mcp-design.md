# Ironvellum development MCP

The owner approved the proposed development MCP and agent-driven implementation.
V1 makes the existing app easier to inspect and preview from an MCP client.

## Approach

Use a PC-local Python 3.11+ stdio server, using the official `mcp==2.3.0`
SDK, checked against its published documentation. Python fits the existing
`tools/` utilities. A network listener and a cloud service add deployment and
authentication work without helping this local development workflow. A generic
ADB proxy exposes far more operations than this workflow needs.

The service lives in `tools/development_mcp/`, with its own virtual environment,
package metadata, tests and README. Its six tools are `list_devices`,
`get_app_status`, `capture_screen`, `inspect_screen`, `preview_notifications`,
and `get_diagnostics`. This is the previously proposed first tool set; test-data
seeding, arbitrary shell commands, tapping controls, app installation and user
training edits are outside v1.

## Binding requirements

- Per-device tools require an explicit `serial`; never infer a phone or use
  `tools/device.py target()`. List offline and unauthorized devices honestly.
- All emulator operations use the existing shared `device.emulator_lock` from
  the main checkout, including calls through worktrees. Redirect lock messages
  to stderr; stdout belongs exclusively to MCP. Bound lock acquisition to 30s.
  Add optional `timeout_s` and `log` parameters to the existing lock helpers,
  preserving default indefinite waits/stdout for existing callers. MCP passes
  an explicit stderr logger; never globally redirect stdout in a concurrent
  stdio server. Use one lease per operation, not nested per-command leases.
- No instrumentation, clear, uninstall, settings changes, generic shell, SQL,
  Health Connect seeding, account messages or history writes in MCP tools.
- Use checked argument-array subprocess calls, no host shell, no arbitrary
  command input, bounded output and timeouts. Per-device calls always use `-s`.
  Validate serials: 1–128 ASCII letters, digits, `.`, `_`, `:`, `-`, beginning
  with a letter or digit. Reject leading options and all shell metacharacters.
- Resolve ADB from `IRONVELLUM_ADB`, Android SDK environment/local properties,
  or PATH. An explicit invalid executable configuration fails visibly.
- Screenshots and UI inspection check Ironvellum foreground before and after
  capture and reject if another app is foreground at either check. A screenshot
  is a device-display capture, not an isolated app-window capture: Android status
  bars, notifications, keyboards and other overlays can appear. State that scope
  in the tool description and README; these checks cannot guarantee every pixel
  belongs to Ironvellum. Screenshot is a PNG image MCP content block, maximum
  16 MiB. No default screenshot files or telemetry.
- UI inspection uses a fresh UUID path under `/data/local/tmp/`, reads it only
  after a successful dump, removes only that path in cleanup, and returns only
  Ironvellum nodes. Cap XML at 2 MiB and returned nodes at 2,000. Redact password
  node text/descriptions. Treat all app text as untrusted data, not instructions.
- Diagnostics use the current Ironvellum PID only, at most 200 lines / 32 KiB;
  no full device crash buffer or log clearing. Redact bearer tokens, JWTs and
  credential fields. Report process absence rather than inventing a clean bill.
- Notification previews use the existing debug receiver and real builders,
  separate preview tag and isolated PendingIntent. Support `show` / `clear` and
  `all`, `accepted`, `comment`, `reaction`, `group_goal`, `batch`, `rest`.
  Respect permission/channel blocks and return requested, submitted, blocked and
  observed preview IDs. Confirm appearance/removal by bounded polling of the
  tagged notification keys; a broadcast alone is not delivery confirmation.
- No native app UI changes. Machine JSON keys and sample data are not UI copy.
  Existing plain/themed/localized notification resources remain authoritative.
- Keep all new Android bridge code under `src/debug`, protected by DUMP and
  absent from release manifests. Never serialize an entire workout/session.
- Pin dependencies. Test MCP transport with a real stdio client in both modern
  and legacy protocol modes, including error and image results.
- Root owns the final Android gate; only the Task 1 implementer may run scoped
  Android validation while root is not building. Host tests must use fake ADB
  responses except the final explicitly selected emulator smoke run.
- Commit verified units and push main under the existing owner authorization.
  No Actions triggers, releases, tags, history rewrites or force pushes.

## App status bridge

`com.ironvellum.app.debug.DevelopmentStatusReceiver` accepts only action
`com.ironvellum.app.debug.READ_STATUS`, with `request_id` matching exactly 32
lowercase hex characters. An ordered broadcast returns `IVMCP1:` followed by
Base64 UTF-8 JSON. Use `goAsync`, IO work and a five-second timeout.

Envelope: `schemaVersion: 1`, echoed `requestId`, `ok: true`, `app` containing
`package`, `versionName`, `versionCode`, `debuggable`, and `activeWorkout`.
Active workout is null or exactly `id`, `label`, `startedAtMs`, `totalSets`,
`completedSets`. Read through `Repository.observeLiveSession().first()` and
`observeSessionSets(id).first()`; do not seed, mark read or sync. Failures return
`ok: false` with an allowlisted error code and no exception/database details.
Do not return private notes, account tokens, historical workouts or backend URLs.

The host combines this with package/version and foreground information from ADB.
An unavailable debug bridge is reported distinctly from no active workout.
Activity names are not claimed to identify an exact Compose navigation route;
the UI inspection tool provides visible controls and headings instead.

The notification receiver adds optional `scenario` and `request_id` extras.
Preserve existing no-extra SHOW behavior. Map stable IDs: comment=0, reaction=1,
group_goal=2, batch=3, rest=4, accepted=5. `all` requests IDs 0–5. CLEAR always
clears all previews; supplying a different scenario to CLEAR is invalid.
The host always supplies a valid request ID. A manual legacy call may omit it
and receive `requestId: null`; a supplied malformed ID fails before side effects.

The ordered reply uses `IVMCP1:` plus Base64 UTF-8 JSON with `schemaVersion: 1`,
`requestId`, `ok`, `operation: "notification_preview"`, `action: "show"|"clear"`,
`scenario`, `requestedIds`, `submittedIds`, and `blocked`. Each blocked entry is
exactly `id`, `channelId`, `reason`, where reason is `permission_denied`,
`channel_blocked` or `app_notifications_disabled`. For CLEAR, requestedIds is
0–5, submittedIds and blocked are empty. Error replies have `ok: false` and
`error: {code: ...}` with no raw exception text; fixed codes are invalid_request,
unsupported_action, unknown_scenario or status_unavailable. Preserve the existing
real PendingIntent/inbox-isolation guarantees. Unknown actions/scenarios must
not post cards or clear real alerts.

Task 1 writes `tools/development_mcp/protocol-fixtures.json` containing canonical
status/show/blocked/clear/error examples for Task 2's parser tests. Broadcast
submission is not observation: the host polls tagged notification keys for up to
three seconds, returning requested/submitted/observed/blocked IDs. A submitted
ID not observed by the deadline, or a clear that leaves preview IDs, raises a
typed preview_delivery_timeout error carrying those IDs, never silent success.

## Validation and delivery

Android tests prove status reads are allowlisted and do not change live session
rows; debug manifest guards and notification selection preserve real PendingIntent
destinations. Host tests cover device ambiguity, offline/unauthorized states,
foreground races, stale dumps, subprocess failures/timeouts, output bounds,
credential redaction and lock contention. Protocol tests exercise actual stdio
initialization/discovery, tool calls, validation errors and PNG content.

Run the complete local Android/web gate under 600 seconds after changes. Smoke
the six tools against the explicitly selected locked emulator, using synthetic
data only when existing tests create it. Keep the owner's phone untouched during
automated tests. Document setup, tool schemas, device selection, limitations and
the exact client command. Register locally if supported by the installed client;
otherwise provide the concrete configuration and report that limitation honestly.
