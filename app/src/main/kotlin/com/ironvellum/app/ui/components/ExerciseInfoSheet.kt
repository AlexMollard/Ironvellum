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
 * They sit on two swipeable pages, ABOUT (the facts) and FORM (how to, cues, mistakes), so
 * none of it is a long scroll; a page without data is left out. [onPick] null hides the confirm button; otherwise it labels itself
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
        // Two pages: the gear rides on ABOUT, and how to, cues and mistakes share FORM. The
        // sheet drops a page with nothing on it.
        pages = listOf(
            InfoPage("ABOUT") {
                if (lastLine != null) text("LAST TRIAL", lastLine, IronvellumColors.SovereignGold)
                muscleFacts(exercise.name, modifiers, showMissingMuscles = true)
                gearFacts(exercise.name)
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
            InfoPage("FORM") { formFacts(exercise.name) },
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
 * The FORM page of an exercise or technique sheet: HOW TO (setup and steps), CUES and COMMON
 * MISTAKES, each left out when the app holds no data for it. Shared by the exercise sheet and the
 * technique detail so the two always agree.
 */
internal fun InfoSheetScope.formFacts(name: String) {
    howToFacts(name)
    cueFacts(name)
    mistakeFacts(name)
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

private fun InfoSheetScope.howToFacts(name: String) {
    val guide = ExerciseGuides.forName(name) ?: return
    steps("HOW TO", guide.steps, lead = guide.setup)
}

private fun InfoSheetScope.cueFacts(name: String) {
    val guide = ExerciseGuides.forName(name) ?: return
    bullets("CUES", guide.cues)
}

private fun InfoSheetScope.mistakeFacts(name: String) {
    val guide = ExerciseGuides.forName(name) ?: return
    bullets("COMMON MISTAKES", guide.commonMistakes, IronvellumColors.InkMuted)
}

internal fun InfoSheetScope.gearFacts(name: String) {
    val gear = GearRequirements.needs(name)
        .joinToString(" or ") { set -> set.joinToString(" + ") { it.label.lowercase() } }
    if (gear.isNotEmpty()) text("ARMOURY", gear.replaceFirstChar { it.uppercase() })
}
