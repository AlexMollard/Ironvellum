# Muscle Fatigue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Estimate each muscle's current fatigue from logged hard sets, show it on a full-body heatmap with plain-words causes, soreness notes and "light by <day>", show each exercise's fatigue footprint, mention fatigue in the plan builder without changing the plan, and give coverage and fatigue one shared home (Train > Muscles) with one shared Coverage map.

**Architecture:** A pure Kotlin model in `domain/` (like `HardSets`) turns hard sets into per-muscle decaying contributions; every number the UI shows is read from one `FatigueReport`. A `MuscleFatigueViewModel` feeds it history plus the live workout; Train gets Plan | Muscles tabs, and Muscles hosts a Fatigue | Coverage switch over two maps of the same figure. Tunables live in one object (`FatigueTuning`); fatigue traits are a fatigue-only table that never touches `ExerciseProfile.stretchBias`.

**Tech Stack:** Kotlin, Jetpack Compose, Room (read only), JUnit4 unit tests in `app/src/test/kotlin/com/ironvellum/app`, Compose instrumented tests in `app/src/androidTest`, local gate `python3 tools/gate.py`.

**Spec:** `D:\Monarch\docs\superpowers\specs\2026-10-23-muscle-fatigue-design.md` (including "Calls made after the builder review", which are binding).

Source root below: `S = app/src/main/kotlin/com/ironvellum/app`; unit tests `T = app/src/test/kotlin/com/ironvellum/app`; instrumented `A = app/src/androidTest/kotlin/com/ironvellum/app`.

## Global Constraints

- All screen text is in `app/src/main/res/values/strings.xml`: plain `key`, themed `key__t` only for a sentence naming a glossary concept (workout/trial, routine/rite, plan/cycle); no concept-word concatenation, no `{...}` placeholders; new keys prefixed `fatigue_`. After any strings change run `StringsXmlTest`, `GlossaryTest`, `WordsFormsTest`, `HardcodedUiLiteralTest` (lower `app/src/test/resources/hardcoded-ui-literals.baseline` if a count drops; never raise it).
- Emulator only through `python3 tools/device.py lock -- <cmd>` on `emulator-5554`; never the phone `R5GL14GXV3J`; instrumented Gradle always with `ANDROID_SERIAL=emulator-5554`.
- One Gradle build at a time (no parallel agents running Gradle or the gate).
- `python3 tools/gate.py` must be green and finish under 5 minutes before any push.
- Variation-aware throughout: novelty, cause rows and lift links key on `Variation.liftKey(name, modifiers)`; combined modifiers are one variation.
- Only hard sets count: exactly `HardSets.isHard`; effort table has no 4+ row; sets with `done=false` or warm-up never count.
- Constants in one place: `FatigueTuning` (`domain/FatigueTuning.kt`); no magic numbers elsewhere.
- Nothing uploaded: no new network, sync, Room table, DAO or Repository write; fatigue is computed on the device from local history.
- No decimals on fatigue levels; shares rounded to the nearest 5%; every estimate labelled as an estimate.
- Every UI piece is screenshot-verified on the emulator with fictional seeded history (see Task 8 seeding); screenshots go to `D:\Monarch\.tmp\audit\fatigue\` (device.py writes `.tmp\shots\<name>.png`; copy from there).
- Commit style: imperative capitalised subject, no prefixes, no attribution, flat bullet body only when several changes are grouped; push to `main` when the gate is green (no force-push, no history rewrite, no tags).
- File encoding: UTF-8 without BOM, CRLF; check the first three bytes after writing.

## Review Focus

Input classes the spec implies but tests tend to miss; each has a test in its owning task.

1. **No history / brand-new install** (empty lists, `firstWorkoutMs == null`): all muscles FRESH, no causes, no light-by line, no crash, empty-state copy. Tests: Task 3 `emptyHistoryIsAllFresh`, Task 5 `noHistoryGivesEmptyReportAndNewLifter`, Task 8 empty-state screenshot.
2. **A live workout in progress**: done hard sets count at "now", undone/warm-up sets do not, the first workout may be the live one, and the map moves as sets are ticked. Tests: Task 5 `liveWorkoutCountsAtNow`, `liveUndoneAndWarmupSetsAreIgnored`, `liveOnlyLifterIsNew`.
3. **Sets with several modifiers**, incl. incline moving chest credit plus deficit stretch and `liftKey` ordering. Tests: Task 2 `inclineDeficitBenchMovesChestAndStretchesMainMuscles`, `modifierOrderDoesNotSplitNovelty`.
4. **Time zone / day boundary** for "light by <day>" and the 08:00 plan note: crossing just before/after local midnight, DST change days, a zone other than the device default. Tests: Task 4 `lightByUsesLocalCalendarDay*`, Task 10 `eightAmOnDstChangeDay`.
5. **Imported / custom data and size**: custom exercises (own shares, stretch 1.0, systemic 1.0), imported sets with no RPE, names with no profile, and a three-year history. Tests: Task 2 `customExerciseUsesOwnSharesAndNoTraits`, `unratedImportedSetCountsAtPointEight`, `unknownExerciseContributesNothing`; Task 5 `threeYearHistoryOnlyReadsTheWindowAndIsFast`.

---

### Task 1: Fatigue constants and traits table

**Files:**
- Create: `S/domain/FatigueTuning.kt`, `S/domain/FatigueTraits.kt`
- Test: `T/domain/FatigueTraitsTest.kt`

**Interfaces:**
- Consumes: `Muscle` (`domain/Program.kt:144`), `MuscleMap.keys`, `MuscleMap.profile(name)`, `CustomExercises.isCustom(name)`.
- Produces:
```kotlin
object FatigueTuning {           // every spec constant, one place
    const val REFERENCE_SETS = 6.0                       // residual of 6 hard-set equivalents = 100%
    const val FRESH_BELOW = 0.15; const val LIGHT_BELOW = 0.35
    const val MODERATE_BELOW = 0.60; const val HIGH_BELOW = 0.85   // else VERY_HIGH
    const val EFFORT_FAILURE = 1.15; const val EFFORT_RIR0 = 1.0; const val EFFORT_RIR1 = 0.9
    const val EFFORT_RIR2 = 0.8; const val EFFORT_RIR3 = 0.65; const val EFFORT_UNRATED = 0.8
    const val STRETCH = 1.3; const val SYSTEMIC = 1.2; const val LOWER_BACK_BONUS_SHARE = 0.25
    const val NOVELTY_NEW = 1.4; const val NOVELTY_SECOND = 1.2; const val NOVELTY_WINDOW_DAYS = 21L
    const val WINDOW_DAYS = 7L
    const val HALF_LIFE_FAST_H = 18.0; const val HALF_LIFE_MEDIUM_H = 24.0; const val HALF_LIFE_SLOW_H = 36.0
    const val BIG_SESSION_DOSE = 10.0; const val BIG_SESSION_STRETCH = 1.25
    const val NEW_LIFTER_WEEKS = 8L; const val NEW_LIFTER_STRETCH = 1.2
    const val SORENESS_MIN_DOSE = 1.5; const val SORENESS_FROM_H = 12.0; const val SORENESS_TO_H = 72.0
    const val MAIN_SHARE = 0.7                            // MuscleMap doc: 0.7+ is main work; deficit stretches these
    const val FOOTPRINT_SETS = 3; const val FOOTPRINT_RIR = 1
    const val BAND_STRONG = 2.0; const val BAND_MEDIUM = 1.0; const val BAND_OMIT_BELOW = 0.3
}
enum class RecoveryClass(val halfLifeHours: Double) { FAST(18.0), MEDIUM(24.0), SLOW(36.0) }
fun Muscle.recoveryClass(): RecoveryClass          // exhaustive `when`, no else
object FatigueTraits {
    /** Muscles this movement loads long: table entry for catalogue names, plus main muscles when a "deficit" modifier is present. Empty for custom exercises. */
    fun stretchedMuscles(exerciseName: String, modifiers: String, profile: ExerciseProfile): Set<Muscle>
    fun isHeavyWholeBody(exerciseName: String): Boolean   // false for custom exercises
    val STRETCH_TABLE: Map<String, Set<Muscle>>; val HEAVY_WHOLE_BODY: Set<String>
}
```
Pinned table: stretch (lowercase names) `romanian deadlift`, `single-leg romanian deadlift`, `good morning` -> HAMSTRINGS; `bulgarian split squat` -> QUADS, GLUTES; `hack squat` -> QUADS; `nordic curl`, `lying leg curl`, `seated leg curl` -> HAMSTRINGS; `dumbbell pullover` -> LATS; `cable fly` -> MID_CHEST; `overhead cable extension` -> TRICEPS; `preacher curl` -> BICEPS; `standing calf raise` -> CALVES. Heavy whole-body: `back squat`, `front squat`, `pause squat`, `heavy squat`, `deadlift`, `sumo deadlift`, `heavy deadlift`, `volume deadlift`, `romanian deadlift`, `good morning`. (Builder may extend; the keys test below guards typos.)

- [ ] **Step 1: Write the failing test** `FatigueTraitsTest`:
  - `everyMuscleHasARecoveryClass`: `Muscle.entries.forEach { it.recoveryClass() }`; counts FAST 9, MEDIUM 11, SLOW 7; spot checks `CALVES=FAST(18.0)`, `LATS=MEDIUM(24.0)`, `QUADS=SLOW(36.0)`, `LOWER_BACK=SLOW`, `NECK=FAST`, `SERRATUS=MEDIUM`.
  - `traitKeysAreCatalogueNames`: `STRETCH_TABLE.keys + HEAVY_WHOLE_BODY` all in `MuscleMap.keys`.
  - `rdlIsStretchHeavyOnHamstringsOnly`: `stretchedMuscles("Romanian Deadlift","",profile) == setOf(HAMSTRINGS)`.
  - `deficitStretchesMainMusclesOnly`: push-up with `"deficit"`: contains `MID_CHEST`, not `TRICEPS` (share 0.6 < 0.7).
  - `plainBenchHasNoStretchTrait`: `stretchedMuscles("Bench Press","",p).isEmpty()`; `isHeavyWholeBody("Back Squat")` true, `("Bench Press")` false.
  - `stretchBiasFlagIsNotTheSource`: `MuscleMap.profile("Cable Curl")!!.stretchBias` is true and `stretchedMuscles("Cable Curl", ...)` is empty (traits are their own data).
  - `customExerciseHasNoTraits`: register a custom exercise `"My Squat"` the way `CustomExercisesTest` does (read it first) and assert empty/false.
- [ ] **Step 2: Run, expect FAIL (compile error):** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.FatigueTraitsTest"`
- [ ] **Step 3: Implement** the two files per the signatures above; `recoveryClass()` maps the spec's table exactly; deficit detection reads `Variation.key(modifiers)` tokens.
- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit:** `git add app/src/main/kotlin/com/ironvellum/app/domain/FatigueTuning.kt app/src/main/kotlin/com/ironvellum/app/domain/FatigueTraits.kt app/src/test/kotlin/com/ironvellum/app/domain/FatigueTraitsTest.kt` -> `Add fatigue constants, recovery classes and traits table`

