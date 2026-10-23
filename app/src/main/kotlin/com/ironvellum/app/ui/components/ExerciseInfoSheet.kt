package com.ironvellum.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Lines
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseGuides
import com.ironvellum.app.domain.FatigueFootprints
import com.ironvellum.app.domain.GearRequirements
import com.ironvellum.app.domain.LiftBoards
import com.ironvellum.app.domain.LiftKind
import com.ironvellum.app.domain.ListText
import com.ironvellum.app.domain.MuscleMap
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.program.ExerciseMuscles
import com.ironvellum.app.ui.program.FootprintBody
import com.ironvellum.app.ui.program.FootprintExplainLink
import com.ironvellum.app.ui.program.FootprintTypical
import com.ironvellum.app.ui.program.whyLines
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * Facts about one exercise, all read from data the app already holds: the
 * muscle profile, how-to guide, gear table, skill tree and ally boards.
 * They sit on four swipeable pages, About (the facts), Form (how to, cues, mistakes), History
 * (what the lifter has logged on it) and Fatigue (where its fatigue lands), so none of it is a long scroll; a page without data is left out. [onPick] null hides the confirm button; otherwise it labels itself
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
    confirmLabel: String = stringResource(R.string.picker_confirm_add),
    modifiers: String = "",
    /** Shown for the lifter's own movements: opens the exercise form on this one. */
    onEdit: (() -> Unit)? = null,
) {
    val skill = Skills.forName(exercise.name)
    val boards = LiftBoards.boardsFor(exercise.name)
    val chips = buildList {
        add(InfoChip(exercise.muscleGroup.displayName))
        add(InfoChip(metricWord(exercise.metric).replaceFirstChar { it.uppercase() }))
        add(InfoChip(stringResource(if (exercise.isWeighted) R.string.infosheet_chip_weighted else R.string.infosheet_chip_bodyweight)))
        if (exercise.custom) add(InfoChip(Words.yours.c))
        if (skill != null) add(InfoChip(stringResource(R.string.infosheet_chip_tier, Skills.tierLabel(skill.tier)), IronvellumColors.SystemGreen))
    }

    InfoSheet(
        title = exercise.name,
        onDismiss = onDismiss,
        chips = chips,
        actions = buildList {
            add(InfoAction(stringResource(R.string.infosheet_close_button), onDismiss, quiet = true))
            if (onEdit != null) add(InfoAction(stringResource(R.string.infosheet_edit_button), onEdit, quiet = true))
            if (onPick != null) add(InfoAction(confirmLabel, onPick))
        },
        // Four pages: the gear rides on About, how to, cues and mistakes share Form, History is the
        // lifter's own sets and Fatigue is the movement's derived footprint. The sheet drops a page with nothing on it (History never is: it says "not logged yet").
        pages = listOf(
            InfoPage(stringResource(R.string.infosheet_page_about)) {
                // The drawing leads: what the movement looks like before anything is said about it.
                section(null) {
                    ExerciseArtSlot(exercise.name, exercise.muscleGroup, Modifier.fillMaxWidth().height(200.dp))
                }
                if (lastLine != null) text(Words.text(UiText.themed(R.string.infosheet_last_label, R.string.infosheet_last_label__t)), lastLine)
                muscleFacts(exercise.name, modifiers, showMissingMuscles = true)
                gearFacts(exercise.name)
                if (skill != null) {
                    section(Words.technique.u) {
                        TechniqueLine("${skill.line} · ${Lines.claimAt}: ${skill.standard}", IronvellumColors.Ink)
                        if (skill.why.isNotBlank()) TechniqueLine(skill.why, IronvellumColors.InkMuted)
                        if (skill.prerequisites.isNotEmpty()) {
                            TechniqueLine(UiText.res(R.string.infosheet_skill_needs, ListText.and(skill.prerequisites)).asString(), IronvellumColors.InkMuted)
                        }
                    }
                }
                if (boards.isNotEmpty()) {
                    bullets(
                        Words.theReckoning.u,
                        boards.map { board ->
                            if (board.lift.kind == LiftKind.LADDER && board.rung != null) {
                                Words.text(UiText.themed(R.string.infosheet_board_rung, R.string.infosheet_board_rung__t, board.lift.label, board.rung.toString(), board.rungCount.toString()))
                            } else {
                                Words.text(UiText.themed(R.string.infosheet_board_counts, R.string.infosheet_board_counts__t, board.lift.label.lowercase()))
                            }
                        },
                    )
                }
            },
            InfoPage(stringResource(R.string.infosheet_page_form)) { formFacts(exercise.name) },
            InfoPage(Words.text(UiText.themed(R.string.infosheet_page_history, R.string.infosheet_page_history__t))) { historyFacts(exercise, onDismiss) },
            InfoPage(stringResource(R.string.infosheet_page_fatigue)) { fatigueFacts(exercise.name, modifiers) },
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
        section(Words.text(UiText.res(R.string.infosheet_section_muscles))) { ExerciseMuscles(shares, name, Modifier.fillMaxWidth(), figureHeight = 180.dp) }
    } else if (showMissingMuscles) {
        text(Words.text(UiText.res(R.string.infosheet_section_muscles)), Lines.noMuscleData, IronvellumColors.InkMuted)
    }
}

/**
 * The FATIGUE page: where the movement's fatigue lands, what three hard sets at one rep left typically do and why
 * this lift leaves what it does. All derived by [FatigueFootprints] from the same dose rule the Muscles tab uses;
 * a movement with no muscle profile has no page.
 */
internal fun InfoSheetScope.fatigueFacts(name: String, modifiers: String = "") {
    val footprint = FatigueFootprints.of(name, modifiers) ?: return
    section(Words.text(UiText.res(R.string.fatigue_footprint_title))) { FootprintBody(footprint) }
    section(null) { FootprintTypical(footprint) }
    text(Words.text(UiText.res(R.string.fatigue_footprint_why_title)), whyLines(footprint).joinToString(" "), collapseAfterLines = WHY_LINES)
    section(null) { FootprintExplainLink() }
}

private const val WHY_LINES = 8

private fun InfoSheetScope.howToFacts(name: String) {
    val guide = ExerciseGuides.forName(name) ?: return
    steps(Words.text(UiText.res(R.string.infosheet_section_how_to)), guide.steps, lead = guide.setup)
}

private fun InfoSheetScope.cueFacts(name: String) {
    val guide = ExerciseGuides.forName(name) ?: return
    bullets(Words.text(UiText.res(R.string.infosheet_section_cues)), guide.cues)
}

private fun InfoSheetScope.mistakeFacts(name: String) {
    val guide = ExerciseGuides.forName(name) ?: return
    bullets(Words.text(UiText.res(R.string.infosheet_section_mistakes)), guide.commonMistakes, IronvellumColors.InkMuted)
}

internal fun InfoSheetScope.gearFacts(name: String) {
    val gear = GearRequirements.needs(name)
        .joinToString(" or ") { set -> set.joinToString(" + ") { it.label.lowercase() } }
    if (gear.isNotEmpty()) text(Words.armoury.u, gear.replaceFirstChar { it.uppercase() })
}
