package com.ironvellum.app.domain

/**
 * What kind of set a logged set is. Working is the default and the only kind a
 * plain tick ever writes. Top, Back-off, Drop, Failure and Rest-pause are working
 * sets done a particular way: they count for XP and volume exactly as Working
 * does (on a lift that has a top set, only the top set is judged for a peak or
 * a record, see [PeakSets]). A Warm-up is the one kind that never counts: it is stored unticked, so
 * everything that counts ticked sets leaves it out ([countsAsWork]).
 */
enum class SetKind(
    /** The word an archive and a CSV write. Never renamed: old archives read it back. */
    val wire: String,
    val label: String,
    /** The letter on the set row's tag; blank for a plain working set. */
    val tag: String,
    /** One line for the explainer sheet. */
    val blurb: String,
) {
    WORKING("working", "Working", "", "A normal set you count: it earns XP and can set a peak."),
    WARMUP("warmup", "Warm-up", "W", "A lighter set to prepare. It never earns XP, volume or a peak."),
    TOP("top", "Top set", "T", "The heaviest set of the lift, taken near your limit. It counts as a working set and is the one a peak is judged on."),
    BACKOFF("backoff", "Back-off", "B", "A lighter set after the top set, for volume. It counts as a working set."),
    DROP("drop", "Drop", "D", "Straight after a set to the limit, cut the load and keep going. It counts as a working set."),
    FAILURE("failure", "Failure", "F", "Taken until no clean rep was left. It counts as a working set."),
    REST_PAUSE("rest_pause", "Rest-pause", "R", "A short breath mid-set, then more reps at the same load. It counts as a working set.");

    /** False only for a warm-up. */
    val countsAsWork: Boolean get() = this != WARMUP

    companion object {
        /** The kind written as [wire]; a word this build does not know reads as a plain working set. */
        fun fromWire(wire: String?): SetKind = entries.firstOrNull { it.wire == wire?.trim()?.lowercase() } ?: WORKING

        /** The stored enum name, the way `metric` is stored; an unknown name reads as Working. */
        fun fromName(name: String?): SetKind = entries.firstOrNull { it.name == name } ?: WORKING

        /**
         * A Strong, Hevy or FitNotes "Set Type" cell. Hevy writes warmup, dropset
         * and failure; anything else (normal, blank, a word we do not know) is a
         * working set.
         */
        fun fromImport(type: String?): SetKind = when (type?.trim()?.lowercase()) {
            "warmup", "warm-up", "warm up", "w" -> WARMUP
            "dropset", "drop set", "drop", "d" -> DROP
            "failure", "to failure", "f" -> FAILURE
            "rest-pause", "rest pause", "restpause", "rest_pause" -> REST_PAUSE
            "top", "top set", "topset" -> TOP
            "backoff", "back-off", "back off", "back-off set", "backoffset" -> BACKOFF
            else -> WORKING
        }

        /** What a CSV export writes back in Set Type: Strong's own blank for working, else the Hevy word. */
        fun csvType(kind: SetKind): String = when (kind) {
            WORKING -> ""
            WARMUP -> "warmup"
            TOP -> "top"
            BACKOFF -> "backoff"
            DROP -> "dropset"
            FAILURE -> "failure"
            REST_PAUSE -> "rest_pause"
        }
    }
}