---

### Task 2: Dose per set

**Files:**
- Create: `S/domain/FatigueDose.kt`
- Test: `T/domain/FatigueDoseTest.kt`

**Interfaces:**
- Consumes: `HardSets.isHard(kind, rpe, done, reps, weightKg, bodyweight)` and `LoggedSet` (`domain/ProgramCoverage.kt:36,59`), `MuscleMap.profile(name, modifiers)`, `Effort.rir(rpe)` (`domain/Effort.kt`), `FatigueTraits`, `SetKind.FAILURE/REST_PAUSE`.
- Produces:
```kotlin
data class MuscleDose(val muscle: Muscle, val dose: Double, val stretched: Boolean)
object FatigueDose {
    fun effort(set: LoggedSet): Double       // FAILURE/REST_PAUSE 1.15; rpe -> rir 0/1/2/3 = 1.0/0.9/0.8/0.65; unrated 0.8
    fun novelty(priorExposures: Int): Double // 0 -> 1.4, 1 -> 1.2, 2+ -> 1.0
    fun dose(share: Double, effort: Double, stretch: Double, systemic: Double, novelty: Double): Double  // plain product
    /** Empty when the set is not hard or its name has no profile. */
    fun perSet(set: LoggedSet, novelty: Double): List<MuscleDose>
}
```
Rules pinned: only shares > 0; `stretch` = 1.3 for muscles in `FatigueTraits.stretchedMuscles`; `systemic` = 1.2 on every muscle when `isHeavyWholeBody`; the lower-back bonus is a synthetic share 0.25 applied only when the profile's lower-back share is 0/absent and the lift is heavy whole-body, multiplied by effort and novelty only (no stretch, no systemic). Holds use `LoggedSet.reps` as already derived by `HardSets.logged`.

- [ ] **Step 1: Write failing tests** `FatigueDoseTest` (helper `set(name, rpe = 9, kind = WORKING, mods = "", reps = 5, weight = 100.0)` with `done = true, atMs = 0`):
  - `effortTable`: rpe 10/9/8/7 -> 1.0/0.9/0.8/0.65; `FAILURE` and `REST_PAUSE` -> 1.15 even with rpe 6; unrated -> 0.8; `DROP` unrated -> 0.8, drop rpe 8 -> 0.8.
  - `noveltyTable`: 0 -> 1.4, 1 -> 1.2, 2 -> 1.0, 5 -> 1.0.
  - `onlyHardSetsProduceDose`: warm-up, `done=false`, rpe 6 working set -> `perSet` empty (same verdicts as `HardSets.isHard`).
  - `benchSetAtRir1`: `perSet(set("Bench Press", rpe=9), 1.0)` -> MID_CHEST 0.9, FRONT_DELTS 0.63, TRICEPS 0.54, SIDE_DELTS 0.27 (shares from the profile times 0.9; assert with `1e-9`).
  - `rdlCombinesStretchAndSystemic`: RDL rpe 9 novelty 1.0 -> HAMSTRINGS `1.0*0.9*1.3*1.2 = 1.404` (stretched=true), GLUTES `1.08`, LOWER_BACK `0.54` (already credited at 0.5, so no bonus), no extra lower-back entry.
  - `squatGetsLowerBackBonus`: Back Squat rpe 9 novelty 1.0 -> QUADS `1.0*0.9*1.2 = 1.08`; LOWER_BACK `0.25*0.9 = 0.225`; HAMSTRINGS absent (share 0).
  - `noveltyScalesEverything`: Bench rpe 9 with novelty 1.4 -> MID_CHEST `1.26`.
  - `inclineDeficitBenchMovesChestAndStretchesMainMuscles` (Review Focus 3): `Bench Press` with mods `"incline, deficit"` -> UPPER_CHEST share 1.0 (incline moves credit) and stretched, MID_CHEST share 0.5 not main so `stretched=false`; FRONT_DELTS 0.7 -> stretched true.
  - `modifierOrderDoesNotSplitNovelty`: `Variation.liftKey("Bench Press","deficit,incline") == Variation.liftKey("Bench Press","incline,deficit")` and `"tempo"` does not change the key.
  - `customExerciseUsesOwnSharesAndNoTraits` (Review Focus 5): custom exercise with MAIN/ASSIST shares -> dose = share*effort, `stretched=false`, no systemic, no lower-back bonus.
  - `unratedImportedSetCountsAtPointEight`: `rpe=null, weight=60.0, reps=8` -> hard via the existing unrated rule, effort 0.8; `reps=20` unrated -> empty.
  - `unknownExerciseContributesNothing`: `perSet(set("Zzz Not A Lift"), 1.0)` is empty.
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.FatigueDoseTest"`
- [ ] **Step 3: Implement** `FatigueDose.kt` as above (reuse `HardSets.isHard`; do not re-derive hardness).
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit:** `git add app/src/main/kotlin/com/ironvellum/app/domain/FatigueDose.kt app/src/test/kotlin/com/ironvellum/app/domain/FatigueDoseTest.kt` -> `Add per-set fatigue dose from effort, stretch, systemic load and novelty`

---

### Task 3: Residual, decay and recovery modifiers

**Files:**
- Create: `S/domain/MuscleFatigue.kt`
- Test: `T/domain/MuscleFatigueTest.kt`

**Interfaces:**
- Consumes: `FatigueDose`, `FatigueTuning`, `recoveryClass()`, `Variation.liftKey`, `LoggedSet`.
- Produces:
```kotlin
data class FatigueSession(val sessionId: Long, val atMs: Long, val sets: List<LoggedSet>)  // atMs = completedAtMs ?: startedAtMs; live session = now
data class FatigueContribution(
    val muscle: Muscle, val sessionId: Long, val sessionAtMs: Long,
    val exerciseName: String, val modifiers: String, val liftKey: String,
    val dose: Double,              // summed dose of this (session, lift, muscle)
    val qualifyingDose: Double,    // part of dose from sets with stretch > 1 or novelty > 1
    val stretched: Boolean, val novel: Boolean,
    val halfLifeHours: Double,     // class half-life with big-session and new-lifter stretches applied
)
class FatigueReport(val nowMs: Long, val newLifter: Boolean, val contributions: Map<Muscle, List<FatigueContribution>>) {
    fun residual(muscle: Muscle, atMs: Long = nowMs): Double   // sum dose * 0.5^((atMs - sessionAtMs)/3.6e6/halfLife); hard-set equivalents
}
object FatigueModel {
    /** [sessions] must cover the 28 days before [nowMs]; sets older than 7 days feed novelty only. */
    fun estimate(sessions: List<FatigueSession>, nowMs: Long, firstWorkoutMs: Long?): FatigueReport
    fun priorExposures(sessions: List<FatigueSession>, liftKey: String, beforeMs: Long): Int   // distinct earlier sessions in the 21 days before beforeMs with a done working set of that lift
}
```
Pinned: contributions are kept for sessions in `(now-7d, now]`; futures ignored; novelty is per session per lift (all sets of that lift in a session share one factor) and counts earlier sessions where the lift had any done non-warm-up set (a light set is still "done this lift"); a session whose dose to one muscle is `> 10.0` stretches that muscle's contributions from that session by 1.25; `newLifter = firstWorkoutMs == null || firstWorkoutMs > now - 8 weeks` (null counts as new) and multiplies every half-life by 1.2 (the two stretches multiply); muscles with no contributions get an empty list.

