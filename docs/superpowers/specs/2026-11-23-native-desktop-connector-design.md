# Native Ironvellum desktop connector

The approved desktop design uses Rust, egui/eframe and wgpu. The scope is the desktop application connector and the local libraries and headless companion it needs. Existing Android, website, hosted service, development MCP, artwork and developer tools retain their implementations.

## Structure and compatibility

- `ironvellum-contracts` validates the existing training and draft formats, exact numbers and bounded pages.
- `ironvellum-local` preserves private filesystem checks, settings, journals, watched exports and truthful session evidence.
- `ironvellum-connector` serves the local personal MCP and manages setup, status and source selection without a window or GPU.
- `ironvellum-desktop` provides the optional native window using the same model and guarded actions.

The desktop and headless executables are siblings. Closing the window leaves independently running connector sessions usable. Local use requires no Ironvellum account or server.

Existing settings, selected datasets, journal records, draft filenames, original proposal bytes and hashes remain compatible. Training files retain their frozen v1/v2 schemas. The personal connector retains its 8 MiB training input, 64 KiB draft input, 65,536-byte LF-inclusive stdio frames and 1 MiB rendered response limits. Watched sources retain four sources and a 32 MiB cache. A failed refresh preserves the last good snapshot and never relabels a session's loaded source.

Setup modifies only the selected named client entry and keeps recoverable backups. Drafts remain proposals for explicit review and saving on the phone. No desktop action automatically saves a workout, launches a provider or transfers phone data.

## Visual identity

Use the actual app's fonts, palette and hierarchy:

- Dark paper: page `#0C0C0B`, cards `#171715`, rules `#32302B`, ink `#E8E8E4`, secondary ink `#A3A099`.
- Vellum: page `#F7F3EA`, cards `#FEFBF5`, rules `#C9BDA9`, ink `#241B15`, secondary ink `#65584C`.
- Existing Chakra Petch typography with licensed regional fallback for the eight supported locales and both wording modes.
- Chamfered outer containers and primary actions, plain inner fields, quiet rules and restrained emerald actions. Gold indicates earned rewards.
- Flat surfaces and clear text, without decorative textures, glowing panels or a generic dashboard layout.

The operating-system palette is the default. Explicit command options select dark or Vellum, language and wording. Custom components retain keyboard operation, text editing, IME semantics, visible focus and accessibility roles.

## Screens and states

1. Overview: actual session evidence, loaded training coverage, ready draft cards, folder actions, setup instructions and manual settings.
2. Manual settings: training and draft folders, optional automatic refresh, visible save progress and safe cancellation.
3. Awaiting training: distinguish configured folders from a verified connector session and provide a useful next action.
4. Invalid refresh: retain good data and explain what each live session still uses; distinguish reconnect-required folder changes from an ordinary refresh.
5. Source choice: show current and proposed datasets, dates and coverage; allow keeping an already selected cached source after its export disappears.
6. Recovery: keep useful actions and truthful prior information through draft, observer and transient loading failures.

Untrusted names are literal text. Multiple live sessions show distinct actual loaded sources. A connected label indicates protocol evidence, not that a provider has consumed the training. Draft arrows reveal the original JSON file and do not execute it.

## Design and implementation evidence

- [Approved overview boards](../../design-previews/2026-11-23-rust-desktop/overview.png).
- [Approved settings and recovery boards](../../design-previews/2026-11-23-rust-desktop/settings-and-recovery.png).

Validation uses synthetic settings, training files, journals and sessions. Existing user data and installed connectors are not test fixtures. Offscreen rendering qualifies the shared view on Windows and Linux; native operating-system dialogs, clipboard interaction, assistive technology and macOS require their own platform evidence. Computer control and owner desktop screenshots are excluded.
