# Development MCP implementation plan

**Goal:** A working local MCP service for safe Ironvellum development inspection.
**Architecture:** Python stdio service with an allowlisted ADB adapter; debug-only
Android status and preview bridges. No network server or training mutation tools.
**Tech stack:** Python 3.11+, official mcp 2.3.0, unittest, Kotlin debug receivers.
**Spec:** docs/superpowers/specs/2026-11-21-development-mcp-design.md

## Global Constraints

The design's Binding requirements apply verbatim to every task. Explicit serials,
shared emulator lock, no phone instrumentation, checked bounded subprocesses,
no generic command tool, DUMP debug-only bridges, allowlisted JSON and stdio-only
protocol output are release requirements. Root owns final integration and gate.
Implementers may not spawn agents. Each task ends with tests and a coherent commit.

## Review Focus

- Multiple online devices and unauthorized devices must never cause fallback.
- Timeout/lock contention/stale XML must fail without returning stale data.
- A foreground change must not expose another app's screenshot or UI nodes.
- Preview construction must not change real PendingIntent targets or inbox state.
- Legacy MCP clients must receive working tool/error/image protocol responses.

### Task 1: Debug app status and notification selection

**Files:** Create debug DevelopmentStatusReceiver.kt and Android DevelopmentStatusTest.kt;
modify debug AndroidManifest.xml, NotificationPreviewReceiver.kt and NotificationPreviewTest.kt.
Use a non-exported debug initialization ContentProvider to register the DUMP-
guarded status receiver dynamically. Status reads use registered-only package
broadcasts and never cold-start the app. Test manifest isolation and delivery.
Create tools/development_mcp/protocol-fixtures.json with the spec's canonical replies.
**Interfaces:** Produce exact READ_STATUS/IVMCP1 schema and optional scenario/reply
contract from the spec. Existing SHOW with no extras still posts six previews.

- [ ] Write failing tests for no-live/live status allowlist, read-only repository
  state, invalid request IDs, and selected accepted notification; preserve the
  existing real PendingIntent and inbox preference tests.
- [ ] Implement the specified bridge with bounded IO/goAsync, sanitized errors,
  manifest DUMP guards and fixed scenario-to-existing-ID mapping.
  Status uses a five-second onReceive watchdog independent of IO scheduling;
  test a stalled status operation still completes with status_unavailable.
- [ ] Run only the scoped relevant Android tests through tools/device.py lock,
  pinned to emulator-5554. Never use a phone. If it is absent, ask root to boot
  the installed AVD; do not change device settings. Record actual RED/GREEN.
- [ ] Commit the verified Android unit. Write .tmp/mcp-task-1-report.md with
  status, paths, commit IDs, commands/results and limitations.

### Task 2: Checked ADB adapter and device operations

**Files:** tools/development_mcp/pyproject.toml; src/ironvellum_mcp/adb.py,
operations.py, __init__.py; tests/test_adb.py and test_operations.py; .gitignore.
Modify tools/device.py lock helpers with optional timeout_s/log parameters;
extend tools/test_device_lock.py. Preserve existing caller defaults. Make PID
liveness portable for POSIX callers while retaining the Windows tasklist path.
**Interfaces:** `AdbClient(repo_root: Path, executable: str | None = None)`;
`DevelopmentTools(adb: AdbClient)` exposes list_devices(), get_app_status(serial),
capture_screen(serial)->bytes, inspect_screen(serial)->dict,
preview_notifications(serial, action='show', scenario='all')->dict,
get_diagnostics(serial, limit=100)->dict. Fake runner injection is the
implementer's choice. Production functions are synchronous and callable by SDK.

- [ ] Write failing fake-ADB tests for every Review Focus case belonging to this
  adapter, serial validation, offline/unauthorized handling, request ID correlation,
  malformed Base64/JSON, explicit override errors, timeouts and bounded output.
  A continuously foreground app with a system overlay remains a documented
  display screenshot; do not claim app-window pixel isolation.
- [ ] Implement each operation using the exact design bounds and bridge protocol.
  Import/reuse the existing emulator lock with a 30-second acquisition bound;
  pass a stderr logger (never globally redirect stdout), one lease per operation,
  and ensure it releases on error. Add optional timeout_s/log parameters to
  lock_acquire, lock_take_over and emulator_lock as needed, preserving existing
  defaults. Test timeout leaves the live holder intact and stdout unpolluted.
  A failed/timed-out PID probe must not classify a live holder as dead; bound
  Windows tasklist probes and conservatively retain an unverified holder.
- [ ] Use Python setuptools src layout, package name ironvellum-development-mcp,
  requires-python >=3.11 and dependency mcp==2.3.0. Root may create the isolated
  .venv. Ignore only this tool's .venv and build/install metadata.
- [ ] Run unittest discovery for host tests with fakes only; no ADB/build/device
  calls. Commit the adapter and report .tmp/mcp-task-2-report.md.

### Task 3: MCP server, protocol tests and usage documentation

**Files:** src/ironvellum_mcp/server.py and __main__.py under tools/development_mcp;
tests/test_server.py; README.md; docs/DEVELOPMENT.md link; pyproject entry point.
**Interfaces:** Consume Task 2 DevelopmentTools unchanged. `create_server(tools)`
returns MCPServer; CLI `python -m ironvellum_mcp --repo-root <absolute path>`.
Six tool names/signatures match the design and adapter; every serial is required.

- [ ] Write protocol tests using official mcp.Client and StdioServerParameters
  in modern and mode='legacy'. Use an executable fake-ADB fixture, not a phone.
  Check the six schemas, structured results, error handling, PNG image content,
  input validation and absence of extra stdout. No generic shell tool exists.
- [ ] Implement SDK MCPServer with tool annotations reflecting actual side effects.
  `capture_screen` returns image content. Send diagnostics/logging to stderr only.
  SDK errors must not expose arbitrary raw command output or private data.
- [ ] Document Windows and POSIX venv/install/client configuration, ADB discovery,
  foreground requirement, explicit phone selection, lock behavior, available tools
  and v1 limits. Include exact runnable config using the isolated venv interpreter.
- [ ] Run all host tests including real stdio tests, commit and report
  .tmp/mcp-task-3-report.md. Root runs actual emulator smoke and final Android gate.
