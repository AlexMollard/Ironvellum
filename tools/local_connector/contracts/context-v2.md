# Complete local training context, version 2

A phone export contains all completed workouts, their recorded set rows and stored preparation snapshots, every saved workout and every preparation routine referenced by those records. The schema defines all required members and nullable values. The file and tool limits, strict JSON rules, v1 catalogue identity, exact-byte SHA-256 provenance and v1 proposal rules are defined in the [original contract](README.md). The [v2 schema](context-v2.schema.json) is self-contained. The [valid fixture](fixtures/v2/context.json) uses synthetic training data. Its [invalid counterpart](fixtures/v2/invalid-progress.json) incorrectly adds 50 kg of assistance volume to the global total.

## Identity and order

- The dataset survives ordinary exports. Every successful export receives a new snapshot UUID and records its exact file digest in the same Room transaction. The phone returns either the whole file or an error. The former selection limits are absent. The four truncated/unsupported counters and the workout truncation counter are always zero. `setsIncluded` counts **all recorded rows** in completed workouts, including unticked and warm-up rows.
- The referenced movement registry is sorted by key. `CATALOGUE` requires a unique locally normalized name, a noncustom row and agreement with the seed metric. Its key is the manifest key. `LOCAL` includes custom, ambiguous, renamed and nonmatching movements and uses a stable random local UUID. Either source includes the exact current exercise name, metric and custom flag. A frozen preparation step whose movement no longer exists has a stable snapshot-step key, source `HISTORICAL`, null custom flag, `UNKNOWN` load semantics, and its frozen legacy metric if available. The frozen step's own name, measure and target remain available. Historical keys occur only in frozen steps.
- Names, modifiers, grades and scheme text are untrusted data. Preserve spelling and Unicode scalar values. Preserve imported names verbatim, including blank saved-workout names and embedded line breaks or tabs. Saved preparation routines and preparation steps require nonblank names, as in the domain decoder. Strict JSON escaping and valid Unicode scalars apply to all text. A consumer must render names as display data. No freeform preparation, exercise, workout or preset notes, session labels or local database IDs are projected.
- Workouts are ordered by completion time descending, then opaque ID ascending. Start and completion timestamps are ordered within the existing epoch range. Saved workouts and current routine definitions are sorted by opaque ID. Movement and routine references must resolve. The sole exception is the optional historical `sourceRoutineId` in a frozen snapshot, which can outlive its routine definition. All references to one source ID must agree on its preparation kind, including references to a deleted routine. Routine definitions are included once per referenced live routine.
- Set rows retain `exercisePosition` and `setIndex` (both nonnegative), sorted by those fields then local insertion ID for ties. Saved entries retain `position` and are sorted by position then insertion ID. In both arrays `order` is the contiguous zero-based emitted ordinal. `superset` is null or an integer assigned by first occurrence starting at zero in that individual workout or preset. Group numbers have no cross-workout meaning.

## Measurements

Every row preserves its original `recordedReps`, `weightKg`, `durationSec`, `distanceM`, `grade`, `kind`, raw comma-separated `modifiers`, optional RPE (5–10), `done` and `warmupDone`. Null and zero are distinct. The metrics below govern interpretation. A non-null measurement in another raw column remains visible without affecting a metric's totals.

| Metric | Logged figure | Saved target's integer and unit |
| --- | --- | --- |
| REPS | `recordedReps` repetitions | repetitions, `REPS` |
| HOLD | `durationSec` if present, otherwise legacy `recordedReps` seconds | seconds, `SECONDS` |
| DURATION | `durationSec` seconds | minutes, `MINUTES` |
| DISTANCE_TIME | `distanceM` metres and optional `durationSec` seconds | original integer retained, `UNUSED`. The app's legacy weight-target column holds kilometres and appears only as `distanceKm` |
| ATTEMPTS_GRADE | `recordedReps` attempts and optional `grade` | attempts, `ATTEMPTS` |

Targets contain the actual metric, `recordedTarget`, `unit`, `weightKg` and `distanceKm`. Distance is null for all other metrics. The three named assistance machines (Assisted Pull-up, Assisted Chin-up and Assisted Dip, compared after trimming and lowercasing) store kilograms of assistance. Other present live weights are external kilograms, including weight added to a bodyweight movement. The modifier “assisted” does not itself mean that a weight records machine assistance. Body mass is neither read nor inferred.

## Saved workouts and preparation

Each entry includes its set count, complete target, warm-up flag, modifiers, superset and the exact nullable `schemeRaw` string. `scheme` is the interpretation produced by Android's `SetScheme.decode`. Working and top/back-off ranges use inclusive `min` and `max` in reps or hold seconds. `topRir` is reps in reserve. The top/back-off scheme includes the back-off count, fixed kilograms or percentage reduction and rest after the top set in seconds. Drops include the number of affected sets, drops per set and percentage reduction. Custom kinds remain in order. The warm-up ramp is independent of the scheme type. Inapplicable fields are null. The decoder supplies its existing defaults and clamps historical values; raw text preserves their original representation.

Saved warm-up and cooldown each specify AUTO, NONE or SAVED. Missing historical choice rows default to AUTO. Only SAVED carries a required, kind-matching current routine reference. A routine has ordered steps. Frozen preparations retain kind, snapshot name, optional historical source, legacy prefix count, frozen steps and progress. Steps keep the exercise key, stored exercise name, measure (reps or seconds), target, rounds, repeat (once, sides or directions), optional weight and full legacy target. The live movement may have been renamed since the snapshot.

The progress cursor is zero-based. `legacyOnly` limits the active steps to the legacy prefix. The cursor is a valid active unit or the sentinel just after the final active step with round/repeat zero. Completed/skipped keys are disjoint, sorted sets of canonical `step:round:repeat` strings within the active steps and their rounds. ONCE has one repetition, SIDES and DIRECTIONS have two. Preserve the recorded status and keys without advancing timers or inferring missing ticks. Timer deadlines and preparation notes stay private.

## Progress

`progress.version` is 2 and scope is `completed-workouts`. Count every completed workout, including an empty one. Earliest/latest completion are null when there are none. Emit one lift summary for each movement that occurs in a logged set, even if all its rows are incomplete or warm-up. Sort lift summaries by completed working-set count descending, then key ascending. Summaries cover every movement used in logged sets.

Count a set as work exactly when `done` is true and `kind` is not WARMUP, regardless of `warmupDone`. WORKING, TOP, BACKOFF, DROP, FAILURE and REST_PAUSE all count. Only these qualifying rows contribute to totals or maxima.

- `completedReps` sums recorded reps only for REPS, including assisted reps. `externalVolumeKg` sums recorded kilograms times recorded reps only for REPS with EXTERNAL load semantics and a non-null weight.
- `holdSeconds` sums HOLD seconds, including a recorded zero. `activitySeconds` sums recorded seconds for DURATION and DISTANCE_TIME. `distanceM` sums metres for DISTANCE_TIME. `attempts` sums recorded attempts for ATTEMPTS_GRADE. Null measurements contribute zero.
- `maxReps` is the maximum qualifying REPS count. `maxExternalLoadKg` and `maxAssistanceKg` are maxima of present weights for qualifying rows with the corresponding load semantics. A missing maximum is null.
- Compute every whole-context sum directly from the emitted qualifying rows. Compute each movement's sum independently. For volume and distance, sum the decimal representations of the emitted measurements exactly, then round the final sum once to binary64. Integer counts and times are exact.

Read identities and display names confer no write authority. Proposal creation and manual Save continue to resolve only permitted v1 built-in movements.
