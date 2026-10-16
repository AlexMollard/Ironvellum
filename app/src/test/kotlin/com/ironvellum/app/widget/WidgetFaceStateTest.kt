package com.ironvellum.app.widget

import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.ui.dashboard.DayKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** What the two widgets read beyond the face: the week strip, the next rite, the live and sealed figures. */
class WidgetFaceStateTest {
    private val zone = ZoneId.of("UTC")

    /** A Friday; the week opened on Monday the 12th. */
    private val friday = LocalDate.of(2026, 10, 16)

    private fun ms(date: LocalDate, hour: Int = 12, minute: Int = 0) =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun entry(exerciseId: Long, sets: Int) = PresetEntry(exerciseId = exerciseId, targetSets = sets, targetReps = 5)

    private val push = WorkoutPreset(id = 1, name = "Push", scheduledDay = 1, entries = listOf(entry(1, 3), entry(2, 3)))
    private val legs = WorkoutPreset(id = 2, name = "Legs", scheduledDay = 2, entries = listOf(entry(3, 5)))
    private val pull = WorkoutPreset(id = 3, name = "Pull", scheduledDay = 5, entries = listOf(entry(4, 4), entry(5, 4)))
    private val plan = listOf(push, legs, pull)

    private fun done(preset: WorkoutPreset, date: LocalDate, hour: Int = 12, minutes: Int = 52) = WorkoutSession(
        id = 100L + date.toEpochDay(), presetId = preset.id, label = preset.name,
        startedAtMs = ms(date, hour), completedAtMs = ms(date, hour, minutes),
    )

    private fun set(done: Boolean, warmup: Boolean = false) =
        SessionSet(exerciseId = 1, setIndex = 0, reps = 5, done = done, kind = if (warmup) SetKind.WARMUP else SetKind.WORKING)

    @Test
    fun `the week strip marks trained days, planned days and free days Monday to Sunday`() {
        // Monday's Push done; Tuesday's Legs missed; today is Friday's Pull, not yet begun.
        val s = widgetState(plan, listOf(done(push, friday.minusDays(4))), null, friday, zone)
        assertEquals(
            listOf(DayMark.DONE, DayMark.PLANNED, DayMark.FREE, DayMark.FREE, DayMark.PLANNED, DayMark.FREE, DayMark.FREE),
            s.week,
        )
        assertEquals(5, s.dayOfWeek)
    }

    @Test
    fun `today turns filled once its rite is sealed`() {
        val s = widgetState(plan, listOf(done(pull, friday)), null, friday, zone)
        assertEquals(DayMark.DONE, s.week[4])
    }

    @Test
    fun `the next rite is the next day on the plan, wrapping past the week's end`() {
        // From Friday the next is Monday's Push; from Monday (a rest-free plan) it is Tuesday's Legs.
        assertEquals(NextRite("Push", 1), widgetState(plan, emptyList(), null, friday, zone).next)
        assertEquals(NextRite("Legs", 2), widgetState(plan, emptyList(), null, friday.minusDays(4), zone).next)
        assertNull(widgetState(emptyList(), emptyList(), null, friday, zone).next)
        // A plan with only today's rite wraps to itself next week.
        assertEquals(NextRite("Pull", 5), widgetState(listOf(pull), emptyList(), null, friday, zone).next)
    }

    @Test
    fun `a trial under way reports minutes elapsed and worked sets done, warm-ups left out`() {
        val live = WorkoutSession(id = 7, presetId = 3, label = "Pull", startedAtMs = ms(friday, 9, 0))
        val sets = listOf(set(true), set(true), set(false), set(false), set(true, warmup = true))
        val s = widgetState(plan, emptyList(), live, friday, zone, nowMs = ms(friday, 9, 23), setsOf = { sets })
        assertEquals(DayKind.LIVE, s.kind)
        assertEquals(23, s.liveMinutes)
        assertEquals(2, s.setsDone)
        assertEquals(4, s.setsTotal)
    }

    @Test
    fun `a sealed rite reports the sets it paid and how long it took`() {
        val sealed = done(pull, friday, hour = 9, minutes = 52)
        val sets = listOf(set(true), set(true), set(true), set(false), set(true, warmup = true))
        val s = widgetState(plan, listOf(sealed), null, friday, zone, setsOf = { sets })
        assertEquals(DayKind.SEALED, s.kind)
        assertEquals(3, s.sealedSets)
        assertEquals(52, s.sealedMinutes)
    }

    @Test
    fun `the duration estimate is carried only for a rite about to begin`() {
        assertEquals(45, widgetState(plan, emptyList(), null, friday, zone, estimate = { 45 }).estimateMinutes)
        assertNull(widgetState(plan, listOf(done(pull, friday)), null, friday, zone, estimate = { 45 }).estimateMinutes)
        assertNull(widgetState(plan, emptyList(), null, friday, zone).estimateMinutes)
    }
}
