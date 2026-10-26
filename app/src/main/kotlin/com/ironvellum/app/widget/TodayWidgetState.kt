package com.ironvellum.app.widget

import com.ironvellum.app.data.ProgramAnswersStore
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.MovementDifficulty
import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.SessionClock
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.domain.SetRecords
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.ui.dashboard.DayKind
import com.ironvellum.app.ui.dashboard.dayKind
import com.ironvellum.app.ui.dashboard.weekDoneDays
import com.ironvellum.app.ui.program.toPlanned
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One weekday in the week strip: a workout happened, a rite is planned, or the day is free. */
internal enum class DayMark { DONE, PLANNED, FREE }

/** The next rite on the plan after today, wrapping the week, and the ISO weekday it falls on. */
internal data class NextRite(val name: String, val day: Int)

/**
 * One exercise of the routine the card shows. [reps] is the rep figure of each set (the plan's while the
 * workout is not under way, the trial's own sets while it runs or once it is sealed) and [done] the reps of the
 * sets ticked, so the live face can draw its pips and boxes from the same row.
 */
internal data class ExRow(
    val name: String,
    val reps: List<Int>,
    val loadKg: Double?,
    /** False for a bodyweight lift, whose added load reads "+10 kg". */
    val weighted: Boolean = true,
    val done: List<Int> = emptyList(),
    /** The exercise the lifter is on: the first not yet finished. */
    val current: Boolean = false,
    /** SEALED: the heaviest set, and whether it set a record. */
    val bestKg: Double? = null,
    val bestReps: Int = 0,
    val record: Boolean = false,
) {
    val finished: Boolean get() = reps.isNotEmpty() && done.size >= reps.size
}

/** A finished workout offered for repeating when there is no plan: its name, the ISO weekday it fell on and its sets. */
internal data class RecentRow(val name: String, val day: Int, val sets: Int)

/**
 * What the home-screen widgets show and what their buttons do, decided once so the two widgets
 * and the tap cannot disagree: the face is Today's own [dayKind] for today, and a tap on Begin is
 * resolved against the state as it is when it lands, not as it was when the widget was drawn.
 */
internal data class WidgetState(
    val kind: DayKind,
    /** The rite's name, or the live trial's; null where the face has no name of its own. */
    val title: String?,
    /** The rite a BEGIN starts. */
    val presetId: Long?,
    /** The live trial a LIVE continues. */
    val sessionId: Long?,
    /** Worked exercises and sets of the rite shown (the live trial's rite while it runs). */
    val exercises: Int,
    val sets: Int,
    val streak: Int,
    /** ISO weekday of today, 1 = Monday. */
    val dayOfWeek: Int,
    /** Today's own estimate for the rite about to begin, when it has one. */
    val estimateMinutes: Int?,
    /** LIVE: minutes since the trial began, and its worked sets done and in all. */
    val liveMinutes: Int,
    val setsDone: Int,
    val setsTotal: Int,
    /** SEALED: the sealing trial's worked sets done, and how long it took. */
    val sealedSets: Int,
    val sealedMinutes: Int,
    val next: NextRite?,
    /** Monday to Sunday. */
    val week: List<DayMark>,
    /** The routine's exercises: the plan (BEGIN), the trial's sets (LIVE) or its best sets (SEALED). */
    val rows: List<ExRow> = emptyList(),
    /** SEALED: how many of the trial's sets beat a record. */
    val records: Int = 0,
    /** RESPITE: the next routine's exercises and set count, shown dimmed. */
    val nextRows: List<ExRow> = emptyList(),
    val nextSets: Int = 0,
    /** NO_CYCLE: the latest finished workouts. */
    val recent: List<RecentRow> = emptyList(),
    /** Monday to Sunday: the day of the month, the routine each day carries (null: rest) and its worked sets done. */
    val dates: List<Int> = emptyList(),
    val dayNames: List<String?> = emptyList(),
    val daySets: List<Int> = emptyList(),
    /** This week's totals: workouts done and planned, worked sets done, minutes trained. */
    val weekDone: Int = 0,
    val weekPlanned: Int = 0,
    val weekSets: Int = 0,
    val weekMinutes: Int = 0,
) {
    /** True when the primary button begins today's rite or resumes the trial under way. */
    val canStart: Boolean get() = kind == DayKind.BEGIN || kind == DayKind.LIVE
}

