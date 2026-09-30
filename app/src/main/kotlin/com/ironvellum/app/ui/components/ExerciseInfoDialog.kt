package com.ironvellum.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.ironvellum.app.ui.theme.IronvellumTracking

/** Most muscles the dialog lists; the tail of a long profile is 0.3-0.5 assistance. */
private const val MAX_MUSCLE_ROWS = 8

/**
 * Facts about one exercise, all read from data the app already holds: the
 * muscle profile, gear table, skill tree and ally boards. Sections without
 * data are left out; the catalogue has no descriptions or cues, so none are
 * shown. [onPick] null hides the confirm button; otherwise it labels itself
 * [confirmLabel] and picks the exercise the way tapping the card does.
 */
@Composable
internal fun ExerciseInfoDialog(
    exercise: Exercise,
    lastLine: String?,
    onDismiss: () -> Unit,
    onPick: (() -> Unit)?,
    confirmLabel: String = "ADD",
) {
    val muscles = MuscleMap.profile(exercise.name)?.muscles.orEmpty().entries
        .filter { it.value > 0.0 }
        .sortedByDescending { it.value }
        .take(MAX_MUSCLE_ROWS)
    val topShare = muscles.firstOrNull()?.value ?: 1.0
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
                if (muscles.isNotEmpty()) {
                    InfoHeading("MUSCLES")
                    muscles.forEach { (muscle, share) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                muscle.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = IronvellumColors.Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(112.dp),
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .background(IronvellumColors.Rune, MaterialTheme.shapes.extraSmall),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth((share / topShare).toFloat().coerceIn(0.05f, 1f))
                                        .height(6.dp)
                                        .background(IronvellumColors.SystemGreen, MaterialTheme.shapes.extraSmall),
                                )
                            }
                        }
                    }
                }
                if (gear.isNotEmpty()) {
                    InfoHeading("GEAR")
                    InfoBody(gear.replaceFirstChar { it.uppercase() })
                }
                if (skill != null) {
                    InfoHeading("SKILL")
                    InfoBody("${Skills.tierLabel(skill.tier)} · ${skill.line}", IronvellumColors.SystemGreen)
                    InfoBody("Claim: ${skill.standard}")
                    if (skill.why.isNotBlank()) InfoBody(skill.why, IronvellumColors.InkMuted)
                    skill.requires?.let { InfoBody("Needs $it first", IronvellumColors.InkMuted) }
                }
                if (boards.isNotEmpty()) {
                    InfoHeading("STRENGTH BOARD")
                    boards.forEach { board ->
                        val detail = if (board.lift.kind == LiftKind.LADDER && board.rung != null) {
                            " · rung ${board.rung} of ${board.rungCount}"
                        } else {
                            ""
                        }
                        InfoBody("${board.lift.label}$detail")
                    }
                }
                if (lastLine != null) {
                    InfoHeading("LAST LOGGED")
                    InfoBody(lastLine, IronvellumColors.SovereignGold)
                }
            }
        },
        confirmButton = {
            if (onPick != null) {
                IronvellumButton(label = confirmLabel, onClick = onPick, modifier = Modifier.width(96.dp).height(44.dp))
            }
        },
        dismissButton = {
            IronvellumButton(label = "CLOSE", quiet = true, onClick = onDismiss, modifier = Modifier.width(96.dp).height(44.dp))
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
