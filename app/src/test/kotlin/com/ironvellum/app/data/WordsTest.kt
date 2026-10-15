package com.ironvellum.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordsTest {

    @After
    fun backToDefault() = Words.useForTest(true)

    // ---- which mode an install starts in

    @Test
    fun `a fresh install starts plain`() {
        assertFalse(Words.defaultThemed(stored = null, databaseExists = false, welcomeSeen = false))
    }

    @Test
    fun `an install that already has a database keeps the themed names`() {
        assertTrue(Words.defaultThemed(stored = null, databaseExists = true, welcomeSeen = false))
    }

    @Test
    fun `an install that already saw the welcome screen keeps the themed names`() {
        assertTrue(Words.defaultThemed(stored = null, databaseExists = false, welcomeSeen = true))
    }

    @Test
    fun `a stored choice always wins over the install's history`() {
        assertFalse(Words.defaultThemed(stored = false, databaseExists = true, welcomeSeen = true))
        assertTrue(Words.defaultThemed(stored = true, databaseExists = false, welcomeSeen = false))
    }

    // ---- plain words

    @Test
    fun `plain mode says the plain words with their plurals and cases`() {
        Words.useForTest(false)
        assertEquals("routine", Words.rite.s)
        assertEquals("routines", Words.rite.pl)
        assertEquals("Routine", Words.rite.c)
        assertEquals("Routines", Words.rite.cpl)
        assertEquals("ROUTINES", Words.rite.upl)
        assertEquals("workout", Words.trial.t)
        assertEquals("weekly plan", Words.cycle.s)
        assertEquals("Weekly plans", Words.cycle.cpl)
        assertEquals("PR", Words.peak.s)
        assertEquals("PRs", Words.peak.cpl)
        assertEquals("kudos", Words.tribute.pl)
        assertEquals("Friends", Words.ally.cpl)
        assertEquals("Stats", Words.ledger.c)
        assertEquals("Idle rewards", Words.theVeil.c)
        assertEquals("a routine", Words.rite.a)
        assertEquals("a friend", Words.ally.a)
        assertEquals("a lifter", Words.lifter.at)
        assertEquals("an empty workout", Words.openTrial.a)
        assertEquals("a streak", Words.oath.a)
    }

    @Test
    fun `plain verb forms follow the verb`() {
        Words.useForTest(false)
        assertEquals("Finish the workout", "${Words.seal.c} the ${Words.trial.s}")
        assertEquals("3 finished workouts", "3 ${Words.sealed.s} ${Words.trial.pl}")
        assertEquals("Finishing", Words.sealing.c)
        assertEquals("3 routines", "3 ${Words.rite.pl}")
        assertEquals("Build a weekly plan", "${Words.forge.c} ${Words.cycle.a}")
    }

    // ---- themed names are exactly what the app always said

    @Test
    fun `themed mode keeps the original names and their capitals`() {
        Words.useForTest(true)
        assertEquals("rite", Words.rite.s)
        assertEquals("Rite", Words.rite.t)
        assertEquals("Trials", Words.trial.cpl)
        assertEquals("TRIAL", Words.trial.u)
        assertEquals("Allies", Words.ally.cpl)
        assertEquals("allies", Words.ally.pl)
        assertEquals("an ally", Words.ally.a)
        assertEquals("an Ironbound", Words.lifter.at)
        assertEquals("An Ironbound", Words.lifter.cat)
        assertEquals("Open Trial", Words.openTrial.c)
        assertEquals("The Veil", Words.theVeil.c)
        assertEquals("the Veil", Words.theVeil.t)
        assertEquals("THE SUMMONS", Words.theSummons.u)
        assertEquals("the Chronicle", Words.theChronicle.t)
        assertEquals("Peak", Words.peak.c)
        assertEquals("Seal the trial", "${Words.seal.c} the ${Words.trial.s}")
    }

    @Test
    fun `flipping the switch changes a word already in hand`() {
        val word = Words.rite
        Words.useForTest(true)
        assertEquals("rite", word.s)
        Words.useForTest(false)
        assertEquals("routine", word.s)
    }

    // ---- catalogues and server text

    @Test
    fun `a per-mode catalogue is built once for each mode and follows the switch`() {
        var builds = 0
        val catalogue by Words.perMode { builds++; listOf(Words.trial.s) }
        Words.useForTest(true)
        assertEquals(listOf("trial"), catalogue)
        assertEquals(listOf("trial"), catalogue)
        Words.useForTest(false)
        assertEquals(listOf("workout"), catalogue)
        assertEquals(listOf("workout"), catalogue)
        assertEquals(2, builds)
    }

    @Test
    fun `server sentences come out plain only in plain mode`() {
        Words.useForTest(true)
        assertEquals("That Ironbound is not in your circle", Words.fromServer("That Ironbound is not in your circle"))
        Words.useForTest(false)
        assertEquals("That lifter is not in your group", Words.fromServer("That Ironbound is not in your circle"))
        assertEquals("Daily comment limit reached — try again tomorrow.", Words.fromServer("Daily remark limit reached — try again tomorrow."))
        assertEquals("Only the owner can rename the group", Words.fromServer("Only the Keeper can rename the circle"))
        assertEquals("Too many friend requests today", Words.fromServer("Too many ally requests today"))
    }

    @Test
    fun `an ascension tier is Level in plain mode and keeps its name when themed`() {
        Words.useForTest(false)
        assertEquals("Level", com.ironvellum.app.domain.ArmyClass.forLevel(13).title)
        Words.useForTest(true)
        assertEquals("Adept", com.ironvellum.app.domain.ArmyClass.forLevel(13).title)
    }
}