/**
 * Pure: today's face from the plan and the finished trials. [completed] holds sealed trials only;
 * [setsOf] gives the sets of the live trial and of the trials sealed this week; [estimate] is
 * Today's duration figure for a rite.
 */
internal fun widgetState(
    presets: List<WorkoutPreset>,
    completed: List<WorkoutSession>,
    live: WorkoutSession?,
    today: LocalDate,
    zone: ZoneId,
    nowMs: Long = 0L,
    setsOf: (Long) -> List<SessionSet> = { emptyList() },
    estimate: (WorkoutPreset) -> Int? = { null },
    weighted: (Long) -> Boolean = { true },
    /** The sets of the sealed trial that beat a record. */
    recordSets: (WorkoutSession, List<SessionSet>) -> List<SessionSet> = { _, _ -> emptyList() },
    /** The latest finished workouts with their sets, for a day with no plan. */
    recentOf: List<Pair<WorkoutSession, List<SessionSet>>> = emptyList(),
): WidgetState {
    val day = today.dayOfWeek.value
    val rite = presets.firstOrNull { it.scheduledDay == day }
    // Today's tick: was this day's scheduled rite sealed this week (taken early counts).
    val weekDone = weekDoneDays(completed, presets, today, zone)
    val sealed = weekDone[day]
    val kind = dayKind(presets.isNotEmpty(), rite, isToday = true, live = live, sealed = sealed)
    val doneDates = completed
        .map { Instant.ofEpochMilli(it.completedAtMs ?: it.startedAtMs).atZone(zone).toLocalDate() }
        .toSet()
    val shown = if (kind == DayKind.LIVE) presets.firstOrNull { it.id == live?.presetId } else rite
    val liveWork = if (kind == DayKind.LIVE) setsOf(live!!.id).filter { !it.warmup } else emptyList()
    val sealedWork = if (kind == DayKind.SEALED) setsOf(sealed!!.id).filter { !it.warmup && it.done } else emptyList()
    val next = (1..7).map { (day - 1 + it) % 7 + 1 }
        .firstNotNullOfOrNull { d -> presets.firstOrNull { it.scheduledDay == d } }
        ?.let { NextRite(it.name, it.scheduledDay!!) }
    val recorded = if (kind == DayKind.SEALED) recordSets(sealed!!, sealedWork) else emptyList()
    val weekStartDate = today.with(DayOfWeek.MONDAY)
    val weekStartMs = weekStartDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val weekTrials = completed.filter { (it.completedAtMs ?: 0L) >= weekStartMs }
    fun worked(t: WorkoutSession) = setsOf(t.id).count { it.done && !it.warmup }
    val nextPreset = next?.let { n -> presets.firstOrNull { it.scheduledDay == n.day } }
    return WidgetState(
        kind = kind,
        title = when (kind) {
            DayKind.LIVE -> live?.title?.ifBlank { null } ?: live?.label
            DayKind.BEGIN, DayKind.SEALED -> rite?.name
            else -> null
        },
        presetId = rite?.id.takeIf { kind == DayKind.BEGIN },
        sessionId = live?.id.takeIf { kind == DayKind.LIVE },
        // Entries, not movements: a rite may name one twice, and the session shows each entry as an exercise of its own.
        exercises = shown?.working?.size ?: 0,
        sets = shown?.working?.sumOf { it.targetSets } ?: 0,
        streak = Titles.trainingStreakDays(doneDates, today),
        dayOfWeek = day,
        estimateMinutes = rite?.takeIf { kind == DayKind.BEGIN }?.let(estimate),
        liveMinutes = if (kind == DayKind.LIVE) ((nowMs - live!!.startedAtMs) / 60_000L).toInt().coerceAtLeast(0) else 0,
        setsDone = liveWork.count { it.done },
        setsTotal = liveWork.size,
        sealedSets = sealedWork.size,
        sealedMinutes = sealed?.completedAtMs?.let { ((it - sealed.startedAtMs) / 60_000L).toInt().coerceAtLeast(1) } ?: 0,
        next = next,
        week = DayOfWeek.entries.map { d ->
            when {
                d.value in weekDone -> DayMark.DONE
                presets.any { it.scheduledDay == d.value } -> DayMark.PLANNED
                else -> DayMark.FREE
            }
        },
        rows = when (kind) {
            DayKind.LIVE -> liveRows(liveWork)
            DayKind.SEALED -> sealedRows(sealedWork, recorded)
            DayKind.BEGIN -> shown?.working.orEmpty().map { it.row(weighted) }
            else -> emptyList()
        },
        records = recorded.size,
        nextRows = if (kind == DayKind.RESPITE) nextPreset?.working.orEmpty().map { it.row(weighted) } else emptyList(),
        nextSets = if (kind == DayKind.RESPITE) nextPreset?.working?.sumOf { it.targetSets } ?: 0 else 0,
        recent = if (kind == DayKind.NO_CYCLE) {
            recentOf.sortedByDescending { it.first.completedAtMs ?: it.first.startedAtMs }.take(4).map { (s, sets) ->
                RecentRow(
                    s.title.ifBlank { null } ?: s.label,
                    Instant.ofEpochMilli(s.completedAtMs ?: s.startedAtMs).atZone(zone).dayOfWeek.value,
                    sets.count { it.done && !it.warmup },
                )
            }
        } else emptyList(),
        dates = (0..6).map { weekStartDate.plusDays(it.toLong()).dayOfMonth },
        dayNames = (1..7).map { d ->
            weekDone[d]?.let { it.title.ifBlank { null } ?: it.label } ?: presets.firstOrNull { it.scheduledDay == d }?.name
        },
        daySets = (0..6).map { i ->
            weekTrials
                .filter { Instant.ofEpochMilli(it.completedAtMs ?: it.startedAtMs).atZone(zone).toLocalDate() == weekStartDate.plusDays(i.toLong()) }
                .sumOf(::worked)
        },
        weekDone = weekDone.size,
        weekPlanned = (1..7).count { d -> d in weekDone || presets.any { it.scheduledDay == d } },
        weekSets = weekTrials.sumOf(::worked),
        weekMinutes = weekTrials.sumOf { t -> t.completedAtMs?.let { ((it - t.startedAtMs) / 60_000L).toInt().coerceAtLeast(0) } ?: 0 },
    )
}

