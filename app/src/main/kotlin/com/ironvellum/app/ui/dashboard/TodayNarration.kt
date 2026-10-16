package com.ironvellum.app.ui.dashboard

import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.UiText

/**
 * The one italic line under the routine name, by state. All of Today's narration lives here so the voice is read,
 * tested and kept in one place. Themed mode keeps the Ledger's own voice; plain mode says the same thing without
 * it. Each sentence is a `narration_*` string and its `__t` twin in strings.xml (docs/STRINGS.md); the counts are
 * plurals, so "1 set" and "7 sets" are each written whole. [setsDone] and [setsTotal] are the live workout's
 * working sets; [daysKept] is the streak's count.
 */
internal fun narratorLine(kind: DayKind, setsDone: Int = 0, setsTotal: Int = 0, daysKept: Int = 0): String =
    Words.text(narration(kind, setsDone, setsTotal, daysKept))

private fun narration(kind: DayKind, setsDone: Int, setsTotal: Int, daysKept: Int): UiText = when (kind) {
    DayKind.BEGIN ->
        if (daysKept > 0) UiText.themedPlural(R.plurals.narration_begin_streak, R.plurals.narration_begin_streak__t, daysKept)
        else UiText.themed(R.string.narration_begin, R.string.narration_begin__t)
    DayKind.LIVE -> when {
        setsDone <= 0 -> UiText.themed(R.string.narration_live_none, R.string.narration_live_none__t)
        setsDone >= setsTotal -> UiText.themed(R.string.narration_live_done, R.string.narration_live_done__t)
        setsDone * 3 < setsTotal -> UiText.themedPlural(R.plurals.narration_live_start, R.plurals.narration_live_start__t, setsDone)
        else -> UiText.themedPlural(R.plurals.narration_live_half, R.plurals.narration_live_half__t, setsDone)
    }
    DayKind.SEALED -> UiText.themed(R.string.narration_sealed, R.string.narration_sealed__t)
    DayKind.PLANNED -> UiText.themed(R.string.narration_planned, R.string.narration_planned__t)
    DayKind.RESPITE ->
        if (daysKept > 0) UiText.themed(R.string.narration_respite_streak, R.string.narration_respite_streak__t)
        else UiText.themed(R.string.narration_respite, R.string.narration_respite__t)
    DayKind.NO_CYCLE -> UiText.themed(R.string.narration_no_cycle, R.string.narration_no_cycle__t)
}
