package com.ironvellum.app.ui

import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.IronvellumApp
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.data.db.StatEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * A believable few weeks of training written through the app's own database, so screens that need history
 * (Stats, lift pages, Progress, the workout log) have rows to lay out. Replaces whatever sessions exist.
 */
object DemoHistory {

    private const val DAY_MS = 86_400_000L
    private const val WEEKS = 10

    private class Lift(val name: String, val sets: Int, val reps: Int, val kg: Double)
    private class Template(val label: String, val lifts: List<Lift>)

    private val templates = listOf(
        Template("Push", listOf(Lift("Bench Press", 4, 6, 70.0), Lift("Overhead Press", 3, 8, 40.0), Lift("Dip", 3, 10, 0.0))),
        Template("Pull", listOf(Lift("Deadlift", 3, 5, 110.0), Lift("Barbell Row", 4, 8, 60.0), Lift("Pull-up", 3, 8, 0.0))),
        Template("Legs", listOf(Lift("Back Squat", 4, 6, 90.0), Lift("Romanian Deadlift", 3, 8, 80.0), Lift("Hip Thrust", 3, 10, 90.0))),
    )

    fun seed() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IronvellumApp
        val database = app.database
        val ids = database.exerciseDao().observeAll().first().associate { it.name to it.id }
        val start = System.currentTimeMillis() - WEEKS * 7 * DAY_MS
        database.openHelper.writableDatabase.execSQL("DELETE FROM sessions")
        database.withTransaction {
            for (week in 0 until WEEKS) {
                database.statDao().insert(
                    StatEntity(takenAtMs = start + week * 7 * DAY_MS, weightKg = 80.0 + (week % 4) * 0.4, heightCm = TestProfile.HEIGHT_CM, bodyFatPct = null),
                )
                for (slot in 0 until 3) {
                    val index = week * 3 + slot
                    val startedAt = start + (week * 7 + slot * 2 + 1) * DAY_MS + 18 * 3_600_000L
                    val template = templates[index % templates.size]
                    val sessionId = database.sessionDao().insertSession(
                        SessionEntity(
                            presetId = null, label = template.label, startedAtMs = startedAt,
                            completedAtMs = startedAt + 4_200_000L, xpAwarded = 150, strengthScore = 400,
                            title = "", note = "", privateNote = "",
                        ),
                    )
                    val rows = ArrayList<SetLogEntity>()
                    template.lifts.forEachIndexed { position, lift ->
                        repeat(lift.sets) { setIndex ->
                            rows += SetLogEntity(
                                sessionId = sessionId, exerciseId = ids.getValue(lift.name), exercisePosition = position,
                                setIndex = setIndex, reps = lift.reps,
                                weightKg = if (lift.kg > 0.0) ((lift.kg + index * 0.5) / 2.5).toInt() * 2.5 else null,
                                modifiers = "", done = true, rpe = if (setIndex == lift.sets - 1) 8 else null,
                            )
                        }
                    }
                    database.sessionDao().insertSets(rows)
                }
            }
        }
    }
}
