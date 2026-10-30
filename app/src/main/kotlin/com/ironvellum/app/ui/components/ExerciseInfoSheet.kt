package com.ironvellum.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Lines
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseGuides
import com.ironvellum.app.domain.ExerciseWhy
import com.ironvellum.app.domain.ExerciseWhys
import com.ironvellum.app.domain.FatigueFootprints
import com.ironvellum.app.domain.GearRequirements
import com.ironvellum.app.domain.LiftBoards
import com.ironvellum.app.domain.LiftKind
import com.ironvellum.app.domain.ListText
import com.ironvellum.app.domain.MuscleMap
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.program.ExerciseMusclesPanel
import com.ironvellum.app.ui.program.FootprintBody
import com.ironvellum.app.ui.program.FootprintWhy
import com.ironvellum.app.ui.program.whyLines
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder

/**
 * Facts about one exercise, all read from data the app already holds: the
 * muscle profile, how-to guide, gear table, skill tree and ally boards.
 * They sit on four swipeable pages, About (the facts), Form (how to, cues, mistakes), History
 * (what the lifter has logged on it) and Fatigue (where its fatigue lands), so none of it is a long scroll; a page without data is left out. [onPick] null hides the confirm button; otherwise it labels itself
 * [confirmLabel] and picks the exercise the way tapping the card does.
 * [modifiers] reshape the muscles as they do in the trial (a deficit
 * push-up works the chest at stretch).
 *
 * Each page is built to fit the sheet on a 412x891dp phone at the default text size without scrolling
 * (ExerciseInfoSheetFitsTest holds it to that); a larger text size scrolls instead.
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
    /** The WHY DO IT entry; the catalogue's for [exercise] unless a test brings its own. */
    why: ExerciseWhy? = ExerciseWhys.forName(exercise.name),
) {
    val skill = Skills.forName(exercise.name)
    // One line per board: an archer push-up is rung 4 itself and counts as a push-up (rung 2), so show the higher.
    val boards = LiftBoards.boardsFor(exercise.name).groupBy { it.lift }.values.map { same -> same.maxBy { it.rung ?: 0 } }
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
                // The drawing leads, small, beside the two facts a lifter looks for first.
                section(null) { AboutTop(exercise, lastLine) }
                if (why != null) section(Words.text(UiText.res(R.string.infosheet_why_label))) { WhyBody(why) }
                muscleFacts(exercise.name, modifiers, showMissingMuscles = true)
                if (skill != null) {
                    section(Words.technique.u) {
                        SkillPanel(skill.line, "${Lines.claimAt.replaceFirstChar { it.uppercase() }}: ${skill.standard}", skill.why, skill.prerequisites)
                    }
                }
                if (boards.isNotEmpty()) {
                    val lines = boards.joinToString("\n") { board ->
                            if (board.lift.kind == LiftKind.LADDER && board.rung != null) {
                                Words.text(UiText.themed(R.string.infosheet_board_rung, R.string.infosheet_board_rung__t, board.lift.label, board.rung.toString(), board.rungCount.toString()))
                            } else {
                                Words.text(UiText.themed(R.string.infosheet_board_counts, R.string.infosheet_board_counts__t, board.lift.label.lowercase()))
                            }
                        }
                    section(Words.theReckoning.u) { Text(lines, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.Ink) }
                }
            },
            InfoPage(stringResource(R.string.infosheet_page_form)) { formFacts(exercise.name) },
            InfoPage(Words.text(UiText.themed(R.string.infosheet_page_history, R.string.infosheet_page_history__t))) { historyFacts(exercise, onDismiss) },
            InfoPage(stringResource(R.string.infosheet_page_fatigue)) { fatigueFacts(exercise.name, modifiers) },
        ),
    )
}

/** The drawing at [ART_SIZE], grown into any spare page height, beside LAST WORKOUT and EQUIPMENT, each only when there is something to say. */
@Composable
private fun AboutTop(exercise: Exercise, lastLine: String?) {
    val gear = gearLine(exercise.name)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        val grow = LocalPageRoom.current?.take(ART_GROWTH) ?: 0.dp
        // Wider than tall, so a lying-down drawing (a push-up) is not shrunk to fit a square; the whole row when alone.
        val width = if (lastLine == null && gear == null) 1f else ART_WIDTH_SHARE
        ExerciseArtSlot(exercise.name, exercise.muscleGroup, Modifier.fillMaxWidth(width).height(ART_SIZE + grow))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lastLine != null) AboutFact(Words.text(UiText.themed(R.string.infosheet_last_label, R.string.infosheet_last_label__t)), lastLine)
            if (gear != null) AboutFact(Words.armoury.u, gear)
        }
    }
}

@Composable
private fun AboutFact(label: String, value: String) {
    Column {
        PanelLabel(label)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink)
    }
}

