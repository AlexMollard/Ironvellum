package com.ironvellum.app.data

import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.data.localconnector.*
import java.math.BigDecimal
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class PortableWorkoutRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "portable-workout-test.db"
    private lateinit var db: IronvellumDatabase
    private lateinit var workouts: PortableWorkoutRepository
    private val sql get() = db.openHelper.writableDatabase

    @Before fun setup() {
        SeededDb.fresh(context, name)
        db = IronvellumDatabase.create(context, name)
        workouts = PortableWorkoutRepository(db) { 1234L }
        sql.execSQL("DELETE FROM presets")
    }

    @After fun close() {
        db.close()
        context.deleteDatabase(name)
    }

    private fun file(key: String = "bench press") = """
        {"type":"ironvellum.workout","schemaVersion":1,"name":"Original","warmup":"AUTO","cooldown":"NONE",
        "entries":[{"exerciseKey":"$key","sets":3,"reps":8,"loadKg":12.345}]}
    """.trimIndent()

    private suspend fun inspect(key: String = "bench press") =
        (workouts.inspect(file(key).encodeToByteArray()) as PortableInspection.Ready).review

    private fun scalar(query: String) = sql.query(query).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getString(0)
    }

    private fun count(table: String) = scalar("SELECT count(*) FROM $table").toInt()

    @Test fun editedWorkoutSavesOnceAcrossCanonicalFileAndPasteWithoutAnAccount() = runBlocking {
        val original = inspect()
        val paste = ("```json\n" + file().replace("12.345", "1.234500e1") + "\n```").encodeToByteArray()
        assertTrue(workouts.inspect(paste) is PortableInspection.Ready)
        val edited = original.draft.copy(name = "Edited", entries = original.draft.entries.map { it.copy(reps = 12) })
        val reviewed = (workouts.reviewEdits(original, edited) as PortableInspection.Ready).review
        assertEquals(BigDecimal("12.345"), reviewed.draft.entries.single().loadKg)
        assertEquals(0, count("presets"))
        assertEquals(0, count("portable_workout_receipts"))
        val saved = workouts.save(reviewed) as LocalSave.Saved
        assertEquals("Edited/unscheduled", scalar("SELECT name||'/'||coalesce(scheduledDay,'unscheduled') FROM presets"))
        assertEquals("3/12/12.345", scalar("SELECT targetSets||'/'||targetReps||'/'||targetWeightKg FROM preset_entries"))
        assertEquals("AUTO", scalar("SELECT mode FROM preset_preparations WHERE kind='WARMUP'"))
        assertEquals("NONE", scalar("SELECT mode FROM preset_preparations WHERE kind='COOLDOWN'"))
        assertEquals("${saved.localPresetId}/1234", scalar("SELECT localPresetId||'/'||savedAtMs FROM portable_workout_receipts"))
        assertEquals(PortableInspection.AlreadySaved(saved.localPresetId), workouts.inspect(paste))
        assertEquals(LocalSave.AlreadySaved(saved.localPresetId), workouts.save(original))
        assertEquals(1, count("presets"))
        assertEquals(1, count("portable_workout_receipts"))
        assertEquals(0, count("local_connector_exports"))
        assertEquals(0, count("mcp_draft_receipts"))
    }

    @Test fun deletionKeepsTheReceiptAndNeverRecreatesThePreset() = runBlocking {
        val review = inspect()
        val saved = workouts.save(review) as LocalSave.Saved
        sql.execSQL("DELETE FROM presets")
        assertEquals(PortableInspection.AlreadySaved(saved.localPresetId), workouts.inspect(file().encodeToByteArray()))
        assertEquals(LocalSave.AlreadySaved(saved.localPresetId), workouts.save(review))
        assertEquals(0, count("presets"))
        assertEquals(1, count("portable_workout_receipts"))
    }

    @Test fun concurrentReviewedSavesWriteExactlyOnePresetAndReceipt() = runBlocking {
        val first = inspect()
        val second = inspect()
        val results = listOf(first, second).map { review -> async(Dispatchers.IO) { workouts.save(review) } }.awaitAll()
        val saved = results.filterIsInstance<LocalSave.Saved>().single()
        assertEquals(LocalSave.AlreadySaved(saved.localPresetId), results.filterIsInstance<LocalSave.AlreadySaved>().single())
        assertEquals(1, count("presets"))
        assertEquals(1, count("portable_workout_receipts"))
    }

    @Test fun failingReceiptInsertRollsBackEveryWorkoutRow() = runBlocking {
        val review = inspect()
        sql.execSQL("""CREATE TRIGGER refuse_portable BEFORE INSERT ON portable_workout_receipts
            BEGIN SELECT RAISE(ABORT,'fixture'); END""")
        assertEquals(LocalSave.Rejected(LocalFailure.STORAGE), workouts.save(review))
        for (table in listOf("presets", "preset_entries", "preset_preparations", "portable_workout_receipts")) {
            assertEquals(table, 0, count(table))
        }
    }

    @Test fun matchingRequiresAnExplicitEligibleIdAndSaveRejectsAReplacementWithTheSameKey() = runBlocking {
        val review = inspect("sunshine push")
        assertEquals(setOf("sunshine push"), review.unresolvedKeys)
        assertEquals(LocalSave.Rejected(LocalFailure.MOVEMENT), workouts.save(review))
        val edit = review.draft.copy(entries = review.draft.entries.map { it.copy(exerciseKey = "bench press") })
        val bench = db.exerciseDao().byName("Bench Press")!!.id
        val pushUp = db.exerciseDao().byName("Push-up")!!.id
        assertEquals(PortableInspection.Rejected(LocalFailure.MOVEMENT), workouts.reviewEdits(review, edit))
        assertEquals(PortableInspection.Rejected(LocalFailure.MOVEMENT), workouts.reviewEdits(review, edit, mapOf(0 to pushUp)))
        val matched = (workouts.reviewEdits(review, edit, mapOf(0 to bench)) as PortableInspection.Ready).review
        assertTrue(matched.unresolvedKeys.isEmpty())
        val badTargets = edit.copy(entries = edit.entries.map { it.copy(sets = 0) })
        assertEquals(PortableInspection.Rejected(LocalFailure.INVALID_FILE), workouts.reviewEdits(matched, badTargets))
        sql.execSQL("UPDATE exercises SET name='Previous Bench Press' WHERE id=?", arrayOf(bench))
        sql.execSQL("""INSERT INTO exercises(name,muscleGroup,isWeighted,metric,category,custom,gear,muscles)
            VALUES('Bench Press','',1,'REPS','',0,'','')""")
        assertEquals(LocalSave.Rejected(LocalFailure.MOVEMENT), workouts.save(matched))
        assertEquals(0, count("presets"))
        assertEquals(0, count("portable_workout_receipts"))
    }

    @Test fun archiveReplacementClearsReceiptsAndFencesPendingReviews() = runBlocking {
        val savedReview = inspect()
        assertTrue(workouts.save(savedReview) is LocalSave.Saved)
        val pending = inspect("push-up")
        val repository = Repository(db)
        repository.importArchive(repository.exportArchive().json).getOrThrow()
        assertEquals(LocalSave.Rejected(LocalFailure.WRONG_DATASET), workouts.save(pending))
        assertEquals(0, count("portable_workout_receipts"))
        assertEquals(1, count("presets"))
    }
}
