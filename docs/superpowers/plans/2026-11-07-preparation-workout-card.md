# Preparation Workout Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the approved Workout card presentation to every preparation screen while preserving existing training, timer and persistence behavior.

**Architecture:** Reuse EntryCard, InkPanel, InkChip, PushedHeader and the existing workout NEXT/check visual language. Keep Room/repository/domain state authoritative; persist Begin in SessionViewModel before navigation, and carry only a read-only initialPreview presentation argument. Two sequential owners implement shared execution presentation first, then management presentation and final verification.

**Tech Stack:** Existing Kotlin, Jetpack Compose, StateFlow/SavedStateHandle, Room and local Gradle/Android test tooling; no new dependencies.

**Spec:** `docs/superpowers/specs/2026-11-07-preparation-workout-card-design.md`

## Global Constraints

- Scope is presentation and entry routing. Keep Room schema **47**, archive format **15**, repository/domain semantics, reward/scoring, recovery, legacy import/prefix handling and progress normalization unchanged unless a specific failing test proves an essential integration fix.
- Warmups remain enabled by default (`true`). Global off hides only untouched Auto general suggestions; saved routines, explicit legacy warmups and existing progress remain available. Cooldown remains independent.
- Explicit manual completion before timer expiry remains possible. Timer expiry never advances a unit. Retain the five-second readiness phase, real remaining time and explicit resume after leaving.
- COMPLETE_UNIT/SKIP_UNIT use the key captured by the displayed unit; transaction validation rejects stale/repeated actions atomically.
- Preserve legacy target labels, weights and distances, draft metadata through recreation, and clearing superseded legacy metadata only when exercise/measure/target/load changes. Notes/rounds/repeat alone retain it.
- Session snapshots remain immutable when routines or workout attachments change. Sealed summaries show exact stored results and never replay awards.
- Every new or changed label, plural, menu description and accessibility action uses paired plain/themed resources in all currently supported locale string files.
- Preserve normal app typography, minimum 48dp interactive targets, accessible names and 200% font scaling.
- All tests run locally. Instrumented work and captures use `emulator-5554` under the shared main-checkout device lock; never target the owner's phone.
- Add no dependency, new gallery movement, artwork, version bump, release, tag or Actions trigger.

## Review Focus

1. Begin/View across warmup, cooldown and recreation: Begin persists once before navigation; read-only preview never starts/resumes/pauses a saved timer (Task 1).
2. Rapid taps, last unit and mixed skipped/completed sides/rounds: one displayed unit advances once, manual early completion works, and completion counts remain truthful (Task 1).
3. Sealed summary re-entry and a global-disabled warmup: exact stored results remain unchanged; saved/legacy/in-progress warmups stay reachable and cooldown remains independent (Task 1).
4. Long names, 200% text, both themes and editor keyboard: text wraps and every primary/menu/reorder action remains readable, reachable and announceable (Tasks 1 and 2).
5. Nested selector/create/manage and copied legacy prescriptions: Use returns to the original workout draft, while metadata survives recreation until a prescription edit intentionally clears it (Task 2).

## File ownership and shared interfaces

Paths below are relative to `app/src/main/kotlin/com/ironvellum/app/` unless prefixed otherwise. Task 1 owns shared files until its review/commit completes; Task 2 then takes them over. Keep existing call sites source-compatible through defaulted arguments and named calls; retain trailing content lambdas.

Task 1 owns `ui/preparation/PreparationComponents.kt`, `PreparationOfferCard.kt`, `PreparationSummaryCard.kt`, `PreparationRunnerScreen.kt`, `PreparationRunnerViewModel.kt`, new `PreparationPresentation.kt`, `ui/train/SessionScreen.kt`, and `ui/IronvellumNav.kt`. Task 2 owns `ui/preparation/PreparationLibraryScreen.kt`, `PreparationSelectorScreen.kt`, `PreparationEditorScreen.kt`; it may extend `PreparationComponents.kt` and resources after Task 1. `PreparationViewModels.kt` changes only if a failing behavior test requires a presentation integration fix. Neither task owns domain/data changes by default.