- [ ] **Step 1: Write failing tests** `MuscleFatigueTest` (helpers: `H = 3_600_000L`, `NOW = 1_000 * 24 * H`, `session(id, hoursAgo, vararg sets)`, `old = firstWorkoutMs = NOW - 400 * 24 * H` so the lifter is not new):
  - `emptyHistoryIsAllFresh` (Review Focus 1): `estimate(emptyList(), NOW, null)`: every `residual == 0.0`, `contributions[m]` empty for all 27 muscles, `newLifter == true`.
  - `decayHalvesAtTheHalfLife`: one RPE-9 Bench set (MID_CHEST dose 0.9*1.4 novelty = 1.26) at 0 h -> residual 1.26; `FRONT_DELTS` (MEDIUM) at +24 h exactly half; `MID_CHEST` (SLOW) at +36 h exactly half (use `residual(m, NOW + 36*H)`).
  - `classesDecayAtTheirOwnRates`: a calf raise set and a squat set at the same time: after 18 h calves are at 50%, quads at `0.5^(18/36)`.
  - `sevenDayWindowIsHalfOpen`: a set 7 days minus 1 minute ago contributes; exactly 7 days or older does not; a set in the future is ignored.
  - `noveltyFirstSecondThird`: Bench sessions 5, 3 and 1 days ago with no earlier history get factors 1.4, 1.2, 1.0 (assert `FatigueContribution.dose / baseDose` for MID_CHEST, where `baseDose` is the same set at novelty 1.0).
  - `noveltyWindowIsTwentyOneDays`: an earlier exposure 22 days before -> still 1.4; 20 days before -> 1.2.
  - `noveltyIsVariationAware`: earlier plain Bench within 21 days does not make `Bench Press` with `"incline"` familiar (1.4); earlier `"deficit,incline"` does make `"incline,deficit"` familiar.
  - `bigSessionStretchesHalfLifeByQuarter`: 11 Bench sets in one session (MID_CHEST dose > 10 at novelty 1.0 after two prior exposures) -> that muscle's `halfLifeHours == 36.0 * 1.25`; 10.0 exactly is not big (strictly above).
  - `newLifterStretchesHalfLifeByTwentyPercent`: `firstWorkoutMs = NOW - 7*7*24*H` -> `newLifter` true and calves `halfLifeHours == 18.0 * 1.2`; at exactly 8 weeks -> false; combined with a big session -> `36 * 1.25 * 1.2`.
  - `importedOnlyLifterIsNew` (spec call 7): `firstWorkoutMs` given by an imported session 3 weeks ago counts.
  - `contributionsKeepTheirSource`: two RDL sets in one session -> one `FatigueContribution` per muscle with `liftKey == "romanian deadlift"`, `sessionId` kept, `dose` summed.
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.MuscleFatigueTest"`
- [ ] **Step 3: Implement** `MuscleFatigue.kt` (group sets by `(sessionId, liftKey)`, compute novelty once per group via `priorExposures`, call `FatigueDose.perSet`, aggregate per muscle, then apply half-life modifiers).
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit:** `git add app/src/main/kotlin/com/ironvellum/app/domain/MuscleFatigue.kt app/src/test/kotlin/com/ironvellum/app/domain/MuscleFatigueTest.kt` -> `Add muscle fatigue residual model with recovery classes and decay`

---

### Task 4: Levels, shares, light-by day, soreness rule

**Files:**
- Create: `S/domain/FatigueReading.kt`
- Test: `T/domain/FatigueReadingTest.kt`

**Interfaces:**
- Consumes: `FatigueReport`/`FatigueContribution` (Task 3), `FatigueTuning`, `java.time.ZoneId`.
- Produces:
```kotlin
enum class FatigueLevel { FRESH, LIGHT, MODERATE, HIGH, VERY_HIGH;
    companion object { fun of(residual: Double): FatigueLevel } }   // fraction = residual / 6.0; boundaries 0.15/0.35/0.60/0.85 are inclusive at the lower edge, 1e-9 tolerance
sealed interface LightBy { object None; object Today; object Tomorrow; data class Weekday(val day: java.time.DayOfWeek); object OverAWeek }
data class CauseShare(val exerciseName: String, val modifiers: String, val liftKey: String, val sessionId: Long, val sessionAtMs: Long, val percent: Int)
enum class SorenessReason { NEW_AND_STRETCH, NEW, STRETCH }
data class SorenessNote(val reason: SorenessReason, val exerciseName: String, val modifiers: String, val muscle: Muscle)
data class FatigueReading(
    val muscle: Muscle, val residual: Double, val level: FatigueLevel,
    val causes: List<CauseShare>,        // top 3 by current residual, percents rounded to nearest 5
    val otherPercent: Int,               // 100 - sum of shown percents, clamped >= 0; 0 when 3 or fewer causes
    val lightBy: LightBy, val freshBy: LightBy, val soreness: SorenessNote?,
)
fun FatigueReport.reading(muscle: Muscle, zone: ZoneId): FatigueReading
fun FatigueReport.levelAt(muscle: Muscle, atMs: Long): FatigueLevel
fun FatigueReport.mostWorked(zone: ZoneId, limit: Int = 4): List<FatigueReading>   // level >= LIGHT, residual descending, ties by muscle ordinal
internal fun FatigueReport.firstBelowMs(muscle: Muscle, fraction: Double): Long?   // null when already below or still above after 7 days; bisection on [now, now + 7d], 48 iterations
```
Pinned: light-by = first instant residual `< 0.35*6`; fresh-by = `< 0.15*6`. Day mapping on `zone` local dates: already below -> `None`; same date as now -> `Today`; next date -> `Tomorrow`; 2..6 days ahead -> `Weekday`; 7+ days or still above at `now+7d` -> `OverAWeek`. Soreness: sessions aged 12.0..72.0 h inclusive; sum `qualifyingDose` of a muscle over those sessions; `>= 1.5` -> note naming the largest qualifying cause (reason from its `novel`/`stretched` flags); a live (age 0) session never qualifies.

- [ ] **Step 1: Write failing tests** `FatigueReadingTest` (build reports via `FatigueModel.estimate` with `ZoneId.of("UTC")` unless stated):
  - `levelBoundaries`: residual 0.89 -> FRESH, 0.90 -> LIGHT, 2.09 -> LIGHT, 2.10 -> MODERATE, 3.6 -> HIGH, 5.1 -> VERY_HIGH, 9.0 -> VERY_HIGH, 0.0 -> FRESH.
  - `sharesRoundToFiveTopThreeAndOther`: four causes with residual weights 62/25/8/5 -> percents `60, 25, 10`, `otherPercent == 5`; two causes 70/30 -> `70, 30`, other 0; one cause 100 -> `[100]`.
  - `sharesUseCurrentResidualNotDose`: an older bigger session decayed below a newer smaller one ranks second.
  - `noCausesWhenFresh`: empty report -> `causes` empty, `lightBy == None`, `freshBy == None`.
  - `lightByIsTheSolvedCrossing`: single contribution dose 6.0 on SLOW quads at now: crossing at `36 * log2(6.0/2.1)` hours (about 54.5 h); assert `firstBelowMs` within 1 minute of that; two contributions (sum of decays) cross later than either alone.
  - `lightByUsesLocalCalendarDayToday/Tomorrow/Weekday/OverAWeek`: build a report whose crossing is 3 h / 20 h / 60 h / 9 d ahead from 10:00 local -> `Today`, `Tomorrow`, `Weekday(<that weekday>)`, `OverAWeek`.
  - `lightByUsesLocalCalendarDayAtMidnight` (Review Focus 4): now 22:00 local, crossing 1 h 59 m later (23:59) -> `Today`; 2 h 01 m later (00:01) -> `Tomorrow`.
  - `lightByInADifferentZoneChangesTheDay`: same instants read in `Pacific/Auckland` vs `America/Los_Angeles` produce different `LightBy`.
  - `lightByAcrossDstChange`: zone `America/New_York`, now = 2026-03-07 20:00 local, crossing 12 h later -> `Tomorrow` (day length 23 h must not make it `Weekday`).
  - `alreadyLightGivesNoLightByLine`: residual below 2.1 -> `lightBy == None` while `freshBy != None` when residual >= 0.9.
  - `sorenessRule`: 1.5 qualifying dose at 13 h -> note with reason; at 11.9 h -> null; at 12.0 h -> note; at 72.0 h -> note; at 72.1 h -> null; 1.4 dose -> null; non-qualifying (familiar, non-stretch) sets of 5.0 dose -> null; a 6 h-old session plus a 30 h-old one counts only the 30 h one.
  - `sorenessNamesTheCause`: new + stretch RDL -> `reason == NEW_AND_STRETCH`, `muscle == HAMSTRINGS`, `exerciseName == "Romanian Deadlift"`.
  - `mostWorkedOrdersAndLimits`: returns at most 4, only LIGHT or above, descending residual.
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.FatigueReadingTest"`
- [ ] **Step 3: Implement** `FatigueReading.kt`. Algorithm not fixed by the tests: `firstBelowMs` returns null if `residual(now) < threshold`; if `residual(now + 7d) >= threshold` return a sentinel mapped to `OverAWeek`; otherwise bisect (residual is monotonically decreasing in time because every term decays). Day mapping converts both `nowMs` and the crossing to `LocalDate` in `zone`, diff = `ChronoUnit.DAYS.between`.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit:** `git add app/src/main/kotlin/com/ironvellum/app/domain/FatigueReading.kt app/src/test/kotlin/com/ironvellum/app/domain/FatigueReadingTest.kt` -> `Add fatigue levels, cause shares, light-by day and soreness rule`

