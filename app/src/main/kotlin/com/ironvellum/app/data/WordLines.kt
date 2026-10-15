package com.ironvellum.app.data

/**
 * Whole lines that swapping a word cannot turn into plain English: the app's own voice ("The Ledger refused") or
 * a sentence whose shape changes with the word. Each is written out once for each vocabulary, themed first, and
 * read through [Words.pick] so it follows the Settings switch. docs/GLOSSARY.md lists the concepts behind them.
 */
object Lines {
    val stumbled: String
        get() = Words.pick("The Ledger stumbled — try again in a moment", "Something went wrong — try again in a moment")

    fun alreadyInLibrary(name: String): String = Words.pick(
        "\"$name\" is already in the Ledger. Pick it from the list instead.",
        "\"$name\" is already in the exercise list. Pick it from the list instead.",
    )

    val masterworkAnswer: String
        get() = Words.pick("The Ledger answers.", "A masterwork find.")

    val noMuscleData: String
        get() = Words.pick("The Ledger holds no muscle data for this exercise yet.", "No muscle data for this exercise yet.")

    fun noExerciseMatching(query: String): String = Words.pick(
        "The Ledger holds no exercise matching \"$query\"",
        "No exercise matches \"$query\"",
    )

    val noExerciseForFilters: String
        get() = Words.pick("The Ledger holds no exercise for these filters", "No exercise matches these filters")

    val linking: String
        get() = Words.pick("Linking to the Ledger…", "Connecting…")

    fun refused(reason: String): String = Words.pick("The Ledger refused: $reason", "That did not go through: $reason")

    val leaderboardUnread: String
        get() = Words.pick(
            "The Ledger could not read the reckoning. Try again in a moment.",
            "The leaderboard could not be loaded. Try again in a moment.",
        )

    val veilBoardUnread: String
        get() = Words.pick(
            "The Ledger could not read the Veil's reckoning. Try again in a moment.",
            "The Veil leaderboard could not be loaded. Try again in a moment.",
        )

    val veilBoardNote: String
        get() = Words.pick(
            "Ranks Veil progress — echoes inscribed — separate from the training reckoning.",
            "Ranks Veil progress — echoes inscribed — separate from the training leaderboard.",
        )

    val noVeilEchoes: String
        get() = Words.pick(
            "No Ironbound has inscribed echoes yet. The Veil is empty and every top standing here is unclaimed.",
            "No lifter has inscribed echoes yet. The Veil is empty and every top position here is open.",
        )

    val claimStanding: String
        get() = Words.pick(
            "No allies stand here yet. Log one of its exercises to claim a standing.",
            "No friends are ranked here yet. Log one of its exercises to get a position.",
        )

    val noBodyData: String
        get() = Words.pick("The Ledger knows nothing of your frame yet.", "No body measurements yet.")

    val ledgerTitle: String
        get() = Words.pick("THE LEDGER", "STATS")

    val welcomeTagline: String
        get() = Words.pick(
            "Seal each trial. Keep the Ledger. Watch the Veil fill.",
            "Finish each workout. Keep your stats. Watch the Veil fill.",
        )

    val forgeFits: String
        get() = Words.pick(
            "The Forge fits each rite inside this time, warm-up left out.",
            "The plan builder fits each routine inside this time, warm-up left out.",
        )

    val summonsBlocked: String
        get() = Words.pick(
            "The Summons won't arrive until you allow notifications.",
            "The reminder won't arrive until you allow notifications.",
        )

    val claimStandard: String
        get() = Words.pick("CLAIM STANDARD", "MASTERY STANDARD")

    val claimAt: String
        get() = Words.pick("claim", "mastered at")

    fun claimHelp(cleared: Boolean, target: String, xp: Int): String = Words.pick(
        (if (cleared) "You've cleared this: claim it when you're ready. " else "Claiming is self-declared: the app never claims for you. ") +
            "Claim only once you can hit $target on demand. It awards $xp XP and can be undone.",
        (if (cleared) "You've cleared this: mark it as mastered when you're ready. " else "Marking as mastered is self-declared: the app never does it for you. ") +
            "Do it only once you can hit $target on demand. It awards $xp XP and can be undone.",
    )

    fun masterAll(count: Int): String = Words.pick(
        "Master all $count techniques. Every path is yours.",
        "Master all $count skills.",
    )

    val practiseSkills: String
        get() = Words.pick(
            "Practise techniques on Paths and log attempts in the Journal.",
            "Practise skills on the Skills tab and log attempts in the practice log.",
        )

    fun feedStale(error: String): String = Words.pick(
        "The ink has faded — these tidings are from your last sync: $error",
        "Could not refresh — this feed is from your last sync: $error",
    )

    fun inboxStale(error: String): String = Words.pick(
        "The ink has faded — these missives are from your last sync: $error",
        "Could not refresh — this inbox is from your last sync: $error",
    )

    /** The Settings switch's example. The same in both modes: it names both vocabularies. */
    val themedNamesCaption: String
        get() = "Uses the app's fantasy names: Workout becomes Trial, Routine becomes Rite."

    val veilHowTitle: String
        get() = "How the Veil works"
}
