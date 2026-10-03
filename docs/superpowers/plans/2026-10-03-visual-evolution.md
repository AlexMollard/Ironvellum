# Ironvellum Visual Evolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task by task. Steps use checkbox (`- [ ]`) syntax for tracking. The owner has selected subagent-driven execution; use `gpt-6-sol` for every implementation and review agent.

**Goal:** Make the existing Ledger easier to scan and visually coherent, while keeping every game-like dark-fantasy capability, route, state, rule, reward, and glossary term reachable.

**Architecture:** Evolve `IronvellumTheme`, `InkPanel`, and established Compose screens in place. Shared tokens and modest layout primitives flow into screen-specific presentation changes; view models, domain calculations, persistence, navigation strings, and cloud wire contracts stay intact. Bundle a small, reviewed family of offline art only after a phone-scale pilot passes.

**Tech Stack:** Kotlin, Jetpack Compose/Material 3, Android resources, Gradle, local `tools/gate.py`.

**Spec:** `docs/superpowers/specs/2026-10-03-visual-evolution-design.md` (approved 3 October 2026). Baseline: `d318855` on `main`. The browser mock is illustrative only.

## Global Constraints

- No current functionality, route, sheet/dialog action, state, game mechanic, data rule, reward path, glossary term, or accessibility affordance may disappear. Preserve route strings, persistence models, cloud wire values, and glossary-protected IDs. `docs/GLOSSARY.md` governs visible copy.
- Retain the game-like dark-fantasy Ledger identity. Use Chakra Petch and existing sanctioned `IronvellumTracking` values. The base papers are `Abyss #0C0C0B`, `Vault #171715`, `VaultHigh #1E1E1B`; bone ink `#E8E8E4`, muted ink `#A3A099`; `Emerald #34D399` signals XP/success, `SystemGreen #6FAE8C` is muted accent, and `SovereignGold #F2C14E` marks earned moments. Avoid blue-grey, parchment-beige, neon HUD, and soldier-rank imagery.
- `INK`/`CLEAN` must both remain useful: same layout/actions, with texture/edge treatment varied by the existing preference. Keep at least 48 dp touch targets, clear talkback order, selected semantics, contrast, long-name wrapping, and narrow-phone reachability. Do not convey status by color alone.
- Training/game use must work without cloud configuration. Keep scientific, privacy, safety, and settings language plain. Decorative art has no spoken label; informative art needs text alternatives.
- Art is bundled offline, with no runtime generation, paid art/font SDK, or new recurring cost. The first art sample requires individual owner review for silhouette, anatomy, pose weight, lighting, stroke density, and legibility. The approved spec proposes an **8 MiB first-release incremental compressed APK/AAB art target**, pending owner review; work to that conservative target against `d318855`, reduce the set if needed, and present a measured trade-off before exceeding it. Plan review can settle the target; an additional approval step is needed only for a concrete pilot asset or measured overage.
- Social photos are outside this plan. `avatarUrl` in `LifterIdentity.kt` is reserved; generated crests remain fallback. Any upload path needs a separate cost, privacy, abuse, authorization, caching, deletion, and deployment decision.
- Tests and builds run locally. Never add `push`/`pull_request` CI triggers. `tools/gate.py` owns the emulator lock and pins an emulator; never run instrumented tests on the owner's physical phone. Follow `AGENTS.md` for verified coherent commit/push units to `main`; no history rewrites, tags, or releases without owner authorization.

## File Structure and Stable Interfaces

`app/src/main/kotlin/com/ironvellum/app/ui/theme/Theme.kt` owns palette, typography, and Material shapes. `ui/theme/Ink.kt` owns `rememberInkShape` and `paperGrain`; preserve its preference behavior. `ui/components/Common.kt` owns `InkPanel(modifier: Modifier = Modifier, accent: Color = IronvellumColors.Rune, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)` and `IronvellumButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, gold: Boolean = false, enabled: Boolean = true, quiet: Boolean = false, danger: Boolean = false)`. Evolve these only with defaulted, source-compatible optional parameters, before trailing `content`, and keep existing behavior by default. `ui/components/InkTabbedPager.kt` owns the tab API and pager behavior. `ui/components/InfoSheet.kt` owns shared sheet presentation. Avoid creating a competing design system.

