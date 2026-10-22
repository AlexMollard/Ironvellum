package com.ironvellum.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.ui.Routes
import com.ironvellum.app.ui.train.ExerciseExplorerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A deficit push-up and a flat one are two lifts: what opens, what "last" says, and what the picker names. */
@RunWith(AndroidJUnit4::class)
class VariationFollowThroughTest {

    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private var pushUp = 0L

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        repo = Repository(db)
        repo.ensureSeeded()
        pushUp = db.exerciseDao().observeAll().first().first { it.name == "Push-up" }.id
        // Oldest first: a deficit trial, then two flat ones.
        trial(1_000_000L, "deficit", reps = 12)
        trial(2_000_000L, "", reps = 20)
        trial(3_000_000L, "", reps = 25)
    }

    @After
    fun tearDown() {
        db.close()
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    private suspend fun trial(at: Long, modifiers: String, reps: Int) {
        val id = db.sessionDao().insertSession(
            SessionEntity(
                presetId = null, label = "T$at", startedAtMs = at, completedAtMs = at + 600_000L,
                xpAwarded = 0, strengthScore = 0, title = "", note = "", privateNote = "",
            ),
        )
        db.sessionDao().insertSets(
            (0..2).map {
                SetLogEntity(
                    sessionId = id, exerciseId = pushUp, exercisePosition = 0, setIndex = it, reps = reps,
                    weightKg = null, modifiers = modifiers, done = true,
                )
            },
        )
    }

    @Test
    fun theRouteCarriesTheVariationAndTheExplorerOpensOnIt() = runBlocking {
        assertEquals("exercises?name=Push-up", Routes.exercises("Push-up"))
        assertEquals("exercises?name=Push-up&variation=deficit%2Celevated", Routes.exercises("Push-up", "deficit,elevated"))
        val exercise = repo.observeExercises().first().first { it.id == pushUp }
        val shown = withContext(Dispatchers.Main) {
            val vm = ExerciseExplorerViewModel(repo)
            vm.pick(exercise, "deficit")
            withTimeout(10_000) { vm.ui.first { it.history?.variation == "deficit" } }.history!!.variation
        }
        assertEquals("deficit", shown)
        val plain = withContext(Dispatchers.Main) {
            val vm = ExerciseExplorerViewModel(repo)
            vm.pick(exercise, "")
            withTimeout(10_000) { vm.ui.first { it.history != null } }.history!!.variation
        }
        assertEquals("", plain)
        // A combination opens on its own chip, whichever order its tokens were picked in.
        trial(5_000_000L, "deficit, archer", reps = 8)
        val combo = withContext(Dispatchers.Main) {
            val vm = ExerciseExplorerViewModel(repo)
            vm.pick(exercise, com.ironvellum.app.domain.Variation.key("deficit, archer"))
            withTimeout(10_000) { vm.ui.first { it.history?.variation == "archer,deficit" } }.history!!.variation
        }
        assertEquals("archer,deficit", combo)
        assertEquals("exercises?name=Push-up&variation=archer%2Cdeficit", Routes.exercises("Push-up", "archer,deficit"))
    }

    @Test
    fun lastLoggedIsPerVariationAndANeverLoggedOneIsEmpty() = runBlocking {
        val last = repo.observeLastLogged().first()
        assertEquals(25, last[pushUp, ""]!!.reps)
        assertEquals("the flat trials do not stand in for the deficit one", 12, last[pushUp, "deficit"]!!.reps)
        assertNull(last[pushUp, "archer"])
        assertEquals("the picker names the most recent variation", 25, last.latest(pushUp)!!.reps)
        // The deficit trial is now the newest: the picker follows it, the flat line stays where it was.
        trial(4_000_000L, "deficit", reps = 14)
        val after = repo.observeLastLogged().first()
        assertEquals(14, after.latest(pushUp)!!.reps)
        assertEquals("deficit", after.latest(pushUp)!!.variation)
        assertEquals(25, after[pushUp, ""]!!.reps)
    }

    private companion object {
        const val TEST_DB = "ironvellum-variation-follow-through-test.db"
    }
}
