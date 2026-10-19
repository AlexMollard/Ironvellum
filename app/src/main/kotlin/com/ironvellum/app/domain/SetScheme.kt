package com.ironvellum.app.domain

import androidx.annotation.StringRes
import com.ironvellum.app.R
import com.ironvellum.app.data.Words

/** How a lift's sets are laid out in a rite. Stored by [wire]; a word this build does not know reads as Straight. */
enum class SchemeType(val wire: String, @StringRes private val labelRes: Int) {
    STRAIGHT("straight", R.string.scheme_type_straight),
    TOP_BACKOFF("top_backoff", R.string.scheme_type_top_backoff),
    DROPS("drops", R.string.scheme_type_drops),
    CUSTOM("custom", R.string.scheme_type_custom);

    /** The layout's name as shown. */
    val label: String get() = Words.text(UiText.res(labelRes))

    companion object {
        fun fromWire(wire: String?): SchemeType = entries.firstOrNull { it.wire == wire } ?: STRAIGHT
    }
}

/**
 * One lift's plan of sets inside a rite entry. Straight is today's behaviour: every set the same, the entry's own
 * sets x reps at its load. The other schemes lay the sets out differently:
 *
 *  - TOP_BACKOFF: one heavy top set ([topReps] reps, [topRir] reps left), then [backoffSets] lighter back-offs of
 *    [backoffReps]. A back-off's load is [backoffPercent] lighter than the top (of the total load, body included, on a
 *    bodyweight lift) or, when [backoffFixedKg] is set, that fixed load.
 *  - DROPS: the last [dropSets] sets each get [dropCount] drops of [dropPercent] lighter, taken straight on.
 *  - CUSTOM: the kind of every set is [custom], in order.
 *
 * The entry keeps meaning something under every scheme: its sets are the working sets in all ([workingSets]), its
 * reps and load the top set's for TOP_BACKOFF and the plain figures for the rest, so everything that reads a rite
 * (coverage, the clock, the effort editor) needs no change.
 */