Screen ownership stays with the files named per task. New static art belongs under `app/src/main/res/drawable-nodpi/` (or density-qualified resources if measured better); `tools/art.py` establishes bone-ink tint. Keep each art asset and its screen use in the same reviewed task. No new repository/backend/photo API is produced by this plan.

## Review Focus

These are the five highest-risk input/state classes to pin in their owning tasks, beyond cosmetic screenshot checks:

1. **A narrow 360 dp view with long true names and movement names:** text wraps or truncates without hiding identity, work metrics, or actions (Tasks 4, 8, 11).
2. **A live Trial resumed from a notification after process death:** elapsed/rest state, set inputs, and seal/abandon confirmations remain usable (Task 6).
3. **Signed out, cloud unconfigured, and stale/offline social data:** account entry remains complete; text-only cards and cached content have visible actions and honest status (Tasks 11–13).
4. **An `INK`/`CLEAN` toggle on a dialog and a screen with art:** content, touch bounds, and contrast stay equivalent while treatment changes (Tasks 2, 3, 14).
5. **A private/amended Trial reached by deep link or notification:** audience, amendment label, moderation, and navigation destination stay correct (Tasks 11–13).

## Preservation Evidence Required for Every Screen Task

Before editing its named screens, the implementer records a concise before→after reachability table in `docs/superpowers/plans/visual-evolution-parity.md`: each tab/route entry, interactive control, sheet/dialog and confirmation, and empty/loading/error/offline/stale/populated state actually present in source, with its resulting location. Mark a state “not applicable” only after source inspection. Include the important callbacks, not merely visible labels. Capture before/after screenshots at **the same viewport and font scale**, and link them in the table. Inspect first-run Binding, signed-out and unconfigured social, and local offline use. A reviewer rejects a slice if any existing action or branch lacks a mapped destination. Use the spec inventory as a starting point, never as a substitute for source inspection. Keep the table compact and update it in the same commit as each slice.

For manual review use an emulator with the shared lock, or let `tools/gate.py` own it. Do not run `pm clear`, install/uninstall, or instrumented tests on `R5GL14GXV3J`. A safe visual `:app:installFossDebug` to the owner's phone is allowed when available. All implementation task commits are made only after their stated local check; the final gate runs once after the full change unless a concrete regression calls for earlier full runs.

The exact lightweight compile/unit command for every code slice is `rtk proxy .\gradlew.bat :app:compileFossDebugKotlin :app:testFossDebugUnitTest`. Run the specifically named instrumented suites with the repository gate when the owning task changes an interaction they cover; the gate owns emulator selection and lock. In all screen migrations, use the existing `InkPanel`/`IronvellumButton` calls and move the current callback with its UI. A representative layout is:

```kotlin
InkPanel(contentPadding = PaddingValues(12.dp), accent = IronvellumColors.Rune) {
    Text("RITES", color = IronvellumColors.Ink)
    // Move existing Rite controls and their existing callbacks here unchanged.
}
```

The `RITES` label is an existing glossary term; the comment is a movement instruction, not a new call or a substitute for the preservation table. Use `IronvellumColors.SystemGreen` for the active primary section and `Rune` for ordinary secondary sections. Review existing Material semantics before adding any wrapper clickable, so nested actions remain separately tappable.

---

### Task 1: Establish the preservation baseline

**Files:** Create `docs/superpowers/plans/visual-evolution-parity.md`; read `app/src/main/kotlin/com/ironvellum/app/ui/IronvellumNav.kt`, `docs/GLOSSARY.md`, and the screen files in Tasks 4–13. No product-code change.

**Interfaces:** Produces the shared before→after evidence table, screenshot naming convention, baseline APK size, and route/action checklist used by every following task.

- [ ] **Step 1:** With `d318855` as the documented source baseline (no checkout/reset needed), record each existing main destination and secondary route string, tab labels/ordinals, notification/deep-link entry, back behavior, and state-restoration path from `IronvellumNav.kt`. Use rows such as `surface | current route/entry | control or branch | callback/result | post-change location | evidence`.
- [ ] **Step 2:** For each screen in Tasks 4–13, inspect the source and populate every current control, sheet/dialog, tab, and visible state. Capture baseline screenshots for first-run, local populated/empty, social signed-out/unconfigured/loading/error/populated, and active Trial where test-safe. Use `docs/GLOSSARY.md` to flag protected copy/IDs.
- [ ] **Step 3:** Run `rtk proxy .\gradlew.bat :app:assembleFossDebug`; record compressed APK bytes and screen capture viewport/font scale in the table. Run `rtk proxy python tools/gate.py --no-device` only if baseline compilation is uncertain; avoid a 17-minute baseline device gate without a concrete need.
- [ ] **Step 4:** Review table with a fresh agent; commit/push this documentation unit after its route and screen references are checked.

