# Hevy warm-ups and supersets — design

Approved 2026-10-05.

## Goal
A Hevy CSV import keeps warm-up sets as warm-ups (not skipped sets) and keeps
superset grouping, and a movement that appears twice in one workout stays two
blocks without colliding set numbers.

## Data model (Room v34 -> v35)
- `set_logs.warmup INTEGER NOT NULL DEFAULT 0`, `set_logs.supersetGroup INTEGER` (nullable).
- Matching defaulted fields on `SetLogEntity` and `SessionSet`.
- A warm-up is stored `warmup = true, done = false`. Every consumer that counts
  ticked sets (XP, strength, PRs, volume, feed) keeps excluding it unchanged;
  the flag only changes how the set is shown.
- `supersetGroup` is written on every set of each member block; adjacent blocks
  sharing a value form one superset.
- Every field-by-field copy site carries both fields (amend, archive restore,
  history/export/live observers).

## Hevy import
- `set_type == "warmup"` -> `warmup = true`; `superset_id` -> `supersetGroup`.
- Each contiguous run of one exercise is its own block (position). `setIndex`
  counts on per movement across the trial, so the cloud key
  (movement, setIndex) never collides.

## Archive, CSV, cloud
- Archive format v6: `"warmup":true` and `"supersetGroup":n` written only when
  set; v5 and older read unchanged.
- CSV export includes warm-ups with a trailing `Set Type` column (`warmup` or
  blank); the Strong reader reads it back. Strong's own format has no
  documented warm-up marker, so none is invented.
- Cloud `session_sets` push unchanged: no Supabase change, no re-upload.

## Display
- Trial detail: a warm-up chip reads `W · WARM-UP`; a superset member block's
  header carries `SUPERSET n`, members sit closer together.
- Amend editor: warm-up sets are labelled and cannot be ticked; `planAmend`
  forces `done = false` on warm-ups. Blocks fold only when the group matches.

## Out of scope
Marking warm-ups or building supersets in a live trial, presets carrying them,
pushing either field to the cloud.

## Testing
Migration from v34; Hevy parse (superset, warm-up, repeated movement); archive
and CSV round trips; TrialDraft amend carries both fields; gate; phone check.
