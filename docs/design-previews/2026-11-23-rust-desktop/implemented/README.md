# Implemented Rust desktop

The PNGs show the production Ironvellum desktop view rendered into a texture using synthetic local training files, connector sessions and ready drafts. Open the overview, manual settings, recovery and source-choice images to compare the dark and Vellum papers. The other images cover keyboard focus, mixed sessions, narrow layout and regional fonts.

Each platform read back 72 frames. Windows used a Radeon RX 9060 XT through Vulkan. Linux used the Mesa llvmpipe software Vulkan adapter. The platform manifests record the fixture, paper, locale, wording, scale, viewport, raw pixel digest, checked colors and layout measurements for every case. The text files preserve the renderer output. Windows retained sixteen representative full-size images. The Linux run used measurement mode. Fixture draft identities and platform rendering can vary across runs.

From the repository root, with the workspace toolchain and locked dependencies available, pass an empty output directory.

```sh
cargo run --locked --offline -p ironvellum-desktop --example render_desktop -- captures-windows
cargo run --locked --offline -p ironvellum-desktop --example render_desktop -- captures-linux --measure-only
```

The target uses the packaged [fonts and provenance](../../../../crates/ironvellum-desktop/assets/fonts/PROVENANCE.md). Acceptance covers texture upload, rendering and pixel readback, semantic status, caption alignment, clipping, panel geometry, palette, the focused primary action and the enabled radio outline before and after switching archives, including keyboard focus on the unselected archive. Native compositor behavior, OS dialogs, clipboard integration, assistive-technology output, IME candidate windows and macOS remain outside this offscreen qualification.
