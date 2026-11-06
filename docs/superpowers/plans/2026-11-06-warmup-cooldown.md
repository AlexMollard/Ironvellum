# Warmup and Cooldown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Attach reusable guided warmups and cooldowns to saved workouts, retaining progress safely and leaving workout scoring unchanged.

**Architecture:** A focused preparation domain and Room-backed facade own routines, workout choices and immutable per-session snapshots. Separate Compose screens supply the manager, selector and runner; the existing session and celebration screens only host entry points and the cooldown summary card.

**Tech Stack:** Kotlin, Jetpack Compose, Room, coroutines/Flow, existing pure Kotlin archive codecs and native vector exercise art.

**Spec:** `docs/superpowers/specs/2026-11-06-warmup-cooldown-design.md` (read before implementation).

## Global Constraints

- Room v46 → v47; archive format v14 → v15; older archives still restore.
- Every starter movement uses one of the thirteen existing first-class gallery exercises in the spec, with existing image, guide, muscles and metric. March in Place and new catalogue/art additions are explicitly excluded.
- All new screen copy is paired plain `key` and themed `key__t` in `res/values/strings.xml`.
- Preparation writes no set rows and changes no stats, XP, achievements, strength, progression, volume or rewards.
- Keep existing legacy warmup exercises and loaded warmup ramps; show one merged general warmup without duplicate exercise IDs.
- Snapshots protect ongoing sessions from routine/preset edits and deletions; timers/progress survive rotation and process recreation.
- Run tests locally; emulator operations use `tools/device.py lock` or the gate; never instrument the owner's phone or add Actions triggers.
- Commit and push each coherent verified unit on main using the repository's house style. Do not rewrite history.

## Review Focus

1. Restoring an archive whose exercise and routine IDs collide with local IDs resolves by names and remaps every reference (Task 1).
2. Editing/deleting a shared routine during an ongoing workout leaves both of its snapshots and progress unchanged (Task 1).
3. A process killed after seal or during cooldown resumes the sealed summary without paying twice (Task 3).
4. A legacy workout warmup also present in the selected routine runs once with the legacy target retained (Tasks 1 and 3).
5. A full keyboard, long names and 200% font scale keep selector/editor/runner actions reachable and announceable (Tasks 2 and 3).

## Shared interfaces

All new types live in `com.ironvellum.app.domain` (`Preparation.kt`, `PreparationStarters.kt`, `PreparationProgress.kt`, `PreparationCodec.kt`). Enum names and step fields are fixed by the spec. Define:

```kotlin
data class PreparationChoice(val mode: PreparationMode = PreparationMode.AUTO, val routineId: Long? = null)
data class PreparationRoutine(val id: Long = 0, val kind: PreparationKind, val name: String, val steps: List<PreparationStep>)
data class PreparationSnapshot(val kind: PreparationKind, val name: String, val steps: List<PreparationStep>, val sourceRoutineId: Long? = null)
data class SessionPreparation(val sessionId: Long, val snapshot: PreparationSnapshot, val progress: PreparationProgress)
enum class PreparationAction { START, PAUSE, RESUME, COMPLETE_UNIT, SKIP_UNIT, SKIP_ROUTINE }
```

`PreparationProgress` fields/status are specified in the design. Expose `val currentUnitKey: String`, formatted `step:round:repeat`. Supply `PreparationProgress.advance(snapshot: PreparationSnapshot, action: PreparationAction, nowMs: Long, expectedUnitKey: String? = null): PreparationProgress` as a pure reducer and `remainingMs(nowMs: Long): Long`. COMPLETE_UNIT and SKIP_UNIT require `expectedUnitKey == currentUnitKey`; missing or stale keys are no-ops. Other actions require no key. The facade compares the key against persisted progress inside the transaction before writing. Codec signatures: `PreparationCodec.encodeSteps(steps: List<PreparationStep>): String`, `decodeSteps(json: String): Result<List<PreparationStep>>`; equivalent `encodeSnapshot/decodeSnapshot` and `encodeProgress/decodeProgress` pairs.

Facade `com.ironvellum.app.data.PreparationRepository(db: IronvellumDatabase)` produces:

