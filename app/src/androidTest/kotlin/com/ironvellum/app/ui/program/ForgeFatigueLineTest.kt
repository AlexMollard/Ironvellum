package com.ironvellum.app.ui.program

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.FatigueModel
import com.ironvellum.app.domain.FatigueSession
import com.ironvellum.app.domain.LoggedSet
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.RoutinePlan
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.ui.FlowDriver
import com.ironvellum.app.ui.TestProfile
import com.ironvellum.app.ui.theme.IronvellumTheme
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The proposed plan names what is still recovering, notes a day that meets it, and never changes the plan. */
@RunWith(AndroidJUnit4::class)
class ForgeFatigueLineTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val driver by lazy { FlowDriver(compose) }

    private val hour = 3_600_000L
    private val now = 1_792_000_000_000L

    private val tomorrow = Instant.ofEpochMilli(now + 24 * hour).atZone(ZoneId.systemDefault()).dayOfWeek.value

    private fun plan() = RoutinePlan(
        listOf(PlannedPreset("Lower", "", tomorrow, listOf(PlannedEntry("Back Squat", 3, 5, null)))),
        "",
    )

    private fun squatsAgo(hoursAgo: Long, sets: Int) = FatigueSession(
        1, now - hoursAgo * hour,
        List(sets) { LoggedSet("Back Squat", "", 100.0, 5, 9, SetKind.WORKING, true, now - hoursAgo * hour) },
    )

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
        driver.takeTheClock()
    }

    private fun show(report: com.ironvellum.app.domain.FatigueReport?, onOpenFatigue: () -> Unit = {}) {
        val p = plan()
        compose.activityRule.scenario.onActivity {
            it.setContent {
                IronvellumTheme {
                    ForgePlanPage(
                        plan = p, previousPlan = null, error = null, saving = false, catalogueLoaded = true,
                        equipment = Equipment.FULL_GYM, subtitle = "1 day a week", savedRites = 0,
                        coveragePresets = p.presets, switchesProgression = false, progressionLine = "",
                        scroll = rememberScrollState(), onBack = {}, onCoverage = {}, onOpenEntry = { _, _ -> },
                        onTryAgain = {}, onApply = {}, onReplace = {},
                        fatigue = report, onOpenFatigue = onOpenFatigue,
                    )
                }
            }
        }
        driver.settle()
    }

    @Test
    fun theRowNamesWhatIsRecoveringAndLinksToMuscles() {
        var opened = 0
        show(FatigueModel.estimate(listOf(squatsAgo(2, 8)), now, now - 400 * 24 * hour)) { opened++ }
        driver.awaitText("still recovering")
        assertTrue(driver.allText().any { it.startsWith("Nothing in your") || it.contains("Nothing in your") })
        driver.click("Muscles ›")
        assertEquals(1, opened)
    }

    @Test
    fun aDayThatMeetsAWorkedMuscleCarriesAQuietEstimateNote() {
        show(FatigueModel.estimate(listOf(squatsAgo(2, 8)), now, now - 400 * 24 * hour))
        driver.awaitText("· estimate")
        assertTrue(driver.allText().any { it.contains(" by ") && it.endsWith("· estimate") })
    }

    @Test
    fun aRestedLifterSeesNoRowAndNoNote() {
        show(FatigueModel.estimate(emptyList(), now, now - 400 * 24 * hour))
        driver.awaitText("Lower")
        assertTrue(driver.allText().none { it.contains("still recovering") || it.endsWith("· estimate") })
    }
}
