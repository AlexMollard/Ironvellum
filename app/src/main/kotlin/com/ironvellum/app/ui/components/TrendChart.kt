package com.ironvellum.app.ui.components

import com.ironvellum.app.domain.UiText
import com.ironvellum.app.data.Words
import com.ironvellum.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.theme.inkDot
import com.ironvellum.app.ui.theme.inkStroke
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * The house trend chart: a flat line over two quiet gridlines, an
 * end-point dot, optional first/last date labels and dashed goal line. Every
 * series in the app renders through this one composable.
 *
 * A null value is a gap, not a zero: the line breaks there, so a missing day
 * reads as missing. [positions] (0..1, one per value) places points by date
 * instead of by index, for sparse series like weigh-ins. Gold marks only a
 * record ([recordMarker]); a latest value is the plain ink end dot.
 *
 * A measurement chart ([fromZero] false) draws its top and bottom values on the left edge ("+46 kg" over "+20 kg"),
 * written by [axisText] or else by [valueText], and keeps only the two dates below.
 *
 * Given [atMs] (the time of each value) the chart also draws a dashed line at the recent average, labelled
 * "2-wk avg 79.6 kg": see [recentAverage] for the window. Volatile series read better against it. With [smooth]
 * (and [atMs]) the bold line is the time-weighted trend ([trendSmooth]) and the readings behind it are a thin faint
 * line with their dots.
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
    /** Writes the highest and lowest value, unit included, on the left edge of a [fromZero] = false chart; [valueText] when null. */
    axisText: ((Double) -> String)? = null,
    /** Epoch millis of each value, one per value; draws the recent-average line. Null for a series that is not a timeline. */
    atMs: List<Long>? = null,
    /** The average on the line's label when [valueText] is too long for it ("959 strength score"); [valueText] when null. */
    averageText: ((Double) -> String)? = null,
    /** What the chart sits on: the average's label is cleared onto it where the line cannot be avoided. */
    surface: Color = IronvellumColors.Vault,
    /** Draw the smoothed trend as the main line, the raw readings thin behind it; needs [atMs] and three readings. */
    smooth: Boolean = true,
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
    val average = remember(values, atMs) { if (atMs != null && atMs.size == values.size) recentAverage(values, atMs) else null }
    val averageLabel = average?.let { stringResource(R.string.trendchart_recent_avg, (averageText ?: valueText)(it)) }
    val averageSpoken = average?.let { stringResource(R.string.trendchart_recent_avg_description, valueText(it)) }
    val trend = remember(values, atMs, smooth) { if (smooth && atMs != null && atMs.size == values.size) trendSmooth(values, atMs) else null }
    val trendSpoken = trend?.lastOrNull { it != null }?.let { stringResource(R.string.trendchart_trend_description, valueText(it)) }
    val summary = description ?: chartSummary(values, startLabel, endLabel, valueText)
    val spoken = listOfNotNull(summary, averageSpoken, trendSpoken).joinToString(" ")
    val scrubState = remember { ScrubState() }
    val measurer = rememberTextMeasurer()
    val readoutStyle = MaterialTheme.typography.labelMedium.copy(color = IronvellumColors.Ink)
    // Every measurement chart labels its span on the left edge, in the caller's own units when it gave no axisText.
    val axis = if (!fromZero) chartAxisLabels(values, axisText ?: valueText, valueText) else null
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = IronvellumColors.InkMuted)
    val axisLayouts = axis?.let { (high, low) -> measurer.measure(high, axisStyle) to measurer.measure(low, axisStyle) }
    val density = LocalDensity.current
    // The labels' own width plus a gap: the plot starts after it, and so do the dates.
    val gutterPx = axisLayouts?.let { (high, low) -> maxOf(high.size.width, low.size.width) + with(density) { 8.dp.toPx() } } ?: 0f

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
                                List(values.size) { i -> if (values[i] == null) null else trendX(i, values.size, positions, width - gutterPx) },
                                x - gutterPx,
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            axisLayouts?.let { (high, low) ->
                val edge = 6.dp.toPx()
                drawText(high, topLeft = Offset(0f, (edge - high.size.height / 2f).coerceAtLeast(0f)))
                drawText(low, topLeft = Offset(0f, (size.height - edge - low.size.height / 2f).coerceAtMost(size.height - low.size.height)))
            }
            inset(left = gutterPx, top = 0f, right = 0f, bottom = 0f) {
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
                // the readings drawn as the main line, or thin and faint with their dots when a trend is the main line
                val shown = trend ?: values
                val rawColor = if (trend != null) color.copy(alpha = 0.4f) else color
                for (series in if (trend != null) listOf(values, trend) else listOf(values)) {
                    val raw = series === values
                    val lineColor = if (raw) rawColor else color
                    val lineWidth = if (raw && trend != null) 1.2.dp.toPx() else w
                    for (run in runs) {
                        if (run.first == run.last) {
                            // an isolated reading has no neighbour to join: a dot, not nothing
                            inkDot(Offset(xFor(run.first), yFor(series[run.first]!!)), 4f, lineColor)
                            continue
                        }
                        val line = Path().apply {
                            for (i in run) {
                                if (i == run.first) moveTo(xFor(i), yFor(series[i]!!)) else lineTo(xFor(i), yFor(series[i]!!))
                            }
                        }
                        drawPath(line, color = lineColor, style = Stroke(width = lineWidth, cap = StrokeCap.Round))
                    }
                    if (raw && trend != null) values.forEachIndexed { i, v -> if (v != null) inkDot(Offset(xFor(i), yFor(v)), 3f, rawColor) }
                }

                goal?.let { g -> dashedLine(yFor(g), 1.8f) }

                if (average != null && averageLabel != null) {
                    val y = yFor(average)
                    dashedLine(y, 1.6f)
                    drawAverageLabel(
                        measurer, axisStyle, averageLabel, y, runs, surface,
                        points = shown.indices.map { i -> shown[i]?.let { Offset(xFor(i), yFor(it)) } },
                    )
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
        }
        if (startLabel != null || endLabel != null) {
            // a measurement's span is on the left-edge axis, so only the dates sit below
            Box(Modifier.padding(start = with(density) { gutterPx.toDp() })) { ChartDates(startLabel, endLabel) }
        }
    }
}

