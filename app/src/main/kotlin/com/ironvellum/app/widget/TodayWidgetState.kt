package com.ironvellum.app.widget

import com.ironvellum.app.data.ProgramAnswersStore
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.SessionClock
import com.ironvellum.app.domain.SessionSet
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
    return WidgetState(
        kind = kind,
        title = when (kind) {
            DayKind.LIVE -> live?.title?.ifBlank { null } ?: live?.label
            DayKind.BEGIN, DayKind.SEALED -> rite?.name
            else -> null
        },
        presetId = rite?.id.takeIf { kind == DayKind.BEGIN },
        sessionId = live?.id.takeIf { kind == DayKind.LIVE },
        exercises = shown?.working?.map { it.exerciseId }?.distinct()?.size ?: 0,
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
    )
}

/** Reads the plan and history as they stand now. */
internal suspend fun loadWidgetState(context: android.content.Context, repo: Repository): WidgetState {
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
            .take(SessionClock.PACE_WINDOW * 2)
            .mapNotNull { s -> (sets[s.id] ?: repo.observeWorkout(s.id).first()?.second)?.let { s to it } }
        val pace = SessionClock.pace(recent)
        estimate = { preset ->
            SessionClock.minutes(SessionClock.planSeconds(preset.toPlanned().entries, focus, pace.secondsPerSet(preset.id)))
        }
    }
    return widgetState(presets, completed, live, today, zone, System.currentTimeMillis(), { sets[it].orEmpty() }, estimate)
}
