# Art Attribution

Ironvellum ships one set of third-party artwork: the exercise drawings (see "Exercise drawings" below, CC BY-SA 4.0).
Everything else is original. The existing empty-state and crest
images were generated for this project with Google **`gemini-3.1-flash-image`**
(via `tools/art.py`); the Codex technique plates are the exercise drawings below or the
app's own pictograms (no AI-generated art remains in the Codex); other glyphs include hand-authored
Android VectorDrawables. All ship under the project licence (GPL-3.0-or-later,
see `LICENSE`).

## Assets

| Drawable | Origin | Licence |
|---|---|---|
| `mipmap-*/ic_launcher.png`, `ic_launcher_round.png`, `ic_launcher_foreground.png`, `ic_launcher_monochrome.png` | owner's selected 02 Folded ribbon IV, extracted directly from the approved mockup generated with Codex's built-in image-generation tool, without redrawing or regenerating the mark; warm-white monochrome source at `tools/art_batches/drawn/icon_iv_folded_mono.png` preserves the original alpha mask, inset and downscaled with `tools/icon_install.py`; legacy tiles composited on the adaptive near-black ground. Prompt and provenance: `tools/art_batches/icon_iv_folded_mono.txt`. | project licence |
| `ic_launcher_background.xml` | hand-authored (near-black `#0C0C0B`) | project licence |
| `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | adaptive icon composition, no artwork of its own | project licence |
| `drawable-nodpi/art_empty_*.png` (quests, stats, skills, board, chronicle, allies, muster) | generated with `tools/art.py` in the house ink style below | project licence |
| the 28 crest marks (`ui/components/CrestMarks.kt`) | hand-authored SVG line art in `tools/crest_art/marks.txt`, baked into Compose path data by `tools/crest_art/build.py` (which refuses a solid fill); drawn in flat gradients at runtime. They replace the ten generated `art_crest_*.png` frames. | project licence |

### Exercise drawings (third party)

| Source | Author | Licence | Taken | Modified |
|---|---|---|---|---|
| [Everkinetic](https://github.com/everkinetic/data) (`dist/svg/<id>-relaxation.svg`, `-tension.svg`; records in `src/exercises/<id>/<id>.json`) | Greg Priday (everkinetic.com) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), `LICENSE.md` in the repository | the start and end drawings of each exercise, and the written steps and tips that the added movements' guides adapt | the two poses are rasterised together into one shared crop box, thresholded and vector-traced (potrace) to one path each, registered to a common ground line and centre, and shipped as path data; recoloured at draw time; steps lightly edited |
| [Workout Guide](https://github.com/bryllim/workout-guide) (`packages/workout-guide/assets/<slug>/frame-N.svg`, `manifest.json`) | Bryl Lim (https://bryllim.com); its first frames of 76 exercises are vector traces of Everkinetic drawings | assets [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) (`LICENSE-ASSETS`, `ATTRIBUTION.md`); its code is MIT and is not used | the three animation frames of every exercise Everkinetic does not draw, and its exercise names | the geometry is used unmodified: each frame's path data is copied verbatim; only a translation that registers the frames to one ground line and centre is stored beside it; recoloured at draw time |

Both are adaptations shared under CC BY-SA 4.0 and keep that licence; the rest of the app is GPL-3.0-or-later. The
licence text is in `licenses/CC-BY-SA-4.0-exercise-art.txt`. Credit is also in `NOTICE`, `THIRD_PARTY_NOTICES.md`, the
README and the app under Support. `tools/exercise_art/` rebuilds `app/src/main/assets/exercise_art/*.paths` and the
generated tables from fresh clones; `mapping.json` records, per drawing, which movement it shows and how sure the match
is, `art_report.json` which source frames each drawing kept, and `library.json` which upstream record each added movement
came from. Nothing was taken from GymMane. A drawing is vector path data and carries no colour of its own: the app fills it
in `IronvellumColors.Ink`. The free sets are used as drawn; a frame is dropped only when it is broken (one Workout Guide frame is empty).

### Technique drawings

The Codex shows no generated art. Of the 119 techniques, 50 show their own movement's drawing (the load variants of the
bench press, overhead press, deadlift and squat show that lift), 19 show the drawing of the movement they build on and say
so on the technique sheet ("Shown: Pull-up"), and 50 wear the app's own pictogram because no upstream drawing shows the
movement (levers, planches, flags, rings, handstand progressions, splits). `docs/art/skill-technique-art.json` lists each.
The earlier AI-generated technique WebP files, their audit pages and the manifest of their prompts were deleted.

### Owner-made drawings (project licence)

Drawings the owner generates for the gaps (`docs/art/missing-exercise-art.md`, `tools/exercise_art/missing.json`) are dropped
in `tools/exercise_art/owner/` and built by `build.py --owner-only`: each frame is thresholded and vector-traced with potrace
to one path, registered and shipped exactly as Everkinetic's poses are, as `own_<slug>.paths`. They are the project's own work
under the project licence (GPL-3.0-or-later). The owner's generator prompt, the style references (frames of the Workout Guide set) and
the date are noted per batch in this section when a batch is added.

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

The exercise drawings and the instructions adapted from them need credit under CC BY-SA 4.0: Everkinetic (Greg Priday)
and Workout Guide (Bryl Lim), shown on the app's Support screen and in `NOTICE`. The other bundled third-party
asset is the Chakra Petch font (SIL Open Font License 1.1), credited the same way.

## README store badges

`docs/images/badge_fdroid.png` is F-Droid's official "Get it on F-Droid" badge (fdroid.gitlab.io/artwork) and `docs/images/badge_obtainium.png` is Obtainium's badge (`assets/graphics/badge_obtainium.png` in ImranR98/Obtainium), copied as their projects ask. They appear only in the README and are not shipped in the app.
