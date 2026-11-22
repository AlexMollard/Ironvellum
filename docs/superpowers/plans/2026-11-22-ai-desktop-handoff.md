# AI Desktop Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an agent set up a personal connector, automatically load validated training files without renaming/restarting, and offer a small optional status/recovery window.

**Architecture:** Keep existing immutable `--context FILE --outbox DIRECTORY` mode intact. Add opt-in folder mode with explicit source tokens on every data tool, bounded immutable snapshot retention and guarded private status/settings files. A separate lazy Tk window reads status and invokes the same setup/settings operations as the command line; it is never required by stdio.

**Tech Stack:** Existing Python≥3.11, official MCP2.3.0, Decimal/simplejson, jsonschema, platform private-filesystem guards; optional standard-library tkinter. No required new dependencies, network service or AI account.

**Spec:** `docs/superpowers/specs/2026-11-22-ai-round-trip-ux-design.md`

## Global Constraints

- Self-hostable on Windows, Linux and macOS. No required Ironvellum server, sign-in, ADB, hosting payment or GUI runtime.
- All v1/v2 existing inputs/outputs and explicit-file immutable mode remain unchanged. Folder mode has its own explicitly named protocol surface.
- New files use exact validated bytes; no lossy JSON reread/reserialization of training or proposals.
- Every in-flight request, cursor and draft remains tied to the exact source it used. Never silently switch datasets or rewrite outboxes.
- Preserve existing path/reparse/symlink/private-storage checks, bounded input/output and durable proposal idempotency.
- Setup only changes an explicitly requested client's settings, preserves other settings, makes backups and reports reload requirements honestly.
- Phone↔PC file transfer remains user chosen and manual. Status must distinguish configured, running and client-connected evidence.
- Approved desktop mockups govern status/settings/invalid-refresh/source-choice layouts; show any additional layout before UI code.
- Tests run locally, not Actions. Commit verified units to main and push. No build/gate concurrency.

## Review Focus

1. A partially copied or invalid newer file leaves the last good source active and exposes a recovery status (Task1).
2. A refresh between pagination or draft creation does not change source identity; stale/evicted tokens fail explicitly (Tasks1–2).
3. A valid file from another dataset cannot be adopted just because it has the latest timestamp (Tasks1 and3).
4. Repeated setup, malformed client settings or an interrupted write preserve unrelated servers and the original recoverable configuration (Task3).
5. Missing Tk, a closed window or a stale/crashed connector status cannot break headless use or falsely show a live client connection (Task4).

## File boundaries and shared protocol

All paths below are under `tools/local_connector/` unless prefixed otherwise.

- `src/ironvellum_local_connector/watched_source.py`: validation, adoption and bounded immutable source cache.
- `watch_server.py`: folder-mode tools, source-token selection and original Journal integration. Leave legacy `server.py` schemas/results untouched.
- `settings.py`: versioned local settings, selected dataset and atomic guarded writes.
- `setup.py`: deterministic agent interface, explicit client-config merge/backups and diagnostics.
- `status.py`: private runtime heartbeat/snapshot/problem publication and read-only GUI view.
- `desktop.py`: optional lazy native window, no background stdio ownership.
- `__main__.py`: backward-compatible old flags, new `serve-folder`, `setup`, `status`, `select-source`, `desktop` command dispatch.
- `contracts/tools-watch-v1.json`: new tool schemas composed from frozen existing inputs/outputs; package them without changing frozen contracts.

### Task 1: Validated folder refresh and explicit source choice

**Files:** Create `watched_source.py`, `settings.py`, `tests/test_watched_source.py`, `tests/test_settings.py`; extend `storage.py` only to expose guarded bounded file reads/enumeration without weakening its existing LocalFiles contract.

**Interfaces:** `WatchedSource(settings_path: Path, clock: Callable)`; `refresh() -> RefreshStatus`; `current() -> Snapshot`; `resolve(source_sha256: str) -> Snapshot`; `select_dataset(dataset_id: str) -> None`. Settings exact schemaVersion1 includes absolute `inputDirectory`, `outboxDirectory`, `stateDirectory`, nullable `selectedDatasetId`, boolean `watchEnabled`; unknown fields reject. Setup defaults watchEnabled to false unless `--watch` is supplied. Folder mode initially loads a validated selected source; when watching is disabled it holds that source for the process lifetime until the user changes selection/enables watching or restarts. The GUI toggle exposes this choice. `RefreshStatus` contains active metadata/hash, pending cross-dataset candidates and sanitized error codes, not raw paths/content.