private const val RECENT_AVERAGE_DAYS = 14
private const val RECENT_AVERAGE_MIN_POINTS = 3
private const val DAY_MS = 86_400_000L

/**
 * The mean the recent-average line sits at: the readings dated within [RECENT_AVERAGE_DAYS] days before the newest
 * one (inclusive), or the last three readings when fewer than three fall in that window. Null with fewer than three
 * readings in all. A null value is a gap and does not count; [atMs] need not be sorted.
 */
internal fun recentAverage(values: List<Double?>, atMs: List<Long>): Double? {
    val points = values.indices.mapNotNull { i -> values[i]?.let { atMs[i] to it } }.sortedBy { it.first }
    if (points.size < RECENT_AVERAGE_MIN_POINTS) return null
    val cutoff = points.last().first - RECENT_AVERAGE_DAYS * DAY_MS
    val window = points.filter { it.first >= cutoff }
    return (if (window.size >= RECENT_AVERAGE_MIN_POINTS) window else points.takeLast(RECENT_AVERAGE_MIN_POINTS)).map { it.second }.average()
}

private const val TREND_HALF_LIFE_DAYS = 7.0

/**
 * The trend of a series: an exponentially weighted moving average weighted by elapsed time, not point count, so a
 * long gap pulls the next reading in harder and a same-day pair barely moves it. The first reading seeds it; each
 * later one moves it by 1 - 0.5^(days since the previous / [halfLifeDays]) of the way. Aligned with [values]: a gap
 * stays a gap, and the result is null with fewer than three readings. [atMs] need not be sorted.
 */
internal fun trendSmooth(values: List<Double?>, atMs: List<Long>, halfLifeDays: Double = TREND_HALF_LIFE_DAYS): List<Double?>? {
    val order = values.indices.filter { values[it] != null }.sortedBy { atMs[it] }
    if (order.size < RECENT_AVERAGE_MIN_POINTS) return null
    val out = MutableList<Double?>(values.size) { null }
    var level = values[order.first()]!!
    out[order.first()] = level
    for (k in 1 until order.size) {
        val days = (atMs[order[k]] - atMs[order[k - 1]]) / DAY_MS.toDouble()
        level += (1.0 - Math.pow(0.5, days / halfLifeDays)) * (values[order[k]]!! - level)
        out[order[k]] = level
    }
    return out
}

/** A dashed horizontal rule across the plot at [y]. */
private fun DrawScope.dashedLine(y: Float, width: Float) {
    var x = 0f
    val dash = 10.dp.toPx()
    while (x < size.width) {
        inkStroke(Offset(x, y), Offset((x + dash / 2).coerceAtMost(size.width), y), IronvellumColors.InkMuted, width)
        x += dash
    }
}

/**
 * Writes [label] against the average line at [y]: above or below it, at the right or left end, whichever of the four
 * spots the plotted line and the end-point dot stay clear of (the least crossed when none is clear). [points] are
 * the plotted positions, null for a gap; [runs] the contiguous stretches between gaps. A spot the line still crosses
 * gets a [surface] backing so the words stay readable.
 */
