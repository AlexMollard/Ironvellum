package com.monarch.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.monarch.app.ui.theme.InkStyle
import com.monarch.app.ui.theme.inkDot
import com.monarch.app.ui.theme.inkStroke
import com.monarch.app.ui.theme.MonarchColors

/**
 * The house trend chart: gradient-filled line on a faint rune grid, record and
 * latest points marked gold, optional dashed goal line. Every series in the app
 * renders through this one composable — bars read as generic Material charting,
 * and two copies of the same canvas drift apart.
 */
@Composable
fun TrendChart(
    values: List<Double>,
    color: Color = MonarchColors.Emerald,
    goal: Double? = null,
    /**
     * Counts (steps, XP, sets) read honestly from zero. Measurements like
     * bodyweight or BMI live in a narrow band — zero-based, a 70-75 kg range
     * renders as a dead flat line, so those scale to their own span.
     */
    fromZero: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth().height(110.dp),
) {
    if (values.isEmpty()) return
    val top = (listOfNotNull(values.max(), goal).max()).takeIf { it > 0.0 } ?: 1.0
    // The floor belongs to the data alone — a goal line raised the floor too,
    // shifting the whole series inside the frame when the goal was toggled on.
    val floor = if (fromZero) 0.0 else values.min()
    val span = (top - floor).takeIf { it > 0.0 } ?: 1.0
    val grid = MonarchColors.Rune
    val gold = MonarchColors.SovereignGold

    Canvas(modifier) {
        fun yFor(v: Double): Float = (size.height * (1.0 - ((v - floor) / span))).toFloat()

        // quiet baseline grid
        repeat(4) { i ->
            val y = size.height * i / 3f
            inkStroke(Offset(0f, y), Offset(size.width, y), grid.copy(alpha = 0.6f), 1.4f, seed = i * 13)
        }

        val step = if (values.size == 1) 0f else size.width / (values.size - 1)
        fun xFor(i: Int): Float = if (values.size == 1) size.width / 2f else i * step

        // filled area under the trend
        if (values.size > 1) {
            val area = Path().apply {
                moveTo(0f, size.height)
                values.forEachIndexed { i, v -> lineTo(xFor(i), yFor(v)) }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(
                area,
                brush = Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f)),
                ),
            )
            val line = Path().apply {
                values.forEachIndexed { i, v ->
                    if (i == 0) moveTo(xFor(i), yFor(v)) else lineTo(xFor(i), yFor(v))
                }
            }
            // The series is brushed segment by segment BETWEEN the computed
            // points. The coordinates are untouched - a chart that wobbles its
            // data misreports a measurement - so only the stroke weight and
            // alpha breathe along the line.
            if (InkStyle.enabled) {
                val w = 3.2.dp.toPx()
                val rng = kotlin.random.Random(values.size * 31)
                values.forEachIndexed { i, v ->
                    if (i == 0) return@forEachIndexed
                    val weight = 0.7f + rng.nextFloat() * 0.5f
                    drawLine(
                        color = color.copy(alpha = color.alpha * (0.75f + 0.25f * weight)),
                        start = Offset(xFor(i - 1), yFor(values[i - 1])),
                        end = Offset(xFor(i), yFor(v)),
                        strokeWidth = w * weight,
                        cap = StrokeCap.Round,
                    )
                }
            } else {
                drawPath(line, color = color, style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round))
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

        // record and latest markers — the only gold fills
        // Last occurrence — a tied record flags the most recent achievement,
        // not the first time the hunter hit it back in mid-history.
        val bestIndex = values.lastIndexOf(values.max())
        values.forEachIndexed { i, v ->
            val isEdge = i == values.lastIndex || i == bestIndex
            inkDot(
                center = Offset(xFor(i), yFor(v)),
                radius = if (isEdge) 5.5f else 3f,
                color = if (isEdge) gold else color,
                seed = i,
            )
        }
    }
}
