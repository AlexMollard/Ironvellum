package com.ironvellum.app.data

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.domain.blockKey
import com.ironvellum.app.ui.ShotRecorder
import com.ironvellum.app.ui.program.MuscleFatigueViewModel
import com.ironvellum.app.ui.theme.IronvellumTheme
import com.ironvellum.app.ui.train.SessionScreen
import com.ironvellum.app.ui.train.SessionViewModel
import org.junit.Rule
import org.junit.Assert.assertFalse
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** "Switch" in a live trial moves only the unticked sets of a block, splitting it when some are ticked. */
@RunWith(AndroidJUnit4::class)
class TwinSwitchTrialTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private var pullUp = 0L
    private var weightedPullUp = 0L
    private var dip = 0L
    private var sessionId = 0L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        pullUp = repo.exerciseIdByName("Pull-up")!!
        weightedPullUp = repo.exerciseIdByName("Weighted Pull-up")!!
        dip = repo.exerciseIdByName("Dip")!!
        sessionId = repo.startFreeformSession("Twin")
        repeat(4) { repo.addExtraSet(sessionId, pullUp, 5, 10.0, "weighted") }
        repeat(2) { repo.addExtraSet(sessionId, dip, 8, null, "") }
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private suspend fun sets() = repo.observeSessionSets(sessionId).first()

    private suspend fun tick(count: Int) {
        sets().filter { it.exerciseId == pullUp }.sortedBy { it.setIndex }.take(count)
            .forEach { repo.updateSet(it.id, it.reps, it.weightKg, done = true) }
    }

    @Test
    fun aBlockWithNothingTickedBecomesTheTwinWhole() = runBlocking {
        val result = repo.switchBlockToTwin(sessionId, repo.blockOf(sessionId, pullUp), weightedPullUp, "")
        assertEquals(Repository.TwinSwitch.MOVED, result)
        val moved = sets().filter { it.exerciseId == weightedPullUp }
        assertEquals(4, moved.size)
        assertTrue(moved.all { it.modifiers == "" && it.exercisePosition == 0 })
        assertEquals(listOf(0, 1, 2, 3), moved.map { it.setIndex }.sorted())
    }

    @Test
    fun tickedSetsKeepTheirExerciseAndTheRestBecomeABlockOfTheirOwn() = runBlocking {
        tick(2)
        val result = repo.switchBlockToTwin(sessionId, repo.blockOf(sessionId, pullUp), weightedPullUp, "")
        assertEquals(Repository.TwinSwitch.SPLIT, result)
        val all = sets()
        val kept = all.filter { it.exerciseId == pullUp }.sortedBy { it.setIndex }
        val moved = all.filter { it.exerciseId == weightedPullUp }.sortedBy { it.setIndex }
        assertEquals(listOf(0, 1), kept.map { it.setIndex })
        assertTrue(kept.all { it.done && it.modifiers == "weighted" && it.exercisePosition == 0 })
        assertEquals(listOf(0, 1), moved.map { it.setIndex })
        assertTrue(moved.none { it.done } && moved.all { it.modifiers == "" && it.exercisePosition == 1 })
        // The dip block made room: one place later, still after both.
        assertEquals(2, all.first { it.exerciseId == dip }.exercisePosition)
        assertEquals(listOf(pullUp, weightedPullUp, dip), all.sortedBy { it.exercisePosition }.map { it.exerciseId }.distinct())
    }

    @Test
    fun nothingMovesWhenEverySetIsTicked() = runBlocking {
        tick(4)
        assertEquals(Repository.TwinSwitch.NONE, repo.switchBlockToTwin(sessionId, repo.blockOf(sessionId, pullUp), weightedPullUp, ""))
        assertTrue(sets().none { it.exerciseId == weightedPullUp })
    }

    private companion object {
        const val TEST_DB = "twin_switch_test.db"
    }
}

