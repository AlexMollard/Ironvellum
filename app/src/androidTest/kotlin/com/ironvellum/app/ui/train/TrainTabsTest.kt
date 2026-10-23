package com.ironvellum.app.ui.train

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.ui.FlowDriver
import com.ironvellum.app.ui.TestProfile
import com.ironvellum.app.ui.theme.IronvellumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Train opens on Plan; Muscles remembers its switch; the coverage row is a link that wins. */
@RunWith(AndroidJUnit4::class)
class TrainTabsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val driver by lazy { FlowDriver(compose) }

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
        TrainTabMemory.reset()
        driver.takeTheClock()
        compose.activityRule.scenario.onActivity {
            it.setContent {
                IronvellumTheme {
                    TrainScreen(
                        onOpenRite = {}, onNew = {}, onGenerate = { _, _ -> }, onStartSession = {},
                        onQuickSession = {}, onOpenExercises = {}, onOpenLog = {}, onOptimise = {}, onOpenLift = { _, _ -> },
                    )
                }
            }
        }
        driver.settle()
    }

    /** The memory outlives a test inside the one instrumentation process; later tests expect Train on Plan. */
    @After
    fun forget() = TrainTabMemory.reset()

    @Test
    fun opensOnPlanAndMusclesOpensOnFatigue() {
        assertEquals(TrainTab.PLAN, TrainTabMemory.tab)
        driver.awaitText("Weekly coverage")
        driver.click("Muscles")
        driver.awaitText("Fatigue")
        assertTrue(driver.allText().contains("Coverage"))
        assertEquals(MusclesView.FATIGUE, TrainTabMemory.view)
    }

    @Test
    fun theSwitchIsRememberedAcrossPlan() {
        driver.click("Muscles")
        driver.click("Coverage")
        driver.click("Plan")
        driver.awaitText("Weekly coverage")
        driver.click("Muscles")
        assertEquals(MusclesView.COVERAGE, TrainTabMemory.view)
        assertEquals(TrainTab.MUSCLES, TrainTabMemory.tab)
    }

    @Test
    fun theCoverageRowOnPlanWinsOverFatigue() {
        TrainTabMemory.view = MusclesView.FATIGUE
        driver.awaitText("Weekly coverage")
        driver.click("Weekly coverage")
        assertEquals(TrainTab.MUSCLES, TrainTabMemory.tab)
        assertEquals(MusclesView.COVERAGE, TrainTabMemory.view)
    }
}
