# Strength Rank Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rank strength per movement pattern (Pull, Push, Legs) from loaded lifts and cleared skill-tree skills, show the breakdown, and celebrate a first-ever band.

**Architecture:** Pure-Kotlin domain: `LiftBoards` exposes its per-set tiered scoring (no behaviour change), a new `RankPatterns` maps lifts and skills to patterns, and `Rank` is rewritten to produce a `RankBreakdown`. `Repository` serves the breakdown as a Flow and computes a rank-up on seal against a SharedPreferences high-water mark. UI adds a `RankSheet` and a Victory-overlay banner.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room (read only here), JUnit 4 JVM tests, Gradle (`gradlew.bat` on Windows).

**Spec:** [docs/superpowers/specs/2026-10-05-strength-rank-overhaul-design.md](../specs/2026-10-05-strength-rank-overhaul-design.md)

## Global Constraints

- Window stays `Rank.WINDOW_DAYS = 90`. Step scale stays 0..10 with `Rank.forStep` bands unchanged.
- Skill tier N clears to step `2N − 1`. Lift steps come from `LiftBoards.stepFor`, unchanged.
- Rank = mean step over patterns that have a step, rounded down. Absent patterns are never zero.
- Claims never count. Assisted sets never count. Other modifiers (weighted, deficit, elevated) do.
- No change to `LiftBoards.marks` output, the ally boards, or cloud sync. `LiftBoardsTest` must stay green untouched.
- No XP for a rank-up.
- New files use CRLF line endings. Edited files keep the line endings they already have (`Rank.kt`, `GLOSSARY.md` are CRLF; `DashboardScreen.kt` is LF).
- Commit style: one-line imperative subject, capitalized, no prefix, no trailing period, no attribution trailer. Commit and push to `main` after each task.
- Unit tests: `cmd /c gradlew.bat :app:testFossDebugUnitTest --tests "<class>" --console=plain` from `D:\Monarch`.
- Never run instrumented tests against the phone `R5GL14GXV3J`. Installing on it (`:app:installFossDebug`) is allowed.

## File map

| File | Responsibility |
|---|---|
| Modify `app/src/main/kotlin/com/ironvellum/app/domain/LiftBoards.kt` | Expose `TieredScore`, `tieredScore`, `ratioForStep`, `movesBodyweight`, `isAssisted` |
| Create `app/src/main/kotlin/com/ironvellum/app/domain/RankPatterns.kt` | `Pattern` enum; lift/skill → pattern table |
| Rewrite `app/src/main/kotlin/com/ironvellum/app/domain/Rank.kt` | `PatternScore`, `RankBreakdown`, `Rank.breakdown`, `Rank.current` |
| Create `app/src/main/kotlin/com/ironvellum/app/domain/RankUp.kt` | Pure high-water comparison |
| Create `app/src/main/kotlin/com/ironvellum/app/data/HighestBandStore.kt` | Interface + SharedPreferences impl |
| Modify `app/src/main/kotlin/com/ironvellum/app/data/Repository.kt` | `observeRankBreakdown`, rank-up in `completeSession` |
| Modify `app/src/main/kotlin/com/ironvellum/app/IronvellumApp.kt` | Wire the store |
| Create `app/src/main/kotlin/com/ironvellum/app/ui/components/RankSheet.kt` | Breakdown sheet |
| Modify `app/src/main/kotlin/com/ironvellum/app/ui/dashboard/DashboardScreen.kt` | Tappable chip, VM flow |
| Modify `app/src/main/kotlin/com/ironvellum/app/ui/train/VictoryOverlay.kt` | Rank-up banner |
| Modify `app/src/main/kotlin/com/ironvellum/app/ui/components/TermInfo.kt` | RANKS copy |
| Modify `docs/GLOSSARY.md` | Strength Rank row |
| Tests under `app/src/test/kotlin/com/ironvellum/app/domain/` | `LiftBoardsTest` (add), `RankPatternsTest` (new), `RankTest` (rewrite), `RankUpTest` (new) |

---

### Task 1: Expose tiered set scoring from LiftBoards

**Files:**
- Modify: `app/src/main/kotlin/com/ironvellum/app/domain/LiftBoards.kt` (lines 140-160 and 297-305)
- Test: `app/src/test/kotlin/com/ironvellum/app/domain/LiftBoardsTest.kt`

**Interfaces:**
- Produces:
  - `data class LiftBoards.TieredScore(val lift: Lift, val step: Int, val exerciseName: String, val reps: Int, val movedKg: Double, val bodyweightKg: Double, val ratio: Double)`
  - `internal fun LiftBoards.tieredScore(set: SessionSet, bodyweight: Double, sex: Sex): TieredScore?`
  - `internal fun LiftBoards.ratioForStep(lift: Lift, sex: Sex, step: Int): Double` (step in 1..MAX_STEP)
  - `internal fun LiftBoards.movesBodyweight(lift: Lift): Boolean`
  - `internal fun LiftBoards.isAssisted(set: SessionSet): Boolean` (was private)

- [ ] **Step 1: Write the failing tests** — append to `LiftBoardsTest`:

```kotlin
    @Test
    fun `ratioForStep is the floor stepFor reads`() {
        for (sex in Sex.entries) for (lift in Lift.entries.filter { it.kind == LiftKind.TIERED }) {
            for (step in 1..LiftBoards.MAX_STEP) {
                val r = LiftBoards.ratioForStep(lift, sex, step)
                assertEquals("$lift $sex $step", step, LiftBoards.stepFor(lift, sex, r + 1e-9))
                assertEquals("$lift $sex under $step", step - 1, LiftBoards.stepFor(lift, sex, r - 1e-6))
            }
        }
    }

    @Test
    fun `tieredScore carries the figures behind the step`() {
        val set = SessionSet(exerciseId = 1, exerciseName = "Pull-up", setIndex = 0, reps = 5, weightKg = 15.2, done = true)
        val score = LiftBoards.tieredScore(set, 78.9, Sex.MALE)!!
        assertEquals(Lift.PULL_UP, score.lift)
        assertEquals(4, score.step)
        assertEquals(94.1, score.movedKg, 1e-9)
        assertEquals(78.9, score.bodyweightKg, 1e-9)
        assertEquals(94.1 * (1 + 5 / 30.0) / 78.9, score.ratio, 1e-9)
        assertEquals("Pull-up", score.exerciseName)
    }

    @Test
    fun `tieredScore skips what the boards skip`() {
        fun s(name: String, reps: Int = 5, done: Boolean = true, mods: String = "") =
            SessionSet(exerciseId = 1, exerciseName = name, setIndex = 0, reps = reps, weightKg = 20.0, modifiers = mods, done = done)
        assertNull(LiftBoards.tieredScore(s("Bench Press", done = false), 80.0, Sex.MALE))
        assertNull(LiftBoards.tieredScore(s("Pull-up", mods = "assisted"), 80.0, Sex.MALE))
        assertNull(LiftBoards.tieredScore(s("Bench Press", reps = 13), 80.0, Sex.MALE))
        assertNull(LiftBoards.tieredScore(s("Bench Press"), 0.0, Sex.MALE))
        assertNull(LiftBoards.tieredScore(s("Incline Bench Press"), 80.0, Sex.MALE))
    }
```