- [ ] Add failing tests for completed valid copy, half copy, invalid newer JSON, unsupported context, source replacement during read, unsafe symlink/reparse path, excessive file count, and same snapshot ID with conflicting original bytes. Assert last good remains byte-identical on invalid candidates; timestamps from filesystem alone cannot determine source age.
- [ ] Add tests: no selected dataset plus one dataset can propose/select explicitly during setup; multiple datasets require a choice; a different dataset never auto-adopts; explicit selection can switch; expired cached token yields `source_expired`; original Snapshot object held by a request remains unchanged after eviction.
- [ ] Implement polling on each folder-mode request and GUI diagnostic poll, throttled to once per2s, without a mandatory background service. Scan only immediate regular `.json` files, at most256 candidates; read/validate at most32 MiB per scan, continuing pending candidates on later scans and caching unchanged validated observations. Complete bytes≤8 MiB, stable identity/size/mtime before+after read, existing Snapshot validation. Add a focused test that exhausted scan budget makes progress without blocking indefinitely or losing the current source. A partial file that parses is adoptable only after two unchanged observations≥2s apart. Select highest validated exportedAtMs in the chosen dataset; tie-break by snapshotId. Ignore older valid copies; retain last good in memory. Reject equal snapshotId/conflicting SHA instead of relabeling. GUI diagnostics must not relabel independently inspected files as loaded by a running stdio process; only its heartbeat confirms that.
- [ ] Cache at most4 immutable sources keyed by SHA-256, raw bytes at most32 MiB total. Old tokens resolve only while retained; do not rebind a stale token to the new current snapshot. Hold local object references for the whole dispatched call.
- [ ] Persist validated settings and source-choice decisions atomically with private local filesystem checks. Distinct input/outbox/state locations cannot overlap dangerously. Cross-dataset pending choice remains a visible status; no file move, delete, rewrite or automatic outbox reset.
- [ ] Run `python -m unittest discover -s tests -p 'test_watched_source.py'` and settings tests in the installed test environment, then existing boundaries/context tests. Commit and push.

### Task 2: Snapshot-safe watched MCP tools and identifiable drafts

**Files:** Create `watch_server.py`, `contracts/tools-watch-v1.json`, `tests/test_watch_stdio.py`; modify `__main__.py`, `stdio.py` with an injected server factory if needed, `pyproject.toml`, `README.md`, `contracts/README.md`; preserve old server dispatch.

**Interfaces:** `create_watch_server(files, sources)`; new `get_training_status` tool accepts a closed empty object and returns current snapshot metadata plus `sourceSha256`, pending choice and sanitized refresh state. In folder mode the existing seven data tool names each require an additional `sourceSha256` argument. Return shapes otherwise stay frozen; strip the token only after resolving a concrete immutable source. Draft Journal is constructed with that resolved source; metadata is computed from the same object.

- [ ] Write failing official-SDK stdio tests for status discovery, missing/malformed token, old page token after refresh, old draft after refresh, eviction and idempotent retry against original snapshot. Request A's metadata, cursor and proposal contextSha256 must all stay A while current status has B. Unknown token returns `source_expired`, never silently falls back.
- [ ] Add tests that legacy CLI produces the exact existing schemas/responses, no watcher is created in immutable mode, all responses remain within1 MiB and arguments64 KiB, stdio stdout has no GUI/log/setup messages, and two refreshes cannot corrupt Journal's per-snapshot idempotency.
- [ ] Implement mutually exclusive legacy flags and new `serve-folder --settings FILE`. Advertise the token requirement prominently in tool descriptions and publish the new schema separately; do not mutate `tools-v1.json`, `tool-types-v1.schema.json` or v2 fixtures. Reuse existing output-limit and exact-number encoding.
- [ ] Preserve UUID proposal filenames and exact original bytes. Add separate folder-mode `list_ready_drafts`/MCP resource support so the AI can identify a draft by its human name, stable draftId and safe `ironvellum-draft://<sourceSha256>/<draftId>` resource URI; `read_resource` returns the original bounded JSON bytes. The resource identifier exposes no absolute path and never permits arbitrary filesystem reads. `list_ready_drafts` accepts only required sourceSha256 plus optional offset (integer≥0, default0) and limit (integer1..20, default20); returns that source's snapshot metadata, items `{name,draftId,filename,uri}`, total and nextOffset (null at end), ordered by draftId. Read resources resolve the source token and Journal, with the same explicit source_expired behavior after eviction; GUI/manual file access remains available. Do not claim imported/saved status on the phone.
- [ ] Run new SDK tests and all existing installed-wheel connector tests. Assert resources cannot traverse/read arbitrary files and no old tool result changes. Commit and push the independently usable folder-mode connector.

### Task 3: Agent-first deterministic setup and machine-readable diagnostics

**Files:** Create `setup.py`, `status.py`, `tests/test_setup.py`, `tests/test_status.py`, `AGENT_SETUP.md`; modify `__main__.py`, `README.md`, package inclusions/version as needed.