---

### Task 5: Input assembly and view model (history plus live workout)

**Files:**
- Create: `S/domain/FatigueInput.kt`, `S/ui/program/MuscleFatigueViewModel.kt`
- Test: `T/domain/FatigueInputTest.kt`, `T/ui/program/MuscleFatigueViewModelTest.kt` (only if a fake `Repository` pattern exists in `T/ui`; otherwise the pure `FatigueInput` tests carry it)
- Read first: `S/data/Repository.kt:408` (`observeExercises`), `:1494` (`observeSessionSets`), `:2220` (`observeLiveSession`), `:2238` (`observeHistory`); `S/domain/PeriodStats.kt:125-141` (the `HardSets.logged(at(session), ...)` pattern to copy).

**Interfaces:**
- Consumes: `HardSets.logged(atMs, sets, exercises)`, `Repository.observeHistory()`, `observeLiveSession()`, `observeSessionSets(id)`, `observeExercises()`.
- Produces:
```kotlin
object FatigueInput {
    /** Sessions of the last 28 days as [FatigueSession]s; the live session (if any) at nowMs; only done sets (HardSets.logged keeps them, isHard filters later). */
    fun sessions(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        live: Pair<WorkoutSession, List<SessionSet>>?,
        exercises: Map<Long, Exercise>, nowMs: Long,
    ): List<FatigueSession>
    /** Earliest start of any completed session (imported or restored included) or the live session; null when none. */
    fun firstWorkoutMs(history: List<Pair<WorkoutSession, List<SessionSet>>>, live: WorkoutSession?): Long?
}
data class FatigueUi(val report: FatigueReport? = null, val hasHistory: Boolean = false, val loaded: Boolean = false)
class MuscleFatigueViewModel(repo: Repository, nowMs: () -> Long = System::currentTimeMillis, tickMs: Long = 60_000L) : ViewModel() {
    val ui: StateFlow<FatigueUi>      // combine(history, exercises, live+liveSets, ticker) -> flowOn(Dispatchers.Default)
}
```
No Repository change, no new DAO (`firstWorkoutMs` is a min over the history already observed).

- [ ] **Step 1: Write failing tests** `FatigueInputTest`:
  - `noHistoryGivesEmptyReportAndNewLifter` (Review Focus 1): `sessions(emptyList(), null, emptyMap(), NOW)` is empty; `firstWorkoutMs(emptyList(), null) == null`; `estimate(...)` all fresh.
  - `liveWorkoutCountsAtNow` (Review Focus 2): live session started 40 min ago with two done RPE-9 bench sets -> `FatigueSession.atMs == NOW`, report `residual(MID_CHEST) == 1.8 * 1.4` at now and decays from now, not from `startedAtMs`.
  - `liveUndoneAndWarmupSetsAreIgnored`: one done, one undone, one warm-up -> only one set contributes.
  - `liveOnlyLifterIsNew`: no history, live session -> `firstWorkoutMs == live.startedAtMs`, `newLifter` true.
  - `historyUsesCompletedAtMsElseStartedAt`: completed session 10 h ago started 11 h ago -> `atMs` is the completion time; a completed row with `completedAtMs = null` falls back to `startedAtMs`.
  - `activitiesAreLeftOut`: a non-strength exercise's sets are dropped (the `HardSets.logged` filter).
  - `threeYearHistoryOnlyReadsTheWindowAndIsFast` (Review Focus 5): 600 sessions over 3 years x 20 sets; `sessions(...)` returns only those within 28 days (assert size) and `estimate` over the full output runs under 500 ms on the JVM (generous; guards against O(history) work).
  - `firstWorkoutIsTheEarliestOfAnyOrigin`: includes `imported = true` sessions; order of the list does not matter.
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.FatigueInputTest"`
- [ ] **Step 3: Implement** `FatigueInput` (filter `at(session) >= nowMs - 28 days` before mapping sets) and the view model: `combine(repo.observeHistory(), repo.observeExercises().map { it.associateBy(Exercise::id) }, liveFlow, ticker)` with `liveFlow = repo.observeLiveSession().flatMapLatest { s -> s?.let { repo.observeSessionSets(it.id).map { sets -> it to sets } } ?: flowOf(null) }`; `ticker = flow { while (true) { emit(nowMs()); delay(tickMs) } }`; `.flowOn(Dispatchers.Default).stateIn(viewModelScope, WhileSubscribed(5_000), FatigueUi())`. Factory pattern as `MuscleCoverageViewModel` (`S/ui/program/MuscleCoverageScreen.kt:160`).
- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit:** `git add app/src/main/kotlin/com/ironvellum/app/domain/FatigueInput.kt app/src/main/kotlin/com/ironvellum/app/ui/program/MuscleFatigueViewModel.kt app/src/test/kotlin/com/ironvellum/app/domain/FatigueInputTest.kt` -> `Feed fatigue from history and the live workout`

---

### Task 6: One shared Coverage map component

**Finding that shapes this task:** setup (`OnboardingScreen.kt:642`), the plan builder (`ProgramBuilderScreen.kt:746`, `ForgeCycle.kt:130`) and Muscles already render `CoveragePreviewScreen`/`CoveragePage` -> `CoverageFigure` + `CoverageLegend`. The work is to name the shared unit, strip screen chrome from the Muscles use, and prove the three entry points agree.

**Files:**
- Create: `S/ui/program/CoverageMap.kt`
- Modify: `S/ui/program/MuscleCoverageScreen.kt` (`CoveragePage` at `:381-470` uses `CoverageMap`; `MuscleCoverageScreen` at `:215-289` becomes `MuscleCoverageContent` without `PushedHeader`/back; `CoveragePreviewScreen` at `:291` unchanged in behaviour), `S/ui/program/MuscleHeatMap.kt` (`FigureBlock` `:210` becomes `internal` for Task 8; `CoverageFigure` `:83` moves under `CoverageMap`)
- Test: `T/ui/program/CoverageMapStateTest.kt`, `A/ui/program/CoverageMapParityTest.kt`

**Interfaces:**
- Consumes: `CoverageFigure`, `CoverageLegend`, `coverageTally`, `rememberMuscleSelection`, `unattributedSets`.
- Produces:
```kotlin
@Composable internal fun CoverageMap(
    loads: Map<Muscle, MuscleLoad>, dismissed: Set<Muscle>, selection: MutableState<Muscle?>,
    unattributed: Int, modifier: Modifier = Modifier, figureHeight: Dp = 260.dp, interactive: Boolean = true,
)   // figure + legend + the unattributed-sets line; the ONLY coverage figure composable callers use
@Composable internal fun MuscleCoverageContent(
    onGenerateSession: () -> Unit, onOpenRite: (Long) -> Unit, onOpenRites: () -> Unit, onOptimise: () -> Unit,
    viewModel: MuscleCoverageViewModel = ...,
)   // today's MuscleCoverageScreen body: pager Planned | Last 7 days, pages, dock; no header
```
Previews keep today's interactivity (spec call 4); only Muscles is guaranteed interactive.

- [ ] **Step 1: Write failing tests.** Unit `CoverageMapStateTest`: `previewAndMusclesShareOneStateRule`: for a fixed `List<PlannedPreset>`, `coverageTally(loadsOf(presets))` equals what `previewCoverage(presets)` -> `CoveragePage` computes (assert both go through `loadsOf` + `coverageTally`, by extracting the pure `coverageMapModel(presets, pooled, dismissed)` helper and testing its `states` for covered/below/not-trained/heavy). Instrumented `CoverageMapParityTest` (follow `AccessibilityChecksTest` setup): render `CoverageMap` for the same loads from the three call sites' entry composables and assert identical content descriptions on the figure node, the same legend labels (`coverage_state_*`), and that tapping a muscle lights the same node in `MuscleCoverageContent`.
- [ ] **Step 2: Run, expect FAIL.** Unit: `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.ui.program.CoverageMapStateTest"`. Instrumented: `python3 tools/device.py lock --label "fatigue t6" -- bash -c 'ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ironvellum.app.ui.program.CoverageMapParityTest'`
- [ ] **Step 3: Implement.** Extract, do not rewrite: `CoverageMap` = existing `CoverageFigure(...)` call at `MuscleCoverageScreen.kt:432` plus the unattributed line at `:441-447`. Replace both with `CoverageMap`. Convert `MuscleCoverageScreen` to `MuscleCoverageContent` (drop `PushedHeader` and the `onBack`); the route wiring changes in Task 7, so keep a thin `MuscleCoverageScreen` wrapper that still compiles until then.
- [ ] **Step 4: Run both, expect PASS;** also `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.ui.program.*"` to catch regressions in existing coverage tests.
- [ ] **Step 5: Screenshot** (setup preview, plan builder preview, Train coverage as it is today): capture the three screens with the same fictional plan to `D:\Monarch\.tmp\audit\fatigue\t6_setup_coverage.png`, `t6_builder_coverage.png`, `t6_coverage_screen.png`; confirm look is identical (figure, legend, hatching).
- [ ] **Step 6: Commit:** `git add app/src/main/kotlin/com/ironvellum/app/ui/program/CoverageMap.kt app/src/main/kotlin/com/ironvellum/app/ui/program/MuscleCoverageScreen.kt app/src/main/kotlin/com/ironvellum/app/ui/program/MuscleHeatMap.kt app/src/test/kotlin/com/ironvellum/app/ui/program/CoverageMapStateTest.kt app/src/androidTest/kotlin/com/ironvellum/app/ui/program/CoverageMapParityTest.kt` -> `Share one Coverage map between setup, plan builder and Muscles`

