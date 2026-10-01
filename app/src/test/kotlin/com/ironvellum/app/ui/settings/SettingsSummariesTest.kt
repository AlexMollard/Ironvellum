package com.ironvellum.app.ui.settings

import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.Gear
import com.ironvellum.app.domain.Sex
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSummariesTest {

    @Test
    fun `armoury reads unset, full gym and nothing plainly`() {
        assertEquals("Not set", SettingsSummaries.armoury(null))
        assertEquals("Full gym", SettingsSummaries.armoury(Equipment.FULL_GYM))
        assertEquals("Nothing", SettingsSummaries.armoury(Equipment.NOTHING))
    }

    @Test
    fun `armoury lists two items in catalogue order then a count`() {
        // Set order must not matter: the summary follows Gear.entries.
        val four = Equipment(false, linkedSetOf(Gear.BENCH, Gear.DUMBBELLS, Gear.RINGS, Gear.PULL_UP_BAR))
        assertEquals("Pull-up bar, rings +2", SettingsSummaries.armoury(four))
        assertEquals("Dumbbells", SettingsSummaries.armoury(Equipment(false, setOf(Gear.DUMBBELLS))))
        assertEquals(
            "Pull-up bar, dumbbells",
            SettingsSummaries.armoury(Equipment(false, setOf(Gear.DUMBBELLS, Gear.PULL_UP_BAR))),
        )
    }

    @Test
    fun `profile joins name height and sex, and says when height is missing`() {
        assertEquals("Sam · 180 cm · Male", SettingsSummaries.profile("Sam", 180.0, Sex.MALE))
        assertEquals("Sam · 172.5 cm · Female", SettingsSummaries.profile(" Sam ", 172.5, Sex.FEMALE))
        assertEquals("height not set · Male", SettingsSummaries.profile("", null, Sex.MALE))
    }

    @Test
    fun `health says whether it is linked and how many days it filled`() {
        assertEquals("Not connected", SettingsSummaries.health(HealthLink.NOT_CONNECTED, 0))
        assertEquals("Connected", SettingsSummaries.health(HealthLink.CONNECTED, 0))
        assertEquals("Connected · 1 day", SettingsSummaries.health(HealthLink.CONNECTED, 1))
        assertEquals("Connected · 31 days", SettingsSummaries.health(HealthLink.CONNECTED, 31))
        assertEquals("Not on this device", SettingsSummaries.health(HealthLink.UNAVAILABLE, 5))
    }
}
