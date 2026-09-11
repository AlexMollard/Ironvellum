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
    val floor = if (fromZero) 0.0 else listOfNotNull(values.min(), goal).min()
    val span = (top - floor).takeIf { it > 0.0 } ?: 1.0
    val grid = MonarchColors.Rune
    val gold = MonarchColors.SovereignGold

    Canvas(modifier) {
        fun yFor(v: Double): Float = (size.height * (1.0 - ((v - floor) / span))).toFloat()

        // quiet baseline grid
        repeat(4) { i ->
            val y = size.height * i / 3f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f, alpha = 0.6f)
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
            drawPath(line, color = color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }

        goal?.let { g ->
            val y = yFor(g)
            var x = 0f
            val dash = 10.dp.toPx()
            while (x < size.width) {
                drawLine(
                    gold,
                    Offset(x, y),
                    Offset((x + dash / 2).coerceAtMost(size.width), y),
                    strokeWidth = 1.5f,
                    alpha = 0.8f,
                )
                x += dash
            }
        }

        // record and latest markers — the only gold fills
        val bestIndex = values.indexOf(values.max())
        values.forEachIndexed { i, v ->
            val isEdge = i == values.lastIndex || i == bestIndex
            drawCircle(
                color = if (isEdge) gold else color,
                radius = if (isEdge) 5.5f else 3f,
                center = Offset(xFor(i), yFor(v)),
            )
        }
    }
}
