# Preparation: Workout card refinement

The owner chose **Workout card** and approved agent-driven implementation on 2026-11-07 after reviewing all 14 prototype states alongside real app captures. This is the approved presentation refinement of the completed warmup/cooldown feature, not a replacement of its data model or rules. No further design approval is required.

Reference: `C:/Users/alexm/.codex/visualizations/2026/11/06/01a1b040-5ebf-7c93-99d9-bd3f90d65578/warmup-cooldown-refinement.html`. The owner's explicit Workout card choice overrides any persisted widget selection of Focused movement. The HTML is a visual reference, not application source; use existing Compose components, gallery art and guides.

## Intent and boundaries

Make preparation feel like the existing workout: a compact offer, one framed movement card, and the familiar NEXT row with its emerald check. Improve hierarchy and density through grouping, spacing and removal of repeated information. Preserve normal app typography, minimum 48dp interactive targets, accessible names and 200% font scaling.

Scope is presentation and entry routing. Keep Room schema **47**, archive format **15**, repository/domain semantics, reward/scoring, recovery, legacy import/prefix handling and progress normalization unchanged unless a specific failing test proves an essential integration fix. Add no dependency, new gallery movement, artwork, version bump, release, tag or Actions trigger.

The prior design, `docs/superpowers/specs/2026-11-06-warmup-cooldown-design.md`, remains authoritative for persistence and behavior. In particular:

- Warmups remain enabled by default (`true`). Global off hides only untouched Auto general suggestions; saved routines, explicit legacy warmups and existing progress remain available. Cooldown remains independent.
- Explicit manual completion before timer expiry remains possible. Timer expiry never advances a unit. Retain the five-second readiness phase, real remaining time and explicit resume after leaving.
- COMPLETE_UNIT/SKIP_UNIT use the key captured by the displayed unit; transaction validation rejects stale/repeated actions atomically.
- Preserve legacy target labels, weights and distances, draft metadata through recreation, and clearing superseded legacy metadata only when exercise/measure/target/load changes. Notes/rounds/repeat alone retain it.
- Session snapshots remain immutable when routines or workout attachments change. Sealed summaries show exact stored results and never replay awards.

## Compact offers and entry

Keep the warmup offer above the existing working sets before the first completed working set; never obstruct logging. Keep cooldown on the final summary after the existing reward pages. Each unfinished offer has kind, actual movement count, an honest estimated time when available, routine title, a compact emerald Begin/Resume action and an overflow containing Change routine and Skip. The title opens the optional overview. Terminal offers show compact completed/skipped status and View; do not repeat the routine list.

For fully timed routines, estimate target seconds multiplied by rounds and side/direction repetitions, round up to whole minutes, and label approximately; readiness and transitions make it an estimate. Omit the minute estimate for an empty routine or any rep/manual legacy prescription without a known duration. Never copy the mockup's fixed “3 movements” or “~3 min” into production.

Begin persists START through the existing session view model **before** opening the runner, for warmup and cooldown. Guard repeated entry taps and navigation against duplicate pending actions and surface failure without exiting. Resume uses the existing persisted cursor and remaining time. Save SUMMARY before opening any cooldown screen. Do not encode a durable `start=true` route argument or start/resume in a composition effect: recreation must not restart a paused timer.

View/title opens the overview without START, RESUME, PAUSE or another progress mutation. Rotation/process restoration of this read-only preview stays read-only. An explicit Begin/Resume from that overview persists its action before revealing guided movement. Returning from a terminal overview retains terminal state.

## Guided runner

Use PushedHeader with routine context and one overflow. Above a single EntryCard show movement index, actual completed/total unit progress and a subtle progress line. The card holds the gallery movement title and Info action, side/direction and round, existing movement art, real timer or manual prescription, a neutral InkChip Pause/Resume control for timed steps, and one short cue (the saved note, otherwise one existing guide cue/instruction). Info opens the full existing gallery guide. No second instruction block or unframed large-art alternative.

Keep a compact Up next row for the actual next side/direction/round/movement. The persistent dock follows the existing workout NEXT/check language: a readable next-unit description and emerald check with a specific accessible completion description. At the last unit describe completion accurately. Use a preparation-specific composition of existing furniture; do not entangle preparation with working-set logging/rest state.

Overflow houses Skip step, View routine, Change routine, Edit a copy, and Finish preparation. Skip step retains the existing SKIP_UNIT behavior for the displayed side/direction/round; it must not promise to skip an entire movement. Keep current confirmation/reset behavior, pause-before-leaving for actions that actually leave guided execution, and explicit finish semantics (remaining work is skipped, never falsely counted as completed). Captured displayed keys still guard unit actions. All real units run in full; the mockup jumping to completion after two sample interactions is only an abbreviated demonstration.

Completion is compact: routine identity, actual completed and skipped unit counts, movement count, optional View movements, and one primary Return to trial/Return to summary. Do not show a full repeated movement list as the default completion screen, and do not call skipped work completed. After returning, show the compact terminal offer.

## Library, selector and editor

Library retains Warmups/Cooldowns tabs and empty personal-library guidance. Use compact personal-routine cards/rows with identity, count/estimate, view/edit options, compact immutable starter rows that create editable copies, and one Create routine dock.

Selector uses one radio group for Auto, personal routines and None, a single preview of the selected routine, Create/Manage rows, and one Use warmup/Use cooldown dock. Selecting or previewing changes only the selector draft; only Use returns the choice to the original owner. Preserve that owner through nested creation/management, including unsaved workout exercise IDs and initial choice. None has no fabricated preview. Existing explicit legacy-prefix explanation remains truthful.

Editor follows the workout EntryCard pattern: one expanded movement, collapsed summary rows, existing target/round steppers, compact repeat choice, optional cue/note, reorder and remove actions, gallery picker, name field and Save dock. All repeat choices and reorder actions remain accessible. Keep existing validation, future-sessions-only guidance, discard and delete confirmations and draft survival. Prefer compact controls that expand on request over permanently stacked repeated text; allow stacking when text scale or width requires it.

Every new or changed label, plural, menu description and accessibility action uses paired plain/themed resources in all currently supported locale string files. Names, notes and gallery guide text remain data. Update the hardcoded-string ratchet downward where literals are removed.

## Acceptance and evidence

Implement in two sequential, independently reviewed tasks: (1) shared presentation, offers/entry, runner/completion and summary; (2) library/selector/editor, integration verification and final capture set. The second task reuses the first task's shared interfaces; no simultaneous edits to shared files.

Update existing behavior tests first. Add only the focused entry/preview coverage missing from the suite. Verify Begin reaches movement directly; View does not start or resume; paused preview/recreation preserves the saved timer; stale/double taps still advance once; all actual sides/rounds/steps are retained; early completion is possible; mixed skipped/completed results are truthful; repeated cooldown entry changes no rewards or stored figures; nested selector ownership and legacy draft metadata survive.

Capture every changed state from the built app: workout offer, overview, warmup movement, paused movement, warmup completion, workout terminal offer, summary cooldown offer, cooldown movement/completion, summary terminal offer, library, selector, editor and leave confirmation. Inspect against the approved reference in both themes; include long names, keyboard open and 200% text with dock/actions reachable. Use real stored counts, timer and artwork. Prototype approval is complete; these captures prove implementation fidelity.

All tests run locally. Instrumented work and captures use `emulator-5554` under the shared main-checkout device lock; never target the owner's phone. Run the complete local gate alone, inspect actual test counts and its seven-minute budget, then commit and push each verified coherent unit using the house style. Record inherited warnings separately from new failures.
