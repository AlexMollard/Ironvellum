package com.ironvellum.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironvellum.app.ui.theme.InkStyle
import com.ironvellum.app.ui.theme.inkDot
import com.ironvellum.app.ui.theme.inkStroke
import com.ironvellum.app.ui.theme.IronvellumColors
import java.util.Locale

/**
 * The house trend chart: gradient-filled line over two quiet gridlines, an
 * end-point dot, optional first/last date labels and dashed goal line. Every
 * series in the app renders through this one composable.
 *
 * A null value is a gap, not a zero: the line breaks there, so a missing day
 * reads as missing. [positions] (0..1, one per value) places points by date
 * instead of by index, for sparse series like weigh-ins. Gold marks only a
 * record ([recordMarker]); a latest value is the plain ink end dot.
 */
@Composable
fun TrendChart(
    values: List<Double?>,
    color: Color = IronvellumColors.Emerald,
    goal: Double? = null,
    /**
     * Counts (steps, XP, sets) read honestly from zero. Measurements like
     * bodyweight or BMI live in a narrow band — zero-based, a 70-75 kg range
     * renders as a dead flat line, so those scale to their own span.
     */
    fromZero: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth().height(110.dp),
    positions: List<Double>? = null,
    startLabel: String? = null,
    endLabel: String? = null,
    /** Spoken summary; a generated one (count, first, latest, range) when null. */
    description: String? = null,
    /** Mark the best value gold. Off for measurements, where the highest weight is not an achievement. */
    recordMarker: Boolean = true,
) {
    val present = values.filterNotNull()
    if (present.isEmpty()) return
    val top = (listOfNotNull(present.max(), goal).max()).takeIf { it > 0.0 } ?: 1.0
    // The floor belongs to the data alone — a goal line raised the floor too,
    // shifting the whole series inside the frame when the goal was toggled on.
    val floor = if (fromZero) 0.0 else present.min()
    val span = (top - floor).takeIf { it > 0.0 } ?: 1.0
    val grid = IronvellumColors.Rune
    val gold = IronvellumColors.SovereignGold
    val lastIndex = values.indexOfLast { it != null }
    val bestIndex = values.indexOfLast { it != null && it == present.max() }
    val spoken = description ?: chartSummary(values, startLabel, endLabel)

    Column(Modifier.fillMaxWidth()) {
        Canvas(modifier.semantics { contentDescription = spoken }) {
            // inset so the extreme points and their dots sit inside the frame, not on its edge
            val inset = 6.dp.toPx()
            // a flat series (every reading equal) rides the middle, not the floor
            val flat = !fromZero && present.max() == present.min()
            fun yFor(v: Double): Float =
                if (flat) size.height / 2f
                else (inset + (size.height - 2 * inset) * (1.0 - ((v - floor) / span))).toFloat()
            fun xFor(i: Int): Float = when {
                positions != null -> (positions[i] * size.width).toFloat()
                values.size == 1 -> size.width / 2f
                else -> i * size.width / (values.size - 1)
            }

            // two quiet gridlines, a third of the way in from each edge
            repeat(2) { i ->
                val y = size.height * (i + 1) / 3f
                inkStroke(Offset(0f, y), Offset(size.width, y), grid.copy(alpha = 0.6f), 1.4f, seed = i * 13)
            }

            // contiguous runs between gaps: each is filled and stroked on its own
            val runs = mutableListOf<IntRange>()
            var runStart = -1
            values.forEachIndexed { i, v ->
                if (v != null && runStart < 0) runStart = i
                if (v == null && runStart >= 0) {
                    runs += runStart until i
                    runStart = -1
                }
            }
            if (runStart >= 0) runs += runStart..values.lastIndex

            val w = 3.2.dp.toPx()
            val rng = kotlin.random.Random(values.size * 31)
            for (run in runs) {
                if (run.first == run.last) {
                    // an isolated reading has no neighbour to join: a dot, not nothing
                    inkDot(Offset(xFor(run.first), yFor(values[run.first]!!)), 4f, color, seed = run.first)
                    continue
                }
                val area = Path().apply {
                    moveTo(xFor(run.first), size.height)
                    for (i in run) lineTo(xFor(i), yFor(values[i]!!))
                    lineTo(xFor(run.last), size.height)
                    close()
                }
                drawPath(
                    area,
                    brush = Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f)),
                    ),
                )
                // The series is brushed segment by segment BETWEEN the computed
                // points. The coordinates are untouched - a chart that wobbles its
                // data misreports a measurement - so only the stroke weight and
                // alpha breathe along the line.
                if (InkStyle.enabled) {
                    for (i in run.first + 1..run.last) {
                        val weight = 0.7f + rng.nextFloat() * 0.5f
                        drawLine(
                            color = color.copy(alpha = color.alpha * (0.75f + 0.25f * weight)),
                            start = Offset(xFor(i - 1), yFor(values[i - 1]!!)),
                            end = Offset(xFor(i), yFor(values[i]!!)),
                            strokeWidth = w * weight,
                            cap = StrokeCap.Round,
                        )
                    }
                } else {
                    val line = Path().apply {
                        for (i in run) {
                            if (i == run.first) moveTo(xFor(i), yFor(values[i]!!)) else lineTo(xFor(i), yFor(values[i]!!))
                        }
                    }
                    drawPath(line, color = color, style = Stroke(width = w, cap = StrokeCap.Round))
                }
            }

            goal?.let { g ->
                val y = yFor(g)
                var x = 0f
                val dash = 10.dp.toPx()
                while (x < size.width) {
                    inkStroke(
                        Offset(x, y),
                        Offset((x + dash / 2).coerceAtMost(size.width), y),
                        gold.copy(alpha = 0.8f),
                        1.8f,
                        seed = x.toInt(),
                        taperEnds = false,
                    )
                    x += dash
                }
            }

            // Markers: the latest point, and a record when asked for. Last
            // occurrence — a tied record flags the most recent achievement,
            // not the first time the lifter hit it back in mid-history.
            if (recordMarker && bestIndex != lastIndex) {
                inkDot(Offset(xFor(bestIndex), yFor(values[bestIndex]!!)), 5.5f, gold, seed = bestIndex)
            }
            val endColor = if (recordMarker && bestIndex == lastIndex) gold else IronvellumColors.Ink
            inkDot(Offset(xFor(lastIndex), yFor(values[lastIndex]!!)), 5.5f, endColor, seed = lastIndex)
        }
        if (startLabel != null || endLabel != null) {
            // measurements get their span in the middle, since the chart has no y axis
            val mid = if (!fromZero && present.size > 1 && present.max() != present.min()) "${chartNum(present.min())}\u2013${chartNum(present.max())}" else null
            ChartDates(startLabel, endLabel, mid)
        }
    }
}

