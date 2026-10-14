package com.ironvellum.app.domain

import kotlin.math.floor

/** What the planner needs to know about a lift to round a load the way the lifter can actually load it. */
data class LiftLoading(
    /** [Progression.weightStepKg]: the grid a plain load moves on. */
    val stepKg: Double,
    /** The vest and belt this lift is worn with ([WornLoad.forLift]); null when the kit does not apply. */
    val worn: LoadKit? = null,
    val unit: LoadUnit = LoadUnit.KG,
    val bodyweightKg: Double? = null,
    /** A bodyweight movement (a pull-up, a dip): its marked load is added to the body. */
    val bodyweightLift: Boolean = false,
    /** The share of [bodyweightKg] the movement moves ([MovementDifficulty.bodyShare]): a push-up carries about two thirds of it. */
    val bodyShare: Double = 1.0,
) {
    /** The body this lift actually moves: what a percent comes off, together with the added load. */
    val movedBodyKg: Double get() = (bodyweightKg?.takeIf { it > 0.0 } ?: 0.0) * bodyShare
}

/** What a back-off was rounded to, so the trial can say so: "Rounded to your vest blocks". */
enum class Rounding(val note: String) {
    VEST_BLOCKS("Rounded to your vest blocks."),
    BELT_PLATES("Rounded to your belt plates."),
}

/** One set the scheme plans. [reps] is the figure to start on; [range] the band it is planned in, when the scheme has one. */
data class PlannedSet(
    val kind: SetKind,
    val reps: Int,
    val weightKg: Double?,
    val range: IntRange? = null,
    val rounding: Rounding? = null,
)

/** A lift's last top set, what a new one is asked to beat. */
data class TopMark(val reps: Int, val weightKg: Double?)

/** The load and reps a top set is suggested at, and the top set it is meant to beat. */
data class TopSuggestion(val reps: Int, val weightKg: Double?, val beats: TopMark)

/**
 * A scheme turned into the sets of a trial, and the loads that follow from a logged top set. Pure: the trial's
 * begin and the top set's tick both call it, and the rite sheet's "You'll do" preview shows the same sets.
 */
object SchemePlan {

    /** Reps of a back-off: the middle of its band, rounded up (6-8 is 7, 4-5 is 5). */
    fun backoffStartReps(range: IntRange): Int = (range.first + range.last + 1) / 2

    /**
     * The back-off that follows a top set of [topReps] at [topKg].
     *
     * A fixed load is taken as given. Otherwise the percent comes off the total load: a bodyweight lift counts the
     * body it moves ([LiftLoading.movedBodyKg]: all of it on a pull-up or dip, about two thirds on a push-up), so
     * 10% lighter than 79 kg + 18.7 kg is 87.9 kg all in, 8.9 kg added, then rounded DOWN onto what the lifter can
     * load (vest blocks, or belt plates on the lift's own step) and never above the percent asked for.
     * Under one block that is bodyweight, null. An unweighted bodyweight top set has no load to take off, so the
     * back-off is two reps fewer instead.
     */
    fun backoff(topKg: Double?, topReps: Int, scheme: SetScheme, lift: LiftLoading): PlannedSet {
        val reps = backoffStartReps(scheme.backoffReps)
        val range = scheme.backoffReps
        val fixed = scheme.backoffFixedKg
        if (fixed != null) return PlannedSet(SetKind.BACKOFF, reps, fixed, range)
        val top = (topKg ?: 0.0).coerceAtLeast(0.0)
        if (top <= 0.0) {
            return if (lift.bodyweightLift) PlannedSet(SetKind.BACKOFF, (topReps - FEWER_REPS).coerceAtLeast(1), null)
            else PlannedSet(SetKind.BACKOFF, reps, topKg, range)
        }
        val keep = 1.0 - scheme.backoffPercent / 100.0
        return if (lift.bodyweightLift) {
            val body = lift.movedBodyKg
            val added = (body + top) * keep - body
            val rounded = floorToKit(added, lift)
            PlannedSet(SetKind.BACKOFF, reps, rounded, range, roundingFor(rounded, lift))
        } else {
            PlannedSet(SetKind.BACKOFF, reps, floorToStep(top * keep, lift)?.takeIf { it > 0.0 }, range)
        }
    }

    /**
     * The load a Fixed back-off starts on when the lifter switches to it: what the "% lighter" option makes of
     * [topKg] right now (the figure the sheet's preview shows), so the switch changes nothing until it is edited.
     * Falls back to the lift's smallest step when the percent leaves nothing to load (no top load, under one block).
     */
    fun seedFixedBackoff(topKg: Double?, scheme: SetScheme, lift: LiftLoading): Double {
        val percent = scheme.copy(backoffFixedKg = null)
        return backoff(topKg, scheme.topReps.first, percent, lift).weightKg?.takeIf { it > 0.0 }
            ?: lift.worn?.firstStepKg(lift.unit)
            ?: lift.stepKg.coerceAtLeast(0.5)
    }

    /** A drop of [percent] lighter than [fromKg], rounded down onto the lift's loads; null at bodyweight. */
    fun drop(fromKg: Double?, percent: Int, lift: LiftLoading): Double? {
        val from = (fromKg ?: 0.0).coerceAtLeast(0.0)
        if (from <= 0.0) return null
        val keep = 1.0 - percent / 100.0
        if (!lift.bodyweightLift) return floorToStep(from * keep, lift)?.takeIf { it > 0.0 }
        val body = lift.movedBodyKg
        return floorToKit((body + from) * keep - body, lift)
    }

