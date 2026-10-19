package com.ironvellum.app.ui.program

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.CustomExercises
import com.ironvellum.app.domain.Muscle
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * The front and back figure as an input: each tap cycles the muscle under it through assist, main and off, the
 * way the exercise form picks what a movement works. [shares] holds the chosen muscles in the order they were
 * tapped (the first main one leads), MAIN at 1.0 and ASSIST at 0.5 - the same scale and the same fills as the
 * figure that shows an exercise's muscles ([ExerciseMuscleMap]). The MAIN and ASSIST lines sit beside it.
 *
 * It reuses [TappableFigure], so the hit testing and TalkBack nodes are the coverage figure's own. The figure is
 * given no selection: a tap arrives as the tapped muscle every time, and empty space (null) is ignored.
 */
@Composable
fun MuscleSelector(
    shares: Map<Muscle, Double>,
    onChange: (Map<Muscle, Double>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val figure = BodyFigures.of(LocalBodySex.current)
    val lineFor = { muscle: Muscle -> Words.text(UiText.res(R.string.muscles_selector_line, muscle.label, levelWord(shares[muscle]))) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TappableFigure(
            figure = figure,
            height = 180.dp,
            fill = { muscle -> shareFill(shares[muscle]) },
            selected = null,
            onSelect = { muscle -> if (muscle != null) onChange(cycled(shares, muscle)) },
            lineFor = lineFor,
            modifier = Modifier.width(176.dp),
        )
        Column(Modifier.weight(1f)) {
            val main = musclesAt(shares, ShareLevel.MAIN)
            val assist = musclesAt(shares, ShareLevel.ASSIST)
            MuscleLine(ShareLevel.MAIN, main)
            MuscleLine(ShareLevel.ASSIST, assist)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.muscles_selector_hint),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}

private fun levelWord(share: Double?): String = Words.text(
    UiText.res(
        when {
            share == null || share <= 0.0 -> R.string.muscles_level_off
            share >= CustomExercises.MAIN_SHARE -> R.string.muscles_level_main
            else -> R.string.muscles_level_assist
        },
    ),
)

/**
 * [shares] after a tap on [muscle]: off becomes assist, assist becomes main (keeping its place), main becomes
 * off. A muscle newly turned on joins the end, so the first MAIN muscle tapped stays the dominant one.
 */
internal fun cycled(shares: Map<Muscle, Double>, muscle: Muscle): Map<Muscle, Double> {
    val out = LinkedHashMap(shares)
    when (shares[muscle]) {
        null -> out[muscle] = CustomExercises.ASSIST_SHARE
        CustomExercises.ASSIST_SHARE -> out[muscle] = CustomExercises.MAIN_SHARE
        else -> out.remove(muscle)
    }
    return out
}
