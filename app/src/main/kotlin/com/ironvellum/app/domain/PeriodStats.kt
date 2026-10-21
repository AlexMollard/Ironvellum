package com.ironvellum.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The strongest set of a period by estimated 1RM: [set] is the set as lifted, [e1rmKg] what it estimates. */
data class BestSet(
    val label: String,
    val set: SessionSet,
    val e1rmKg: Double,
    val atMs: Long,
    /** The load is ADDED to the lifter's own body (a bodyweight movement), so it prints with a plus. */
    val addsToBody: Boolean,
)

/** One lift's work in a period: [reps] is the common rep count when every set had it, else null. */
data class LiftWork(
    val label: String,
    val sets: Int,
    val reps: Int?,
    val topKg: Double?,
    val addsToBody: Boolean,
)

/** One Monday-to-Sunday week's workouts and kilograms moved: what a period's best week is picked by. */
data class WeekTotal(val weekStart: LocalDate, val workouts: Int, val volumeKg: Double) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)
}

/**
 * What a stretch of training added up to: workouts, sets, volume, time, the lifts that set new records and the hard
 * sets each muscle took. The weekly recap is the first reader; a month or year in review reads the same
 * figures over its own range, so this knows nothing about weeks. Pure.
 *
 * Calendar days, not hours: [from] and [to] (exclusive) are dates in [zone], so a week that holds a clock change is
 * still seven days. A workout belongs to the day it completed (the day it started, when it never completed), the rule
 * the streak and the week rail already use.
 */
