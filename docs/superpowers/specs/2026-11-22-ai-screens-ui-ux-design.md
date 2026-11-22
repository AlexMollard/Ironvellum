# AI screens: native card styling and a clearer file workflow

Status: approved by the owner ("Use this design", 2026-11-22). This builds on the reviewed complete-training connector at `eae5ff10`; it does not replace that work or the pending phone/PC delivery.

## Intent

Make the new AI screens feel like Ironvellum's workout screens and make the everyday file workflow easy to understand: prepare training, use it with an AI on the computer, import its draft, review exact targets, explicitly save. The owner previously preferred the existing workout-card style and requires real-app-based mockups before UI code changes. Keep Muscles first on phone-width Train and AI tools immediately before Recent workouts.

## Approaches

The approved design is a card-based companion: two direct actions, compact summaries, read-only workout cards, optional details and clear outcome states. It reuses the app's existing type, colors, clipped panels and buttons. A guided setup wizard would explain each handoff on separate pages and add more taps. The card approach keeps a short guide available without making returning users repeat setup.

## Mockups already shown

These are proposed layouts derived from actual app screenshots, not screenshots of implemented changes. The native components and typography remain authoritative; raster mockups are not production assets.

| Coverage | Image |
|---|---|
| Hub and training-file preview | C:/Users/alexm/.codex/generated_images/01a1b040-5ebf-7c93-99d9-bd3f90d65578/exec-55f06277-8821-4c00-890e-f8360319af28.png |
| Draft review and saved result | C:/Users/alexm/.codex/generated_images/01a1b040-5ebf-7c93-99d9-bd3f90d65578/exec-fa914ee7-cfb8-4149-844a-cd43b4e27607.png |
| Loading, invalid file, already imported and later-deleted states | C:/Users/alexm/.codex/generated_images/01a1b040-5ebf-7c93-99d9-bd3f90d65578/exec-7499e0bf-08c0-41b0-9742-527812bab1cc.png |
| Optional connections, draft inbox and sign-in gate | C:/Users/alexm/.codex/generated_images/01a1b040-5ebf-7c93-99d9-bd3f90d65578/exec-d8296c11-9fce-4974-9b2a-4fb78db04905.png |
| Vellum/themed review and empty inbox | C:/Users/alexm/.codex/generated_images/01a1b040-5ebf-7c93-99d9-bd3f90d65578/exec-d1812e66-6fbc-48a5-b316-ad7d699f3ef9.png |

## Screen and interaction design

### AI tools hub

Use two workout-style action cards: Share training → Preview training file, and Import a workout draft → Choose draft file. The second action opens the existing Android picker directly; do not add an intermediate import page. A compact three-step guide explains the computer handoff. Put the existing two cloud destinations under a clearly optional expandable group. Do not fetch a cloud account or make network requests just to show the hub. No in-app AI chat, automatic workout generation or required sign-in is implied. Include concise setup/refresh help, using the existing local connector guide rather than promising an automatic connection.

### Training-file preview

Show All completed training, the actual prepared date, and three clear counts from LocalExportSummary: completed workouts, logged sets and saved workouts. Values in mockups are examples. Do not parse the serialized export in composition to obtain more statistics. The complete v2 path omits no records, so avoid repeating zero-omission lines in the normal summary. Preserve an explicit warning/details fallback if coverage ever reports omissions.

Show concise Included and Kept private rows and an always-visible disclosure that saved-workout, custom movement and routine names are included. Sharing details expands to the full existing scope, metadata and transfer/refresh instructions. The short view is a summary, not a change in what leaves the phone. Share file and Save on this phone remain distinct actions, with status feedback near the actions. Retain the local-only document flag and native share/document pickers. Copying a fresh file and restarting/refreshing the connector remains a manual handoff, not live synchronization.

### Draft review

Lead with the workout name, movement count and total prescribed sets. Keep the sender/client name visibly unverified. Render every movement as a read-only workout-style card, including all prescribed sets/reps and the exact canonical kg target. If the user prefers another unit, retain both the existing preferred-unit display and the exact kg target. Long decimals and names wrap; do not round away information or squeeze the movement name out of view. No editing controls appear before Save.

