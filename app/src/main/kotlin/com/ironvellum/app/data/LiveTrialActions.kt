package com.ironvellum.app.data

import android.content.Context
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.RestTimer
import com.ironvellum.app.domain.SessionClock
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.blockKey
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.domain.Supersets
import com.ironvellum.app.domain.isStrength
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the lifter does to a live trial's sets, in one place: the trial screen's
 * log button and steppers and the lock-screen "Set done" all write through
 * [commit], so a tick starts the same rest, honours the same rest settings and
 * lands in the same repository writer whichever door it came through. The rest
 * itself is [RestClock]: skipping and extending it are calls on that, which the
 * screen's buttons and the notification's actions share too.
 */
class LiveTrialActions(
    private val repo: Repository,
    private val context: Context,
    private val sessionId: Long,
) {
    private val prefs = TrialPrefs(context)

    /**
     * Writes [set] (its figures and its ticked state) with the writer its [metric]
     * owns, each handed the set's current values for the columns it does not own so
     * no edit erases a neighbour's figure. A tick that earns a rest starts it first,
     * judged against the sets as they stood before this write.
     */
    suspend fun commit(set: SessionSet, metric: ExerciseMetric) = writeLock.withLock { write(set, metric) }

    private suspend fun write(set: SessionSet, metric: ExerciseMetric) {
        // Only a tick can start a rest, so a stepper press goes straight to the writer.
        if (set.done) startRestAfter(set.id, set.done)
        when {
            metric == ExerciseMetric.HOLD -> repo.updateHoldSet(set.id, set.durationSec ?: 0, set.weightKg, set.done)
            metric.isStrength -> repo.updateSet(set.id, set.reps, set.weightKg, set.done)
            else -> repo.updateActivitySet(set.id, set.reps, set.durationSec, set.distanceM, set.grade, set.weightKg, set.done)
        }
    }

    /**
     * The lock-screen "Set done": ticks the next set still to log ([Supersets.nextSet], which is
     * the one the notification names) with the figures it shows. False, and nothing written, when no set is waiting or [expectedSetId]
     * (the set the notification named) is no longer the next one: a tap on a stale
     * notification must not tick the set after it.
     */
    suspend fun tickNext(expectedSetId: Long? = null): Boolean = writeLock.withLock {
        val sets = repo.observeSessionSets(sessionId).first()
        val next = Supersets.nextSet(sets) ?: return@withLock false
        if (expectedSetId != null && next.id != expectedSetId) return@withLock false
        val metric = metricOf(next.exerciseId)
        write(next.copy(done = true), metric)
        true
    }

    private suspend fun startRestAfter(setId: Long, nowDone: Boolean) {
        val sets = repo.observeSessionSets(sessionId).first()
        val set = sets.firstOrNull { it.id == setId } ?: return
        val exercise = repo.observeExercises().first().firstOrNull { it.id == set.exerciseId }
        // Mid-round in a superset no rest starts, and the one still running belongs to the round before.
        if (!set.done && nowDone && !Supersets.completesRound(sets, setId)) RestClock.cancel(sessionId)
        val window = RestTimer.restAfterTick(
            before = sets,
            setId = setId,
            nowDone = nowDone,
            focus = focus(),
            metric = exercise?.metric,
            weighted = exercise?.isWeighted,
            restOff = prefs.restIsOff(set.exerciseName),
        ) ?: return
        // After a top set the rest is the long one the lift's scheme suggests (3:00 by default), and its ceiling at least that.
        if (set.kind == SetKind.TOP) {
            val ready = repo.restAfterTopSeconds(sessionId, set.blockKey)
            RestClock.start(sessionId, ready, maxOf(window.max, ready))
            return
        }
        RestClock.start(sessionId, window.ready, window.max)
    }

    /** What the lifter last told the generator, else the profile mode: the focus rests are priced at. */
    private suspend fun focus() = SessionClock.focusFor(
        ProgramAnswersStore.get(context)?.focus,
        repo.observeProfile().first()?.trainingMode,
    )

    private suspend fun metricOf(exerciseId: Long): ExerciseMetric =
        repo.observeExercises().first().firstOrNull { it.id == exerciseId }?.metric ?: ExerciseMetric.REPS

    private companion object {
        /**
         * One lock for the whole process, so the trial screen's taps and the notification's
         * "Set done" write in the order they came: a write waits behind the reads of the one
         * before it, and a tick's rest is judged against the sets as that one left them.
         */
        val writeLock = Mutex()
    }
}