Both tasks update resource pairs sequentially in `app/src/main/res/{values,values-b+zh+Hans,values-de,values-es,values-fr,values-ja,values-ko,values-pt-rBR}/strings.xml`; do not add strings to `values-night` merely because that directory exists. Reuse current translation/testing conventions and update any applicable hardcoded-literal baseline.

Minimal shared additions:

```kotlin
// ui/preparation/PreparationPresentation.kt: pure UI metadata, no persistence.
internal fun preparationEstimatedMinutes(snapshot: PreparationSnapshot): Int?

// ui/preparation/PreparationComponents.kt: only a presentation slot on the existing frame.
// Add before its final content parameter: actions: @Composable RowScope.() -> Unit = {},
//         dock: (@Composable () -> Unit)? = null
// Existing primary/enabled/content behavior remains default-compatible.
@Composable internal fun PreparationNextDock(
    next: String, completionDescription: String, enabled: Boolean, onComplete: () -> Unit,
)

// SessionViewModel in ui/train/SessionScreen.kt
fun beginPreparation(kind: PreparationKind, after: () -> Unit)
```

`preparationEstimatedMinutes` returns null for empty snapshots or steps with REPS/manual legacy metrics (only legacy DURATION/HOLD support timed estimates); otherwise ceil(sum(target * rounds * repetitions) / 60), using Long arithmetic. Converted legacy duration seconds remain valid. Exclude the five-second readiness cue. It is approximate display metadata only, never a timer target. `PreparationNextDock` composes the existing NEXT/check visual treatment and exposes button role, enabled state and the supplied specific completion description.

Append `onView: () -> Unit = onBegin` to `PreparationOfferCard` and `PreparationSummaryCard`; production callers always supply it. Append `onViewPreparation: (PreparationKind) -> Unit = onPreparation` to `SessionScreen`. Extend `Routes.preparationRunner(kind: PreparationKind, sessionId: Long, initialPreview: Boolean = false)` and its route pattern with `?preview={preview}` (default false). Append `initialPreview: Boolean = false` to `PreparationRunnerScreen` after existing parameters. It initializes saved presentation state only; it never triggers a repository action.

`beginPreparation` guards pending repeated entry actions and awaits `repo.preparations.act(sessionId, kind, START, now)` in NonCancellable before `after`; failures remain on the current screen using existing error handling. For a terminal View, never call it. Explicit overview Begin calls existing runner `act(START, after = { preview = false })`. Keep existing explicit Resume semantics; do not auto-resume in LaunchedEffect or from a persisted route command.

---

### Task 1: Shared Workout card execution and entry

**Files:** Own the Task 1 files above. Tests: update `app/src/androidTest/kotlin/com/ironvellum/app/ui/{PreparationRunnerLayoutTest,PreparationSessionFlowTest,PreparationResumeTest,WarmupExerciseFlowTest}.kt`; add only missing entry/preview cases to these suites. Create `app/src/test/kotlin/com/ironvellum/app/ui/preparation/PreparationPresentationTest.kt` for estimate behavior. Existing `domain/PreparationProgressTest.kt` and `data/PreparationPersistenceTest.kt` are regression evidence, not layout-test targets.

**Interfaces:** Consumes existing SessionPreparation, snapshots/progress/reducer, facade `act`, ExerciseInspection, gallery guides/art and existing summary slots. Produces the shared additions above plus compact offer/runner/completion behavior for Task 2 to reuse. Retain existing runner view-model `act(action, expectedUnitKey, after)`/`leave(after)` signatures.