---

### Task 7: Train tabs (Plan | Muscles), Fatigue | Coverage switch and links

**Files:**
- Create: `S/ui/train/TrainScreen.kt`, `S/ui/train/TrainTabMemory.kt`, `S/ui/program/MusclesScreen.kt`
- Modify: `S/ui/train/PresetsScreen.kt` (coverage `WayOutRow` at `:381-387` keeps its row but calls `onOpenCoverage`, which now switches tab in place; the page itself becomes the Plan tab), `S/ui/IronvellumNav.kt` (`Routes.MUSCLE_COVERAGE` `:143`, `PresetsScreen` call `:422`, route `:437-449`), `S/res/values/strings.xml`
- Test: `T/ui/train/TrainTabMemoryTest.kt`, `A/ui/train/TrainTabsTest.kt`

**Interfaces:**
- Consumes: `InkTabs` (`ui/components/InkTabs.kt:80`; no nested pagers: Coverage keeps its own Planned | Last 7 days pager), `MuscleCoverageContent` (Task 6), `PresetsScreen`.
- Produces:
```kotlin
enum class TrainTab { PLAN, MUSCLES }; enum class MusclesView { FATIGUE, COVERAGE }
object TrainTabMemory {                                    // process-lifetime only, never persisted
    var tab by mutableStateOf(TrainTab.PLAN); var view by mutableStateOf(MusclesView.FATIGUE)
    fun openCoverage() { tab = TrainTab.MUSCLES; view = MusclesView.COVERAGE }   // a link always wins
}
@Composable fun TrainScreen(/* PresetsScreen params minus onOpenCoverage */)       // InkTabs: Plan | Muscles
@Composable internal fun MusclesScreen(onGenerateSession: () -> Unit, onOpenRite: (Long) -> Unit, onOpenRites: () -> Unit, onOptimise: () -> Unit, onOpenLift: (String, String) -> Unit)
```
Resolved: the Train coverage row stays on Plan and now calls `TrainTabMemory.openCoverage()`; `Routes.MUSCLE_COVERAGE` stays only as a redirect (`LaunchedEffect { TrainTabMemory.openCoverage(); navController.navigate(Routes.PRESETS) { popUpTo(Routes.MUSCLE_COVERAGE) { inclusive = true }; launchSingleTop = true } }`) so any stored link still works; the plan builder chip keeps its in-place `CoveragePreviewScreen` overlay (it shows the unsaved proposal, which Muscles cannot). Optimise (`MuscleCoverageContent` dock) still navigates `Routes.OPTIMISE_RITES`; back returns to Train with the remembered Muscles/Coverage state. Fatigue pane in this task is a placeholder composable `FatiguePane()` with the final caption string `fatigue_window_caption` ("Estimated from your last 7 days"); Task 8 fills it.

