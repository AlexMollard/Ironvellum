package com.ironvellum.app.domain

import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Eight realistic training sessions (a push, a pull and a leg rotation with real progressions: weighted
 * pull-ups, holds, loaded push-ups, single-leg lifts), anonymised: the exercises, sets, reps, loads and
 * ordering are as logged, the dates are synthetic but keep the same weekdays and gaps, there are no labels
 * or notes, and the bodyweight is fixed at 79 kg. Each is priced by the old load-weighted rule and by the
 * effort rule; the table is written to build/xp-calibration.txt, and the worked example the owner agreed to
 * is held here (the heavier 4/3/3/2 pull day pays more pull-up XP than the earlier 5x5 one).
 */
class EffortXpSessionsTest {

    private val bodyweight = 79.0

    private class Row(val session: Long, val pos: Int, val idx: Int, val name: String, val metric: String,
                      val reps: Int, val weight: Double?, val mods: String, val seconds: Int?, val weighted: Boolean)
    private class Sess(val id: Long, val start: Long, val end: Long, val rows: MutableList<Row> = mutableListOf())

    private val sessions: List<Sess> = run {
        val out = mutableListOf<Sess>()
        for (line in DATA.lines().filter { it.isNotBlank() }) {
            val f = line.split("|")
            if (f[0] == "S") out += Sess(f[1].toLong(), f[2].toLong(), f[3].toLong())
            else out.last().rows += Row(f[1].toLong(), f[2].toInt(), f[3].toInt(), f[4], f[5], f[6].toInt(),
                f[7].toDoubleOrNull(), f[8], f[9].toIntOrNull(), f[10] == "1")
        }
        out
    }

    private val exercises: Map<Long, Exercise> = sessions.flatMap { it.rows }.map { it.name }.distinct()
        .mapIndexed { i, n ->
            val r = sessions.flatMap { it.rows }.first { it.name == n }
            (i + 1).toLong() to Exercise(
                id = (i + 1).toLong(), name = n, muscleGroup = MuscleGroup.CORE, isWeighted = r.weighted,
                metric = ExerciseMetric.valueOf(r.metric),
            )
        }.toMap()

    private fun idOf(name: String) = exercises.values.first { it.name == name }.id

    private fun domainSession(s: Sess) = WorkoutSession(id = s.id, label = "", startedAtMs = s.start, completedAtMs = s.end) to
        s.rows.map { r ->
            SessionSet(
                exerciseId = idOf(r.name), exerciseName = r.name, exercisePosition = r.pos, setIndex = r.idx,
                reps = r.reps, weightKg = r.weight, modifiers = r.mods, done = true, durationSec = r.seconds,
            )
        }

    private fun legacySetXp(set: Xp.SetEffort): Int {
        val hold = MovementDifficulty.isHoldSet(set.metric, set.exerciseName, set.modifiers)
        val raw = if (hold) MovementDifficulty.holdRepEquivalents(set.holdSeconds ?: set.reps) else set.reps.toDouble()
        if (raw <= 0.0) return 0
        val marked = (set.weightKg ?: 0.0).coerceAtLeast(0.0)
        val mult = if (marked <= 0.0) 1.0
        else ((bodyweight + marked * MovementDifficulty.loadFactor(set.exerciseName)) / bodyweight).pow(3.0).coerceAtMost(3.0)
        val units = MovementDifficulty.taperedVolume(raw * mult) *
            MovementDifficulty.intensity(set.exerciseName) * MovementDifficulty.modifierFactor(set.modifiers)
        return 5 + (0.8 * units).roundToInt()
    }

    private fun book(s: Sess) = SessionXp(
        exercises,
        sessions.takeWhile { it.id != s.id }.map(::domainSession),
        bodyweightAt = { bodyweight },
        excludeSessionId = s.id,
    )

    private fun newBreakdown(s: Sess): Xp.Breakdown {
        val (session, sets) = domainSession(s)
        val book = book(s)
        val facts = book.factsFor(session, sets, planned = null, quest = false, zone = java.time.ZoneOffset.UTC)
        return book.breakdown(sets, bodyweight, facts, doneSetCount = sets.size)
    }