data class SetScheme(
    val type: SchemeType = SchemeType.STRAIGHT,
    val topReps: IntRange = 4..6,
    val topRir: IntRange = 0..1,
    val backoffSets: Int = 2,
    /** The back-offs' own load in kg, or null to take [backoffPercent] off the top set. */
    val backoffFixedKg: Double? = null,
    val backoffPercent: Int = DEFAULT_BACKOFF_PERCENT,
    val backoffReps: IntRange = 6..8,
    val dropSets: Int = 1,
    val dropCount: Int = 1,
    val dropPercent: Int = DEFAULT_DROP_PERCENT,
    val custom: List<SetKind> = emptyList(),
    /** Rest suggested after a top set, in seconds. */
    val restAfterTopSec: Int = DEFAULT_TOP_REST_SEC,
    /** Whether the trial begins with the warm-up ramp for the lift. */
    val warmupRamp: Boolean = false,
    /**
     * The reps (seconds, for a hold) a Straight or Drop-set lift is worked in, "10-15": progression climbs through
     * it instead of the training mode's band. Null is the entry's one target, as ever. The floor is the entry's own
     * reps; [withFloor] keeps the two together.
     */
    val workingReps: IntRange? = null,
) {
    val isStraight: Boolean get() = type == SchemeType.STRAIGHT

    /** The working sets the scheme makes (warm-ups and drops excluded), or [entrySets] when the scheme leaves it to the entry. */
    fun workingSets(entrySets: Int): Int = when (type) {
        SchemeType.TOP_BACKOFF -> 1 + backoffSets
        SchemeType.CUSTOM -> custom.count { it != SetKind.DROP }.coerceAtLeast(1)
        else -> entrySets
    }

    /** Every set of the lift after any warm-ups, in order, with its kind. */
    fun kinds(entrySets: Int): List<SetKind> = when (type) {
        SchemeType.STRAIGHT -> List(entrySets) { SetKind.WORKING }
        SchemeType.TOP_BACKOFF -> listOf(SetKind.TOP) + List(backoffSets) { SetKind.BACKOFF }
        SchemeType.DROPS -> buildList {
            val firstDropper = (entrySets - dropSets).coerceAtLeast(0)
            for (i in 0 until entrySets) {
                add(SetKind.WORKING)
                if (i >= firstDropper) repeat(dropCount) { add(SetKind.DROP) }
            }
        }
        SchemeType.CUSTOM -> custom.ifEmpty { listOf(SetKind.WORKING) }
    }

    /** The scheme with its set counts made to fit an entry of [sets] working sets (a Forge rewrite changed the count). */
    fun fitTo(sets: Int): SetScheme = when (type) {
        SchemeType.TOP_BACKOFF -> copy(backoffSets = (sets - 1).coerceIn(1, MAX_BACKOFF_SETS))
        SchemeType.DROPS -> copy(dropSets = dropSets.coerceAtMost(sets.coerceAtLeast(1)))
        SchemeType.CUSTOM -> if (workingSets(sets) == sets) this else SetScheme(warmupRamp = warmupRamp)
        else -> this
    }

    /** The scheme a lift keeps when it joins a superset, which cannot hold drops: Drop sets turn straight, Custom loses its drops. */
    fun withoutDrops(): SetScheme = when (type) {
        SchemeType.DROPS -> SetScheme(warmupRamp = warmupRamp, workingReps = workingReps)
        SchemeType.CUSTOM -> copy(custom = custom.filter { it != SetKind.DROP }).let { if (it.custom.isEmpty()) SetScheme(warmupRamp = warmupRamp) else it }
        else -> this
    }

    /** The reps range the lift's [kind] set is planned in, or null when it takes the entry's own reps. */
    fun repsRange(kind: SetKind): IntRange? = when {
        type == SchemeType.TOP_BACKOFF && kind == SetKind.TOP -> topReps
        type == SchemeType.TOP_BACKOFF && kind == SetKind.BACKOFF -> backoffReps
        kind == SetKind.WORKING && (type == SchemeType.STRAIGHT || type == SchemeType.DROPS) -> workingReps
        else -> null
    }

    /** The scheme with its [workingReps] floor moved to [floor] (the entry's reps changed); a floor at or past the top ends the range. */
    fun withFloor(floor: Int): SetScheme {
        val range = workingReps ?: return this
        return copy(workingReps = if (floor >= range.last) null else floor..range.last)
    }

    /**
     * Only the settings the chosen [type] reads, the rest back at their defaults: the sheet keeps every card's
     * settings while the lifter flips between them, and what is saved is just the one that was chosen.
     */
    fun normalised(): SetScheme = when (type) {
        SchemeType.STRAIGHT -> SetScheme(warmupRamp = warmupRamp, workingReps = workingReps)
        SchemeType.TOP_BACKOFF -> SetScheme(
            type = type, topReps = topReps, topRir = topRir, backoffSets = backoffSets, backoffFixedKg = backoffFixedKg,
            backoffPercent = if (backoffFixedKg != null) DEFAULT_BACKOFF_PERCENT else backoffPercent,
            backoffReps = backoffReps, restAfterTopSec = restAfterTopSec, warmupRamp = warmupRamp,
        )
        SchemeType.DROPS -> SetScheme(
            type = type, dropSets = dropSets, dropCount = dropCount, dropPercent = dropPercent, warmupRamp = warmupRamp,
            workingReps = workingReps,
        )
        SchemeType.CUSTOM -> SetScheme(type = type, custom = custom, warmupRamp = warmupRamp)
    }

    /** Written to the column: null for the plain default, so every existing rite stays exactly as it is. */
    fun encode(): String? = if (this == DEFAULT) null else buildString {
        append("t=").append(type.wire)
        when (type) {
            SchemeType.TOP_BACKOFF -> {
                append(";tr=").append(rangeText(topReps)).append(";rir=").append(rangeText(topRir))
                append(";bo=").append(backoffSets).append(";br=").append(rangeText(backoffReps))
                if (backoffFixedKg != null) append(";bf=").append(kgText(backoffFixedKg)) else append(";bp=").append(backoffPercent)
                append(";rt=").append(restAfterTopSec)
            }
            SchemeType.DROPS -> append(";ds=").append(dropSets).append(";dc=").append(dropCount).append(";dp=").append(dropPercent)
            SchemeType.CUSTOM -> append(";ck=").append(custom.joinToString(",") { it.wire })
            SchemeType.STRAIGHT -> Unit
        }
        if (workingReps != null && (type == SchemeType.STRAIGHT || type == SchemeType.DROPS)) append(";rr=").append(rangeText(workingReps))
        if (warmupRamp) append(";wr=1")
    }

    companion object {
        /**
         * One tap of the "up to" stepper over a range starting at [floor]: the top moves a rep, and a top at or under the
         * floor is no range at all (null, shown "–"). From no range, "more" makes it one rep wide.
         */
        fun stepRepsUp(floor: Int, up: Int?, delta: Int): Int? =
            ((up?.takeIf { it > floor } ?: floor) + delta).coerceAtMost(WORKING_REPS_MAX).takeIf { it > floor }

        /** The most reps (or seconds) a lift's range can reach: the rite editor's own reps field ceiling. */
        const val WORKING_REPS_MAX = 500

        const val DEFAULT_BACKOFF_PERCENT = 10
        const val DEFAULT_DROP_PERCENT = 20
        const val DEFAULT_TOP_REST_SEC = 180
        const val MAX_BACKOFF_SETS = 10
        const val MAX_DROPS = 4
        const val MAX_CUSTOM_SETS = 30
        val PERCENT_RANGE = 5..50
        val REPS_RANGE = 1..100
        val REST_RANGE_SEC = 30..600

        val DEFAULT = SetScheme()

        /** What the column holds back as a scheme: null or anything unreadable is Straight, every figure clamped to its sane range. */
        fun decode(text: String?): SetScheme {
            if (text.isNullOrBlank()) return DEFAULT
            val fields = text.split(';').mapNotNull { part ->
                val at = part.indexOf('=')
                if (at <= 0) null else part.substring(0, at).trim() to part.substring(at + 1).trim()
            }.toMap()
            val type = SchemeType.fromWire(fields["t"])
            val base = SetScheme(type = type)
            fun int(key: String, default: Int, range: IntRange) = fields[key]?.toIntOrNull()?.coerceIn(range) ?: default
            fun range(key: String, default: IntRange, within: IntRange): IntRange {
                val parts = fields[key]?.split('-') ?: return default
                val low = (parts.getOrNull(0)?.toIntOrNull() ?: return default).coerceIn(within)
                val high = (parts.getOrNull(1)?.toIntOrNull() ?: low).coerceIn(within)
                return low..maxOf(low, high)
            }
            return base.copy(
                topReps = range("tr", base.topReps, REPS_RANGE),
                topRir = range("rir", base.topRir, 0..10),
                backoffSets = int("bo", base.backoffSets, 1..MAX_BACKOFF_SETS),
                backoffFixedKg = fields["bf"]?.toDoubleOrNull()?.takeIf { it > 0.0 },
                backoffPercent = int("bp", base.backoffPercent, PERCENT_RANGE),
                backoffReps = range("br", base.backoffReps, REPS_RANGE),
                dropSets = int("ds", base.dropSets, 1..MAX_CUSTOM_SETS),
                dropCount = int("dc", base.dropCount, 1..MAX_DROPS),
                dropPercent = int("dp", base.dropPercent, PERCENT_RANGE),
                custom = fields["ck"]?.split(',')?.map { SetKind.fromWire(it) }?.filter { it != SetKind.WARMUP }
                    ?.take(MAX_CUSTOM_SETS).orEmpty(),
                restAfterTopSec = int("rt", base.restAfterTopSec, REST_RANGE_SEC),
                warmupRamp = fields["wr"] == "1",
                workingReps = fields["rr"]?.let { range("rr", 1..1, 1..WORKING_REPS_MAX) }
                    ?.takeIf { it.first < it.last && (type == SchemeType.STRAIGHT || type == SchemeType.DROPS) },
            )
        }

        private fun rangeText(r: IntRange) = if (r.first == r.last) "${r.first}" else "${r.first}-${r.last}"

        private fun kgText(kg: Double) = if (kg == Math.floor(kg)) kg.toLong().toString() else kg.toString()
    }
}

