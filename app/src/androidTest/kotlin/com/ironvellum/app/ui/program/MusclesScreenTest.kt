package com.ironvellum.app.ui.program

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.ui.FlowDriver
import com.ironvellum.app.ui.TestProfile
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Muscles is one screen with one tab row, Fatigue | Coverage, and a link lands on the view it names. */
@RunWith(AndroidJUnit4::class)
class MusclesScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val driver by lazy { FlowDriver(compose) }

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
        driver.takeTheClock()
    }

    private fun show(view: MusclesView, onBack: () -> Unit = {}) {
        compose.activityRule.scenario.onActivity {
            it.setContent {
                IronvellumTheme {
                    MusclesScreen(
                        initialView = view, onBack = onBack, onGenerateSession = {}, onOpenRite = {},
                        onOpenRites = {}, onOptimise = {},
                    )
                }
            }
        }
        driver.settle()
    }

    private fun tabCount() = compose
        .onAllNodes(SemanticsMatcher("is a tab") { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab })
        .fetchSemanticsNodes().size

    @Test
    fun fatigueIsOneRowOfTwoTabsAndHasBack() {
        var backed = false
        show(MusclesView.FATIGUE) { backed = true }
        driver.awaitText("Estimated from your last 7 days")
        assertEquals(2, tabCount())
        driver.click("BACK")
        assertTrue(backed)
    }

    @Test
    fun coverageHasOneMapAndNoSecondTabRow() {
        show(MusclesView.COVERAGE)
        driver.awaitText("This week, Monday to Sunday")
        assertEquals(2, tabCount())
        val text = driver.allText()
        assertFalse(text.any { it == "Last 7 days" })
        assertTrue(text.any { it.contains("Planned") || it.contains("Short") || it.contains("Done") })
    }

    @Test
    fun theTabsSwitchBetweenTheTwoPages() {
        show(MusclesView.FATIGUE)
        driver.click("Coverage")
        driver.awaitText("This week, Monday to Sunday")
        driver.click("Fatigue")
        driver.awaitText("Estimated from your last 7 days")
        assertEquals(2, tabCount())
    }
}