### Task 2: Shared palette, surfaces, and accessible control geometry

**Files:** Modify `app/src/main/kotlin/com/ironvellum/app/ui/theme/Theme.kt`, `app/src/main/kotlin/com/ironvellum/app/ui/components/Common.kt`, and, only if the audit shows a shared defect, `app/src/main/kotlin/com/ironvellum/app/ui/components/InfoSheet.kt`. Test `app/src/test/kotlin/com/ironvellum/app/ui/components/LedgerContrastTest.kt` and `app/src/androidTest/kotlin/com/ironvellum/app/ui/AccessibilityChecksTest.kt` when a behavior changes.

**Interfaces:** Preserve `IronvellumTheme`, `InkPanel`, and `IronvellumButton` call compatibility. Later tasks consume the resulting warm-charcoal tokens and current components.

**Proposed shared API:** Add `contentPadding` immediately before `InkPanel`'s trailing `content`, preserving all defaults and existing calls. Add the `PaddingValues` import, then make this exact signature and inner-body change in `Common.kt` while retaining its existing surface modifier, brush, border, accent draw, and click branches:

```kotlin
@Composable
fun InkPanel(
    modifier: Modifier = Modifier,
    accent: Color = IronvellumColors.Rune,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = rememberInkShape(accent.hashCode())
    val body: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(contentPadding),
            content = content,
        )
    }
    val surfaceModifier = modifier
        .background(WindowFill, shape)
        .paperGrain(accent.hashCode())
        .inkBorder(IronvellumColors.Rune, shape, 1.dp)
        .drawBehind {
            val outline = when (val o = shape.createOutline(size, layoutDirection, this)) {
                is Outline.Generic -> o.path
                is Outline.Rounded -> Path().apply { addRoundRect(o.roundRect) }
                is Outline.Rectangle -> Path().apply { addRect(o.rect) }
            }
            drawPath(
                outline,
                accent.copy(alpha = accent.alpha * 0.30f),
                style = Stroke(1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = surfaceModifier,
        ) { body() }
    } else {
        Surface(
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = surfaceModifier,
        ) { body() }
    }
}
```

The default remains 16 dp and `WindowFill`/grain/edge behavior is unchanged. Keep `IronvellumButton`'s existing signature and color priority (`!enabled`, `danger`, `quiet`, primary); use a caller `modifier.heightIn(min = 48.dp)` only where measured target bounds need it.

- [ ] **Step 1:** Inspect `Theme.kt`'s `onPrimary = Color.White` against actual Material primary controls; `IronvellumButton` already draws dark text on emerald/gold, so do not change it on assumption. Measure concrete contrast or 48 dp failures in current components and add a focused `LedgerContrastTest` case only for a real palette defect. Do not write tests that merely assert exact palette literals.
- [ ] **Step 2:** Add the optional `InkPanel` padding argument above, leaving existing call sites unchanged. Run any new focused test to see a measured defect before adjusting color or button geometry. Avoid a global theme override that changes INK/CLEAN behavior.
- [ ] **Step 3:** Check dialogs/sheets that hard-code `#0D1110` as candidates, not a blind replacement. Verify a dialog with its actual selected/disabled state, `INK` and `CLEAN`, 48 dp target, talkback order, and narrow-phone wrapping.
- [ ] **Step 4:** Run focused tests and `rtk proxy .\gradlew.bat :app:compileFossDebugKotlin`; update parity evidence for shared controls; commit/push the verified shared unit.

### Task 3: Pilot one offline bone-ink art asset

**Files:** Create one optimized asset under `app/src/main/res/drawable-nodpi/`; modify one low-risk hero/empty-state placement in `app/src/main/kotlin/com/ironvellum/app/ui/idle/IdleScreen.kt` after source inventory; update `docs/superpowers/plans/visual-evolution-parity.md` with measurements. Reuse `tools/art.py` tint rules.

