package com.ironvellum.app.widget

import com.ironvellum.app.ui.dashboard.DayKind

/**
 * The data the v4 widget mockups were drawn with (the Push and Legs routines, a Friday of 12 to 18 October), as a
 * [WidgetState] for each face, so a render can be laid beside its mockup and differ only in layout, never in content.
 */
internal object WidgetSamples {
    private fun push(done: List<Int> = listOf(0, 0, 0, 0, 0), live: Boolean = false, sealed: Boolean = false): List<ExRow> {
        val names = listOf("Bench Press", "Overhead Press", "Incline DB Press", "Dip", "Lateral Raise")
        val reps = listOf(listOf(6, 6, 6, 6), listOf(8, 8, 8), listOf(10, 10, 10), listOf(10, 10, 10), listOf(15, 15, 15))
        val kg = listOf(72.5, 40.0, 24.0, 10.0, 8.0)
        val result = listOf(listOf(6, 6, 6, 5), listOf(8, 8, 7), listOf(10, 10, 9), listOf(10, 9, 8), listOf(15, 15, 13))
        return names.indices.map { i ->
            ExRow(
                name = names[i],
                reps = if (sealed) result[i] else reps[i],
                loadKg = kg[i], weighted = i != 3,
                done = if (sealed) result[i] else if (live) result[i].take(done[i]) else emptyList(),
                current = live && i == 1,
                bestKg = kg[i], bestReps = result[i].max(), record = sealed && i in setOf(0, 2),
            )
        }
    }

    private val legs = listOf(
        ExRow("Back Squat", listOf(5, 5, 5, 5), 100.0), ExRow("Romanian Deadlift", listOf(8, 8, 8), 80.0),
        ExRow("Walking Lunge", listOf(12, 12, 12), 16.0), ExRow("Calf Raise", listOf(12, 12, 12, 12), 60.0),
    )

    private val recent = listOf(RecentRow("Push", 1, 16), RecentRow("Legs", 6, 15), RecentRow("Pull", 4, 14), RecentRow("Push", 2, 16))
    private val names = listOf("Push", "Pull", null, "Legs", "Push", null, "Pull")
    private val dates = listOf(12, 13, 14, 15, 16, 17, 18)
    private val volume = listOf(16, 14, 0, 15, 0, 0, 0)

    private val D = DayMark.DONE
    private val P = DayMark.PLANNED
    private val F = DayMark.FREE

    fun state(kind: DayKind, noPlanHistory: Boolean = false): WidgetState {
        val base = WidgetState(
            kind = kind, title = "Push", presetId = null, sessionId = null, exercises = 5, sets = 16, streak = 4, dayOfWeek = 5,
            estimateMinutes = 45, liveMinutes = 0, setsDone = 0, setsTotal = 0, sealedSets = 0, sealedMinutes = 0,
            next = NextRite("Pull", 1), week = listOf(D, D, F, D, P, F, P),
            dates = dates, dayNames = names, daySets = volume, weekDone = 3, weekPlanned = 5, weekSets = 45, weekMinutes = 161,
        )
        return when (kind) {
            DayKind.BEGIN -> base.copy(presetId = 1, rows = push())
            DayKind.LIVE -> base.copy(
                sessionId = 2, liveMinutes = 23, setsDone = 7, setsTotal = 16,
                rows = push(listOf(4, 1, 0, 0, 0), live = true),
            )
            DayKind.SEALED -> base.copy(
                title = "Push", streak = 5, sealedSets = 16, sealedMinutes = 52, records = 2, rows = push(sealed = true),
                week = listOf(D, D, F, D, D, F, P), daySets = listOf(16, 14, 0, 15, 16, 0, 0), weekDone = 4, weekSets = 61, weekMinutes = 213,
            )
            DayKind.RESPITE -> base.copy(
                title = null, dayOfWeek = 3, next = NextRite("Legs", 4), nextRows = legs, nextSets = 15,
                week = listOf(D, D, F, P, P, F, P), weekDone = 2,
            )
            else -> base.copy(
                title = null, streak = 0, recent = recent, next = null,
                week = listOf(D, D, F, D, F, F, F), dayNames = listOf("Push", "Pull", null, "Legs", null, null, null),
                weekPlanned = 3, weekDone = 3,
            )
        }
    }
}