- [ ] **Step 1: Write failing tests.** Unit `TrainTabMemoryTest`: defaults are PLAN / FATIGUE; `openCoverage()` sets MUSCLES / COVERAGE; setting `view = COVERAGE` then `tab = PLAN` then `tab = MUSCLES` keeps COVERAGE (remembered); a fresh process default is restored by `TrainTabMemory.reset()` (test hook, internal). Instrumented `TrainTabsTest`: Train opens on Plan with Begin/rites visible; tapping "Muscles" shows the Fatigue | Coverage switch with Fatigue selected; tapping Coverage then Plan then Muscles still shows Coverage; tapping the Plan tab's coverage row lands on Muscles with Coverage selected even when Fatigue was remembered; navigating to `muscle_coverage` redirects to Train on Coverage; Muscles > Coverage > Optimise > back returns to Coverage.
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.ui.train.TrainTabMemoryTest"`; instrumented via `python3 tools/device.py lock --label "fatigue t7" -- bash -c 'ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedFossDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ironvellum.app.ui.train.TrainTabsTest'`
- [ ] **Step 3: Implement.** Strings: `fatigue_tab_plan` ("Plan"), `fatigue_tab_muscles` ("Muscles"), `fatigue_switch_fatigue` ("Fatigue"), `fatigue_switch_coverage` ("Coverage"), `fatigue_window_caption`. Nav: the `Routes.PRESETS` composable renders `TrainScreen`; remove the `PresetsScreen onOpenCoverage = { navigate(MUSCLE_COVERAGE) }` call (`IronvellumNav.kt:422`); replace the route body (`:437-449`) with the redirect. Run the strings guards.
- [ ] **Step 4: Run, expect PASS;** plus `StringsXmlTest`, `GlossaryTest`, `HardcodedUiLiteralTest` (`--tests` each).
- [ ] **Step 5: Screenshot:** `t7_train_plan.png`, `t7_muscles_fatigue_placeholder.png`, `t7_muscles_coverage.png`, `t7_coverage_row_deeplink.png` to `D:\Monarch\.tmp\audit\fatigue\`. Check Plan looks like today's Train (no regression: Begin button, rows, recent workouts still fit).
- [ ] **Step 6: Commit:** `git add` the created and modified files above -> `Split Train into Plan and Muscles tabs and fold coverage into Muscles`

---

### Task 8: Fatigue map, legend and Most worked list (plus emulator seeding)

**Files:**
- Create: `S/ui/program/FatigueMap.kt` (figure + legend + `RegionLook`s), `S/ui/program/FatiguePane.kt` (replaces the Task 7 placeholder), `.tmp/audit/fatigue/seed_fatigue.py` (scratch, not committed)
- Modify: `S/ui/program/MuscleHeatMap.kt` (`RegionDecor` `:55` gains nothing new: reuse `HATCH` for High, `CROSS_HATCH` for Very high), `S/res/values/strings.xml`
- Test: `T/ui/program/FatigueLooksTest.kt`, `A/ui/program/FatiguePaneTest.kt`

**Interfaces:**
- Consumes: `FatigueReport.reading/mostWorked` (Task 4), `MuscleFatigueViewModel` (Task 5), `FigureBlock` (now internal), `TappableFigure`, `LocalBodySex`, `RegionLook`, `IronvellumColors`.
- Produces:
```kotlin
internal fun fatigueLook(level: FatigueLevel): RegionLook
// FRESH: bare paper (IronvellumColors.Rune), no decor.  LIGHT: primary accent alpha 0.30, no decor.
// MODERATE: accent alpha 0.60, no decor.  HIGH: accent alpha 0.85 + HATCH.  VERY_HIGH: accent alpha 0.95 + CROSS_HATCH in IronvellumColors.Caution.
@Composable internal fun FatigueMap(report: FatigueReport, selection: MutableState<Muscle?>, zone: ZoneId, modifier: Modifier = Modifier)
@Composable internal fun FatiguePane(onOpenMuscle: (Muscle) -> Unit /* Task 9 */, viewModel: MuscleFatigueViewModel = ...)
```
Light and Moderate must be separable at the 10 dp legend swatch: pinned alphas differ by 0.30 (0.30 vs 0.60) and `FatigueLooksTest` asserts it.

- [ ] **Step 1: Write failing tests.** Unit `FatigueLooksTest`: `fillsRiseWithLevel` (alpha strictly increasing FRESH < LIGHT < MODERATE < HIGH < VERY_HIGH); `lightAndModerateAreClearlyApart` (alpha gap >= 0.25); `hatchOnHighCrossHatchOnVeryHigh` (decor `HATCH`, `CROSS_HATCH`; the others `NONE`); `levelIsNeverColourAlone` (every level >= HIGH has decor != NONE). Instrumented `FatiguePaneTest` with a stub report: the figure node description lists muscles at Moderate and above by name; legend shows five labelled keys (Fresh, Light, Moderate, High, Very high); "Most worked" shows at most 4 rows each with level word, one-line cause and "fresh by <day>" (or none when already below); empty report shows the new-install empty copy (Review Focus 1); a report with only Fresh muscles shows the nothing-worked copy.
- [ ] **Step 2: Run, expect FAIL.** Unit `--tests "com.ironvellum.app.ui.program.FatigueLooksTest"`; instrumented via the lock with `-Pandroid.testInstrumentationRunnerArguments.class=com.ironvellum.app.ui.program.FatiguePaneTest`.
- [ ] **Step 3: Implement.** `FatigueMap` = `FigureBlock(figure, height, fill, selection, lineFor, modifier, decor = look) { legend }`, with `lineFor(m) = muscleLine(m, levelWord)`. Layout: figure, legend, caption `fatigue_window_caption`, then "Most worked" rows (muscle, level word, cause line "about 60% from Tuesday's Romanian Deadlift" built from `CauseShare` with a weekday formatter, and "fresh by <day>" from `freshBy`). Strings (plain unless naming a concept): `fatigue_level_fresh/light/moderate/high/very_high`, `fatigue_legend_*`, `fatigue_most_worked_title`, `fatigue_cause_line` (`%1$s` percent, `%2$s` weekday, `%3$s` lift name), `fatigue_fresh_by_today/tomorrow/weekday/over_week`, `fatigue_empty_new` (pair: "Log a workout and your muscles will show up here." / `__t` trial wording), `fatigue_empty_rested`. Selecting a muscle only lights it and shows the one-line summary (the breakdown sheet is Task 9).
- [ ] **Step 4: Seed fictional history on the emulator** (scratch script, never on the phone): `python3 tools/device.py up`, build+install `:app:assembleFossDebug`, then with MSYS_NO_PATHCONV=1 pull the emulator DB via `run-as` as in the phone-DB procedure, insert with Python `sqlite3` several fictional completed sessions across the last 7 days (Mon back squat 5 sets RPE 8, Tue Romanian Deadlift 4 sets RPE 9 new lift, Wed bench press and incline press with `"incline,deficit"`, Fri a stretch-heavy `Lying Leg Curl`, today a lat pulldown), push back with `test -s` guard and removal of old `-wal`/`-shm`; name fictional workouts plainly (no real data). All emulator commands inside `python3 tools/device.py lock -- ...`.
- [ ] **Step 5: Run, expect PASS** (unit, then instrumented under the lock).
- [ ] **Step 6: Screenshots** to `D:\Monarch\.tmp\audit\fatigue\`: `t8_fatigue_map_seeded.png` (figure + legend + Most worked), `t8_fatigue_legend_zoom.png` (Light vs Moderate swatches distinct), `t8_fatigue_empty_new_install.png` (fresh install, `pm clear` first, under the lock), `t8_fatigue_light_theme.png` and dark if the app has both, `t8_fatigue_tap_muscle.png`.
- [ ] **Step 7: Commit:** `git add app/src/main/kotlin/com/ironvellum/app/ui/program/FatigueMap.kt app/src/main/kotlin/com/ironvellum/app/ui/program/FatiguePane.kt app/src/main/kotlin/com/ironvellum/app/ui/program/MuscleHeatMap.kt app/src/main/res/values/strings.xml app/src/test/kotlin/com/ironvellum/app/ui/program/FatigueLooksTest.kt app/src/androidTest/kotlin/com/ironvellum/app/ui/program/FatiguePaneTest.kt` -> `Show muscle fatigue on a body heatmap with a Most worked list`

---

### Task 9: Muscle breakdown, "How this is estimated" sheet, cause rows to the lift page

**Files:**
- Create: `S/ui/program/MuscleBreakdownSheet.kt`, `S/ui/program/FatigueExplainerSheet.kt`
- Modify: `S/ui/program/FatiguePane.kt` (opens the breakdown on selection), `S/ui/program/MusclesScreen.kt` (passes `onOpenLift`), `S/ui/IronvellumNav.kt` (`Routes.exercises(name, variation)` at `:174`; the `onOpenLift` lambda already exists at `:408` for Titles), `S/res/values/strings.xml`
- Test: `T/ui/program/FatigueCopyTest.kt`, `A/ui/program/MuscleBreakdownTest.kt`

**Interfaces:**
- Consumes: `FatigueReading` (Task 4), `Variation.label(name, modifiers)`, `Variation.key(modifiers)`, `Routes.exercises(name, variation)`, `DayNames.full`.
- Produces:
```kotlin
internal fun lightByLine(lightBy: LightBy, locale: Locale): String?          // null for None; "back to light by tomorrow" / by Thursday / "in over a week"
internal fun sorenessLine(note: SorenessNote): String                         // "Romanian Deadlift was new to you and loads the stretched hamstring."
@Composable internal fun MuscleBreakdownSheet(reading: FatigueReading, onOpenLift: (name: String, variation: String) -> Unit, onExplain: () -> Unit, onDismiss: () -> Unit)
@Composable internal fun FatigueExplainerSheet(onDismiss: () -> Unit)
```
Explainer states plainly: it is an estimate from logged hard sets; soreness does not mean a good workout and its absence does not mean a bad one; constants are starting estimates, not fitted to the user; nothing leaves the device.

- [ ] **Step 1: Write failing tests.** Unit `FatigueCopyTest` (reads strings via `DiskStrings`, as `GlossaryTest` does; run both `Words.themed` values): `lightByLinePerCase` (Today/Tomorrow/Weekday/OverAWeek texts, None -> null); `sorenessLineNamesTheCause` for each `SorenessReason`; `explainerSaysSorenessIsNotAGoodWorkoutSignal` (the string contains the not-a-quality-signal sentence key `fatigue_explainer_soreness`); `noDecimalsInCopy` (no `\d\.\d` in any `fatigue_*` string that renders a level). Instrumented `MuscleBreakdownTest`: tapping a muscle on the Fatigue map opens the sheet with level word, one row per cause with a percent ending in 0 or 5, an "other" row only when more than 3 causes, the soreness note when present, the light-by line when present, and "How this is estimated"; tapping a cause row calls `onOpenLift("Romanian Deadlift", "")` and for an incline-deficit bench cause `("Bench Press", "deficit,incline")` (variation key, sorted).
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.ui.program.FatigueCopyTest"` and the instrumented class under the lock.
- [ ] **Step 3: Implement** sheets (reuse the existing `InfoSheet`/sheet components from `ui/components/ExerciseInfoSheet.kt` for chrome; a contribution bar is one stacked `Row` with fixed 5% rounding). Strings: `fatigue_breakdown_*`, `fatigue_light_by_*`, `fatigue_soreness_new_stretch/new/stretch` (`%1$s` lift, `%2$s` muscle lowercased), `fatigue_explainer_*`, `fatigue_other_label`. Wire `onOpenLift = { name, variation -> navController.navigate(Routes.exercises(name, variation)) }` from the nav for Train's `MusclesScreen`.
- [ ] **Step 4: Run, expect PASS;** plus the strings guards.
- [ ] **Step 5: Screenshots:** `t9_breakdown_hamstrings_soreness.png` (new + stretch RDL with the soreness note), `t9_breakdown_chest_variation.png` (incline/deficit cause row label shows the variation), `t9_breakdown_other_row.png` (more than 3 causes), `t9_explainer.png`, `t9_cause_row_lift_page.png` (lift page opens on the right variation chip).
- [ ] **Step 6: Commit:** `git add app/src/main/kotlin/com/ironvellum/app/ui/program/MuscleBreakdownSheet.kt app/src/main/kotlin/com/ironvellum/app/ui/program/FatigueExplainerSheet.kt app/src/main/kotlin/com/ironvellum/app/ui/program/FatiguePane.kt app/src/main/kotlin/com/ironvellum/app/ui/program/MusclesScreen.kt app/src/main/kotlin/com/ironvellum/app/ui/IronvellumNav.kt app/src/main/res/values/strings.xml app/src/test/kotlin/com/ironvellum/app/ui/program/FatigueCopyTest.kt app/src/androidTest/kotlin/com/ironvellum/app/ui/program/MuscleBreakdownTest.kt` -> `Explain each muscle's fatigue with causes, soreness note and estimate sheet`

---

### Task 10: Plan builder "Muscles right now" row and per-day note

**Files:**
- Create: `S/domain/PlanFatigue.kt`
- Modify: `S/ui/program/ForgeCycle.kt` (`ForgePlanPage` chip at `:657`, `ProposedDay` call at `:662-675`), `S/ui/program/ProgramBuilderScreen.kt` (chip at `:952`/`:985`; `ProposedDay` at `:968`), `S/ui/IronvellumNav.kt`/callers to pass an `onOpenFatigue` that does `TrainTabMemory.tab = MUSCLES; view = FATIGUE` then pops to Train, `S/res/values/strings.xml`
- Test: `T/domain/PlanFatigueTest.kt`, `A/ui/program/ForgeFatigueLineTest.kt`
- Read first: `ProposedDay` (`ForgeCycle.kt`), `PlannedPreset.scheduledDay` (`domain/Program.kt:255`, 1 = Monday).

