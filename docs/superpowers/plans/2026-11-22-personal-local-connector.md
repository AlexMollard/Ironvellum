# Personal Local Connector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship accountless offline-file training reads and reviewed draft imports, with optional/self-hostable network services and no hosting bill.

**Architecture:** Android exports a dedicated allowlisted snapshot and records its exact-byte provenance. A separate PC stdio MCP reads that immutable file and writes proposal files; Android explicitly reviews and atomically imports each proposal once. Existing cloud/account guards stay separate.

**Tech Stack:** Kotlin/Compose/Room 51, strict JSON contract v1, CPython >=3.11, official MCP 2.3.0 stdio, SQLite local bookkeeping; optional upstream Supabase Docker stack.

**Spec:** `docs/superpowers/specs/2026-11-22-personal-local-connector-design.md`

## Global Constraints

- No Ironvellum/server sign-in, backend, network, paid hosting, developer mode or ADB is required for personal training or the local connector.
- Owner selected Share training files and import draft files; no live LAN/USB bridge.
- Existing optional cloud/social/production MCP remain independent and configurable; never fake an account or weaken their guards.
- Strict context 8 MiB, proposal 64 KiB, JSON depth 16; reject unknown/duplicate keys, invalid UTF-8, wrong scalar types and nonfinite/overflowed numbers.
- Export latest 100 completed workouts, at most 200 done sets/workout, at most 50 supported saved workouts with at most 20 entries; count every truncation/unsupported omission.
- No notes, session labels, custom movement names, arbitrary modifiers, health/body/profile/photos/social/rewards/archives or unfinished workouts; bounded saved-workout display names are explicitly allowed untrusted data.
- Context IDs are random local dataset/snapshot/opaque object IDs; SHA-256 binds exact context bytes. Proposal receipt hashes exact imported proposal bytes, not SQL/canonical JSON serialization.
- Draft v1: built-in REPS only, name 1..80 code points, 1..20 entries, sets 1..50, reps 1..1000, finite/null external load 0..1000 kg, AUTO/NONE prep, 30-day maximum lifetime, five-minute future clock skew.
- Local Save alone adds one unscheduled preset plus receipt atomically; revalidate bounded 5,000+1 movement rows; no schedule/session/profile/reward/cloud writes.
- Room 50→51 additive, separate local identity/mapping/export/receipt tables; full archive replacement resets them coherently, CSV merges do not; receipt tombstones survive preset deletion.
- Exactly seven local tools, pages max 50, search max 20, rendered result max 1 MiB, stdio preparse frame max 64 KiB; stdout protocol only.
- Idempotency key 1..128 ASCII `[A-Za-z0-9._-]+`, namespace dataset/snapshot/key, journal max 1,000 proposals; retries retain identical stored bytes and no unrelated file is overwritten.
- Show real-app-style mockups before UI code. New copy resource-backed in both wording modes and seven existing translations; preserve kg/unit precision and actual app components.
- All shell commands use rtk; code discovery graph-first. Main verified commits/push authorized; no force/tags/releases/automatic Actions/cloud changes/paid accounts/system-service installation.
- Emulator-5554 only with main-checkout lock; never instrument/clear/uninstall the owner's phone. Required full gate alone, unchanged counts/budget, under 600 seconds.

## Review Focus

1. A restored/replaced database races an open review/export: old identity must never commit into replacement data (Task 1).
2. Nested private/unknown fields, malformed UTF-8/JSON and overlong data cross Android/Python: reject or explicitly bound before allocation, never leak in errors (Tasks 1–2).
3. Same proposal imported twice, concurrently, after preset deletion or with edited bytes: one durable receipt, no duplicate or silent acceptance (Tasks 1 and 3).
4. Outbox interruption, conflicting file, reparse/symlink or concurrent connector creation: no partial proposal, overwrite, path escape or dataset switch (Task 2).
5. Signed-out/unconfigured or unreachable optional cloud during onboarding/export/import: local actions remain usable and no cloud call gates them (Tasks 3–4).

---

### Task 1: Android offline contract, projections and transactional import

**Files:**
- Create `tools/local_connector/contracts/README.md`, versioned JSON schemas/fixtures and cross-language exact-byte vectors.
- Create `app/src/main/kotlin/com/ironvellum/app/domain/LocalConnectorFiles.kt` (split focused files if needed).
- Create `app/src/main/kotlin/com/ironvellum/app/data/localconnector/LocalConnectorRepository.kt`.
- Create `app/src/main/kotlin/com/ironvellum/app/data/db/LocalConnectorDao.kt` and local entities.
- Modify `data/IronvellumDatabase.kt`, `data/Repository.kt`, archive replacement transaction and schema JSON.
- Add focused JVM/Room tests under corresponding test/androidTest packages.

