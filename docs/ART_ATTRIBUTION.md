# Art Attribution

All artwork in this project is original. Icons are hand-authored Android
VectorDrawables and generated pieces were made for the project with Google
`gemini-3.1-flash-image`; all of it is released under the project licence.
Nothing is sourced from a third-party icon set.

## Assets

| Drawable | Origin | Author | Licence | Source URL |
|---|---|---|---|---|
| `mipmap-*/ic_launcher.png`, `ic_launcher_foreground.png`, `ic_launcher_monochrome.png` | generated for this project with Google **`gemini-3.1-flash-image`** (reached via OpenRouter because the omp `google-antigravity` route was quota-exhausted at the time — same underlying model), then cropped, background-keyed to transparency, inset to the adaptive-icon safe zone and downscaled per density with Pillow | — | generated asset, project licence | — |
| `ic_launcher_background.xml` | original Ironvellum work (radial void-green gradient), no third-party source | — | project licence | — |
| `ic_line_pull.xml` | original artwork | — | project licence | — |
| `ic_line_push.xml` | original artwork | — | project licence | — |
| `ic_line_handstand.xml` | original artwork | — | project licence | — |
| `ic_line_lever.xml` | original artwork | — | project licence | — |
| `ic_line_planche.xml` | original artwork | — | project licence | — |
| `ic_line_legs.xml` | original artwork | — | project licence | — |
| `ic_rank_soldier.xml` | original artwork | — | project licence | — |
| `ic_rank_knight.xml` | original artwork | — | project licence | — |
| `ic_rank_commander.xml` | original artwork | — | project licence | — |
| `ic_rank_grand_marshal.xml` | original artwork | — | project licence | — |
| `art_empty_quests.xml` | original artwork | — | project licence | — |
| `art_empty_stats.xml` | original artwork | — | project licence | — |
| `art_empty_skills.xml` | original artwork | — | project licence | — |
| `mipmap-anydpi-v26/ic_launcher.xml` | adaptive icon composition (no artwork) | — | project licence | — |
| `mipmap-anydpi-v26/ic_launcher_round.xml` | adaptive round icon composition (no artwork) | — | project licence | — |

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

### Status: on hold

The `google-antigravity` image route is **quota-exhausted**
(`QUOTA_EXHAUSTED`, `cloudcode-pa.googleapis.com`); the error carries a
`quotaResetDelay` - last seen `4h38m`, so check it rather than guessing. The
allowance is small enough that a single throwaway probe can consume it, so
spend the window on real subjects only.

The shipped `art_empty_*` assets remain the original vectors listed
above; nothing half-finished is wired in. Approved ink compositions (gate,
balance scale, bare tree) are parked in `.tmp/art-parked/` at 256px -
recovered from a preview composite, so they are NOT release-quality and exist
only as a reference for re-prompting. When quota resets, regenerate at 512px:

```
python tools/art.py --style ink "<subject>" \
  -o app/src/main/res/drawable-nodpi/art_empty_<name>.png \
  --size 512 --alpha --colors 64
```

All three empty states must swap together or one screen keeps the old style:

| drawable | call site |
|---|---|
| `art_empty_quests` | `ui/dashboard/DashboardScreen.kt` (rest-day quest panel) |
| `art_empty_stats` | `ui/stats/StatsScreen.kt` (Readings section) |
| `art_empty_skills` | `ui/titles/SkillJournal.kt:227` |

## Rank emblem set

`ic_rank_soldier` → `ic_rank_knight` → `ic_rank_commander` are an original
chevron-shield family, tinted in an escalating
sequence (`#9AA3AD` muted ink → `#6FAE8C` muted green → `#34D399` emerald);
`ic_rank_grand_marshal` is an original crowned shield in sovereign gold `#F2C14E`.

## Attribution string

No artwork needs third-party credit: every drawable is original to the project.
