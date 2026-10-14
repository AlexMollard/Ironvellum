package com.ironvellum.app.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.ui.train.PresetEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Marking an entry as a warm-up in the rite editor: it moves to the head of the rite, leaves any superset, cannot be
 * dragged past a worked exercise or linked to one, and the flag reaches the stored rite.
 */
@RunWith(AndroidJUnit4::class)
class PresetEditorWarmupTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private lateinit var vm: PresetEditorViewModel

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        vm = withContext(Dispatchers.Main) { PresetEditorViewModel(repo, presetId = null) }
        val loaded = withTimeout(10_000) { vm.ui.first { it.exercises.isNotEmpty() } }
        assertTrue("the editor must load its catalogue", loaded.exercises.isNotEmpty())
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private suspend fun addThree(): List<String> {
        val reps = vm.ui.value.exercises.filter { it.metric.name == "REPS" }.take(3)
        withContext(Dispatchers.Main) { reps.forEach { vm.addEntry(it) } }
        return reps.map { it.name }
    }

    @Test
    fun aWarmupMovesToTheHeadAndTheOpenRowFollowsIt() = runBlocking {
        val names = addThree()
        val now = withContext(Dispatchers.Main) { vm.setWarmup(2, true) }
        assertEquals("the entry's new place", 0, now)
        val entries = vm.ui.first().entries
        assertEquals(listOf(names[2], names[0], names[1]), entries.map { it.exerciseName })
        assertEquals(listOf(true, false, false), entries.map { it.warmup })

        // Worked again, it stays where it is: only warm-ups are pulled forward.
        withContext(Dispatchers.Main) { vm.setWarmup(0, false) }
        assertEquals(listOf(false, false, false), vm.ui.first().entries.map { it.warmup })
    }

    @Test
    fun aWarmupLeavesItsSupersetAndCannotJoinOneOrCrossAWorkedExercise() = runBlocking {
        addThree()
        withContext(Dispatchers.Main) { vm.linkEntries(0, 1) }
        assertTrue(vm.ui.first().entries.take(2).all { it.supersetGroup != null })

        withContext(Dispatchers.Main) { vm.setWarmup(0, true) }
        val entries = vm.ui.first().entries
        assertTrue("a warm-up is in no superset", entries[0].warmup && entries[0].supersetGroup == null)
        assertNull("and the partner it left is no longer in a group of one", entries[1].supersetGroup)

        val link = withContext(Dispatchers.Main) { vm.linkEntries(0, 2) }
        assertNull("a warm-up cannot be linked", link)
        val before = vm.ui.first().entries
        assertNull("nor dragged below a worked exercise", withContext(Dispatchers.Main) { vm.moveEntry(0, 1) })
        assertNull("nor a worked exercise above it", withContext(Dispatchers.Main) { vm.moveEntry(1, -1) })
        assertEquals(before, vm.ui.first().entries)
    }

    @Test
    fun theFlagIsSavedWithTheRite() = runBlocking {
        val names = addThree()
        withContext(Dispatchers.Main) {
            vm.setName("Legs")
            vm.setWarmup(2, true)
        }
        withContext(Dispatchers.Main) { vm.save {} }
        val stored = withTimeout(10_000) { repo.observePresets().first { it.isNotEmpty() } }.single()
        assertEquals(listOf(names[2], names[0], names[1]), stored.entries.map { it.exerciseName })
        assertEquals(listOf(true, false, false), stored.entries.map { it.warmup })
    }

    private companion object {
        const val TEST_DB = "ironvellum-preset-warmup-test.db"
    }
}
