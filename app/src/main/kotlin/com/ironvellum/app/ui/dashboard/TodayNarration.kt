package com.ironvellum.app.ui.dashboard

import com.ironvellum.app.data.Words
import com.ironvellum.app.ui.components.plural

/**
 * The one italic line under the routine name, by state. All of Today's narration lives here so the voice is read,
 * tested and kept in one place. Themed mode keeps the Ledger's own voice; plain mode says the same thing without
 * it. [setsDone] and [setsTotal] are the live workout's working sets; [daysKept] is the streak's count.
 * This file is a vocabulary file: it holds both vocabularies, so the glossary scan skips it.
 */
internal fun narratorLine(kind: DayKind, setsDone: Int = 0, setsTotal: Int = 0, daysKept: Int = 0): String =
    if (Words.themed) themedNarration(kind, setsDone, setsTotal, daysKept) else plainNarration(kind, setsDone, setsTotal, daysKept)

private fun themedNarration(kind: DayKind, setsDone: Int, setsTotal: Int, daysKept: Int): String = when (kind) {
    DayKind.BEGIN ->
        if (daysKept > 0) "The page is blank. $daysKept ${plural(daysKept, "day", "days")} of ink behind it."
        else "The page is blank."
    DayKind.LIVE -> when {
        setsDone <= 0 -> "The page is blank. Ink the first set."
        setsDone >= setsTotal -> "Every set is inked. Seal the page."
        setsDone * 3 < setsTotal -> "$setsDone ${plural(setsDone, "set", "sets")} inked. The page begins to fill."
        else -> "$setsDone ${plural(setsDone, "set", "sets")} inked. The page is half written."
    }
    DayKind.SEALED -> "The Ledger gilds its page."
    DayKind.PLANNED -> "The page waits for its day."
    DayKind.RESPITE ->
        if (daysKept > 0) "The Ledger rests its pen. Your oath holds." else "The Ledger rests its pen."
    DayKind.NO_CYCLE -> "Your cycle is unwritten. Forge one and its rites land here."
}

private fun plainNarration(kind: DayKind, setsDone: Int, setsTotal: Int, daysKept: Int): String = when (kind) {
    DayKind.BEGIN ->
        if (daysKept > 0) "Ready when you are. Your streak is $daysKept ${plural(daysKept, "day", "days")}."
        else "Ready when you are."
    DayKind.LIVE -> when {
        setsDone <= 0 -> "Log your first set."
        setsDone >= setsTotal -> "Every set is logged. Finish the workout."
        setsDone * 3 < setsTotal -> "$setsDone ${plural(setsDone, "set", "sets")} logged. Good start."
        else -> "$setsDone ${plural(setsDone, "set", "sets")} logged. Over halfway."
    }
    DayKind.SEALED -> "Workout finished. Nice work."
    DayKind.PLANNED -> "Planned for this day."
    DayKind.RESPITE ->
        if (daysKept > 0) "Rest day. Your streak holds." else "Rest day."
    DayKind.NO_CYCLE -> "You have no weekly plan yet. Build one and its routines land here."
}
