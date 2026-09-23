# 05: Import from Strong and Hevy

**Goal:** a lifter switching from Strong or Hevy brings their whole history,
and arrives with their PRs, strength history and level intact. Losing years
of logged sets is what keeps people on the tracker they already use.

**Done when:**

- Settings → DATA → IMPORT FROM ANOTHER APP accepts a Strong or Hevy CSV,
  shows a review step for unmatched exercise names, and then **adds** the
  sessions without touching existing ones;
- re-importing the same file adds nothing;
- imported sessions carry strength scores computed by the app's own formula.

## Facts this rests on

- `Repository.importArchive` is a **destructive replace**. It clears presets,
  sessions, stats, titles, skills, health days and measurements first
  (`Repository.kt:1596-1606`). There is no append path, so this needs a new
  function. Never route CSV import through `importArchive`.
- Sets reference exercises by `exerciseId` (`Entities.kt:85`). Name resolution
  is case-insensitive exact match only: `exerciseDao.byName`,
  `COLLATE NOCASE` (`Daos.kt:27-28`). There is no alias table.
  `importArchive`'s local `resolveExercise` (`Repository.kt:1633-1667`)
  creates unknown names with guessed defaults and reports each guess.
- Weight is stored in kg only (`SetLogEntity.weightKg`, `Entities.kt:89`).
  There is no unit setting anywhere. There is no RPE field.
- `completeSession` (`Repository.kt:760-887`) must **not** be called for
  history, because it fires quest bonuses, now-dated title unlocks and gacha
  rolls.
- `rescoreStrengthScores(onlyUnscored = true)` (`Repository.kt:1034-1079`)
  already scores every session whose `strengthScore == 0` from its stored sets.
  It uses interpolated bodyweight and sex, then re-sums `lifetimeStrength`.
- The SAF picker already exists: `ActivityResultContracts.OpenDocument` at
  `SettingsScreen.kt:374`, reading off the main thread (`:376-383`).

## Formats

Sources: https://help.strongapp.io/article/235-export-workout-data and
https://help.hevyapp.com/hc/en-us/articles/35687878672663. Both are one row
per set, with workout fields repeated on every row.

| | Strong | Hevy |
|---|---|---|
| Sniff by header | `Date,Workout Name,...,Exercise Name,Set Order` | `title,start_time,end_time,...,exercise_title,set_index` |
| Workout key | `Date` (e.g. `2021-07-24 10:37:18`, local time) + `Workout Name` | `start_time` + `title` |
| Weight | `Weight`, **no unit column**; follows the exporter's app setting | `weight_kg` or `weight_lbs` |
| Distance / time | `Distance`, `Seconds` | `distance_km` or `distance_miles`, `duration_seconds` |
| Extras | `Notes`, `Workout Notes`, `RPE` (newer exports) | `set_type` (normal/warmup/dropset/failure), `rpe`, `superset_id` |

Strong's unit is ambiguous, so the review step asks "Were these weights in kg
or lb?" before import. It pre-selects lb when the median working weight of a
barbell lift is at least 1.6 times what kg would suggest. No guess is ever
silent. Parse both of Hevy's paired columns, preferring the kg one.

Collect three real exports as fixtures before writing the parser, rather than
trusting the help pages:

- the owner's own data, if it is anywhere;
- samples from the Strong and Hevy help centres or community threads.

`[UNCERTAIN]`: whether Strong's current export includes `RPE` and
`Workout Notes`, and the exact date format across locales.

## Design (code)

1. **`domain/CsvWorkoutReader.kt`** (pure Kotlin, next to `ExportReader.kt`):
   sniffs the header and returns
   `ParsedImport(source, workouts: List<ParsedWorkout>, units, problems)`.
   Its behaviour:
   - RFC 4180 quoting, because notes contain commas and newlines;
   - lb × 0.45359237 → kg, km × 1000 → m, miles × 1609.344 → m;
   - Strong's `Date` read in the device time zone.
   - Hand-rolled, around 100 lines. There is no CSV library in the dependency
     list, and the format is too small to justify adding one.