/** The plan's figure for each set of the lift: a scheme's own reps where it has them, else the entry's. */
private fun PresetEntry.row(weighted: (Long) -> Boolean) = ExRow(
    name = exerciseName,
    reps = scheme.kinds(targetSets).map { scheme.repsRange(it)?.first ?: targetReps },
    loadKg = targetWeightKg,
    weighted = weighted(exerciseId),
)

private fun grouped(sets: List<SessionSet>) =
    sets.sortedWith(compareBy({ it.exercisePosition }, { it.setIndex })).groupBy { it.exercisePosition }.values

private fun liveRows(work: List<SessionSet>): List<ExRow> {
    var found = false
    return grouped(work).map { sets ->
        val row = ExRow(
            name = sets.first().exerciseName, reps = sets.map { it.reps }, loadKg = sets.first().weightKg,
            done = sets.filter { it.done }.map { it.reps },
        )
        if (!found && !row.finished) {
            found = true
            row.copy(current = true)
        } else row
    }
}

private fun sealedRows(done: List<SessionSet>, recorded: List<SessionSet>): List<ExRow> =
    grouped(done).map { sets ->
        val best = sets.maxWith(compareBy({ it.weightKg ?: 0.0 }, { it.reps }))
        ExRow(
            name = sets.first().exerciseName, reps = sets.map { it.reps }, loadKg = best.weightKg, done = sets.map { it.reps },
            bestKg = best.weightKg, bestReps = best.reps,
            record = recorded.any { it.exercisePosition == sets.first().exercisePosition },
        )
    }

/**
 * The sealed trial's sets that beat what the lifter had done before it: the same per-position record rule the
 * live set badges use ([SetRecords.delta]), judged as history stood when the trial began.
 */
