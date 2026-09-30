package com.ironvellum.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.GearRequirements
import com.ironvellum.app.domain.LiftBoards
import com.ironvellum.app.domain.LiftKind
import com.ironvellum.app.domain.MuscleMap
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.program.ExerciseMuscles
import com.ironvellum.app.ui.theme.IronvellumTracking

/**
 * Facts about one exercise, all read from data the app already holds: the
 * muscle profile, gear table, skill tree and ally boards. Sections without
 * data are left out; the catalogue has no descriptions or cues, so none are
 * shown. [onPick] null hides the confirm button; otherwise it labels itself
 * [confirmLabel] and picks the exercise the way tapping the card does.
 * [modifiers] reshape the muscles as they do in the trial (a deficit
 * push-up works the chest at stretch).
 */
@Composable
internal fun ExerciseInfoDialog(
    exercise: Exercise,
    lastLine: String?,
    onDismiss: () -> Unit,
    onPick: (() -> Unit)?,
    confirmLabel: String = "ADD",
    modifiers: String = "",
) {
    val shares = MuscleMap.profile(exercise.name, modifiers)?.muscles.orEmpty()
    val gear = GearRequirements.needs(exercise.name)
        .joinToString(" or ") { set -> set.joinToString(" + ") { it.label.lowercase() } }
    val skill = Skills.forName(exercise.name)
    val boards = LiftBoards.boardsFor(exercise.name)

    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        containerColor = Color(0xFF0D1110),
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(
                        exercise.muscleGroup.name.lowercase(),
                        metricWord(exercise.metric),
                        if (exercise.isWeighted) "weighted" else "bodyweight",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (shares.isNotEmpty()) {
                    InfoHeading("MUSCLES")
                    // The coverage screen's figure and MAIN / ASSIST lines, so
                    // an exercise reads the same wherever the lifter asks.
                    ExerciseMuscles(shares, Modifier.fillMaxWidth(), figureHeight = 180.dp)
                } else {
                    InfoHeading("MUSCLES")
                    InfoBody("The Ledger holds no muscle data for this exercise yet.", IronvellumColors.InkMuted)
                }
                if (gear.isNotEmpty()) {
                    InfoHeading("ARMOURY")
                    InfoBody(gear.replaceFirstChar { it.uppercase() })
                }
                if (skill != null) {
                    InfoHeading("TECHNIQUE")
                    InfoBody("${Skills.tierLabel(skill.tier)} · ${skill.line}", IronvellumColors.SystemGreen)
                    InfoBody("Claim: ${skill.standard}")
                    if (skill.why.isNotBlank()) InfoBody(skill.why, IronvellumColors.InkMuted)
                    skill.requires?.let { InfoBody("Needs $it first", IronvellumColors.InkMuted) }
                }
                if (boards.isNotEmpty()) {
                    InfoHeading("THE RECKONING")
                    boards.forEach { board ->
                        InfoBody(
                            if (board.lift.kind == LiftKind.LADDER && board.rung != null) {
                                "${board.lift.label} reckoning — rung ${board.rung} of ${board.rungCount}"
                            } else {
                                "Counts toward the ${board.lift.label.lowercase()} reckoning"
                            },
                        )
                    }
                }
                if (lastLine != null) {
                    InfoHeading("LAST TRIAL")
                    InfoBody(lastLine, IronvellumColors.SovereignGold)
                }
            }
        },
        confirmButton = {
            if (onPick != null) {
                IronvellumButton(label = confirmLabel, onClick = onPick, modifier = Modifier.widthIn(min = 96.dp).heightIn(min = 44.dp))
            }
        },
        dismissButton = {
            IronvellumButton(label = "CLOSE", quiet = true, onClick = onDismiss, modifier = Modifier.widthIn(min = 96.dp).heightIn(min = 44.dp))
        },
    )
}

@Composable
private fun InfoHeading(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        color = IronvellumColors.SystemGreen,
        letterSpacing = IronvellumTracking.ScreenTitle,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun InfoBody(text: String, color: Color = IronvellumColors.Ink) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(bottom = 2.dp))
}
