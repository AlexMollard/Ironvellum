package com.ironvellum.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * The vocabulary layer: every concept word the app shows (docs/GLOSSARY.md) routes through here, so one switch
 * chooses between the plain words (the default) and the themed names. [themed] is Compose state, so a screen that
 * reads a word recomposes the moment Settings flips it; non-Compose text (notifications, share text) reads the same
 * flag when it is built. Words are always computed at the point of use, never held in a `val` that outlives a flip.
 *
 * An install that existed before this setting keeps the themed names it has always seen; a fresh one starts plain
 * ([defaultThemed]). The choice is decided once and stored, so it never changes behind the lifter's back.
 * User-entered names (a routine called "Full Body A") are data and never pass through here.
 */
object Words {
    internal const val PREFS = "appearance"
    internal const val KEY = "themedNames"

    /**
     * True when the themed names are shown. Read it only to branch on mode; prefer the words below. [init] sets it
     * before anything prints a word, so the initial value only matters to unit tests, which keep the names the
     * suite was written against.
     */
    var themed: Boolean by mutableStateOf(true)
        private set

    /** Reads (or, on the first run of this build, decides and stores) the mode. Call before anything prints a word. */
    fun init(context: Context, databaseExists: Boolean, welcomeSeen: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = if (prefs.contains(KEY)) prefs.getBoolean(KEY, false) else null
        val decided = defaultThemed(stored, databaseExists, welcomeSeen)
        if (stored == null) prefs.edit { putBoolean(KEY, decided) }
        themed = decided
    }