Warmup/cooldown use compact existing-style rows. Put source date, created date and expiry in Draft details; keep the older-file notice visible when applicable and use wording about a prepared/exported file, not an unsupported claim that a newer file was actually shared. Do not infer expiration from a new UI clock; repository checks remain authoritative.

Use a clearly reachable Save workout action with the existing unscheduled-save explanation. The local secondary action becomes Not now because it only closes the RAM review and does not delete the source file. Cloud Reject draft retains its actual reject semantics and danger treatment. Use a fixed header/action region with scrollable content where this improves reachability; nothing may be obscured by system bars or a software keyboard.

### Saved, repeated and deleted outcomes

Show a calm success state and the saved name. Add View workout using the existing `Routes.riteDetail(localPresetId)` route. Both local save results and McpRepository.Saved already provide that ID; the current UI drops it. Preserve it in RAM-only UI state and pass an explicit navigation callback from IronvellumNav. Do not use a cloud draft ID as a local preset ID.

Newly saved summaries may use the just-reviewed draft. For an already imported workout that may have been edited, do not claim stale targets/counts from the old proposal. The existing name lookup determines whether the local preset still exists. A deleted receipt shows Imported before with no View or Save and no recreation. No file is deleted by Not now. Cloud save/acknowledgement and current-account guards remain intact; never navigate using a stale account scope.

### Progress and errors

Use a shared state-card treatment with a relevant icon, concise title, reason and an available next action. Preparing/checking work remains cancellable exactly when the model permits it. Writing/saving must not offer a fake cancellation action. Preserve all current failure classifications, retry behavior, stale-job suppression and preview recovery. Invalid file, expired file, wrong dataset, unknown export, changed data, missing movement, storage failure and size limits keep distinct, accurate guidance. Native picker cancellation is not presented as an error. File-saved feedback remains persistent enough to find; it must not rely solely on an expiring toast.

### Optional connected AI pages

Reuse the same cards for the server address, connected apps, permissions, pending draft summaries and recent access. Keep the full exact address copyable, client names unverified, expiry/revocation states accurate and disconnect controls explicit. Recent access can expand. Retain visible concise freshness information that these AI connections see synced training, plus access/retention information; do not lose it while shortening the page.

Draft cards show real movement/set counts and open the existing review. Refresh and Load older retain their existing scope/cursor guards. Empty, unsupported-server, signed-out, expired/rejected/accepted draft and acknowledgement-pending states use the shared visual language, without inventing new backend operations. Sign-in is offered only where currently configured and required for optional connected services. File sharing stays independently accessible.

## Implementation constraints

- Use existing native Compose components, theme tokens, Chakra Petch, clipped borders and material icons. No generated raster assets, new font/dependency, gradients or provider branding.
- The reviewed read/draft protocols, file/resource limits, exact-byte provenance, manual Save, receipts and data privacy are unchanged. No server or schema migration.
- UI expansion flags and summaries can be remembered, but full drafts, raw files and review handles remain RAM-only. Preserve cancellation/current-job checks, immutable sharing and account/backend scope invalidation.
- All screen copy is in resources, including default/themed wording and all seven translation directories. Preserve count plurals, exact placeholders and accessible untrusted-text wrapping.
- Support small phone widths, wide layouts, both palettes and 200% text. Decorative icons are not announced; actionable rows/buttons have clear labels. Expanded details expose state to accessibility services.

## Verification and delivery

Use existing AI local/cloud screen, picker, cancellation, scope and receipt tests, adapting copy/navigation expectations without weakening their behavior assertions. Do not add tests mirroring reversible styling changes. A real saved-workout navigation integration check is appropriate for the new View action. Capture all major running states in dark/plain and Vellum/themed and verify key actions at 200% text. Run release checks and the full local gate alone under600seconds after the UI settles. Then finish the original private PC refresh and safe in-place phone update, preserving training data. The phone was disconnected at the latest ADB check; a reconnect question is pending.

Builder decisions left open: small helper/component decomposition, responsive stacking and spacing using existing tokens, exact concise localized wording and safe back-stack mechanics for the new View callback. Settle these without changing the stated user flow or data contracts.
