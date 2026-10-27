package com.ironvellum.app.ui.train

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.SeededDb
import com.ironvellum.app.ui.program.MuscleCoverageViewModel
import com.ironvellum.app.ui.program.MuscleFatigueViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import java.time.LocalDate
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
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.TestProfile
import com.ironvellum.app.ui.program.FatigueUi
import com.ironvellum.app.ui.program.MusclesNowCard
import com.ironvellum.app.ui.program.MusclesView
import com.ironvellum.app.ui.theme.IronvellumColors
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

    private fun showTrainAtWidth(
        widthDp: Float,
        repo: Repository,
        onRite: (Long) -> Unit,
        onMuscles: (MusclesView) -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val planModel = PresetsViewModel(repo, context)
        val fatigueModel = MuscleFatigueViewModel(repo)
        val coverageModel = MuscleCoverageViewModel(repo)
        val paneLight = InstrumentationRegistry.getArguments().getString("paneLight") == "true"
        compose.activityRule.scenario.onActivity {
            it.setContent {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val actualDensity = LocalDensity.current
                    val actualWindow = LocalWindowInfo.current
                    val widthPx = with(actualDensity) { maxWidth.roundToPx() }
                    val heightPx = with(actualDensity) { maxHeight.roundToPx() }
                    val testDensity = Density(widthPx / widthDp, actualDensity.fontScale)
                    val window = object : WindowInfo by actualWindow {
                        override val containerSize = IntSize(widthPx, heightPx)
                    }
                    CompositionLocalProvider(LocalDensity provides testDensity, LocalWindowInfo provides window) {
                        IronvellumTheme(light = paneLight) {
                            Box(Modifier.fillMaxSize().background(IronvellumColors.Abyss)) {
                                PresetsScreen(
                                    onOpenRite = onRite, onNew = {}, onGenerate = { _, _ -> },
                                    onStartSession = {}, onQuickSession = {}, onOpenExercises = {},
                                    onOpenLog = {}, onOpenMuscles = onMuscles,
                                    viewModel = planModel, fatigueModel = fatigueModel, coverageModel = coverageModel,
                                )
                            }
                        }
                    }
                }
            }
        }
        driver.settle()
    }

    private fun checkTrainPlan(widthDp: Float, expanded: Boolean) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "train-pane-${java.util.UUID.randomUUID()}.db"
        SeededDb.fresh(context, dbName)
        val db = IronvellumDatabase.create(context, dbName)
        val repo = Repository(db)
        repo.ensureSeeded()
        val id = repo.savePreset(null, "Pane fixture workout", "", LocalDate.now().dayOfWeek.value, emptyList())
        try {
            var opened = -1L
            var view: MusclesView? = null
            showTrainAtWidth(widthDp, repo, { opened = it }, { view = it })
            driver.awaitText("Pane fixture workout")
            driver.awaitText("MUSCLES RIGHT NOW")
            val plan = compose.onAllNodesWithText("Pane fixture workout").onFirst()
            val muscles = compose.onNodeWithText("MUSCLES RIGHT NOW")
            if (expanded) {
                plan.assertIsDisplayed()
                muscles.assertIsDisplayed()
                assertTrue(plan.getUnclippedBoundsInRoot().left < muscles.getUnclippedBoundsInRoot().left)
                if (InstrumentationRegistry.getArguments().getString("shots") == "true") {
                    ShotRecorder.take(compose, "expanded-train-plan-muscles")
                }
            }
            if (!expanded) plan.performScrollTo()
            plan.performClick()
            assertEquals(id, opened)
            if (!expanded) muscles.performScrollTo()
            muscles.performClick()
            assertEquals(MusclesView.FATIGUE, view)
        } finally {
            compose.activityRule.scenario.onActivity { it.setContent {} }
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun expandedTrainPlacesMusclesBesideTheWeekPlan() = checkTrainPlan(1000f, true)

    @Test
    fun phoneTrainKeepsPlanAndMusclesActions() = checkTrainPlan(400f, false)
}