- [x] **Step 1: Add focused failing behavior coverage.** In session/resume tests add `beginPersistsBeforeDirectMovementNavigation`, `previewNeverStartsOrResumesAcrossRecreation` and `cooldownPreviewKeepsStoredSummaryAndAwards`. Assert navigation observes RUNNING only after committed START; the first visible screen is the actual movement, not another Begin gate; View on OPEN and PAUSED retains exact progress/remaining/deadlines through activity recreation; closing preview has no action side effect. The cooldown case keeps stored XP/strength/profile/sets/rewards equal before/after repeated entry. Cover a suppressed untouched Auto warmup, a visible saved/legacy/progress warmup with global off and independent cooldown using existing tests where possible.
- [x] **Step 2: Update execution tests for actual controls and establish red.** Retain `rapidDoubleTapCompletesOnlyDisplayedUnit`; query the check by its accessible completion description and Skip via overflow. Assert first/second direction, another round and another movement all run; completion before expiry is enabled; mixed completion/skip counts match stored key sets; expiry does not advance. Extend existing layout cases for normal/200% text and both themes with long routine/movement names, reachable Info/Pause/menu/check and return. Add estimate cases: 30 seconds each direction plus 45 seconds gives 2 approximate minutes; 30 seconds * 2 sides * 2 rounds gives 2; empty/REPS/manual distance gives null. Run the focused commands below and confirm failures identify the missing refinement.
- [x] **Step 3: Implement the shared presentation and entry seam.** Add the pure estimate helper and frame action/dock slots without changing defaults. Compose compact offers and terminal rows; wire explicit View separately from Begin. Implement guarded persisted `beginPreparation`, retain cooldown SUMMARY before any navigation, and pass the optional preview argument. Keep View and preview Back read-only; only guided leave uses pause-before-navigation. Existing saved summary, owner-return and notification/service gates remain intact.
- [x] **Step 4: Implement the runner using the existing furniture.** One EntryCard contains art, target/real timer, round/direction, Info, one cue and timed Pause/Resume InkChip. Put actual progress above, Up next below, and PreparationNextDock outside scrolling content. Route skip/view/change/copy/finish through overflow and existing confirmations; pause before actually leaving guided execution. Finish uses existing SKIP_ROUTINE and retains its status/key semantics; completion reports actual completed/total and recorded skipped counts distinctly, never claiming unfinished work was completed. Terminal screen has counts and Return, with the full list only behind View. Keep all units and captured displayed-key callbacks; never copy prototype sample clock values or abbreviated transitions.
- [x] **Step 5: Add localized resource pairs and verify Task 1.** Update changed plain/themed copy, plurals and accessibility descriptions in all supported locales; adjust any decreased hardcoded baseline. Run estimate, domain and focused instrumented tests; run release lint and confirm zero new errors. Capture Task 1 changed states from the actual build at normal size plus 200% in both themes and inspect action reachability/card geometry against the approved mockup.
- [x] **Step 6: Commit the verified execution unit and obtain independent review.** Commit and push the tested coherent unit using current house style so the controller can package its exact base-to-head diff. A fresh task reviewer then checks all five applicable Review Focus risks, read-only preview, manual finish truthfulness and unchanged domain/data scope. Resolve findings in follow-up commits, rerun covering checks, and obtain scoped re-review. Task 2 starts only after this gate; no parallel shared-file edits or Git mutations.

Focused commands (from repository root; instrumented helper pins the emulator):

```powershell
rtk proxy .\gradlew.bat :app:testFossDebugUnitTest --tests com.ironvellum.app.ui.preparation.PreparationPresentationTest --tests com.ironvellum.app.domain.PreparationProgressTest
rtk proxy python tools/device.py lock --label preparation-card-execution -- .\gradlew.bat :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ironvellum.app.ui.PreparationRunnerLayoutTest,com.ironvellum.app.ui.PreparationSessionFlowTest,com.ironvellum.app.ui.PreparationResumeTest,com.ironvellum.app.ui.WarmupExerciseFlowTest
rtk proxy .\gradlew.bat :app:lintFossRelease
```

Expected: all selected tests pass with nonzero declared counts and release lint has zero errors; inherited warnings are recorded separately. Do not run another Gradle build alongside the final gate.

### Task 2: Management screens, final fidelity and full verification

**Files:** Task 2 ownership above; tests `app/src/androidTest/kotlin/com/ironvellum/app/ui/{PreparationLibraryFlowTest,PreparationAttachmentFlowTest,PreparationRunnerLayoutTest}.kt`; existing draft metadata unit tests at their current paths (discover exact symbols through graph first). Only extend existing tests unless no suite can exercise a new entry condition. New screenshots and reports stay under `.tmp/`.

**Interfaces:** Consumes Task 1 `preparationEstimatedMinutes`, unchanged `PreparationFrame` defaults and `PreparationPreview`, existing selector/editor view models and ExercisePickerSheet. Existing public library/selector/editor signatures remain unchanged. No new repository interface or serialized draft shape is needed.

