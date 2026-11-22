# Complete Local Training Read Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all valid training readable by the personal connector, including the owner's missing 149 sets and seven saved workouts.

**Architecture:** Version the read snapshot independently of the unchanged write proposal. Android projects complete, privacy-filtered training into a strict v2 contract; the PC validates either v1 or v2 and serves the same seven tools with version-appropriate result schemas.

**Tech Stack:** Kotlin/Room/kotlinx serialization, Python/jsonschema/official MCP SDK.

**Spec:** docs/superpowers/specs/2026-11-22-complete-local-training-read.md

## Global Constraints

- New exports use training-context schemaVersion 2. Preserve the existing schemaVersion 1 contract and fixtures and continue accepting them on the PC.
- Proposal/draft version 1, exact-byte provenance, explicit phone review and Save, idempotent receipts, and built-in REPS-only draft admission remain unchanged.
- Keep the 8 MiB file limit, JSON depth 16, strict number and text validation, private filesystem handling and 1 MiB tool result limit.
- Remain fully local, no server/sign-in/network connection required.
- Never run destructive tests on the owner's phone; never commit personal data.
- Run the local full gate alone, strictly under 600 seconds.

## Review Focus

- Legacy metric units and assistance must not inflate rep/external volume totals (Task 1 and 2 parity tests).
- Unticked warm-up rows must remain visible without counting as completed work (Task 1 and 2 fixtures).
- Custom or ambiguous movement references must remain readable and non-draftable (both tasks).
- Rich scheme and preparation references must preserve ordering and exclude private notes/raw IDs (Task 1 fixtures, Task 2 reader tests).
- Files beyond old selection limits must remain complete; resource overflow must fail atomically (Task 1 export, Task 2 validation/tool limit tests).

## Controller verification and delivery

- [ ] Independently review each task then one whole change review, resolve findings, and run the full local gate alone under600 seconds.
- [ ] Verify running preview in both wording modes and font scale with synthetic emulator data under the shared lock.
- [ ] Back up and safely update phone without clearing/uninstalling; refresh private PC package and training file, exercise all seven actual MCP calls, verify 19/309/7 and semantic fidelity. Leave draft Save to owner.
- [ ] Record release evidence, commit and push verified main, update scoped memory and close checkpoint.

### Task 1: Complete Android projection and frozen v2 contract

**Files:** Modify app/src/main/kotlin/com/ironvellum/app/data/{db/LocalConnectorDao.kt,localconnector/LocalContext.kt,localconnector/LocalConnectorRepository.kt}; add focused v2 projection/model helper files in localconnector as needed. Add v2 schema/fixtures and documentation under tools/local_connector/contracts. Modify existing local AI preview resources (values and seven locale directories), and minimal LocalConnectorScreens.kt copy binding if needed. Extend existing localconnector unit/instrumented tests; locate via graph first.

**Interfaces:** Consumes current Room training entities/domain decoders and immutable LocalContextExport API. Produces training-context v2 schema, synthetic valid/invalid fixtures, documented exact unit/progress semantics and complete Android bytes. Keep LocalExportSummary coverage fields usable by existing preview. LocalSnapshot provenance still references exact bytes.

- [ ] Write meaningful failing tests for 19 workouts/309 sets/seven rich saved workouts, every metric/kind and modifier, assisted/custom/ambiguous movements, complete preparation snapshots/choices, and exact private-note omission.
- [ ] Run focused tests and record expected RED evidence before implementation.
- [ ] Add strict versioned v2 contract with explicit fields/units and fixture, leaving all v1 files byte-identical. Implement full read projection/metric-aware progress, using bounded iteration or early limit checks to avoid unbounded output construction. Invalid or oversized history must record no snapshot and expose the existing clear error path.
- [ ] Update actual preview wording using shown mockup: all completed history; all movement types/set kinds/modifiers; names/targets/schemes/preparation; metric distinctions; disclose custom/routine names; retain private fields. Preserve layout, accessible scaling and all seven translations.
- [ ] Run focused local Android tests/compile and contract checks. Tests must also exceed old100/200/50/20 read limits, cover zero values/unknown references, and demonstrate draft import restriction unchanged. Do not run the full gate or touch the phone; controller will run integrated verification alone.
- [ ] Self-review and commit with house style; write report containing commands, RED/GREEN evidence, exact v2 model/progress decisions and remaining concerns. Do not push yet.

### Task 2: PC v2 reader and tool compatibility

**Files:** Modify tools/local_connector/src/ironvellum_local_connector/{contract.py,snapshot.py,server.py,__init__.py} and focused helper modules if needed; versioned tool schema additions under contracts; relevant Python tests/README/package metadata; update the dated training-file scope paragraph in PRIVACY.md. Preserve journal/storage/stdio behavior except narrow necessary interfaces.

**Interfaces:** Consumes Task 1 frozen training-context v2 schema/fixtures and documented progress semantics. Produces strict v1+v2 Snapshot reader, metric-aware independent validation and version-correct outputs from existing seven tools. Snapshot dataset/snapshot/sha256 properties remain identical interfaces for Journal.

- [ ] Write reader/tool tests showing current reader rejects v2 fixture, then record RED. Cover full 309/7 counterpart, old v1 fixtures, metric/load/unit parity, names as untrusted display and unsupported rich/custom draft creation refusal.
- [ ] Implement strict v2 validation, independent aggregate checks, referential integrity, unique/scoped opaque IDs, original byte SHA and version-appropriate advertised output schemas. search_movements should expose referenced custom/ambiguous movements as read-only while retaining built-in draft policy. Existing pagination/cursor and bounded result behavior remain.
- [ ] Add malformed rich-context tests for mismatched totals, wrong units/references and resource constraints; preserve all existing parser/journal/private-files tests and v1 fixtures. Document latest file format, reinstall/refresh steps and remaining write limits plainly. Replace PRIVACY.md's obsolete 100/200/50 cap and custom-name exclusion statement with accurate complete-training scope, included names and retained exclusions; do not change unrelated cloud policy.
- [ ] Run the full Python package suite once, self-review and commit. Report test evidence, versioned schemas and any concerns. No full gate, phone operations or push.

### Task 3: Requested Train section order

**Files:** Modify app/src/main/kotlin/com/ironvellum/app/ui/train/PresetsScreen.kt; update existing TrainMusclesCardTest and LocalConnectorNavigationTest or other concretely affected navigation tests only as needed.

**Interfaces:** Existing musclesCard() and draftEntry() composable slots and all callbacks remain unchanged. This is independent of read file format and changes section placement only.

- [ ] Inspect existing navigation/layout tests and capture the current running screen as the before evidence. This reversible placement-only change uses existing tests and actual screenshots; do not add new tests mirroring the section order.
- [ ] Move the existing mobile Muscles card to the first card under Train header (before lead/live card if present); retain top-right placement in the existing expanded two-column layout. Move draftEntry() from the top to directly after Exercises/history links and before Recent workouts. Do not redesign components or change copy.
- [ ] Update existing navigation expectations to scroll to the deliberately lower AI tools card, retaining assertions for actual routing and back navigation. Keep all 200% accessibility/overflow assertions and existing wide-layout tests.
- [ ] Run focused existing emulator tests under the shared lock, pin emulator-5554. Capture actual top and lower views in plain and themed modes using existing opt-in shots mechanism or pinned ADB screenshots. Visually verify Muscles above weekly plan, AI below Exercises/before Recent, no-history availability and exactly one Muscles card; retain wide-screen checks. No phone operations/full gate. Self-review, commit and report exact commands, before/after evidence and screenshots. No push.
