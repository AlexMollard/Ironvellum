package com.ironvellum.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.ui.components.ExerciseInfoSheet
import com.ironvellum.app.ui.theme.IronvellumTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The exercise sheet's History page: it is reachable by a swipe (a drag of a third of the screen is enough, not half),
 * lists the lifter's own sets from the real database, and says "not logged yet" for a movement never done.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseHistoryTabTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp
    private val seeded = mutableListOf<Long>()

    private lateinit var pullUp: Exercise
    private lateinit var neverDone: Exercise

    @Before
    fun seed() {
        TestProfile.ensureSetUp()
        runBlocking {
            val dao = app.database.sessionDao()
            val exercises = app.database.exerciseDao()
            val pull = exercises.byName("Pull-up")!!
            pullUp = Exercise(id = pull.id, name = pull.name, muscleGroup = MuscleGroup.PULL, isWeighted = false)
            val never = exercises.byName("Shrimp Squat")!!
            neverDone = Exercise(id = never.id, name = never.name, muscleGroup = MuscleGroup.LEGS, isWeighted = false)
            // Shrimp Squat must have no history on this install, whatever an earlier run left.
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM set_logs WHERE exerciseId = ${never.id}")
            val day = 24L * 60 * 60 * 1000
            val now = System.currentTimeMillis()
            for ((age, reps) in listOf(9 to listOf(8, 7), 2 to listOf(12, 10, 8))) {
                val started = now - age * day
                val id = dao.insertSession(
                    SessionEntity(presetId = null, label = "Pull Strength $age", startedAtMs = started, completedAtMs = started + 3_600_000, xpAwarded = 0),
                )
                seeded += id
                dao.insertSets(
                    reps.mapIndexed { i, r ->
                        SetLogEntity(sessionId = id, exerciseId = pull.id, setIndex = i, reps = r, weightKg = 18.0, modifiers = "weighted", done = true)
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

    private fun show(exercise: Exercise) {
        compose.setContent {
            IronvellumTheme { ExerciseInfoSheet(exercise = exercise, lastLine = null, onDismiss = {}, onPick = null) }
        }
        compose.waitForIdle()
    }

    private fun swipePages(from: Float, to: Float, durationMs: Long) {
        compose.onAllNodes(isRoot()).onLast().performTouchInput {
            swipe(Offset(width * from, height * 0.55f), Offset(width * to, height * 0.55f), durationMs)
        }
        compose.waitForIdle()
    }

    @Test
    fun aSlowDragOfAThirdOfTheScreenChangesPage() {
        show(pullUp)
        swipePages(0.65f, 0.35f, 1500)
        compose.onNodeWithText("HOW TO").assertExists()
    }

    @Test
    fun swipingTwoPagesLandsOnHistoryWithTheSetsThatWereLogged() {
        show(pullUp)
        compose.onAllNodes(isRoot()).onLast().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onLast().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("TIMES DONE")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("+18 kg × 12, 10, 8").assertExists()
        compose.onNodeWithText("+18 kg × 8, 7").assertExists()
    }

    @Test
    fun aMovementNeverLoggedSaysSo() {
        show(neverDone)
        // The tab reads History in plain words and Chronicle in themed ones; either is the third tab.
        compose.onNode(hasText("History") or hasText("Chronicle")).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Not logged yet")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Not logged yet").assertExists()
    }
}