**Interfaces:**
- Consumes existing `WorkoutDraft`, `DraftMovements`, Seed manifest and Room DB.
- Produces immutable `LocalContextExport` (bytes/summary), private-construction `LocalDraftReview`, typed inspection/save results, and `LocalConnectorRepository.exportContext()`, `inspectDraft(bytes)`, `saveDraft(review)`.
- Expose `Repository.localConnector` for UI; no Android Uri dependency in these APIs.
- Freezes exact JSON contracts and fixtures for Task 2, including local tool argument/result schemas. Report precise signatures before Task 3; adjust downstream briefs to actual verified APIs, never guess.

- [ ] Write failing contracts for duplicate/private/unknown fields, byte/depth/type/version bounds, Unicode/numeric vectors and exact source SHA. Name explicit cases rather than mirror serializer implementation.
- [ ] Write Room cases with nested privacy canaries, 101 workouts/201 sets/51 presets, stable IDs across exports, coherent counts/progress, empty data and overflow failures. Verify absence of broad excluded state by exact projection keys.
- [ ] Write migration survival plus full-restore/open-review fencing, transactional rollback, repeated/concurrent save, conflicting bytes, expiry/prior receipt, tombstone and movement-edit/collision cases.
- [ ] Implement the smallest bounded projection/strict parser/persistence meeting spec; record generated exports before share; pin immutable review bytes; revalidate identity/snapshot/hash/movements at actual COMMIT. No UI changes.
- [ ] Generate/check shared fixtures from real Kotlin serialization and the actual full manifest; document scalar bounds and coverage/order semantics. No hypothetical Python/SQL hash equivalence.
- [ ] Run focused JVM and locked emulator tests with synthetic isolated data; capture RED/GREEN and counts. Verify old production receipt/account/migration tests remain intact. Do not run the full gate.
- [ ] Commit verified task; report API/contract paths, tested cases, findings and explicit source/build/emulator ownership release.

### Task 2: Cross-platform accountless stdio connector

**Files:**
- Create `tools/local_connector/{pyproject.toml,requirements.lock,README.md}` and package `src/ironvellum_local_connector/`.
- Create modules for strict context validation, local tools, journal/publication and bounded stdio entrypoint; package catalogue data.
- Create `tools/local_connector/tests/` with official SDK subprocess/contract/filesystem tests.

**Interfaces:**
- Consumes Task 1's exact contract/schemas/raw fixtures and full 584-movement manifest.
- Produces `python -m ironvellum_local_connector --context <absolute-file> --outbox <absolute-directory>` and exactly the seven named tools. AI settings contain command/argument arrays only.
- Proposal files are immutable exact bytes with no inferred phone acceptance; safe basenames/IDs, no absolute paths in tool output.

- [ ] Read frozen Task 1 report/contract and official pinned SDK stdio implementation. Test raw oversized frames before claiming preparse bound.
- [ ] Write failing real subprocess initialize/list/call/EOF tests, no-network guards, stdout/error canary checks, file parsing/cursor/coverage/scalar cases and cross-language fixtures. Test paths with spaces and Unicode.
- [ ] Write journal restart/idempotency/conflict/capacity and actual concurrent/crash-window/no-clobber tests. Exercise native Windows file handles/reparse/ADS/UNC protections, and supported POSIX symlink/FIFO checks on an available Linux environment; document unexecuted OS checks honestly.
- [ ] Implement local-only immutable adapters, safe path/handle operations and serialized recoverable publication. No imports of production settings/auth/backend/server or development device code; do not refactor production behavior.
- [ ] Keep pure validation duplication minimal, with contract parity tests; do not add a general shared package merely to avoid small independently versioned DTOs.
- [ ] Build/install the package into an isolated environment and run the real SDK flow there; verify bundled manifest and no launch-time installs. Check locked dependency advisories and pip consistency.
- [ ] Document Windows/Linux/macOS installation and local stdio configuration, snapshot updates, transfer, expiry and recovery. No claim cloud-only AI apps reach a local subprocess.
- [ ] Commit/report exact commands, counts, raw artifacts, supported/untested platforms and ownership release.

### Task 3: Real app UI for local AI files