**Interfaces:** Produces an Android drawable resource for optional decorative placement; no state/ownership/reward contract.

- [ ] **Step 1:** Generate one transparent or warm-black **dark-fantasy bone-ink/sumi-e** silhouette: an Ironbound figure in a grounded training pose, pale bone ink and restrained brush texture, coherent weight and light from one side, no soldier insignia, neon or parchment. Inspect at 1× and high density for anatomy, pose weight, lighting direction, stroke density, and phone-scale legibility. Obtain individual owner review of this concrete pilot before expanding the family; do not reuse the earlier anatomical engraved-study image as final art. The later technique illustrations must depict a dead hang, scapular pull and pull-up as three anatomically distinct poses while retaining code-rendered labels, state and connectors.
- [ ] **Step 2:** Name the accepted pilot `iv_veil_ink_figure.webp`. Place it behind or beside existing Veil content with `contentDescription = null` when decorative and without occluding counters, odds, crest ownership, or buttons. Representative placement: `Image(painterResource(R.drawable.iv_veil_ink_figure), contentDescription = null, modifier = Modifier.fillMaxWidth())` in a constrained/background layer after measuring scroll and contrast.
- [ ] **Step 3:** Build and compare compressed package size with Task 1; document the incremental bytes and projected family budget. Check INK/CLEAN, motion reduction, screen-reader silence, and scrolling on emulator; inspect safe phone install if available. If the pilot fails, revise or omit it before continuation. Commit/push only the accepted asset and placement.

### Task 4: Today and first-run Binding

**Files:** Modify `app/src/main/kotlin/com/ironvellum/app/ui/dashboard/DashboardScreen.kt`, `app/src/main/kotlin/com/ironvellum/app/ui/onboarding/OnboardingScreen.kt`; relevant tests `app/src/androidTest/kotlin/com/ironvellum/app/ui/OnboardingBackTest.kt`, `app/src/androidTest/kotlin/com/ironvellum/app/ui/NavigationReachabilityTest.kt`, and `app/src/test/kotlin/com/ironvellum/app/ui/dashboard/WeekDoneDaysTest.kt` if their behavior is affected.

**Interfaces:** Keep existing callbacks/navigation. Today's primary action becomes visually prominent; no cycle/deed/progress calculation changes.

**Representative migration:** Keep `DashboardScreen`'s existing `onStartSession: (Long) -> Unit` callback and its `viewModel.beginPreset(selectedPreset.id, onStartSession)` branch. Move the existing begin/resume button and the label that explains it into an `InkPanel` with `contentPadding = PaddingValues(12.dp)` and `accent = IronvellumColors.SystemGreen`. Leave the other progress rows immediately after that panel and keep every current callback and condition.

- [ ] **Step 1:** Map Today identity/worn Title, Rank, XP/level/Ascension, steps/sync, oath/Deeds, seven-day rail and markers, scheduled begin/resume/sealed, Build Cycle/Open Trial, respite/next Rite, recent Trial, body-data gap prompt, Settings/Codex/Veil. Map Binding identity, training choices, proposal review/edit and completion/back steps. Record the exact existing destination of each.
- [ ] **Step 2:** Capture failing reachability/semantics checks only for changed controls, especially a 360 dp long movement/title and first-run back. Move today’s scheduled action into the leading hierarchy using existing callbacks; keep the seven-day selector, progress systems, and all secondary actions in the same route.
- [ ] **Step 3:** Apply shared heading, panel, and spacing rules to each Binding step. Do not change validation, defaults, proposal generation, or save path. Verify empty/no Cycle, respite, scheduled, active, sealed, and first-run states in the parity table.
- [ ] **Step 4:** Run focused tests, compile, same-scale screenshot comparison, talkback/48 dp check; commit/push this verified slice.

### Task 5: Rites, Forge, exercise catalogue, and muscle coverage

**Files:** Modify `ui/train/PresetsScreen.kt`, `ui/train/PresetEditorScreen.kt`, `ui/train/ExerciseExplorerScreen.kt`, `ui/program/ProgramBuilderScreen.kt`, `ui/program/MuscleCoverageScreen.kt` under `app/src/main/kotlin/com/ironvellum/app/`; test existing `app/src/androidTest/kotlin/com/ironvellum/app/ui/PresetEditorReorderTest.kt` and `PresetAutoFillTest.kt`, plus focused `ui/program` unit tests for any changed presentation logic.

