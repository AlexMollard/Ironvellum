package com.ironvellum.app.domain

import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class WebSnapshotBuilderTest {
    private fun single(sets: List<SessionSet>, exercises: List<Exercise>): SnapshotInput {
        val base = WebSnapshotFixtures.input()
        val session = WorkoutSession(id = 99, label = "Test", startedAtMs = base.nowMs - 3_600_000, completedAtMs = base.nowMs)
        return base.copy(history = listOf(session to sets), exercises = exercises.associateBy { it.id }, stats = emptyList())
    }
    @Test fun sessionVolumeIsMovedKg() {
        val exercise = Exercise(10, "Barbell Squat", MuscleGroup.LEGS, true)
        val sets = (0..2).map { SessionSet(exerciseId = 10, exerciseName = exercise.name, setIndex = it, reps = 8, weightKg = 60.0, done = true) }
        assertEquals(1440.0, WebSnapshotBuilder.sessionEntries(single(sets, listOf(exercise))).getValue(99).volumeKg, 0.0)
    }
    @Test fun bodyweightLiftsAddNoVolume() {
        val exercise = Exercise(10, "Pull-up", MuscleGroup.PULL, false)
        val sets = listOf(SessionSet(exerciseId = 10, exerciseName = exercise.name, setIndex = 0, reps = 8, done = true))
        assertEquals(0.0, WebSnapshotBuilder.sessionEntries(single(sets, listOf(exercise))).getValue(99).volumeKg, 0.0)
    }
    @Test fun hardSetsExcludeWarmupsAndEasySets() {
        val exercise = Exercise(10, "Pull-up", MuscleGroup.PULL, false)
        val sets = listOf(
            SessionSet(exerciseId = 10, exerciseName = exercise.name, setIndex = 0, reps = 8, kind = SetKind.WARMUP),
            SessionSet(exerciseId = 10, exerciseName = exercise.name, setIndex = 1, reps = 8, done = true, rpe = 6),
            SessionSet(exerciseId = 10, exerciseName = exercise.name, setIndex = 2, reps = 8, done = true),
            SessionSet(exerciseId = 10, exerciseName = exercise.name, setIndex = 3, reps = 8, done = true, kind = SetKind.FAILURE),
        )
        assertEquals(2, WebSnapshotBuilder.sessionEntries(single(sets, listOf(exercise))).getValue(99).hardSets)
    }
    @Test fun levelAndXpMatchXpProgress() {
        val input = WebSnapshotFixtures.input()
        val profile = WebSnapshotBuilder.profileAndDeeds(input).first
        val expected = Xp.progress(126420)
        assertEquals(expected.level, profile.level); assertEquals(expected.intoLevel, profile.intoLevel); assertEquals(expected.needed, profile.needed)
    }
    @Test fun unitAndEffortAreCarried() {
        val input = WebSnapshotFixtures.input().copy(unit = LoadUnit.LB, effort = EffortScale.RIR)
        val snapshot = WebSnapshotBuilder.build(input)
        assertEquals("lb", snapshot.unit); assertEquals("rir", snapshot.effort)
    }
    @Test fun figureFollowsInput() {
        for (figure in listOf("male", "female")) assertEquals(figure, WebSnapshotBuilder.profileAndDeeds(WebSnapshotFixtures.input().copy(figure = figure)).first.figure)
    }
    @Test fun weeklyBucketsHave52EntriesWithZeros() {
        val input = WebSnapshotFixtures.input().copy(history = emptyList())
        val trends = WebSnapshotBuilder.weekAndTrends(input).second
        assertEquals(52, trends.weeks.size)
        assertTrue(trends.weeks.all { it.workouts == 0 && it.hardSets == 0.0 })
    }
    @Test fun monthsKeepTheLast24() {
        val input = WebSnapshotFixtures.input()
        val month = java.time.YearMonth.from(java.time.Instant.ofEpochMilli(input.nowMs).atZone(input.zone))
        val history = (0 until 30).map { n ->
            val at = month.minusMonths(n.toLong()).atDay(15).atStartOfDay(input.zone).toInstant().toEpochMilli()
            WorkoutSession(id = n.toLong(), label = "Trial", startedAtMs = at) to emptyList<SessionSet>()
        }
        val months = WebSnapshotBuilder.monthEntries(input.copy(history = history))
        assertEquals(24, months.size)
        assertEquals(month.minusMonths(23).toString(), months.keys.first())
    }
    @Test fun activityHasNoLiftEntry() {
        val input = WebSnapshotFixtures.input()
        assertFalse(WebSnapshotBuilder.liftEntries(input).any { it.name == "Run" })
    }
    @Test fun importedWorkoutsAreCounted() {
        val input = WebSnapshotFixtures.input()
        val imported = input.history.first { it.first.imported }.first
        assertEquals(0, WebSnapshotBuilder.sessionEntries(input).getValue(imported.id).xp)
        assertTrue(WebSnapshotBuilder.liftEntries(input).any { lift -> lift.variations.any { v -> v.points.any { it[1].toString() == imported.id.toString() } } })
    }
    private fun heavyPullInput(): SnapshotInput {
        val base = WebSnapshotFixtures.input()
        val exercise = Exercise(10, "Pull-up", MuscleGroup.PULL, false)
        val history = listOf(3L, 1L).mapIndexed { index, days ->
            WorkoutSession(id = index + 1L, label = "Pull", startedAtMs = base.nowMs - days * 86_400_000) to
                (0 until 5).map { SessionSet(exerciseId = 10, exerciseName = "Pull-up", setIndex = it,
                    reps = 8, weightKg = 10.0, rpe = 9, done = true) }
        }
        return base.copy(history = history, exercises = mapOf(10L to exercise))
    }
    @Test fun fatigueLevelsMatchTheModel() {
        val input = heavyPullInput()
        val report = FatigueModel.estimate(FatigueInput.sessions(input.history, null, input.exercises, input.nowMs), input.nowMs, FatigueInput.firstWorkoutMs(input.history, null))
        val section = WebSnapshotBuilder.fatigueSection(input)
        Muscle.entries.forEach { assertEquals(FatigueLevel.of(report.residual(it)).ordinal, section.levels.getValue(it.name)) }
        assertEquals(report.mostWorked(input.zone).map { it.muscle.name }, section.worked.map { it.muscle })
        assertEquals(input.nowMs, section.atMs)
    }
    @Test fun fatigueMutedWhenNoHistory() {
        val input = WebSnapshotFixtures.input().copy(history = emptyList())
        val fatigue = WebSnapshotBuilder.fatigueSection(input)
        assertFalse(fatigue.hasHistory); assertTrue(fatigue.worked.isEmpty()); assertTrue(fatigue.levels.values.all { it == 0 })
    }
    @Test fun fatigueCauseUsesInjectedText() {
        val input = heavyPullInput().copy(fatigueText = object : FatigueText {
            override fun cause(c: CauseShare, nowMs: Long, zone: java.time.ZoneId) = "cause:${c.exerciseName}"
            override fun freshBy(by: LightBy): String? = null
        })
        val worked = WebSnapshotBuilder.fatigueSection(input).worked
        assertTrue(worked.isNotEmpty()); assertTrue(worked.first().cause!!.startsWith("cause:"))
    }
    @Test fun fatigueIgnoresSessionsOlderThan28Days() {
        val input = WebSnapshotFixtures.input()
        val old = input.copy(history = input.history.filter { it.first.startedAtMs < input.nowMs - 28L * 86_400_000 })
        val result = WebSnapshotBuilder.fatigueSection(old)
        assertTrue(result.hasHistory)
        assertTrue(result.worked.isEmpty())
        assertTrue(result.levels.values.all { it == 0 })
    }
    @Test fun streakUsesCurrentAndLongest() {
        val input = WebSnapshotFixtures.input()
        val today = java.time.Instant.ofEpochMilli(input.nowMs).atZone(input.zone).toLocalDate()
        val dates = input.history.map { java.time.Instant.ofEpochMilli(it.first.completedAtMs!!).atZone(input.zone).toLocalDate() }.toSet()
        val profile = WebSnapshotBuilder.profileAndDeeds(input).first
        assertEquals(Streak.current(dates, today), profile.streakDays)
        assertEquals(Streak.longest(dates, today), profile.longestStreakDays)
    }
    @Test fun groupsSumToTheWeeksHardSets() {
        val trends = WebSnapshotBuilder.weekAndTrends(WebSnapshotFixtures.input()).second
        trends.weeks.forEach { assertEquals(it.hardSets, it.groups.values.sum(), 0.05) }
    }
    @Test fun groupLevelsTakeTheMaximumPerGroup() {
        val input = WebSnapshotFixtures.input()
        WebSnapshotBuilder.sessionEntries(input).values.forEach { session ->
            session.groupLevels.forEach { (name, level) ->
                val expected = session.muscleLevels.filterKeys { WebSnapshotTuning.GROUPS.getValue(Muscle.valueOf(it)).name == name }.values.max()
                assertEquals(expected, level)
            }
        }
    }
    @Test fun variationsKeepSeparateSeries() {
        val pull = WebSnapshotBuilder.liftEntries(WebSnapshotFixtures.input()).first { it.name == "Pull-up" }
        assertTrue(pull.variations.any { it.key.isEmpty() })
        assertTrue(pull.variations.any { it.key.contains("archer", ignoreCase = true) })
        assertTrue(pull.variations.all { it.points.isNotEmpty() })
    }
    @Test fun customExerciseHasNoArt() {
        assertNull(WebSnapshotBuilder.liftEntries(WebSnapshotFixtures.input()).first { it.name == "Fictional lift" }.art)
    }
    @Test fun prTagsNeverCarryE1rm() {
        val tag = WebSnapshot.PrTag("Pull-up", 6, 12.5, null)
        val encoded = kotlinx.serialization.json.Json.encodeToJsonElement(WebSnapshot.PrTag.serializer(), tag).jsonObject
        assertEquals(setOf("label", "reps", "weightKg", "durationSec"), encoded.keys)
    }
    @Test fun e1rmCountsBodyWeightForBodyweightLifts() {
        val exercise = Exercise(10, "Pull-up", MuscleGroup.PULL, false)
        val sets = listOf(SessionSet(exerciseId = 10, exerciseName = exercise.name, setIndex = 0,
            reps = 6, weightKg = 12.5, modifiers = "Weighted", done = true))
        val base = single(sets, listOf(exercise))
        val input = base.copy(stats = listOf(StatEntry(takenAtMs = base.nowMs - 86_400_000, weightKg = 80.0, heightCm = 180.0)))
        // Epley: (80 + 12.5) * (1 + 6/30) - 80 = 31 kg.
        assertEquals(31.0, WebSnapshotBuilder.sessionEntries(input).getValue(99).e1rm.getValue("Pull-up#0"), 0.01)
    }
    @Test fun noWeighInFallsBackToAddedLoad() {
        val exercise = Exercise(10, "Pull-up", MuscleGroup.PULL, false)
        val sets = listOf(SessionSet(exerciseId = 10, exerciseName = exercise.name, setIndex = 0,
            reps = 6, weightKg = 12.5, modifiers = "Weighted", done = true))
        assertEquals(15.0, WebSnapshotBuilder.sessionEntries(single(sets, listOf(exercise))).getValue(99).e1rm.getValue("Pull-up#0"), 0.01)
    }
    @Test fun peaksAreJudgedAgainstEarlierTrialsOnly() {
        val exercise = Exercise(10, "Pull-up", MuscleGroup.PULL, false)
        val input = single(emptyList(), listOf(exercise))
        val history = listOf(5, 8, 6).mapIndexed { index, reps ->
            WorkoutSession(id = index + 1L, label = "Pull", startedAtMs = input.nowMs - (3 - index) * 86_400_000L) to
                listOf(SessionSet(exerciseId = 10, exerciseName = "Pull-up", setIndex = 0, reps = reps, done = true))
        }
        val sessions = WebSnapshotBuilder.sessionEntries(input.copy(history = history))
        assertEquals(0, sessions.getValue(1).peaks)
        assertEquals(1, sessions.getValue(2).peaks)
        assertEquals(0, sessions.getValue(3).peaks)
        assertEquals(listOf("Pull-up#0"), sessions.getValue(2).peakSets)
    }
    @Test fun prTagsNameLiftAndLoadAsLogged() {
        val exercise = Exercise(10, "Pull-up", MuscleGroup.PULL, false)
        fun row(reps: Int) = SessionSet(exerciseId = 10, exerciseName = "Pull-up", setIndex = 0,
            reps = reps, weightKg = 12.5, modifiers = "Weighted", done = true)
        val history = listOf(WorkoutSession(id = 1, label = "Pull", startedAtMs = 0) to listOf(row(5)))
        val book = RepMax.Ledger(history) { ExerciseMetric.REPS }
        val tag = WebSnapshotBuilder.prTags(listOf(row(6)), book.first(1), mapOf(10L to exercise)).single()
        assertEquals(Variation.label("Pull-up", Variation.key("Weighted")), tag.label)
        assertEquals(6, tag.reps)
        assertEquals(12.5, tag.weightKg!!, 0.0)
    }
    @Test fun monthEntryCountsWorkoutsAndTopLifts() {
        val october = WebSnapshotBuilder.monthEntries(WebSnapshotFixtures.input()).getValue("2026-10")
        assertEquals(9, october.workouts)
        assertEquals("Pull-up", october.topLifts.first().name)
        assertEquals(6, october.topLifts.first().sessions)
    }
    @Test fun deedsListEarnedOnlyNewestFirstWithTotals() {
        val input = WebSnapshotFixtures.input()
        val titles = Titles.ALL.take(2)
        val result = WebSnapshotBuilder.profileAndDeeds(input.copy(titles = listOf(
            UnlockedTitle(titles[0].id, 10), UnlockedTitle(titles[1].id, 20))))
        assertEquals(Titles.ALL.size, result.first.deedsTotal)
        assertEquals(2, result.first.deedsEarned)
        assertEquals(listOf(titles[1].id, titles[0].id), result.second.map { it.id })
        assertEquals(Titles.category(titles[1].rule), result.second.first().category)
    }
    @Test fun weekCountsOnlyThisCalendarWeekInZone() {
        val input = WebSnapshotFixtures.input()
        val at = java.time.Instant.parse("2026-10-25T23:30:00Z").toEpochMilli()
        val history = listOf(WorkoutSession(id = 1, label = "Sunday", startedAtMs = at, xpAwarded = 17) to emptyList<SessionSet>())
        val now = java.time.Instant.parse("2026-10-26T12:00:00Z").toEpochMilli()
        val sydney = WebSnapshotBuilder.weekAndTrends(input.copy(history = history, nowMs = now, zone = java.time.ZoneId.of("Australia/Sydney"))).first
        val utc = WebSnapshotBuilder.weekAndTrends(input.copy(history = history, nowMs = now, zone = java.time.ZoneId.of("UTC"))).first
        assertEquals(1, sydney.workouts); assertEquals(17, sydney.xp); assertEquals(0, utc.workouts)
    }
    @Test fun prFlagMarksOnlyNewBests() {
        val exercise = Exercise(10, "Pull-up", MuscleGroup.PULL, false)
        val input = single(emptyList(), listOf(exercise))
        val history = listOf(5, 5, 6).mapIndexed { index, reps ->
            WorkoutSession(id = index + 1L, label = "Pull", startedAtMs = input.nowMs - (3 - index) * 86_400_000L) to
                listOf(SessionSet(exerciseId = 10, exerciseName = "Pull-up", setIndex = 0, reps = reps, weightKg = 10.0, done = true))
        }
        val points = WebSnapshotBuilder.liftEntries(input.copy(history = history)).single().variations.single().points
        assertEquals(listOf("0", "0", "1"), points.map { it[3].toString() })
    }
    @Test fun repeatedMovementKeysMatchWireIndexes() {
        val exercise = Exercise(10, "Push-up", MuscleGroup.PUSH, false)
        val sets = (0..1).flatMap { block -> (0..2).map { index ->
            SessionSet(exerciseId = 10, exerciseName = "Push-up", exercisePosition = block, setIndex = index, reps = 8, done = true)
        } }
        val entry = WebSnapshotBuilder.sessionEntries(single(sets, listOf(exercise))).getValue(99)
        assertEquals((0..5).map { "Push-up#$it" }.toSet(), entry.e1rm.keys)
    }
    @Test fun liftCarriesArtAndMuscleSummary() {
        val input = WebSnapshotFixtures.input()
        val pull = WebSnapshotBuilder.liftEntries(input).first { it.name == "Pull-up" }
        assertEquals(ExerciseArtIndex.forName("Pull-up")!!.id.removePrefix("own_"), pull.art)
        assertEquals(com.ironvellum.app.ui.program.exerciseMuscleSummary(MuscleMap.profile("Pull-up")!!.muscles), pull.muscleSummary)
    }
    @Test fun holdIsJudgedBySeconds() {
        val exercise = Exercise(10, "Plank", MuscleGroup.CORE, false, ExerciseMetric.HOLD)
        val base = single(emptyList(), listOf(exercise))
        val history = listOf(20, 40).mapIndexed { index, seconds ->
            WorkoutSession(id = index + 1L, label = "Hold", startedAtMs = base.nowMs - (2 - index) * 86_400_000L) to
                listOf(SessionSet(exerciseId = 10, exerciseName = "Plank", setIndex = 0, reps = 0, durationSec = seconds, done = true))
        }
        val stats = listOf(StatEntry(takenAtMs = base.nowMs - 10 * 86_400_000L, weightKg = 80.0, heightCm = 180.0))
        val peak = WebSnapshotBuilder.liftEntries(base.copy(history = history, stats = stats)).single().variations.single().peak!!
        assertEquals(40, peak.durationSec!!)
        assertEquals(0, peak.reps)
    }
    @Test fun holdChartCarriesPhoneStrengthScoreNotSeconds() {
        val plank = WebSnapshotBuilder.liftEntries(WebSnapshotFixtures.input())
            .first { it.name == "Plank" }.variations.single()
        assertEquals("strength", plank.chartMetric)
        assertEquals("score", plank.chartUnit)
        assertEquals(33.97, plank.points.first()[2].jsonPrimitive.double, 0.001)
        assertEquals(40, plank.peak!!.durationSec!!)
    }
    @Test fun mixedVariationChartUnitsFollowEachPhoneSeries() {
        val pull = WebSnapshotBuilder.liftEntries(WebSnapshotFixtures.input()).first { it.name == "Pull-up" }
        val weighted = pull.variations.first { it.key.isEmpty() }
        val archer = pull.variations.first { it.key == "archer" }
        assertEquals("e1rm", weighted.chartMetric)
        assertEquals("kg", weighted.chartUnit)
        assertEquals("strength", archer.chartMetric)
        assertEquals("score", archer.chartUnit)
        assertTrue(weighted.points.isNotEmpty())
        assertTrue(archer.points.isNotEmpty())
    }
    @Test fun windowSevenMarksStateByFloorAndHeavy() {
        assertEquals("none", WebSnapshotBuilder.windowState(0.0))
        assertEquals("below", WebSnapshotBuilder.windowState(3.9))
        assertEquals("covered", WebSnapshotBuilder.windowState(4.0))
        assertEquals("heavy", WebSnapshotBuilder.windowState(20.0))
        val exercise = Exercise(10, "Pull-up", MuscleGroup.PULL, false)
        val sets = (0..3).map { SessionSet(exerciseId = 10, exerciseName = "Pull-up", setIndex = it, reps = 8, done = true) }
        val window = WebSnapshotBuilder.weekAndTrends(single(sets, listOf(exercise))).second.windows.getValue("7")
        assertEquals(4.0, window.perWeek.getValue("LATS"), 0.0)
        assertEquals("covered", window.state.getValue("LATS"))
    }
    @Test fun threeYearBuildIsFast() {
        val input = WebSnapshotFixtures.input()
        val exercise = Exercise(10, "Pull-up", MuscleGroup.PULL, false)
        val history = (0 until 3 * 52 * 4).map { n ->
            val at = input.nowMs - (3L * 52 * 4 - n) * 7 * 86_400_000 / 4
            WorkoutSession(id = n + 1L, label = "Pull", startedAtMs = at) to
                (0 until 4).map { index -> SessionSet(exerciseId = 10, exerciseName = "Pull-up", setIndex = index,
                    reps = 8, done = true) }
        }
        val start = System.nanoTime()
        WebSnapshotBuilder.build(input.copy(history = history, exercises = mapOf(10L to exercise)))
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("Building three years took $elapsedMs ms", elapsedMs < 5_000)
    }
    @Test fun recentRecordsAreLiftGainsInLast30Days() {
        val base = WebSnapshotFixtures.input()
        val exercise = Exercise(10, "Pull-up", MuscleGroup.PULL, false)
        val history = listOf(40L to 10.0, 5L to 15.0).mapIndexed { index, (days, load) ->
            val at = base.nowMs - days * 86_400_000
            WorkoutSession(id = index + 1L, label = "Pull", startedAtMs = at) to
                listOf(SessionSet(exerciseId = 10, exerciseName = "Pull-up", setIndex = 0,
                    reps = 5, weightKg = load, done = true))
        }
        val trends = WebSnapshotBuilder.weekAndTrends(base.copy(history = history, exercises = mapOf(10L to exercise))).second
        assertEquals(1, trends.records30d)
        assertEquals(2L, trends.recent.single().localId)
        assertEquals("Pull-up", trends.recent.single().name)
        assertEquals(1, trends.recordsPerMonth.single { it.month == "2026-10" }.count)
        assertEquals(0, trends.recordsPerMonth.single { it.month == "2026-09" }.count)
    }
    @Test fun assistedAndNegativePeaksKeepTheirSignAndDuration() {
        val pull = WebSnapshotBuilder.liftEntries(WebSnapshotFixtures.input()).first { it.name == "Pull-up" }
        val assisted = pull.variations.first { it.key.contains("assisted", ignoreCase = true) }
        assertEquals(-20.0, assisted.peak!!.weightKg!!, 0.0)
        val exercise = Exercise(11, "Negative Pull-up", MuscleGroup.PULL, false)
        val sets = listOf(SessionSet(exerciseId = 11, exerciseName = exercise.name, setIndex = 0,
            reps = 5, durationSec = 5, done = true))
        val base = single(sets, listOf(exercise))
        val input = base.copy(stats = listOf(StatEntry(takenAtMs = base.nowMs - 86_400_000, weightKg = 80.0, heightCm = 180.0)))
        val negative = WebSnapshotBuilder.liftEntries(input).single().variations.single()
        assertEquals(5, negative.peak!!.durationSec!!)
    }
    @Test fun setRecordsAreBestPerPosition() {
        val input = WebSnapshotFixtures.input()
        val expected = SetRecords.recordsOf("Pull-up", input.history, SetRecords.bodyweightLookup(input.stats)) {
            input.exercises[it.exerciseId]?.metric
        }
        val lift = WebSnapshotBuilder.liftEntries(input).first { it.name == "Pull-up" }
        lift.variations.forEach { variation ->
            val wanted = expected.values.filter { it.variation == variation.key }.sortedBy { it.setIndex }
                .map { WebSnapshot.SetRecord(it.setIndex, it.reps, it.weightKg, it.achievedAtMs) }
            assertEquals(wanted, variation.setRecords)
        }
    }
    private fun routines(input: SnapshotInput = WebSnapshotFixtures.input()) = WebSnapshotBuilder.routinesSection(input)
    private fun plain(block: () -> Unit) { val old = com.ironvellum.app.data.Words.themed; com.ironvellum.app.data.Words.useForTest(false); try { block() } finally { com.ironvellum.app.data.Words.useForTest(old) } }

    @Test fun routineEntriesKeepOrderAndWarmups() = plain {
        val upperB = routines().presets.first { it.name == "Upper B" }
        assertEquals(listOf("Plank", "Pull-up", "Pull-up", "Push-up", "Weighted Dip"), upperB.entries.map { it.name })
        assertEquals(listOf(0, 1, 2, 3, 4), upperB.entries.map { it.position })
        assertEquals(listOf(true, false, false, false, false), upperB.entries.map { it.warmup })
    }
    @Test fun schemeLineIsThePhonesOwn() = plain {
        val upperB = routines().presets.first { it.name == "Upper B" }
        val top = upperB.entries[1]
        assertEquals(SchemeText.summary(SetScheme(SchemeType.TOP_BACKOFF, backoffSets = 3), 4), top.scheme)
        assertNull(upperB.entries[3].scheme)
        assertEquals(SchemeText.figure(SetScheme(SchemeType.TOP_BACKOFF, topReps = 3..5, backoffSets = 3, backoffPercent = 10, backoffReps = 4..6), 4, 4, 12.5, { Loads.withUnit(it, LoadUnit.KG) }, MovementDifficulty.isHoldByName("Pull-up")), top.figure)
        assertEquals("Weighted", top.label); assertEquals("", upperB.entries[3].label)
    }
    @Test fun figureUsesTheSnapshotUnit() = plain {
        val figure = routines(WebSnapshotFixtures.input().copy(unit = LoadUnit.LB)).presets.first { it.name == "Lower A" }.entries[0].figure
        assertEquals("4 × 6 @" + Loads.withUnit(80.0, LoadUnit.LB), figure)
        assertTrue(figure, figure.contains("lb"))
    }
    @Test fun holdRepsReadAsSeconds() = plain {
        val plank = routines().presets.first { it.name == "Lower A" }.entries[1]
        assertEquals(SchemeText.figure(SetScheme(), 3, 40, null, null, hold = MovementDifficulty.isHoldByName("Plank")), plank.figure)
    }
    @Test fun supersetGroupIsCarried() = plain {
        val upperB = routines().presets.first { it.name == "Upper B" }
        assertEquals(listOf(null, null, null, 1, 1), upperB.entries.map { it.supersetGroup })
    }
    @Test fun scheduledDayIsCarriedAndUnscheduledIsNull() = plain {
        val byName = routines().presets.associateBy { it.name }
        assertEquals(listOf(1, 3, 5, 7, null), listOf("Upper A", "Lower A", "Upper B", "Lower B", "Mobility").map { byName.getValue(it).day })
    }
    @Test fun minutesAndHardSetsMatchTheRulesObject() = plain {
        val input = WebSnapshotFixtures.input()
        val lowerB = routines(input).presets.first { it.name == "Lower B" }
        val planned = PlannedPreset("Lower B", "", 7, listOf(PlannedEntry("Back Squat", 5, 5, 90.0)))
        val pace = SessionClock.pace(input.history).secondsPerSet(input.presets.first { it.name == "Lower B" })
        val seconds = SessionClock.planSeconds(planned.entries, input.focus, pace) { input.exercises.values.firstOrNull { e -> e.name == it.exerciseName }?.metric }
        assertEquals(SessionClock.minutes(seconds), lowerB.minutes)
        assertEquals(5, lowerB.hardSets)
        assertEquals(ProgramRules.weeklyVolume(listOf(planned)).filterValues { it > 0 }.mapKeys { it.key.name }.mapValues { FeedCardJson.level(it.value) }, lowerB.levels)
    }
    @Test fun weekCoversMatchesCoverageTally() = plain {
        val input = WebSnapshotFixtures.input(); val week = routines(input).week
        val tally = com.ironvellum.app.ui.program.coverageTally(input.presets.filter { it.scheduledDay != null }.map { p ->
            PlannedPreset(p.name, p.note, p.scheduledDay, p.working.map { PlannedEntry(it.exerciseName, it.targetSets, it.targetReps, it.targetWeightKg, it.modifiers) })
        })
        assertEquals(com.ironvellum.app.ui.program.coverageHeadline(tally), week.headline)
        val expected = com.ironvellum.app.ui.program.JUDGED_MUSCLES.associate { m ->
            m.name to when (tally.states.getValue(m)) {
                com.ironvellum.app.ui.program.CoverageState.COVERED -> "covered"; com.ironvellum.app.ui.program.CoverageState.BELOW_MINIMUM -> "below"
                com.ironvellum.app.ui.program.CoverageState.HEAVY -> "heavy"; else -> "none"
            }
        }
        assertEquals(expected, week.covers)
        assertEquals(4, week.days); assertEquals(6 + 7 + 13 + 5, week.hardSets)
    }
    @Test fun customExerciseCarriesMusclesAndGear() = plain {
        val custom = routines().custom
        assertEquals(listOf("Fictional lift", "Fictional press"), custom.map { it.name })
        val press = custom[1]
        assertEquals(mapOf("MID_CHEST" to "main", "TRICEPS" to "assist"), press.muscles)
        assertEquals(listOf(CustomNeed.BARBELL.label, CustomNeed.BENCH.label), press.needs)
        assertEquals(emptyMap<String, String>(), custom[0].muscles)
    }
    @Test fun emptyPresetsGiveEmptyRoutines() = plain {
        val r = routines(WebSnapshotFixtures.input().copy(presets = emptyList()))
        assertEquals(emptyList<WebSnapshot.RoutinePreset>(), r.presets); assertEquals(0, r.week.days); assertEquals(0, r.week.hardSets)
        assertTrue(r.week.covers.values.all { it == "none" })
    }
    @Test fun weekPlannedDaysAgreesWithRoutines() = plain {
        val input = WebSnapshotFixtures.input()
        assertEquals(routines(input).presets.mapNotNull { it.day }.distinct().size, WebSnapshotBuilder.weekAndTrends(input).first.plannedDays)
    }
    @Test fun prTagsCapAtSix() {
        val exercises = (1L..7L).associateWith { Exercise(it, "Lift $it", MuscleGroup.PULL, true) }
        fun sets(reps: Int) = exercises.values.map {
            SessionSet(exerciseId = it.id, exerciseName = it.name, exercisePosition = it.id.toInt(),
                setIndex = 0, reps = reps, weightKg = 10.0, done = true)
        }
        val history = listOf(WorkoutSession(id = 1, label = "Trial", startedAtMs = 0) to sets(5))
        val ledger = RepMax.Ledger(history) { ExerciseMetric.REPS }
        assertEquals(6, WebSnapshotBuilder.prTags(sets(6), ledger.first(1), exercises).size)
    }
}
