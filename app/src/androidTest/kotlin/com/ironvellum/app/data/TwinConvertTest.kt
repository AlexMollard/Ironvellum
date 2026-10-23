package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.data.db.SetLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one-time convert of past sets onto dedicated exercises, through the database: a history shaped like a lifter
 * who logged Pull-up weighted, Push-up weighted-deficit-elevated and the like. Written in one transaction, idempotent,
 * and marking every touched trial amended so cloud sync re-uploads it.
 */
@RunWith(AndroidJUnit4::class)
class TwinConvertTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private val ids = HashMap<String, Long>()
    private val sealedAt = 1_000_000L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        listOf("Pull-up", "Chin-up", "Push-up", "Archer Push-up", "Pistol Squat", "Weighted Pull-up", "Weighted Chin-up", "Decline Push-up", "Incline Push-up")
            .forEach { ids[it] = repo.exerciseIdByName(it)!! }
        trial(1, listOf(Block("Pull-up", 5, 10.0, "weighted"), Block("Push-up", 4, 10.0, "weighted, deficit, elevated"), Block("Archer Push-up", 3, null, "")))
        trial(2, listOf(Block("Pull-up", 5, 12.5, "weighted"), Block("Chin-up", 3, 10.0, "weighted"), Block("Push-up", 3, 10.0, "weighted, deficit, elevated")))
        trial(3, listOf(Block("Pull-up", 4, 15.0, "weighted"), Block("Push-up", 3, 10.0, "weighted, deficit, elevated"), Block("Push-up", 2, 5.0, "weighted, incline"), Block("Pistol Squat", 2, 10.0, "weighted")))
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private class Block(val name: String, val sets: Int, val kg: Double?, val modifiers: String)

    private suspend fun trial(n: Int, blocks: List<Block>) {
        val id = db.sessionDao().insertSession(
            SessionEntity(
                presetId = null, label = "Convert $n", startedAtMs = sealedAt * n, completedAtMs = sealedAt * n + 3_600_000,
                xpAwarded = 100, strengthScore = 0,
            ),
        )
        blocks.forEachIndexed { position, block ->
            db.sessionDao().insertSets(
                (0 until block.sets).map {
                    SetLogEntity(
                        sessionId = id, exerciseId = ids.getValue(block.name), exercisePosition = position, setIndex = it,
                        reps = 5, weightKg = block.kg, modifiers = block.modifiers, done = true,
                    )
                },
            )
        }
    }

    private suspend fun countBy(name: String, modifiers: String? = null) =
        (1L..3L).flatMap { db.sessionDao().setsFor(it) }.count { it.exerciseId == ids.getValue(name) && (modifiers == null || it.modifiers == modifiers) }

    @Test
    fun theGroupsAreTheOnesTheOwnersHistoryHas() = runBlocking {
        val groups = TwinConversionGroups.of(repo)
        assertEquals(
            listOf("Pull-up>Weighted Pull-up:14", "Push-up>Decline Push-up:10", "Chin-up>Weighted Chin-up:3", "Push-up>Incline Push-up:2"),
            groups,
        )
    }

    @Test
    fun convertingMovesEveryGroupInOneGoAndLeavesTheRest() = runBlocking {
        var backedUp = 0
        val result = repo.convertTwins(backup = { json -> assertTrue(json.isNotBlank()); backedUp++ }, nowMs = 5_000_000L)
        assertEquals(1, backedUp)
        assertEquals(29, result.sets)
        assertEquals(3, result.sessions)
        assertEquals(14, countBy("Weighted Pull-up", ""))
        assertEquals(0, countBy("Pull-up"))
        assertEquals(10, countBy("Decline Push-up", "weighted, deficit"))
        assertEquals(2, countBy("Incline Push-up", "weighted"))
        assertEquals(3, countBy("Weighted Chin-up", ""))
        // Already dedicated, and no twin: untouched.
        assertEquals(3, countBy("Archer Push-up", ""))
        assertEquals(2, countBy("Pistol Squat", "weighted"))
        // Every touched trial is marked amended at that moment, paid what it was, and keeps its XP.
        (1L..3L).forEach {
            val session = db.sessionDao().byId(it)!!
            assertEquals(5_000_000L, session.editedAtMs)
            assertEquals(100, session.sealedXp)
            assertEquals(100, session.xpAwarded)
        }
    }

    @Test
    fun aSecondRunFindsNothingAndTouchesNothing() = runBlocking {
        repo.convertTwins(backup = {}, nowMs = 5_000_000L)
        var backedUp = 0
        val again = repo.convertTwins(backup = { backedUp++ }, nowMs = 9_000_000L)
        assertEquals(0, again.sets)
        assertEquals(0, backedUp)
        assertEquals(5_000_000L, db.sessionDao().byId(1)!!.editedAtMs)
        assertTrue(repo.observeTwinMoves().first().isEmpty())
    }

    @Test
    fun aFailureMidWayRollsEverythingBack() = runBlocking {
        try {
            repo.convertTwins(backup = {}, nowMs = 5_000_000L, afterGroup = { written -> if (written == 2) error("boom") })
            fail("the throw should have reached the caller")
        } catch (expected: IllegalStateException) {
            assertEquals("boom", expected.message)
        }
        assertEquals(14, countBy("Pull-up", "weighted"))
        assertEquals(0, countBy("Weighted Pull-up"))
        assertEquals(10, countBy("Push-up", "weighted, deficit, elevated"))
        (1L..3L).forEach { assertNull(db.sessionDao().byId(it)!!.editedAtMs) }
    }

    @Test
    fun noBackupMeansNoConvert() = runBlocking {
        try {
            repo.convertTwins(backup = { error("disk full") })
            fail("the backup failure should stop the convert")
        } catch (expected: IllegalStateException) {
            assertEquals("disk full", expected.message)
        }
        assertEquals(14, countBy("Pull-up", "weighted"))
        assertNotNull(db.sessionDao().byId(1))
        assertNull(db.sessionDao().byId(1)!!.editedAtMs)
    }

    private companion object {
        const val TEST_DB = "twin_convert_test.db"
    }
}

/** The groups as "from>to:sets" lines, in the order the confirm screen lists them. */
private object TwinConversionGroups {
    suspend fun of(repo: Repository): List<String> =
        com.ironvellum.app.domain.TwinConversion.groups(repo.observeTwinMoves().first()).map { "${it.from}>${it.to}:${it.sets}" }
}