private fun chartNum(v: Double) = String.format(Locale.US, "%.1f", v)

/**
 * Bars for per-day counts. A null slot is a visible gap (a hairline stub), so
 * "3 of 14 days tracked" can be seen. [faded] marks estimated slots.
 */
@Composable
fun BarChart(
    values: List<Double?>,
    color: Color = IronvellumColors.Emerald,
    goal: Double? = null,
    faded: List<Boolean>? = null,
    modifier: Modifier = Modifier.fillMaxWidth().height(96.dp),
    startLabel: String? = null,
    endLabel: String? = null,
    description: String? = null,
) {
    if (values.isEmpty()) return
    val present = values.filterNotNull()
    val top = (listOfNotNull(present.maxOrNull(), goal).maxOrNull() ?: 0.0).takeIf { it > 0.0 } ?: 1.0
    val gold = IronvellumColors.SovereignGold
    val spoken = description ?: chartSummary(values, startLabel, endLabel)

    Column(Modifier.fillMaxWidth()) {
        Canvas(modifier.semantics { contentDescription = spoken }) {
            // bars stand on a baseline just inside the frame so round caps never touch the date labels
            val pad = 4.dp.toPx()
            val base = size.height - pad
            fun yFor(v: Double): Float = (pad + (base - pad) * (1.0 - v / top)).toFloat()
            repeat(2) { i ->
                val y = size.height * (i + 1) / 3f
                inkStroke(Offset(0f, y), Offset(size.width, y), IronvellumColors.Rune.copy(alpha = 0.6f), 1.4f, seed = i * 13)
            }
            val slot = size.width / values.size
            val barW = (slot * 0.62f).coerceAtLeast(2.dp.toPx())
            values.forEachIndexed { i, v ->
                val x = slot * i + slot / 2f
                if (v == null) {
                    inkStroke(
                        Offset(x, base), Offset(x, base - 3.dp.toPx()),
                        IronvellumColors.Bracket, barW.coerceAtMost(3.dp.toPx()), seed = i, taperEnds = false,
                    )
                } else {
                    val alpha = if (faded?.getOrNull(i) == true) 0.45f else 1f
                    inkStroke(
                        Offset(x, base), Offset(x, yFor(v).coerceAtMost(base - 2.dp.toPx())),
                        color.copy(alpha = color.alpha * alpha), barW, seed = i, taperEnds = false,
                    )
                }
            }
            goal?.let { g ->
                val y = yFor(g)
                var x = 0f
                val dash = 10.dp.toPx()
                while (x < size.width) {
                    inkStroke(
                        Offset(x, y), Offset((x + dash / 2).coerceAtMost(size.width), y),
                        gold.copy(alpha = 0.8f), 1.8f, seed = x.toInt(), taperEnds = false,
                    )
                    x += dash
                }
            }
        }
        if (startLabel != null || endLabel != null) {
            ChartDates(startLabel, endLabel)
        }
    }
}

@Composable
private fun ChartDates(start: String?, end: String?, mid: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(start.orEmpty(), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
        if (mid != null) Text(mid, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
        Text(end.orEmpty(), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
    }
}

/** "Chart, 14 slots, 9 with data. Latest 8.1, low 2.0, high 9.4. From 18 Sep to 1 Oct." */
internal fun chartSummary(values: List<Double?>, startLabel: String?, endLabel: String?): String {
    val present = values.filterNotNull()
    if (present.isEmpty()) return "Chart with no data"
    fun f(v: Double) = if (v >= 1000.0) String.format(Locale.US, "%,.0f", v) else String.format(Locale.US, "%.1f", v)
    val gaps = values.size - present.size
    return buildString {
        append("Chart of ${present.size} ${if (present.size == 1) "value" else "values"}")
        if (gaps > 0) append(", $gaps of ${values.size} slots empty")
        append(". Latest ${f(values.last { it != null }!!)}, low ${f(present.min())}, high ${f(present.max())}.")
        if (startLabel != null && endLabel != null) append(" From $startLabel to $endLabel.")
    }
}
