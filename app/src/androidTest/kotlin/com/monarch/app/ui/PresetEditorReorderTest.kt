package com.monarch.app.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monarch.app.data.MonarchDatabase
import com.monarch.app.data.Repository
import com.monarch.app.ui.train.PresetEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reordering exercises is an explicit product rule, and the editor drives it by
 * row index. An index arrives from a rendered row, so a tap queued before a
 * removal recomposes carries an index the list no longer has: that used to
 * crash on `list[index]`. Rearranging must survive a stale tap.
 */
@RunWith(AndroidJUnit4::class)
class PresetEditorReorderTest {

    private lateinit var db: MonarchDatabase
    private lateinit var vm: PresetEditorViewModel

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = MonarchDatabase.create(context, TEST_DB)
        val repo = Repository(db)
        repo.ensureSeeded()
        vm = withContext(Dispatchers.Main) { PresetEditorViewModel(repo, presetId = null) }
        // The editor needs its catalogue loaded before addEntry() has anything
        // to add; it loads asynchronously.
        // runBlocking, not runTest: under runTest `delay` is virtual and this
        // loop spins instantly, so the wait is only a wait in real time.
        var waited = 0
        while (vm.ui.value.exercises.isEmpty() && waited < 100) {
            kotlinx.coroutines.delay(50); waited++
        }
        assertEquals("the editor must load its catalogue", true, vm.ui.value.exercises.isNotEmpty())
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    @Test
    fun reorderingSwapsNeighboursAndIgnoresStaleRows() = runBlocking {
        withContext(Dispatchers.Main) {
            vm.addEntry()
            vm.addEntry()
            // Distinguish the two rows, since addEntry seeds both from the same
            // catalogue head: without this a swap is unobservable.
            vm.updateEntry(0, vm.ui.value.entries[0].copy(reps = "FIRST"))
            vm.updateEntry(1, vm.ui.value.entries[1].copy(reps = "SECOND"))
        }
        assertEquals(listOf("FIRST", "SECOND"), vm.ui.first().entries.map { it.reps })

        withContext(Dispatchers.Main) { vm.moveEntry(0, 1) }
        assertEquals(
            "moving a row down must swap it with its neighbour",
            listOf("SECOND", "FIRST"),
            vm.ui.first().entries.map { it.reps },
        )

        // A tap carrying an index past the end of the list — the stale-row case.
        withContext(Dispatchers.Main) { vm.moveEntry(5, -5) }
        assertEquals(
            "a stale row index must do nothing, not crash",
            listOf("SECOND", "FIRST"),
            vm.ui.first().entries.map { it.reps },
        )

        // And the bounds the buttons rely on: first cannot move up, last down.
        withContext(Dispatchers.Main) {
            vm.moveEntry(0, -1)
            vm.moveEntry(1, 1)
        }
        assertEquals(
            "moves past either end leave the order alone",
            listOf("SECOND", "FIRST"),
            vm.ui.first().entries.map { it.reps },
        )
    }

    private companion object {
        const val TEST_DB = "monarch-preset-reorder-test.db"
    }
}
