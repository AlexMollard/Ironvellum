package com.ironvellum.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseGuides
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
 * muscle profile, how-to guide, gear table, skill tree and ally boards.
 * Sections without data are left out. [onPick] null hides the confirm button; otherwise it labels itself
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
            Column(Modifier.topFade().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(6.dp))
                ExerciseFacts(exercise.name, modifiers, showMissingMuscles = true)
                if (skill != null) {
                    InfoHeading("TECHNIQUE")
                    InfoBody("${Skills.tierLabel(skill.tier)} · ${skill.line}", IronvellumColors.SystemGreen)
                    InfoBody("Claim: ${skill.standard}")
                    if (skill.why.isNotBlank()) InfoBody(skill.why, IronvellumColors.InkMuted)
                    if (skill.prerequisites.isNotEmpty()) {
                        InfoBody("Needs ${skill.prerequisites.joinToString(" and ")} first", IronvellumColors.InkMuted)
                    }
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

/**
 * The how-to half of an exercise's facts: muscle figure, HOW TO, CUES, COMMON
 * MISTAKES and ARMOURY, each left out when the app holds no data for it.
 * Shared by this dialog and the technique detail so the two always agree.
 * It does not scroll: both callers already sit in a scrolling column.
 * [showMissingMuscles] says so when there is no muscle profile instead of
 * leaving the section out.
 */
@Composable
internal fun ExerciseFacts(name: String, modifiers: String = "", showMissingMuscles: Boolean = false) {
    val shares = remember(name, modifiers) { MuscleMap.profile(name, modifiers)?.muscles.orEmpty() }
    val gear = remember(name) {
        GearRequirements.needs(name)
            .joinToString(" or ") { set -> set.joinToString(" + ") { it.label.lowercase() } }
    }
    val guide = remember(name) { ExerciseGuides.forName(name) }
    if (shares.isNotEmpty()) {
        InfoHeading("MUSCLES")
        // The coverage screen's figure and MAIN / ASSIST lines, so
        // an exercise reads the same wherever the lifter asks.
        ExerciseMuscles(shares, Modifier.fillMaxWidth(), figureHeight = 180.dp)
    } else if (showMissingMuscles) {
        InfoHeading("MUSCLES")
        InfoBody("The Ledger holds no muscle data for this exercise yet.", IronvellumColors.InkMuted)
    }
    if (guide != null) {
        InfoHeading("HOW TO")
        // One level for the whole procedure: the setup and the steps read in
        // Ink, and only the numerals step back.
        InfoBody(guide.setup)
        Spacer(Modifier.height(4.dp))
        guide.steps.forEachIndexed { i, step ->
            Row(Modifier.padding(bottom = 2.dp)) {
                Text(
                    "${i + 1}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.width(22.dp),
                )
                Text(step, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.Ink, modifier = Modifier.weight(1f))
            }
        }
        if (guide.cues.isNotEmpty()) {
            InfoHeading("CUES")
            guide.cues.forEach { InfoBody("• $it") }
        }
        if (guide.commonMistakes.isNotEmpty()) {
            InfoHeading("COMMON MISTAKES")
            guide.commonMistakes.forEach { InfoBody("• $it", IronvellumColors.InkMuted) }
        }
    }
    if (gear.isNotEmpty()) {
        InfoHeading("ARMOURY")
        InfoBody(gear.replaceFirstChar { it.uppercase() })
    }
}

/**
 * Fades the top [height] of a scrolling area to transparent, so content that
 * scrolls under the dialog's header dissolves instead of being cut hard. The
 * first child should start with at least that much space, or it rests faded.
 */
internal fun Modifier.topFade(height: Dp = 14.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = height.toPx()
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = h),
            size = Size(size.width, h),
            blendMode = BlendMode.DstIn,
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
