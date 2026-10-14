package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrialHintsTest {

    private val kg = { v: Double? -> v?.let { "${it.toInt()} kg" } ?: "bodyweight" }
    private val plain = LiftLoading(stepKg = 2.5)
    private val scheme = SetScheme(type = SchemeType.TOP_BACKOFF, backoffSets = 2)

    private fun set(id: Long, kind: SetKind, done: Boolean = false, kgLoad: Double? = 100.0, reps: Int = 5) =
        SessionSet(id = id, exerciseId = 1, setIndex = id.toInt(), reps = reps, weightKg = kgLoad, done = done, kind = kind)

    @Test
    fun theTopSetNamesWhatItBeatsAndWhenItWouldBeAPeak() {
        val sets = listOf(set(1, SetKind.TOP), set(2, SetKind.BACKOFF), set(3, SetKind.BACKOFF))
        val last = TopMark(4, 100.0)
        assertEquals("Beat 4 × 100 kg", TrialHints.forLift(sets, scheme, last, false, plain, kg)[1])
        assertEquals("Beat 4 × 100 kg · this would be a new peak", TrialHints.forLift(sets, scheme, last, true, plain, kg)[1])
    }

    @Test
    fun backOffsWaitForTheTopSetThenOnlySayWhenTheyAreRounded() {
        val waiting = listOf(set(1, SetKind.TOP), set(2, SetKind.BACKOFF))
        assertEquals("Fills from your top set", TrialHints.forLift(waiting, scheme, null, false, plain, kg)[2])

        val vest = LoadKit(vestMaxKg = 20.0, vestBlockKg = 1.0, dipBelt = false)
        val lift = LiftLoading(stepKg = 2.5, worn = vest, bodyweightKg = 80.0, bodyweightLift = true)
        val logged = listOf(set(1, SetKind.TOP, done = true, kgLoad = 10.0), set(2, SetKind.BACKOFF, kgLoad = 5.0))
        assertEquals("Rounded to your vest blocks.", TrialHints.forLift(logged, scheme, null, false, lift, kg)[2])
    }

    @Test
    fun anUnweightedBodyweightTopSetGivesTwoFewerRepBackOffs() {
        val lift = LiftLoading(stepKg = 2.5, bodyweightKg = 80.0, bodyweightLift = true)
        val logged = listOf(set(1, SetKind.TOP, done = true, kgLoad = null, reps = 8), set(2, SetKind.BACKOFF, kgLoad = null))
        assertEquals("Two fewer reps than your top set", TrialHints.forLift(logged, scheme, null, false, lift, kg)[2])
    }

    @Test
    fun dropsRunStraightOnAndLoggedSetsGetNoHint() {
        val sets = listOf(set(1, SetKind.WORKING, done = true), set(2, SetKind.DROP))
        val hints = TrialHints.forLift(sets, SetScheme.DEFAULT, null, false, plain, kg)
        assertEquals("Straight on, no rest", hints[2])
        assertNull(hints[1])
    }
}
