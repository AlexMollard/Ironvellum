package com.ironvellum.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
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
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The exercise sheet's four pages fit the sheet on the owner's phone (411x891dp) at the default text size with no
 * scrolling, dark and light: Pull-up on About, Form and Fatigue, a weighted lift with a logged history on History.
 * The About page is given a "why do it" at the writers' caps (a purpose of about 120 characters, a pick-it of about
 * 140), the longest the catalogue will hold. At a larger text size a page may scroll, so that is only checked for
 * being there. With `-Pandroid.testInstrumentationRunnerArguments.shots=true` each run also saves the sheet as a PNG
 * in /data/local/tmp/optimise-shots for a human to look at (see [ShotRecorder]).
 */
@RunWith(AndroidJUnit4::class)
class ExerciseInfoSheetFitsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp
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
        if (!light) compose.activityRule.scenario.onActivity { ShotRecorder.darkWindow(it) }
        compose.setContent {
            IronvellumTheme(light = light) {
                ExerciseInfoSheet(exercise = exercise, lastLine = lastLine, onDismiss = {}, onPick = {}, why = when (exercise.name) { "Pull-up" -> worstWhy; "Hammer Curl" -> curlWhy; else -> null })
            }
        }
        compose.waitForIdle()
    }

    /** Runs [block] with the system text size at [scale] (the sheet is a window of its own, so only the system size reaches it), then puts it back. */
    private fun withSystemFontScale(scale: String, block: () -> Unit) {
        fun shell(command: String) {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        shell("settings put system font_scale $scale")
        try {
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            block()
        } finally {
            shell("settings put system font_scale 1.0")
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
    }

    /** How far the page behind the open tab can still scroll, in px; zero when all of it is on screen. */
    private fun scrollRoom(): Float {
        val withRange = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        return compose.onAllNodes(withRange).fetchSemanticsNodes().maxOfOrNull {
            it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
        } ?: 0f
    }

    private fun assumePhone() {
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assumeTrue("needs a 411x891dp screen, this one is ${config.screenWidthDp}x${config.screenHeightDp}", config.screenHeightDp in 880..900 && config.screenWidthDp in 400..420)
    }

    /** Opens [tab], asserts the page has nothing to scroll to, then takes the picture [shot]. */
    private fun assertFits(tab: String, shot: String) {
        assumePhone()
        openTab(tab)
        val room = scrollRoom()
        ShotRecorder.take(compose, shot)
        assertEquals("$tab scrolls by $room px (${room / app.resources.displayMetrics.density} dp) on a 411x891dp phone", 0f, room, 0.5f)
    }

    @Test
    fun aboutFitsWithItsWhyAtTheCaps() {
        show(pullUp)
        assertFits("About", "pullup-dark-about")
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
    fun aSparseAboutFitsToo() {
        show(hammerCurl, lastLine = "12 × 16 kg · 13 Oct")
        assertFits("About", "hammercurl-dark-about")
    }

    @Test
    fun lightAboutFits() {
        show(pullUp, light = true)
        assertFits("About", "pullup-light-about")
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
