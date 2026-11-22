# AI screens UI and UX implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Implement the approved native card-based AI screens and make saved drafts directly viewable.

**Architecture:** Reuse native Compose cards and theme tokens with small shared presentation helpers. Keep local file state in the existing ViewModel and cloud account-scoped operations in their existing owner; add explicit navigation callbacks carrying real local preset IDs.

**Tech Stack:** Android, Kotlin, Compose, existing string resources and instrumented tests; no dependencies.

**Spec:** `docs/superpowers/specs/2026-11-22-ai-screens-ui-ux-design.md` (approved).

## Global Constraints

- Use existing native Compose components, theme tokens, Chakra Petch, clipped borders and material icons. No generated raster assets, new font/dependency, gradients or provider branding.
- The reviewed read/draft protocols, file/resource limits, exact-byte provenance, manual Save, receipts and data privacy are unchanged. No server or schema migration.
- UI expansion flags and summaries can be remembered, but full drafts, raw files and review handles remain RAM-only. Preserve cancellation/current-job checks, immutable sharing and account/backend scope invalidation.
- All screen copy is in resources, including default/themed wording and all seven translation directories. Preserve count plurals, exact placeholders and accessible untrusted-text wrapping.
- Support small phone widths, wide layouts, both palettes and 200% text. Decorative icons are not announced; actionable rows/buttons have clear labels. Expanded details expose state to accessibility services.
- Preserve Muscles first on phone-width Train and AI tools immediately before Recent workouts.
- Run emulator work through `tools/device.py lock`; never run instrumentation on the physical phone. No competing builds during the final gate.

## Review Focus

- A previously imported and then deleted local preset must show no View/Save and must not be recreated (Task 1 receipt screen test).
- A very long movement name or exact 128-digit decimal must remain readable at 200% text (Task 1 existing target test plus actual captures).
- A canceled document picker or late canceled job must leave the correct review/preview intact (Task 1 existing cancellation tests).
- Switching account/backend during a cloud operation must not publish or navigate with stale IDs (Task 2 existing scope tests plus saved callback guard).
- Already-imported presets may have been edited; saved UI must not present original proposal counts as current (Task 1/2 result checks).

### Task 1: Shared cards and local file flow

**Files:**
- Create: `app/src/main/kotlin/com/ironvellum/app/ui/ai/AiComponents.kt` if shared helpers warrant it.
- Modify: `ui/ai/LocalConnectorScreens.kt`, shared `AiFrame`/`DraftTargets` in `ui/ai/McpScreens.kt`, `ui/IronvellumNav.kt` under the Kotlin app root.
- Modify: `app/src/main/res/values/strings.xml` and all seven translated string files.
- Test: `app/src/androidTest/kotlin/com/ironvellum/app/ui/ai/LocalConnectorScreensTest.kt`, `LocalConnectorNavigationTest.kt`; adapt existing `McpScreensTest.kt` only where shared rendering changes expectations.

**Interfaces:**
- Consumes: existing `LocalContextExport.summary`, `LocalDraftReview`, `LocalSave.Saved.localPresetId`, `savedPresetName(id)`, existing native UI primitives.
- Produces: shared native card/state/disclosure helpers, unchanged `DraftTargets(draft, movementName)` contract, `LocalConnectorScreen(..., onOpenWorkout: (Long) -> Unit)`, saved state retaining real ID only when the preset exists, explicit nav callback to `Routes.riteDetail(id)`.

- [x] Read the approved spec and existing local screen/navigation tests. Add a meaningful integration check that Save then View opens the actual new local preset; cover repeated and deleted receipt availability using existing fixtures rather than a styling assertion.
- [x] Run the targeted test to establish missing View behavior before implementing it. Emulator commands must use the shared lock and emulator serial.
- [x] Implement shared components and local hub, preview, review, busy/error/result states exactly as the spec. Shared frame must keep key actions reachable without losing scroll access at 200%; preserve cloud call compatibility for Task 2. Keep exact kg values visible and names disclosure always visible. Local Not now closes only RAM review. Add all localized/themed resources with correct plurals.
- [x] Wire retained local IDs through `IronvellumNav` with safe back-stack behavior; deleted receipts have no View action and no re-save path. Preserve job/picker semantics.
- [x] Run existing local screen/navigation and shared target tests, updating copy and scroll expectations without weakening behavior checks. Capture running local hub/preview/review/saved/repeated/deleted/error states, both palettes, and key 200% target/action screens using existing shot harnesses. Report exact commands/counts and inspected capture paths.
- [x] Self-review and commit the coherent local/shared unit; write task report. Do not push or run the full gate yet.

### Task 2: Optional connected screens and whole-flow verification

**Files:**
- Modify: `app/src/main/kotlin/com/ironvellum/app/ui/ai/McpScreens.kt`, `AiComponents.kt` as needed, `ui/IronvellumNav.kt`.
- Modify: all eight string resource files for added connected copy.
- Test: `app/src/androidTest/kotlin/com/ironvellum/app/ui/ai/McpScreensTest.kt`, related existing scope/receipt tests and navigation checks.

**Interfaces:**
- Consumes: Task 1 shared presentation helpers and native `DraftTargets`; existing scoped repository and `McpRepository.Saved(localPresetId, acknowledgementPending)`.
- Produces: `McpScreen(..., onOpenWorkout: (Long) -> Unit)` with current-account guarded callbacks, native cards for connections/inbox/review/results/recovery, unchanged protocol and repository behavior.

- [x] Read approved connected-screen sections and Task 1 interfaces/report. Add/extend saved callback integration coverage: real local ID navigates while account-current; account invalidation suppresses stale action. Establish failing missing callback behavior first.
- [x] Apply native cards to signed-out/unsupported/loading/error, exact copyable address, connected grants, optional recent access, inbox/empty/pagination, review and results. Preserve visible freshness/retention, unverified sender, permission and status semantics, actual rejection, expiry, cursors and acknowledgement handling. Reuse Task 1 details and exact targets.
- [x] Retain saved local preset ID and wire guarded View workout callback through navigation. Saved screen must not assert stale proposal counts. Never invent a View action for an accepted cloud envelope with no known local ID.
- [x] Run focused connected-screen and scope tests on the emulator under the shared lock. Adapt copy/scroll expectations only; inspect actual dark/plain and Vellum/themed screenshots and 200% key states. Run resource-contract checks for all locales.
- [x] Self-review, commit and report commands, counts, captures and any remaining concerns. Do not push or run the full gate concurrently with other builds.

## Controller final verification and delivery

- [x] Review each task independently, then whole change once; resolve important findings with scoped re-review.
- [x] Run the real full local gate alone using `.tmp/run-dependency-gate.py` with a unique label; require success and wall clock strictly under 600 seconds. Record accurate evidence in `docs/RELEASE_CHECKLIST.md` where appropriate.
- [x] Finish existing complete-training phone/PC delivery: private fresh backup, safe explicit phone `install -r`, actual app export, byte-identical PC file handoff, training-fidelity and official SDK checks, no phone data clearing. Verified after the phone reconnected: backup, in-place install, unchanged training, original v2 transfer and complete read-tool checks; private evidence remains on the workstation.
- [x] Commit verified results and push main under standing owner authorization. Update required memory and collect ledger rulings before removing only this plan's scratch workspace.

Self-review: both task file overlaps are sequential; shared helpers remain backward compatible until Task 2. All approved spec sections are assigned. Existing behavior tests handle unchanged data/security semantics; new tests target real navigation behavior, not reversible appearance.
