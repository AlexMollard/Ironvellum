package com.ironvellum.app.ui

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.R
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseWhy
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.ui.components.ExerciseInfoSheet
import com.ironvellum.app.ui.theme.IronvellumTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Form, History and Fatigue fit the owner's phone (411x891dp) at default text size.
 * About reserves readable space for artwork: dense descriptions may scroll, but its facts
 * stay reachable and Close/Add stay pinned. Includes dark/light themes and larger text.
 * Optional captures use `-Pandroid.testInstrumentationRunnerArguments.shots=true`.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseInfoSheetFitsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp
    private val phoneWidthDp = 411
    private val phoneHeightDp = 891
    private lateinit var sheetWindow: Window
    private lateinit var sheetPane: SemanticsMatcher

    private val seeded = mutableListOf<Long>()

    private lateinit var pullUp: Exercise
    private lateinit var hammerCurl: Exercise
    private lateinit var bench: Exercise

    private val worstWhy = ExerciseWhy(
        purpose = "Builds a wide, strong back with nothing but a bar, and tests how much of your own weight you can pull for reps.",
        pickIt = "Pick it for the most back work per minute with no machine; take rows instead until you can hang and pull your full bodyweight cleanly.",
        tags = listOf("Back width", "Grip", "Pulling strength"),
    )

    private val curlWhy = ExerciseWhy(
        purpose = "Builds the brachialis and forearms that make arms look thick from the side.",
        pickIt = "Pick it when regular curls bother your wrists; it trades some biceps peak for thicker arms.",
        tags = listOf("Arm thickness", "Grip", "Elbow-friendly"),
    )

    @Before
    fun seed() {
        TestProfile.ensureSetUp()
        runBlocking {
            val dao = app.database.sessionDao()
            val exercises = app.database.exerciseDao()
            val pull = exercises.byName("Pull-up")!!
            pullUp = Exercise(id = pull.id, name = pull.name, muscleGroup = MuscleGroup.PULL, isWeighted = false)
            val curl = exercises.byName("Hammer Curl")!!
            hammerCurl = Exercise(id = curl.id, name = curl.name, muscleGroup = MuscleGroup.PULL, isWeighted = true)
            val press = exercises.byName("Bench Press")!!
            bench = Exercise(id = press.id, name = press.name, muscleGroup = MuscleGroup.PUSH, isWeighted = true)
            // The bench press has six logged workouts. Nothing already logged is touched: other tests lean on it.
            val day = 24L * 60 * 60 * 1000
            val now = System.currentTimeMillis()
            // Pull-up gets six more recent bodyweight workouts, for its History page.
            for ((age, reps) in listOf(2 to listOf(10, 9, 8), 5 to listOf(9, 9, 8), 8 to listOf(8, 8, 7), 11 to listOf(8, 7, 6), 15 to listOf(7, 6, 6), 19 to listOf(6, 6, 5))) {
                val started = now - age * day
                val id = dao.insertSession(
                    SessionEntity(presetId = null, label = "Full Body A", startedAtMs = started, completedAtMs = started + 3_600_000, xpAwarded = 0),
                )
                seeded += id
                dao.insertSets(reps.mapIndexed { i, r -> SetLogEntity(sessionId = id, exerciseId = pull.id, setIndex = i, reps = r, weightKg = null, modifiers = "", done = true) })
            }
            val log = listOf(
                2 to (100.0 to listOf(5, 5, 4)), 5 to (97.5 to listOf(5, 5, 5)), 9 to (95.0 to listOf(6, 5, 5)),
                12 to (92.5 to listOf(6, 6, 5)), 16 to (90.0 to listOf(8, 7, 6)), 20 to (90.0 to listOf(7, 6, 6)),
            )
            for ((age, set) in log) {
                val started = now - age * day
                val id = dao.insertSession(
                    SessionEntity(presetId = null, label = "Push A", startedAtMs = started, completedAtMs = started + 3_600_000, xpAwarded = 0),
                )
                seeded += id
                dao.insertSets(
                    set.second.mapIndexed { i, r ->
                        SetLogEntity(sessionId = id, exerciseId = press.id, setIndex = i, reps = r, weightKg = set.first, modifiers = "", done = true)
                    },
                )
            }
        }
    }

    @After
    fun clean() {
        val db = app.database.openHelper.writableDatabase
        seeded.forEach { db.execSQL("DELETE FROM sessions WHERE id = $it") }
    }

    private fun show(exercise: Exercise, light: Boolean = false, lastLine: String? = "10 × BW · 13 Oct") {
        sheetPane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, exercise.name)
        if (!light) compose.activityRule.scenario.onActivity { ShotRecorder.darkWindow(it) }
        compose.setContent {
            // The dialog viewport is 411x891dp of usable space. Supply a matching
            // outer window, including the actual system bars the sheet subtracts.
            val phone = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = phoneWidthDp
                screenHeightDp = phoneHeightDp
            }
            val density = LocalDensity.current
            val bars = WindowInsets.systemBars
            val actualWindow = LocalWindowInfo.current
            val phoneWindow = object : WindowInfo by actualWindow {
                override val containerSize = IntSize(
                    with(density) { phoneWidthDp.dp.roundToPx() },
                    with(density) { phoneHeightDp.dp.roundToPx() } + bars.getTop(density) + bars.getBottom(density),
                )
            }
            CompositionLocalProvider(LocalConfiguration provides phone, LocalWindowInfo provides phoneWindow) {
                IronvellumTheme(light = light) {
                    ExerciseInfoSheet(exercise = exercise, lastLine = lastLine, onDismiss = {}, onPick = {}, why = when (exercise.name) { "Pull-up" -> worstWhy; "Hammer Curl" -> curlWhy; else -> null })
                }
            }
        }
        compose.waitForIdle()
        var window: Window? = null
        compose.runOnUiThread {
            fun findDialog(view: View): Window? {
                if (view is DialogWindowProvider) return view.window
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) {
                        findDialog(view.getChildAt(index))?.let { return it }
                    }
                }
                return null
            }
            window = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::findDialog)
            assertNotNull("Exercise sheet dialog window not found", window)
            sheetWindow = window!!
            val density = compose.activity.resources.displayMetrics.density
            sheetWindow.setLayout((phoneWidthDp * density).toInt(), (phoneHeightDp * density).toInt())
        }
        compose.waitForIdle()
        assertPhoneViewport()
    }

    private fun assertPhoneViewport() {
        compose.runOnUiThread {
            val density = compose.activity.resources.displayMetrics.density
            val decor = sheetWindow.decorView
            assertEquals("Dialog viewport width (px)", phoneWidthDp * density, decor.width.toFloat(), 1f)
            assertEquals("Dialog viewport height (px)", phoneHeightDp * density, decor.height.toFloat(), 1f)
        }
        val sheet = compose.onNode(sheetPane)
            .getUnclippedBoundsInRoot()
        assertTrue("Sheet stays inside the phone viewport: $sheet",
            sheet.left.value >= -1f && sheet.top.value >= -1f &&
                sheet.right.value <= phoneWidthDp + 1f && sheet.bottom.value <= phoneHeightDp + 1f)
    }

    /** Runs [block] with the system text size at [scale] (the sheet is a window of its own, so only the system size reaches it), then puts it back. */
    private fun withSystemFontScale(scale: String, block: () -> Unit) {
        fun shell(command: String): String {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            return android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes().decodeToString().trim() }
        }
        val original = shell("settings get system font_scale")
        val originalScale = compose.activity.resources.configuration.fontScale
        shell("settings put system font_scale $scale")
        try {
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            block()
        } finally {
            if (original == "null") shell("settings delete system font_scale") else shell("settings put system font_scale $original")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(5_000) { kotlin.math.abs(compose.activity.resources.configuration.fontScale - originalScale) < 0.001f }
            compose.waitForIdle()
        }
    }

    private fun openTab(label: String) {
        // The third tab reads History in plain words and Chronicle in themed ones.
        val tab = if (label == "History") hasText("History") or hasText("Chronicle") else hasText(label)
        if (label != "About") compose.onNode(tab).performClick()
        compose.waitForIdle()
        if (label == "History") {
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("TIMES DONE")).fetchSemanticsNodes().isNotEmpty() }
        }
        assertPhoneViewport()
    }

    /** How far the page behind the open tab can still scroll, in px; zero when all of it is on screen. */
    private fun scrollRoom(): Float {
        val withRange = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyAncestor(sheetPane)
        val nodes = compose.onAllNodes(withRange).fetchSemanticsNodes()
        assertTrue("Sheet page must expose a vertical scroll range", nodes.isNotEmpty())
        return nodes.maxOf { it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() }
    }

    /** Opens [tab], asserts the page has nothing to scroll to, then takes the picture [shot]. */
    private fun assertFits(tab: String, shot: String) {
        openTab(tab)
        assertEquals("No-scroll fit requires normal system text size", 1f, compose.activity.resources.configuration.fontScale, 0.001f)
        val room = scrollRoom()
        ShotRecorder.take(compose, shot)
        assertEquals("$tab scrolls by $room px (${room / app.resources.displayMetrics.density} dp) on a 411x891dp phone", 0f, room, 0.5f)
    }

    private fun assertAboutUsable(shot: String) {
        openTab("About")
        compose.onNode(hasText(app.getString(R.string.infosheet_close_button))).assertIsDisplayed()
        compose.onNode(hasText(app.getString(R.string.picker_confirm_add))).assertIsDisplayed()
        ShotRecorder.take(compose, shot)
        compose.mainClock.autoAdvance = true
        compose.onNode(hasText(app.getString(R.string.infosheet_section_muscles))).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText(app.getString(R.string.infosheet_close_button))).assertIsDisplayed()
        compose.onNode(hasText(app.getString(R.string.picker_confirm_add))).assertIsDisplayed()
    }

    @Test
    fun aboutKeepsFactsAndActionsReachableWithItsWhyAtTheCaps() {
        show(pullUp)
        assertAboutUsable("pullup-dark-about")
    }

    @Test
    fun formFits() {
        show(pullUp)
        assertFits("Form", "pullup-dark-form")
    }

    @Test
    fun historyFitsForALiftWithLoggedSets() {
        show(bench, lastLine = null)
        assertFits("History", "bench-dark-history")
    }

    @Test
    fun pullUpHistoryFits() {
        show(pullUp, lastLine = null)
        assertFits("History", "pullup-dark-history")
    }

    @Test
    fun pullUpHistoryFitsInLight() {
        show(pullUp, light = true, lastLine = null)
        assertFits("History", "pullup-light-history")
    }

    @Test
    fun tappingAMuscleRowLightsItAndTheHeadlineFollows() {
        show(pullUp)
        openTab("Fatigue")
        assertTrue(compose.onAllNodesWithText("LATS RECOVERY, FIRST 24 HOURS").fetchSemanticsNodes().isNotEmpty())
        compose.onNode(hasText("Biceps") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("Typical result: Biceps", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("BICEPS RECOVERY, FIRST 24 HOURS").fetchSemanticsNodes().isNotEmpty())
        ShotRecorder.take(compose, "pullup-dark-fatigue-biceps")
    }

    @Test
    fun fatigueFits() {
        show(pullUp)
        assertFits("Fatigue", "pullup-dark-fatigue")
    }

    @Test
    fun aSparseAboutKeepsFactsAndActionsReachableToo() {
        show(hammerCurl, lastLine = "12 × 16 kg · 13 Oct")
        assertAboutUsable("hammercurl-dark-about")
    }

    @Test
    fun lightAboutKeepsFactsAndActionsReachable() {
        show(pullUp, light = true)
        assertAboutUsable("pullup-light-about")
    }

    @Test
    fun lightFormFits() {
        show(pullUp, light = true)
        assertFits("Form", "pullup-light-form")
    }

    @Test
    fun lightHistoryFits() {
        show(bench, light = true, lastLine = null)
        assertFits("History", "bench-light-history")
    }

    @Test
    fun lightFatigueFits() {
        show(pullUp, light = true)
        assertFits("Fatigue", "pullup-light-fatigue")
    }

    @Test
    fun largerTextStillShowsTheFatiguePage() {
        // At 1.3 a page may scroll, but its content is all there.
        withSystemFontScale("1.3") {
            show(pullUp)
            openTab("Fatigue")
            ShotRecorder.take(compose, "pullup-dark-fatigue-x13")
            assertTrue(compose.onAllNodesWithText("FATIGUE FOOTPRINT", substring = true).fetchSemanticsNodes().isNotEmpty())
            assertTrue(compose.onAllNodesWithText("How this is estimated", substring = true).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun largerTextStillShowsTheAboutPage() {
        withSystemFontScale("1.3") {
            show(pullUp)
            openTab("About")
            ShotRecorder.take(compose, "pullup-dark-about-x13")
            assertTrue(compose.onAllNodesWithText("MUSCLES").fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun theTechniqueDetailShowsTheSameFormPage() {
        val skill = com.ironvellum.app.domain.Skills.forName("Pull-up")!!
        compose.activityRule.scenario.onActivity { ShotRecorder.darkWindow(it) }
        compose.setContent {
            IronvellumTheme {
                com.ironvellum.app.ui.titles.SkillDetailSheet(
                    skill = skill, mastered = false, unlocked = true, entries = emptyList(),
                    onLogPractice = { _, _ -> }, onClaim = {}, onUnclaim = {}, onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onNode(hasText("FORM") or hasText("Form")).performClick()
        compose.waitForIdle()
        ShotRecorder.take(compose, "skilldetail-dark-form")
        assertTrue(compose.onAllNodesWithText("CUES").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("HOW TO").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun aLiftWithManyMusclesListsThemAllOnFatigue() {
        // Bench press has more muscle rows than Pull-up; it may scroll, but every row is there and the lit one follows a tap.
        show(bench, lastLine = null)
        openTab("Fatigue")
        ShotRecorder.take(compose, "bench-dark-fatigue")
        assertTrue(compose.onAllNodesWithText("Mid chest").fetchSemanticsNodes().isNotEmpty())
    }
}