- [ ] **Step 2: Run, expect FAIL** (unresolved `ratioForStep`, `tieredScore`)

Run: `cmd /c gradlew.bat :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.LiftBoardsTest" --console=plain`

- [ ] **Step 3: Implement.** In `LiftBoards.kt`, after `stepFor` add:

```kotlin
    /** The smallest bodyweight multiple that scores [step] (1..[MAX_STEP]) on [lift]: the inverse of [stepFor]. */
    internal fun ratioForStep(lift: Lift, sex: Sex, step: Int): Double {
        require(step in 1..MAX_STEP) { "step $step outside 1..$MAX_STEP" }
        val f = floors(lift, sex)
        val tier = (step - 1) / 2
        if ((step - 1) % 2 == 0) return f[tier]
        val next = if (tier < f.lastIndex) f[tier + 1] else f[tier] * (f[tier] / f[tier - 1])
        return sqrt(f[tier] * next)
    }

    /** True when the moved load is bodyweight + added kilos (pull-up, dip). */
    internal fun movesBodyweight(lift: Lift): Boolean = lift in BODYWEIGHT_LIFTS

    /** A qualifying set on a TIERED board, scored, with the figures behind its step. */
    data class TieredScore(
        val lift: Lift,
        val step: Int,
        val exerciseName: String,
        val reps: Int,
        val movedKg: Double,
        val bodyweightKg: Double,
        /** Epley e1RM over bodyweight. */
        val ratio: Double,
    )

    /** [set] scored on its TIERED board; null when it does not qualify for one. */
    internal fun tieredScore(set: SessionSet, bodyweight: Double, sex: Sex): TieredScore? {
        if (!set.done || isAssisted(set) || bodyweight <= 0.0) return null
        // Epley's rep term stops being honest past 12 (ProgramRules.MAX_E1RM_REPS).
        if (set.reps < 1 || set.reps > ProgramRules.MAX_E1RM_REPS) return null
        val lift = LIFT_BY_NAME[Titles.normaliseName(set.exerciseName)] ?: return null
        val added = (set.weightKg ?: 0.0).coerceAtLeast(0.0)
        val load = if (lift in BODYWEIGHT_LIFTS) bodyweight + added else added
        if (load <= 0.0) return null
        val ratio = ProgramRules.epley(load, set.reps) / bodyweight
        return TieredScore(lift, stepFor(lift, sex, ratio), set.exerciseName, set.reps, load, bodyweight, ratio)
    }
```

Change `private fun isAssisted` to `internal fun isAssisted`. In `marks`, replace the block from `if (bodyweight <= 0.0) continue` through `record(lift, stepFor(lift, sex, e1rm / bodyweight), workoutAt, isRecent)` with:

```kotlin
                tieredScore(set, bodyweight, sex)?.let { record(it.lift, it.step, workoutAt, isRecent) }
```

- [ ] **Step 4: Run, expect PASS** (all of `LiftBoardsTest`, old and new)

- [ ] **Step 5: Commit and push**

```bash
git add app/src/main/kotlin/com/ironvellum/app/domain/LiftBoards.kt app/src/test/kotlin/com/ironvellum/app/domain/LiftBoardsTest.kt
git commit -m "Expose per-set tiered scoring and step floors from the lift boards"
git push
```

---

### Task 2: Pattern table

**Files:**
- Create: `app/src/main/kotlin/com/ironvellum/app/domain/RankPatterns.kt`
- Test: `app/src/test/kotlin/com/ironvellum/app/domain/RankPatternsTest.kt`

**Interfaces:**
- Produces:
  - `enum class Pattern(val label: String) { PULL, PUSH, LEGS }`
  - `RankPatterns.forLift(lift: Lift): Pattern?`
  - `RankPatterns.forSkill(skill: Skills.SkillDef): Pattern?`
  - `RankPatterns.COUNTED: List<Pair<Skills.SkillDef, Pattern>>` (catalogue order)
  - `RankPatterns.EXCLUDED_LINES: Set<String>`, `RankPatterns.EXCLUDED_SKILLS: Set<String>`

- [ ] **Step 1: Write the failing test** (`RankPatternsTest.kt`, CRLF):

```kotlin
package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every lift and skill has a pattern or a stated reason not to count. */
class RankPatternsTest {

    @Test
    fun `every skill is counted or deliberately left out`() {
        val unplaced = Skills.ALL.filter { s ->
            RankPatterns.forSkill(s) == null &&
                s.name !in RankPatterns.EXCLUDED_SKILLS &&
                s.line !in RankPatterns.EXCLUDED_LINES &&
                s.metric != Skills.Metric.METRES
        }
        assertEquals(emptyList<String>(), unplaced.map { "${it.line}/${it.name}" })
    }

    @Test
    fun `no counted skill is a loaded standard`() {
        val loaded = RankPatterns.COUNTED.map { it.first }.filter {
            it.standard.contains("bodyweight", ignoreCase = true) || Regex("\\d\\s*kg", RegexOption.IGNORE_CASE).containsMatchIn(it.standard)
        }
        assertEquals(emptyList<String>(), loaded.map { it.name })
    }

    @Test
    fun `every tiered lift has a pattern`() {
        val missing = Lift.entries.filter { it.kind == LiftKind.TIERED && RankPatterns.forLift(it) == null }
        assertEquals(emptyList<Lift>(), missing)
    }

    @Test
    fun `patterns read as a coach would`() {
        fun p(name: String) = RankPatterns.forSkill(Skills.forName(name)!!)
        assertEquals(Pattern.PUSH, p("Handstand Push-up"))
        assertEquals(Pattern.PUSH, p("Tuck Planche"))
        assertEquals(Pattern.PUSH, p("Ring Dip"))
        assertEquals(Pattern.PULL, p("Front Lever"))
        assertEquals(Pattern.PULL, p("Muscle-up"))
        assertEquals(Pattern.LEGS, p("Pistol Squat"))
        assertNull(p("Hollow Hold"))
        assertNull(p("Weighted Pull-up"))
        assertNull(p("Handstand Walk"))
        assertEquals(Pattern.PULL, RankPatterns.forLift(Lift.PULL_UP))
        assertEquals(Pattern.PUSH, RankPatterns.forLift(Lift.OVERHEAD_PRESS))
        assertEquals(Pattern.LEGS, RankPatterns.forLift(Lift.DEADLIFT))
        assertNull(RankPatterns.forLift(Lift.PLANCHE))
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (unresolved `RankPatterns`)

Run: `cmd /c gradlew.bat :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.RankPatternsTest" --console=plain`

- [ ] **Step 3: Implement** (`RankPatterns.kt`, CRLF):