**Files:**
- Create `app/src/main/kotlin/com/ironvellum/app/ui/ai/LocalConnectorScreens.kt` and bounded URI helper.
- Modify `ui/IronvellumNav.kt`, `ui/ai/McpScreens.kt` only as needed to reuse presentation, `ui/ai/DraftEntry.kt` and string resources/locales.
- Add scoped Compose/URI tests for selected file review and explicit save.

**Interfaces:**
- Consumes actual Task 1 APIs and root-shown pre-code mockups.
- Produces signed-out/unconfigured Train → AI tools, context preview/share, bounded OpenDocument import/review/save/discard, saved/already-saved/error states and explicitly optional cloud links.

- [ ] Confirm root has shown all mockups before changing product UI. Inspect existing actual app captures and use real components; no invented generic dashboard or web-style cards.
- [ ] Write failing meaningful tests for signed-out/unconfigured flow, no mutation before Save/discard/cancel, oversize/foreign/expired/read-error states and one saved preset after repeated actions.
- [ ] Implement resource-backed UI and asynchronous bounded I/O. Share a staged file through existing restricted FileProvider/EXTRA_STREAM; also offer Save file on this device with CreateDocument for local Downloads/USB transfer. Both use the same immutable preview bytes/identity; cancelled picker does nothing and failed writes never claim saved. No generic exported JSON receiver or broad storage permission.
- [ ] Use immutable handle through review; cancellation/recreation never saves automatically. Show source export date, omissions/coverage, unverified sender and exact targets; no network refresh/ack promises in local states.
- [ ] Reuse/keep independent cloud review routes and AccountRepository scope guards. Maintain unit preferences, accessibility, clipping and both wording modes.
- [ ] Run focused JVM/locked Compose/URI checks; capture actual dark/plain and Vellum/themed screenshots and inspect them. Drive real chooser/picker where practical using isolated synthetic files. Report any fixture-only seam.
- [ ] Commit/report raw screenshots, test counts, actual UI behavior and ownership release.

### Task 4: Backend-free releases, optional self-hosting and cross-layer proof

**Files:**
- Modify `app/build.gradle.kts`, `docs/DEVELOPMENT.md`, `README.md`, `COSTS.md`/privacy docs only where behavior changed.
- Add explicit local-only release configuration tests and offline acceptance tests.
- Add `tools/self_host/` bootstrap/lock/docs/tests; update `supabase/SELF_HOSTING.md` and `web/README.md` component map.
- Add `tools/local_connector/tests/verify_android_roundtrip.py` (or focused equivalent) and final release evidence.

**Interfaces:**
- Consumes verified Tasks 1–3 artifacts; no backend is needed for their runtime.
- Produces documented `-PlocalOnly=true` FOSS release configuration, optional PC Supabase setup and actual Kotlin → installed stdio package → Kotlin import/save proof.

- [ ] Write a failing local-only build-configuration check: default URL/key/Google ID absent, normal validation unchanged, partial custom settings never silently select shared defaults.
- [ ] Implement explicit flag and compile/inspect actual generated configuration for local-only and normal builds. Do not remove ordinary backend validation or break users' existing settings.
- [ ] Verify fresh local onboarding/log/complete/reopen/export/import without account/server and optional unavailable-backend failure independence; use existing test machinery, not phone or real cloud. Keep meaningful assertions and isolated data.
- [ ] Add safe optional stack bootstrap pinned to an upstream Supabase source revision with verified download hash. Generate secrets outside checkout; refuse existing/nonempty data before fresh schema application, persist storage, support explicit start/stop/status/backup instructions and never invoke reset automatically.
- [ ] Validate helper safety with disposable/fake-engine tests and syntax/config checks; run the actual container stack only if a usable runtime already exists. Do not install system services or claim unexecuted containers passed. Document phone-reachable trusted HTTPS and private/local setup choices without requiring paid domains or maintainer accounts for the offline path.
- [ ] Run synthetic cross-layer flow using actual Kotlin-exported context bytes, installed official SDK subprocess reads/proposal bytes and actual phone parser/Room Save. Prove returned source identity and same-file repeated import, modified-ID conflict, wrong dataset and no Save side effect before explicit action. Isolate all Android data; report exact byte digests.
- [ ] Update user-facing setup/component docs and verification evidence, keeping optional hosted production MCP clearly separate. Release notes must not imply AI client subscriptions or initial dependency downloads disappear.
- [ ] Run focused checks and commit/report. Coordinator alone runs the final complete gate under 600s and whole-change independent review, then records final evidence and pushes verified main.