    private fun pullUpXp(s: Sess): Int = newBreakdown(s).of(Xp.Kind.EXERCISE).first { it.label == "Pull-up" }.xp

    @Test
    fun oldAgainstNewForEverySession() {
        val table = StringBuilder("session  oldFormula  new  breakdown\n")
        for (s in sessions) {
            val old = s.rows.sumOf { r ->
                legacySetXp(Xp.SetEffort(r.name, r.reps, r.seconds.takeIf { r.metric == "HOLD" }, r.weight, r.mods,
                    ExerciseMetric.valueOf(r.metric)))
            } + Xp.COMPLETION_BONUS
            val bd = newBreakdown(s)
            val exerciseOnly = bd.lines.filter { it.kind == Xp.Kind.EXERCISE }.sumOf { it.xp }
            table.append("%7d  %10d  %3d  sets-only %d vs %d  %s\n".format(s.id, old, bd.total, exerciseOnly, old - Xp.COMPLETION_BONUS, bd.describe()))
        }
        val weekCoveredOn = sessions.filter { newBreakdown(it).of(Xp.Kind.WEEK_COVERED).isNotEmpty() }.map { it.id }
        table.append("week covered fires on sessions: $weekCoveredOn\n")
        // How near each week comes: the required muscles still short of 4 hard sets.
        val zone = java.time.ZoneOffset.UTC
        val weeks = sessions.groupBy {
            java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate()
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        }
        for ((monday, week) in weeks) {
            val logged = week.flatMap { s ->
                domainSession(s).second.map {
                    LoggedSet(it.exerciseName, it.modifiers, it.weightKg, if (it.durationSec != null) maxOf(1, it.durationSec!! / 5) else it.reps,
                        it.rpe, it.kind, true, s.start, bodyweight = !exercises.getValue(it.exerciseId).isWeighted)
                }
            }
            val load = HardSets.fromLog(logged, monday.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
            val short = Xp.WEEK_COVERED_MUSCLES.filter { (load[it] ?: 0.0) < ProgramRules.COVERED_MIN }
            table.append("week of %s: %d of %d required muscles at 4+ hard sets; short: %s\n".format(monday, Xp.WEEK_COVERED_MUSCLES.size - short.size, Xp.WEEK_COVERED_MUSCLES.size, short.joinToString { it.name }))
        }
        File("build/xp-calibration.txt").also { it.parentFile.mkdirs() }.writeText(table.toString())
        println(table)
    }

    @Test
    fun theHeavierFourThreeThreeTwoPullDayPaysMorePullUpXpThanTheEarlierFiveByFive() {
        val earlier = sessions.first { s -> s.rows.any { it.name == "Pull-up" && it.reps == 5 && it.weight == 15.0 } }
        val heavier = sessions.first { s -> s.rows.any { it.name == "Pull-up" && it.weight == 17.7 } }
        assertTrue("${pullUpXp(heavier)} vs ${pullUpXp(earlier)}", pullUpXp(heavier) > pullUpXp(earlier))
    }

    @Test
    fun theHeavierPullDayIsAHeavierPeakThatPaysInFullAgainstEverySessionBeforeIt() {
        val heavier = sessions.first { s -> s.rows.any { it.name == "Pull-up" && it.weight == 17.7 } }
        val (_, sets) = domainSession(heavier)
        val peak = SessionPeaks.of(sets, book(heavier).peakBook, bodyweight) { exercises[it.exerciseId] }
            .first { it.exerciseName == "Pull-up" }
        assertEquals(RepMax.Kind.HEAVIER, peak.kind)
        assertEquals(Xp.PEAK_BONUS, peak.xp)
        // The bonus is in the award, once, under the lift's name.
        assertEquals(Xp.PEAK_BONUS, newBreakdown(heavier).of(Xp.Kind.PEAK).single().xp)
    }

    private companion object {
        val DATA = (
        "S|1|1931911200000|1931914140000\n" +
        "T|1|0|0|Handstand Push-up|REPS|6||||0\n" +
        "T|1|0|1|Handstand Push-up|REPS|7||||0\n" +
        "T|1|0|2|Handstand Push-up|REPS|5||||0\n" +
        "T|1|1|0|Archer Push-up|REPS|7|7.5|weighted, deficit, elevated||0\n" +
        "T|1|1|1|Archer Push-up|REPS|7|7.5|weighted, deficit, elevated||0\n" +
        "T|1|1|2|Archer Push-up|REPS|7|7.5|weighted, deficit, elevated||0\n" +
        "T|1|2|0|Push-up|REPS|10|7.5|deficit, elevated, weighted||0\n" +
        "T|1|2|1|Push-up|REPS|9|7.5|deficit, elevated, weighted||0\n" +
        "T|1|2|2|Push-up|REPS|7|7.5|deficit, elevated, weighted||0\n" +
        "T|1|3|0|Hollow Hold|HOLD|0|||45|0\n" +
        "T|1|3|1|Hollow Hold|HOLD|0|||30|0\n" +
        "S|2|1932714000000|1932718020000\n" +
        "T|2|0|0|Pull-up|REPS|5|15.2|weighted||0\n" +
        "T|2|0|1|Pull-up|REPS|5|15.2|weighted||0\n" +
        "T|2|0|2|Pull-up|REPS|5|15.2|weighted||0\n" +
        "T|2|0|3|Pull-up|REPS|3|15.2|weighted||0\n" +
        "T|2|1|0|Chin-up|REPS|8||||0\n" +
        "T|2|1|1|Chin-up|REPS|8||||0\n" +
        "T|2|1|2|Chin-up|REPS|8||||0\n" +
        "T|2|2|0|Dumbbell Row|REPS|8|24.0|||1\n" +
        "T|2|2|1|Dumbbell Row|REPS|8|24.0|||1\n" +
        "T|2|2|2|Dumbbell Row|REPS|8|24.0|||1\n" +
        "T|2|2|3|Dumbbell Row|REPS|8|24.0|||1\n" +
        "T|2|3|0|Lateral Raise|REPS|10|12.5|||1\n" +
        "T|2|3|1|Lateral Raise|REPS|9|12.5|||1\n" +
        "T|2|3|2|Lateral Raise|REPS|10|10.0|||1\n" +
        "T|2|4|0|Ab Wheel Rollout|REPS|8||||0\n" +
        "T|2|4|1|Ab Wheel Rollout|REPS|8||||0\n" +
        "T|2|4|2|Ab Wheel Rollout|REPS|8||||0\n" +
        "S|3|1932886800000|1932890280000\n" +
        "T|3|0|0|Knee-to-Wall Dorsiflexion|REPS|10||||0\n" +
        "T|3|0|1|Knee-to-Wall Dorsiflexion|REPS|10||||0\n" +
        "T|3|0|2|Knee-to-Wall Dorsiflexion|REPS|10||||0\n" +
        "T|3|1|0|Pistol Squat|REPS|8|7.7|weighted||0\n" +
        "T|3|1|1|Pistol Squat|REPS|8|7.7|weighted||0\n" +
        "T|3|1|2|Pistol Squat|REPS|8|7.7|weighted||0\n" +
        "T|3|2|0|Shrimp Squat|REPS|7|7.7|weighted||0\n" +
        "T|3|2|1|Shrimp Squat|REPS|3|7.7|weighted||0\n" +
        "T|3|2|2|Shrimp Squat|REPS|7||weighted||0\n" +
        "T|3|3|0|Single-Leg Glute Bridge|REPS|10|30.0|weighted||0\n" +
        "T|3|3|1|Single-Leg Glute Bridge|REPS|10|30.0|weighted||0\n" +
        "T|3|3|2|Single-Leg Glute Bridge|REPS|10|30.0|weighted||0\n" +
        "T|3|4|0|Single-Leg Romanian Deadlift|REPS|10|24.0|||1\n" +
        "T|3|4|1|Single-Leg Romanian Deadlift|REPS|10|24.0|||1\n" +
        "T|3|5|0|Single-Leg Calf Raise|REPS|10|30.0|weighted||0\n" +
        "T|3|5|1|Single-Leg Calf Raise|REPS|10|37.0|weighted||0\n" +
        "T|3|5|2|Single-Leg Calf Raise|REPS|10|37.0|weighted||0\n" +
        "T|3|6|0|Hanging Knee Raise|REPS|10||||0\n" +
        "T|3|7|0|Hanging Leg Raise|REPS|10||||0\n" +
        "T|3|7|1|Hanging Leg Raise|REPS|10||||0\n" +
        "S|4|1933203600000|1933208340000\n" +
        "T|4|0|0|Wall HSPU|REPS|5||||0\n" +
        "T|4|0|1|Wall HSPU|REPS|4||||0\n" +
        "T|4|0|2|Wall HSPU|REPS|5||||0\n" +
        "T|4|2|0|Archer Push-up|REPS|7|7.7|weighted, deficit, elevated||0\n" +
        "T|4|2|1|Archer Push-up|REPS|7|7.7|weighted, deficit, elevated||0\n" +
        "T|4|2|2|Archer Push-up|REPS|7|7.7|weighted, deficit, elevated||0\n" +
        "T|4|3|0|Tuck Planche|HOLD|0|||5|0\n" +
        "T|4|3|1|Tuck Planche|HOLD|0|||5|0\n" +
        "T|4|3|2|Tuck Planche|HOLD|0|||5|0\n" +
        "T|4|4|0|Push-up|REPS|12|7.7|weighted, deficit, elevated||0\n" +
        "T|4|4|1|Push-up|REPS|12|7.7|weighted, deficit, elevated||0\n" +
        "T|4|4|2|Push-up|REPS|12|7.7|weighted, deficit, elevated||0\n" +
        "T|4|5|0|Ab Wheel Rollout|REPS|8||||0\n" +
        "T|4|5|1|Ab Wheel Rollout|REPS|8||||0\n" +
        "T|4|5|2|Ab Wheel Rollout|REPS|8||||0\n" +
        "S|5|1933315200000|1933318860000\n" +
        "T|5|0|0|Pull-up|REPS|5|15.0|weighted||0\n" +
        "T|5|0|1|Pull-up|REPS|5|15.0|weighted||0\n" +
        "T|5|0|2|Pull-up|REPS|5|15.0|weighted||0\n" +
        "T|5|0|3|Pull-up|REPS|5|15.0|weighted||0\n" +
        "T|5|1|0|Tuck Front Lever|HOLD|0|||15|0\n" +
        "T|5|1|1|Tuck Front Lever|HOLD|0|||15|0\n" +
        "T|5|1|2|Tuck Front Lever|HOLD|0|||15|0\n" +
        "T|5|2|0|Chin-up|REPS|10||||0\n" +
        "T|5|2|1|Chin-up|REPS|9||||0\n" +
        "T|5|2|2|Chin-up|REPS|6||||0\n" +
        "T|5|3|0|Lateral Raise|REPS|10|10.0|||1\n" +
        "T|5|3|1|Lateral Raise|REPS|10|10.0|||1\n" +
        "T|5|3|2|Lateral Raise|REPS|10|10.0|||1\n" +
        "T|5|5|0|Hanging Knee Raise|REPS|10||||0\n" +
        "T|5|5|1|Hanging Knee Raise|REPS|10||||0\n" +
        "T|5|5|2|Hanging Knee Raise|REPS|10||||0\n" +
        "S|6|1933488000000|1933492740000\n" +
        "T|6|0|0|Knee-to-Wall Dorsiflexion|REPS|12||||0\n" +
        "T|6|0|1|Knee-to-Wall Dorsiflexion|REPS|12||||0\n" +
        "T|6|0|2|Knee-to-Wall Dorsiflexion|REPS|12||||0\n" +
        "T|6|1|0|Pistol Squat|REPS|9|7.7|weighted||0\n" +
        "T|6|1|1|Pistol Squat|REPS|9|7.7|weighted||0\n" +
        "T|6|1|2|Pistol Squat|REPS|9|7.7|weighted||0\n" +
        "T|6|2|0|Shrimp Squat|REPS|7|7.7|weighted||0\n" +
        "T|6|2|1|Shrimp Squat|REPS|7|7.7|weighted||0\n" +
        "T|6|2|2|Shrimp Squat|REPS|7|7.7|weighted||0\n" +
        "T|6|3|0|Single-Leg Glute Bridge|REPS|10|35.0|weighted||0\n" +
        "T|6|3|1|Single-Leg Glute Bridge|REPS|10|35.0|weighted||0\n" +
        "T|6|3|2|Single-Leg Glute Bridge|REPS|10|35.0|weighted||0\n" +
        "T|6|4|0|Single-Leg Romanian Deadlift|REPS|10|24.0|||1\n" +
        "T|6|4|1|Single-Leg Romanian Deadlift|REPS|10|24.0|||1\n" +
        "T|6|4|2|Single-Leg Romanian Deadlift|REPS|10|24.0|||1\n" +
        "T|6|5|0|Single-Leg Calf Raise|REPS|10|37.7|weighted||0\n" +
        "T|6|5|1|Single-Leg Calf Raise|REPS|10|37.7|weighted||0\n" +
        "T|6|5|2|Single-Leg Calf Raise|REPS|10|37.7|weighted||0\n" +
        "T|6|6|0|Hanging Knee Raise|REPS|10||||0\n" +
        "T|6|6|1|Hanging Knee Raise|REPS|10||||0\n" +
        "T|6|6|2|Hanging Knee Raise|REPS|10||||0\n" +
        "S|7|1933718400000|1933723800000\n" +
        "T|7|0|0|Wall HSPU|REPS|6||deficit||0\n" +
        "T|7|0|1|Wall HSPU|REPS|5||deficit||0\n" +
        "T|7|0|2|Wall HSPU|REPS|4||deficit||0\n" +
        "T|7|1|0|Archer Push-up|REPS|7|7.7|weighted, deficit, elevated||0\n" +
        "T|7|1|1|Archer Push-up|REPS|8|7.7|weighted, deficit, elevated||0\n" +
        "T|7|1|2|Archer Push-up|REPS|7|7.7|weighted, deficit, elevated||0\n" +
        "T|7|2|0|Tuck Planche|HOLD|0|||5|0\n" +
        "T|7|2|1|Tuck Planche|HOLD|0|||5|0\n" +
        "T|7|2|2|Tuck Planche|HOLD|0|||5|0\n" +
        "T|7|3|0|Push-up|REPS|14|7.7|weighted, deficit, elevated||0\n" +
        "T|7|3|1|Push-up|REPS|13|7.7|weighted, deficit, elevated||0\n" +
        "T|7|3|2|Push-up|REPS|10|7.7|weighted, deficit, elevated||0\n" +
        "T|7|4|0|Ab Wheel Rollout|REPS|8||||0\n" +
        "T|7|4|1|Ab Wheel Rollout|REPS|8||||0\n" +
        "T|7|4|2|Ab Wheel Rollout|REPS|8||||0\n" +
        "S|8|1933923600000|1933926600000\n" +
        "T|8|0|0|Pull-up|REPS|4|17.7|weighted||0\n" +
        "T|8|0|1|Pull-up|REPS|3|17.7|weighted||0\n" +
        "T|8|0|2|Pull-up|REPS|3|17.7|weighted||0\n" +
        "T|8|0|3|Pull-up|REPS|2|17.7|weighted||0\n" +
        "T|8|1|0|Advanced Tuck Front Lever|HOLD|0|||20|0\n" +
        "T|8|1|1|Advanced Tuck Front Lever|HOLD|0|||15|0\n" +
        "T|8|1|2|Advanced Tuck Front Lever|HOLD|0|||10|0\n" +
        "T|8|2|0|Chin-up|REPS|9|7.7|weighted||0\n" +
        "T|8|2|1|Chin-up|REPS|6|7.7|weighted||0\n" +
        "T|8|2|2|Chin-up|REPS|4|7.7|weighted||0\n" +
        "T|8|3|0|Lateral Raise|REPS|8|11.5|||1\n" +
        "T|8|3|1|Lateral Raise|REPS|6|11.5|||1\n" +
        "T|8|3|2|Lateral Raise|REPS|6|11.5|||1\n" +
        "T|8|4|0|Hanging Knee Raise|REPS|10||||0\n" +
        "T|8|4|1|Hanging Knee Raise|REPS|10||||0\n" +
        "T|8|4|2|Hanging Knee Raise|REPS|10||||0\n" +
            ""
        )
    }
}
