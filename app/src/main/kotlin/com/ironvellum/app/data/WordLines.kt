package com.ironvellum.app.data

import com.ironvellum.app.R
import com.ironvellum.app.domain.UiText

/**
 * Whole lines that swapping a word cannot turn into plain English: the app's own voice ("The Ledger refused") or
 * a sentence whose shape changes with the word. Each is a `line_*` string and its `__t` twin in strings.xml (written
 * whole in each vocabulary, docs/STRINGS.md) and is read through [Words] so it follows the Settings switch.
 * docs/GLOSSARY.md lists the concepts behind them. A line with no twin (`always_*`) is the same in both modes.
 */
object Lines {
    private fun line(plain: Int, themed: Int, vararg args: Any): String = Words.text(UiText.themed(plain, themed, *args))

    val stumbled: String
        get() = line(R.string.line_stumbled, R.string.line_stumbled__t)

    fun alreadyInLibrary(name: String): String = line(R.string.line_already_in_library, R.string.line_already_in_library__t, name)

    val masterworkAnswer: String
        get() = line(R.string.line_masterwork_answer, R.string.line_masterwork_answer__t)

    val noMuscleData: String
        get() = line(R.string.line_no_muscle_data, R.string.line_no_muscle_data__t)

    fun noExerciseMatching(query: String): String = line(R.string.line_no_exercise_matching, R.string.line_no_exercise_matching__t, query)

    val noExerciseForFilters: String
        get() = line(R.string.line_no_exercise_for_filters, R.string.line_no_exercise_for_filters__t)

    val linking: String
        get() = line(R.string.line_linking, R.string.line_linking__t)

    fun refused(reason: String): String = line(R.string.line_refused, R.string.line_refused__t, reason)

    val leaderboardUnread: String
        get() = line(R.string.line_leaderboard_unread, R.string.line_leaderboard_unread__t)

    val veilBoardUnread: String
        get() = line(R.string.line_veil_board_unread, R.string.line_veil_board_unread__t)

    val veilBoardNote: String
        get() = line(R.string.line_veil_board_note, R.string.line_veil_board_note__t)

    val noVeilEchoes: String
        get() = line(R.string.line_no_veil_echoes, R.string.line_no_veil_echoes__t)

    val claimStanding: String
        get() = line(R.string.line_claim_standing, R.string.line_claim_standing__t)

    val noBodyData: String
        get() = line(R.string.line_no_body_data, R.string.line_no_body_data__t)

    val ledgerTitle: String
        get() = line(R.string.line_ledger_title, R.string.line_ledger_title__t)

    val welcomeTagline: String
        get() = line(R.string.line_welcome_tagline, R.string.line_welcome_tagline__t)

    val forgeFits: String
        get() = line(R.string.line_forge_fits, R.string.line_forge_fits__t)

    val claimStandard: String
        get() = line(R.string.line_claim_standard, R.string.line_claim_standard__t)

    val claimAt: String
        get() = line(R.string.line_claim_at, R.string.line_claim_at__t)

    /** Two whole sentences (cleared or not), each in both vocabularies, never a prefix glued to a shared tail. */
    fun claimHelp(cleared: Boolean, target: String, xp: Int): String =
        if (cleared) line(R.string.line_claim_help_cleared, R.string.line_claim_help_cleared__t, target, xp)
        else line(R.string.line_claim_help_open, R.string.line_claim_help_open__t, target, xp)

    fun masterAll(count: Int): String = line(R.string.line_master_all, R.string.line_master_all__t, count)

    val practiseSkills: String
        get() = line(R.string.line_practise_skills, R.string.line_practise_skills__t)

    fun feedStale(error: String): String = line(R.string.line_feed_stale, R.string.line_feed_stale__t, error)

    fun inboxStale(error: String): String = line(R.string.line_inbox_stale, R.string.line_inbox_stale__t, error)

    fun standingsStale(error: String?): String = line(R.string.line_standings_stale, R.string.line_standings_stale__t, error.orEmpty())

    val rosterStale: String
        get() = line(R.string.line_roster_stale, R.string.line_roster_stale__t)

    /** The tape pane's title and the Ledger row that opens it. */
    val tapeReadings: String
        get() = line(R.string.line_tape_readings, R.string.line_tape_readings__t)

    val firstReadingPrompt: String
        get() = line(R.string.line_first_reading_prompt, R.string.line_first_reading_prompt__t)

    /** The Settings switch's example. The same in both modes: it names both vocabularies. */
    val themedNamesCaption: String
        get() = Words.text(UiText.res(R.string.always_themed_names_caption))

    val veilHowTitle: String
        get() = Words.text(UiText.res(R.string.always_veil_how_title))
}
