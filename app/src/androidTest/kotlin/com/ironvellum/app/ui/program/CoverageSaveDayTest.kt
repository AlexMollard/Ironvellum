package com.ironvellum.app.ui.program

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.ProgramAnswersStore
import com.ironvellum.app.domain.EffortCue
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.ui.FlowDriver
import com.ironvellum.app.ui.TestProfile
import com.ironvellum.app.ui.theme.IronvellumTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Forging a rite from Weekly coverage used to save it with no day, so the page never counted it and the gaps
 * never moved. Saving now asks which day; these drive the real path (coverage, the Forge, the day sheet, the
 * save) and read the page's own rule back, and check the quiet line for rites the page leaves out.
 */
@RunWith(AndroidJUnit4::class)
class CoverageSaveDayTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val driver by lazy { FlowDriver(compose) }

    private val app
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp

    private val press = PlannedEntry(
        "Bench Press", sets = 3, reps = 8, targetWeightKg = 60.0,
        setsMin = 2, setsMax = 3, effort = EffortCue.RIR_0_2, repLow = 6, repHigh = 10,
    )

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
        ProgramAnswersStore.saveEquipment(app, Equipment.FULL_GYM)
        // A board this test owns: the starter week would hold every day of the week.
        runBlocking {
            val repo = app.repository
            repo.observePresets().first().forEach { repo.deletePreset(it.id) }
        }
        driver.takeTheClock()
    }

    private fun saved(): List<WorkoutPreset> = runBlocking { app.repository.observePresets().first() }

    private fun fixture(name: String, day: Int?): Long =
        runBlocking { app.repository.savePlannedPreset(null, PlannedPreset(name, "", day, listOf(press))) }

    private fun gaps(): Int {
        val counted = scheduledRoutine(saved()) { it.scheduledDay }.map { it.toPlanned() }
        return coverageTally(counted).gaps
    }

    private fun await(what: String, attempts: Int = 80, condition: () -> Boolean) {
        repeat(attempts) {
            if (condition()) return
            compose.mainClock.advanceTimeBy(500)
            Thread.sleep(100)
        }
        error("timed out waiting for $what")
    }

    /** The sheet and the dock under it both say "Add this rite"; the sheet is the later window. */
    private fun clickLast(label: String) {
        compose.onAllNodesWithText(label).onLast().performSemanticsAction(SemanticsActions.OnClick)
        driver.settle()
    }

    @Test
    fun savingFromCoverageAsksForADayAndTheGapsMove() {
        fixture("Fixture Push", day = 1)
        val gapsBefore = gaps()
        assertTrue("one bench-press rite leaves gaps", gapsBefore > 0)

        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                IronvellumTheme {
                    var page by remember { mutableStateOf("coverage") }
                    if (page == "coverage") {
                        MuscleCoverageScreen(onBack = {}, onGenerateSession = { page = "builder" })
                    } else {
                        ProgramBuilderScreen("session", null, onDone = { page = "coverage" })
                    }
                }
            }
        }
        driver.settle()
        driver.awaitText("Forge a rite")
        driver.click("Forge a rite")
        driver.awaitText("PROPOSED RITE")
        // The proposal waits for the catalogue; the dock appears with it.
        driver.awaitText("Add this rite")
        driver.click("Add this rite")

        driver.awaitText("Which day is")
        // Monday holds the fixture, so Tuesday is the first free day and it is ticked.
        driver.awaitText("Tuesday is free.")
        clickLast("Add this rite")

        await("the rite to be saved") { saved().size == 2 }
        val added = saved().first { it.name != "Fixture Push" }
        assertEquals("the new rite is on the first free day, not left with none", 2, added.scheduledDay)
        assertTrue("the gaps changed once the new rite counts: ${gaps()} vs $gapsBefore", gaps() < gapsBefore)
        driver.awaitText("Forge a rite")
    }

    @Test
    fun noDayIsOfferedAndLeavesTheRiteOutOfTheCount() {
        fixture("Fixture Push", day = 1)
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { IronvellumTheme { ProgramBuilderScreen("session", null, onDone = {}) } }
        }
        driver.settle()
        driver.awaitText("Add this rite")
        driver.click("Add this rite")
        driver.awaitText("Which day is")
        driver.click("No day")
        driver.awaitText("A rite with no day is not counted in Weekly coverage.")
        clickLast("Add this rite")
        await("the rite to be saved") { saved().size == 2 }
        assertEquals(null, saved().first { it.name != "Fixture Push" }.scheduledDay)
    }

    @Test
    fun theQuietLineNamesRitesWithNoDayAndOpensThem() {
        fixture("Fixture Push", day = 1)
        val loose = fixture("Fixture Loose", day = null)
        var openedRite: Long? = null
        var openedList = 0
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                IronvellumTheme {
                    MuscleCoverageScreen(
                        onBack = {}, onGenerateSession = {},
                        onOpenRite = { openedRite = it }, onOpenRites = { openedList++ },
                    )
                }
            }
        }
        driver.settle()
        driver.awaitText("1 rite has no day, so it isn't counted")
        driver.click("1 rite has no day, so it isn't counted")
        assertEquals(loose, openedRite)
        assertEquals(0, openedList)

        // A second loose rite: the line counts both and goes to the list.
        fixture("Fixture Loose Two", day = null)
        driver.awaitText("2 rites have no day, so they aren't counted")
        driver.click("2 rites have no day, so they aren't counted")
        assertEquals(1, openedList)
        assertNotNull(openedRite)
    }
}
