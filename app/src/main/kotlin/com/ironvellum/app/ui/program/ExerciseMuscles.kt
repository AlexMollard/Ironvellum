package com.ironvellum.app.ui.program

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking

/**
 * What one exercise works: the figure filled by its shares, then the muscles
 * named under MAIN and ASSIST. Shared by the coverage screen and the live
 * trial, so an exercise reads the same wherever the lifter asks.
 */
@Composable
fun ExerciseMuscles(
    shares: Map<Muscle, Double>,
    exerciseName: String,
    modifier: Modifier = Modifier,
    figureHeight: Dp = 200.dp,
) {
    // One spoken summary for the figure, its legend and the lines under it,
    // which otherwise read the same muscles twice. The figure's own muscle
    // nodes stay outside the cleared parts so they can be stepped through.
    val description = exerciseMuscleSummary(shares)
    Column(modifier.semantics { contentDescription = description }) {
        ExerciseMuscleMap(shares, exerciseName, Modifier.fillMaxWidth(), figureHeight)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.clearAndSetSemantics {}) {
            MuscleLine(ShareLevel.MAIN, musclesAt(shares, ShareLevel.MAIN))
            MuscleLine(ShareLevel.ASSIST, musclesAt(shares, ShareLevel.ASSIST))
        }
    }
}

/**
 * The muscles [shares] works at [level], in the profile's own order: its
 * first main muscle is the exercise's dominant one.
 */
fun musclesAt(shares: Map<Muscle, Double>, level: ShareLevel): List<Muscle> =
    shares.filter { (_, share) -> share > 0.0 && shareLevel(share) == level }.keys.toList()

/**
 * What a screen reader hears for one exercise's figure, in the MAIN / ASSIST
 * words the legend uses: "Main: Lats, Biceps. Assists: Rear delts."
 */
fun exerciseMuscleSummary(shares: Map<Muscle, Double>): String {
    val main = musclesAt(shares, ShareLevel.MAIN)
    val assist = musclesAt(shares, ShareLevel.ASSIST)
    if (main.isEmpty() && assist.isEmpty()) return Words.text(UiText.res(R.string.muscles_exercise_none))
    return buildList {
        if (main.isNotEmpty()) add(Words.text(UiText.res(R.string.muscles_summary_main, main.joinToString(", ") { it.label })))
        if (assist.isNotEmpty()) add(Words.text(UiText.res(R.string.muscles_summary_assist, assist.joinToString(", ") { it.label })))
    }.joinToString(" ")
}

@Composable
internal fun MuscleLine(level: ShareLevel, muscles: List<Muscle>) {
    if (muscles.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            level.label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = if (level == ShareLevel.MAIN) IronvellumColors.Ink else IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
            modifier = Modifier.width(64.dp),
        )
        Text(
            muscles.joinToString(", ") { it.label },
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.Ink,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The About page's muscles panel: both bodies side by side (tappable, glowing the muscle under a tap) beside a
 * swatch list, MAIN then ASSIST. The swatches are the figure's own fills, so they are the legend; an unlit muscle
 * is the bare figure and needs no entry. One spoken summary stands for the figure and the lists.
 */
@Composable
fun ExerciseMusclesPanel(
    shares: Map<Muscle, Double>,
    exerciseName: String,
    modifier: Modifier = Modifier,
    figureHeight: Dp = 96.dp,
) {
    val description = exerciseMuscleSummary(shares)
    val figure = BodyFigures.of(LocalBodySex.current)
    val selection = rememberMuscleSelection()
    InkPanel(modifier.semantics { contentDescription = description }, contentPadding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TappableFigure(
                figure, figureHeight,
                { muscle -> shareFill(shares[muscle]) },
                selection.value, { selection.value = it },
                { muscle -> exerciseMuscleLine(muscle, shares[muscle], exerciseName) },
                Modifier.width(figureWidth(figure, figureHeight)),
            )
            Column(Modifier.weight(1f).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwatchGroup(ShareLevel.MAIN, musclesAt(shares, ShareLevel.MAIN), shareFill(1.0))
                SwatchGroup(ShareLevel.ASSIST, musclesAt(shares, ShareLevel.ASSIST), shareFill(0.5))
            }
        }
    }
}

/** One level's label over its swatch and the muscles at it; nothing when the exercise has none at that level. */
@Composable
private fun SwatchGroup(level: ShareLevel, muscles: List<Muscle>, swatch: Color) {
    if (muscles.isEmpty()) return
    val main = level == ShareLevel.MAIN
    Column {
        Text(
            level.label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
        )
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 4.dp).size(12.dp).background(swatch, MaterialTheme.shapes.extraSmall))
            Spacer(Modifier.width(8.dp))
            Text(
                muscles.joinToString(", ") { it.label },
                style = if (main) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                fontWeight = if (main) FontWeight.SemiBold else FontWeight.Normal,
                color = IronvellumColors.Ink,
            )
        }
    }
}