```kotlin
package com.ironvellum.app.domain

/** The three movement patterns Strength Rank averages over. */
enum class Pattern(val label: String) {
    PULL("Pull"),
    PUSH("Push"),
    LEGS("Legs"),
}

/**
 * Which pattern each TIERED lift and each counted skill-tree skill feeds.
 *
 * Core and Mobility never count, so core work cannot lower the rank. The gym
 * lines (Squat, Bench, Press, Deadlift) and the two weighted skills are
 * bodyweight-multiple standards the lift route already measures by e1RM. A
 * distance standard has no set figure to read.
 */
object RankPatterns {

    private val LIFTS: Map<Lift, Pattern> = mapOf(
        Lift.PULL_UP to Pattern.PULL,
        Lift.DIP to Pattern.PUSH,
        Lift.BENCH to Pattern.PUSH,
        Lift.OVERHEAD_PRESS to Pattern.PUSH,
        Lift.SQUAT to Pattern.LEGS,
        Lift.DEADLIFT to Pattern.LEGS,
    )

    private val LINES: Map<String, Pattern> = mapOf(
        "Pull" to Pattern.PULL,
        "Lever" to Pattern.PULL,
        "Push" to Pattern.PUSH,
        "Handstand" to Pattern.PUSH,
        "Planche" to Pattern.PUSH,
        "Legs" to Pattern.LEGS,
    )

    /** Rings and Movement mix patterns, so they are placed skill by skill. */
    private val SKILLS: Map<String, Pattern> = mapOf(
        "Ring Row" to Pattern.PULL,
        "Ring Muscle-up" to Pattern.PULL,
        "Banded Iron Cross" to Pattern.PULL,
        "Iron Cross" to Pattern.PULL,
        "Muscle-up" to Pattern.PULL,
        "Strict Muscle-up" to Pattern.PULL,
        "Inverted Muscle-up" to Pattern.PULL,
        "Tuck Human Flag" to Pattern.PULL,
        "Human Flag" to Pattern.PULL,
        "Ring Support Hold" to Pattern.PUSH,
        "RTO Support Hold" to Pattern.PUSH,
        "Ring Dip" to Pattern.PUSH,
    )

    val EXCLUDED_LINES: Set<String> = setOf("Core", "Mobility", "Squat", "Bench", "Press", "Deadlift")

    val EXCLUDED_SKILLS: Set<String> = setOf("Weighted Pull-up", "Weighted Dip", "Kip-up", "Handstand-to-Bridge")

    fun forLift(lift: Lift): Pattern? = LIFTS[lift]

    fun forSkill(skill: Skills.SkillDef): Pattern? = when {
        skill.name in EXCLUDED_SKILLS || skill.line in EXCLUDED_LINES -> null
        skill.metric == Skills.Metric.METRES -> null
        else -> SKILLS[skill.name] ?: LINES[skill.line]
    }

    /** The counted skills in catalogue order, each with its pattern. */
    val COUNTED: List<Pair<Skills.SkillDef, Pattern>> =
        Skills.ALL.mapNotNull { skill -> forSkill(skill)?.let { skill to it } }
}
```

- [ ] **Step 4: Run, expect PASS.** If `every skill is counted…` lists a skill, place it in `SKILLS` or `EXCLUDED_SKILLS` with the same reasoning as the spec's pattern table, and note it in the commit body.

- [ ] **Step 5: Commit and push**

```bash
git add app/src/main/kotlin/com/ironvellum/app/domain/RankPatterns.kt app/src/test/kotlin/com/ironvellum/app/domain/RankPatternsTest.kt
git commit -m "Map lifts and skill-tree skills to Pull, Push and Legs patterns"
git push
```

---

### Task 3: Rank by pattern, with the breakdown

**Files:**
- Rewrite: `app/src/main/kotlin/com/ironvellum/app/domain/Rank.kt` (keep CRLF)
- Rewrite: `app/src/test/kotlin/com/ironvellum/app/domain/RankTest.kt`

**Interfaces:**
- Consumes: Task 1 `LiftBoards.tieredScore`, `ratioForStep`, `movesBodyweight`, `isAssisted`, `TieredScore`; Task 2 `Pattern`, `RankPatterns.forLift`, `RankPatterns.COUNTED`.
- Produces:
  - `data class PatternScore(val pattern: Pattern, val step: Int, val source: String, val liftTarget: String?, val skillTarget: String?)`
  - `data class RankBreakdown(val band: String, val step: Int, val patterns: List<PatternScore>, val nextBand: String?, val stepsToNext: Int?, val progress: Float)`
  - `Rank.BANDS: List<String>` (Untrained..Elite)
  - `Rank.breakdown(history, bodyweightAt: (Long) -> Double, sex: Sex, nowMs: Long, practices: List<SkillPractice> = emptyList()): RankBreakdown?`
  - `Rank.current(history, bodyweightAt, sex, nowMs, practices = emptyList()): String?` (same first four params as today)
  - `Rank.forMarks` is removed (no callers outside `Rank.kt`).

- [ ] **Step 1: Write the failing test.** Replace `RankTest.kt` with:

