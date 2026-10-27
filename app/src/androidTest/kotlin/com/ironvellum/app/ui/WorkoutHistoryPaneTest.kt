package com.ironvellum.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.WorkoutLogScreen
import com.ironvellum.app.ui.train.WorkoutDetailViewModel
import com.ironvellum.app.ui.train.WorkoutLogViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WorkoutHistoryPaneTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "history-pane-${UUID.randomUUID()}.db"
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private lateinit var logViewModel: WorkoutLogViewModel
    private val ids = mutableListOf<Long>()
    private val names = List(2) { "Pane ${UUID.randomUUID()}" }
    private val widthDp = mutableFloatStateOf(1000f)
    private var openedId: Long? = null
    private var leftLog = false

    @Before fun seed() = runBlocking {
        db = IronvellumDatabase.create(context, databaseName)
        repo = Repository(db)
        val dao = db.sessionDao()
        val now = System.currentTimeMillis() + 86_400_000L
        names.forEachIndexed { index, name ->
            ids += dao.insertSession(SessionEntity(
                presetId = null, label = name, startedAtMs = now - index * 60_000L,
                completedAtMs = now - index * 60_000L + 1_000L, xpAwarded = 0,
            ))
        }
        compose.runOnUiThread {
            fun <T : ViewModel> register(key: String, type: Class<T>, create: () -> T): T {
                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
                }
                return ViewModelProvider(compose.activity, factory)[key, type]
            }
            logViewModel = register("test-workout-log", WorkoutLogViewModel::class.java) {
                WorkoutLogViewModel(repo)
            }
            ids.forEach { id ->
                register("workout-detail-$id", WorkoutDetailViewModel::class.java) {
                    WorkoutDetailViewModel(repo, id, context.applicationContext)
                }
            }
        }
    }

    @After fun cleanUp() {
        compose.runOnUiThread { compose.activity.viewModelStore.clear() }
        db.close()
        context.deleteDatabase(databaseName)
    }

    // Preserve the real pixel constraints while making the window's measured width
    // correspond to the requested dp width, including on a phone-sized test device.
    private fun show(restoration: StateRestorationTester? = null) {
        val paneLight = InstrumentationRegistry.getArguments().getString("paneLight") == "true"
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            val pixels = LocalWindowInfo.current.containerSize.width
            val fontScale = LocalDensity.current.fontScale
            CompositionLocalProvider(LocalDensity provides Density(pixels / widthDp.floatValue, fontScale)) {
                IronvellumTheme(light = paneLight) {
                    Box(Modifier.fillMaxSize().background(IronvellumColors.Abyss)) {
                        WorkoutLogScreen(
                            onOpenWorkout = { openedId = it },
                            onBack = { leftLog = true },
                            viewModel = logViewModel,
                        )
                    }
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    private fun waitForCount(name: String, count: Int) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(name).fetchSemanticsNodes().size == count
        }
    }

    @Test fun expandedSelectionShowsDetailBesideListAndBackClearsIt() {
        show()
        waitForCount(names[0], 1)
        compose.onNodeWithText(names[1]).assertIsDisplayed()
        compose.onNodeWithText(names[0]).performClick()
        waitForCount(names[0], 2)
        val bounds = compose.onAllNodesWithText(names[0]).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals("the list and detail must be in separate horizontal panes", true,
            bounds.maxOf { it.left } > bounds.minOf { it.right })
        compose.onNodeWithText(names[1]).performClick()
        waitForCount(names[1], 2)
        waitForCount(names[0], 1)
        if (InstrumentationRegistry.getArguments().getString("shots") == "true") {
            val autoAdvance = compose.mainClock.autoAdvance
            ShotRecorder.take(compose, "expanded-workout-history-detail")
            compose.mainClock.autoAdvance = autoAdvance
        }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitForCount(names[1], 1)
        compose.onNodeWithText(names[0]).assertIsDisplayed()
        assertEquals(null, openedId)
        assertEquals(false, leftLog)
    }

    @Test fun narrowSelectionUsesExistingNavigationCallback() {
        widthDp.floatValue = 400f
        show()
        waitForCount(names[0], 1)
        compose.onNodeWithText(names[0]).performClick()
        compose.runOnIdle { assertEquals(ids[0], openedId) }
        waitForCount(names[0], 1)
    }

    @Test fun selectionSurvivesRestorationAndWidthChangeThenBackReturnsToList() {
        val restoration = StateRestorationTester(compose)
        show(restoration)
        waitForCount(names[0], 1)
        compose.onNodeWithText(names[0]).performClick()
        waitForCount(names[0], 2)
        restoration.emulateSavedInstanceStateRestore()
        waitForCount(names[0], 2)
        compose.runOnIdle { widthDp.floatValue = 400f }
        waitForCount(names[0], 1)
        compose.onNodeWithText(names[0]).assertIsDisplayed()
        compose.onAllNodesWithText(names[1]).assertCountEquals(0)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitForCount(names[1], 1)
        assertEquals(false, leftLog)
    }
}
