package com.ironvellum.app.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.ironvellum.app.R
import com.ironvellum.app.domain.StringSource
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.resolve
import com.ironvellum.app.ui.ResourcesStrings
import java.util.ServiceLoader

/**
 * The vocabulary layer: every concept word the app shows (docs/GLOSSARY.md) routes through here, so one switch
 * chooses between the plain words (the default) and the themed names. [themed] is Compose state, so a screen that
 * reads a word recomposes the moment Settings flips it; non-Compose text (notifications, share text) reads the same
 * flag when it is built. Words are always computed at the point of use, never held in a `val` that outlives a flip.
 *
 * An install that existed before this setting keeps the themed names it has always seen; a fresh one starts plain
 * ([defaultThemed]). The choice is decided once and stored, so it never changes behind the lifter's back.
 * User-entered names (a routine called "Full Body A") are data and never pass through here.
 *
 * The words themselves are string resources (`concept_*` and `always_*` in strings.xml, docs/STRINGS.md): a [Concept]
 * is only the list of forms a screen uses and which resource holds each, so a translator writes every form whole.
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
        source = ResourcesStrings(context.applicationContext)
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

    @Volatile private var source: StringSource? = null

    /**
     * Where the words come from: the app's resources once [init] has run. A JVM unit test never runs it, so it falls
     * back to the one [StringSource] the test classpath registers (META-INF/services, `DiskStrings`); in the app that
     * lookup is never reached.
     */
    private val strings: StringSource
        get() = source ?: ServiceLoader.load(StringSource::class.java).firstOrNull()?.also { source = it }
            ?: error("Words.init has not run and no StringSource is registered for tests")

    /** A sentence for the current mode (plain, or themed when the switch is on and the language has themed strings). */
    internal fun text(sentence: UiText): String = sentence.resolve(strings, themed)

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

    /** One form of a concept as two strings: the plain wording and its `__t` themed twin (one string for an `always_` form). */
    class Form(@StringRes val plain: Int, @StringRes val themed: Int)

    private fun f(@StringRes plain: Int, @StringRes themed: Int = plain) = Form(plain, themed)

    /**
     * One concept in both vocabularies, as the forms a screen uses: [s] lower case, [c] capitalised, [t] as the
     * themed name is written mid-sentence (a proper noun) but lower case in plain, [u] upper case, each with a plural
     * twin ([pl], [cpl], [tpl], [upl]); [a], [ca], [at] and [cat] carry the article ("a routine", "an ally"). [toString]
     * is [s]. Each form is its own pair of strings (`concept_<name>_<form>` and `__t`), written out whole rather than
     * derived, because capitals, plurals and articles are not the same rules in every language. A form no screen uses
     * has no strings: asking for it fails loudly (WordsTest lists every form the sources read).
     *
     * [bothModes] is the reward layer: its forms are one `always_` string each, themed whatever the switch says.
     * [themedName] is the themed name, capitalised, that stays themed in both modes (a deed called "Three-Day Oath").
     */
    class Concept(
        private val name: String,
        vararg forms: Pair<String, Form>,
        private val bothModes: Boolean = false,
    ) {
        private val forms = forms.toMap()

        private fun formOf(form: String): Form =
            forms[form] ?: error("Words.$name has no `$form` form: add its strings to strings.xml and the form to its Concept in Words.kt")

        private fun text(form: String): String {
            val f = formOf(form)
            return if (bothModes) Words.strings.string(f.themed, emptyList())
            else Words.text(UiText.Themed(UiText.Res(f.plain), UiText.Res(f.themed)))
        }

        /** The plain wording whatever the switch says, for matching and replacing words in text the server wrote. */
        internal fun plainText(form: String): String = Words.strings.string(formOf(form).plain, emptyList())

        val themedName: String get() = Words.strings.string(formOf("n").themed, emptyList())

        val s: String get() = text("s")
        val pl: String get() = text("pl")
        val c: String get() = text("c")
        val cpl: String get() = text("cpl")
        val t: String get() = text("t")
        val tpl: String get() = text("tpl")
        val u: String get() = text("u")
        val upl: String get() = text("upl")
        val a: String get() = text("a")
        val ca: String get() = text("ca")
        val at: String get() = text("at")
        val cat: String get() = text("cat")

        override fun toString(): String = s
    }

    /** A word the server writes in its themed English ([themed], [themedPl]), and the concept whose plain word replaces it. */
    private class ServerTerm(val themed: String, val themedPl: String, val concept: Concept)

    /**
     * A sentence the server wrote ("That Ironbound is not in your circle") shown in the current vocabulary. The
     * migrations raise their messages in the themed English words, so plain mode swaps them for the plain word of the
     * concept (from its resources); themed mode shows them as is. The server's wording is English data, not copy.
     */
    fun fromServer(text: String): String =
        if (themed) text else serverTerms.fold(text) { acc, term -> swapToPlain(acc, term) }

    private fun swapToPlain(text: String, term: ServerTerm): String {
        var out = text
        val plain = term.concept.plainText("s")
        val plainPl = term.concept.plainText("pl")
        val pairs = if (term.themedPl == term.themed) listOf(term.themed to plain) else listOf(term.themedPl to plainPl, term.themed to plain)
        for ((from, to) in pairs) {
            if (from == to) continue
            out = Regex("(?i)(?<!\\w)" + Regex.escape(from) + "(?!\\w)").replace(out) { m ->
                val word = m.value
                when {
                    word.length > 1 && word == word.uppercase() -> to.uppercase()
                    word[0].isUpperCase() && startsSentence(out, m.range.first) -> cap(to)
                    else -> to
                }
            }
        }
        return out
    }

    /**
     * The profile name to show. A fresh profile is stored as "Ironbound" whatever the mode (it is a sentinel the
     * sign-up and cloud code match), so the default reads as the current word for a lifter; a typed name never changes.
     */
    fun isDefaultName(stored: String): Boolean =
        stored.equals("Ironbound", ignoreCase = true) || stored.equals("Lifter", ignoreCase = true)

    fun displayName(stored: String): String =
        if (isDefaultName(stored)) lifter.c else stored

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

    private fun cap(text: String): String = text.replaceFirstChar { it.uppercase() }

    // You
    val lifter = Concept(
        "lifter",
        "s" to f(R.string.concept_lifter_s, R.string.concept_lifter_s__t),
        "c" to f(R.string.concept_lifter_c, R.string.concept_lifter_c__t),
        "t" to f(R.string.concept_lifter_t, R.string.concept_lifter_t__t),
        "u" to f(R.string.concept_lifter_u, R.string.concept_lifter_u__t),
        "pl" to f(R.string.concept_lifter_pl, R.string.concept_lifter_pl__t),
        "at" to f(R.string.concept_lifter_at, R.string.concept_lifter_at__t),
        "cat" to f(R.string.concept_lifter_cat, R.string.concept_lifter_cat__t),
    )
    val trueName = Concept(
        "trueName",
        "s" to f(R.string.concept_true_name_s, R.string.concept_true_name_s__t),
        "c" to f(R.string.concept_true_name_c, R.string.concept_true_name_c__t),
        "pl" to f(R.string.concept_true_name_pl, R.string.concept_true_name_pl__t),
        "a" to f(R.string.concept_true_name_a, R.string.concept_true_name_a__t),
    )
    val folio = Concept(
        "folio",
        "s" to f(R.string.concept_folio_s, R.string.concept_folio_s__t),
        "c" to f(R.string.concept_folio_c, R.string.concept_folio_c__t),
        "pl" to f(R.string.concept_folio_pl, R.string.concept_folio_pl__t),
    )
    val frame = Concept(
        "frame",
    )

    // Training
    val rite = Concept(
        "rite",
        "s" to f(R.string.concept_rite_s, R.string.concept_rite_s__t),
        "c" to f(R.string.concept_rite_c, R.string.concept_rite_c__t),
        "t" to f(R.string.concept_rite_t, R.string.concept_rite_t__t),
        "pl" to f(R.string.concept_rite_pl, R.string.concept_rite_pl__t),
        "cpl" to f(R.string.concept_rite_cpl, R.string.concept_rite_cpl__t),
        "upl" to f(R.string.concept_rite_upl, R.string.concept_rite_upl__t),
        "a" to f(R.string.concept_rite_a, R.string.concept_rite_a__t),
        "ca" to f(R.string.concept_rite_ca, R.string.concept_rite_ca__t),
    )
    val cycle = Concept(
        "cycle",
        "s" to f(R.string.concept_cycle_s, R.string.concept_cycle_s__t),
        "c" to f(R.string.concept_cycle_c, R.string.concept_cycle_c__t),
        "t" to f(R.string.concept_cycle_t, R.string.concept_cycle_t__t),
        "u" to f(R.string.concept_cycle_u, R.string.concept_cycle_u__t),
        "pl" to f(R.string.concept_cycle_pl, R.string.concept_cycle_pl__t),
        "cpl" to f(R.string.concept_cycle_cpl, R.string.concept_cycle_cpl__t),
        "a" to f(R.string.concept_cycle_a, R.string.concept_cycle_a__t),
    )
    val trial = Concept(
        "trial",
        "s" to f(R.string.concept_trial_s, R.string.concept_trial_s__t),
        "c" to f(R.string.concept_trial_c, R.string.concept_trial_c__t),
        "t" to f(R.string.concept_trial_t, R.string.concept_trial_t__t),
        "u" to f(R.string.concept_trial_u, R.string.concept_trial_u__t),
        "pl" to f(R.string.concept_trial_pl, R.string.concept_trial_pl__t),
        "cpl" to f(R.string.concept_trial_cpl, R.string.concept_trial_cpl__t),
        "upl" to f(R.string.concept_trial_upl, R.string.concept_trial_upl__t),
        "a" to f(R.string.concept_trial_a, R.string.concept_trial_a__t),
        "ca" to f(R.string.concept_trial_ca, R.string.concept_trial_ca__t),
        "n" to f(R.string.always_trial_name),
    )
    val openTrial = Concept(
        "openTrial",
        "s" to f(R.string.concept_open_trial_s, R.string.concept_open_trial_s__t),
        "c" to f(R.string.concept_open_trial_c, R.string.concept_open_trial_c__t),
        "a" to f(R.string.concept_open_trial_a, R.string.concept_open_trial_a__t),
    )
    val seal = Concept(
        "seal",
        "s" to f(R.string.concept_seal_s, R.string.concept_seal_s__t),
        "c" to f(R.string.concept_seal_c, R.string.concept_seal_c__t),
        "t" to f(R.string.concept_seal_t, R.string.concept_seal_t__t),
    )
    val seals = Concept(
        "seals",
    )
    val sealed = Concept(
        "sealed",
        "s" to f(R.string.concept_sealed_s, R.string.concept_sealed_s__t),
        "c" to f(R.string.concept_sealed_c, R.string.concept_sealed_c__t),
        "u" to f(R.string.concept_sealed_u, R.string.concept_sealed_u__t),
        "a" to f(R.string.concept_sealed_a, R.string.concept_sealed_a__t),
        "ca" to f(R.string.concept_sealed_ca, R.string.concept_sealed_ca__t),
    )
    val sealing = Concept(
        "sealing",
        "s" to f(R.string.concept_sealing_s, R.string.concept_sealing_s__t),
        "c" to f(R.string.concept_sealing_c, R.string.concept_sealing_c__t),
    )
    val unsealed = Concept(
        "unsealed",
        "s" to f(R.string.concept_unsealed_s, R.string.concept_unsealed_s__t),
        "c" to f(R.string.concept_unsealed_c, R.string.concept_unsealed_c__t),
    )
    val chronicle = Concept(
        "chronicle",
        "s" to f(R.string.concept_chronicle_s, R.string.concept_chronicle_s__t),
        "c" to f(R.string.concept_chronicle_c, R.string.concept_chronicle_c__t),
        "t" to f(R.string.concept_chronicle_t, R.string.concept_chronicle_t__t),
    )
    val theChronicle = Concept(
        "theChronicle",
        "c" to f(R.string.concept_the_chronicle_c, R.string.concept_the_chronicle_c__t),
        "t" to f(R.string.concept_the_chronicle_t, R.string.concept_the_chronicle_t__t),
    )
    val yours = Concept(
        "yours",
        "s" to f(R.string.concept_yours_s, R.string.concept_yours_s__t),
        "c" to f(R.string.concept_yours_c, R.string.concept_yours_c__t),
        "u" to f(R.string.concept_yours_u, R.string.concept_yours_u__t),
    )
    val peak = Concept(
        "peak",
        "s" to f(R.string.concept_peak_s, R.string.concept_peak_s__t),
        "c" to f(R.string.concept_peak_c, R.string.concept_peak_c__t),
        "u" to f(R.string.concept_peak_u, R.string.concept_peak_u__t),
        "pl" to f(R.string.concept_peak_pl, R.string.concept_peak_pl__t),
        "cpl" to f(R.string.concept_peak_cpl, R.string.concept_peak_cpl__t),
        "upl" to f(R.string.concept_peak_upl, R.string.concept_peak_upl__t),
        "a" to f(R.string.concept_peak_a, R.string.concept_peak_a__t),
    )
    val oath = Concept(
        "oath",
        "s" to f(R.string.concept_oath_s, R.string.concept_oath_s__t),
        "c" to f(R.string.concept_oath_c, R.string.concept_oath_c__t),
        "t" to f(R.string.concept_oath_t, R.string.concept_oath_t__t),
        "pl" to f(R.string.concept_oath_pl, R.string.concept_oath_pl__t),
        "a" to f(R.string.concept_oath_a, R.string.concept_oath_a__t),
        "n" to f(R.string.always_oath_name),
    )
    val respite = Concept(
        "respite",
        "s" to f(R.string.concept_respite_s, R.string.concept_respite_s__t),
        "c" to f(R.string.concept_respite_c, R.string.concept_respite_c__t),
    )
    val summons = Concept(
        "summons",
        "s" to f(R.string.concept_summons_s, R.string.concept_summons_s__t),
    )
    val theSummons = Concept(
        "theSummons",
        "c" to f(R.string.concept_the_summons_c, R.string.concept_the_summons_c__t),
        "u" to f(R.string.concept_the_summons_u, R.string.concept_the_summons_u__t),
    )
    val theBinding = Concept(
        "theBinding",
    )
    val pattern = Concept(
        "pattern",
        "s" to f(R.string.concept_pattern_s, R.string.concept_pattern_s__t),
        "cpl" to f(R.string.concept_pattern_cpl, R.string.concept_pattern_cpl__t),
        "a" to f(R.string.concept_pattern_a, R.string.concept_pattern_a__t),
    )
    val forge = Concept(
        "forge",
        "s" to f(R.string.concept_forge_s, R.string.concept_forge_s__t),
        "c" to f(R.string.concept_forge_c, R.string.concept_forge_c__t),
    )
    val forged = Concept(
        "forged",
        "s" to f(R.string.concept_forged_s, R.string.concept_forged_s__t),
    )
    val theForge = Concept(
        "theForge",
        "c" to f(R.string.concept_the_forge_c, R.string.concept_the_forge_c__t),
        "t" to f(R.string.concept_the_forge_t, R.string.concept_the_forge_t__t),
    )
    val temper = Concept(
        "temper",
        "s" to f(R.string.concept_temper_s, R.string.concept_temper_s__t),
        "c" to f(R.string.concept_temper_c, R.string.concept_temper_c__t),
    )
    val armoury = Concept(
        "armoury",
        "s" to f(R.string.concept_armoury_s, R.string.concept_armoury_s__t),
        "c" to f(R.string.concept_armoury_c, R.string.concept_armoury_c__t),
        "u" to f(R.string.concept_armoury_u, R.string.concept_armoury_u__t),
    )

    // Codex
    val codex = Concept(
        "codex",
        "c" to f(R.string.concept_codex_c, R.string.concept_codex_c__t),
        "u" to f(R.string.concept_codex_u, R.string.concept_codex_u__t),
    )
    /** Reward layer: themed in both modes. */
    val deed = Concept(
        "deed",
        "s" to f(R.string.always_deed_s),
        "c" to f(R.string.always_deed_c),
        "pl" to f(R.string.always_deed_pl),
        "cpl" to f(R.string.always_deed_cpl),
        "upl" to f(R.string.always_deed_upl),
        "a" to f(R.string.always_deed_a),
        bothModes = true,
    )
    val path = Concept(
        "path",
        "s" to f(R.string.concept_path_s, R.string.concept_path_s__t),
        "pl" to f(R.string.concept_path_pl, R.string.concept_path_pl__t),
        "a" to f(R.string.concept_path_a, R.string.concept_path_a__t),
    )
    /** The section that lists the paths (a tab label, a button). */
    val paths = Concept(
        "paths",
        "c" to f(R.string.concept_paths_c, R.string.concept_paths_c__t),
        "u" to f(R.string.concept_paths_u, R.string.concept_paths_u__t),
    )
    val technique = Concept(
        "technique",
        "s" to f(R.string.concept_technique_s, R.string.concept_technique_s__t),
        "c" to f(R.string.concept_technique_c, R.string.concept_technique_c__t),
        "t" to f(R.string.concept_technique_t, R.string.concept_technique_t__t),
        "u" to f(R.string.concept_technique_u, R.string.concept_technique_u__t),
        "pl" to f(R.string.concept_technique_pl, R.string.concept_technique_pl__t),
        "cpl" to f(R.string.concept_technique_cpl, R.string.concept_technique_cpl__t),
        "a" to f(R.string.concept_technique_a, R.string.concept_technique_a__t),
        "n" to f(R.string.always_technique_name),
    )
    val claim = Concept(
        "claim",
        "s" to f(R.string.concept_claim_s, R.string.concept_claim_s__t),
        "c" to f(R.string.concept_claim_c, R.string.concept_claim_c__t),
        "u" to f(R.string.concept_claim_u, R.string.concept_claim_u__t),
    )
    val claiming = Concept(
        "claiming",
    )
    val claimed = Concept(
        "claimed",
        "s" to f(R.string.concept_claimed_s, R.string.concept_claimed_s__t),
        "c" to f(R.string.concept_claimed_c, R.string.concept_claimed_c__t),
        "u" to f(R.string.concept_claimed_u, R.string.concept_claimed_u__t),
    )
    val journal = Concept(
        "journal",
        "c" to f(R.string.concept_journal_c, R.string.concept_journal_c__t),
        "t" to f(R.string.concept_journal_t, R.string.concept_journal_t__t),
        "u" to f(R.string.concept_journal_u, R.string.concept_journal_u__t),
    )

    // Ledger
    val ledger = Concept(
        "ledger",
        "c" to f(R.string.concept_ledger_c, R.string.concept_ledger_c__t),
        "t" to f(R.string.concept_ledger_t, R.string.concept_ledger_t__t),
    )
    val reading = Concept(
        "reading",
        "s" to f(R.string.concept_reading_s, R.string.concept_reading_s__t),
        "c" to f(R.string.concept_reading_c, R.string.concept_reading_c__t),
        "pl" to f(R.string.concept_reading_pl, R.string.concept_reading_pl__t),
        "cpl" to f(R.string.concept_reading_cpl, R.string.concept_reading_cpl__t),
        "upl" to f(R.string.concept_reading_upl, R.string.concept_reading_upl__t),
    )
    /** "Reading the thread…": the verb, not the measurement. */
    val reads = Concept(
        "reads",
        "c" to f(R.string.concept_reads_c, R.string.concept_reads_c__t),
    )
    /** The voice that speaks the app's own lines ("The Ledger refused"). */
    val narrator = Concept(
        "narrator",
    )

    // Allies
    val ally = Concept(
        "ally",
        "s" to f(R.string.concept_ally_s, R.string.concept_ally_s__t),
        "c" to f(R.string.concept_ally_c, R.string.concept_ally_c__t),
        "pl" to f(R.string.concept_ally_pl, R.string.concept_ally_pl__t),
        "cpl" to f(R.string.concept_ally_cpl, R.string.concept_ally_cpl__t),
        "tpl" to f(R.string.concept_ally_tpl, R.string.concept_ally_tpl__t),
        "upl" to f(R.string.concept_ally_upl, R.string.concept_ally_upl__t),
        "a" to f(R.string.concept_ally_a, R.string.concept_ally_a__t),
    )
    val circle = Concept(
        "circle",
        "s" to f(R.string.concept_circle_s, R.string.concept_circle_s__t),
        "c" to f(R.string.concept_circle_c, R.string.concept_circle_c__t),
        "pl" to f(R.string.concept_circle_pl, R.string.concept_circle_pl__t),
        "cpl" to f(R.string.concept_circle_cpl, R.string.concept_circle_cpl__t),
        "a" to f(R.string.concept_circle_a, R.string.concept_circle_a__t),
        "ca" to f(R.string.concept_circle_ca, R.string.concept_circle_ca__t),
    )
    val keeper = Concept(
        "keeper",
        "s" to f(R.string.concept_keeper_s, R.string.concept_keeper_s__t),
        "c" to f(R.string.concept_keeper_c, R.string.concept_keeper_c__t),
        "t" to f(R.string.concept_keeper_t, R.string.concept_keeper_t__t),
        "pl" to f(R.string.concept_keeper_pl, R.string.concept_keeper_pl__t),
    )
    val tidings = Concept(
        "tidings",
        "s" to f(R.string.concept_tidings_s, R.string.concept_tidings_s__t),
        "c" to f(R.string.concept_tidings_c, R.string.concept_tidings_c__t),
        "t" to f(R.string.concept_tidings_t, R.string.concept_tidings_t__t),
    )
    val reckoning = Concept(
        "reckoning",
        "s" to f(R.string.concept_reckoning_s, R.string.concept_reckoning_s__t),
        "c" to f(R.string.concept_reckoning_c, R.string.concept_reckoning_c__t),
        "pl" to f(R.string.concept_reckoning_pl, R.string.concept_reckoning_pl__t),
        "a" to f(R.string.concept_reckoning_a, R.string.concept_reckoning_a__t),
    )
    val theReckoning = Concept(
        "theReckoning",
        "s" to f(R.string.concept_the_reckoning_s, R.string.concept_the_reckoning_s__t),
        "t" to f(R.string.concept_the_reckoning_t, R.string.concept_the_reckoning_t__t),
        "u" to f(R.string.concept_the_reckoning_u, R.string.concept_the_reckoning_u__t),
    )
    val rung = Concept(
        "rung",
        "s" to f(R.string.concept_rung_s, R.string.concept_rung_s__t),
        "pl" to f(R.string.concept_rung_pl, R.string.concept_rung_pl__t),
        "cpl" to f(R.string.concept_rung_cpl, R.string.concept_rung_cpl__t),
    )
    val standing = Concept(
        "standing",
        "s" to f(R.string.concept_standing_s, R.string.concept_standing_s__t),
        "c" to f(R.string.concept_standing_c, R.string.concept_standing_c__t),
    )
    val tribute = Concept(
        "tribute",
        "s" to f(R.string.concept_tribute_s, R.string.concept_tribute_s__t),
        "c" to f(R.string.concept_tribute_c, R.string.concept_tribute_c__t),
        "u" to f(R.string.concept_tribute_u, R.string.concept_tribute_u__t),
        "pl" to f(R.string.concept_tribute_pl, R.string.concept_tribute_pl__t),
        "upl" to f(R.string.concept_tribute_upl, R.string.concept_tribute_upl__t),
    )
    val remark = Concept(
        "remark",
        "s" to f(R.string.concept_remark_s, R.string.concept_remark_s__t),
        "c" to f(R.string.concept_remark_c, R.string.concept_remark_c__t),
        "pl" to f(R.string.concept_remark_pl, R.string.concept_remark_pl__t),
        "cpl" to f(R.string.concept_remark_cpl, R.string.concept_remark_cpl__t),
        "upl" to f(R.string.concept_remark_upl, R.string.concept_remark_upl__t),
        "a" to f(R.string.concept_remark_a, R.string.concept_remark_a__t),
    )
    val missive = Concept(
        "missive",
        "s" to f(R.string.concept_missive_s, R.string.concept_missive_s__t),
        "pl" to f(R.string.concept_missive_pl, R.string.concept_missive_pl__t),
        "cpl" to f(R.string.concept_missive_cpl, R.string.concept_missive_cpl__t),
        "tpl" to f(R.string.concept_missive_tpl, R.string.concept_missive_tpl__t),
        "ca" to f(R.string.concept_missive_ca, R.string.concept_missive_ca__t),
    )
    val inbox = Concept(
        "inbox",
        "c" to f(R.string.concept_inbox_c, R.string.concept_inbox_c__t),
    )

    private val serverTerms get() = listOf(
        ServerTerm("true name", "true names", trueName),
        ServerTerm("folio", "folios", folio),
        ServerTerm("rite", "rites", rite),
        ServerTerm("cycle", "cycles", cycle),
        ServerTerm("trial", "trials", trial),
        ServerTerm("oath", "oaths", oath),
        ServerTerm("ally", "allies", ally),
        ServerTerm("circle", "circles", circle),
        ServerTerm("keeper", "keepers", keeper),
        ServerTerm("tribute", "tributes", tribute),
        ServerTerm("remark", "remarks", remark),
        ServerTerm("missive", "missives", missive),
        ServerTerm("ironbound", "ironbound", lifter),
    )

    // The Veil
    val veil = Concept(
        "veil",
        "c" to f(R.string.always_veil_c),
        "u" to f(R.string.always_veil_u),
        bothModes = true,
    )
    val veilDraw = Concept(
        "veilDraw",
        "c" to f(R.string.always_veil_draw_c),
        "at" to f(R.string.always_veil_draw_at),
        bothModes = true,
    )
    val theVeil = Concept(
        "theVeil",
        "c" to f(R.string.always_the_veil_c),
        "t" to f(R.string.always_the_veil_t),
        bothModes = true,
    )
}
