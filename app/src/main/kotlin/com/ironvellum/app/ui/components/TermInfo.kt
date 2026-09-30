package com.ironvellum.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.ArmyClass
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * A term the app uses without explaining it inline. The definitions are short
 * and plain on purpose: a beginner reads them mid-screen, and anyone who
 * already knows the word never has to open one.
 */
enum class Term(val title: String, val definition: String) {
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
        "Both follow your level, which rises with the XP each trial earns. Strength Rank: " +
            "Untrained, then Novice from level 10, Intermediate from 30, Advanced from 50 and " +
            "Elite from 80. Ascension: " +
            ArmyClass.LADDER.joinToString(", ") { "${it.title} at ${it.level}" } + ".",
    ),
}

/**
 * The small "what's this?" button that opens a [Term]'s definition. Sized to a
 * 44dp touch target around an 18dp icon, the same as the Veil's drop-rate info.
 */
@Composable
fun TermInfo(term: Term, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(44.dp)
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
    if (open) {
        AlertDialog(
            shape = MaterialTheme.shapes.medium,
            containerColor = Color(0xFF0D1110),
            onDismissRequest = { open = false },
            title = {
                Text(
                    term.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.Ink,
                )
            },
            text = {
                Text(
                    term.definition,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.Ink,
                )
            },
            confirmButton = {
                IronvellumButton(label = "Close", onClick = { open = false }, quiet = true)
            },
        )
    }
}