```kotlin
fun observeRoutines(kind: PreparationKind): Flow<List<PreparationRoutine>>
fun observeChoices(presetId: Long): Flow<Map<PreparationKind, PreparationChoice>>
fun observeSession(sessionId: Long, kind: PreparationKind): Flow<SessionPreparation?>
suspend fun saveRoutine(routine: PreparationRoutine): Long
suspend fun deleteRoutine(id: Long)
suspend fun choices(presetId: Long): Map<PreparationKind, PreparationChoice>
suspend fun setChoices(presetId: Long, choices: Map<PreparationKind, PreparationChoice>)
suspend fun ensureSession(sessionId: Long): Unit
suspend fun changeSession(sessionId: Long, kind: PreparationKind, choice: PreparationChoice): Unit
suspend fun act(sessionId: Long, kind: PreparationKind, action: PreparationAction, nowMs: Long, expectedUnitKey: String? = null): Unit
```

`ensureSession` snapshots both missing kinds without replacing any existing row; it reads session/preset/exercise/sets through the shared db. `changeSession` replaces only that session's chosen kind after explicit confirmation when it has progress, resetting progress; it never edits the saved workout. An empty or None selection has no general steps but may retain explicit legacy warmups. Missing saved routine falls back to Auto. `setChoices` validates kind consistency and is safe inside the enclosing `savePreset` transaction.

`PreparationStarters.suggest(kind: PreparationKind, working: List<Exercise>, catalogue: List<Exercise>): PreparationSnapshot` implements the exact classification/table in the spec. `templates(kind: PreparationKind, catalogue: List<Exercise>): List<PreparationSnapshot>` supplies three editable-copy templates. No anonymous exercise fallback: a missing canonical movement is a catalogue error caught by tests.

## Task 1: Preparation domain, Room and archive survival

**Files:** Create domain files named in Shared interfaces, `data/PreparationRepository.kt`, `data/db/PreparationEntities.kt`, `data/db/PreparationDao.kt`; modify `domain/Models.kt`, `data/IronvellumDatabase.kt`, `data/Repository.kt`, `domain/ExportWriter.kt`, `domain/ExportReader.kt`, existing custom-exercise usage/twin conversion implementation and Room schema export `app/schemas/com.ironvellum.app.data.IronvellumDatabase/47.json` (confirm existing schema directory). Tests: `domain/PreparationStartersTest.kt`, `PreparationProgressTest.kt`, `PreparationArchiveTest.kt`, androidTest `data/PreparationPersistenceTest.kt` and existing migration suite.

**Interfaces:** Produces all Shared interfaces. `Repository.val preparations` is a stable facade instance. Extend `WorkoutPreset` with `preparation: Map<PreparationKind, PreparationChoice> = emptyMap()`. Existing `savePreset(presetId: Long?, name: String, note: String, scheduledDay: Int?, entries: List<PresetDraftEntry>, preparation: Map<PreparationKind, PreparationChoice>? = null): Long` keeps old callers source-compatible and preserves choices when null. Existing `startSessionFromPreset(presetId: Long, override: SessionOverride = SessionOverride.NONE): Long` remains unchanged externally; invoke `ensureSession` within `startFromPreset`'s transaction after set creation. `trialWarmups(sessionId: Long)` is retained for compatibility but UI will move to snapshots in Task 3.

