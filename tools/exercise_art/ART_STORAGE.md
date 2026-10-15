# Exercise art storage

All 562 exercise illustrations live in a separate public repository,
[Open-Exercise-Art](https://github.com/AlexMollard/Open-Exercise-Art) (artwork CC BY-SA 4.0,
code GPL-3.0). It is the single source of truth for the images. Monarch pins it as the
git submodule `art/`; there are no copies of the PNGs here.

- `art/png/<slug>.png`: the only masters, one still per exercise, no `-1` suffix.
- `art/catalogue.json` (schema `open-exercise-art-v1`): every approved exercise with its `id`, `name`,
  `aliases` and the `sha256` of its PNG and SVG.
- **Approval = present in the pinned catalogue.** An image is approved when its slug is in
  `art/catalogue.json` and the PNG's sha256 matches. The builder checks this on every run;
  there is no `APPROVED` list.
- `reviews/batch-NNN/`: batch prompts, feedback and exported approval reports; JSON records only, no image copies.
- `reviews/history/`: earlier review and pose-selection metadata, retained without the old images.
- `review-decisions.json`: the review site's record of past decisions, bound to the hashes of the images they judged.
  It is history; the build does not read it.
- `generation-review.json`: generation status and batch membership. Its style reference is `art/png/banded-donkey-kick.png`.
- `missing.json` and `replacement-catalog.json`: exercise identities, poses and generation briefs, and which slug fills
  which movement. An entry with `"technique": true` is also the drawing of the skill-tree node of the same name.
  Source-frame IDs are historical provenance, not bundled upstream artwork.
- `frame-counts.json`: one frame per exercise.
- `app/src/main/assets/exercise_art/own_<slug>.paths`: the built single-frame vectors used by the app. They are
  committed. Regenerate them; never edit them by hand.
- `review-site/dist/`: ignored, disposable copies for the local approval website.

## The submodule is only needed to rebuild art

The app, `tools/gate.py` and the unit and instrumented tests read the committed `.paths` files and
Kotlin tables, so they never touch `art/`. Only the builder does. Initialise it once:

```powershell
git submodule update --init art
```

Without it, `build.py --owner-only` and `build.py --check` stop with that instruction.

## Adding or changing art

1. Generate drafts into `drafts/batch-NNN/` with the helpers here and review them locally (the review site). Never auto-approve art.
2. After the owner approves, work in a local clone of Open-Exercise-Art (the `art/` checkout is one):
   copy the still to `png/<slug>.png`, export `svg/<slug>.svg`, add the row to `catalogue.json` and
   `INDEX.md`, and commit and push it there (see its `CONTRIBUTING.md`).
3. In Monarch, bump the pin: `cd art && git pull origin main`, then `git add art`.
4. If the slug is new, add an entry to `replacement-catalog.json` (`name`, `slug`, `kind`, `frameCount: 1`, and
   `"technique": true` for a skill-tree step), then rebuild and validate:

```powershell
tools/exercise_art/.venv/Scripts/python -P tools/exercise_art/build.py --owner-only
tools/exercise_art/.venv/Scripts/python -P tools/exercise_art/build.py --check
tools/exercise_art/.venv/Scripts/python -P tools/exercise_art/review-site/build.py --batch 11
```

5. Check that `git diff app/src/main/assets/exercise_art` shows only the new files, then commit the pin,
   the `.paths`, `art_report.json` and the regenerated Kotlin together.
6. Move the audit JSON to `reviews/batch-NNN/` and delete all draft images.

The builder thresholds, traces and redraws each PNG at the house line weight (0.8% of its long side).
Fix the source PNG in the art repo if tracing fails; do not edit the tracing or line-weight code or built paths by hand.