**Interfaces:** Preserve all route/callback and generator interfaces; `InkPanel`/buttons are consumed with existing signatures.

- [ ] **Step 1:** Inventory Open Trial/catalogue, Rite card/edit/begin/continue, Cycle sharing/Chronicle/muscle sheet; Forge Cycle/Rite/Pattern/Temper modes, focus, split, weekly volume, Armoury, compound filter, exercise limit, priorities, preview/editable entries, add-versus-replace Cycle and apply-versus-keep-original confirmations; planned/logged muscle comparison and return-to-Forge. Map each control and dialog to its same destination.
- [ ] **Step 2:** Write a focused test for any changed control reachability or selected-mode state. Recompose the current Rite/Forge sections with shared spacing and a clear primary action. Keep scientific labels, units, selections, preview edits, and all confirmation callbacks intact; art cannot overlap inputs.
- [ ] **Step 3:** Check every mode, empty catalogue/filter result, long exercise name, replacement/apply decision, and muscle sheet at 360 dp. Run focused tests/compile, capture paired screenshots, update parity table, and commit/push.

### Task 6: Active Trial and Chronicle

**Files:** Modify `ui/train/SessionScreen.kt`, `ui/train/WorkoutLogScreen.kt`, `ui/train/WorkoutDetailScreen.kt`; validate with `app/src/androidTest/kotlin/com/ironvellum/app/ui/ResumeLiveSessionTest.kt`, `WorkoutFlowTest.kt`, `WorkoutLogRendersHistoryTest.kt`, and relevant existing domain/data tests only if behavior changes.

**Interfaces:** Preserve session/repository callbacks, elapsed/rest timing, amendment and sharing paths.

- [ ] **Step 1:** Inventory active elapsed/rest/notification, each set-input metric variant, prior comparison, complete/add/remove/reorder exercise and set, modifiers, info, notes, abandon/seal gates, finish awards/Rite update; Chronicle list, detail amendment/share/note audience/Deeds. Include resumed notification/process-death path and all dialogs.
- [ ] **Step 2:** Add or adapt a focused instrumented assertion for resumed active Trial: timing and set fields still appear, then seal and abandon each require their existing confirmation. Keep existing `SessionScreen` callbacks and inputs; group the active set ahead of secondary tools without making secondary tools hidden.
- [ ] **Step 3:** Apply the same hierarchy to sealed list/detail, preserving dates, units, notes, earned awards, amendment and privacy controls. Verify state restoration and paired screenshots at 360 dp, run focused local tests/compile, update parity table, and commit/push.

### Task 7: Ledger Body, Training, Daily, and calendar

**Files:** Modify `ui/stats/StatsScreen.kt`, `LedgerBody.kt`, `MeasurementDetailScreen.kt`, `DailyTab.kt`, `TrainingCalendar.kt` under `app/src/main/kotlin/com/ironvellum/app/`; test `app/src/test/kotlin/com/ironvellum/app/ui/stats/TrainingCalendarTest.kt`, `CalendarScheduleStartTest.kt`, and existing chart tests if chart behavior changes.

**Interfaces:** Preserve the Body/Training/Daily tabs, calculations, chart datasets, measurement units, and Health Connect settings path.

- [ ] **Step 1:** Inventory tabs and `LedgerBody.kt`'s `BodyTab`: weight trend/range/history, BMI/FFMI, strength/lift records, tape/drill-down and Daily links; calendar month/day/oath; Daily Health Connect empty/settings, steps/sleep/active kcal/resting HR/charts/estimation explanation. Map empty/data-permission/error and populated branches.
- [ ] **Step 2:** Use shared section spacing and panel contrast. Keep charts and explanation text; do not rewrite formulas or infer missing readings. If a selected tab or month/day interaction changes, add a focused existing-test extension first and observe its failure.
- [ ] **Step 3:** Verify chart labels/units at long values and narrow width, no Health Connect, stale readings, and all drill-down links. Run focused tests/compile, screenshots/talkback, parity update, commit/push.

### Task 8: Codex Deeds, Titles, Paths, and Journal

