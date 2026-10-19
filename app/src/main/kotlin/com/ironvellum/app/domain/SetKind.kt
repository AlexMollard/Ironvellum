package com.ironvellum.app.domain

import androidx.annotation.StringRes
import com.ironvellum.app.R
import com.ironvellum.app.data.Words

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
    @StringRes private val labelRes: Int,
    /** The letter on the set row's tag; none for a plain working set. */
    @StringRes private val tagRes: Int?,
    /** One line for the explainer sheet. */
    private val blurbText: UiText,
) {
    WORKING("working", R.string.setkind_working_label, null, UiText.themed(R.string.setkind_working_blurb, R.string.setkind_working_blurb__t)),
    WARMUP("warmup", R.string.setkind_warmup_label, R.string.setkind_warmup_tag, UiText.themed(R.string.setkind_warmup_blurb, R.string.setkind_warmup_blurb__t)),
    TOP("top", R.string.setkind_top_label, R.string.setkind_top_letter, UiText.themed(R.string.setkind_top_blurb, R.string.setkind_top_blurb__t)),
    BACKOFF("backoff", R.string.setkind_backoff_label, R.string.setkind_backoff_tag, UiText.res(R.string.setkind_backoff_blurb)),
    DROP("drop", R.string.setkind_drop_label, R.string.setkind_drop_tag, UiText.res(R.string.setkind_drop_blurb)),
    FAILURE("failure", R.string.setkind_failure_label, R.string.setkind_failure_tag, UiText.res(R.string.setkind_failure_blurb)),
    REST_PAUSE("rest_pause", R.string.setkind_rest_pause_label, R.string.setkind_rest_pause_tag, UiText.res(R.string.setkind_rest_pause_blurb));

    /** The name shown for this kind, in the app's language. */
    val label: String get() = Words.text(UiText.res(labelRes))

    /** The tag letter, empty for a plain working set. */
    val tag: String get() = tagRes?.let { Words.text(UiText.res(it)) }.orEmpty()

    val blurb: String get() = Words.text(blurbText)

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
