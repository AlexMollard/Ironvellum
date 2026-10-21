package com.ironvellum.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.ui.components.CelebrationGate
import com.ironvellum.app.ui.components.CelebrationPage
import com.ironvellum.app.ui.components.GatedAchievementOverlay
import com.ironvellum.app.ui.components.HoldsCelebration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A seal's own celebration (the XP page and the level it crosses) and Today's waiting deed or crest pages are two
 * dialogs. They take turns: while the seal tells its story only one dialog exists, and the waiting page opens after.
 */
@RunWith(AndroidJUnit4::class)
class CelebrationNeverStacksTest {

    @get:Rule
    val compose = createComposeRule()

    private fun dialogs() = compose.onAllNodes(isDialog()).fetchSemanticsNodes().size

    @Test
    fun aWaitingPageOpensOnlyAfterTheSealsStoryIsOver() {
        var sealOpen by mutableStateOf(true)
        compose.setContent {
            Box(Modifier.testTag("host")) {
                if (sealOpen) HoldsCelebration()
                GatedAchievementOverlay(
                    pages = listOf(CelebrationPage.GoalMet(name = "Circle", xp = 10)),
                    onDone = {},
                )
            }
        }
        compose.waitForIdle()
        assertEquals("the waiting page stays back while the seal speaks", 0, dialogs())
        sealOpen = false
        compose.waitForIdle()
        assertEquals("the waiting page opens once the seal is done", 1, dialogs())
        assertEquals(0, CelebrationGate.held.value)
    }
}