/** The live dialog survives activity recreation without retargeting a repeated movement. */
@RunWith(AndroidJUnit4::class)
class TwinSwitchTrialRecreationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private lateinit var vm: SessionViewModel
    private val owner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
    private var sessionId = 0L
    private var pullUp = 0L
    private var weightedPullUp = 0L

    // Neither the workout service nor preference writes may reach the real app state.
    private fun isolated(base: Context) = object : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            base.getSharedPreferences("twin_recreation_$name", mode)
        override fun startForegroundService(service: Intent): ComponentName? = null
        override fun startService(service: Intent): ComponentName? = null
    }

    @Before fun setUp() = runBlocking {
        context.deleteSharedPreferences("twin_recreation_trial_prefs")
        context.deleteSharedPreferences("twin_recreation_trial")
        context.deleteDatabase(DB_NAME)
        SeededDb.fresh(context, DB_NAME)
        db = IronvellumDatabase.create(context, DB_NAME)
        repo = Repository(db)
        repo.ensureSeeded()
        pullUp = requireNotNull(repo.exerciseIdByName("Pull-up"))
        weightedPullUp = requireNotNull(repo.exerciseIdByName("Weighted Pull-up"))
        val dip = requireNotNull(repo.exerciseIdByName("Dip"))
        val preset = repo.savePreset(null, "Twin recreation", "", null, listOf(
            Repository.PresetDraftEntry(pullUp, 2, 5, null),
            Repository.PresetDraftEntry(dip, 1, 8, null),
            Repository.PresetDraftEntry(pullUp, 2, 7, null),
        ))
        sessionId = repo.startSessionFromPreset(preset)
        vm = SessionViewModel(repo, context, sessionId)
        ViewModelProvider(owner, viewModelFactory {
            initializer { MuscleFatigueViewModel(repo) }
        })[MuscleFatigueViewModel::class.java]
        Unit
    }

    @After fun tearDown() {
        owner.viewModelStore.clear()
        db.close()
        context.deleteDatabase(DB_NAME)
        context.deleteSharedPreferences("twin_recreation_trial_prefs")
        context.deleteSharedPreferences("twin_recreation_trial")
    }

    private fun show() {
        compose.activityRule.scenario.onActivity { activity ->
            val safe = isolated(activity)
            FirstRunPrefs.setNotificationsAsked(safe)
            activity.setContent {
                CompositionLocalProvider(
                    LocalContext provides safe,
                    LocalViewModelStoreOwner provides owner,
                ) {
                    IronvellumTheme {
                        SessionScreen(sessionId, onExit = {}, onSealed = {}, viewModel = vm)
                    }
                }
            }
        }
    }

    private fun awaitText(text: String) = compose.waitUntil(15_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun openAndRecreate() {
        show()
        compose.waitUntil(15_000) { vm.seenTwinModifiers.size == 3 }
        val block = runBlocking { repo.observeSessionSets(sessionId).first().first { it.exercisePosition == 2 }.blockKey }
        runBlocking { repo.setExerciseModifiers(sessionId, block, "weighted") }
        val text = context.getString(R.string.twin_switch_prompt, "Weighted Pull-up")
        awaitText(text)
        compose.activityRule.scenario.recreate()
        show()
        awaitText(text)
        if (InstrumentationRegistry.getArguments().getString("shots") == "true") {
            val autoAdvance = compose.mainClock.autoAdvance
            try {
                ShotRecorder.take(compose, "session-twin-after-recreation")
            } finally {
                compose.mainClock.autoAdvance = autoAdvance
            }
        }
    }

    @Test fun switchAfterRecreationMovesOnlyTheOriginalBlock() {
        openAndRecreate()
        compose.onNodeWithText(context.getString(R.string.twin_switch_button)).performClick()
        compose.waitUntil(15_000) {
            runBlocking { repo.observeSessionSets(sessionId).first().count { it.exerciseId == weightedPullUp } == 2 }
        }
        val rows = runBlocking { repo.observeSessionSets(sessionId).first() }
        assertEquals(2, rows.count { it.exerciseId == pullUp && it.exercisePosition == 0 && it.reps == 5 })
        assertEquals(2, rows.count { it.exerciseId == weightedPullUp && it.exercisePosition == 2 && it.reps == 7 && it.modifiers.isEmpty() })
        assertFalse(rows.any { it.exerciseId == pullUp && it.exercisePosition == 2 })
        compose.activityRule.scenario.recreate()
        show()
        compose.waitUntil(15_000) { vm.seenTwinModifiers.keys.any { it.exerciseId == weightedPullUp } }
        assertEquals(0, compose.onAllNodes(hasText(context.getString(R.string.twin_switch_button))).fetchSemanticsNodes().size)
    }

    @Test fun keepAfterRecreationPreservesTheEditedBlockAndDoesNotReplay() {
        openAndRecreate()
        compose.onNodeWithText(context.getString(R.string.twin_keep_button)).performClick()
        val rows = runBlocking { repo.observeSessionSets(sessionId).first() }
        assertEquals(2, rows.count { it.exerciseId == pullUp && it.exercisePosition == 2 && it.reps == 7 && it.modifiers == "weighted" })
        assertEquals(2, rows.count { it.exerciseId == pullUp && it.exercisePosition == 0 && it.modifiers.isEmpty() })
        assertTrue(TrialPrefs(isolated(context)).twinKept.isNotEmpty())
        compose.activityRule.scenario.recreate()
        show()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(hasText(context.getString(R.string.twin_switch_button))).fetchSemanticsNodes().size)
        // A later, genuinely new modifier change must still be offered.
        val dipBlock = runBlocking { repo.observeSessionSets(sessionId).first().first { it.exercisePosition == 1 }.blockKey }
        runBlocking { repo.setExerciseModifiers(sessionId, dipBlock, "weighted") }
        awaitText(context.getString(R.string.twin_switch_prompt, "Weighted Dip"))
    }

    private companion object {
        const val DB_NAME = "twin_recreation_test.db"
    }
}