/** What the movement builds, when to pick it over its neighbours, and up to three tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhyBody(why: ExerciseWhy) {
    Text(why.purpose, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
    Spacer(Modifier.height(4.dp))
    Text(why.pickIt, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
    if (why.tags.isNotEmpty()) {
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            why.tags.take(MAX_WHY_TAGS).forEach { tag ->
                Text(
                    tag,
                    style = MaterialTheme.typography.labelMedium,
                    color = IronvellumColors.Ink,
                    modifier = Modifier
                        .inkBorder(LedgerContrast.Graphic, MaterialTheme.shapes.extraSmall, 1.dp)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** The skill in one panel: its line and what it needs on the title row, how it is claimed, then why it matters. */
@Composable
private fun SkillPanel(line: String, claim: String, why: String, prerequisites: List<String>) {
    InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(line, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink)
            if (prerequisites.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    UiText.res(R.string.infosheet_skill_needs, ListText.and(prerequisites)).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(claim, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
        if (why.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(why, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        }
    }
}

/**
 * The FORM page of an exercise or technique sheet: CUES first, as the thing to take to the bar, then HOW TO (setup
 * and steps) and COMMON MISTAKES, each left out when the app holds no data for it. Shared by the exercise sheet and
 * the technique detail so the two always agree.
 */
internal fun InfoSheetScope.formFacts(name: String) {
    cueFacts(name)
    howToFacts(name)
    mistakeFacts(name)
}

/** The muscle figure with its MAIN / ASSIST lines: both bodies beside the swatch lists, the same fills as the coverage screen's. */
internal fun InfoSheetScope.muscleFacts(name: String, modifiers: String = "", showMissingMuscles: Boolean = false) {
    val shares = MuscleMap.profile(name, modifiers)?.muscles.orEmpty()
    if (shares.isNotEmpty()) {
        section(Words.text(UiText.res(R.string.infosheet_section_muscles))) { ExerciseMusclesPanel(shares, name, Modifier.fillMaxWidth()) }
    } else if (showMissingMuscles) {
        text(Words.text(UiText.res(R.string.infosheet_section_muscles)), Lines.noMuscleData, IronvellumColors.InkMuted)
    }
}

/**
 * The FATIGUE page: where the movement's fatigue lands, what three hard sets at one rep left typically do to the
 * lit muscle and how it recovers, and why this lift leaves what it does. All derived by [FatigueFootprints] from
 * the same dose rule the Muscles tab uses; a movement with no muscle profile has no page.
 */
internal fun InfoSheetScope.fatigueFacts(name: String, modifiers: String = "") {
    val footprint = FatigueFootprints.of(name, modifiers) ?: return
    section(Words.text(UiText.res(R.string.fatigue_footprint_title))) { FootprintBody(footprint) }
    section(null) { FootprintWhy(whyLines(footprint).joinToString(" ")) }
}

private const val MAX_WHY_TAGS = 3
private val ART_SIZE = 76.dp

/** How far the drawing grows into a page's empty space: up to 140dp on a page with room to spare. */
private val ART_GROWTH = 64.dp

/** The drawing's share of the row when facts sit beside it. */
private const val ART_WIDTH_SHARE = 0.45f

private fun InfoSheetScope.howToFacts(name: String) {
    val guide = ExerciseGuides.forName(name) ?: return
    steps(Words.text(UiText.res(R.string.infosheet_section_how_to)), guide.steps, lead = guide.setup)
}

/** The cues in one panel, a row each at 16sp semibold: short enough to read mid-set. */
private fun InfoSheetScope.cueFacts(name: String) {
    val cues = ExerciseGuides.forName(name)?.cues.orEmpty()
    if (cues.isEmpty()) return
    section(Words.text(UiText.res(R.string.infosheet_section_cues))) {
        InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
            cues.forEachIndexed { index, cue ->
                if (index > 0) InkDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(IronvellumColors.Emerald, MaterialTheme.shapes.extraSmall))
                    Spacer(Modifier.width(12.dp))
                    Text(cue, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink)
                }
            }
        }
    }
}

private fun InfoSheetScope.mistakeFacts(name: String) {
    val guide = ExerciseGuides.forName(name) ?: return
    bullets(Words.text(UiText.res(R.string.infosheet_section_mistakes)), guide.commonMistakes, IronvellumColors.InkMuted)
}

/** The gear a movement needs, as one EQUIPMENT section (the technique detail's). */
internal fun InfoSheetScope.gearFacts(name: String) {
    gearLine(name)?.let { text(Words.armoury.u, it) }
}

/** The gear a movement needs ("Pull-up bar or rings"), or null when it needs none. */
private fun gearLine(name: String): String? {
    val gear = GearRequirements.needs(name)
        .joinToString(" or ") { set -> set.joinToString(" + ") { it.label.lowercase() } }
    return gear.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() }
}