**Files:** Modify `ui/titles/TitlesScreen.kt`, `DeedsBoard.kt`, `DeedDetailSheet.kt`, `PathOverview.kt`, `SkillTree.kt`, `SkillDetail.kt`, `SkillJournal.kt`, `SkillGlyph.kt`, `SkillGlyphArt.kt` under `app/src/main/kotlin/com/ironvellum/app/` as required; create `app/src/main/res/drawable-nodpi/skill_dead_hang.webp`, `skill_scapular_pull.webp`, `skill_pull_up.webp` only after pilot art review; validate with existing `ui/titles/CodexUiTest.kt`, `SkillTreeLayoutTest.kt`, `SkillTreeEdgeTest.kt`, `SkillGlyphTest.kt`, and `app/src/androidTest/kotlin/com/ironvellum/app/ui/SkillPracticeFlowTest.kt`.

**Interfaces:** Preserve three-tab Codex pager/selection and every Deed/Path/Title/technique callback. No prerequisite, claim, or reward logic changes.

**Presentation-only mapping:** Keep `Skills.SkillDef.name` unchanged and give only the three source-verified Pull technique names new art. The existing `SkillGlyph` call remains the fallback:

```kotlin
@DrawableRes
private fun skillPoseArt(skill: Skills.SkillDef): Int? = when (skill.name) {
    "Dead Hang" -> R.drawable.skill_dead_hang
    "Scapular Pull" -> R.drawable.skill_scapular_pull
    "Pull-up" -> R.drawable.skill_pull_up
    else -> null
}
```

Render the drawable decoratively beside the code-rendered name/state/connectors; `else` continues through the current `SkillGlyph` call in `SkillTree.kt`:

```kotlin
SkillGlyph(
    family = glyphFamily(skill),
    color = when (state) {
        NodeState.MASTERED -> IronvellumColors.Abyss
        NodeState.NEXT -> IronvellumColors.Ink
        NodeState.LOCKED -> LockedGlyph
    },
    modifier = Modifier.size(32.dp),
)
```

Add the Android `@DrawableRes` import only if this helper is implemented.

- [ ] **Step 1:** Inventory Deed counts/progress/categories/ladders/earned-locked detail and Title wear; Path grid/node prerequisites/claim standard, practice/evidence, claim/unclaim, add-to-Rite, Journal heatmap/timeline and sheet actions. Record path position across tab switches and rotation.
- [ ] **Step 2:** Add a focused assertion for path position and selected state if existing tests do not cover the changed presentation. Improve row and graph-node hierarchy; reserve gold for earned/worn status. Keep all labels, explanations, and graph edges accessible, including long technique names at 360 dp.
- [ ] **Step 3:** Pilot three distinct Pull-line poses: `Dead Hang`, `Scapular Pull`, and `Pull-up` (exact existing `Skills.kt` names) must show different body positions at phone scale. Keep `glyphFamily(skill)` in `SkillGlyph.kt` and procedural `SkillGlyph(family, color, modifier)` in `SkillGlyphArt.kt` as the fallback and preserve `SkillGlyphTest.kt`. Add a presentation-only exact-name mapping for these three approved drawable resources in `SkillTree.kt`; do not rename the skills, change their prerequisite relationships, or map all Pull nodes to one pose. Keep node labels, prerequisites, connectors, selected/earned/locked state, claim actions, and fallback for every other technique. Review the three-pose pilot with the owner before producing more art, count its compressed bytes under the 8 MiB target, and do not reuse the engraved anatomical study.
- [ ] **Step 4:** Run relevant unit/instrumented tests, compare screenshot positions and sheet reachability with and without art, update parity table, commit/push.

### Task 9: Veil composition and reward visibility

**Files:** Modify `app/src/main/kotlin/com/ironvellum/app/ui/idle/IdleScreen.kt` and related Veil subcomponents only where presentation changes; validate with existing `app/src/test/kotlin/com/ironvellum/app/domain/IdleTest.kt`, `GachaTest.kt`, `RelicsTest.kt` only if their logic is touched.

**Interfaces:** Consume the approved Task 3 art if retained; preserve Echo/essence, rates, draw/odds, crest/relic ownership and Circle destination.

- [ ] **Step 1:** Inventory live Echo/essence, away earnings and banking, rate explanation, inscriptions/odds, crest collection/equip, Vault, Circle banner/link, draw feedback and explanatory/empty states.
- [ ] **Step 2:** Place numbers and actions above atmospheric art, clearly distinguish held from unheld rewards, and keep exact odds and descriptions. Do not alter payout/rarity logic or `LifterIdentity.kt` frame rules.
- [ ] **Step 3:** Verify INK/CLEAN, offline resume, long item names, collection ownership, scroll performance, talkback, and paired screenshots. Run focused compile/tests, update parity table, commit/push.