internal fun recordSetsOf(
    session: WorkoutSession,
    sets: List<SessionSet>,
    history: List<Pair<WorkoutSession, List<SessionSet>>>,
    bodyweightAt: (Long) -> Double,
    metricOf: (SessionSet) -> com.ironvellum.app.domain.ExerciseMetric?,
): List<SessionSet> {
    val records = SetRecords.records(history, bodyweightAt, excludeSessionId = session.id, beforeMs = session.startedAtMs, metricOf = metricOf)
    val bodyweight = bodyweightAt(session.startedAtMs)
    val best = HashMap<Long, Double>()
    return sets.sortedWith(compareBy({ it.exercisePosition }, { it.setIndex })).filter { set ->
        val hold = MovementDifficulty.isHoldSet(metricOf(set), set.exerciseName, set.modifiers)
        val figure = if (hold) (set.durationSec ?: set.reps) else set.reps
        val delta = SetRecords.delta(
            records, set.exerciseName, SetRecords.position(set, sets), figure, set.weightKg, bodyweight,
            isHold = hold, bestEarlierThisWorkout = best[set.exerciseId], modifiers = set.modifiers,
        )
        best[set.exerciseId] = maxOf(best[set.exerciseId] ?: Double.NEGATIVE_INFINITY, delta.score)
        delta.isRecord
    }
}

/** Reads the plan and history as they stand now; a [forTap] read skips the record search the faces alone need. */
internal suspend fun loadWidgetState(context: android.content.Context, repo: Repository, forTap: Boolean = false): WidgetState {
    val presets = repo.observePresets().first()
    val completed = repo.completedSessions()
    val live = repo.observeLiveSession().first()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    // Sets are read only for the trials the faces count: the one under way and those sealed this week.
    val weekStart = today.with(DayOfWeek.MONDAY).atStartOfDay(zone).toInstant().toEpochMilli()
    val sets = HashMap<Long, List<SessionSet>>()
    if (live != null) sets[live.id] = repo.observeSessionSets(live.id).first()
    completed.filter { (it.completedAtMs ?: 0L) >= weekStart }.forEach { s ->
        repo.observeWorkout(s.id).first()?.let { sets[s.id] = it.second }
    }
    // Today's duration figure needs the lifter's pace: the newest few sealed trials with their sets.
    val rite = presets.firstOrNull { it.scheduledDay == today.dayOfWeek.value }
    var estimate: (WorkoutPreset) -> Int? = { null }
    if (rite != null && live == null) {
        val focus = SessionClock.focusFor(
            runCatching { ProgramAnswersStore.get(context)?.focus }.getOrNull(),
            repo.observeProfile().first()?.trainingMode,
        )
        val recent = completed.sortedByDescending { it.completedAtMs ?: 0L }
            .take(SessionClock.PACE_WINDOW * 4)
            .mapNotNull { s -> (sets[s.id] ?: repo.observeWorkout(s.id).first()?.second)?.let { s to it } }
        val pace = SessionClock.pace(recent)
        estimate = { preset ->
            SessionClock.minutes(SessionClock.planSeconds(preset.toPlanned().entries, focus, pace.secondsPerSet(preset)))
        }
    }
    val catalogue = repo.observeExercises().first()
    val weighted = catalogue.associate { it.id to it.isWeighted }
    val metrics = catalogue.associate { it.id to it.metric }
    // A day with no plan offers the latest workouts; a sealed day judges its sets against the whole history.
    val offered = if (presets.isEmpty() && live == null) {
        completed.sortedByDescending { it.completedAtMs ?: 0L }.take(4).mapNotNull { s ->
            (sets[s.id] ?: repo.observeWorkout(s.id).first()?.second)?.let { s to it }
        }
    } else emptyList()
    fun build(recordSets: (WorkoutSession, List<SessionSet>) -> List<SessionSet>) = widgetState(
        presets, completed, live, today, zone, System.currentTimeMillis(), { sets[it].orEmpty() }, estimate,
        weighted = { weighted[it] ?: true }, recordSets = recordSets, recentOf = offered,
    )
    val first = build { _, _ -> emptyList() }
    if (first.kind != DayKind.SEALED || forTap) return first
    // Only a sealed day pays for the whole history: its sets are judged against everything before the trial.
    val history = repo.observeHistory().first()
    val bodyweightAt = SetRecords.bodyweightLookup(repo.observeStats().first())
    return build { session, work -> recordSetsOf(session, work, history, bodyweightAt) { metrics[it.exerciseId] } }
}
