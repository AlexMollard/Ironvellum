# Art Attribution

Ironvellum ships no third-party artwork. Every image is either generated for
this project with Google **`gemini-3.1-flash-image`** (via `tools/art.py`) or
hand-authored as an Android VectorDrawable, and all of it is released under
the project licence (GPL-3.0-or-later, see `LICENSE`).

## Assets

| Drawable | Origin | Licence |
|---|---|---|
| `mipmap-*/ic_launcher.png`, `ic_launcher_foreground.png`, `ic_launcher_monochrome.png` | generated with Google **`gemini-3.1-flash-image`** (reached via OpenRouter because the omp `google-antigravity` route was quota-exhausted at the time — same underlying model), then cropped, background-keyed to transparency, inset to the adaptive-icon safe zone and downscaled per density with Pillow | project licence |
| `ic_launcher_background.xml` | hand-authored (radial void-green gradient) | project licence |
| `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | adaptive icon composition, no artwork of its own | project licence |
| `drawable-nodpi/art_empty_*.png` (quests, stats, skills, board, chronicle, allies, muster) | generated with `tools/art.py` in the house ink style below | project licence |
| `drawable-nodpi/art_crest_*.png` (ten crest frames) | generated with `tools/art.py` from `tools/art_batches/crests.txt` | project licence |
| `drawable/ic_rank_*.xml` (soldier, knight, commander, grand marshal) | hand-authored 48-unit vectors | project licence |

## House style: monochrome ink (adopted)

Original artwork for this project is generated with `tools/art.py`, which wraps
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

### Toggle

The treatment is a user choice, not a hardcoded look: **Settings -> APPEARANCE**
switches between `INK` and `CLEAN`. It persists on the profile row
(`inkStyle`, added by `MIGRATION_21_22`) and is mirrored into
`InkStyle.enabled`, which every ink primitive reads at draw time - so a flip
repaints immediately with no restart.

`CLEAN` is not "wobble set to zero". Each primitive falls back to the geometry
the app shipped with: cut-corner silhouettes, even rules, flat rails, true arcs,
single-pass borders, and no paper grain.

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

## Rank emblem set

`ic_rank_soldier` → `ic_rank_knight` → `ic_rank_commander` →
`ic_rank_grand_marshal` escalate from a single chevron to a crowned shield,
tinted in sequence (`#9AA3AD` muted ink → `#6FAE8C` muted green → `#34D399`
emerald, gold accents on the last).

## Attribution string

No artwork needs third-party credit. The only bundled third-party asset is the
Chakra Petch font (SIL Open Font License 1.1), credited in `NOTICE` and on the
app's Support/About screen.