2. **Name mapping.** A curated `ImportAliases` map from Strong/Hevy names to
   `Seed.kt` catalogue names, for example `"Bench Press (Barbell)"` →
   `"Bench Press"` and `"Pull Up"` → `"Pull-up"`. Build it from the two apps'
   default exercise lists against the 214-movement catalogue.
   - Strip each app's equipment suffix pattern, `"(Barbell)"` and
     `"(Dumbbell)"`, before matching.
   - Then try `byName`.
   - Anything still unmatched goes to review. Never use fuzzy auto-matching:
     a wrong silent match corrupts strength history.
3. **Review screen** (`ui/settings/ImportReviewScreen.kt`, route
   `Routes.IMPORT_REVIEW`). It shows:
   - "N workouts, M sets, from DATE to DATE";
   - the unit question (Strong only);
   - one row per unmatched name, each with a catalogue picker or
     KEEP AS NEW MOVEMENT. New movements take the metric from the columns
     present (reps, seconds or distance), not `importArchive`'s blind
     `PULL/REPS` guess.
   - The mapping choices are remembered for the session only.
4. **`Repository.mergeImported(parsed, mapping): MergeResult`**, in one
   `db.withTransaction`:
   - **Idempotence key** `(startedAtMs, normalised label)`. Skip any workout
     whose key already exists. `normaliseName` = `lowercase().trim()`, the
     precedent in `TitleEngine.kt:599-600`.
   - Insert each session with `completedAtMs` set (end time, or start plus
     `Duration`) and `strengthScore = 0`.
   - Hevy `set_type = warmup` → `done = false`, so warm-ups never score.
     `dropset`/`failure` → add a modifier tag. `rpe` is appended to
     `modifiers` as `RPE 8`, so nothing is lost.
   - The private note gets the workout notes. A public note is never filled
     from an import.
   - After the transaction, run `rescoreStrengthScores(onlyUnscored = true)`.
5. **XP for imported history (decision below).** If awarded, compute per
   session with the same `Xp.award` / `ActivityScore.xp` calls
   `completeSession` uses (`Repository.kt:788-814`), sum them, and add them
   once to `totalXp`, with no quest bonus and no gacha rolls. Titles
   re-evaluate through the existing engine, dated at import time.
6. **Feed.** Imported sessions must not flood the public feed. Add
   `SessionEntity.imported: Boolean` (Room migration 27 → 28, with
   `room-migration-data-survival-test` coverage). `CloudSync.push` still backs
   them up but excludes them from feed rows.
   - Check how `public_feed` selects rows first: if it derives from
     `sessions` server-side, this needs an `imported` column in migration
     `0015` instead.

## Decision for the owner

**Does imported history earn level XP?**

- **(a, recommended)** Yes. It is the lifter's real history, and manual
  logging already trusts the lifter's numbers, so an import adds no new trust
  hole, only speed. The server's bounded, monotonic XP claim (`0011`) still
  caps absurd values, and imported sessions never reach the feed.
- (b) No XP: history, PRs and strength only. This is safer for boards, but a
  five-year lifter starts at level 1, which is exactly the switching pain this
  plan exists to remove.

## Checks

- **Unit:** each fixture parses to the expected workout and set counts; unit
  conversion; quoted multi-line notes; header sniffing rejects an unrelated
  CSV with a readable problem.
- **Instrumented** (emulator only):
  - import the fixture, and a second import adds 0 sessions;
  - pre-existing sessions are untouched;
  - every imported session has `strengthScore > 0` when it contains scored
    sets.
  - Mutation-prove the idempotence check by deleting the skip, which must fail
    the second-import assertion.
- **On the phone:** install only, never run instrumented tests there. Import a
  real export into a **fresh account on the emulator first**, then check Stats
  and Codex render the imported years.