    fun setThemed(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY, on) }
        themed = on
        // Channel names are words too; registering a channel again with the same id renames it.
        runCatching { Notifications.ensureChannels(context.applicationContext) }
    }

    /** Sets the mode without touching storage; for tests that need either vocabulary. */
    fun useForTest(on: Boolean) {
        themed = on
    }

    /**
     * The mode when nothing is stored yet: a lifter who already used the app (its database exists, or the
     * Welcome screen was seen) keeps the themed names; a fresh install gets the plain words.
     */
    fun defaultThemed(stored: Boolean?, databaseExists: Boolean, welcomeSeen: Boolean): Boolean =
        stored ?: (databaseExists || welcomeSeen)

    /**
     * One concept in both vocabularies. Forms: [s] lower case, [c] capitalised, [t] as the themed name is written
     * mid-sentence (a proper noun) but lower case in plain, [u] upper case, each with a plural twin ([pl], [cpl],
     * [tpl], [upl]); [a] and [ca] carry the article ("a routine", "an ally"). [toString] is [s].
     */
    class Concept(
        private val themed: String,
        private val plain: String,
        themedPlural: String? = null,
        plainPlural: String? = null,
        private val themedProper: String = titleCase(themed),
        themedProperPlural: String? = null,
        private val keepCase: Boolean = false,
    ) {
        private val themedPl = themedPlural ?: (themed + "s")
        private val plainPl = plainPlural ?: (plain + "s")
        private val themedProperPl = themedProperPlural ?: (if (themedPlural != null) titleCase(themedPlural) else themedProper + "s")

        private val sing get() = if (Words.themed) themed else plain
        private val plur get() = if (Words.themed) themedPl else plainPl

        val s: String get() = sing
        val pl: String get() = plur
        val c: String get() = if (Words.themed) cap(themedProper) else if (keepCase) plain else cap(plain)
        val cpl: String get() = if (Words.themed) cap(themedProperPl) else if (keepCase) plainPl else cap(plainPl)
        val t: String get() = if (Words.themed) themedProper else plain
        val tpl: String get() = if (Words.themed) themedProperPl else plainPl
        val u: String get() = sing.uppercase()
        val upl: String get() = plur.uppercase()
        val a: String get() = article(sing) + " " + sing
        val ca: String get() = cap(article(sing)) + " " + sing
        val at: String get() = article(t) + " " + t
        val cat: String get() = cap(article(t)) + " " + t

        override fun toString(): String = s

        /** [text] with this concept's themed word (and its plural) swapped for the plain one, keeping the case. */
        internal fun swapToPlain(text: String): String {
            var out = text
            val pairs = if (themedPl == themed) listOf(themed to plain) else listOf(themedPl to plainPl, themed to plain)
            for ((from, to) in pairs) {
                if (from == to) continue
                out = Regex("(?i)(?<!\\w)" + Regex.escape(from) + "(?!\\w)").replace(out) { m ->
                    val word = m.value
                    when {
                        word.length > 1 && word == word.uppercase() -> to.uppercase()
                        word[0].isUpperCase() && !keepCase && startsSentence(out, m.range.first) -> cap(to)
                        else -> to
                    }
                }
            }
            return out
        }
    }

    /**
     * A sentence the server wrote ("That Ironbound is not in your circle") shown in the current vocabulary. The
     * migrations raise their messages in the themed words, so plain mode swaps them here; themed mode shows them as is.
     */
    fun fromServer(text: String): String =
        if (themed) text else serverTerms.fold(text) { acc, concept -> concept.swapToPlain(acc) }

    /**
     * The profile name to show. A fresh profile is stored as "Ironbound" whatever the mode (it is a sentinel the
     * sign-up and cloud code match), so the default reads as the current word for a lifter; a typed name never changes.
     */
    fun displayName(stored: String): String =
        if (stored.equals("Ironbound", ignoreCase = true) || stored.equals("Lifter", ignoreCase = true)) lifter.c else stored

    /** The line for the current mode, for text that cannot be reached by swapping words. Used only by the vocabulary files. */
    fun pick(themedText: String, plainText: String): String = if (themed) themedText else plainText

    /**
     * A catalogue whose text names concepts (a list of deeds, crests, skills), built once for each vocabulary and
     * handed out for the current one. Declare it as `val ALL: List<X> by Words.perMode { listOf(...) }`: reading it
     * inside a composable follows the Settings switch, where a plain `val` would keep the first mode's words.
     */
    class PerMode<T>(private val build: () -> T) {
        @Volatile private var themedValue: T? = null
        @Volatile private var plainValue: T? = null

        val value: T
            get() = if (Words.themed) {
                themedValue ?: build().also { themedValue = it }
            } else {
                plainValue ?: build().also { plainValue = it }
            }

        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = value
    }

    fun <T> perMode(build: () -> T): PerMode<T> = PerMode(build)

    /** True when the word at [index] opens the text or a sentence, so a capital there is grammar, not a proper noun. */
    private fun startsSentence(text: String, index: Int): Boolean =
        text.substring(0, index).trimEnd().let { it.isEmpty() || it.last() in ".!?:" }

    private fun titleCase(text: String): String =
        text.split(' ').joinToString(" ") { if (it == "the") it else cap(it) }

    private fun cap(text: String): String = text.replaceFirstChar { it.uppercase() }

    private fun article(word: String): String =
        if ((word.firstOrNull() ?: ' ').lowercaseChar() in "aeiou") "an" else "a"

    // You
    val lifter = Concept("ironbound", "lifter", themedPlural = "ironbound", plainPlural = "lifters", themedProper = "Ironbound")
    val trueName = Concept("true name", "display name")
    val folio = Concept("folio", "profile")
    val frame = Concept("frame", "body", plainPlural = "bodies")

    // Training
    val rite = Concept("rite", "routine")
    val cycle = Concept("cycle", "weekly plan")
    val trial = Concept("trial", "workout")
    val openTrial = Concept("open trial", "empty workout", themedProper = "Open Trial", themedProperPlural = "Open Trials")
    val seal = Concept("seal", "finish", plainPlural = "finishes")
    val seals = Concept("seals", "finishes")
    val sealed = Concept("sealed", "finished")
    val sealing = Concept("sealing", "finishing")
    val unsealed = Concept("unsealed", "unfinished")
    val chronicle = Concept("chronicle", "history", plainPlural = "histories")
    val theChronicle = Concept("the chronicle", "your history", themedProper = "the Chronicle")
    val yours = Concept("yours", "custom")
    val peak = Concept("peak", "PR", keepCase = true)
    val oath = Concept("oath", "streak")
    val respite = Concept("respite", "rest day")
    val summons = Concept("summons", "reminder", themedPlural = "summonses", themedProper = "Summons")
    val theSummons = Concept("the summons", "reminder", themedPlural = "the summonses", themedProper = "the Summons")
    val theBinding = Concept("the binding", "setup", themedProper = "the Binding")
    val pattern = Concept("pattern", "template")
    val forge = Concept("forge", "build", plainPlural = "builds")
    val forged = Concept("forged", "built")
    val theForge = Concept("the forge", "plan builder", themedPlural = "the forges", themedProper = "the Forge")
    val temper = Concept("temper", "improve", themedPlural = "tempers", plainPlural = "improves")
    val armoury = Concept("armoury", "equipment", themedPlural = "armouries", plainPlural = "equipment")

    // Codex
    val codex = Concept("codex", "progress", themedPlural = "codices", plainPlural = "progress")
    val deed = Concept("deed", "achievement")
    val path = Concept("path", "skill tree")
    /** The section that lists the paths (a tab label, a button). */
    val paths = Concept("paths", "skills", plainPlural = "skills")
    val technique = Concept("technique", "skill")
    val claim = Concept("claim", "mark as mastered", plainPlural = "marks as mastered")
    val claiming = Concept("claiming", "marking as mastered")
    val claimed = Concept("claimed", "mastered")
    val journal = Concept("journal", "practice log")

    // Ledger
    val ledger = Concept("ledger", "stats", plainPlural = "stats")
    val reading = Concept("reading", "measurement")
    /** "Reading the thread…": the verb, not the measurement. */
    val reads = Concept("reading", "loading")
    /** The voice that speaks the app's own lines ("The Ledger refused"). */
    val narrator = Concept("the ledger", "the app", themedProper = "the Ledger")

    // Allies
    val ally = Concept("ally", "friend", themedPlural = "allies")
    val circle = Concept("circle", "group")
    val keeper = Concept("keeper", "owner")
    val tidings = Concept("tidings", "feed", themedPlural = "tidings", plainPlural = "feeds")
    val reckoning = Concept("reckoning", "leaderboard")
    val theReckoning = Concept("the reckoning", "leaderboard", themedPlural = "the reckonings", themedProper = "the Reckoning")
    val rung = Concept("rung", "tier")
    val standing = Concept("standing", "rank", themedPlural = "standings")
    val tribute = Concept("tribute", "kudos", plainPlural = "kudos")
    val remark = Concept("remark", "comment")
    val missive = Concept("missive", "message")
    val inbox = Concept("missives", "inbox", themedPlural = "missives", plainPlural = "inboxes")

    private val serverTerms get() = listOf(trueName, folio, rite, cycle, trial, oath, deed, ally, circle, keeper, tribute, remark, missive, lifter)

    // The Veil
    val veil = Concept("veil", "idle rewards", plainPlural = "idle rewards")
    val veilDraw = Concept("veil draw", "idle-rewards draw", themedProper = "Veil draw")
    val theVeil = Concept("the veil", "idle rewards", themedProper = "the Veil")
}