    /** One set of a lift as [dropLoads] reads it. [edited]: the lifter set its load by hand, so it is theirs. */
    data class DropRow(val isDrop: Boolean, val done: Boolean, val weightKg: Double?, val edited: Boolean)

    /**
     * The load each set of a lift's [rows] (warm-ups left out, in order) should carry once its neighbours moved:
     * a Drop still to do is [percent] lighter than the set right before it, so a drop follows the load actually
     * logged or typed on that set, not the one the trial began with. Every other set, a done drop and a drop whose
     * load the lifter set by hand keep their own, and a chain of drops steps down from one to the next.
     */
    fun dropLoads(rows: List<DropRow>, percent: Int, lift: LiftLoading): List<Double?> {
        val out = ArrayList<Double?>(rows.size)
        rows.forEachIndexed { i, row ->
            val kg = if (row.isDrop && !row.done && !row.edited && i > 0) drop(out[i - 1], percent, lift) else row.weightKg
            out += kg
        }
        return out
    }

    /**
     * Every set the scheme makes for an entry of [entrySets] x [entryReps] at [entryKg], after any warm-ups: the
     * top set at [top] when there is a suggestion for it, else at the entry's own load. A back-off's figures are
     * worked out from the top set it follows, so what the trial begins with is already what it will refill to.
     */
    fun plan(
        scheme: SetScheme,
        entrySets: Int,
        entryReps: Int,
        entryKg: Double?,
        lift: LiftLoading,
        top: TopSuggestion? = null,
    ): List<PlannedSet> {
        val topKg = top?.weightKg ?: entryKg
        val topReps = top?.reps ?: if (scheme.type == SchemeType.TOP_BACKOFF) scheme.topReps.first else entryReps
        var previousKg = entryKg
        val out = mutableListOf<PlannedSet>()
        for (kind in scheme.kinds(entrySets)) {
            val set = when (kind) {
                SetKind.TOP -> PlannedSet(kind, topReps, topKg, scheme.repsRange(kind))
                SetKind.BACKOFF -> backoff(topKg, topReps, scheme, lift)
                SetKind.DROP -> PlannedSet(kind, entryReps, drop(previousKg, scheme.dropPercent, lift))
                else -> PlannedSet(kind, entryReps, entryKg, scheme.repsRange(kind))
            }
            previousKg = set.weightKg
            out += set
        }
        return out
    }

    /**
     * What a new top set is suggested at, from the lift's last one: one more rep at the same load while the reps
     * range has room, else the next load up the kit's steps at the range's floor. When the lifter has set the rite's
     * top load above that, the rite's load is the suggestion (a bump on purpose). Null with no last top set.
     */
    fun suggestTop(last: TopMark?, range: IntRange, riteKg: Double?, nextKg: (Double?) -> Double?): TopSuggestion? {
        last ?: return null
        val lastKg = last.weightKg
        val raised = riteKg != null && riteKg > (lastKg ?: 0.0) + LOAD_EPSILON
        val suggestion = when {
            raised -> TopSuggestion(range.first, riteKg, last)
            last.reps < range.last -> TopSuggestion((last.reps + 1).coerceAtMost(range.last), lastKg, last)
            else -> TopSuggestion(range.first, nextKg(lastKg) ?: lastKg, last)
        }
        return suggestion
    }

    /** The rest a top set suggests: the scheme's own, 3:00 by default. */
    fun restAfterTop(scheme: SetScheme?): Int = scheme?.takeIf { it.type == SchemeType.TOP_BACKOFF }?.restAfterTopSec ?: SetScheme.DEFAULT_TOP_REST_SEC

    // ----------------------------------------------------------------- rounding

    private fun floorToKit(added: Double, lift: LiftLoading): Double? {
        if (added <= 0.0) return null
        val worn = lift.worn
        if (worn != null) {
            // Under the first block there is nothing to hang: that is bodyweight, not "one block".
            if (added < worn.firstStepKg(lift.unit) - LOAD_EPSILON) return null
            return worn.floorKg(added)
        }
        return floorToStep(added, lift)?.takeIf { it > 0.0 }
    }

    /** [kg] down to the lift's own step: the 2.5 kg grid, or the lb grid on the figure the lifter reads. */
    private fun floorToStep(kg: Double, lift: LiftLoading): Double? {
        if (kg <= 0.0) return null
        if (lift.unit == LoadUnit.LB) {
            val grid = Loads.lbGrid(lift.stepKg)
            val lb = Loads.snap(kg, lift.unit)
            val down = floor(lb / grid + 1e-9) * grid
            return if (down <= 0.0) null else Loads.toKg(down, lift.unit)
        }
        val grid = minOf(lift.stepKg, Progression.WEIGHT_STEP_KG).coerceAtLeast(0.25)
        val down = floor(kg / grid + 1e-9) * grid
        return if (down <= 0.0) null else Math.round(down * 1000.0) / 1000.0
    }

    private fun roundingFor(kg: Double?, lift: LiftLoading): Rounding? {
        if (kg == null) return null
        val worn = lift.worn ?: return Rounding.BELT_PLATES
        val max = worn.vestMaxKg ?: return Rounding.BELT_PLATES
        return if (worn.dipBelt && kg > max + LOAD_EPSILON) Rounding.BELT_PLATES else Rounding.VEST_BLOCKS
    }

    private const val FEWER_REPS = 2
    private const val LOAD_EPSILON = 0.0005
}