private fun DrawScope.drawAverageLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: androidx.compose.ui.text.TextStyle,
    label: String,
    y: Float,
    runs: List<IntRange>,
    surface: Color,
    points: List<Offset?>,
) {
    val pad = 4.dp.toPx()
    val layout = measurer.measure(
        label, style, overflow = TextOverflow.Ellipsis, maxLines = 1,
        constraints = Constraints(maxWidth = (size.width - 2 * pad).toInt().coerceAtLeast(0)),
    )
    val w = layout.size.width.toFloat()
    val h = layout.size.height.toFloat()
    val gap = 3.dp.toPx()
    val dot = 6.dp.toPx()
    val lastDot = points.lastOrNull { it != null }
    fun crossings(left: Float, top: Float): Int {
        val right = left + w
        val bottom = top + h
        var n = 0
        if (lastDot != null && lastDot.x + dot > left && lastDot.x - dot < right && lastDot.y + dot > top && lastDot.y - dot < bottom) n++
        for (run in runs) {
            if (run.first == run.last) {
                val p = points[run.first]!!
                if (p.x in left..right && p.y in top..bottom) n++
                continue
            }
            for (i in run.first until run.last) {
                val a = points[i]!!
                val b = points[i + 1]!!
                val x0 = maxOf(minOf(a.x, b.x), left)
                val x1 = minOf(maxOf(a.x, b.x), right)
                if (x0 > x1) continue
                fun at(x: Float) = if (b.x == a.x) a.y else a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x)
                val lo = minOf(at(x0), at(x1))
                val hi = maxOf(at(x0), at(x1))
                if (hi > top && lo < bottom) n++
            }
        }
        return n
    }
    // the two ends first, then spots between them: the first clear one wins, ties keep the earlier
    val xs = listOf(size.width - pad - w, pad, (size.width - w) * 0.75f, (size.width - w) / 2f, (size.width - w) * 0.25f)
    val ys = listOf(y - gap - h, y + gap)
    val best = ys.flatMap { top -> xs.map { left -> left to top } }
        .filter { (_, top) -> top >= 0f && top + h <= size.height }
        .minByOrNull { (left, top) -> crossings(left, top) }
        ?: (xs[0] to (y - gap - h).coerceAtLeast(0f))
    if (crossings(best.first, best.second) > 0) {
        val margin = 2.dp.toPx()
        drawRect(surface.copy(alpha = 0.92f), topLeft = Offset(best.first - margin, best.second), size = androidx.compose.ui.geometry.Size(w + 2 * margin, h))
    }
    drawText(layout, topLeft = Offset(best.first, best.second))
}

/**
 * The top and bottom of a chart's y axis as (highest, lowest), each written by [axisText]; null when there is no span
 * to label (fewer than two readings, or all equal). When rounding makes the two read the same, [fine] writes them instead.
 */
internal fun chartAxisLabels(values: List<Double?>, axisText: (Double) -> String, fine: (Double) -> String = axisText): Pair<String, String>? {
    val present = values.filterNotNull()
    if (present.size < 2) return null
    val high = present.max()
    val low = present.min()
    if (high == low) return null
    val top = axisText(high)
    val bottom = axisText(low)
    return if (top == bottom) fine(high) to fine(low) else top to bottom
}

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
    val noData = stringResource(R.string.trendchart_no_data)

    Column(Modifier.fillMaxWidth()) {
        Canvas(
            modifier
                .semantics {
                    scrubSemantics(
                        spoken, scrubState, values.size,
                        included = { true },
                        readout = { i ->
                            scrubReadout(values[i]?.let(valueText) ?: noData, dateText?.invoke(i))
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
                    scrubReadout(if (v == null) noData else valueText(v), dateText?.invoke(at)),
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
private fun ChartDates(start: String?, end: String?) {
    // One label, not two, when both ends read the same.
    if (start != null && start == end) {
        Text(start, style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted, modifier = Modifier.padding(top = 4.dp))
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(start.orEmpty(), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.InkMuted)
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
    if (present.isEmpty()) return Words.text(UiText.res(R.string.chart_summary_none))
    val f = valueText
    val gaps = values.size - present.size
    val n = present.size
    val latest = f(values.last { it != null }!!)
    val low = f(present.min())
    val high = f(present.max())
    val ranged = startLabel != null && endLabel != null
    return Words.text(
        when {
            gaps > 0 && ranged -> UiText.plural(R.plurals.chart_summary_gaps_range, n, n, gaps, values.size, latest, low, high, startLabel!!, endLabel!!)
            gaps > 0 -> UiText.plural(R.plurals.chart_summary_gaps, n, n, gaps, values.size, latest, low, high)
            ranged -> UiText.plural(R.plurals.chart_summary_range, n, n, latest, low, high, startLabel!!, endLabel!!)
            else -> UiText.plural(R.plurals.chart_summary, n, n, latest, low, high)
        },
    )
}
