package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.ExerciseMetric
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A rite's supersets: saved where the entries are neighbours, carried onto a trial's sets, and through the archive. */
@RunWith(AndroidJUnit4::class)
class RiteSupersetTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private var ids = emptyList<Long>()

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        ids = db.exerciseDao().observeAll().first().filter { it.metric == ExerciseMetric.REPS.name }.take(4).map { it.id }
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private fun entry(index: Int, group: Int?) =
        Repository.PresetDraftEntry(ids[index], targetSets = 2, targetReps = 8, targetWeightKg = 40.0, supersetGroup = group)

    private suspend fun save(vararg entries: Repository.PresetDraftEntry): Long =
        repo.savePreset(null, "Push day", "", null, entries.toList())

    private suspend fun groupsOf(presetId: Long) =
        repo.observePresets().first().first { it.id == presetId }.entries.sortedBy { it.position }.map { it.supersetGroup }

    @Test
    fun aGroupIsSavedOnlyWhereItsMembersAreNeighbours() = runBlocking {
        val kept = save(entry(0, 1), entry(1, 1), entry(2, null))
        assertEquals(listOf(1, 1, null), groupsOf(kept))
        // Two entries sharing a number with another between them are not a superset.
        val split = save(entry(0, 5), entry(1, null), entry(2, 5))
        assertEquals(listOf(null, null, null), groupsOf(split))
    }

    @Test
    fun aTrialBegunFromTheRiteStartsLinked() = runBlocking {
        val rite = save(entry(0, null), entry(1, 3), entry(2, 3))
        val session = repo.startSessionFromPreset(rite)
        val sets = repo.observeSessionSets(session).first()
        assertEquals(listOf(null, 3, 3), sets.groupBy { it.exerciseId }.entries.sortedBy { it.value.first().exercisePosition }.map { it.value.first().supersetGroup })
        assertTrue("every set of a member carries the group", sets.filter { it.exerciseId in ids.subList(1, 3) }.all { it.supersetGroup == 3 })
    }

    @Test
    fun theArchiveKeepsARitesSupersets() = runBlocking {
        val rite = save(entry(0, 2), entry(1, 2), entry(2, null))
        val json = repo.exportJson()
        assertTrue(repo.importArchive(json).isSuccess)
        val restored = repo.observePresets().first().single()
        assertEquals(listOf(2, 2, null), restored.entries.sortedBy { it.position }.map { it.supersetGroup })
        assertTrue(rite > 0)
    }

    private companion object {
        const val TEST_DB = "rite_superset_test.db"
    }
}
