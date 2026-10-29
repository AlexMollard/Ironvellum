package com.ironvellum.app.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * The friend-safe card of one completed workout, stored in `sessions.feed_card` and shown to whoever may read the
 * workout. It holds only what an ally may see: hard sets, tonnes of loaded work, the PR lifts with the load and reps
 * as logged, and muscle levels 0-3. Never an e1RM and never a figure derived from body weight, so it is built with
 * no body weight at all.
 */
data class FeedCard(
    val hardSets: Int,
    val volumeKg: Double,
    val prs: List<WebSnapshot.PrTag>,
    val levels: Map<Muscle, Int>,
)

object FeedCardJson {
    /** The server's check on the column: an object of at most this many characters. */
    const val MAX_CHARS = 2048
    const val VERSION = 1
    const val MAX_PRS = 6

    /** Keys `v, hardSets, volumeKg, prs, levels` in that order. PRs are dropped from the end if the text would pass the cap. */
    fun write(card: FeedCard): String {
        var prs = card.prs.take(MAX_PRS)
        while (true) {
            val text = element(card, prs).toString()
            if (text.length <= MAX_CHARS || prs.isEmpty()) return text
            prs = prs.dropLast(1)
        }
    }

    fun element(card: FeedCard): JsonObject = element(card, card.prs.take(MAX_PRS))

    private fun element(card: FeedCard, prs: List<WebSnapshot.PrTag>): JsonObject = buildJsonObject {
        put("v", JsonPrimitive(VERSION))
        put("hardSets", JsonPrimitive(card.hardSets))
        put("volumeKg", JsonPrimitive(card.volumeKg))
        put("prs", JsonArray(prs.map { pr ->
            buildJsonObject {
                put("label", JsonPrimitive(pr.label))
                put("reps", JsonPrimitive(pr.reps))
                put("weightKg", pr.weightKg?.let { JsonPrimitive(it) } ?: JsonNull)
                put("durationSec", pr.durationSec?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        }))
        put("levels", buildJsonObject {
            card.levels.entries.filter { it.value > 0 }.sortedBy { it.key.ordinal }.forEach { put(it.key.name, JsonPrimitive(it.value)) }
        })
    }

    /** Muscle level 0-3 from the planned or logged hard-set credit: the thresholds the snapshot uses. */
    fun level(credit: Double): Int = when {
        credit >= WebSnapshotTuning.LEVEL_MOST -> 3
        credit >= WebSnapshotTuning.LEVEL_SOME -> 2
        credit > 0 -> 1
        else -> 0
    }

    /**
     * The card of one workout, or null for an imported one or one with no done set. [book] is what the lifter had
     * done before it ([RepMax.Ledger.first]). Body weight is deliberately not an input.
     */
    fun build(
        session: WorkoutSession,
        sets: List<SessionSet>,
        book: RepBook,
        exercises: Map<Long, Exercise>,
    ): FeedCard? {
        if (session.imported || sets.none { it.done }) return null
        val at = session.completedAtMs ?: session.startedAtMs
        val logged = HardSets.logged(at, sets, exercises)
        val hard = logged.count { HardSets.isHard(it.kind, it.rpe, it.done, it.reps, it.weightKg, it.bodyweight) }
        val levels = HardSets.inRange(logged, at, at + 1).mapValues { (_, n) -> level(n) }.filterValues { it > 0 }
        val prs = WebSnapshotBuilder.prTags(sets, book, exercises)
        return FeedCard(hard, WorkoutShare.totals(sets, exercises).movedKg.toDouble(), prs, levels)
    }

    /** One ledger pass over [history]: the card of every completed workout that has one, by local session id. */
    fun cards(history: List<Pair<WorkoutSession, List<SessionSet>>>, exercises: Map<Long, Exercise>): Map<Long, FeedCard> {
        val ordered = history.sortedBy { it.first.completedAtMs ?: it.first.startedAtMs }
        val ledger = RepMax.Ledger(ordered) { exercises[it.exerciseId]?.metric }
        return buildMap {
            ordered.forEachIndexed { index, (session, sets) ->
                build(session, sets, ledger.first(index), exercises)?.let { put(session.id, it) }
            }
        }
    }
}
