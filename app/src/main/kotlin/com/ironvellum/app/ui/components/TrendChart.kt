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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.theme.inkDot
import com.ironvellum.app.ui.theme.inkStroke
import com.ironvellum.app.ui.theme.IronvellumColors
import java.util.Locale

/**
 * The house trend chart: a flat line over two quiet gridlines, an
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
    /** The value in the scrub readout, with its unit. */
    valueText: (Double) -> String = ::scrubNumber,
    /** The date of point [index] for the scrub readout; none when null. */
    dateText: ((index: Int) -> String?)? = null,
    /** Off for sparklines too small to hold a readout. */
    scrub: Boolean = true,
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
    val spoken = description ?: chartSummary(values, startLabel, endLabel, valueText)
    val scrubState = remember { ScrubState() }
    val measurer = rememberTextMeasurer()
    val readoutStyle = MaterialTheme.typography.labelMedium.copy(color = IronvellumColors.Ink)

    Column(Modifier.fillMaxWidth()) {
        Canvas(
            modifier
                .semantics {
                    if (scrub) {
                        scrubSemantics(
                            spoken, scrubState, values.size,
                            included = { values[it] != null },
                            readout = { scrubReadout(valueText(values[it]!!), dateText?.invoke(it)) },
                        )
                    } else {
                        contentDescription = spoken
                    }
                }
                .then(
                    if (scrub) {
                        Modifier.scrubGesture(scrubState) { x, width ->
                            nearestIndex(
                                List(values.size) { i -> if (values[i] == null) null else trendX(i, values.size, positions, width) },
                                x,
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            // inset so the extreme points and their dots sit inside the frame, not on its edge
            val inset = 6.dp.toPx()
            // a flat series (every reading equal) rides the middle, not the floor
            val flat = !fromZero && present.max() == present.min()
            fun yFor(v: Double): Float =
                if (flat) size.height / 2f
                else (inset + (size.height - 2 * inset) * (1.0 - ((v - floor) / span))).toFloat()
            fun xFor(i: Int): Float = trendX(i, values.size, positions, size.width)

            // two quiet gridlines, a third of the way in from each edge
            repeat(2) { i ->
                val y = size.height * (i + 1) / 3f
                inkStroke(Offset(0f, y), Offset(size.width, y), grid.copy(alpha = 0.6f), 1.4f)
            }

            // contiguous runs between gaps: each is stroked on its own
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

            val w = 2.5.dp.toPx()
            for (run in runs) {
                if (run.first == run.last) {
                    // an isolated reading has no neighbour to join: a dot, not nothing
                    inkDot(Offset(xFor(run.first), yFor(values[run.first]!!)), 4f, color)
                    continue
                }
                val line = Path().apply {
                    for (i in run) {
                        if (i == run.first) moveTo(xFor(i), yFor(values[i]!!)) else lineTo(xFor(i), yFor(values[i]!!))
                    }
                }
                drawPath(line, color = color, style = Stroke(width = w, cap = StrokeCap.Round))
            }

            goal?.let { g ->
                val y = yFor(g)
                var x = 0f
                val dash = 10.dp.toPx()
                while (x < size.width) {
                    inkStroke(
                        Offset(x, y),
                        Offset((x + dash / 2).coerceAtMost(size.width), y),
                        IronvellumColors.InkMuted,
                        1.8f,
                    )
                    x += dash
                }
            }

            // Markers: the latest point, and a record when asked for. Last
            // occurrence — a tied record flags the most recent achievement,
            // not the first time the lifter hit it back in mid-history.
            if (recordMarker && bestIndex != lastIndex) {
                inkDot(Offset(xFor(bestIndex), yFor(values[bestIndex]!!)), 5.5f, gold)
            }
            val endColor = if (recordMarker && bestIndex == lastIndex) gold else IronvellumColors.Ink
            inkDot(Offset(xFor(lastIndex), yFor(values[lastIndex]!!)), 5.5f, endColor)

            val at = scrubState.index
            val atValue = values.getOrNull(at)
            if (scrub && atValue != null) {
                drawScrub(
                    measurer, readoutStyle,
                    scrubReadout(valueText(atValue), dateText?.invoke(at)),
                    xFor(at), yFor(atValue), seed = at,
                )
            }
        }
        if (startLabel != null || endLabel != null) {
            // measurements get their span in the middle, since the chart has no y axis
            val mid = if (!fromZero && present.size > 1 && present.max() != present.min()) "${chartNum(present.min())}\u2013${chartNum(present.max())}" else null
            ChartDates(startLabel, endLabel, mid)
        }
    }
}

/** A whole figure reads as an integer ("70"), anything else with one decimal ("70.5"). */
private fun chartNum(v: Double) =
    if (v == Math.rint(v)) String.format(Locale.US, "%d", v.toLong()) else String.format(Locale.US, "%.1f", v)

/** The default readout number: one decimal, "." always. */
internal fun scrubNumber(v: Double): String = "%.1f".fmt(v)

/** The default readout for counts: whole, thousands-separated. */
internal fun scrubWhole(v: Double): String = "%,d".fmt(Math.round(v))

/** Value on the first line, date (when known) on the second. */
internal fun scrubReadout(value: String, date: String?): String = if (date == null) value else value + "\n" + date

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
    /** The final slot (today, on the Ledger's windows) in its own colour; null keeps [color]. */
    lastColor: Color? = null,
    /** The hairline stub drawn for a slot with no data. */
    emptyColor: Color = LedgerContrast.Graphic,
    modifier: Modifier = Modifier.fillMaxWidth().height(96.dp),
    startLabel: String? = null,
    endLabel: String? = null,
    description: String? = null,
    /** The value in the scrub readout, with its unit. */
    valueText: (Double) -> String = ::scrubWhole,
    /** The date of slot [index] for the scrub readout; none when null. */
    dateText: ((index: Int) -> String?)? = null,
) {
    if (values.isEmpty()) return
    val present = values.filterNotNull()
    val top = (listOfNotNull(present.maxOrNull(), goal).maxOrNull() ?: 0.0).takeIf { it > 0.0 } ?: 1.0
    val spoken = description ?: chartSummary(values, startLabel, endLabel, valueText)
    val scrubState = remember { ScrubState() }
    val measurer = rememberTextMeasurer()
    val readoutStyle = MaterialTheme.typography.labelMedium.copy(color = IronvellumColors.Ink)

    Column(Modifier.fillMaxWidth()) {
        Canvas(
            modifier
                .semantics {
                    scrubSemantics(
                        spoken, scrubState, values.size,
                        included = { true },
                        readout = { i ->
                            scrubReadout(values[i]?.let(valueText) ?: "no data", dateText?.invoke(i))
                        },
                    )
                }
                .scrubGesture(scrubState) { x, width -> slotIndex(x, width, values.size) },
        ) {
            // bars stand on a baseline just inside the frame so they never touch the date labels
            val pad = 4.dp.toPx()
            val base = size.height - pad
            fun yFor(v: Double): Float = (pad + (base - pad) * (1.0 - v / top)).toFloat()
            repeat(2) { i ->
                val y = size.height * (i + 1) / 3f
                inkStroke(Offset(0f, y), Offset(size.width, y), IronvellumColors.Rune.copy(alpha = 0.6f), 1.4f)
            }
            val slot = size.width / values.size
            val barW = (slot * 0.62f).coerceAtLeast(2.dp.toPx())
            values.forEachIndexed { i, v ->
                val x = slot * i + slot / 2f
                if (v == null) {
                    inkBar(x, base, 3.dp.toPx(), barW.coerceAtMost(3.dp.toPx()), emptyColor)
                } else {
                    val alpha = if (faded?.getOrNull(i) == true) 0.45f else 1f
                    val fill = if (i == values.lastIndex && lastColor != null) lastColor else color
                    inkBar(
                        x, base, base - yFor(v).coerceAtMost(base - 2.dp.toPx()), barW,
                        fill.copy(alpha = fill.alpha * alpha),
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
                        IronvellumColors.InkMuted, 1.8f,
                    )
                    x += dash
                }
            }

            val at = scrubState.index
            if (at in values.indices) {
                val v = values[at]
                drawScrub(
                    measurer, readoutStyle,
                    scrubReadout(if (v == null) "no data" else valueText(v), dateText?.invoke(at)),
                    barCenterX(at, values.size, size.width),
                    v?.let { yFor(it).coerceAtMost(base - 2.dp.toPx()) },
                    seed = at,
                )
            }
        }
        if (startLabel != null || endLabel != null) {
            ChartDates(startLabel, endLabel)
        }
    }
}

/**
 * One bar with flat ends: a filled quad, not a stroke, because a stroke is
 * drawn with round caps.
 */
private fun DrawScope.inkBar(centerX: Float, base: Float, height: Float, width: Float, color: Color) {
    val half = width / 2f
    val top = base - height
    val path = Path().apply {
        moveTo(centerX - half, base)
        lineTo(centerX - half, top)
        lineTo(centerX + half, top)
        lineTo(centerX + half, base)
        close()
    }
    drawPath(path, color)
}

@Composable
private fun ChartDates(start: String?, end: String?, mid: String? = null) {
    // One label, not two, when both ends read the same.
    if (start != null && start == end && mid == null) {
        Text(start, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 4.dp))
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(start.orEmpty(), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
        if (mid != null) Text(mid, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
        Text(end.orEmpty(), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
    }
}

/**
 * "Chart of 9 values, 5 of 14 slots empty. Latest 8.1 kg, low 2.0 kg, high 9.4 kg. From 18 Sep to 1 Oct."
 * The figures go through [valueText], the chart's own readout formatter, so
 * they carry the unit and the integer formatting a sighted reader gets.
 */
internal fun chartSummary(
    values: List<Double?>,
    startLabel: String?,
    endLabel: String?,
    valueText: (Double) -> String = ::scrubNumber,
): String {
    val present = values.filterNotNull()
    if (present.isEmpty()) return "Chart with no data"
    val f = valueText
    val gaps = values.size - present.size
    return buildString {
        append("Chart of ${present.size} ${if (present.size == 1) "value" else "values"}")
        if (gaps > 0) append(", $gaps of ${values.size} slots empty")
        append(". Latest ${f(values.last { it != null }!!)}, low ${f(present.min())}, high ${f(present.max())}.")
        if (startLabel != null && endLabel != null) append(" From $startLabel to $endLabel.")
    }
}
