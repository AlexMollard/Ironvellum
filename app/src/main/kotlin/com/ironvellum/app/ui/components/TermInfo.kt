package com.ironvellum.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.ArmyClass
import com.ironvellum.app.domain.Rank
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.domain.Streak
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * A term the app uses without explaining it inline. The definitions are short
 * and plain on purpose: a beginner reads them mid-screen, and anyone who
 * already knows the word never has to open one.
 */
enum class Term private constructor(
    private val titleText: () -> String,
    private val definitionText: () -> String,
    /** Word stems that name this term in running copy; empty for terms only shown by hand. */
    val stems: List<String> = emptyList(),
) {
    BMI(
        { uiString(R.string.terminfo_bmi_title) },
        { uiString(R.string.terminfo_bmi_definition) },
    ),
    FFMI(
        { uiString(R.string.terminfo_ffmi_title) },
        { uiPair(R.string.terminfo_ffmi_definition, R.string.terminfo_ffmi_definition__t, "%") },
    ),
    NAVY_TAPE(
        { uiString(R.string.terminfo_navy_tape_title) },
        { uiString(R.string.terminfo_navy_tape_definition) },
    ),
    ONE_REP_MAX(
        { uiString(R.string.terminfo_one_rep_max_title) },
        { uiString(R.string.terminfo_one_rep_max_definition) },
    ),
    DOUBLE_PROGRESSION(
        { uiString(R.string.terminfo_double_progression_title) },
        { uiPair(R.string.terminfo_double_progression_definition, R.string.terminfo_double_progression_definition__t) },
    ),
    DELOAD(
        { uiString(R.string.terminfo_deload_title) },
        { uiPair(R.string.terminfo_deload_definition, R.string.terminfo_deload_definition__t, 10) },
    ),
    RANKS(
        { uiString(R.string.terminfo_ranks_title) },
        {
            Words.text(
                UiText.themed(
                    R.string.terminfo_ranks_definition,
                    R.string.terminfo_ranks_definition__t,
                    UiText.plural(R.plurals.terminfo_ranks_window, Rank.WINDOW_DAYS),
                    ArmyClass.LADDER.joinToString(", ") { uiString(R.string.terminfo_ranks_ladder_item, it.title, it.level) },
                ),
            )
        },
    ),
    TUCK(
        { uiString(R.string.terminfo_tuck_title) },
        { uiString(R.string.terminfo_tuck_definition) },
        stems = listOf("tuck"),
    ),
    STRADDLE(
        { uiString(R.string.terminfo_straddle_title) },
        { uiString(R.string.terminfo_straddle_definition) },
        stems = listOf("straddle"),
    ),
    SCAPULA(
        { uiString(R.string.terminfo_scapula_title) },
        { uiPair(R.string.terminfo_scapula_definition, R.string.terminfo_scapula_definition__t) },
        stems = listOf("scapul", "protract"),
    ),
    HOLLOW(
        { uiString(R.string.terminfo_hollow_title) },
        { uiString(R.string.terminfo_hollow_definition) },
        stems = listOf("hollow"),
    ),
    PIKE(
        { uiString(R.string.terminfo_pike_title) },
        { uiString(R.string.terminfo_pike_definition) },
        stems = listOf("pike"),
    ),
    FALSE_GRIP(
        { uiString(R.string.terminfo_false_grip_title) },
        { uiString(R.string.terminfo_false_grip_definition) },
        stems = listOf("false grip"),
    ),
    NEGATIVE(
        { uiString(R.string.terminfo_negative_title) },
        { uiString(R.string.terminfo_negative_definition) },
        stems = listOf("negative", "eccentric", "concentric"),
    ),
    TRIAL(
        { Words.trial.c },
        { uiPair(R.string.terminfo_trial_definition, R.string.terminfo_trial_definition__t) },
    ),
    RITE(
        { Words.rite.c },
        { uiPair(R.string.terminfo_rite_definition, R.string.terminfo_rite_definition__t) },
    ),
    CYCLE(
        { Words.cycle.c },
        { uiPair(R.string.terminfo_cycle_definition, R.string.terminfo_cycle_definition__t) },
    ),
    LEVEL(
        { uiString(R.string.terminfo_level_title) },
        { uiPair(R.string.terminfo_level_definition, R.string.terminfo_level_definition__t) },
    ),
    OATH(
        { Words.oath.c },
        { Words.text(UiText.themed(R.string.terminfo_oath_definition, R.string.terminfo_oath_definition__t, UiText.plural(R.plurals.terminfo_oath_gap, Streak.MAX_GAP_DAYS.toInt(), Streak.MAX_GAP_DAYS))) },
    ),
    SEAL(
        { Words.seal.c },
        { uiPair(R.string.terminfo_seal_definition, R.string.terminfo_seal_definition__t) },
    ),
    SET_TYPES(
        { uiString(R.string.terminfo_set_types_title) },
        {
            uiPair(
                R.string.terminfo_set_types_definition,
                R.string.terminfo_set_types_definition__t,
                SetKind.entries.joinToString(" ") { "${it.label}: ${it.blurb}" },
            )
        },
    ),
    THE_VEIL(
        { Words.theVeil.c },
        { uiPair(R.string.terminfo_veil_definition, R.string.terminfo_veil_definition__t) },
    ),
    ;

    val title: String get() = titleText()
    val definition: String get() = definitionText()
}

/** Every [Term] named in [text], in declaration order, each once. */
fun termsIn(text: String): List<Term> = Term.entries.filter { term ->
    term.stems.any { stem -> Regex("""(?i)(?<![\w])${Regex.escape(stem)}""").containsMatchIn(text) }
}

/**
 * The small "what's this?" button that opens a [Term]'s definition. Sized to a
 * 48dp touch target around an 18dp icon.
 */
@Composable
fun TermInfo(term: Term, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.terminfo_explain_label)) { open = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = stringResource(R.string.terminfo_what_is_description, term.title),
            tint = IronvellumColors.InkMuted,
            modifier = Modifier.size(18.dp),
        )
    }
    if (open) TermDialog(term) { open = false }
}

/**
 * The term written out with its info mark, for a word that appears in copy
 * the explainer cannot sit inside. Tapping it opens the same definition.
 */
@Composable
fun TermChip(term: Term, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.terminfo_explain_term_label, term.title)) { open = true }
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = IronvellumColors.SystemGreen,
            modifier = Modifier.size(16.dp),
        )
        Text(
            term.title,
            style = MaterialTheme.typography.labelMedium,
            color = IronvellumColors.SystemGreen,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
    if (open) TermDialog(term) { open = false }
}

@Composable
internal fun TermDialog(term: Term, onDismiss: () -> Unit) {
    InfoSheet(
        title = term.title,
        onDismiss = onDismiss,
        size = InfoSheetSize.Compact,
        actions = listOf(InfoAction(stringResource(R.string.terminfo_close_button), onDismiss, quiet = true)),
    ) {
        text(null, term.definition)
    }
}
