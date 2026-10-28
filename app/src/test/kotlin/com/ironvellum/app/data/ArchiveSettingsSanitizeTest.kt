package com.ironvellum.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A setting this build cannot read is dropped and named; it never throws, so it can never fail a training restore. */
class ArchiveSettingsSanitizeTest {

    @Test fun unknownFilesKeysAndStaleValuesAreDroppedOneByOne() {
        val result = ArchiveSettings.sanitize(
            mapOf(
                "appearance" to mapOf("theme" to "s:RETIRED_THEME", "themedNames" to "b:true", "brandNewKey" to "b:true"),
                "auto_backup" to mapOf("folder" to "s:content://private"),
                "program_answers" to mapOf("sessionMinutes" to "i:37", "daysPerWeek" to "i:3", "gear" to "s:NOT_A_GEAR"),
            ),
        )
        assertEquals(mapOf("themedNames" to "b:true"), result.accepted["appearance"])
        assertEquals(mapOf("daysPerWeek" to "i:3"), result.accepted["program_answers"])
        assertFalse(result.accepted.containsKey("auto_backup"))
        assertEquals(
            setOf("appearance/theme", "appearance/brandNewKey", "auto_backup", "program_answers/sessionMinutes", "program_answers/gear"),
            result.dropped.toSet(),
        )
    }

    @Test fun permissionStateIsIgnoredSilently() {
        val result = ArchiveSettings.sanitize(
            mapOf(
                "trial" to mapOf("asked_post_notifications" to "b:true", "post_notifications_not_now" to "i:2"),
                "inbox_notifier" to mapOf("enabled" to "b:true", "asked_permission" to "b:true"),
            ),
        )
        assertEquals(emptyMap<String, String>(), result.accepted["trial"])
        assertEquals(mapOf("enabled" to "b:true"), result.accepted["inbox_notifier"])
        assertTrue(result.dropped.isEmpty())
    }

    @Test fun anArchiveWithoutSettingsHasNothingToDrop() {
        val result = ArchiveSettings.sanitize(emptyMap())
        assertTrue(result.accepted.isEmpty() && result.dropped.isEmpty())
    }
}
