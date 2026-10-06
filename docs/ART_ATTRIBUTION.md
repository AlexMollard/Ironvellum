# Art Attribution

Ironvellum ships no third-party artwork. The existing empty-state and crest
images were generated for this project with Google **`gemini-3.1-flash-image`**
(via `tools/art.py`); the technique illustrations below were generated
with Codex's built-in image-generation tool; other glyphs include hand-authored
Android VectorDrawables. All ship under the project licence (GPL-3.0-or-later,
see `LICENSE`).

## Assets

| Drawable | Origin | Licence |
|---|---|---|
| `mipmap-*/ic_launcher.png`, `ic_launcher_round.png`, `ic_launcher_foreground.png`, `ic_launcher_monochrome.png` | emerald IV monogram generated with Codex's built-in image-generation tool from the owner's selected Original Refined mockup; transparent source retained at `tools/art_batches/drawn/icon_iv.png`, inset and downscaled with `tools/icon_install.py`; legacy tiles composited on the adaptive near-black ground | project licence |
| `ic_launcher_background.xml` | hand-authored (near-black `#0C0C0B`) | project licence |
| `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | adaptive icon composition, no artwork of its own | project licence |
| `drawable-nodpi/art_empty_*.png` (quests, stats, skills, board, chronicle, allies, muster) | generated with `tools/art.py` in the house ink style below | project licence |
| `drawable-nodpi/art_crest_*.png` (ten crest frames) | generated with `tools/art.py` from `tools/art_batches/crests.txt` | project licence |
| `drawable-nodpi/skill_*.webp` (119 technique illustrations across all 14 Paths) | generated individually with Codex's built-in image-generation tool. The owner chose the first Dead Hang pilot (`exec-d14e11a6-fc99-47fe-b8ca-ffb581007096.png`) as the style reference. Shipped WebP files retain the generated warm-bone/charcoal colours and transparency, with a maximum edge of 640 px. The [technique art manifest](art/skill-technique-art.json) records source image identifiers, prompts, dimensions and file hashes. | project licence |

### Technique illustrations

The technique set follows the owner's approved detailed bone-ink athlete style.
The earlier crest/empty-state generator and its tinting rules remain unchanged.
Each technique owns a distinct bundled image, shared by its 48dp tree illustration
and 176dp About preview. Node states, tier labels, progression and actions stay in
code. No runtime image generation or remote image hosting is required.

For another technique, use the built-in image generator with the approved Dead
Hang source as a **style-only reference**, a genuinely transparent background,
the technique's published standard and a precise full-body pose description.
Retain the raw generated PNG, preserve alpha when reducing it to WebP, and update
the manifest and `TechniqueArtwork.kt` together. Do not apply the older generator's
automatic tinting to this set.

## House style: monochrome ink (adopted)

The earlier empty-state and crest artwork was generated with `tools/art.py`, which wraps
Google `gemini-3.1-flash-image` through omp's `google-antigravity` provider
(local headroom proxy -> Cloud Code Assist). Generated pieces are project-licensed.

The style is a contract held in the script, not a per-request instruction, so
screens cannot drift apart:

- **`ink`** (house default) - sumi-e brush work, visible dry-brush texture,
  imperfect hand-painted edges. Chosen over `line` (read as a stock pictogram)
  and `woodcut` (hard square frame, invented lettering).
- **Black ink only.** Colour is banned outright, not steered: tinted pieces sat
  beside untinted ones and the set stopped looking like one hand.
- **Bone ink on transparency.** Monochrome ink is drawn dark on paper, which is
  the wrong polarity for a panel at luminance 23 - so ink density becomes alpha
  and every piece is re-tinted to one bone tone. That tone is **read from
  `IronvellumColors.Ink` in `Theme.kt`**, not copied: UI text and artwork must be
  the same ink, and holding the number twice drifted the first time it was
  tried.

The enforcement point is `_ink_tint_from_theme()` in `tools/art.py`, which
regexes `val Ink = Color(0xFF……)` out of `Theme.kt` at import time. Renaming or
moving that token breaks generation loudly with the path it tried, which is the
intended failure: the alternative considered was declaring `tools/art.py` as an
input to the unit-test task, but that keeps the number in two places and only
*detects* disagreement. The generator already depends on this repo's layout
(it writes into `app/src/main/res/`), so reading one token adds no new coupling.

Every run self-audits and a failing audit exits nonzero rather than emitting
unusable art (the rejected PNG is still written, for inspection only):

| audit | fails when |
|---|---|
| chroma | more than 5% of opaque pixels are tinted (max-min channel > 24) |
| contrast | more than 25% of the ink sits at or below the panel's luminance (23) |
| backdrop | any corner is opaque, **or** more than 55% of the image is opaque |

The backdrop rule needs both halves: one piece came back as a white paper
square inside a ragged border, which passed a corners-only check.

### Retired appearance toggle

The ink chrome treatment and its Settings toggle are gone: the app has one look,
the former `CLEAN` one (cut-corner silhouettes, even rules, flat rails, true
arcs, single-pass borders, no paper grain). The `inkStyle` column on the profile
row (added by `MIGRATION_21_22`) is kept, unused, so Room's schema and old
archives stay valid. The ink style above still describes the generated artwork.

### Regenerating a piece

The `google-antigravity` image route has a small quota that can run out
mid-batch (`QUOTA_EXHAUSTED`; the error carries a `quotaResetDelay`), so batch
runs go through the resumable `tools/art_batches/run.py`. One piece:

```
python tools/art.py --style ink "<subject>" \
  -o app/src/main/res/drawable-nodpi/art_empty_<name>.png \
  --size 512 --alpha --colors 64
```

Empty-state art must swap as a set, or one screen keeps the old style.

## Attribution string

No artwork needs third-party credit. The only bundled third-party asset is the
Chakra Petch font (SIL Open Font License 1.1), credited in `NOTICE` and on the
app's Support/About screen.