**Interfaces:**
- Consumes: `FatigueReport.levelAt`, `FatigueReport.reading`, `MuscleMap.profile(entry)`.
- Produces:
```kotlin
data class RightNowRow(val items: List<RightNowItem>)                       // Moderate+ muscles, up to 3, worst first
data class RightNowItem(val muscle: Muscle, val level: FatigueLevel, val cause: CauseShare)
data class DayFatigueNote(val muscle: Muscle, val level: FatigueLevel, val weekday: DayOfWeek)   // up to 2 per day
object PlanFatigue {
    fun rightNow(report: FatigueReport, zone: ZoneId): RightNowRow?          // null when no muscle is Moderate or above
    /** Next 08:00 local on [scheduledDay] that is strictly after nowMs. */
    fun nextEightAm(scheduledDay: Int, nowMs: Long, zone: ZoneId): Long
    /** Muscles the day trains (share >= 0.5, MuscleMap's "trains the muscle" threshold) still at MODERATE or above at 08:00; empty when Light/Fresh. */
    fun dayNotes(preset: PlannedPreset, report: FatigueReport, zone: ZoneId): List<DayFatigueNote>
}
```
Footer copy `fatigue_plan_unchanged` ("Nothing in your plan changed.", pair for the concept word "plan"/"cycle") sits under the row; it never alters presets.

- [ ] **Step 1: Write failing tests** `PlanFatigueTest`:
  - `rightNowIsNullWhenNothingIsModerate` (also empty report, Review Focus 1).
  - `rightNowNamesWorstMusclesAndTheirTopCause`: quads High from Tuesday's squats, glutes Moderate -> items `[QUADS, GLUTES]`, cause `exerciseName == "Back Squat"`, at most 3 items.
  - `nextEightAmIsStrictlyAfterNow`: now = Mon 07:00 local, day 1 -> same Monday 08:00; now = Mon 08:00 exactly or 14:00 -> next Monday 08:00; day 7 from Wednesday -> that Sunday.
  - `eightAmOnDstChangeDay` (Review Focus 4): zone `America/New_York`, spring-forward Sunday 2026-03-08, day 7 -> epoch equals `ZonedDateTime.of(2026,3,8,8,0,0,0,zone)`; fall-back 2026-11-01 likewise (08:00 exists both days, offset differs).
  - `dayNoteWhenStillModerateAtEightAm`: quads at Moderate at that 08:00 and the day trains quads at share 1.0 -> one note "QUADS MODERATE Monday"; Light at that time -> no note; a muscle at 0.25 share -> no note.
  - `dayNotesNeverMutateThePlan`: the `PlannedPreset` list is unchanged (equals before/after).
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.PlanFatigueTest"`; instrumented `ForgeFatigueLineTest` (row shows under the coverage chip with a link to Muscles > Fatigue; per-day note renders quietly as "Quads: Moderate by Monday · estimate"; row absent on a rested lifter).
- [ ] **Step 3: Implement** `PlanFatigue` with `ZonedDateTime.of(date, LocalTime.of(8,0), zone)`; UI: a compact row under `CoverageChip` in `ForgePlanPage` and in both `ProgramBuilderScreen` chip sites that already show the proposed week, reading the same `MuscleFatigueViewModel`. Strings: `fatigue_right_now_title`, `fatigue_right_now_item` (`%1$s` muscle, `%2$s` level, `%3$s` weekday, `%4$s` lift), `fatigue_day_note` (`%1$s` muscle `%2$s` level `%3$s` weekday), `fatigue_plan_unchanged`/`__t`.
- [ ] **Step 4: Run, expect PASS** plus guards.
- [ ] **Step 5: Screenshots:** `t10_forge_muscles_right_now.png`, `t10_forge_day_note.png`, `t10_forge_rested_no_row.png` (before seeding the week, or on a clean install).
- [ ] **Step 6: Commit:** `git add app/src/main/kotlin/com/ironvellum/app/domain/PlanFatigue.kt app/src/main/kotlin/com/ironvellum/app/ui/program/ForgeCycle.kt app/src/main/kotlin/com/ironvellum/app/ui/program/ProgramBuilderScreen.kt app/src/main/kotlin/com/ironvellum/app/ui/IronvellumNav.kt app/src/main/res/values/strings.xml app/src/test/kotlin/com/ironvellum/app/domain/PlanFatigueTest.kt app/src/androidTest/kotlin/com/ironvellum/app/ui/program/ForgeFatigueLineTest.kt` -> `Mention current muscle fatigue in the plan builder without changing the plan`

---

### Task 11: Exercise info sheet Fatigue page (derived footprint)

**Files:**
- Create: `S/domain/FatigueFootprint.kt`
- Modify: `S/ui/components/ExerciseInfoSheet.kt` (`pages = listOf(...)` at `:68-103`: add a fourth `InfoPage` after History using `Words.text(UiText.themed(...))`-style label `infosheet_page_fatigue`; the sheet already drops pages with nothing on them, so an exercise with no profile shows none), `S/res/values/strings.xml`
- Test: `T/domain/FatigueFootprintTest.kt`, `A/ui/components/ExerciseInfoFatigueTest.kt`

**Interfaces:**
- Consumes: `FatigueDose.perSet`, `FatigueTraits`, `FatigueModel`/`FatigueReport`, `MuscleMap.keys`/`profile`, `FatigueLevel`, `Exercise.custom`.
- Produces:
```kotlin
enum class FootprintBand { STRONG, MEDIUM, LIGHT }
data class FootprintMuscle(val muscle: Muscle, val dose: Double, val band: FootprintBand)
data class FatigueFootprint(
    val muscles: List<FootprintMuscle>,          // dose >= 0.3 only, descending
    val stretchHeavy: Boolean, val heavyWholeBody: Boolean,
    val typicalLevel: FatigueLevel,              // level right after 3 hard sets at 1 rep left on the lead muscle, fresh, novelty 1.0
    val lightInHours: Int?,                      // hours until that muscle's residual is below 35% (the light-by threshold); null when it starts below it
    val comparison: Comparison?,
)
data class Comparison(val alternative: String, val trait: FootprintTrait)   // FootprintTrait: LOADS_LONG, HEAVY_WHOLE_BODY
object FatigueFootprints {
    fun of(exerciseName: String, modifiers: String, pool: Collection<String> = MuscleMap.keys): FatigueFootprint?   // null when no profile
}
```
Pinned: dose per muscle = `FatigueDose.perSet(<RPE 9 set>, novelty = 1.0)` summed x 3. Bands: `>= 2.0` STRONG, `1.0 <= d < 2.0` MEDIUM, `< 1.0` LIGHT. Lead muscle = first share-1.0 entry. Comparison: only when the exercise has a trait; among `pool` names with a profile whose lead muscle is the same, who lack that trait, pick the highest lead share, ties by same `MovementPattern`, then alphabetical; null when none.

- [ ] **Step 1: Write failing tests** `FatigueFootprintTest`:
  - `benchBands`: Bench Press -> MID_CHEST dose `1.0*0.9*3 = 2.7` STRONG; FRONT_DELTS `1.89` MEDIUM; TRICEPS `1.62` MEDIUM; UPPER_CHEST/LOWER_CHEST `1.35` MEDIUM; SIDE_DELTS `0.81` LIGHT; list descending.
  - `muscleUnderPointThreeIsOmitted`: a profile with a 0.1 share is absent; exactly 0.3 dose is kept (`>= 0.3`).
  - `rdlTags`: `stretchHeavy` and `heavyWholeBody` both true; HAMSTRINGS `1.404*3 = 4.212` STRONG.
  - `typicalIsFreshMuscleLevelAndLightIn`: Bench: lead dose 2.7 -> fraction 0.45 -> MODERATE; `lightInHours` equals `36*log2(2.7/2.1)` rounded (about 13 h); a lift whose lead dose is under 2.1 has `typicalLevel` LIGHT or FRESH and `lightInHours == null`.
  - `customExerciseHasNoTagsAndNoComparison`: tags false, comparison null; muscles by own shares.
  - `comparisonPicksNonTraitAlternative`: `of("Romanian Deadlift", "", pool = listOf("romanian deadlift","hamstring bridge","lying leg curl"))` -> `Comparison("Hamstring Bridge"-style name from the profile key's display, LOADS_LONG)` (the stretch table excludes `lying leg curl`); no trait -> null; empty pool -> null.
  - `derivedNeverHandWritten`: for every catalogue key with a profile, `of(key, "")` is non-null and every `dose >= 0.3` (guards drift between footprint and `FatigueDose`).
  - `noProfileGivesNull`.
