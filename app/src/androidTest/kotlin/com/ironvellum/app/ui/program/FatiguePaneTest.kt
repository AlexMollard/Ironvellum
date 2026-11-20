package com.ironvellum.app.ui.program

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.filter
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.ironvellum.app.domain.mostWorked
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironvellum.app.domain.FatigueModel
import com.ironvellum.app.domain.FatigueSession
import com.ironvellum.app.domain.LoggedSet
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.ui.FlowDriver
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.TestProfile
import com.ironvellum.app.ui.theme.IronvellumTheme
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Fatigue pane on a stub report: the figure's words, the five-level legend, Most worked and the empty copy. */
@RunWith(AndroidJUnit4::class)
class FatiguePaneTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val driver by lazy { FlowDriver(compose) }

    private val hour = 3_600_000L
    private val now = 1_792_000_000_000L
    private val long = now - 400 * 24 * hour

    private fun sets(name: String, count: Int, at: Long) =
        List(count) { LoggedSet(name, "", 100.0, 5, 9, SetKind.WORKING, true, at) }

    private fun session(id: Long, hoursAgo: Long, name: String, count: Int) =
        FatigueSession(id, now - hoursAgo * hour, sets(name, count, now - hoursAgo * hour))

    private fun busy() = FatigueModel.estimate(
        listOf(session(1, 30, "Back Squat", 5), session(2, 8, "Romanian Deadlift", 4)),
        now, long,
    )

    @Before
    fun setUp() {
        TestProfile.ensureSetUp()
        driver.takeTheClock()
    }

    private fun show(ui: FatigueUi) {
        compose.activityRule.scenario.onActivity {
            it.setContent { IronvellumTheme { FatigueContent(ui, mutableStateOf<Muscle?>(null), ZoneId.of("UTC")) } }
        }
        driver.settle()
    }

    private fun descriptions(): List<String> =
        compose.onAllNodes(
            SemanticsMatcher("has description") { it.config.getOrNull(SemanticsProperties.ContentDescription) != null },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }

    @Test
    fun theFigureNamesTheWorkedMusclesAndTheLegendHasFiveLevels() {
        show(FatigueUi(busy(), hasHistory = true, loaded = true))
        val summary = descriptions().first { it.startsWith("Muscle fatigue") }
        assertTrue(summary, summary.contains("quads") || summary.contains("hamstrings"))
        val text = driver.allText()
        listOf("Fresh", "Light", "Moderate", "High", "Very high").forEach { assertTrue("$it in $text", it in text) }
        assertTrue("Estimated from your last 7 days" in text)
    }

    @Test
    fun mostWorkedShowsAtMostFourRowsEachWithACauseAndAFreshDay() {
        show(FatigueUi(busy(), hasHistory = true, loaded = true))
        driver.awaitText("MOST WORKED")
        val rows = driver.allText().filter { it.startsWith("Fresh by") || it.startsWith("Fresh later") }
        assertTrue("rows: $rows", rows.size in 1..4)
        assertTrue(driver.allText().any { it.contains("from") && it.contains("Romanian Deadlift") })
    }

    @Test
    fun largeTextCardKeepsMuscleAndFreshDateWholeAndClickable() {
        val report = busy()
        val reading = report.mostWorked(ZoneId.of("UTC"), 3).first()
        val muscle = reading.muscle.label
        val fresh = requireNotNull(freshByLine(reading.freshBy))
        var opened = false
        compose.activityRule.scenario.onActivity {
            it.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    IronvellumTheme {
                        Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState())) {
                            MusclesNowCard(FatigueUi(report, hasHistory = true, loaded = true), null, ZoneId.of("UTC"), { opened = true }, {})
                        }
                    }
                }
            }
        }
        driver.settle()
        ShotRecorder.take(compose, "fatigue-card-large-text")
        for (text in listOf(muscle, fresh)) {
            val layouts = mutableListOf<TextLayoutResult>()
            val node = compose.onAllNodes(hasText(text), useUnmergedTree = true)
                .filter(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)).onFirst().fetchSemanticsNode()
            val measured = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            assertTrue("$text was not measured: $measured", layouts.isNotEmpty())
            assertTrue("$text is clipped or ellipsized", layouts.all { layout ->
                layout.lineCount > 0 &&
                    layout.getLineEnd(layout.lineCount - 1, visibleEnd = true) == layout.layoutInput.text.length &&
                    (0 until layout.lineCount).none(layout::isLineEllipsized)
            })
        }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(opened)
    }

    @Test
    fun aNewInstallSaysHowMusclesWillAppear() {
        show(FatigueUi(FatigueModel.estimate(emptyList(), now, null), hasHistory = false, loaded = true))
        driver.awaitText("Log a ")
        assertTrue(driver.allText().none { it == "MOST WORKED" })
    }

    @Test
    fun aRestedLifterIsToldNothingIsWorked() {
        show(FatigueUi(FatigueModel.estimate(emptyList(), now, long), hasHistory = true, loaded = true))
        driver.awaitText("No muscle looks worked")
        assertEquals(0, driver.allText().count { it == "MOST WORKED" })
    }
}
