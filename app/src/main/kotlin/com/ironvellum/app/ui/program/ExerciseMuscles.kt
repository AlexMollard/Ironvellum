package com.ironvellum.app.ui.program

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Muscle
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
    if (main.isEmpty() && assist.isEmpty()) return "No muscle data for this exercise."
    return buildList {
        if (main.isNotEmpty()) add("Main: " + main.joinToString(", ") { it.label } + ".")
        if (assist.isNotEmpty()) add("Assists: " + assist.joinToString(", ") { it.label } + ".")
    }.joinToString(" ")
}

@Composable
private fun MuscleLine(level: ShareLevel, muscles: List<Muscle>) {
    if (muscles.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            level.label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = if (level == ShareLevel.MAIN) IronvellumColors.SystemGreen else IronvellumColors.InkMuted,
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
