package com.ironvellum.app.domain

import java.time.Instant
import java.time.ZoneId

/**
 * Re-prices past trials with the current XP rules, once, for a developer build.
 *
 * What a sealed trial's `xpAwarded` holds is the whole breakdown: effort lines, peaks,
 * records, the trial-level bonuses, the completion bonus and, when it was earned, the
 * quest bonus. The old pricer is gone, so a trial is priced afresh by the same
 * [SessionXp] path a seal uses, judged against the history that existed before it
 * (chronologically, so each trial's own benchmark is only what came earlier), and the
 * difference from the stored figure is the change.
 *
 * Two inputs are not stored and are read as they are today, which is the one assumption
 * here: the quest bonus (rebuilt by [questEarned] from the rite's scheduled day) and the
 * rite's planned sets (the rite-as-planned bonus).
 *
 * Only trials are touched. [plan] turns the changes into the profile's new total: the
 * sum of the deltas, never below the floor of the level the lifter is on, so the level
 * never drops and no reward is taken back. Running it again finds every delta zero.
 */
object XpRescore {

    /** One trial's stored XP and what it is worth now. */
    data class Change(
        val sessionId: Long,
        val label: String,
        val startedAtMs: Long,
        val oldXp: Int,
        val newXp: Int,
        /** The trial was amended after sealing: its sets are the edited ones, so a raise its edit window refused is granted. */
        val amended: Boolean,
        /** The device-only sealed figure, when one was recorded; null otherwise. */
        val sealedXp: Int?,
    ) {
        val delta: Int get() = newXp - oldXp

        /** A recorded sealed figure moves by the same delta, so the amend cap's room is unchanged; null stays null. */
        val newSealedXp: Int? get() = sealedXp?.let { (it + delta).coerceAtLeast(0) }
    }

    /** What applying the changes does to the profile. */
    data class Plan(
        val changes: List<Change>,
        val totalBefore: Long,
        val totalAfter: Long,
        val levelBefore: Int,
        val levelAfter: Int,
        /** True when the sum of the deltas would have lowered the level and the total was held at its floor. */
        val clamped: Boolean,
        /** Inscriptions the rise in level pays, by the same once-per-level rule as a seal ([Xp.rollsDue]). */
        val inscriptionsDue: Int,
    ) {
        /** The trials whose XP actually changes. */
        val changed: List<Change> get() = changes.filter { it.delta != 0 }

        /** The sum of the deltas, before any clamp. */
        val delta: Long get() = changes.sumOf { it.delta.toLong() }

        /** What is added to the total: the sum of the deltas, less whatever the clamp held back. */
        val applied: Long get() = totalAfter - totalBefore
    }

    /** The lowest total at [totalXp]'s own level: the clamp's floor. */
    fun levelFloor(totalXp: Long): Long = totalXp.coerceAtLeast(0) - Xp.progress(totalXp).intoLevel

    fun plan(changes: List<Change>, totalXp: Long, rollLevelMark: Int): Plan {
        val before = totalXp.coerceAtLeast(0)
        val raw = before + changes.sumOf { it.delta.toLong() }
        val floor = levelFloor(before)
        val after = maxOf(raw, floor)
        val levelBefore = Xp.levelFor(before)
        val levelAfter = Xp.levelFor(after)
        return Plan(
            changes = changes,
            totalBefore = before,
            totalAfter = after,
            levelBefore = levelBefore,
            levelAfter = levelAfter,
            clamped = raw < floor,
            inscriptionsDue = Xp.rollsDue(levelBefore, levelAfter, rollLevelMark),
        )
    }

    /**
     * Every non-imported completed trial of [history], oldest first, priced with the rules in force now.
     * An imported trial keeps what its import paid.
     *
     * @param bodyweightAt the weigh-in in force at a moment, as the seal uses; a non-positive figure means none.
     * @param plannedSets a rite's planned sets per movement, null when it has gone.
     * @param scheduledDay a rite's scheduled day of the week (1 = Monday), null when it has none or has gone.
     */
    fun reprice(
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        exercises: Map<Long, Exercise>,
        bodyweightAt: (Long) -> Double,
        plannedSets: (Long) -> Map<Long, Int>?,
        scheduledDay: (Long) -> Int?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Change> {
        val timeline = SessionXp.Timeline(exercises, history, bodyweightAt)
        return history
            .filter { (session, _) -> session.completedAtMs != null && !session.imported }
            .sortedWith(compareBy({ it.first.startedAtMs }, { it.first.id }))
            .map { (session, sets) ->
                val book = timeline.before(session.startedAtMs)
                val done = sets.filter { it.done && !it.warmup }
                val quest = questEarned(session, sets, history, scheduledDay, zone)
                val facts = book.factsFor(session, done, session.presetId?.let(plannedSets), quest, zone)
                val bodyweight = bodyweightAt(session.startedAtMs).takeIf { it > 0.0 }
                Change(
                    sessionId = session.id,
                    label = session.title.ifBlank { session.label },
                    startedAtMs = session.startedAtMs,
                    oldXp = session.xpAwarded,
                    newXp = book.breakdown(done, bodyweight, facts, doneSetCount = done.size).total,
                    amended = session.editedAtMs != null || session.sealedXp != null,
                    sealedXp = session.sealedXp,
                )
            }
    }

    /**
     * The seal's own rule, read back: the quest bonus is paid once, for a trial with a done set of the
     * rite scheduled for the day it was sealed, when no earlier trial of that rite was sealed that day.
     */
    internal fun questEarned(
        session: WorkoutSession,
        sets: List<SessionSet>,
        history: List<Pair<WorkoutSession, List<SessionSet>>>,
        scheduledDay: (Long) -> Int?,
        zone: ZoneId,
    ): Boolean {
        val presetId = session.presetId ?: return false
        val sealedAt = session.completedAtMs ?: return false
        if (sets.none { it.done }) return false
        val day = Instant.ofEpochMilli(sealedAt).atZone(zone).toLocalDate()
        if (scheduledDay(presetId) != day.dayOfWeek.value) return false
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        return history.none { (other, _) ->
            val at = other.completedAtMs
            other.id != session.id && other.presetId == presetId && at != null && at >= dayStart &&
                (at < sealedAt || (at == sealedAt && other.id < session.id))
        }
    }
}
