package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunTest {

    @Test
    fun `the notification explainer is offered until it has been refused three times`() {
        assertTrue(FirstRun.askForNotifications(asked = false, notNowCount = 0))
        assertTrue(FirstRun.askForNotifications(asked = false, notNowCount = 2))
        assertFalse(FirstRun.askForNotifications(asked = false, notNowCount = FirstRun.MAX_NOTIFICATION_ASKS))
    }

    @Test
    fun `a lifter who allowed the system prompt, or was asked on an older build, is never asked again`() {
        assertFalse(FirstRun.askForNotifications(asked = true, notNowCount = 0))
    }

    @Test
    fun `not now is shown three times in all`() {
        assertEquals(3, FirstRun.MAX_NOTIFICATION_ASKS)
        var shown = 0
        var notNow = 0
        while (FirstRun.askForNotifications(asked = false, notNowCount = notNow)) {
            shown++
            notNow++
        }
        assertEquals(3, shown)
    }

    @Test
    fun `the Veil introduces itself to a lifter with nothing of it`() {
        assertTrue(FirstRun.veilIntroDue(seen = false, lifetimeEssence = 0, relics = 0, crests = 0))
    }

    @Test
    fun `the Veil stays quiet once seen`() {
        assertFalse(FirstRun.veilIntroDue(seen = true, lifetimeEssence = 0, relics = 0, crests = 0))
    }

    @Test
    fun `an existing install with essence, a relic or a crest counts as introduced`() {
        assertFalse(FirstRun.veilIntroDue(seen = false, lifetimeEssence = 1, relics = 0, crests = 0))
        assertFalse(FirstRun.veilIntroDue(seen = false, lifetimeEssence = 0, relics = 1, crests = 0))
        assertFalse(FirstRun.veilIntroDue(seen = false, lifetimeEssence = 0, relics = 0, crests = 1))
    }

    @Test
    fun `a restore finishes setup only with a body profile and rites`() {
        assertTrue(FirstRun.restoreCompletesSetup(heightCm = 175.0, riteCount = 4))
        assertFalse(FirstRun.restoreCompletesSetup(heightCm = 175.0, riteCount = 0))
        assertFalse(FirstRun.restoreCompletesSetup(heightCm = null, riteCount = 4))
        assertFalse(FirstRun.restoreCompletesSetup(heightCm = 12.0, riteCount = 4))
    }
}