- [ ] **Step 2: Run, expect FAIL:** `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.FatigueFootprintTest"`; instrumented `ExerciseInfoFatigueTest`: sheet shows pages About, Form, History, Fatigue in that order; the Fatigue page lists bands with the words Strong/Medium/Light, the tags "Stretch-heavy"/"Heavy whole-body" only when true, the "3 hard sets, 1 rep left, typically <level>, light in about N h/days" line and the why line.
- [ ] **Step 3: Implement** `FatigueFootprints.of` via `FatigueDose.perSet` and a one-session `FatigueReport` for the light-in solve (reuse `firstBelowMs`; no new decay code). UI page body in `ExerciseInfoSheet.kt` via small `fatigueFacts(exercise, modifiers)` (the same builder style as `muscleFacts`/`gearFacts` at `:129,153`). Strings: `infosheet_page_fatigue`, `fatigue_footprint_*` (band words, tags, typical line with `%1$s` level `%2$s` duration, why lines `loads the muscle long` / `heavy whole-body lift` / neither / comparison with `%1$s` alternative), duration formatting in hours below 48 h else days.
- [ ] **Step 4: Run, expect PASS** plus guards.
- [ ] **Step 5: Screenshots:** `t11_info_fatigue_bench.png`, `t11_info_fatigue_rdl_tags_comparison.png`, `t11_info_fatigue_custom.png`, `t11_info_pages_order.png`.
- [ ] **Step 6: Commit:** `git add app/src/main/kotlin/com/ironvellum/app/domain/FatigueFootprint.kt app/src/main/kotlin/com/ironvellum/app/ui/components/ExerciseInfoSheet.kt app/src/main/res/values/strings.xml app/src/test/kotlin/com/ironvellum/app/domain/FatigueFootprintTest.kt app/src/androidTest/kotlin/com/ironvellum/app/ui/components/ExerciseInfoFatigueTest.kt` -> `Add a derived Fatigue page to the exercise info sheet`

---

### Task 12: Skill practice rename (plain mode) and Account same-screen check

**Files:**
- Modify: `S/ui/titles/TitlesScreen.kt:392`, `S/res/values/strings.xml`
- Test: `T/ui/titles/TitlesTabLabelTest.kt`, `T/ui/AccountRoutesTest.kt`

**Interfaces:**
- Consumes: `Words.journal.u` (current tab label; `Concept` at `S/data/Words.kt:445`). `Words.journal.c/.t` stay untouched: `DeedLadders.kt:104` and `TitleEngine.kt:407-421,869` build sentences from them and would read badly ("Skill practice attempts").
- Produces: one pair `titles_tab_journal` = "SKILL PRACTICE" / `titles_tab_journal__t` = "JOURNAL" read as `Words.text(UiText.themed(R.string.titles_tab_journal, R.string.titles_tab_journal__t))` in the `labels = listOf(Words.deed.upl, Words.paths.u, <that>)` list. The themed name stays.

Finding for the Account check: both entry points (`IronvellumNav.kt:532` Settings and `:607` Friends/Allies) navigate `Routes.ACCOUNT` -> `AccountSettingsScreen` (`:624-626`), so signed-in it is already one screen; signed-out, Settings pushes `Routes.SIGN_IN` (`AccountScreen(pushed = true)`) while Allies embeds `AccountScreen` in the tab (same composable). The task is therefore a regression check, not a rewrite.

- [ ] **Step 1: Write failing tests.** `TitlesTabLabelTest`: with `Words.themed = false` the label resolves to "SKILL PRACTICE"; with `true` to "JOURNAL"; `Words.journal.c` still resolves to "Practice log" plain (unchanged). `AccountRoutesTest` (uses `SourceScan` on `IronvellumNav.kt`): every `onOpenAccount = {` lambda body navigates `Routes.ACCOUNT`, and `Routes.ACCOUNT` composable hosts `AccountSettingsScreen` (fails if a second Account destination appears).
- [ ] **Step 2: Run, expect FAIL** (label test only): `./gradlew :app:testFossDebugUnitTest --tests "com.ironvellum.app.ui.titles.TitlesTabLabelTest" --tests "com.ironvellum.app.ui.AccountRoutesTest"`
- [ ] **Step 3: Implement** the string pair and the one-line label change. List (do not change) other user-visible "practice log" strings found by `grep -n -i "practice log" app/src/main/res/values/strings.xml` (`:494, :829, :947, :4868` at planning time) in the commit report for the owner.
- [ ] **Step 4: Run, expect PASS** plus `GlossaryTest`, `StringsXmlTest`, `WordsFormsTest`, `HardcodedUiLiteralTest`.
- [ ] **Step 5: Screenshots:** `t12_progress_tab_skill_practice.png` (plain mode), `t12_progress_tab_journal_themed.png`, `t12_account_from_settings.png`, `t12_account_from_friends.png` (signed-in is not possible without an account on the emulator, so capture the signed-out view from both entry points and note that in the report); any visible difference is a bug to fix here.
- [ ] **Step 6: Commit:** `git add app/src/main/kotlin/com/ironvellum/app/ui/titles/TitlesScreen.kt app/src/main/res/values/strings.xml app/src/test/kotlin/com/ironvellum/app/ui/titles/TitlesTabLabelTest.kt app/src/test/kotlin/com/ironvellum/app/ui/AccountRoutesTest.kt` -> `Call the Progress practice tab Skill practice in plain mode`

---

### Task 13: Full gate and screenshot sweep

**Files:**
- Modify: none expected; `app/src/test/resources/hardcoded-ui-literals.baseline` only if a count dropped.
- Test: whole suite.

- [ ] **Step 1: Self-audit greps** (expect no hits): `grep -rn "stretchBias" app/src/main/kotlin/com/ironvellum/app/domain/Fatigue*.kt app/src/main/kotlin/com/ironvellum/app/domain/MuscleFatigue.kt` (traits never read it); `git diff main --stat -- app/src/main/kotlin/com/ironvellum/app/domain/MuscleMap.kt` (unchanged); `grep -rn "0\.35\|0\.15\|1\.15\|1\.4\b" app/src/main/kotlin/com/ironvellum/app/ui` (no constants outside `FatigueTuning`); no new network/Room/DAO symbols in the diff.
- [ ] **Step 2: Run the gate once** (one Gradle build at a time): `python3 tools/gate.py` (build, unit, lint, instrumented). Expected: green, wall clock under 5 minutes, test count not below declared. If the emulator was left locked: `python3 tools/device.py lock --label "fatigue gate" -- python3 tools/gate.py` is unnecessary, the gate takes the lock itself.
- [ ] **Step 3: Screenshot sweep** against the seeded emulator (all under the lock), saved in `D:\Monarch\.tmp\audit\fatigue\`: every file named in Tasks 6-12 exists and looks right; additionally `sweep_large_font.png` (font scale 1.3 on Muscles > Fatigue) and `sweep_talkback_labels.txt` (output of `python3 tools/device.py labels` on the Fatigue pane showing muscle node descriptions with level words).
- [ ] **Step 4: Push** (only when green): `git status` clean except intended, `git push origin main`.
- [ ] **Step 5: Report** the gate time, the screenshot list and the "practice log" strings left untouched.

---

## Self-review against the spec

- Where it lives: Train tabs, Muscles switch, Fatigue content, Coverage unchanged, route behaviour, breakdown, info sheet page, plan builder row and note, one Coverage map -> Tasks 7, 8, 6, 9, 11, 10, 6.
- Model: reuse of `HardSets`/`MuscleMap` and all dose factors -> Tasks 1-2; recovery classes, modifiers, residual window, session time -> Task 3; levels, shares, light-by, soreness -> Task 4; calls 1-3, 6, 7, 9 -> Tasks 2-5; call 4 -> Task 6; call 5 -> Task 7; call 8 -> Task 11; call 10 (batched screenshots, seeding) -> Task 8 seeding and per-task screenshots.
- Visuals (five levels, hatch/cross-hatch, Light vs Moderate distinct, strings) -> Task 8; also-in-this-work (rename, Account) -> Task 12; constraints (device-only, performance, variation-aware) -> Tasks 5, 2, 3.
- Names used across tasks are consistent: `FatigueReport`, `FatigueReading`, `FatigueLevel`, `LightBy`, `CauseShare`, `SorenessNote`, `FatigueSession`, `FatigueTuning`, `TrainTabMemory`.

## Decisions taken where the spec was open

1. **Novelty exposures** count earlier sessions with any done non-warm-up set of the same `liftKey` in the 21 days before the session (not only hard sets), one exposure per session.
2. **Lower-back bonus** is a synthetic share of 0.25 times effort and novelty only (no stretch or systemic), added only for heavy whole-body lifts whose profile gives lower back nothing.
3. **Stretch is per muscle** (table maps a lift to the muscles it loads long; a `deficit` modifier stretches the muscles with share >= 0.7), so the soreness note can name "the stretched hamstring".
4. **New lifter** is judged at "now" (first workout of any origin under 8 weeks old, or no history at all), not per session.
5. **Soreness** counts qualifying dose only from sessions aged 12 to 72 h inclusive; the note names the largest qualifying cause.
6. **Light-by days**: today, tomorrow, weekday name for 2-6 days ahead, "in over a week" from 7 days on or when still above 35% at 7 days; no line when already below.
7. **Per-day plan note** uses the next 08:00 local on the planned weekday strictly after now, only for muscles the day trains at share >= 0.5, only when Moderate or above, at most 2 per day; the "Muscles right now" row lists Moderate-and-above muscles (max 3) and is hidden when none.
8. **Plan builder coverage chip** keeps its in-place preview overlay (it shows the unsaved proposal); the Train coverage row and the `muscle_coverage` redirect open Muscles on Coverage.
9. **Most worked** lists muscles at Light or above, top 4 by residual.
10. **Skill practice** changes only the Progress tab label (new `titles_tab_journal` pair); `Words.journal` forms used in deed/title sentences are left alone.
