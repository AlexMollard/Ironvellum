# Exercise art storage

Generated artwork belongs to Ironvellum. Never save a generated image in an
upstream repository or change an upstream drawing in place.

- `drafts/batch-NNN/`: generated PNGs and their exact prompts, source-pose IDs,
  hashes and generation status. Batches 1Ã¢â‚¬â€œ4 contain the original 108 generated
  exercises. Batches 5Ã¢â‚¬â€œ13 replace the 445 selected upstream drawings.
- `owner/`: approved generated PNGs. A slug must be listed in `owner/APPROVED`
  before the build consumes it. Pending replacements remain in drafts.
- `replacement-catalog.json`: replacement names, aliases, selected source-frame
  indices and prompts. This is a queue, not an approval record.
- `review-decisions.json`: hash-bound user review decisions. Every new image
  requires approval, including replacements of previously approved source poses.
- `animation-sources/`: archived upstream vector animations and reports for
  traceability and pose selection. These are source references, not generated art.
- `review-site/dist/`: disposable local review copies; not the master images.
- `app/src/main/assets/exercise_art/`: built vector outputs; never edit by hand.

Upstream clones are inputs only. Keep them outside these generated-art folders.
Keep upstream attribution while any derived or original upstream artwork remains.
Do not delete a clone or its source archive as part of generating a replacement.

Generate one still per exercise. Present at most 50 new stills on the local site,
then wait for the owner's approvals and redo notes before starting another batch.
The final replacement batch contains 45 stills. Never auto-approve replacements.

`prepare_replacements.py` prepares the queue without changing shipped artwork.
`save_replacement.py <batch> <filename> <generated-path>` copies a built-in
imagegen result into its draft folder and records its hash. Preserve the original
tool output as well. Resume existing draft batches rather than generating them
again.

After visually checking every image, run
`finalize_replacement_batch.py <batch>` with the exercise-art Python environment.
This verifies PNG dimensions, transparency and hashes and marks the batch as
awaiting review. It never approves images or changes shipped artwork. Then run
`review-site/build.py --batch <batch>` to refresh the local approval site.
