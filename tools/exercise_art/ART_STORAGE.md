# Exercise art storage

All 553 exercise illustrations have been approved. Each exercise has one still.

- `owner/`: the only master images, named `<exercise-slug>-1.png`. No animation frames or rejected alternatives belong here.
- `owner/INDEX.md`: an alphabetical list of exercise names linking to their approved PNGs.
- `owner/APPROVED`: the slugs permitted in a build.
- `reviews/batch-NNN/`: batch prompts, feedback and exported approval reports; JSON records only, no image copies.
- `reviews/history/`: earlier review and pose-selection metadata, retained without the old images.
- `review-decisions.json`: current user decisions bound to each approved image's SHA-256 hash.
- `generation-review.json`: generation status and batch membership. Its style reference is an approved master PNG.
- `missing.json` and `replacement-catalog.json`: exercise identities, poses and generation briefs. Source-frame IDs are historical provenance, not bundled upstream artwork.
- `frame-counts.json`: one frame per exercise.
- `app/src/main/assets/exercise_art/own_<slug>.paths`: the built single-frame vectors used by the app. Regenerate them; never edit them by hand.
- `review-site/dist/`: ignored, disposable copies for the local approval website. The builder reads approved masters directly from `owner/` and verifies their approval hashes.

There is no drafts folder or upstream animation archive now that approval is complete.
The old upstream drawings, rejected alternatives and duplicate draft images have been removed.
Generated artwork stays separate from any upstream clone; approved-only rebuilds require no clones.

If new art is requested later, the generation helpers can create `drafts/batch-NNN/`
for pending images. Review the new images locally before moving them into `owner/`,
record their hashes in `review-decisions.json`, and add their slugs to `owner/APPROVED`.
Use batches of 50 unless the owner requests another size. Never auto-approve art.
After approval, move the JSON audit records to `reviews/batch-NNN/` and remove all
draft images, including duplicate approved copies.

Build and validate with:

```powershell
tools/exercise_art/.venv/Scripts/python -P tools/exercise_art/build.py --owner-only
tools/exercise_art/.venv/Scripts/python -P tools/exercise_art/build.py --check
tools/exercise_art/.venv/Scripts/python -P tools/exercise_art/review-site/build.py --batch 11
```