### Task 10: Settings, support, and account safety

**Files:** Modify `ui/settings/SettingsScreen.kt`, `SettingsPage.kt`, `AppearanceSettings.kt`, `DataSettings.kt`, `ImportReviewScreen.kt`, `AdvancedSettings.kt`, `SupportScreen.kt`, and `ui/social/AccountSettingsScreen.kt` only for presentation, under `app/src/main/kotlin/com/ironvellum/app/`. Inspect `ui/settings/ProfileSettings.kt`, `TrainingModeSettings.kt`, `ArmourySettings.kt`, `SummonsSettings.kt`, and `HealthConnectSettings.kt` for parity; edit only if their presentation needs the shared treatment. Validate with `SettingsHubTest.kt`, `SupportScreenFlavourTest.kt`, and existing `ui/settings` unit tests.

**Interfaces:** Preserve `AppearanceSettings(viewModel: SettingsViewModel, onBack: () -> Unit)` and `viewModel.setInkStyle(Boolean)`, all settings routes and destructive confirmations.

- [ ] **Step 1:** Inventory Profile/account, training mode, Armoury, Summons, INK/CLEAN, Health Connect, data/advanced, export/share, Strong/Hevy import review (units/unmatched/duplicates), restore confirmation, cloud probe/choice, crash logs, coverage, costs/source/licence/version/links. Inventory account true name, audience, sync, backup/restore, notifications, blocked users, sign-out/delete gates.
- [ ] **Step 2:** Group rows and value summaries more clearly while retaining each current setting route and safety dialog. Keep privacy/destructive wording plain and `INK`/`CLEAN` exactly named. Do not alter import/export, cloud, or backup behavior.
- [ ] **Step 3:** Verify both appearance modes on a page and dialog, import review with unmatched entry, signed-out account, and destructive confirmation; run focused tests/compile, update parity/screenshots, commit/push.

### Task 11: Allies pager and text-only Tidings

**Files:** Modify `ui/social/SocialScreen.kt`, `FeedScreen.kt`, and `LifterIdentity.kt` only if the existing identity layout needs spacing; use current `ui/components/InkTabbedPager.kt`. No media loader or backend code.

**Interfaces:** Preserve `SocialScreen(onOpenLifter: (String,String)->Unit, onOpenComments: (String,String,String)->Unit, onOpenAccount: ()->Unit, inboxRequest: Int = 0)`; preserve `FeedCard`'s `onReact`, `onOpenComments`, `onShowLikers`, `onAddAlly`, `onRetryLikers`, and `onLikersClosed` callbacks.

- [ ] **Step 1:** Inventory FEED/INBOX/BOARD/ALLIES order, pager restoration, signed-out/unconfigured account path, unread badge and settled INBOX notification semantics, permission dialog. Inventory feed refresh/pagination, identity/Folio, Trial name/note/movement/time/work/audience, tribute picker/retraction/likers/error retry, remarks and ally action.
- [ ] **Step 2:** Add focused UI assertions for a 360 dp text-only card with long name/movement and all feed actions reachable, plus signed-out/unconfigured and stale/error cards. Keep `FeedCard`'s current `FeedEntry`, `AllyState`, likers/reaction state, and callbacks intact. Use shared `IdentityRow`, `MovementLine`, `StatStrip`, and `CountChip` in a cleaner hierarchy; retain generated crests.
- [ ] **Step 3:** Verify newest-first feed, pull refresh/pagination and card-open behavior, tapped identity/ally/action precedence, reaction rollback/error line, and audience visibility. Run focused tests/compile and screenshots, update parity table, commit/push.

### Task 12: Missives and Reckoning

**Files:** Modify `ui/social/InboxScreen.kt`, `LeaderboardScreen.kt`; keep `SocialScreen.kt` pager wiring from Task 11.

**Interfaces:** Preserve existing callbacks from `SocialScreen`: INBOX `onOpenLifter`, `onOpenComments`, `onOpenCircle`, `active`; BOARD `onOpenFriend`.

