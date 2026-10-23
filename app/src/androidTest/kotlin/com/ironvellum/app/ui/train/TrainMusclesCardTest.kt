package com.ironvellum.app.ui.train

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.domain.FatigueModel
import com.ironvellum.app.domain.FatigueSession
import com.ironvellum.app.domain.LoggedSet
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.ui.FlowDriver
import com.ironvellum.app.ui.TestProfile
import com.ironvellum.app.ui.program.FatigueUi
import com.ironvellum.app.ui.program.MusclesNowCard
import com.ironvellum.app.ui.program.MusclesView
import com.ironvellum.app.ui.theme.IronvellumTheme
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Train has no tab row; the "Muscles right now" card opens Muscles, its coverage line opens Coverage. */
@RunWith(AndroidJUnit4::class)
class TrainMusclesCardTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val driver by lazy { FlowDriver(compose) }

    private val hour = 3_600_000L
    private val now = 1_792_000_000_000L
    private val long = now - 400 * 24 * hour

    private fun sets(name: String, count: Int, at: Long) =
        List(count) { LoggedSet(name, "", 100.0, 5, 9, SetKind.WORKING, true, at) }

    private fun worked() = FatigueUi(
        FatigueModel.estimate(listOf(FatigueSession(1, now - 8 * hour, sets("Back Squat", 5, now - 8 * hour))), now, long),
        hasHistory = true, loaded = true,
    )

    private fun rested() = FatigueUi(FatigueModel.estimate(emptyList(), now, long), hasHistory = true, loaded = true)

    private fun show(ui: FatigueUi, onOpen: () -> Unit = {}, onCoverage: () -> Unit = {}) {
        compose.activityRule.scenario.onActivity {
            it.setContent {
                IronvellumTheme {
                    MusclesNowCard(ui, "This week: 2 muscles short, 1 heavy", ZoneId.of("UTC"), onOpen, onCoverage)
                }
            }
        }
        driver.settle()
    }

    private fun showTrain(onOpenMuscles: (MusclesView) -> Unit = {}) {
        compose.activityRule.scenario.onActivity {
            it.setContent {
                IronvellumTheme {
                    TrainScreen(
                        onOpenRite = {}, onNew = {}, onGenerate = { _, _ -> }, onStartSession = {},
                        onQuickSession = {}, onOpenExercises = {}, onOpenLog = {}, onOpenMuscles = onOpenMuscles,
                    )
                }
            }
        }
        driver.settle()
    }

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
        driver.takeTheClock()
    }

    @Test
    fun theCardNamesTheTopWorkedMusclesAndOneCoverageLine() {
        show(worked())
        driver.awaitText("MUSCLES RIGHT NOW")
        driver.awaitText("Quads")
        driver.awaitText("This week: 2 muscles short, 1 heavy")
    }

    @Test
    fun theTopOfTheCardOpensFatigueAndTheCoverageLineOpensCoverage() {
        var opened = ""
        show(worked(), onOpen = { opened = "fatigue" }, onCoverage = { opened = "coverage" })
        driver.click("Quads")
        assertEquals("fatigue", opened)
        driver.click("This week: 2 muscles short, 1 heavy")
        assertEquals("coverage", opened)
    }

    @Test
    fun aRestedBodySaysNoMuscleLooksWorked() {
        show(rested())
        driver.awaitText("No muscle looks worked")
    }

    @Test
    fun noHistoryPointsAtLoggingAWorkout() {
        show(FatigueUi(FatigueModel.estimate(emptyList(), now, null), hasHistory = false, loaded = true))
        driver.awaitText("muscles will show up here")
    }

    @Test
    fun trainShowsNoTabRow() {
        showTrain()
        driver.awaitText("MUSCLES RIGHT NOW")
        val tabs = compose.onAllNodes(SemanticsMatcher("is a tab") { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab })
        assertEquals(0, tabs.fetchSemanticsNodes().size)
    }

    @Test
    fun theCardOnTrainOpensTheViewTheLinkNames() {
        var view: MusclesView? = null
        showTrain { view = it }
        driver.awaitText("This week:")
        compose.onAllNodesWithText("This week:", substring = true).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(MusclesView.COVERAGE, view)
        driver.click("MUSCLES RIGHT NOW")
        assertEquals(MusclesView.FATIGUE, view)
    }
}
