# AI handoffs: current journeys and proposed UX

Historical design: the desktop connector described below is preserved on
[archive-personal-connector](https://github.com/AlexMollard/Ironvellum/tree/archive-personal-connector)
and is no longer shipped on main. Phone sharing/import remains supported.
Use [production MCP](../../../server/production_mcp/README.md) for hosted integration.

Status: direction and shown mockups accepted by the owner, 2026-11-22 ("That proposal seems like the step in the correct direction"). The phone and desktop implementations and their reviews are complete at c3de5ee0; final verification and remaining limits are recorded in docs/RELEASE_CHECKLIST.md. The owner has confirmed: accept workouts from any AI, even without a prior training export; allow exercises, sets, reps and weights to be edited during review before saving; always require explicit Save; provide a small desktop window; expect most desktop users to have an agent set up the connection. Implementation details are specified in the linked mobile and desktop plans. New layouts still require real-app-based mockups before their UI code.

## Why this change was needed

The individual AI screens used native cards, but people still managed the handoff: choose files, find a recipient, invent instructions, move files back, and restart a desktop connector. Outbound sharing worked on the owner's phone; the return journey had a functional gap as well as extra navigation.

## Journeys observed before this change, step by step

### 1. Ironvellum to an AI app on the phone

1. Open Train and scroll to AI tools, immediately before Recent workouts.
2. Open AI tools.
3. Choose Preview training file and wait for preparation.
4. Review the counts and sharing disclosure.
5. Tap Share file.
6. In Samsung's share sheet, find the AI app; on the tested phone this meant scrolling the apps strip and tapping More.
7. Choose Claude or ChatGPT. Both were offered as JSON recipients; actual processing inside either AI app was not tested.
8. Ask a question in that app. Ironvellum currently supplies training data, without a task prompt or instructions for making an importable workout.

Saving to Downloads is optional for this journey. Each attachment is a snapshot; giving the AI newer training requires sharing again.

### 2. An AI workout back to Ironvellum on the phone

1. Obtain a compatible proposal file. The desktop connector currently creates the supported proposal envelope; a normal phone AI response is not automatically compatible.
2. Download or otherwise save that file somewhere Android's document picker can access.
3. Switch to Ironvellum.
4. Open Train, scroll to AI tools, and open it.
5. Choose Import a workout draft / Choose draft file.
6. Find and select the downloaded file.
7. Read the workout review and any source/format feedback.
8. Tap Save workout, then optionally View workout.

There is no direct AI-draft Share/Open entry and no paste entry. The existing Android incoming-file handler targets workout CSV imports, not AI draft JSON. A generic AI JSON file sent as text/plain or octet-stream can enter that existing CSV route. The AI proposal format additionally requires source IDs, an exact training-file SHA-256, timestamps and a valid draft body. Those technical fields are currently assembled by the PC connector, not by a guided phone flow.

### 3. Desktop connector

First setup, usually performed by the person's agent:

1. Have a desktop AI client that can launch a local MCP connector.
2. Install Python and the connector into its own environment.
3. Pick private training-file and draft-output locations.
4. Add the connector command/arguments to the AI client's settings and load it.

The user-facing setup can already begin with asking an agent to do these tasks. The current friction is the lack of one standard setup action and visible verification, not a requirement that the person type every command themselves.

Routine use:

1. Prepare training in Ironvellum and save/share the file.
2. Transfer the original file to the PC.
3. Replace the configured training.json, preserving its bytes.
4. Restart the connector/client so it reads the new snapshot.
5. Ask the AI about training or request a workout.
6. Locate the generated UUID-named proposal in the outbox.
7. Transfer that original file to the phone.
8. Navigate to AI tools, choose the file, review and Save.

The connector deliberately pins its input snapshot. Refreshing it safely requires more than rereading a file during an arbitrary request. The phone cannot learn whether a PC proposal was generated until it receives the file; the PC cannot claim the phone saved it.

## Approach

Use a phone-first, file-based round trip, with an optional desktop connector. This follows the owner's existing offline/file choice and keeps servers, pairing, hosting and Ironvellum sign-in optional. A cosmetic cleanup alone would leave the return-format and desktop-refresh gaps. A live hosted connection is a different, optional workflow and is not required for this proposal.

The accepted direction adds a provider-neutral workout-import format and direct incoming-file routing, plus an optional folder-refresh mode on the desktop. Precise contracts and task boundaries belong to the implementation plans; accepting the direction does not authorize unrelated features.

## Proposed phone journey: give training to an AI

1. Open AI tools and choose Share with AI.
2. See one concise preview: training counts, names/privacy disclosure, file date and an optional question or intent such as reviewing progress or suggesting a workout.
3. Choose an installed compatible AI app or the regular Android share menu.
4. Continue the conversation in that AI app.

The handoff should contain readable guidance and a simple supported workout-return format. Do not make the user supply hashes, identifiers or timestamps. Keep the existing machine-readable training context compatible with the desktop reader; an AI-ready companion/package is a separate, explicit format rather than silently adding fields to the frozen v2 schema.

Prefer compatible installed-app shortcuts and the last chosen recipient, with the system share menu always available. Avoid automatic sending. Providers may discard shared prompt text, so test the actual attachment behavior and retain an explicit Copy instructions fallback. Do not claim an AI received or understood an attachment merely because its app appeared in a chooser.

Keep Save a copy as a secondary action, separated from sharing with an AI. Default to complete training, preserve the permanent names disclosure and keep notes/health/private content excluded. Preserve Muscles first and AI before Recent on Train.

## Proposed phone journey: bring a workout back

Preferred file route:

1. In the AI app or Files, choose Share/Open with Ironvellum for a supported workout file.
2. Ironvellum opens the workout review directly.
3. Edit exercises, sets, reps or weights and resolve anything missing or unsupported, then explicitly Save.
4. View the saved workout.

Fallback when the AI cannot provide a file:

1. Copy the workout code the AI was instructed to return.
2. Open Add a workout from AI > Paste workout.
3. Review and edit the workout, resolve clear errors, then Save.

File picker remains available. This is provider-neutral and does not require a previous training export. Do not promise arbitrary prose interpretation or a hidden AI service: a clear supported structure is the reliable first path, with actionable feedback for unsupported content. A person can copy formatting instructions into any existing chat. Malformed or ambiguous content must never silently become a different exercise, load or prescription. Review stays disabled while the paste field is empty; Save stays disabled until all required fields and movement matches are valid.

Introduce a separate portable workout-import format; do not loosen existing source-bound proposal-v1 checks. An unlinked workout is visibly described as imported content, without claiming it was based on the person's training. Only show verified training-file provenance when the source can actually be matched. Initial target eligibility follows the current fixed-set/repetition draft capabilities; do not silently flatten rich schemes or unsupported metrics. Friendly explanations must say what can be corrected or request a supported return from the AI.

Both file and paste paths use the same bounded validation and review. Preserve exact decimal targets and unit clarity. New generic imports need transactional, persistent repeat-import protection, including after deletion; do not fake a historical training export to reuse the old protocol. Stable identity/canonicalization and receipt storage details belong to the implementation design. Existing v1 receipts remain unchanged.

The owner chose editable review. Reuse existing workout-editing controls in a RAM-only staging state; no permanent workout is created by changing a field. For source-bound proposals, keep original file bytes, source checks and receipt identity unchanged, and treat human edits as an explicit local overlay. Validate the final reviewed targets and commit them with the receipt atomically. Repeat import must not overwrite local edits or mint another saved workout. Do not claim edited prescriptions are byte-identical to the AI's original file. No backend draft or AI chat is rewritten by editing locally.

Add dedicated incoming AI-workout routing without breaking CSV import. Treat MIME type, extension and client name as hints. Read content URIs under their grants, bound bytes before parsing, distinguish formats, and handle expired grants or process loss with a clear retry. Never read local arbitrary paths or automatically trust shared text. Use the current RAM-only review and explicit unscheduled Save principles.

## Proposed desktop journey

Agent-first setup:

1. Ask the agent to connect Ironvellum, using one copyable setup instruction supplied by the app or project.
2. The agent runs a documented local setup action: install the connector, choose/confirm private folders, configure the requested supported AI client, and verify the connection.
3. The user opens the optional small status window if they want to see connection health, loaded training date, draft files or manual setup help.

Supply an agent-friendly setup entry point with machine-readable results and clear failure reasons. Do not make an agent click through a wizard or invent configuration. Preserve other client settings, back up any changed configuration and make repeat setup safe. Installing for one requested client must not change every AI client found on the PC. State what still needs the client to reload; do not claim success merely because files were written.

The owner selected a small window and then clarified that agents will perform most setup. Therefore the window is primarily status and recovery, not a mandatory onboarding wizard: Connector status, Training loaded with its date and coverage, Workouts ready, Open drafts folder, Copy agent setup instructions, and optional manual settings. It must not need to remain open for the stdio connector to work.

Routine use:

1. Transfer a new training file into the chosen input folder, without renaming it.
2. The optional folder-refresh mode validates and adopts it, showing the actual training date and coverage.
3. Ask the AI for advice or a workout.
4. Transfer the named draft to the phone using the user's chosen method.
5. Open/Share it into Ironvellum, review and Save.

File transfer remains necessary. This proposal does not promise automatic phone-PC transport, a server, pairing or cloud synchronization. Generated drafts should have a human-readable display name, with stable internal IDs and safe filenames. Expose a clear Open drafts location action or equivalent setup guidance; the AI's tool result must identify the actual artifact, not just a path the user has to infer.

Automatic refresh is opt-in. Keep existing explicit-file immutable mode and all v1 input/output contracts. In-flight reads, cursors and draft creation must stay bound to the training version they actually used. Validate a complete new file before adoption, retain the last valid snapshot on partial/invalid copies, and report a refresh problem rather than silently switching datasets or rewriting an outbox. Cross-dataset changes need an explicit user choice. Design the source-selection behavior and any additional v2+ arguments before implementation; never silently relabel a draft as based on newer data.

Setup must remain self-hostable and usable on Windows, Linux and macOS, with no required Ironvellum service, sign-in, ADB or paid hosting. Provide both the agent setup action and the chosen optional graphical status/manual-setup window, without changing the file model. Preserve private-filesystem checks and exact-byte transfers. Implementation framework and packaging are builder choices; the GUI must not become a dependency for headless agent setup.

## Priority and acceptance

1. Make incoming Share/Open work and add the portable import/paste round trip, retaining manual review and duplicate protection.
2. Make outbound sharing clearly task-oriented, with instructions that explain the return trip.
3. Remove routine desktop rename/restart work through explicit optional refresh mode, then improve first setup guidance.

Before UI code: show the real current app beside mockups for the hub, share preview, incoming/paste review/recovery and the chosen desktop setup presentation. Reuse native cards, palettes, typography, system pickers and current visual style. All new copy belongs in string resources, plain/themed and seven translations. Support phone/wide/short/200% and accessible controls. Do not put technical protocol fields in ordinary screens.

Verification must exercise real incoming intents and local Save, CSV coexistence, exact values and untrusted names, generic repeats/deletion, source-bound legacy imports, denied/expired grants, account independence, invalid/partial desktop refreshes and snapshot changes during pagination or draft creation. Use synthetic data for provider handoff checks. Never test destructively on the owner's phone. Run the real full local gate alone under 600 seconds once approved implementation settles.

## Evidence

Current local UI: `app/src/main/kotlin/com/ironvellum/app/ui/ai/LocalConnectorScreens.kt`. Incoming routing: `MainActivity.kt`, `data/IncomingWorkoutShare.kt`, `app/src/main/AndroidManifest.xml`. File contracts and PC workflow: `tools/local_connector/README.md`, `contracts/context-v2.schema.json`, `contracts/README.md`, and connector `__main__.py`/`stdio.py`. Graph lookup found MainActivity/incoming CSV symbols; the newer AI symbols were absent, so known-file source reads were used. Actual owner phone previously showed Claude and ChatGPT under Share file > More; no external AI ingestion was claimed.

## Review and mockups

One fresh builder check identified two owner decisions: pre-save corrections and desktop setup presentation. Both are now answered: editable review; a small desktop window, with agent-first setup and status/recovery as the window's main job. The owner subsequently accepted the proposed direction and shown mockups. Minor implementation details remain builder choices.

The following preview-only mockups were shown inline. They use the actual app's hub, preview and review screenshots as visual references. They are proposals, not claims of implemented screens. Counts and dates are illustrative. Native app tokens remain authoritative over incidental raster shading. Existing saved/receipt/loading/recovery and movement-gallery layouts are reused where no new layout is introduced. New layouts discovered during planning must be shown before their UI code is written.

| Board | Preview path |
|---|---|
| AI hub, sharing and optional PC status | $CODEX_HOME/generated_images/01a1b040-5ebf-7c93-99d9-bd3f90d65578/exec-3f1c5763-8c1e-451c-bcf5-c400b1767d6c.png |
| Paste, editable review and movement matching | $CODEX_HOME/generated_images/01a1b040-5ebf-7c93-99d9-bd3f90d65578/exec-93b9cebc-5c25-4b55-a7d0-b3fe060db07a.png |
| PC settings, invalid refresh and source choice | $CODEX_HOME/generated_images/01a1b040-5ebf-7c93-99d9-bd3f90d65578/exec-973bc2d2-dd18-4a47-83c4-b9134b813606.png |

## Implementation evidence

Both implementation plans are complete with reviewed fixes. The final full local gate passed below 600 seconds; the earlier cold-start integration run passed correctness but missed the timing budget. See [release verification](../../RELEASE_CHECKLIST.md). Computer control stopped when the owner instructed it; remaining native desktop visual and interaction checks are explicitly unverified. No owner-phone install or live AI-provider ingestion is claimed.
