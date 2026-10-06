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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.ArmyClass
import com.ironvellum.app.domain.Rank
import com.ironvellum.app.domain.Streak
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * A term the app uses without explaining it inline. The definitions are short
 * and plain on purpose: a beginner reads them mid-screen, and anyone who
 * already knows the word never has to open one.
 */
enum class Term(
    val title: String,
    val definition: String,
    /** Word stems that name this term in running copy; empty for terms only shown by hand. */
    val stems: List<String> = emptyList(),
) {
    BMI(
        "BMI",
        "Body mass index: your weight divided by your height squared. It is a rough screen " +
            "for body size and cannot tell muscle from fat, so a muscular person often reads high.",
    ),
    FFMI(
        "FFMI",
        "Fat-free mass index: your weight minus body fat, divided by your height squared. " +
            "It rates how much muscle you carry for your height. It needs a body fat % reading.",
    ),
    NAVY_TAPE(
        "Navy tape method",
        "The US Navy's body fat estimate from a tape measure: neck and waist (plus hips for " +
            "women) against your height. Expect it to be within a few percent, not exact. " +
            "Measure the same way each time and watch the trend.",
    ),
    ONE_REP_MAX(
        "1-rep max",
        "The heaviest load you could lift once with good form. Ironvellum estimates it from " +
            "the sets you log, so you never have to test it.",
    ),
    DOUBLE_PROGRESSION(
        "Double progression",
        "Keep the load and add reps each trial until you reach the top of the rep range, then " +
            "add load and start again at the bottom. Muscle mode trains this way.",
    ),
    DELOAD(
        "Deload",
        "If you fail the same load three trials in a row, the next one drops it by 10% so you " +
            "can recover and build back past where you stalled. It happens on its own.",
    ),
    RANKS(
        "Strength Rank and Ascension",
        "Strength Rank is how strong you are now: Untrained, Novice, Intermediate, Advanced or " +
            "Elite, the strength-standard bands. It reads three patterns, Pull, Push and Legs, " +
            "over the last ${Rank.WINDOW_DAYS} days. Each takes its best mark: a squat, bench press, " +
            "deadlift, overhead press, pull-up or dip scored by estimated 1-rep max against your " +
            "bodyweight, or a pull, push or leg technique you cleared, at its tier. Your rank " +
            "is the average of the patterns you trained, rounded down. One you skipped is left out, " +
            "and core work never lowers it. Tap your rank to see each pattern and what lifts it next. " +
            "Ascension follows your level, which rises with the XP each trial " +
            "earns: " + ArmyClass.LADDER.joinToString(", ") { "${it.title} at ${it.level}" } + ".",
    ),
    TUCK(
        "Tuck",
        "Knees pulled in tight to your chest. Folding the legs shortens the lever, so the " +
            "hold is far easier than with legs straight. It is the first shape of most levers " +
            "and planches.",
        stems = listOf("tuck"),
    ),
    STRADDLE(
        "Straddle",
        "Legs straight and spread wide apart. Spreading them brings their weight closer to " +
            "your body, so it sits between a tuck and the full shape in difficulty.",
        stems = listOf("straddle"),
    ),
    SCAPULA(
        "Scapula",
        "Your shoulder blade. Depression pulls it down away from your ears, retraction " +
            "squeezes it toward your spine, and protraction pushes it forward around your ribs. " +
            "Many techniques start with the shoulder blade moving before the arm bends.",
        stems = listOf("scapul", "protract"),
    ),
    HOLLOW(
        "Hollow body",
        "Lower back pressed flat, ribs down, legs and arms long. The whole body makes one " +
            "shallow curve, like a banana. It is the core shape behind handstands and levers.",
        stems = listOf("hollow"),
    ),
    PIKE(
        "Pike",
        "Bent at the hips with your legs straight, so the body makes an upside-down V.",
        stems = listOf("pike"),
    ),
    FALSE_GRIP(
        "False grip",
        "On rings, the wrist sits on top of the ring instead of hanging below it. It feels " +
            "awkward at first but lets you move from below the rings to above them.",
        stems = listOf("false grip"),
    ),
    NEGATIVE(
        "Negative",
        "Only the lowering half of a rep, done slowly on purpose. The lowering half is the " +
            "eccentric; the lifting half is the concentric. You can lower more than you can " +
            "lift, so negatives build a rep you cannot do yet.",
        stems = listOf("negative", "eccentric", "concentric"),
    ),
    TRIAL(
        "Trial",
        "One time through your training: you begin it, log your sets, then seal it. Only a sealed trial " +
            "counts toward your XP, strength score, oath and deeds.",
    ),
    RITE(
        "Rite",
        "A saved trial to repeat: its movements and the sets set out for each. Begin a trial from a " +
            "rite, and edit it any time without touching the trials you have already sealed.",
    ),
    CYCLE(
        "Cycle",
        "Your week of rites, each set on a day. Sealing the rite that falls on today " +
            "earns a bonus on top of the trial's XP.",
    ),
    OATH(
        "Oath",
        "The days you have kept training without a break. Every sealed trial keeps it, and so does a day of respite: " +
            "it only breaks when more than ${Streak.MAX_GAP_DAYS} days pass without one. " +
            "The count is the days since the chain began, so it grows every morning it lives.",
    ),
    DEED(
        "Deed",
        "A mark of something reached: a lift, an oath held, a total. Some deeds " +
            "grant a title you can wear on your folio.",
    ),
    SEAL(
        "Seal",
        "Finishing a trial. Sealing banks its XP and adds its strength score to your lifetime " +
            "total. A sealed trial can still be amended later, and the difference is settled.",
    ),
    THE_VEIL(
        "The Veil",
        "Where echoes gather essence while you are away. Your recent training sets the pace, " +
            "so seal trials to raise it, then return to collect.",
    ),
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
            .clickable(role = Role.Button, onClickLabel = "Explain") { open = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = "What is ${term.title}?",
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
            .clickable(role = Role.Button, onClickLabel = "Explain ${term.title}") { open = true }
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
        actions = listOf(InfoAction("Close", onDismiss, quiet = true)),
    ) {
        text(null, term.definition)
    }
}