data class PeriodStats(
    val from: LocalDate,
    val to: LocalDate,
    val workouts: Int,
    /** Done sets (warm-ups are never done), activities included: the figure the victory screen and the share card print. */
    val sets: Int,
    /** Kilograms moved, counted as [WorkoutShare.totals] counts them. */
    val volumeKg: Double,
    val minutes: Long,
    val records: List<LiftRecords.RecordGain>,
    val bestSet: BestSet?,
    /** Fractional hard sets per muscle ([HardSets]). */
    val muscleSets: Map<Muscle, Double>,
    /** The days with a workout. */
    val days: Set<LocalDate>,
    val lifts: List<LiftWork>,
    /** The workouts' own names, oldest first. */
    val titles: List<String>,
) {
    val isEmpty: Boolean get() = workouts == 0

    companion object {
        /** A session longer than a day is a trial left open (and an import carries none): it adds no time. */
        private const val MAX_SESSION_MINUTES = 24L * 60

        internal fun within(
            history: List<Pair<WorkoutSession, List<SessionSet>>>,
            from: LocalDate,
            to: LocalDate,
            zone: ZoneId,
        ): List<Pair<WorkoutSession, List<SessionSet>>> {
            val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val toMs = to.atStartOfDay(zone).toInstant().toEpochMilli()
            return history.filter { (session, _) -> (session.completedAtMs ?: session.startedAtMs) in fromMs until toMs }
        }

        /** How many workouts fall in [from] until [to]: [of]'s count without the rest of its work. */
        fun workoutsIn(history: List<Pair<WorkoutSession, List<SessionSet>>>, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int =
            within(history, from, to, zone).size

        /**
         * The whole weeks (Monday to Sunday) of [from] until [to] that held a workout, oldest first. A week belongs to the
         * period that holds its Thursday (the ISO rule), so a week straddling two months is counted once, and its figures
         * are the whole week's, the ones its weekly recap shows. One pass over [history], whatever the period's length.
         */
        fun weeks(
            history: List<Pair<WorkoutSession, List<SessionSet>>>,
            exercises: Map<Long, Exercise>,
            from: LocalDate,
            to: LocalDate,
            zone: ZoneId = ZoneId.systemDefault(),
        ): List<WeekTotal> {
            val workouts = HashMap<LocalDate, Int>()
            val volume = HashMap<LocalDate, Double>()
            for ((session, sets) in history) {
                val day = Instant.ofEpochMilli(session.completedAtMs ?: session.startedAtMs).atZone(zone).toLocalDate()
                val monday = day.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                val thursday = monday.plusDays(3)
                if (thursday.isBefore(from) || !thursday.isBefore(to)) continue
                workouts.merge(monday, 1, Int::plus)
                volume.merge(monday, WorkoutShare.totals(sets, exercises).movedKg.toDouble(), Double::plus)
            }
            return workouts.map { (monday, count) -> WeekTotal(monday, count, volume.getValue(monday)) }.sortedBy { it.weekStart }
        }

        /** The best of [weeks]: the most kilograms moved, then the most workouts, then the earliest. Null with no weeks. */
        fun best(weeks: List<WeekTotal>): WeekTotal? =
            weeks.sortedWith(compareByDescending<WeekTotal> { it.volumeKg }.thenByDescending { it.workouts }.thenBy { it.weekStart }).firstOrNull()

        /**
         * The figures for [from] until [to] over a completed [history] (as `Repository.observeHistory` holds it).
         * [exercises] is the catalogue by id: metrics, and which lifts carry their load on top of the body.
         */
        fun of(
            history: List<Pair<WorkoutSession, List<SessionSet>>>,
            exercises: Map<Long, Exercise>,
            from: LocalDate,
            to: LocalDate,
            zone: ZoneId = ZoneId.systemDefault(),
        ): PeriodStats {
            val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val toMs = to.atStartOfDay(zone).toInstant().toEpochMilli()
            fun at(session: WorkoutSession) = session.completedAtMs ?: session.startedAtMs
            val inside = within(history, from, to, zone).sortedBy { (session, _) -> at(session) }

            var sets = 0
            var volume = 0.0
            var minutes = 0L
            val logged = ArrayList<LoggedSet>()
            var best: BestSet? = null
            val work = LinkedHashMap<String, MutableList<SessionSet>>()
            val names = HashMap<String, Pair<String, Boolean>>()
            for ((session, setList) in inside) {
                val totals = WorkoutShare.totals(setList, exercises)
                sets += totals.sets
                volume += totals.movedKg
                val length = ((session.completedAtMs ?: session.startedAtMs) - session.startedAtMs) / 60_000L
                if (length in 1..MAX_SESSION_MINUTES) minutes += length
                logged += HardSets.logged(at(session), setList, exercises)
                for ((key, top) in LiftRecords.sessionBests(setList, exercises)) {
                    if (best == null || top.e1rmKg > best.e1rmKg) {
                        best = BestSet(Variation.label(top.display, top.variation), top.set, top.e1rmKg, at(session), top.addsToBody)
                    }
                    names.putIfAbsent(key, Variation.label(top.display, top.variation) to top.addsToBody)
                }
                for (set in setList) {
                    if (!set.done || set.warmup || exercises[set.exerciseId]?.metric?.isStrength == false) continue
                    val name = set.exerciseName.ifEmpty { exercises[set.exerciseId]?.name ?: "" }.trim()
                    if (name.isEmpty()) continue
                    val key = Variation.liftKey(name, set.modifiers)
                    work.getOrPut(key) { mutableListOf() } += set
                    names.putIfAbsent(key, Variation.label(name, Variation.key(set.modifiers)) to (exercises[set.exerciseId]?.isWeighted == false))
                }
            }

            return PeriodStats(
                from = from,
                to = to,
                workouts = inside.size,
                sets = sets,
                volumeKg = volume,
                minutes = minutes,
                records = LiftRecords.gains(history, exercises, fromMs, toMs),
                bestSet = best,
                muscleSets = HardSets.inRange(logged, fromMs, toMs),
                days = inside.map { (session, _) -> Instant.ofEpochMilli(at(session)).atZone(zone).toLocalDate() }.toSet(),
                lifts = work.map { (key, list) ->
                    val (label, adds) = names.getValue(key)
                    val timed = exercises[list.first().exerciseId]?.metric == ExerciseMetric.HOLD
                    LiftWork(
                        label = label,
                        sets = list.size,
                        reps = list.map { it.reps }.distinct().singleOrNull()?.takeIf { !timed && it > 0 },
                        topKg = list.maxOf { it.weightKg ?: 0.0 }.takeIf { it > 0.0 },
                        addsToBody = adds,
                    )
                },
                titles = inside.map { (session, _) -> session.title.ifBlank { session.label } },
            )
        }
    }
}
