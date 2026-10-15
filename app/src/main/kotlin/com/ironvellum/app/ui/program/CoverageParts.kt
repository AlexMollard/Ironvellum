package com.ironvellum.app.ui.program

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Evidence
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.MuscleLoad
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.ListRowHeight
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking

/*
 * The coverage furniture: marks, legend, rows, summary and the "why effort" explainer. Every state has a word
 * AND a mark - dashed ring, half-filled ring, solid check, ringed double chevron - so it reads without colour.
 */

internal fun markColor(state: CoverageState): Color = when (state) {
    CoverageState.NOT_TRAINED -> IronvellumColors.InkMuted
    CoverageState.BELOW_MINIMUM -> IronvellumColors.Ink
    CoverageState.COVERED -> IronvellumColors.Emerald
    CoverageState.HEAVY -> IronvellumColors.Caution
}

/**
 * The state's mark, decorative: whoever draws it says the state in words beside it. Not trained is a dashed
 * ring, below minimum a ring with its left half filled, covered a solid disc with a check, heavy a ring with a
 * double up-chevron. Path-only drawing, so the ruled-draw scan has nothing to count.
 */
@Composable
internal fun CoverageMark(state: CoverageState, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val color = markColor(state)
    val onFill = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val w = this.size.minDimension
        val stroke = w * 0.11f
        val r = (w - stroke) / 2f
        val c = Offset(w / 2f, w / 2f)
        val ring = Path().apply { addOval(Rect(c, r)) }
        when (state) {
            CoverageState.NOT_TRAINED -> drawPath(
                ring, color,
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.16f, w * 0.13f))),
            )
            CoverageState.BELOW_MINIMUM -> {
                drawPath(ring, color, style = Stroke(stroke))
                // The left half, from twelve o'clock round through nine to six.
                val half = Path().apply {
                    moveTo(c.x, c.y - r)
                    arcTo(Rect(c, r), -90f, -180f, false)
                    close()
                }
                drawPath(half, color)
            }
            CoverageState.COVERED -> {
                drawPath(ring, color)
                val check = Path().apply {
                    moveTo(w * 0.28f, w * 0.52f)
                    lineTo(w * 0.44f, w * 0.67f)
                    lineTo(w * 0.73f, w * 0.34f)
                }
                drawPath(check, onFill, style = Stroke(stroke * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            CoverageState.HEAVY -> {
                drawPath(ring, color, style = Stroke(stroke))
                val chevrons = Path().apply {
                    for (dy in listOf(0.40f, 0.60f)) {
                        moveTo(w * 0.32f, w * (dy + 0.08f))
                        lineTo(w * 0.50f, w * (dy - 0.08f))
                        lineTo(w * 0.68f, w * (dy + 0.08f))
                    }
                }
                drawPath(chevrons, color, style = Stroke(stroke * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** The legend's four keys: a mark, the state's name and the weekly hard sets it means. */
@Composable
internal fun CoverageLegend(showHeavy: Boolean) {
    val floor = ProgramRules.COVERED_MIN.toInt()
    LegendItem(CoverageState.NOT_TRAINED, "0")
    LegendItem(CoverageState.BELOW_MINIMUM, "under $floor")
    LegendItem(CoverageState.COVERED, "$floor+")
    if (showHeavy) LegendItem(CoverageState.HEAVY, "${ProgramRules.HEAVY_WEEK.toInt()}+")
}

@Composable
private fun LegendItem(state: CoverageState, range: String) {
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "${state.label}: $range hard sets a week" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverageMark(state, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            state.label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            range,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Ink,
        )
    }
}

/**
 * One muscle's week as a row: mark, name, hard sets and the state in words. Tapped, it lights the muscle on
 * the figure. The state rides on the node as its state description too, so a screen reader hears it without
 * reading the colour.
 */
@Composable
internal fun CoverageRow(
    muscle: Muscle,
    load: MuscleLoad?,
    state: CoverageState,
    onPick: () -> Unit,
) {
    val sets = hardSetsLabel(load?.weekly ?: 0.0)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ListRowHeight)
            .clickable(onClickLabel = "Show what trains ${muscle.label}", role = Role.Button, onClick = onPick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Show what trains ${muscle.label}: $sets, ${state.label}"
                stateDescription = state.label
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverageMark(state)
        Text(
            muscle.label,
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.Ink,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                sets,
                style = MaterialTheme.typography.labelMedium,
                color = IronvellumColors.Ink,
            )
            Text(
                state.label,
                style = MaterialTheme.typography.labelSmall,
                color = IronvellumColors.InkMuted,
            )
        }
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * The Forge's one-line coverage read: a mark, the summary in words, and a way into the page. A hint, never a
 * gate: nothing about it disables Apply.
 */
@Composable
internal fun CoverageChip(tally: CoverageTally, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val headline = coverageHeadline(tally)
    Column(modifier.fillMaxWidth()) {
        InkDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ListRowHeight)
                .clickable(onClickLabel = "Open coverage of this ${Words.cycle.s}", role = Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) { contentDescription = "Coverage: $headline" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoverageMark(tally.lead)
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    headline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.Ink,
                )
                if (tally.gaps > 0) {
                    Text(
                        COVERAGE_REASON,
                        style = MaterialTheme.typography.labelSmall,
                        color = IronvellumColors.InkMuted,
                    )
                }
            }
            Text(
                "Coverage ›",
                style = MaterialTheme.typography.labelMedium,
                color = IronvellumColors.SystemGreen,
            )
        }
        InkDivider()
    }
}

/** Three counts side by side - not trained, below minimum, covered - each with its mark. */
@Composable
internal fun CoverageTiles(tally: CoverageTally) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            CoverageState.NOT_TRAINED to tally.notTrained.size,
            CoverageState.BELOW_MINIMUM to tally.below.size,
            CoverageState.COVERED to tally.covered.size,
        ).forEach { (state, count) ->
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$count ${if (count == 1) "muscle" else "muscles"}: ${state.label}"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CoverageMark(state, size = 22.dp)
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = IronvellumColors.Ink,
                )
                Text(
                    state.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A "+ / −" disclosure row, announced as collapsed or expanded. */
@Composable
internal fun Disclosure(title: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ListRowHeight)
            .clickable(role = Role.Button, onClickLabel = if (open) "Hide $title" else "Show $title", onClick = onToggle)
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.Ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (open) "−" else "+",
            style = MaterialTheme.typography.titleMedium,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * Why the page counts hard sets and not set counts: three plain points and the papers behind them. The page
 * opens it inline from the "Why effort, not set counts" disclosure.
 */
@Composable
internal fun CoverageExplainer(tally: CoverageTally, onSeeMuscles: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        CoverageTiles(tally)
        Spacer(Modifier.height(12.dp))
        listOf(
            "Effort drives growth." to "A set taken close to failure is the one that counts.",
            "More sets help a bit." to "Each extra set adds less than the one before.",
            "About ${ProgramRules.COVERED_MIN.toInt()} hard sets a week" to "per muscle is a sound floor. Do more if you like.",
        ).forEachIndexed { index, (lead, rest) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SystemGreen,
                    modifier = Modifier.width(16.dp),
                )
                Column {
                    Text(lead, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink)
                    Text(rest, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                }
            }
        }
        Text(
            "${Evidence.ROBINSON_2024.label}; ${Evidence.PELLAND_2026.label}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Below minimum is a hint, never a block. Save any ${Words.cycle.s} you want.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        if (tally.gaps > 0) {
            Text(
                "See the muscles that need sets ›",
                style = MaterialTheme.typography.labelLarge,
                color = IronvellumColors.SystemGreen,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onSeeMuscles)
                    .padding(vertical = 14.dp),
            )
        }
        IronvellumButton(label = "Got it", onClick = onDone, quiet = true, modifier = Modifier.fillMaxWidth())
    }
}

/** What a lit heavy muscle adds to the page: why it is flagged, the paper, and a way to dismiss the flag. */
@Composable
internal fun HeavyNote(muscle: Muscle, load: MuscleLoad, onDismiss: () -> Unit) {
    val (reason, paper) = heavyReason(load)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoverageMark(CoverageState.HEAVY)
            Text(
                "${muscle.label} runs heavy: ${hardSetsLabel(load.weekly)} this week",
                style = MaterialTheme.typography.bodyMedium,
                color = IronvellumColors.Ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(reason, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
        Text(
            paper.label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        IronvellumButton(
            label = "Dismiss for ${muscle.label}",
            onClick = onDismiss,
            quiet = true,
            modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
        )
    }
}