```kotlin
package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Strength Rank by pattern: both routes, the mean, the window, and the breakdown. */
class RankTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day
    private val bodyweight = 100.0

    private fun set(name: String, reps: Int, weightKg: Double? = null, mods: String = "", seconds: Int? = null) =
        SessionSet(
            exerciseId = 1, exerciseName = name, setIndex = 0, reps = reps, weightKg = weightKg,
            modifiers = mods, done = true, durationSec = seconds,
        )

    private fun hold(name: String, seconds: Int) = set(name, reps = 0, seconds = seconds)

    private fun trial(daysAgo: Int, vararg sets: SessionSet): Pair<WorkoutSession, List<SessionSet>> {
        val at = now - daysAgo * day
        return WorkoutSession(id = 1, label = "t", startedAtMs = at - 3_600_000, completedAtMs = at) to sets.toList()
    }

    /** A one-rep set whose Epley e1RM is [ratio] x bodyweight. */
    private fun at(name: String, ratio: Double) =
        set(name, reps = 1, weightKg = ratio * bodyweight / (1.0 + 1 / 30.0))

    private fun rank(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        sex: Sex = Sex.MALE,
        bw: (Long) -> Double = { bodyweight },
        practices: List<SkillPractice> = emptyList(),
    ) = Rank.current(history, bw, sex, now, practices)

    private fun breakdown(history: List<Pair<WorkoutSession, List<SessionSet>>>, bw: (Long) -> Double = { bodyweight }) =
        Rank.breakdown(history, bw, Sex.MALE, now)

    /** The owner's phone log, 2026-10-05, at 78.9 kg. */
    private val ownerLog = listOf(
        trial(
            16,
            set("Handstand Push-up", 7),
            set("Archer Push-up", 7, 7.5, "weighted, deficit, elevated"),
            set("Push-up", 10, 7.5, "deficit, elevated, weighted"),
            hold("Hollow Hold", 45),
        ),
        trial(
            7,
            set("Pull-up", 5, 15.2, "weighted"),
            set("Pull-up", 3, 15.2, "weighted"),
            set("Chin-up", 8),
            set("Dumbbell Row", 8, 24.0),
        ),
        trial(
            5,
            set("Pistol Squat", 8, 7.7, "weighted"),
            set("Shrimp Squat", 7, 7.7, "weighted"),
            set("Hanging Leg Raise", 10),
        ),
        trial(
            1,
            set("Wall HSPU", 5),
            set("Archer Push-up", 7, 7.7, "weighted, deficit, elevated"),
            hold("Tuck Planche", 5),
            set("Push-up", 12, 7.7, "weighted, deficit, elevated"),
        ),
    )
    private val ownerBw: (Long) -> Double = { 78.9 }

    @Test
    fun `no work is unranked`() {
        assertNull(rank(emptyList()))
    }

    @Test
    fun `a lift without a bodyweight reading is unranked`() {
        assertNull(rank(listOf(trial(1, at("Bench Press", 1.5))), bw = { 0.0 }))
    }

    @Test
    fun `steps map two to a band`() {
        val expected = listOf(
            Rank.UNTRAINED, Rank.UNTRAINED, Rank.UNTRAINED,
            Rank.NOVICE, Rank.NOVICE,
            Rank.INTERMEDIATE, Rank.INTERMEDIATE,
            Rank.ADVANCED, Rank.ADVANCED,
            Rank.ELITE, Rank.ELITE,
        )
        assertEquals(expected, (0..LiftBoards.MAX_STEP).map { Rank.forStep(it) })
    }

    @Test
    fun `each band starts at its strength standard floor`() {
        val edges = listOf(1.0 to Rank.NOVICE, 1.25 to Rank.INTERMEDIATE, 1.5 to Rank.ADVANCED, 2.0 to Rank.ELITE)
        val below = listOf(Rank.UNTRAINED, Rank.NOVICE, Rank.INTERMEDIATE, Rank.ADVANCED)
        edges.forEachIndexed { i, (floor, band) ->
            assertEquals("at $floor", band, rank(listOf(trial(1, at("Bench Press", floor + 1e-6)))))
            assertEquals("under $floor", below[i], rank(listOf(trial(1, at("Bench Press", floor - 1e-3)))))
        }
    }

    @Test
    fun `the floors follow sex`() {
        val history = listOf(trial(1, at("Bench Press", 0.76)))
        assertEquals(Rank.INTERMEDIATE, rank(history, sex = Sex.FEMALE))
        assertEquals(Rank.UNTRAINED, rank(history, sex = Sex.MALE))
    }

    @Test
    fun `patterns combine as the mean step, rounded down`() {
        // Squat 2.3 = Legs 7, bench 1.0 = Push 3: mean 5.
        assertEquals(Rank.INTERMEDIATE, rank(listOf(trial(1, at("Back Squat", 2.3), at("Bench Press", 1.0)))))
        // Squat 2.3 (7), bench 0.9 (2): mean 4.5 rounds down to Novice.
        assertEquals(Rank.NOVICE, rank(listOf(trial(1, at("Back Squat", 2.3), at("Bench Press", 0.9)))))
    }

    @Test
    fun `a weak lift in a trained pattern does not lower it`() {
        val strong = listOf(trial(1, at("Deadlift", 4.0), at("Bench Press", 0.6)))
        val withWeakSquat = listOf(trial(1, at("Deadlift", 4.0), at("Bench Press", 0.6), at("Back Squat", 0.8)))
        assertEquals(Rank.INTERMEDIATE, rank(strong))
        assertEquals(rank(strong), rank(withWeakSquat))
    }

    @Test
    fun `each pattern counts its best in the window`() {
        val history = listOf(trial(3, at("Bench Press", 1.0)), trial(10, at("Bench Press", 1.5)))
        assertEquals(Rank.ADVANCED, rank(history))
    }

    @Test
    fun `work older than the window no longer counts`() {
        val old = trial(Rank.WINDOW_DAYS + 1, at("Bench Press", 2.0), set("Pistol Squat", 8))
        assertNull(rank(listOf(old)))
        assertEquals(Rank.NOVICE, rank(listOf(old, trial(5, at("Bench Press", 1.0)))))
        assertEquals(Rank.ELITE, rank(listOf(trial(Rank.WINDOW_DAYS - 1, at("Bench Press", 2.0)))))
    }

    @Test
    fun `the owner's log reads Intermediate`() {
        val b = breakdown(ownerLog, ownerBw)!!
        assertEquals(mapOf(Pattern.PULL to 4, Pattern.PUSH to 7, Pattern.LEGS to 5), b.patterns.associate { it.pattern to it.step })
        assertEquals(5, b.step)
        assertEquals(Rank.INTERMEDIATE, b.band)
    }

    @Test
    fun `core work never changes the rank`() {
        val withCore = ownerLog + trial(0, hold("Hollow Hold", 60), set("Hanging Leg Raise", 10), set("Hanging Knee Raise", 10))
        assertEquals(breakdown(ownerLog, ownerBw)!!.patterns, breakdown(withCore, ownerBw)!!.patterns)
    }

    @Test
    fun `a specialist is ranked on the patterns they train`() {
        val b = breakdown(listOf(trial(1, at("Bench Press", 1.3))))!!
        assertEquals(listOf(Pattern.PUSH), b.patterns.map { it.pattern })
        assertEquals(Rank.INTERMEDIATE, b.band)
    }

    @Test
    fun `skills rank without a weigh-in`() {
        assertEquals(Rank.INTERMEDIATE, rank(listOf(trial(1, set("Pistol Squat", 8))), bw = { 0.0 }))
        assertEquals(Rank.NOVICE, rank(listOf(trial(1, set("Push-up", 50))), bw = { 0.0 }))
    }

    @Test
    fun `a skill clears only at its standard`() {
        assertNull(rank(listOf(trial(1, set("Pistol Squat", 7)))))
        assertEquals(Rank.INTERMEDIATE, rank(listOf(trial(1, set("Pistol Squat", 8, 7.7, "weighted")))))
        assertNull(rank(listOf(trial(1, hold("Tuck Planche", 14)))))
        assertEquals(Rank.INTERMEDIATE, rank(listOf(trial(1, hold("Tuck Planche", 15)))))
    }

    @Test
    fun `assisted sets and claims never clear a skill`() {
        assertNull(rank(listOf(trial(1, set("Pistol Squat", 8, mods = "assisted")))))
        val claim = SkillPractice("Pistol Squat", now - day, claimed = true, value = 8)
        assertNull(rank(emptyList(), practices = listOf(claim)))
        val practice = SkillPractice("Pistol Squat", now - day, claimed = false, value = 8)
        assertEquals(Rank.INTERMEDIATE, rank(emptyList(), practices = listOf(practice)))
        val oldPractice = practice.copy(practicedAtMs = now - (Rank.WINDOW_DAYS + 1) * day)
        assertNull(rank(emptyList(), practices = listOf(oldPractice)))
    }

    @Test
    fun `the breakdown names the source and the next targets`() {
        val b = breakdown(ownerLog, ownerBw)!!
        val pull = b.patterns.single { it.pattern == Pattern.PULL }
        assertEquals("Pull-up 5 × +15.2 kg", pull.source)
        assertEquals("+0.6 kg on Pull-up × 5", pull.liftTarget)
        assertTrue(pull.skillTarget!!, pull.skillTarget!!.startsWith("Pull-up: "))
        val push = b.patterns.single { it.pattern == Pattern.PUSH }
        assertNull(push.liftTarget)
        assertTrue(push.skillTarget!!, push.skillTarget!!.startsWith("90-Degree Push-up: "))
        val legs = b.patterns.single { it.pattern == Pattern.LEGS }
        assertEquals("Pistol Squat", legs.source)
        assertTrue(legs.skillTarget!!, legs.skillTarget!!.startsWith("Dragon Squat: "))
    }

    @Test
    fun `the lift target is enough to clear the boundary`() {
        // 94.1 kg moved needs 0.58 kg more; 0.6 kg must reach Silver I.
        val bumped = LiftBoards.tieredScore(set("Pull-up", 5, 15.8, "weighted"), 78.9, Sex.MALE)!!
        assertEquals(5, bumped.step)
    }

    @Test
    fun `the headline counts steps to the next band`() {
        val b = breakdown(ownerLog, ownerBw)!!
        assertEquals(Rank.ADVANCED, b.nextBand)
        assertEquals(5, b.stepsToNext) // 7 x 3 patterns - 16
        assertEquals(1f / 6f, b.progress, 1e-6f) // (16 - 15) / (2 x 3)
    }

    @Test
    fun `elite has no next band and no lift target`() {
        val b = breakdown(listOf(trial(1, at("Bench Press", 2.6))))!!
        assertEquals(10, b.step)
        assertEquals(Rank.ELITE, b.band)
        assertNull(b.nextBand)
        assertNull(b.stepsToNext)
        assertEquals(1f, b.progress, 0f)
        assertNull(b.patterns.single().liftTarget)
    }

    @Test
    fun `an intermediate-looking lifter at 85 kg reads Novice`() {
        val history = listOf(
            trial(
                2,
                set("Bench Press", reps = 1, weightKg = 100.0),
                set("Back Squat", reps = 1, weightKg = 140.0),
                set("Deadlift", reps = 1, weightKg = 180.0),
            ),
        )
        // Push 4, Legs max(4, 5) = 5: mean 4.5 rounds down.
        assertEquals(Rank.NOVICE, Rank.current(history, { 85.0 }, Sex.MALE, now))
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (unresolved `Rank.breakdown`, `Pattern` fields, new `current` parameter)

Run: `cmd /c gradlew.bat :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.RankTest" --console=plain`

- [ ] **Step 3: Implement.** Replace `Rank.kt` with:

```kotlin
package com.ironvellum.app.domain

import java.util.Locale
import kotlin.math.ceil

/** One pattern's best mark in the window, and what raises it next. */
data class PatternScore(
    val pattern: Pattern,
    val step: Int,
    /** What earned [step]: "Pull-up 5 × +15.2 kg", or a skill's name. */
    val source: String,
    /** The least load that lifts the pattern one step on one of its lifts; null with no lift in it or at the top. */
    val liftTarget: String?,
    /** The next skill that would raise the pattern, with its standard; null when none is left. */
    val skillTarget: String?,
)

data class RankBreakdown(
    val band: String,
    val step: Int,
    val patterns: List<PatternScore>,
    /** The band above [band]; null at Elite. */
    val nextBand: String?,
    /** Pattern steps still to gain to reach [nextBand]; null at Elite. */
    val stepsToNext: Int?,
    /** 0..1 through the current band toward [nextBand]; 1 at Elite. */
    val progress: Float,
)

/**
 * Strength Rank: how strong the Ironbound is now, by movement pattern.
 *
 * The five words are the strength-standard bands. Each of Pull, Push and Legs
 * ([RankPatterns]) takes its best step in the last [WINDOW_DAYS] days from
 * either route:
 *  - a TIERED lift: estimated 1RM over bodyweight on [LiftBoards]' floors
 *    (Strength Level's Beginner..Elite), so it needs a weigh-in;
 *  - a skill-tree skill cleared at its standard by a done, unassisted set or a
 *    practice attempt: tier N is step 2N - 1, the lower half of band N.
 *
 * The rank is the mean step over the patterns that have one, rounded down. A
 * pattern with no work in the window is left out, never zero, and Core never
 * counts, so extra work can only raise the rank. It still falls when a pattern
 * goes untrained past the window or strength fades.
 *
 * Claims never count: a claim is an honours mark, not a measured figure.
 * Level never feeds this. Level follows XP and drives [ArmyClass].
 */
object Rank {

    const val UNRANKED = "Unranked"
    const val UNTRAINED = "Untrained"
    const val NOVICE = "Novice"
    const val INTERMEDIATE = "Intermediate"
    const val ADVANCED = "Advanced"
    const val ELITE = "Elite"

    const val WINDOW_DAYS = 90

    private const val WINDOW_MS = WINDOW_DAYS * 24L * 60 * 60 * 1000

    /** The bands in order; an index here is a band's place on the rank-up high-water mark. */
    val BANDS: List<String> = listOf(UNTRAINED, NOVICE, INTERMEDIATE, ADVANCED, ELITE)

    /** First step of each band in [BANDS]. */
    private val BAND_FLOORS = listOf(0, 3, 5, 7, 9)

    /** Band for a step 0..[LiftBoards.MAX_STEP]: two steps per band, step 0 with the first. */
    fun forStep(step: Int): String = when {
        step < 3 -> UNTRAINED
        step < 5 -> NOVICE
        step < 7 -> INTERMEDIATE
        step < 9 -> ADVANCED
        else -> ELITE
    }

    private val COUNTED_BY_NAME: Map<String, Pair<Skills.SkillDef, Pattern>> =
        RankPatterns.COUNTED.associateBy { Titles.normaliseName(it.first.name) }

    private fun skillStep(skill: Skills.SkillDef) = 2 * skill.tier - 1

    /** Current band from the trials and practices in the [WINDOW_DAYS] days up to [nowMs]; null when unranked. */
    fun current(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        bodyweightAt: (Long) -> Double,
        sex: Sex,
        nowMs: Long,
        practices: List<SkillPractice> = emptyList(),
    ): String? = breakdown(history, bodyweightAt, sex, nowMs, practices)?.band

    /** The rank with each pattern's mark and next targets; null when no pattern has a mark. */
    fun breakdown(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        bodyweightAt: (Long) -> Double,
        sex: Sex,
        nowMs: Long,
        practices: List<SkillPractice> = emptyList(),
    ): RankBreakdown? {
        fun inWindow(at: Long) = at <= nowMs && at > nowMs - WINDOW_MS

        val bestByLift = HashMap<Lift, LiftBoards.TieredScore>()
        val bestSkill = HashMap<Pattern, Skills.SkillDef>()
        val cleared = HashSet<String>()

        fun clear(skill: Skills.SkillDef, pattern: Pattern) {
            cleared += skill.name
            val held = bestSkill[pattern]
            if (held == null || skill.tier > held.tier) bestSkill[pattern] = skill
        }

        for ((session, sets) in history) {
            if (!inWindow(session.completedAtMs ?: session.startedAtMs)) continue
            val bodyweight = bodyweightAt(session.startedAtMs)
            for (set in sets) {
                LiftBoards.tieredScore(set, bodyweight, sex)?.let { score ->
                    val held = bestByLift[score.lift]
                    if (held == null || score.ratio > held.ratio) bestByLift[score.lift] = score
                }
                if (!set.done || LiftBoards.isAssisted(set)) continue
                val (skill, pattern) = COUNTED_BY_NAME[Titles.normaliseName(set.exerciseName)] ?: continue
                val figure = when (skill.metric) {
                    Skills.Metric.REPS -> set.reps
                    Skills.Metric.SECONDS -> set.durationSec ?: 0
                    Skills.Metric.METRES -> 0
                }
                if (skill.target > 0 && figure >= skill.target) clear(skill, pattern)
            }
        }
        for (practice in practices) {
            if (practice.claimed || !inWindow(practice.practicedAtMs)) continue
            val (skill, pattern) = COUNTED_BY_NAME[Titles.normaliseName(practice.skillName)] ?: continue
            if (skill.target > 0 && practice.value >= skill.target) clear(skill, pattern)
        }

        val patterns = Pattern.entries.mapNotNull { pattern ->
            val lifts = bestByLift.values.filter { RankPatterns.forLift(it.lift) == pattern }
            val skill = bestSkill[pattern]
            val liftStep = lifts.maxOfOrNull { it.step } ?: -1
            val skillStep = skill?.let(::skillStep) ?: -1
            val step = maxOf(liftStep, skillStep)
            if (step < 0) return@mapNotNull null
            val source = if (liftStep >= skillStep) liftSource(lifts.filter { it.step == liftStep }.maxBy { it.ratio }) else skill!!.name
            PatternScore(pattern, step, source, liftTarget(lifts, step, sex), skillTarget(pattern, step, cleared))
        }
        if (patterns.isEmpty()) return null

        val n = patterns.size
        val sum = patterns.sumOf { it.step }
        val step = sum / n
        val band = forStep(step)
        val index = BANDS.indexOf(band)
        val floor = BAND_FLOORS[index]
        val nextFloor = BAND_FLOORS.getOrNull(index + 1)
        return RankBreakdown(
            band = band,
            step = step,
            patterns = patterns,
            nextBand = BANDS.getOrNull(index + 1),
            stepsToNext = nextFloor?.let { it * n - sum },
            progress = if (nextFloor == null) 1f else (sum - floor * n).toFloat() / ((nextFloor - floor) * n),
        )
    }

    private fun liftSource(s: LiftBoards.TieredScore): String {
        val load = if (LiftBoards.movesBodyweight(s.lift)) {
            val added = s.movedKg - s.bodyweightKg
            if (added < 0.05) "BW" else "+${kg(added)} kg"
        } else {
            "${kg(s.movedKg)} kg"
        }
        return "${s.exerciseName} ${s.reps} × $load"
    }

    /** The smallest added load, over the pattern's lifts, that scores [step] + 1 at the same reps. */
    private fun liftTarget(lifts: List<LiftBoards.TieredScore>, step: Int, sex: Sex): String? {
        if (step >= LiftBoards.MAX_STEP) return null
        return lifts
            .map { s ->
                val needed = LiftBoards.ratioForStep(s.lift, sex, step + 1) * s.bodyweightKg / ProgramRules.epley(1.0, s.reps)
                s to roundUpTenth(needed - s.movedKg)
            }
            .minByOrNull { it.second }
            ?.let { (s, delta) -> "+${kg(delta)} kg on ${s.exerciseName} × ${s.reps}" }
    }

    /**
     * The next skill that raises the pattern past [step]: the lowest tier that
     * does, preferring one whose prerequisites the lifter has already cleared,
     * else catalogue order.
     */
    private fun skillTarget(pattern: Pattern, step: Int, cleared: Set<String>): String? {
        val above = RankPatterns.COUNTED
            .filter { (skill, p) -> p == pattern && skillStep(skill) > step && skill.name !in cleared }
            .map { it.first }
        val tier = above.minOfOrNull { it.tier } ?: return null
        val candidates = above.filter { it.tier == tier }
        val next = candidates.firstOrNull { c -> c.prerequisites.any { it in cleared } } ?: candidates.first()
        return "${next.name}: ${next.standard}"
    }

    /** Up to the next 0.1 kg, so meeting the target always clears the boundary. */
    private fun roundUpTenth(kg: Double): Double = (ceil(kg * 10 - 1e-9) / 10).coerceAtLeast(0.1)

    private fun kg(value: Double): String {
        val r = Math.round(value * 10) / 10.0
        return if (r % 1.0 == 0.0) String.format(Locale.ROOT, "%.0f", r) else String.format(Locale.ROOT, "%.1f", r)
    }
}
```

- [ ] **Step 4: Run, expect PASS** for `RankTest`, `RankPatternsTest`, `LiftBoardsTest`. Then run the whole unit suite (`:app:testFossDebugUnitTest`) to catch any other `Rank.forMarks` / `Rank.current` caller. `Repository.observeStrengthRank` still compiles: the new parameter has a default.

- [ ] **Step 5: Commit and push**

```bash
git add app/src/main/kotlin/com/ironvellum/app/domain/Rank.kt app/src/test/kotlin/com/ironvellum/app/domain/RankTest.kt
git commit -m "Rank strength by Pull, Push and Legs from lifts and cleared skills"
git push
```

---

### Task 4: Rank-up high-water mark and repository wiring

**Files:**
- Create: `app/src/main/kotlin/com/ironvellum/app/domain/RankUp.kt`
- Create: `app/src/main/kotlin/com/ironvellum/app/data/HighestBandStore.kt`
- Modify: `app/src/main/kotlin/com/ironvellum/app/data/Repository.kt` (constructor ~140, `CompletionResult` ~1083, `completeSession` ~1096-1166, `observeStrengthRank` ~1344)
- Modify: `app/src/main/kotlin/com/ironvellum/app/IronvellumApp.kt:33`
- Test: `app/src/test/kotlin/com/ironvellum/app/domain/RankUpTest.kt`

**Interfaces:**
- Consumes: Task 3 `Rank.BANDS`, `Rank.breakdown`, `Rank.current`, `RankBreakdown`.
- Produces:
  - `RankUp.check(highest: Int?, before: String?, after: String?): RankUp.Outcome` with `data class Outcome(val highest: Int, val rankUp: String?)`
  - `interface HighestBandStore { fun get(): Int?; fun set(index: Int) }`, `class PrefsHighestBandStore(context: Context)`
  - `Repository.observeRankBreakdown(): Flow<RankBreakdown?>`
  - `Repository.CompletionResult.rankUp: String?` (default null)

- [ ] **Step 1: Write the failing test** (`RankUpTest.kt`, CRLF):

```kotlin
package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** A rank-up is a band never held before, once. */
class RankUpTest {

    @Test
    fun `the first seal after the change starts the mark silently`() {
        assertEquals(RankUp.Outcome(2, null), RankUp.check(null, Rank.INTERMEDIATE, Rank.INTERMEDIATE))
    }

    @Test
    fun `the first seal still celebrates a real gain`() {
        assertEquals(RankUp.Outcome(2, Rank.INTERMEDIATE), RankUp.check(null, Rank.NOVICE, Rank.INTERMEDIATE))
        assertEquals(RankUp.Outcome(0, Rank.UNTRAINED), RankUp.check(null, null, Rank.UNTRAINED))
    }

    @Test
    fun `a new band celebrates and raises the mark`() {
        assertEquals(RankUp.Outcome(3, Rank.ADVANCED), RankUp.check(2, Rank.INTERMEDIATE, Rank.ADVANCED))
    }

    @Test
    fun `regaining a band held before is quiet`() {
        assertEquals(RankUp.Outcome(3, null), RankUp.check(3, Rank.INTERMEDIATE, Rank.ADVANCED))
        assertEquals(RankUp.Outcome(3, null), RankUp.check(3, Rank.ADVANCED, Rank.INTERMEDIATE))
        assertEquals(RankUp.Outcome(3, null), RankUp.check(3, Rank.ADVANCED, null))
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (unresolved `RankUp`)

Run: `cmd /c gradlew.bat :app:testFossDebugUnitTest --tests "com.ironvellum.app.domain.RankUpTest" --console=plain`

- [ ] **Step 3: Implement `RankUp.kt`** (CRLF):

```kotlin
package com.ironvellum.app.domain

/**
 * The rank-up moment: a band the Ironbound has never held before.
 *
 * [check] takes the stored high-water band index (null before the first seal
 * that recorded one) and the bands before and after a seal. With no stored
 * mark, the band before the seal stands in for it, so switching scoring rules
 * is never itself a rank-up. Bands are indices into [Rank.BANDS]; unranked
 * is -1.
 */
object RankUp {

    data class Outcome(val highest: Int, val rankUp: String?)

    fun check(highest: Int?, before: String?, after: String?): Outcome {
        val held = highest ?: index(before)
        val now = index(after)
        return if (now > held) Outcome(now, after) else Outcome(held, null)
    }

    private fun index(band: String?): Int = band?.let { Rank.BANDS.indexOf(it) } ?: -1
}
```

- [ ] **Step 4: Run, expect PASS.**

- [ ] **Step 5: Implement `HighestBandStore.kt`** (CRLF):

```kotlin
package com.ironvellum.app.data

import android.content.Context
import androidx.core.content.edit

/** Where the rank-up high-water mark lives: an index into Rank.BANDS, null before the first seal records one. */
interface HighestBandStore {
    fun get(): Int?
    fun set(index: Int)
}

/** [HighestBandStore] in its own prefs file. A reinstall starts it fresh, which costs at most one repeat moment. */
class PrefsHighestBandStore(context: Context) : HighestBandStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun get(): Int? = if (prefs.contains(KEY)) prefs.getInt(KEY, -1) else null

    override fun set(index: Int) = prefs.edit { putInt(KEY, index) }

    private companion object {
        const val PREFS = "rank_state"
        const val KEY = "highest_band"
    }
}
```

- [ ] **Step 6: Wire `Repository`.**

Constructor:

```kotlin
class Repository(
    private val db: IronvellumDatabase,
    private val health: HealthSync? = null,
    private val highestBand: HighestBandStore? = null,
) {
```

`CompletionResult`: add a last field `val rankUp: String? = null,` after `durationMinutes`.

In `completeSession`, immediately before `sessionDao.updateSession(`, add:

```kotlin
        // Before the seal: observeHistory holds sealed trials only, so this one is not in it yet.
        val rankBefore = if (highestBand != null) rankNow() else null
```

Immediately before `CompletionResult(`, add:

```kotlin
        val rankUp = highestBand?.let { store ->
            val outcome = RankUp.check(store.get(), rankBefore, rankNow())
            store.set(outcome.highest)
            outcome.rankUp
        }
```

and pass `rankUp = rankUp,` as the last argument.

Replace `observeStrengthRank` with:

```kotlin
    /** The Strength Rank written out ([Rank.breakdown]); null while unranked. */
    fun observeRankBreakdown(): Flow<RankBreakdown?> =
        combine(observeHistory(), observeStats(), observeBodyProfile(), observeSkillPractices()) { history, stats, body, practices ->
            Rank.breakdown(history, SetRecords.bodyweightLookup(stats), body.second, System.currentTimeMillis(), practices)
        }

    /** The current Strength Rank band; null while unranked. */
    fun observeStrengthRank(): Flow<String?> = observeRankBreakdown().map { it?.band }

    private suspend fun rankNow(): String? = Rank.current(
        observeHistory().first(),
        bodyweightLookup(),
        profileSex(),
        System.currentTimeMillis(),
        observeSkillPractices().first(),
    )
```

Add `import com.ironvellum.app.domain.RankBreakdown` and `import com.ironvellum.app.domain.RankUp` beside the other `domain` imports (the file imports them one by one).

`IronvellumApp.kt:33`:

```kotlin
    val repository: Repository by lazy { Repository(database, healthSync, PrefsHighestBandStore(this)) }
```

with `import com.ironvellum.app.data.PrefsHighestBandStore`.

- [ ] **Step 7: Build and run the unit suite**

Run: `cmd /c gradlew.bat :app:compileFossDebugKotlin :app:testFossDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 8: Commit and push**

```bash
git add app/src/main/kotlin/com/ironvellum/app/domain/RankUp.kt app/src/main/kotlin/com/ironvellum/app/data/HighestBandStore.kt app/src/main/kotlin/com/ironvellum/app/data/Repository.kt app/src/main/kotlin/com/ironvellum/app/IronvellumApp.kt app/src/test/kotlin/com/ironvellum/app/domain/RankUpTest.kt
git commit -m "Serve the rank breakdown and flag a first-ever band on seal"
git push
```

---

### Task 5: Rank sheet, rank-up banner and copy

**Files:**
- Create: `app/src/main/kotlin/com/ironvellum/app/ui/components/RankSheet.kt`
- Modify: `app/src/main/kotlin/com/ironvellum/app/ui/dashboard/DashboardScreen.kt` (VM ~272-275; composable ~582 and ~644-653) — LF endings
- Modify: `app/src/main/kotlin/com/ironvellum/app/ui/train/VictoryOverlay.kt` (~85-111 and ~143-150)
- Modify: `app/src/main/kotlin/com/ironvellum/app/ui/components/TermInfo.kt:70-80`
- Modify: `docs/GLOSSARY.md:61`

**Interfaces:**
- Consumes: Task 3 `RankBreakdown`, `PatternScore`, `Rank.forStep`, `Rank.WINDOW_DAYS`; Task 4 `Repository.observeRankBreakdown`, `CompletionResult.rankUp`; existing `InfoSheet`, `InfoProgress`, `InkPanel`.
- Produces: `@Composable fun RankSheet(breakdown: RankBreakdown?, onDismiss: () -> Unit)`

- [ ] **Step 1: Create `RankSheet.kt`** (CRLF):

```kotlin
package com.ironvellum.app.ui.components

import androidx.compose.runtime.Composable
import com.ironvellum.app.domain.Rank
import com.ironvellum.app.domain.RankBreakdown
import com.ironvellum.app.ui.theme.IronvellumColors

/** Strength Rank written out: each pattern's mark, what earned it, and what lifts it next. */
@Composable
fun RankSheet(breakdown: RankBreakdown?, onDismiss: () -> Unit) {
    InfoSheet(
        title = breakdown?.band ?: Rank.UNRANKED,
        subtitle = "Strength Rank",
        onDismiss = onDismiss,
        summary = if (breakdown != null) ({ RankProgress(breakdown) }) else null,
    ) {
        if (breakdown == null) {
            text(null, "Seal a trial with a pull, push or leg lift, or clear a pull, push or leg skill, to earn a rank.")
            return@InfoSheet
        }
        breakdown.patterns.forEach { p ->
            rows(
                p.pattern.label,
                listOfNotNull(
                    "Holds" to Rank.forStep(p.step),
                    "From" to p.source,
                    p.liftTarget?.let { "Next lift" to it },
                    p.skillTarget?.let { "Next skill" to it },
                    ("Next" to "Top of the scale").takeIf { p.liftTarget == null && p.skillTarget == null },
                ),
            )
        }
        text(
            null,
            "A pattern you have not trained in ${Rank.WINDOW_DAYS} days is left out, not counted as zero. " +
                "Core work never lowers your rank.",
            color = IronvellumColors.InkMuted,
        )
    }
}

@Composable
private fun RankProgress(b: RankBreakdown) {
    val steps = b.stepsToNext
    InfoProgress(
        fraction = b.progress,
        line = if (b.nextBand == null || steps == null) "Top of the scale" else "$steps ${if (steps == 1) "step" else "steps"} to ${b.nextBand}",
        caption = "Average of ${b.patterns.size} of 3 patterns over the last ${Rank.WINDOW_DAYS} days",
    )
}
```

If `return@InfoSheet` does not compile (the content lambda is `InfoSheetScope.() -> Unit`, so the label is the call's name), wrap the pattern rows and closing text in `else { … }` instead.

- [ ] **Step 2: Dashboard.** In the ViewModel, after `strengthRank`:

```kotlin
    /** The rank written out for [RankSheet]; null while unranked. */
    val rankBreakdown: StateFlow<RankBreakdown?> = repo.observeRankBreakdown()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
```

Update the `strengthRank` KDoc to "Strength Rank band, "Unranked" without one; blank until first read." In the composable, beside `val strengthRank by …` (~582):

```kotlin
    val rankBreakdown by viewModel.rankBreakdown.collectAsStateWithLifecycle()
    var rankOpen by remember { mutableStateOf(false) }
```

On the rank `Text` (~644), replace `modifier = Modifier.padding(start = 10.dp),` with:

```kotlin
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .clickable(role = Role.Button, onClickLabel = "Show Strength Rank") { rankOpen = true },
```

After the `Row { … TermInfo(Term.RANKS) }` closes, add:

```kotlin
                        if (rankOpen) RankSheet(rankBreakdown) { rankOpen = false }
```

Add any missing imports: `com.ironvellum.app.domain.RankBreakdown`, `com.ironvellum.app.ui.components.RankSheet`, `androidx.compose.ui.semantics.Role`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.setValue`, `androidx.compose.foundation.clickable`. Keep the file's LF endings.

- [ ] **Step 3: Victory banner.** In `VictoryOverlay`, beside `var peaksShown …`:

```kotlin
    var rankShown by remember { mutableStateOf(false) }
```

In the `LaunchedEffect(result)`, after the `repeat(peaks.size) { … }` loop and before `coroutineScope {`:

```kotlin
        if (result.rankUp != null) {
            rankShown = true
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            delay(400)
        }
```

In the report column, between the `if (peaks.isNotEmpty()) { … }` block and `Spacer(Modifier.height(16.dp)); TotalsStrip(…)`:

```kotlin
                        result.rankUp?.let { band ->
                            Spacer(Modifier.height(16.dp))
                            RankUpBanner(band, rankShown)
                        }
```

Add the composable after `PeaksPanel`:

```kotlin
/** A band never held before, announced once. */
@Composable
private fun RankUpBanner(band: String, visible: Boolean) {
    val t by animateFloatAsState(if (visible) 1f else 0f, tween(320), label = "rank")
    InkPanel(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = t
                val s = 0.92f + 0.08f * t
                scaleX = s
                scaleY = s
            },
        accent = IronvellumColors.SovereignGold,
    ) {
        Text(
            "RANK UP",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            letterSpacing = IronvellumTracking.SectionHeader,
            color = IronvellumColors.SovereignGold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            band.uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
        )
        Text(
            "Strength Rank",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
    }
}
```

Add `import androidx.compose.runtime.mutableStateOf` and `import androidx.compose.runtime.setValue` if missing.

- [ ] **Step 4: TermInfo copy.** Replace the `RANKS` body with:

```kotlin
    RANKS(
        "Strength Rank and Ascension",
        "Strength Rank is how strong you are now: Untrained, Novice, Intermediate, Advanced or " +
            "Elite, the strength-standard bands. It reads three patterns, Pull, Push and Legs, " +
            "over the last ${Rank.WINDOW_DAYS} days. Each takes its best mark: a squat, bench press, " +
            "deadlift, overhead press, pull-up or dip scored by estimated 1-rep max against your " +
            "bodyweight, or a pull, push or leg skill you cleared, at its skill-tree tier. Your rank " +
            "is the average of the patterns you trained, rounded down. One you skipped is left out, " +
            "and core work never lowers it. Tap your rank to see each pattern and what lifts it next. " +
            "Ascension follows your level, which rises with the XP each trial " +
            "earns: " + ArmyClass.LADDER.joinToString(", ") { "${it.title} at ${it.level}" } + ".",
    ),
```

If `ProgramRules` is no longer used in `TermInfo.kt`, remove its import.

- [ ] **Step 5: Glossary.** In `docs/GLOSSARY.md:61`, replace the definition cell only (keep the other cells and CRLF):

```
| **Strength Rank** | Current strength by pattern, Pull, Push and Legs: each takes its best mark in the last 90 days, a loaded lift's estimated 1-rep max over bodyweight or a cleared skill-tree skill at its tier, and the rank is their average, rounded down: Untrained, Novice, Intermediate, Advanced, Elite; **Unranked** before any | its own labelled value, never joined to the ascension; "STRENGTH RANK · Unranked" | rank alone; never for level |
```

- [ ] **Step 6: Full local gate** (takes the emulator lock itself; never the phone):

Run: `python3 tools/gate.py`
Expected: build, unit, lint and instrumented all green, test counts match sources.

- [ ] **Step 7: Visual check on the phone.**

Run: `ANDROID_SERIAL=R5GL14GXV3J ./gradlew :app:installFossDebug` (installing over the existing build keeps the data).
Check: the dashboard chip reads **Intermediate**. Tapping it opens the sheet with Pull (Novice, "Pull-up 5 × +15.2 kg", "+0.6 kg on Pull-up × 5"), Push (Advanced), Legs (Intermediate), and "5 steps to Advanced". Take screenshots with `adb -s R5GL14GXV3J exec-out screencap -p`. Do not seal a trial on the phone to test the banner; the owner sees it on their next real rank-up.

- [ ] **Step 8: Commit and push**

```bash
git add app/src/main/kotlin/com/ironvellum/app/ui/components/RankSheet.kt app/src/main/kotlin/com/ironvellum/app/ui/dashboard/DashboardScreen.kt app/src/main/kotlin/com/ironvellum/app/ui/train/VictoryOverlay.kt app/src/main/kotlin/com/ironvellum/app/ui/components/TermInfo.kt docs/GLOSSARY.md
git commit -m "Show the rank breakdown and announce a new Strength Rank"
git push
```