/** What one set of a scheme looks like as a bar: its kind and how high its load stands (1.0 is the heaviest). */
data class SchemeBar(val kind: SetKind, val level: Float)

/** The words a scheme is shown in: the rite row's line, the sheet's preview labels, the end-of-trial offer. */
object SchemeText {

    private fun rangeText(r: IntRange) = if (r.first == r.last) "${r.first}" else "${r.first}–${r.last}"

    fun reps(range: IntRange): String = rangeText(range)

    private fun last(n: Int): UiText = UiText.plural(R.plurals.scheme_drop_last, n)

    private fun times(n: Int): UiText = when (n) {
        1 -> UiText.res(R.string.scheme_times_once)
        2 -> UiText.res(R.string.scheme_times_twice)
        else -> UiText.plural(R.plurals.scheme_times_many, n)
    }

    /** The rite row's scheme line, "Top set + 2 back-offs"; null for Straight, whose own sets x reps line says it all. */
    fun summary(scheme: SetScheme, sets: Int): String? = when (scheme.type) {
        SchemeType.STRAIGHT -> null
        SchemeType.TOP_BACKOFF -> Words.text(UiText.plural(R.plurals.scheme_summary_top_backoffs, scheme.backoffSets))
        SchemeType.DROPS -> Words.text(
            UiText.res(
                R.string.scheme_summary_drops,
                UiText.plural(R.plurals.scheme_count_sets, sets),
                last(scheme.dropSets),
                times(scheme.dropCount),
            ),
        )
        SchemeType.CUSTOM -> Words.text(UiText.plural(R.plurals.scheme_summary_custom, scheme.custom.size))
    }

