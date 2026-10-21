package com.ironvellum.app.domain

import com.ironvellum.app.R

/**
 * What the live workout shows outside the app, as plain data: the notification (the Android 16 Live Update and the
 * ordinary ongoing notification under it) and the Quick Settings tile. Pure, so the wording, the segments and the
 * rest/between-sets switch are unit-tested; data/LiveWorkoutNotification.kt and LiveWorkoutTileService.kt only
 * paint what is decided here, so the shade, the lock screen, the chip and the tile can never disagree.
 */
object LiveWorkout {

    /** One state of the notification, for the sets as they stand and whether a rest is running. */
    data class Content(
        /** The exercise on, or the concept word while the trial opens. */
        val title: UiText,
        /** The line under the title: the set in hand, or the rest and the set after it. */
        val body: UiText,
        /** Before Android 16 the card expands to [body] plus the last set and the next exercise; null when there is nothing to add. */
        val expanded: UiText?,
        /** The status-bar chip's text between sets; null while resting (the chip counts the rest down) or before any set exists. */
        val chip: UiText?,
        /** "Push A · 6 / 16 sets", or the plain progress when the routine has no name. */
        val subText: UiText,
        /** One segment per exercise, its length the exercise's working sets. Empty while the trial opens. */
        val segments: List<Int>,
        val done: Int,
        val total: Int,
        val resting: Boolean,
        val allDone: Boolean,
        /** The set "Set done" ticks; null when every set is done. */
        val nextSetId: Long?,
    )

    /**
     * The notification for [sets] (warm-ups never count). [routine] is the workout's own name, [resting] whether a rest
     * is running now, [load] formats a weight with its unit ("47.5 kg") in the lifter's unit.
     */
    fun content(sets: List<SessionSet>, routine: String, resting: Boolean, load: (Double) -> String): Content {
        val working = sets.filter { !it.warmup }
        val total = working.size
        val done = working.count { it.done }
        val next = Supersets.nextSet(sets)
        val blocks = Supersets.blocks(sets)
        val segments = blocks.map { b -> b.sets.count { !it.warmup } }.filter { it > 0 }
        val allDone = total > 0 && next == null
        val isResting = resting && next != null

        fun count(set: SessionSet) = working.count { it.exerciseId == set.exerciseId }
        fun loadOf(set: SessionSet) = set.weightKg?.takeIf { it > 0.0 }?.let(load)
        fun holds(set: SessionSet) = set.durationSec != null && set.reps == 0
        fun repsText(set: SessionSet) = UiText.plural(R.plurals.live_figure_reps, set.reps)
        fun holdText(set: SessionSet) = UiText.res(R.string.live_figure_hold, set.durationSec ?: 0)

        /** "6 reps · 47.5 kg": what the set in hand asks. */
        fun long(set: SessionSet): UiText {
            val figure = if (holds(set)) holdText(set) else repsText(set)
            return loadOf(set)?.let { UiText.join(listOf(figure, UiText.Raw(it)), " · ") } ?: figure
        }

        /** "6 × 47.5 kg": the same set where space is short. */
        fun short(set: SessionSet): UiText {
            val load = loadOf(set)
            return when {
                holds(set) -> long(set)
                load == null -> repsText(set)
                else -> UiText.res(R.string.live_figure_reps_load, set.reps, load)
            }
        }

        val title = (next ?: sets.firstOrNull())?.exerciseName?.let { UiText.Raw(it) }
            ?: UiText.themed(R.string.live_tile_label, R.string.live_tile_label__t)
        val body = when {
            total == 0 -> UiText.themed(R.string.notification_trial_opening, R.string.notification_trial_opening__t)
            next == null -> UiText.themed(R.string.notification_trial_all_done, R.string.notification_trial_all_done__t)
            isResting -> UiText.res(R.string.live_rest_then, next.workingNumber(sets), count(next), short(next))
            else -> UiText.res(R.string.live_set_in_hand, next.workingNumber(sets), count(next), long(next))
        }
        val chip = when {
            total == 0 || isResting -> null
            next == null -> UiText.res(R.string.live_chip_done)
            else -> UiText.res(R.string.live_chip_set, next.workingNumber(sets), count(next))
        }

        val last = working.filter { it.done }.maxWithOrNull(compareBy({ it.exercisePosition }, { it.setIndex }))
            ?.let { UiText.res(R.string.live_last_set, short(it), it.workingNumber(sets), count(it)) }
        val upNext = next?.let { current ->
            val at = blocks.indexOfFirst { it.exerciseId == current.exerciseId }
            blocks.drop(at + 1).firstOrNull { it.hasPending && (it.group == null || it.group != current.supersetGroup) }
        }?.let { block ->
            val first = block.sets.first { it.isPending }
            val target = if (holds(first)) "${first.durationSec}s" else first.reps.toString()
            UiText.res(R.string.live_next_up, first.exerciseName, block.sets.count { it.isPending }, target)
        }
        val extra = listOfNotNull(last, upNext)

        val subText = if (routine.isNotBlank()) {
            UiText.plural(R.plurals.live_progress_named, total, done, total, routine.trim())
        } else {
            UiText.themedPlural(R.plurals.notification_trial_progress, R.plurals.notification_trial_progress__t, total, done, total)
        }
        return Content(
            title = title,
            body = body,
            expanded = extra.takeIf { it.isNotEmpty() }?.let { UiText.join(listOf(body, UiText.join(it, "\n")), "\n\n") },
            chip = chip,
            subText = subText,
            segments = segments,
            done = done,
            total = total,
            resting = isResting,
            allDone = allDone,
            nextSetId = next?.id,
        )
    }

    /** The Quick Settings tile: its label and subtitle, and whether it shows as on (a workout is running). */
    data class Tile(val label: UiText, val subtitle: UiText, val active: Boolean)

    /**
     * The tile for a workout in [sets] (null: none running), with [restLabel] the time left when a rest runs ("1:24")
     * and [todayRoutine] the name of today's planned routine when there is one.
     */
    fun tile(todayRoutine: String?, sets: List<SessionSet>?, restLabel: String?): Tile {
        val label = UiText.themed(R.string.live_tile_label, R.string.live_tile_label__t)
        if (sets == null) {
            val subtitle = todayRoutine?.takeIf { it.isNotBlank() }?.let { UiText.res(R.string.live_tile_idle_today, it) }
                ?: UiText.res(R.string.live_tile_idle_none)
            return Tile(label, subtitle, active = false)
        }
        val working = sets.filter { !it.warmup }
        val next = Supersets.nextSet(sets)
        val of = next?.let { n -> working.count { it.exerciseId == n.exerciseId } }
        return when {
            working.isEmpty() ->
                Tile(label, UiText.themed(R.string.notification_trial_opening, R.string.notification_trial_opening__t), true)
            next == null -> Tile(label, UiText.res(R.string.live_tile_all_done), true)
            restLabel != null -> Tile(
                UiText.res(R.string.live_tile_rest_label, restLabel),
                UiText.res(R.string.live_tile_rest_sub, next.workingNumber(sets), of!!),
                true,
            )
            else -> Tile(
                label,
                UiText.res(R.string.live_tile_live_sub, next.workingNumber(sets), of!!, working.count { it.done }, working.size),
                true,
            )
        }
    }
}
