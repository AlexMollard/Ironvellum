# Art Attribution

Ironvellum's exercise illustrations come from
[Open Exercise Art](https://github.com/AlexMollard/Open-Exercise-Art) by Alex Mollard,
licensed CC BY-SA 4.0. The app ships 561 single-frame illustrations, traced from that
repository's PNGs into vector paths. Other project artwork uses the project licence
(GPL-3.0-or-later, see `LICENSE`). Adapted exercise instruction text retains its upstream
credit and licence below.

## Assets

| Drawable | Origin | Licence |
|---|---|---|
| `mipmap-*/ic_launcher.png`, `ic_launcher_round.png`, `ic_launcher_foreground.png`, `ic_launcher_monochrome.png` | owner's selected 02 Folded ribbon IV, extracted directly from the approved mockup generated with Codex's built-in image-generation tool, without redrawing or regenerating the mark; warm-white monochrome source at `tools/art_batches/drawn/icon_iv_folded_mono.png` preserves the original alpha mask, inset and downscaled with `tools/icon_install.py`; legacy tiles composited on the adaptive near-black ground. Prompt and provenance: `tools/art_batches/icon_iv_folded_mono.txt`. | project licence |
| `ic_launcher_background.xml` | hand-authored (near-black `#0C0C0B`) | project licence |
| `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | adaptive icon composition, no artwork of its own | project licence |
| `drawable-nodpi/art_empty_*.png` (quests, stats, skills, board, chronicle, allies, muster) | generated with `tools/art.py` in the house ink style below | project licence |
| the 28 crest marks (`ui/components/CrestMarks.kt`) | hand-authored SVG line art in `tools/crest_art/marks.txt`, baked into Compose path data by `tools/crest_art/build.py` (which refuses a solid fill); drawn in flat gradients at runtime. They replace the ten generated `art_crest_*.png` frames. | project licence |

### Exercise illustrations

The illustrations in the Open Exercise Art repository were generated with Codex's built-in
image-generation tool and approved individually by the owner. Their visual inspiration was
[Everkinetic](https://github.com/everkinetic/data), created by Greg Priday, and
[Workout Guide](https://github.com/bryllim/workout-guide), by Bryl Lim.
No original upstream exercise SVGs, raster frames or animation path geometry remain
in the current artwork collection or shipped exercise assets.

The repository is the git submodule `art/`, pinned to a commit. An exercise is approved when
it is listed in `art/catalogue.json` with a matching PNG hash. Batch prompt and feedback
records live in `tools/exercise_art/reviews/`. See `tools/exercise_art/ART_STORAGE.md` for
the layout and how to add art.

`build.py --owner-only` thresholds and traces the approved PNGs, redraws their
centre lines at one house weight (0.8% of the drawing's long side), and produces
the single-frame `own_<slug>.paths` assets. The app supplies the ink colour at draw
time. Fix source images in the art repository rather than hand-editing the tracing
code or built vectors.

The traced `.paths` files are adaptations of CC BY-SA 4.0 artwork and carry that licence.
Credit: Open Exercise Art / Alex Mollard, CC BY-SA 4.0,
https://github.com/AlexMollard/Open-Exercise-Art; the licence text is in
`licenses/CC-BY-SA-4.0-exercise-art.txt`.

### Technique drawings

The Codex uses the same approved stills from the art repository. `docs/art/skill-technique-art.json`
records each technique's mapping, including the shared drawing for load variants
of the same lift. The app's own pictograms remain available as a fallback.

### Exercise catalogue and instruction text

The additional exercise catalogue records and written instructions remain adapted
from [Everkinetic](https://github.com/everkinetic/data), created by Greg Priday,
with instruction text lightly edited for Ironvellum (`domain/LibraryGuides.kt`).
These textual adaptations retain [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).
The licence text is retained at `licenses/CC-BY-SA-4.0-exercise-art.txt`.
Historical source IDs in the art metadata document pose selection; they are not
copies of upstream drawings. Credits also appear in `NOTICE`,
`THIRD_PARTY_NOTICES.md`, the README and the app's Support screen.

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

The Support screen credits the exercise illustrations as generated for Ironvellum,
inspired by Everkinetic (Greg Priday) and Workout Guide (Bryl Lim). It separately
credits the adapted Everkinetic instruction text under CC BY-SA 4.0.
The Chakra Petch font is credited under the SIL Open Font License 1.1.

## README store badges

`docs/images/badge_fdroid.png` is F-Droid's official "Get it on F-Droid" badge (fdroid.gitlab.io/artwork) and `docs/images/badge_obtainium.png` is Obtainium's badge (`assets/graphics/badge_obtainium.png` in ImranR98/Obtainium), copied as their projects ask. They appear only in the README and are not shipped in the app.