    /**
     * The figure a rite row prints for a lift. Top + back-offs reads its real plan, "2–4 @22kg · 3×4–5 @15kg"
     * ("3×4–5 −10%" for a percent back-off); every other scheme, and Straight, reads "sets×reps @load", the reps a
     * range ("3×10–15 @24kg") when the lift has one.
     * [load] formats a kg figure with its unit; null leaves loads out (a percent back-off still says its percent).
     * [hold] puts an "s" on the reps of a timed hold.
     */
    fun figure(scheme: SetScheme, sets: Int, reps: Int, kg: Double?, load: ((Double) -> String)?, hold: Boolean = false): String {
        fun at(v: Double?) = if (load != null && v != null) " @" + load(v) else ""
        if (scheme.type != SchemeType.TOP_BACKOFF) {
            val repsText = scheme.repsRange(SetKind.WORKING)?.let(::rangeText) ?: reps.toString()
            val shown = if (hold) Words.text(UiText.res(R.string.scheme_figure_seconds, repsText)) else repsText
            return "$sets × $shown" + at(kg)
        }
        val fixed = scheme.backoffFixedKg
        val back = "${scheme.backoffSets} × ${rangeText(scheme.backoffReps)}" + if (fixed != null) at(fixed) else " −${scheme.backoffPercent}%"
        return rangeText(scheme.topReps) + at(kg) + " · " + back
    }

    /** One short clause for the keep-offer's Sets line: "3 straight", "top + 2 back-offs". */
    fun clause(scheme: SetScheme, sets: Int): String = when (scheme.type) {
        SchemeType.STRAIGHT -> Words.text(UiText.res(R.string.scheme_clause_straight, sets))
        SchemeType.TOP_BACKOFF -> Words.text(UiText.plural(R.plurals.scheme_clause_top_backoffs, scheme.backoffSets))
        SchemeType.DROPS -> Words.text(UiText.res(R.string.scheme_clause_drops, last(scheme.dropSets), times(scheme.dropCount), scheme.dropPercent))
        SchemeType.CUSTOM -> Words.text(UiText.plural(R.plurals.scheme_clause_custom, scheme.custom.size))
    }

    /** The bars of the load-shape mini graphic: one per set (and one for the warm-up ramp when it is on). */
    fun bars(scheme: SetScheme, sets: Int): List<SchemeBar> {
        val kinds = scheme.kinds(sets)
        val backoffLevel = when {
            scheme.backoffFixedKg != null -> 0.8f
            else -> (1f - scheme.backoffPercent / 100f).coerceIn(0.4f, 0.95f)
        }
        var chain = 0.8f
        val out = mutableListOf<SchemeBar>()
        if (scheme.warmupRamp) out += SchemeBar(SetKind.WARMUP, 0.45f)
        for (kind in kinds) {
            val level = when (kind) {
                SetKind.TOP -> 1f
                SetKind.BACKOFF -> backoffLevel
                SetKind.DROP -> {
                    chain *= 1f - scheme.dropPercent / 100f
                    chain
                }
                else -> 0.8f.also { chain = it }
            }
            out += SchemeBar(kind, level)
        }
        return out
    }
}
