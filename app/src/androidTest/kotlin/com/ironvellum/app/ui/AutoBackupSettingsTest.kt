package com.ironvellum.app.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Data settings' automatic backup. The system folder picker is another app, so the
 * on state is seeded as a stored folder whose permission this install does not
 * hold: exactly what a folder revoked in system settings looks like, and the
 * case that must be shown plainly rather than fail silently.
 */
@RunWith(AndroidJUnit4::class)
class AutoBackupSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val prefs get() = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("auto_backup", Context.MODE_PRIVATE)

    @Before fun clean() {
        prefs.edit().clear().commit()
    }

    @After fun restore() {
        prefs.edit().clear().commit()
    }

    private fun openData() {
        TestProfile.ensureSetUp()
        compose.mainClock.autoAdvance = false
        compose.waitUntil(timeoutMillis = 10_000) {
            advance()
            compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        advance()
        compose.onNode(hasText("Data") and hasClickAction()).performClick()
        advance()
    }

    @Test fun offUntilAFolderIsChosen() {
        openData()
        compose.onNodeWithText("Automatic backup").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Off").assertIsDisplayed()
        compose.onNodeWithText("Saves a full archive to a folder you choose, every week.").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Back up now").fetchSemanticsNodes().size)
        ShotRecorder.take(compose, "backup-off")
    }

    @Test fun aFolderWhoseAccessIsGoneIsReportedAndCanBeTurnedOff() {
        prefs.edit()
            .putString("folder", "content://com.android.externalstorage.documents/tree/primary%3ABackups")
            .putString("folder_name", "Backups")
            .commit()
        openData()
        compose.onNodeWithText("Weekly").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Folder: Backups. Keeps the newest 5.").assertIsDisplayed()
        compose.onNodeWithText("Back up now").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Access to the backup folder was lost. Choose the folder again.")
            .performScrollTo().assertIsDisplayed()

        ShotRecorder.take(compose, "backup-folder-lost")
        compose.onNodeWithText("Turn off automatic backup").performScrollTo().performClick()
        advance()
        compose.onNodeWithText("Off").performScrollTo().assertIsDisplayed()
        assertEquals(null, prefs.getString("folder", null))
    }

    private fun advance() = compose.mainClock.advanceTimeBy(1_200L)
}