- [ ] Write failing catalogue assertions that all thirteen exact canonical starter names resolve once with existing art and guides; do not add new gallery entries or art. Read the applicable hand-edited-catalogue-invariants and monarch-exercise-economy-audit skills before touching starter resolution. Write failing domain tests for each exact starter order/target, all group classifications including duplicate work IDs and empty/activity-only workouts; test positive bounds, sides/directions/round advancement, timer five-second readiness, pause/resume and no auto-advance at expiry.
- [ ] Run focused JVM tests to establish red, then implement domain and pure codecs with exact public names above.
- [ ] Write failing Room tests: migration 46→47 retains preset/sets/profile; save/update default preservation; deleting routine changes only future attachments to Auto; editing/deleting routine or preset cannot change a session snapshot; reused live session retains progress; legacy ongoing session snapshots once; duplicate legacy/general exercise retains legacy target; None retains explicit legacy warmups; custom exercise deletion/convert counts or remaps all references.
- [ ] Read the applicable room-migration-data-survival-test skill. Add three entities, DAO, migration and exported schema. Implement facade transactions and the `Repository` seams. Use typed domain values above JSON at the repository boundary. Preserve empty personal libraries on upgrade; starter templates are not silently inserted as user routines.
- [ ] Write archive tests for v15 local/cloud round trips, empty/absent sections in v14, routine/preset/exercise ID collisions, unused personal routines, custom exercises used only by routines, sealed snapshots and paused remaining timer state. Assert malformed routine data reports a restore problem rather than silently binding an unrelated numeric ID. Existing training sections still restore.
- [ ] Add optional v15 routines/attachments/sealed preparation sections to writer/reader and export/import paths. Use existing `resolveExercise(rawName)` in `importArchive`, restore routines before attachments, and session progress after new session IDs exist. Archive export currently uses `completedSessionsWithSets`: preserve that active-session exclusion. No cloud feed/schema changes.
- [ ] Run focused JVM and Room tests using `rtk proxy python tools/device.py lock --label preparation-persistence -- .\gradlew.bat :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ironvellum.app.data.PreparationPersistenceTest`; run existing migration/archive tests too. Commit and push the persistence unit after green checks.

## Task 2: Routine manager, selector and saved-workout attachments

**Files:** Create `ui/preparation/PreparationLibraryScreen.kt`, `PreparationEditorScreen.kt`, `PreparationSelectorScreen.kt`, `PreparationViewModels.kt`, `PreparationComponents.kt`; modify `ui/train/PresetEditorScreen.kt`, `ui/IronvellumNav.kt` (contains Routes), `ui/settings/SettingsScreen.kt` and `SettingsPage.kt`, `res/values/strings.xml`. Tests: androidTest `ui/PreparationLibraryFlowTest.kt`, `ui/PreparationAttachmentFlowTest.kt`.

**Interfaces:** Consumes Task 1 facade/domain. Public composables: `PreparationLibraryScreen(initialKind: PreparationKind, onEdit: (Long?, PreparationKind) -> Unit, onBack: () -> Unit)`; `PreparationEditorScreen(routineId: Long?, kind: PreparationKind, starterIndex: Int? = null, onDone: () -> Unit)`; `PreparationSelectorScreen(kind: PreparationKind, presetId: Long?, sessionId: Long?, onSelected: (PreparationChoice) -> Unit, onBack: () -> Unit)`. Add callbacks to existing PresetEditorScreen as optional/default-compatible as needed. Its EditorUi and baseline carry both choices; Save sends them with savePreset. Never persist an attachment merely by previewing it.

- [ ] Write flow tests: empty library → copy full starter → reorder/edit target/note → save → attach to workout → reopen unchanged; separate warmup/cooldown choices; None persists; changing Auto workout groups changes preview; edits/discard cancellation and deletion confirmation behave as approved. Assert choices survive another editor operation and unrelated generator updates.
- [ ] Run to establish red; implement view models with SavedStateHandle drafts, validation and async error surfaces, then focused reusable Compose screens matching the approved mockup and existing Ink/EntryCard/DockedActionBar components.
- [ ] Add library/editor/selector Routes and navigation arguments in IronvellumNav. Return editor choice to its owning entry SavedStateHandle; do not recreate/lose the workout draft when returning. Add Settings entry and workout Preparation & recovery section. Use the existing ExercisePickerSheet and gallery guide/detail mechanism, never a second anonymous movement list.
- [ ] Add paired plain/themed strings for all new copy, starter titles, empty state, validation, discard/delete and accessibility actions. Timer targets, names and notes are data; UI labels are resources. Run the hardcoded-literal ratchet and update reduced baselines if applicable.
- [ ] Run flow tests at normal and 200% font scale; test long user names, keyboard open, dark/light appearance, all reorder/remove/save controls reachable. Commit and push manager/attachment unit after checks pass.

## Task 3: Runner and session/summary integration