**Interfaces:** `setup --settings FILE --input-directory DIR --outbox-directory DIR --state-directory DIR --client config-only|claude-desktop [--client-config FILE] [--dataset-id UUID] [--watch|--no-watch] --json`; `status --settings FILE --json`; `select-source --settings FILE --dataset-id UUID --json`. JSON results use `schemaVersion:1`, stable `status`, `code`, selected sources and next actions. Config-only returns the stdio MCP fragment for any compatible agent; an explicit Claude Desktop target may merge only its named `ironvellum-personal` entry.

- [ ] Add failing tests for fresh setup, repeat identical setup without extra backups, preserving unrelated client servers/settings, malformed/duplicate-key config, read-only paths, interrupted writes, conflicting existing Ironvellum entry and invalid source. Conflict must stop with actionable result; no silent overwrite. Explicit new settings may be prepared without any training file and report `awaiting_training`, not configured/connected success.
- [ ] Implement atomic config update with same-directory private backup before the first changed write, verify resulting settings/config by rereading, preserve argument boundaries and private paths. Never discover/change every installed client. Client-config selection is explicit or the documented per-platform Claude Desktop location only when that client was requested. No shell interpolation of client/user paths.
- [ ] Emit separate `configured`, `awaiting_training`, `running`, `client_reload_required` and verified handshake evidence. Writing config alone is never connected. Runtime heartbeat is per process/session, stale after15s; stale status never counts as live. Status includes actual loaded exportedAtMs/coverage and safe problem codes. Guard private state files against links and partial reads.
- [ ] Document one copyable agent instruction: obtain this repository/package from its verified release/source, install in an isolated Python environment, run this setup command for the requested client, verify via MCP initialize/list_tools/read-only status, then report any remaining reload/file-transfer action. Include Windows/macOS/Linux instructions and no-GUI fallback. Installation may require downloading public dependencies; ordinary runtime/file flow does not contact Ironvellum servers.
- [ ] Run setup/status tests and fake-client handshake test; ensure failure outputs contain no raw training data and no unrequested application restart. Commit and push.

### Task 4: Optional status/recovery window and complete workflow verification

**Files:** Create `desktop.py`, `tests/test_desktop_model.py`; modify `README.md`, `AGENT_SETUP.md`, `__main__.py`, `docs/RELEASE_CHECKLIST.md` and phone setup instructions after mobile Task4 is available.

**Interfaces:** `desktop --settings FILE` lazily imports tkinter. `DesktopState` is a testable read-only view of Task3 status, dataset candidates and draft metadata. Actions use Task3 select/setup APIs; opening folders uses OS argument APIs, never shell-composed commands. Missing Tk returns `gui_unavailable` with headless guidance and does not affect any other command.

- [ ] Add failing model tests for stale heartbeat, configured-only, missing/invalid training, pending dataset choice, malformed display names, missing Tk, closed GUI and unavailable folder opener. Assert draft names render as literal text and no path/string is executable.
- [ ] Implement the approved small status window: connector status, loaded training date/coverage, workouts ready with human names, Open drafts folder, Copy agent setup instructions, optional manual folders/settings including the approved watch toggle. Reuse approved desktop settings/refresh-error/source-choice layouts; no mandatory wizard. Provide keyboard focus/labels, scroll/resize, clear error and retry actions. Window polls status and may refresh diagnostics, but must not launch a second competing stdio server or own its lifetime. When watching is off, explain that the running source is held; startup/restart and explicit dataset selection may load another validated source.
- [ ] Source choice explicitly shows pending dataset information and confirms the chosen source through select-source; never switch silently. Preserve original proposal filenames and outbox, and make selected files discoverable without revealing paths through MCP tool output.
- [ ] Run the window on this PC against synthetic private folders; inspect approved states, close it, and verify headless SDK reads/draft creation still work. Platform-specific GUI smoke checks unavailable here must be disclosed; cross-platform path/model tests still run locally.
- [ ] Run installed-wheel connector suite and end-to-end synthetic export→watched read→draft artifact→phone importer, including refresh while old draft is pending. No remote upload or owner data edits. Update actual evidence, then commit/push. If Android source changed, run the full local gate alone under600s; otherwise reuse the already verified Android source evidence and clearly identify it.

## Self-review / execution

This plan covers optional refresh, source pinning, agent setup, visible artifacts and the selected status/recovery GUI. The companion phone plan independently covers portable import/edit/share. Tests own all five Review Focus cases. Explicit immutable mode and frozen contracts have dedicated regression checks. Execute after the phone contract is settled; desktop implementation need not depend on portable imports because existing source-bound proposal files remain supported.