- [ ] **Step 1:** Inventory unread/read state, ally requests/answers, remarks/tributes/Circle events, Folio/Trial/Circle links, notification entry; lift-specific and Veil standings, board chooser, own position/rung, Folio links, empty/error/loading. Record destination of each card/row.
- [ ] **Step 2:** Make unread and action priority clear and align board columns/selected board/own row. Keep read marking only on the settled INBOX page; never change rank calculation or notification routing. Add a focused assertion if selected board or notification state is touched.
- [ ] **Step 3:** Check stale/offline inbox, empty board, long names, back from linked Trial/Folio/Circle, talkback, 48 dp controls and paired screenshots. Run focused compile/tests, parity update, commit/push.

### Task 13: Allies Folio, Circle, and Trial conversation

**Files:** Modify `ui/social/AccountScreen.kt`, `LifterScreen.kt`, `CircleUi.kt`, `CommentsScreen.kt`, and, if needed, presentational `LifterIdentity.kt`; keep `AccountSettingsScreen.kt` safety work in Task 10.

**Interfaces:** Preserve generated crest fallback/rarity/level/frame rules and existing cloud privacy/moderation callbacks. No photo storage, loading, or upload path.

- [ ] **Step 1:** Inventory sign-in/configuration/true-name claim, Folio visibility/public recent Trials, invite/accept/decline/remove/block/report, Circle creation/join/goal/roster/keeper/leave actions; Trial exercise/set detail, tribute/retract, remarks, deletion/moderation/report. Include Public/Allies only/Private and private/amended deep-link cases.
- [ ] **Step 2:** Reuse identity/action placement and text-only Tidings hierarchy in Folio and conversation while retaining full Trial work detail. Keep all Circle and account actions in their present sheet/route. Add focused UI checks for long names, private/amended Trial audience, report/delete reachability, and signed-out/unconfigured paths where touched.
- [ ] **Step 3:** Verify blocked/removed ally, empty roster, stale data, moderation failure, and back/deep-link destinations. Run focused compile/tests, paired screenshots/talkback, parity update, commit/push.

### Task 14: Final consistency, accessibility, package, and local release gate

**Files:** Modify only the concrete screen/shared files whose review finds defects; finalize `docs/superpowers/plans/visual-evolution-parity.md`. No new photo/deployment scope.

**Interfaces:** All current APIs, route strings, persistence and cloud values remain backward compatible.

- [ ] **Step 1:** Review every before→after parity row and screenshot at identical viewport/font scale; inspect Today, Binding, Rites, Trial/Chronicle, Ledger, Codex, Veil, Allies four tabs/Folio/Circle/conversation, settings/support. Fix any lost action or visual inconsistency before the gate. Check notification/deep-link/back restoration, Trial seal/Chronicle, Deed/Path/Veil rewards, privacy/moderation, appearance, account safety.
- [ ] **Step 2:** Exercise TalkBack order, selected tabs/nodes, 48 dp bounds, contrast, 360 dp long names, INK/CLEAN, offline/unconfigured, empty/loading/stale/error/populated states. Use `AccessibilityChecksTest.kt` and `NavigationReachabilityTest.kt` for real regressions; add focused tests only for observed gaps.
- [ ] **Step 3:** Build compressed APK/AAB and compare with Task 1. Document incremental art bytes against the proposed 8 MiB first-release target; reduce art or present a measured trade-off for owner review before exceeding it. Confirm decorative assets have no spoken label and no runtime media dependency.
- [ ] **Step 4:** Run the full local `rtk proxy python tools/gate.py` (add `--backend` only if backend behavior changed). Confirm no automatic GitHub workflow triggers were added. Inspect on emulator, and safe phone install if available. Commit/push only after all required checks pass; request a whole-branch review.

## Handoff

The owner has already selected subagent-driven execution and `gpt-6-sol` for future implementation/review agents. Review this plan against the approved spec before beginning Task 1; preserve that execution method. The concrete pilot art sample requires owner review; a measured overage against the proposed 8 MiB first-release target requires a separate trade-off review. Photo upload remains a separate future decision requiring monthly storage/transfer/request projections, per-account and per-post limits, compression/thumbnails/cache, abuse/deletion consistency, authorization for Public/Allies only/Private (including revoked allies/stale caches), host and documented free/paid limits, and owner approval for any recurring cost or deployment.
