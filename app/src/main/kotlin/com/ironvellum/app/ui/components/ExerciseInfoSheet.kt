package com.ironvellum.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseGuides
import com.ironvellum.app.domain.GearRequirements
import com.ironvellum.app.domain.LiftBoards
import com.ironvellum.app.domain.LiftKind
import com.ironvellum.app.domain.MuscleMap
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.ui.program.ExerciseMuscles
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * Facts about one exercise, all read from data the app already holds: the
 * muscle profile, how-to guide, gear table, skill tree and ally boards.
 * They sit on swipeable pages (OVERVIEW, HOW TO, CUES, MISTAKES) so none of it is a
 * long scroll; a page without data is left out. [onPick] null hides the confirm button; otherwise it labels itself
 * [confirmLabel] and picks the exercise the way tapping the card does.
 * [modifiers] reshape the muscles as they do in the trial (a deficit
 * push-up works the chest at stretch).
 */
@Composable
internal fun ExerciseInfoSheet(
    exercise: Exercise,
    lastLine: String?,
    onDismiss: () -> Unit,
    onPick: (() -> Unit)?,
    confirmLabel: String = "ADD",
    modifiers: String = "",
) {
    val skill = Skills.forName(exercise.name)
    val boards = LiftBoards.boardsFor(exercise.name)
    val chips = buildList {
        add(InfoChip(exercise.muscleGroup.name.lowercase().replaceFirstChar { it.uppercase() }))
        add(InfoChip(metricWord(exercise.metric).replaceFirstChar { it.uppercase() }))
        add(InfoChip(if (exercise.isWeighted) "Weighted" else "Bodyweight"))
        if (skill != null) add(InfoChip("Tier ${Skills.tierLabel(skill.tier)}", IronvellumColors.SystemGreen))
    }

    InfoSheet(
        title = exercise.name,
        onDismiss = onDismiss,
        chips = chips,
        actions = buildList {
            add(InfoAction("CLOSE", onDismiss, quiet = true))
            if (onPick != null) add(InfoAction(confirmLabel, onPick))
        },
        // Four tabs at most, so they fit a 360dp phone: the gear rides under HOW TO and the
        // technique under OVERVIEW. The sheet drops a page with nothing on it.
        pages = listOf(
            InfoPage("OVERVIEW") {
                if (lastLine != null) text("LAST TRIAL", lastLine, IronvellumColors.SovereignGold)
                muscleFacts(exercise.name, modifiers, showMissingMuscles = true)
                if (skill != null) {
                    section("TECHNIQUE") {
                        TechniqueLine("${skill.line} · claim: ${skill.standard}", IronvellumColors.Ink)
                        if (skill.why.isNotBlank()) TechniqueLine(skill.why, IronvellumColors.InkMuted)
                        if (skill.prerequisites.isNotEmpty()) {
                            TechniqueLine("Needs ${skill.prerequisites.joinToString(" and ")} first", IronvellumColors.InkMuted)
                        }
                    }
                }
                if (boards.isNotEmpty()) {
                    bullets(
                        "THE RECKONING",
                        boards.map { board ->
                            if (board.lift.kind == LiftKind.LADDER && board.rung != null) {
                                "${board.lift.label} reckoning — rung ${board.rung} of ${board.rungCount}"
                            } else {
                                "Counts toward the ${board.lift.label.lowercase()} reckoning"
                            }
                        },
                    )
                }
            },
            InfoPage("HOW TO") {
                howToFacts(exercise.name)
                gearFacts(exercise.name)
            },
            InfoPage("CUES") { cueFacts(exercise.name) },
            InfoPage("MISTAKES") { mistakeFacts(exercise.name) },
        ),
    )
}

@Composable
private fun TechniqueLine(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    )
}

/**
 * The how-to half of an exercise's facts as sheet sections: the muscle figure,
 * HOW TO, CUES, COMMON MISTAKES and ARMOURY, each left out when the app holds
 * no data for it. Shared by the exercise sheet and the technique detail so the
 * two always agree. [showMissingMuscles] says so when there is no muscle
 * profile instead of leaving the section out.
 */
internal fun InfoSheetScope.exerciseFacts(name: String, modifiers: String = "", showMissingMuscles: Boolean = false) {
    muscleFacts(name, modifiers, showMissingMuscles)
    howToFacts(name)
    cueFacts(name)
    mistakeFacts(name)
    gearFacts(name)
}

/** The muscle figure with its MAIN / ASSIST lines: the coverage screen's, so an exercise reads the same wherever the lifter asks. */
internal fun InfoSheetScope.muscleFacts(name: String, modifiers: String = "", showMissingMuscles: Boolean = false) {
    val shares = MuscleMap.profile(name, modifiers)?.muscles.orEmpty()
    if (shares.isNotEmpty()) {
        section("MUSCLES") { ExerciseMuscles(shares, Modifier.fillMaxWidth(), figureHeight = 180.dp) }
    } else if (showMissingMuscles) {
        text("MUSCLES", "The Ledger holds no muscle data for this exercise yet.", IronvellumColors.InkMuted)
    }
}

internal fun InfoSheetScope.howToFacts(name: String) {
    val guide = ExerciseGuides.forName(name) ?: return
    steps("HOW TO", guide.steps, lead = guide.setup)
}

internal fun InfoSheetScope.cueFacts(name: String) {
    val guide = ExerciseGuides.forName(name) ?: return
    bullets("CUES", guide.cues)
}

internal fun InfoSheetScope.mistakeFacts(name: String) {
    val guide = ExerciseGuides.forName(name) ?: return
    bullets("COMMON MISTAKES", guide.commonMistakes, IronvellumColors.InkMuted)
}

internal fun InfoSheetScope.gearFacts(name: String) {
    val gear = GearRequirements.needs(name)
        .joinToString(" or ") { set -> set.joinToString(" + ") { it.label.lowercase() } }
    if (gear.isNotEmpty()) text("ARMOURY", gear.replaceFirstChar { it.uppercase() })
}
