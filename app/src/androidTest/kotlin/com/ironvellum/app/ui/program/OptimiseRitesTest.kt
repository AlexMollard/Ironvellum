package com.ironvellum.app.ui.program

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.ProgramAnswersStore
import com.ironvellum.app.domain.EffortCue
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.PlannedEntry
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.ProgramGenerator
import com.ironvellum.app.domain.ProgramRequest
import com.ironvellum.app.domain.TrainingFocus
import com.ironvellum.app.domain.VolumeLevel
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.ui.FlowDriver
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.TestProfile
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Optimise rites on the real screen, from the Weekly coverage dock: the hub, a gap's movements and the rite it
 * goes into, a heavy muscle's changes (one opened), two movements doing one job, what was applied and Undo.
 * Each step reads the saved rites back, so a write that did not land (or an Undo that did not restore) fails
 * here, and each saves a real device capture for a human to look at.
 */
@RunWith(AndroidJUnit4::class)
class OptimiseRitesTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val driver by lazy { FlowDriver(compose) }

    private val app
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp

    private fun entry(name: String, sets: Int) = PlannedEntry(
        name, sets, 8, null, setsMin = 2, setsMax = sets, effort = EffortCue.RIR_0_2, repLow = 6, repHigh = 10,
    )

    /** Heavy on front delts (past 10 in one rite), one pair of rows doing one job, and gaps below the waist. */
    private val fixture = listOf(
        PlannedPreset("Upper A", "", 1, listOf(entry("Overhead Press", 7), entry("Incline Bench Press", 6), entry("Bench Press", 5))),
        PlannedPreset("Pull A", "", 2, listOf(entry("Dumbbell Row", 4), entry("Seated Cable Row", 3), entry("Lat Pulldown", 3))),
        PlannedPreset("Lower A", "", 4, listOf(entry("Back Squat", 4))),
    )

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
        ProgramAnswersStore.saveEquipment(app, Equipment.FULL_GYM)
        replaceRites(fixture)
        driver.takeTheClock()
    }

    private fun mondayVm() = com.ironvellum.app.ui.program.MuscleCoverageViewModel(
        app.repository,
        nowMs = { java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY).atTime(8, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() },
    )

    private fun replaceRites(week: List<PlannedPreset>) = runBlocking {
        val repo = app.repository
        repo.observePresets().first().forEach { repo.deletePreset(it.id) }
        week.forEach { repo.savePlannedPreset(null, it) }
    }

    private fun saved(): List<WorkoutPreset> = runBlocking { app.repository.observePresets().first() }

    /** What the rites hold, as one comparable value: name, day and each movement with its sets. */
    private fun snapshot(): List<String> =
        saved().sortedBy { it.name }.map { rite ->
            "${rite.name}@${rite.scheduledDay}:" + rite.entries.joinToString(",") { "${it.exerciseName}x${it.targetSets}" }
        }

    private fun show(start: String = "coverage") {
        compose.activityRule.scenario.onActivity { activity ->
            ShotRecorder.darkWindow(activity)
            activity.setContent {
                IronvellumTheme {
                    var page by remember { mutableStateOf(start) }
                    Box(Modifier.fillMaxSize().background(IronvellumColors.Abyss).statusBarsPadding()) {
                        if (page == "coverage") {
                            MuscleCoverageScreen(onBack = {}, onGenerateSession = {}, onOptimise = { page = "optimise" }, viewModel = remember { mondayVm() })
                        } else {
                            OptimiseScreen(onBack = { page = "coverage" }, onForgeCycle = {})
                        }
                    }
                }
            }
        }
        driver.settle()
    }

    /** Every state that is pictured is also checked: each tappable control says what it is and is 48dp. */
    private fun shot(name: String) {
        val density = compose.density.density
        val offenders = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { it.size.width > 0 }.mapNotNull { node ->
            val label = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
                ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
            when {
                label.none { it.isLetterOrDigit() } -> "unlabelled control at ${node.boundsInRoot}"
                node.size.height < 48 * density - 1 || node.size.width < 48 * density - 1 ->
                    "\"$label\" is ${node.size.width / density}x${node.size.height / density} dp"
                else -> null
            }
        }
        assertEquals("$name: controls that cannot be announced or are under 48dp", emptyList<String>(), offenders)
        ShotRecorder.take(compose, name)
    }

    private fun await(what: String, attempts: Int = 80, condition: () -> Boolean) {
        repeat(attempts) {
            if (condition()) return
            compose.mainClock.advanceTimeBy(500)
            Thread.sleep(100)
        }
        error("timed out waiting for $what")
    }

    private fun descriptions(): List<String> =
        compose.onAllNodes(SemanticsMatcher("has description") { it.config.contains(SemanticsProperties.ContentDescription) }, useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { node ->
                (node.config.getOrNull(SemanticsProperties.ContentDescription) ?: emptyList())
            }

    private fun awaitDescription(fragment: String) {
        await("a control described \"$fragment\"") { descriptions().any { it.contains(fragment) } }
    }

    private fun clickDescription(fragment: String) {
        awaitDescription(fragment)
        compose.onAllNodes(
            SemanticsMatcher("described $fragment") {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.contains(fragment) } == true
            },
        ).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        driver.settle()
    }

    private fun clickTextStarting(prefix: String, except: Set<String> = emptySet()) {
        val text = driver.awaitAnyText { it.startsWith(prefix) && it !in except }
        driver.click(text)
    }

    private fun openOptimise() {
        show()
        driver.awaitText("Optimise rites")
        driver.click("Optimise rites")
        driver.awaitText("Suggestions change the rites you already have")
    }

    @Test
    fun theCoverageDockOffersBothActionsAndTheHubListsWhatItFound() {
        show()
        driver.awaitText("Forge a rite")
        driver.awaitText("Optimise rites")
        // The coverage figure's muscle hotspots are the page's own and are not under test here.
        ShotRecorder.take(compose, "optimise-01-coverage-dock")
        driver.click("Optimise rites")
        driver.awaitText("Suggestions change the rites you already have")
        // Every finding is a word as well as a mark.
        driver.awaitText("Not trained")
        driver.awaitText("Heavy")
        driver.awaitText("Two movements doing one job")
        shot("optimise-02-hub")
    }

    @Test
    fun aGapOffersThreeRankedMovementsThenAskedWhichRiteAndApplyingCanBeUndone() {
        val before = snapshot()
        openOptimise()
        driver.click("Calves")
        awaitDescription("Option 1 of")
        awaitDescription("best match")
        shot("optimise-03-gap-sheet")
        clickTextStarting("Add ", except = setOf("Add a movement"))
        driver.awaitText("Put it on")
        driver.awaitText("Effect on your week")
        shot("optimise-04-gap-where")
        clickTextStarting("Add to ")
        driver.awaitText("1 change applied")
        shot("optimise-05-applied")
        assertNotEquals("the rite really changed", before, snapshot())
        assertEquals("one rite got the movement", 1, snapshot().zip(before).count { (now, was) -> now != was })

        driver.click(driver.allText().first { it.startsWith("Undo, put") })
        driver.awaitText("Suggestions change the rites you already have")
        await("the undo to land") { snapshot() == before }
        shot("optimise-06-after-undo")
    }

    @Test
    fun aHeavyMuscleShowsItsChangesOneOpensAndKeepingSomeApplies() {
        val before = snapshot()
        openOptimise()
        driver.click("Front delts")
        driver.awaitText("What it fixes")
        shot("optimise-07-heavy")
        clickDescription("Open change 1")
        driver.awaitText("Effect on your week")
        shot("optimise-08-heavy-change")
        clickDescription("Keep this change")
        driver.awaitText("What it fixes")
        shot("optimise-09-heavy-kept")
        clickTextStarting("Apply ")
        driver.awaitAnyText { it.endsWith("applied") }
        shot("optimise-10-heavy-applied")
        assertNotEquals(before, snapshot())
        driver.click(driver.allText().first { it.startsWith("Undo, put") })
        await("the undo to land") { snapshot() == before }
    }

    @Test
    fun twoMovementsDoingOneJobCanBeSwappedAndSkippedChangesStayAsTheyAre() {
        val before = snapshot()
        openOptimise()
        driver.click("Pull A")
        driver.awaitText("Two movements, one job")
        awaitDescription("Option 1 of")
        shot("optimise-11-overlap")
        // Skip it: nothing may be written.
        clickDescription("Skip change 1")
        driver.awaitText("Apply")
        compose.mainClock.advanceTimeBy(400)
        assertEquals(before, snapshot())
        clickDescription("Keep change 1")
        clickTextStarting("Apply ")
        driver.awaitText("1 change applied")
        val after = snapshot()
        assertNotEquals(before, after)
        assertTrue("Dumbbell Row stays", after.any { it.startsWith("Pull A") && it.contains("Dumbbell Row") })
        assertTrue("the weaker row went", after.none { it.startsWith("Pull A") && it.contains("Seated Cable Row") })
    }

    @Test
    fun aWeekWithNothingToChangeSaysSo() {
        // A week the Forge just made, with its own overlaps swapped out: nothing left to suggest.
        val repo = app.repository
        val catalogue = runBlocking { repo.observeExercises().first() }
        val strength = runBlocking { repo.strengthProfile() }
        val request = ProgramRequest(TrainingFocus.MUSCLE, VolumeLevel.STANDARD, Equipment.FULL_GYM, daysPerWeek = 4)
        replaceRites(ProgramGenerator.week(request, catalogue, strength).presets)
        repeat(8) {
            val report = buildReport(OptimiseInput(saved(), request, catalogue, strength))
            val week = report.input.week
            // Swap an overlap out, or give a gap its best movement in its best rite, until nothing is left.
            val after = report.overlaps.flatMap { it.changes }.firstOrNull()?.let { resolveWeek(week, listOf(it.options.first())) }
                ?: report.gaps.firstNotNullOfOrNull { muscle ->
                    report.gapOptions.getValue(muscle).options.firstOrNull()?.let { candidate ->
                        val entry = gapEntry(report.input, candidate)
                        withAdded(week, riteChoices(report.input, entry).first().index, entry)
                    }
                }
                ?: return@repeat
            val touched = week.indices.filter { after[it] != week[it] }
            runBlocking { repo.savePlannedPresets(touched.map { report.input.ids[it] to after[it] }) }
        }
        val report = buildReport(OptimiseInput(saved(), request, catalogue, strength))
        assertTrue("fixture week has nothing left: gaps ${report.gaps} heavy ${report.heavy.map { it.muscle }} overlaps ${report.overlaps.size} spread ${report.spread != null}", report.nothing)

        // With nothing to fix the coverage dock is gone too, so the screen is opened directly.
        show(start = "optimise")
        driver.awaitText("Nothing to change")
        shot("optimise-12-nothing")
    }
}
