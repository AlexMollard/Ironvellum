# AI Phone Round Trip Implementation Plan

Historical plan: PC connector paths below refer to the
[archive-personal-connector branch](https://github.com/AlexMollard/Ironvellum/tree/archive-personal-connector).
Phone sharing, import and review remain supported; current hosted integration
lives in [production MCP](../../../server/production_mcp/README.md).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Share useful training instructions with phone AI apps, and receive, edit and explicitly save a workout from any AI without requiring a prior export.

**Architecture:** Add a separate bounded portable JSON contract and canonical import receipts alongside the unchanged source-bound protocol. Both incoming files and paste feed RAM-only editable review; outbound AI packages embed the unchanged training JSON with static return instructions. Native Compose cards and the approved mockups supply the presentation.

**Tech Stack:** Existing Kotlin, Compose, Room, kotlinx.serialization and Android content-URI/FileProvider APIs; no new dependencies or remote services.

**Status:** All four tasks and reviews complete. Final integrated source c3de5ee0 passed the full local gate below 600 seconds; see docs/RELEASE_CHECKLIST.md for both run records and verification limits. Remaining desktop native visual/interaction checks were superseded by the owner's no-computer-control instruction and remain unverified.

**Spec:** `docs/superpowers/specs/2026-11-22-ai-round-trip-ux-design.md`

## Global Constraints

- No required server, sign-in, paid hosting, ADB, live pairing or AI API; account independence is mandatory.
- Preserve legacy proposal-v1, training context-v1/v2 and their frozen fixtures. New portable imports are not fabricated exports.
- Use only current fixed sets/repetitions, built-in non-assisted draft-eligible movements; never flatten other metrics or schemes.
- Explicit unscheduled Save only; editing creates no persistent workout. Original source-bound identity and bytes survive local edits.
- New copy is resources, plain/themed and all seven translations. Preserve Muscles first and AI before Recent on Train.
- Approved boards in the spec govern hub/share/paste/edit/matching; show any additional layout before its UI code.
- All device tests use the locked emulator; never clear, uninstall or run instrumentation on the owner's phone.
- Commit verified units to main and push. Final full local gate runs alone and must finish under 600 seconds.

## Review Focus

1. Same portable workout from file, paste, different JSON spacing or decimal spelling must produce one receipt, including after local editing/deletion (Task 2).
2. A text/plain CSV must still reach CSV import; an incoming JSON or expired grant must never be interpreted as CSV data (Task 3).
3. An edited source-bound proposal must retain original identity while saving only explicitly reviewed targets; changed/deleted movement IDs reject the save (Task 2).
4. Empty paste, malformed Unicode, huge numbers, surrounding prose and ambiguous movement names must produce actionable recovery without a partial write (Tasks 1 and 3).
5. Recipient dismissal, missing AI app and grant revocation must leave the preview reusable; chooser presence must not be presented as delivery (Task 4).

## File boundaries

- `domain/PortableWorkout.kt`: portable contract, shared draft serialization, canonical digest and AI instructions.
- `data/localconnector/PortableWorkoutRepository.kt`: private review handles and transactional receipts.
- Existing `LocalConnectorRepository.kt`: source-bound edit overlay, retaining its private original-byte handle.
- `data/IncomingAiWorkout.kt`: bounded URI reads and incoming-content classification, independent of CSV parsing.
- `ui/ai/WorkoutImportScreens.kt`: paste/edit/match staging and UI; `LocalConnectorScreens.kt` remains hub/preview/saved/navigation host.
- `ui/ai/AiTrainingPackage.kt`: bounded AI-ready attachment; keep raw training staging compatible.
- Contract docs/schema/sample live under `docs/training-contracts/`; portable support on the phone does not depend on the desktop plan.

### Task 1: Portable contract and common draft serialization

**Files:** Create `app/src/main/kotlin/com/ironvellum/app/domain/PortableWorkout.kt`, `app/src/test/kotlin/com/ironvellum/app/domain/PortableWorkoutTest.kt`, `docs/training-contracts/portable-workout-v1.schema.json`, `docs/training-contracts/portable-workout-v1.example.json`; modify `domain/WorkoutDraft.kt` and `docs/training-contracts/README.md`.

**Interfaces:** `PortableWorkout.parse(bytes: ByteArray): PortableWorkout`; properties `draft: WorkoutDraft`, `fingerprint: String`; `WorkoutDraft.toJson(): JsonObject`; `PortableWorkout.instructions(): String`. Portable root is exactly `{type,schemaVersion,name,warmup,cooldown,entries}`; `type="ironvellum.workout"`, schemaVersion=1; the remaining fields use existing WorkoutDraft validation. Unknown normalized exercise keys may parse and must be resolved explicitly during review. No source IDs, hashes, sender or timestamps are accepted in this format.

- [ ] Write tests `fileAndPasteHaveSameIdentity`, `canonicalDecimalsAndObjectOrder`, `rejectUntrustedMalformedInput`, `unknownMovementRemainsUnresolved`. Assert whitespace/key order and load `40`, `40.0`, `4e1` have the same digest; name/entry order/target changes differ. Preserve exact positive decimal values; do not round through Double.
- [ ] Run `:app:testFossDebugUnitTest --tests '*PortableWorkoutTest'` with the existing Gradle wrapper; confirm new tests fail before implementation.
- [ ] Implement strict UTF-8, 64 KiB limit, existing LocalJsonReader duplicate/depth16/token128 checks. Accept raw JSON or one entire fenced `json` block (optional fence only at the outer boundary), not surrounding prose/multiple blocks. Bound original bytes before stripping fences. Keep legacy parser unchanged.
- [ ] Implement canonical UTF-8 serialization with fixed member order, normalized decimal tokens and no insignificant whitespace; SHA-256 the portable canonical bytes. Reuse WorkoutDraft's 1..80 scalar name, 1..20 entries, key1..128, sets1..50, reps1..1000, load null/0..1000, AUTO/NONE rules. Unknown keys must remain syntactically normalized; no fuzzy matching.
- [ ] Publish schema/example and static instructions explaining supported values, kg, explicit movement keys, no arbitrary prose, and review-before-Save. Unit tests parse the example and verify instruction sample against the parser.
- [ ] Run new tests and existing `LocalConnectorFilesTest`/`LocalConnectorVectorTest`; commit and push the verified contract unit.

### Task 2: Persistent portable receipts and editable source-bound reviews

**Files:** Create `data/localconnector/PortableWorkoutRepository.kt`, `app/src/androidTest/kotlin/com/ironvellum/app/data/PortableWorkoutRepositoryTest.kt`; modify `data/localconnector/LocalConnectorRepository.kt`, `data/db/LocalConnectorDao.kt`, `data/IronvellumDatabase.kt`, `data/Repository.kt`, `app/src/androidTest/kotlin/com/ironvellum/app/data/LocalConnectorRepositoryTest.kt`, `LocalConnectorMigrationTest.kt`, `app/src/test/kotlin/com/ironvellum/app/data/MigrationRegistryTest.kt`; add exported Room schema52.

**Interfaces:** Reuse `LocalInspection`/`LocalSave` semantics where applicable. Portable API: `inspect(bytes: ByteArray): PortableInspection`, `reviewEdits(review: PortableReview, draft: WorkoutDraft, selectedMovementIds: Map<Int, Long> = emptyMap()): PortableInspection`, `save(review: PortableReview): LocalSave`; ready handle exposes final `draft` and `unresolvedKeys: Set<String>` for display, but Save requires zero unresolved keys. Source-bound API: add final `draft` to `LocalDraftReview`, `reviewEdits(review: LocalDraftReview, draft: WorkoutDraft, selectedMovementIds: Map<Int, Long> = emptyMap()): LocalInspection`; existing `inspectDraft`/`saveDraft` remain compatible. The optional selection map pins the exact eligible gallery ID at each final entry index. Changed exercise keys require explicit selection; targets alone retain original IDs. Use the latest reviewed handle for subsequent edits. Add/reorder controls are outside this pass.

- [ ] Add failing Room tests: canonical repeats across file/paste, edited first-save then repeat, deleted saved preset then repeat, concurrent double-save, failed insert rollback, wrong/stale edited movement ID, legacy original-byte conflict/expiry/source checks after editing, and no account requirement. Assert no row exists before Save and exactly one unscheduled preset plus receipt after success.
- [ ] Add migration51→52 test preserving sentinel training and all existing local receipts. New `portable_workout_receipts` primary `(datasetId,fingerprint)` contains localPresetId/savedAtMs, no preset FK. Use current identity generation (create if absent) and clear portable receipts when archive replacement clears local identity/receipts. Verify old imports remain non-resurrecting tombstones.
- [ ] Run targeted emulator tests through `tools/device.py lock`, explicitly pinning emulator-5554; confirm intended failures.
- [ ] Implement private immutable review handles retaining original portable fingerprint or original source-bound bytes and provenance. Validate final edited WorkoutDraft using the same limits/catalogue policy and pin selected movement IDs. Keep decimal targets in staging until existing storage conversion. Transactionally recheck dataset, receipt, source/time where required, and current movement IDs before inserting preset/entries/preparation choices/receipt. Repeat returns original ID without overwriting edits, including deleted presets.
- [ ] A portable unresolved key can be shown in review, but only an explicit eligible gallery selection may replace it. Do not automatically substitute by approximate name. Test rejection of changed keys without explicit IDs and supplied IDs inconsistent with the eligible movement. Source-bound original invalid files keep existing refusal behavior; edits cannot bypass provenance checks.
- [ ] Run focused migration/repository tests, existing source-bound regressions and schema registry tests; commit and push this coherent storage unit.

### Task 3: Direct receive, paste and native editable review

**Files:** Create `data/IncomingAiWorkout.kt`, `ui/ai/WorkoutImportScreens.kt`, `app/src/androidTest/kotlin/com/ironvellum/app/data/IncomingAiWorkoutTest.kt`; modify `MainActivity.kt`, `data/IncomingWorkoutShare.kt`, `ui/IronvellumNav.kt`, `ui/ai/LocalConnectorScreens.kt`, `AndroidManifest.xml`, all eight `res/values*/strings.xml`, and existing `LocalConnectorScreensTest.kt`/`LocalConnectorNavigationTest.kt`/`IncomingWorkoutShareTest.kt`.

**Interfaces:** `IncomingAiWorkout` accepts ACTION_SEND/ACTION_VIEW content URIs or explicit shared workout code, returning a bounded classified payload (portable/source-bound/CSV/unsupported/unreadable). `Routes.aiWorkoutImport(uri: String)` opens the native importer. Preserve CSV route and existing notification routing. Review UI consumes Task2 handles, creates edited WorkoutDraft via Task1 serialization/parser, and calls reviewEdits then Save.

- [ ] Add failing integration cases for real SEND/VIEW application/json, text/plain JSON, octet JSON, text/plain CSV, unsupported content, missing/revoked grants, repeated intent and recreation. Use synthetic content providers, never unrestricted file paths. CSV may use its existing32 MiB cap; AI candidate bytes always cap64 KiB. Content sniffing determines the route, MIME/extension only hints.
- [ ] Add UI tests: empty paste Review disabled; valid paste opens review with exact load; malformed paste retains recoverable input; edits do not persist before Save; unmatched key disables Save until explicit eligible selection; correction of sets/reps/load; explicit Save/View and duplicate/deleted receipt handling. Cancel/read-loss must permit retry without showing stale review or a saved claim.
- [ ] Run new tests to prove failure; implement native cards from approved boards. Reuse existing exercise picker/editor affordances and eligibility filters. The universal picker currently offers custom creation/editing: a default-preserving `allowCustom=false` capability or existing lower-level native picker is permitted for this eligible-choice-only flow; no custom movement writes during review. Add no new layout without owner-visible mockup first. File picker and paste converge on the same parser/repository. Keep staging RAM-only and expose clear process-loss retry.
- [ ] Add manifest receiving filters and incoming routing before CSV fallback, handling granted streams safely on I/O; navigation must not carry raw JSON in URLs, logs or persistent saved state. Avoid broad arbitrary file/content access and avoid intercepting unrelated text unnecessarily.
- [ ] Add resource copy in both modes and all seven translations; check plain/themed, phone/wide/short/200% layouts, actionable accessibility labels and exact unit display on the running emulator. Capture synthetic screenshots through the emulator lock.
- [ ] Run focused integration/UI/CSV tests and resource contracts; commit and push.

### Task 4: Outbound AI package and handoff guidance

**Files:** Create `ui/ai/AiTrainingPackage.kt`, `app/src/test/kotlin/com/ironvellum/app/ui/ai/AiTrainingPackageTest.kt`; modify `ui/ai/LocalConnectorScreens.kt`, `ui/ai/LocalTrainingShare.kt`, `ui/ai/LocalConnectorStreams.kt` if needed, resources and UI/share tests, `tools/local_connector/README.md`, `docs/RELEASE_CHECKLIST.md`.

**Interfaces:** `AiTrainingPackage.encode(export: LocalContextExport, question: String): ByteArray`; `stageAiShare(directory: File, name: String, bytes: ByteArray): File`. AI-ready `.md` attachment includes fixed instructions, optional question≤2000 Unicode scalars and the unchanged UTF-8 training JSON in a fenced section. Package cap9 MiB. A separate raw JSON Save copy/PC share preserves exact bytes.

- [ ] Write failing tests for exact context embedding, escaped/untrusted training names, question bounds,9 MiB package cap, atomic completed-file publication, provider cancellation/missing app, immutable retries and pruning only owned staged files after24h. Instructions must clearly distinguish user data from instructions and never include notes/health/private fields not already exported.
- [ ] Implement optional question, Share with AI/eligible installed-app shortcuts plus Android chooser, Copy instructions and secondary raw Save copy. Use safe Android package resolution rather than broad app enumeration. Remember last selected eligible app locally, allow other apps always; missing app falls back to chooser. Do not record delivery or send automatically. Keep unique package identity per question/content so retries cannot reuse old bytes for a changed question.
- [ ] Verify instructions sample imports through Task1; synthetic receiver test asserts attachment/grants/MIME. Actual provider acceptance, if tested manually, must be reported separately from chooser eligibility and use synthetic data only.
- [ ] Update phone/PC guide to show the three revised journeys and desktop agent setup link; no claim desktop refresh exists until its independent plan passes. Preserve CRLF release-checklist formatting.
- [ ] Run focused unit/UI tests; commit and push. After integration settles, run the full local gate alone under600s, plus connector regression suite when shared contract packaging changes. Record actual evidence, limitations and screenshots; no phone installation unless requested.

## Self-review / execution

Each spec requirement for phone receive, editable review, portable identity, sharing and recovery maps to Tasks1–4. Desktop watch/setup/window are deliberately isolated in the companion desktop plan. All five Review Focus cases have owning tests above. Existing installed app/data are unaffected by planning. Preserve the user's agent-driven execution method; use task-by-task implementer/reviewer gates and a final integration review.