**Files:** Create `ui/preparation/PreparationRunnerScreen.kt`, `PreparationRunnerViewModel.kt`, `PreparationOfferCard.kt`, `PreparationSummaryCard.kt`; modify `ui/train/SessionScreen.kt`, existing `ui/train/TrialCelebration.kt` (locate its definition through graph if a differently named file), `ui/IronvellumNav.kt`, existing `domain/CelebrationFlow.kt` only if required for summary recovery, and `res/values/strings.xml`. Tests: androidTest `ui/PreparationSessionFlowTest.kt`, `ui/PreparationResumeTest.kt`; JVM extend PreparationProgressTest.

**Interfaces:** Consumes Task 2 `ensureSession`, `observeSession`, `changeSession` and `act`. Public `PreparationRunnerScreen(sessionId: Long, kind: PreparationKind, onBack: () -> Unit, onComplete: () -> Unit)`. Runner state belongs to Room and its view model, not remember-only fields. `PreparationOfferCard(state: SessionPreparation, onBegin: () -> Unit, onChange: () -> Unit, onSkip: () -> Unit)` and `PreparationSummaryCard` use the same data with summary-specific copy. Existing Finish enum retains XP→LEVEL→DEEDS→SUMMARY→PHOTO→ROUTINE. Add summary slot `preparationContent: @Composable () -> Unit = {}` to the actual celebration summary component.

- [ ] Write failing reducer tests for repeated COMPLETE_UNIT/SKIP_UNIT with the same captured displayed `currentUnitKey` (exactly one transition), missing or stale keys (no transition), skipped units, both directions/sides, multiple rounds, readiness interrupted by pause, rotation-equivalent reload, invalid/reboot deadline recovery and explicit target-reached completion. Exercise the same key check through the Room transaction to reject queued duplicate taps. Add coroutine/view-model test for persisted actions before route exit.
- [ ] Implement runner overview/timed/reps/completion views, art/guide details and preview/edit-copy link. Complete/skip callbacks capture the displayed `currentUnitKey` and pass it as `expectedUnitKey`; never fetch a later current key when delivering a queued callback. Start/Pause/Resume use facade actions and injected clock; a UI ticker only refreshes display. Back confirms leave and persists pause; rotation leaves the running timer intact. Progress always reflects completed/skipped unit identities. Include the retained legacy weight in target display.
- [ ] Replace current WarmupRow wiring with one snapshot offer/runner entry before the first working set, preserving existing global default-off and explicit legacy warmup behavior. Import old WarmupStore state/ticks once when lazily initializing an already-live legacy session. Do not call old checklist and new runner concurrently; update WarmupExerciseFlowTest to the guided flow while retaining its original non-scoring assertion.
- [ ] Add cooldown card only to final summary after existing reward pages. Navigate runner over the session route and return to SUMMARY. On process recreation of a completed session with preparation state, recover a read-only sealed summary instead of current automatic exit; load saved result values without completeSession/reopen. Existing photo/routine offers still follow Done. Cooldown start/return/revisit never execute reward functions.
- [ ] Write integration assertions: begin/skip/change warmup then log work; legacy dedupe and no new set_logs; seal once then cooldown, rotate, leave/resume, complete and return to summary; kill/recreate activity/process recovery reconstructs persisted state; profile totalXp/session xpAwarded/strength/sets/rewards remain identical before and after repeated cooldown entry. Test an empty freeform session and deleted source workout.
- [ ] Run focused runner/summary/legacy-warmup flow tests through device lock, including 200% font scale and accessible descriptions/touch targets. Verify relevant existing celebration/routine-update/photo tests remain green.
- [ ] Run `rtk proxy python tools/gate.py` alone for the full local build/unit/lint/instrumented gate. Inspect the reported test count and seven-minute budget; resolve failures before claiming completion. No GitHub Actions runs. Review final diff, commit and push the runner/session unit.

## Self-review

The three tasks cover all approved screens and thirteen existing gallery movements, both attachment modes plus None, deterministic starters, snapshot/restore/migration survival, process/rotation resume, legacy warmups, final-summary cooldown, resource copy and reward isolation. Shared interfaces are defined once; later tasks consume the same names. The five Review Focus failure classes each have explicit owning test steps. No implementation or release approval is implied by this planning document beyond the user's already approved agent-driven task scope.
