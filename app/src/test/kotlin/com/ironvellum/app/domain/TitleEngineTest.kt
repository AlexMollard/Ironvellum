package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import java.time.LocalDate

class TitleEngineTest {

    private val ledger = Titles.Ledger(totalXp = 0, workouts = 0, sets = 0, reps = 0)

    @Test
    fun `a rest day does not break the streak but a skipped training day does`() {
        val today = LocalDate.of(2026, 9, 18) // a Friday
        // Scheduled Mon/Wed/Fri. The days in between are rest, and rest must
        // neither grow nor break the run — the streak counted calendar days
        // before, so anyone training three times a week read 1.
        val monWedFri = setOf(1, 3, 5)
        val trained = setOf(
            LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 11),
            LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 18),
        )
        assertEquals(5, Titles.trainingStreakDays(trained, monWedFri, today))

        // Skipping a SCHEDULED day breaks it: Wednesday the 16th was missed.
        assertEquals(
            1,
            Titles.trainingStreakDays(trained - LocalDate.of(2026, 9, 16), monWedFri, today),
        )

        // Today is forgiving: a scheduled day not yet trained still shows the
        // run, so opening the app in the morning never reads 0.
        assertEquals(
            4,
            Titles.trainingStreakDays(trained - today, monWedFri, today),
        )
    }

    @Test
    fun `the deeds ledger carries the streak it is judged on`() {
        // trainingStreakDays was never assigned by ledgerOf, so it defaulted
        // to 0 and every TrainingStreak deed was unreachable.
        val zone = java.time.ZoneId.systemDefault()
        val today = LocalDate.now()
        val history = (0L..6L).map { back ->
            val at = today.minusDays(back).atStartOfDay(zone).toInstant().toEpochMilli()
            WorkoutSession(id = back + 1, label = "Pull", startedAtMs = at, completedAtMs = at) to
                emptyList<SessionSet>()
        }
        val built = Titles.ledgerOf(
            totalXp = 0,
            history = history,
            healthDays = emptyList(),
            practices = emptyList(),
            exercises = emptyMap(),
        )
        assertEquals(7, built.trainingStreakDays)
        assertTrue(Titles.satisfied(TitleRule.TrainingStreak(7), built))
    }

    @Test
    fun `first workout rule needs one workout`() {
        assertFalse(Titles.satisfied(TitleRule.FirstWorkout, ledger))
        assertTrue(Titles.satisfied(TitleRule.FirstWorkout, ledger.copy(workouts = 1)))
    }

    @Test
    fun `health data alone unlocks step titles`() {
        // The unlock path used to assemble a ledger from sessions only, so a
        // 20k-step day never awarded anything.
        val day = HealthDay(
            date = java.time.LocalDate.now(),
            steps = 21_000,
            distanceKm = 14.0,
            activeKcal = 900,
        )
        val built = Titles.ledgerOf(
            totalXp = 0,
            history = emptyList(),
            healthDays = listOf(day),
            practices = emptyList(),
            exercises = emptyMap(),
        )
        assertEquals(21_000, built.stepsBestDay)
        assertEquals(1, built.stepGoalDays)

        val unlocked = Titles.newlyUnlocked(built, emptySet()).map { it.id }
        assertTrue(
            "a 21k-step day must award the 10k and 20k step deeds, got $unlocked",
            unlocked.containsAll(
                Titles.ALL.filter {
                    it.rule is TitleRule.StepsInDay && (it.rule as TitleRule.StepsInDay).count <= 21_000
                }.map { it.id },
            ),
        )
    }

    @Test
    fun `skill practice feeds mastery and attempt counters`() {
        val built = Titles.ledgerOf(
            totalXp = 0,
            history = emptyList(),
            healthDays = emptyList(),
            practices = listOf(
                SkillPractice("Dead Hang", 1L, claimed = true),
                SkillPractice("Pull-up", 2L, claimed = false),
                SkillPractice("Pull-up", 3L, claimed = false),
            ),
            exercises = emptyMap(),
        )
        assertEquals(1, built.skillsMastered)
        assertEquals(2, built.practiceAttempts)
    }

    @Test
    fun `workout count rule is inclusive`() {
        val rule = TitleRule.Workouts(10)
        assertTrue(Titles.satisfied(rule, ledger.copy(workouts = 10)))
        assertFalse(Titles.satisfied(rule, ledger.copy(workouts = 9)))
    }

    @Test
    fun `level rule reads from xp curve`() {
        // Level 5 starts at 100+200+300+400 = 1000 xp
        val rule = TitleRule.ReachLevel(5)
        assertTrue(Titles.satisfied(rule, ledger.copy(totalXp = 1000)))
        assertFalse(Titles.satisfied(rule, ledger.copy(totalXp = 999)))
    }

    @Test
    fun `sets and reps rules are inclusive`() {
        assertTrue(Titles.satisfied(TitleRule.SetsLogged(100), ledger.copy(sets = 100)))
        assertFalse(Titles.satisfied(TitleRule.SetsLogged(100), ledger.copy(sets = 99)))
        assertTrue(Titles.satisfied(TitleRule.RepsLogged(1000), ledger.copy(reps = 1000)))
    }

    @Test
    fun `newlyUnlocked skips already held titles`() {
        val held = setOf("awakened")
        val l = ledger.copy(workouts = 1)
        val unlocked = Titles.newlyUnlocked(l, held)
        assertTrue(unlocked.none { it.id == "awakened" })

        val again = Titles.newlyUnlocked(l, emptySet())
        assertTrue(again.any { it.id == "awakened" })
    }

    @Test
    fun `all catalog ids are unique`() {
        assertEquals(Titles.ALL.size, Titles.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `byId resolves every catalog entry`() {
        Titles.ALL.forEach { def -> assertEquals(def, Titles.byId(def.id)) }
        assertEquals(null, Titles.byId("missing"))
    }

    // ---- every rule type: exact threshold vs one below ----

    private fun full() = ledger.copy(
        workouts = 1,
        sessionStrength = 1_000,
        lifetimeStrength = 50_000,
        stepsBestDay = 10_000,
        stepsLifetime = 100_000,
        distanceKmLifetime = 50.0,
        activeKcalBestDay = 500,
        sleepBestMinutes = 480,
        stepGoalDays = 10,
        skillsMastered = 1,
        practiceAttempts = 25,
        trainingStreakDays = 3,
        bestWeekWorkouts = 3,
        activityMinutes = 60,
        activityDistanceKm = 10.0,
        distinctActivities = 3,
        bestRunKm = 5.0,
        bestSwimKm = 1.0,
        hardestGrade = "V1",
        sportSessions = 1,
    )

    /** Asserts a rule flips exactly at its threshold, not one unit below. */
    private fun check(
        rule: TitleRule,
        value: Long,
        field: (Titles.Ledger, Long) -> Titles.Ledger,
    ) {
        assertTrue("satisfied at threshold: $rule", Titles.satisfied(rule, field(full(), value)))
        assertFalse("not satisfied below: $rule", Titles.satisfied(rule, field(full(), value - 1)))
    }

    @Test
    fun `every rule type is satisfied exactly at threshold and not one below`() {
        assertTrue(Titles.satisfied(TitleRule.FirstWorkout, ledger.copy(workouts = 1)))
        check(TitleRule.Workouts(10), 10) { l, v -> l.copy(workouts = v.toInt()) }
        check(TitleRule.SetsLogged(100), 100) { l, v -> l.copy(sets = v.toInt()) }
        check(TitleRule.RepsLogged(1_000), 1_000) { l, v -> l.copy(reps = v.toInt()) }
        check(TitleRule.SessionStrength(1_000), 1_000) { l, v -> l.copy(sessionStrength = v.toInt()) }
        check(TitleRule.LifetimeStrength(50_000), 50_000) { l, v -> l.copy(lifetimeStrength = v.toInt()) }
        check(TitleRule.StepsInDay(10_000), 10_000) { l, v -> l.copy(stepsBestDay = v.toInt()) }
        check(TitleRule.StepsLifetime(100_000), 100_000) { l, v -> l.copy(stepsLifetime = v) }
        check(TitleRule.DistanceKmLifetime(50.0), 50) { l, v -> l.copy(distanceKmLifetime = v.toDouble()) }
        check(TitleRule.ActivityMinutes(60), 60) { l, v -> l.copy(activityMinutes = v.toInt()) }
        check(TitleRule.ActivityDistanceKm(10.0), 10) { l, v -> l.copy(activityDistanceKm = v.toDouble()) }
        check(TitleRule.DistinctActivities(3), 3) { l, v -> l.copy(distinctActivities = v.toInt()) }
        check(TitleRule.LongestRun(5.0), 5) { l, v -> l.copy(bestRunKm = v.toDouble()) }
        check(TitleRule.LongestSwim(1.0), 1) { l, v -> l.copy(bestSwimKm = v.toDouble()) }
        // grade rule: flips on recognised grades of equal or higher rank
        val v1 = TitleRule.HardestGrade("V1")
        assertTrue(Titles.satisfied(v1, full().copy(hardestGrade = "V1")))
        assertTrue(Titles.satisfied(v1, full().copy(hardestGrade = "7A"))) // 7A ≈ V4, harder
        assertFalse(Titles.satisfied(v1, full().copy(hardestGrade = "V0")))
        assertFalse(Titles.satisfied(v1, full().copy(hardestGrade = "garbage")))
        assertTrue(Titles.satisfied(TitleRule.HardestGrade("6B"), full().copy(hardestGrade = "V1")))
        check(TitleRule.ActiveKcalInDay(500), 500) { l, v -> l.copy(activeKcalBestDay = v.toInt()) }
        check(TitleRule.SleepMinutesInNight(480), 480) { l, v -> l.copy(sleepBestMinutes = v.toInt()) }
        check(TitleRule.StepGoalDays(10), 10) { l, v -> l.copy(stepGoalDays = v.toInt()) }
        check(TitleRule.SkillsMastered(84), 84) { l, v -> l.copy(skillsMastered = v.toInt()) }
        check(TitleRule.PracticeAttempts(500), 500) { l, v -> l.copy(practiceAttempts = v.toInt()) }
        check(TitleRule.TrainingStreak(100), 100) { l, v -> l.copy(trainingStreakDays = v.toInt()) }
        check(TitleRule.WorkoutsInWeek(6), 6) { l, v -> l.copy(bestWeekWorkouts = v.toInt()) }
        // level rule via xp: level 5 starts at 1000 xp
        assertTrue(Titles.satisfied(TitleRule.ReachLevel(5), ledger.copy(totalXp = 1000)))
        assertFalse(Titles.satisfied(TitleRule.ReachLevel(5), ledger.copy(totalXp = 999)))
    }

    @Test
    fun `progress never exceeds 1 and reports correct remaining`() {
        Titles.ALL.forEach { def ->
            val p = Titles.progress(def.rule, full())
            assertTrue("fraction ${def.id}", p.fraction in 0f..1f)
            // remaining must hit zero exactly when the rule is satisfied —
            // full() only sets the lowest tier of each ladder, so higher
            // tiers legitimately still have work left.
            if (Titles.satisfied(def.rule, full())) {
                assertEquals("remaining ${def.id}", 0L, p.remaining)
            } else {
                assertTrue("remaining ${def.id}", p.remaining > 0L)
            }
        }
        val p = Titles.progress(TitleRule.StepsInDay(30_000), full().copy(stepsBestDay = 10_000))
        assertEquals(20_000L, p.remaining)
        assertTrue(p.fraction < 1f)
    }

    @Test
    fun `progress units read correctly`() {
        assertEquals("steps in a day", Titles.progress(TitleRule.StepsInDay(1), full()).unit)
        assertEquals("km lifetime", Titles.progress(TitleRule.DistanceKmLifetime(1.0), full()).unit)
        assertEquals("day streak", Titles.progress(TitleRule.TrainingStreak(3), full()).unit)
        assertEquals("active kcal in a day", Titles.progress(TitleRule.ActiveKcalInDay(500), full()).unit)
        assertEquals("minutes slept in a night", Titles.progress(TitleRule.SleepMinutesInNight(480), full()).unit)
        assertEquals("skills mastered", Titles.progress(TitleRule.SkillsMastered(5), full()).unit)
        assertEquals("practice attempts", Titles.progress(TitleRule.PracticeAttempts(25), full()).unit)
        assertEquals("workouts in a week", Titles.progress(TitleRule.WorkoutsInWeek(3), full()).unit)
    }

    // ---- activity ledger assembly ----

    private val exercises = mapOf(
        1L to Exercise(name = "Run", muscleGroup = MuscleGroup.LEGS, isWeighted = false, metric = ExerciseMetric.DISTANCE_TIME, category = "Cardio"),
        2L to Exercise(name = "Swim", muscleGroup = MuscleGroup.CORE, isWeighted = false, metric = ExerciseMetric.DISTANCE_TIME, category = "Water"),
        3L to Exercise(name = "Boulder", muscleGroup = MuscleGroup.PULL, isWeighted = false, metric = ExerciseMetric.ATTEMPTS_GRADE, category = "Climbing"),
        4L to Exercise(name = "Football", muscleGroup = MuscleGroup.LEGS, isWeighted = false, metric = ExerciseMetric.DURATION, category = "Sport"),
        5L to Exercise(name = "Squat", muscleGroup = MuscleGroup.LEGS, isWeighted = true, metric = ExerciseMetric.REPS),
    )

    private fun activityHistory() = listOf(
        WorkoutSession(id = 1L, label = "mixed", startedAtMs = 1L) to listOf(
            SessionSet(exerciseId = 1L, setIndex = 0, reps = 1, durationSec = 3600, distanceM = 5000.0, done = true),
            SessionSet(exerciseId = 2L, setIndex = 0, reps = 1, distanceM = 1000.0, done = true),
            SessionSet(exerciseId = 3L, setIndex = 0, reps = 1, grade = "V3", done = true),
            SessionSet(exerciseId = 4L, setIndex = 0, reps = 1, durationSec = 5400, done = true),
            SessionSet(exerciseId = 5L, setIndex = 0, reps = 10, weightKg = 100.0, done = true),
            // undone set must contribute nothing
            SessionSet(exerciseId = 1L, setIndex = 1, reps = 1, distanceM = 99999.0, done = false),
        ),
        WorkoutSession(id = 2L, label = "second run", startedAtMs = 2L) to listOf(
            // same activity logged again — must not double-count distinct
            SessionSet(exerciseId = 1L, setIndex = 0, reps = 1, distanceM = 3000.0, done = true),
        ),
    )

    @Test
    fun `ledgerOf derives every activity field`() {
        val built = Titles.ledgerOf(0, activityHistory(), emptyList(), emptyList(), exercises)
        // 90 (run) + 60 (football) minutes; swim and climb sets carry no duration
        assertEquals(150, built.activityMinutes)
        assertEquals(9.0, built.activityDistanceKm, 0.0001)
        assertEquals(4, built.distinctActivities)
        assertEquals(5.0, built.bestRunKm, 0.0001) // best single session, not lifetime sum
        assertEquals(1.0, built.bestSwimKm, 0.0001)
        assertEquals("V3", built.hardestGrade)
        assertEquals(1, built.sportSessions)
    }

    @Test
    fun `unrecognised grade never becomes hardest and lifting never counts`() {
        val history = listOf(
            WorkoutSession(id = 1L, label = "s", startedAtMs = 1L) to listOf(
                SessionSet(exerciseId = 3L, setIndex = 0, reps = 1, grade = "insane proj", done = true),
                SessionSet(exerciseId = 5L, setIndex = 0, reps = 1, grade = "V15", done = true),
            ),
        )
        val built = Titles.ledgerOf(0, history, emptyList(), emptyList(), exercises)
        assertEquals("", built.hardestGrade)
        assertFalse(Titles.satisfied(TitleRule.HardestGrade("V1"), built))
        assertEquals(0, built.activityMinutes)
        assertEquals(0, built.sportSessions)
    }

    @Test
    fun `grade ranking orders V Font and YDS consistently`() {
        // V-scale internally
        assertTrue(GradeRank.rank("VB")!! < GradeRank.rank("V0")!!)
        assertTrue(GradeRank.rank("V4")!! < GradeRank.rank("V5")!!)
        // Font internally
        assertTrue(GradeRank.rank("6A")!! < GradeRank.rank("6B")!!)
        assertTrue(GradeRank.rank("6B")!! < GradeRank.rank("6B+")!!)
        assertTrue(GradeRank.rank("7C+")!! < GradeRank.rank("8A")!!)
        // YDS internally
        assertTrue(GradeRank.rank("5.9")!! < GradeRank.rank("5.10a")!!)
        assertTrue(GradeRank.rank("5.13b")!! < GradeRank.rank("5.13c")!!)
        // Cross-system anchors: 6A ≈ V0, 7A ≈ V4, 8A ≈ V8
        assertEquals(GradeRank.rank("V0"), GradeRank.rank("6A"))
        assertEquals(GradeRank.rank("V4"), GradeRank.rank("7A"))
        assertEquals(GradeRank.rank("V8"), GradeRank.rank("8A"))
        // YDS vs V: 5.12a sits between V5 and V8
        assertTrue(GradeRank.rank("V5")!! < GradeRank.rank("5.12a")!!)
        assertTrue(GradeRank.rank("5.12a")!! < GradeRank.rank("V8")!!)
        // Garbage never ranks
        assertNull(GradeRank.rank("insane proj"))
        assertNull(GradeRank.rank("6Z"))
        assertNull(GradeRank.rank("V99"))
        assertNull(GradeRank.rank(""))
        // Garbage never outranks any recognised grade
        assertTrue((GradeRank.rank("v4") ?: Int.MIN_VALUE) == GradeRank.rank("V4"))
    }

    @Test
    fun `every catalog entry has nonblank name and description`() {
        Titles.ALL.forEach { def ->
            assertTrue("name ${def.id}", def.name.isNotBlank())
            assertTrue("description ${def.id}", def.description.isNotBlank())
        }
    }

    @Test
    fun `category is nonblank for every rule in catalog`() {
        Titles.ALL.forEach { def -> assertTrue("category ${def.id}", Titles.category(def.rule).isNotBlank()) }
    }
}
