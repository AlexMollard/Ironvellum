package com.ironvellum.app.ui.program

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.domain.FatigueLevel
import com.ironvellum.app.domain.FatigueReading
import com.ironvellum.app.domain.mostWorked
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.theme.IronvellumColors
import java.time.ZoneId

/** The most worked muscles Train's card names. */
private const val CARD_MUSCLES = 3

/**
 * Train's "Muscles right now": a small fatigue figure, the three most worked muscles with their level and
 * when they are fresh, and one coverage line. The top of the card opens Muscles > Fatigue, the coverage line
 * Muscles > Coverage. With no history it points at logging a workout; with nothing worked it says so.
 * Nothing shows until the first read has finished, so the card never flashes an all-fresh figure.
 */
@Composable
internal fun MusclesNowCard(
    ui: FatigueUi,
    coverageLine: String?,
    zone: ZoneId,
    onOpen: () -> Unit,
    onOpenCoverage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = ui.report
    if (!ui.loaded || report == null) return
    val worked = remember(report, zone) { report.mostWorked(zone, CARD_MUSCLES) }
    val levels = remember(report) { levelsOf(report) }
    Column(modifier.fillMaxWidth()) {
        InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.muscles_now_open_hint), onClick = onOpen)
                    .padding(start = 16.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(stringResource(R.string.muscles_now_title), Modifier.weight(1f), topPadding = 14.dp)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(FIGURE_WIDTH).clearAndSetSemantics {}) {
                        val figure = BodyFigures.of(LocalBodySex.current)
                        val look = { muscle: com.ironvellum.app.domain.Muscle -> fatigueLook(levels[muscle] ?: FatigueLevel.FRESH) }
                        TappableFigure(figure, FIGURE_HEIGHT, { look(it).fill }, null, {}, { "" }, interactive = false, decor = look)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        when {
                            !ui.hasHistory -> CardLine(UiText.themed(R.string.fatigue_empty_new, R.string.fatigue_empty_new__t).asString())
                            worked.isEmpty() -> CardLine(stringResource(R.string.muscles_now_rested))
                            else -> worked.forEach { CardMuscleRow(it) }
                        }
                    }
                }
            }
            if (coverageLine != null) {
                InkDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.muscles_now_coverage_hint), onClick = onOpenCoverage)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        coverageLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = IronvellumColors.InkMuted)
                }
            }
        }
    }
}

@Composable
private fun CardLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.InkMuted)
}

/** One worked muscle: its swatch and name, with its level and when it is fresh on the right. */
@Composable
private fun CardMuscleRow(reading: FatigueReading) {
    val fresh = freshByLine(reading.freshBy)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FatigueSwatch(reading.level)
        Text(
            reading.muscle.label,
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(levelWord(reading.level), style = MaterialTheme.typography.labelMedium, color = IronvellumColors.Ink, maxLines = 1)
            if (fresh != null) Text(fresh, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted, maxLines = 1)
        }
    }
}

private val FIGURE_WIDTH = 132.dp
private val FIGURE_HEIGHT = 120.dp