- [x] **Step 1: Update the existing behavior assertions and establish red.** Library tests cover both tabs, empty state, starter copy without mutating its template, compact personal view/edit, and save/reopen. Attachment tests select Auto/Saved/None with one radio selection and one selected preview; browsing/previewing performs no attachment write; Use returns to the original unsaved workout after nested create/manage with working IDs and initial choices intact. Exercise saved-session preview without changing it. Reuse existing draft-recreation/legacy metadata assertions and add a missing assertion only where the new controls change the edit path.
- [x] **Step 2: Apply compact library and selector presentation.** Keep one Create dock, compact starter copy rows, truthful counts/estimate and personal-routine options. Use proper radio group/RadioButton semantics for Auto/Saved/None and render one selected preview, never a fabricated None routine. Preserve owner SavedStateHandle return and existing confirmation paths. Avoid implementing a new choice model or duplicate gallery detail screen.
- [x] **Step 3: Apply the workout editor pattern.** Keep one EntryCard expanded and the rest collapsed; arrange existing WholeStepper target/round controls compactly with adaptive stacking. Make repeat selection compact/expandable, cue optional, and reorder/remove reachable through explicit accessible actions. Preserve name, measures/validation, notes/repeats/rounds, legacy metadata handling, picker, future-only copy, discard/delete, saved drafts and keyboard-safe Save dock. Reuse existing components; add no general component API unless Task 1's defaults cannot express the layout.
- [x] **Step 4: Verify management accessibility and localization.** At 200% text with long names and the keyboard open, change target, rounds, repeat and note, reorder/remove, save, reopen, and verify exact values. Inspect both themes and every menu action. Verify plain/themed resource pairs in every supported locale and run applicable resource/hardcoded checks through the gate. Retest nested owner navigation and legacy copied-step recreation and prescription edits; source session snapshot remains equal.
- [x] **Step 5: Run focused management tests, then the complete local gate alone.** Use the focused command below; then run the full gate with the installed-AVD override described in the execution report. Require all build/unit/lint/instrumented/web phases and nonzero expected counts. Inspect seven-minute budget, retry reason if any, and new-vs-inherited lint output. Do not count the previous feature's gate or a separate old web pass as this revision's full gate.
- [x] **Step 6: Capture and inspect all 14 changed real-app states.** Workout offer/overview/warmup/paused/completion/terminal offer, sealed summary offer/cooldown/completion/terminal summary, library/selector/editor/leave confirmation. Include both themes, 200% text and keyboard/long-name evidence. Show the built-app captures to the owner as implementation evidence; approval to implement has already been supplied. Fix material visual or behavioral discrepancies and rerun the covering tests before final completion.
- [ ] **Step 7: Commit, push and obtain independent reviews.** Commit/push the tested management unit using house style and report actual gate counts/time plus screenshot location. A fresh task reviewer then checks management behavior, full resource coverage and scope; the final whole-change reviewer checks all Review Focus risks and real-app fidelity. Resolve findings in follow-up commits, rerun covering checks and obtain scoped re-review. No release/version/tag/Actions changes.

```powershell
rtk proxy python tools/device.py lock --label preparation-card-management -- .\gradlew.bat :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ironvellum.app.ui.PreparationLibraryFlowTest,com.ironvellum.app.ui.PreparationAttachmentFlowTest
rtk proxy python tools/gate.py
```

The last command expresses the normal repository gate. This machine lacks its configured `D:` AVD; use a temporary import wrapper that sets `device.AVD = "Ironvellum_Art_API35"` before `gate.main()` and sets `ANDROID_AVD_HOME` to `C:/Users/alexm/.android/avd`. Keep the same gate/lock, emulator port 5554 and all phases; do not change committed device defaults or silently substitute `--no-device`. See `.tmp/refinement-planning-report.md` for exact wrapper guidance.

## Self-review and handoff

The two tasks cover every approved state and preserve earlier persistence rulings. Shared types/signatures are defined once; Task 2 consumes them sequentially. Each Review Focus risk has owning test assertions, and tests exercise navigation, persisted state, rewards, metadata and action reachability rather than matching a component tree. Approximate timing is presentation-only and omitted when the prescription does not support it. Explicit approval and agent-driven execution are already supplied; continue with that method without another approval gate.
