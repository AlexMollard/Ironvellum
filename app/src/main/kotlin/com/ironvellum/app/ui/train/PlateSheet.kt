package com.ironvellum.app.ui.train

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.R
import com.ironvellum.app.data.PlateStore
import com.ironvellum.app.data.Units
import com.ironvellum.app.domain.LoadUnit
import com.ironvellum.app.domain.Loads
import com.ironvellum.app.domain.PlateRack
import com.ironvellum.app.domain.PlateRack.Kind
import com.ironvellum.app.domain.PlateSizes
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.BarChooser
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.components.StepButton
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.METAL_WASH
import com.ironvellum.app.ui.theme.Metal
import com.ironvellum.app.ui.theme.inkStroke

/**
 * The plate calculator: a bottom sheet over a barbell lift's live set. The load stepper walks only
 * the totals the owned plates can make; the bar, per exercise, is a tap away; the barbell is drawn
 * with its plates per side. A load that falls between plates shows the closest build below and
 * offers the nearest loadable either side. [onUse] hands back the chosen load in kg.
 */
@Composable
internal fun PlateSheet(
    exerciseName: String,
    setLabel: String,
    loadKg: Double?,
    onUse: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val unit = Units.current
    val setups by PlateStore.setups.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { PlateStore.load(context.applicationContext) }
    val setup = setups.getValue(unit)
    val rack = remember(setup, unit, exerciseName) { PlateStore.rack(setup, unit, exerciseName) }
    // Everything here is in the unit: the set's load snapped to what the screen shows for it.
    val setLoad = remember { loadKg?.takeIf { it > 0.0 }?.let { Loads.snap(it, unit) } }
    var target by remember(rack.bar) { mutableDoubleStateOf(setLoad ?: rack.bar) }
    var barOpen by remember { mutableStateOf(false) }
    val reading = rack.read(target)
    val name = unit.label
    fun say(value: Double) = "${PlateRack.number(value)} $name"

    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.plates_title), style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink)
                Text(
                    "$exerciseName · $setLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        },
        text = {
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.plates_load_label), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, modifier = Modifier.weight(1f))
                    StepButton("−", stringResource(R.string.plates_load_down_description)) { rack.stepFrom(target, up = false)?.let { target = it } }
                    Text(
                        say(target),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.Ink,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    StepButton("+", stringResource(R.string.plates_load_up_description)) { rack.stepFrom(target, up = true)?.let { target = it } }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.plates_bar_change_label)) { barOpen = !barOpen },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.plates_bar_label), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, modifier = Modifier.weight(1f))
                    Text("${say(rack.bar)} ›", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                }
                if (barOpen) {
                    BarChooser(unit, rack.bar) { PlateStore.setBarFor(context.applicationContext, unit, exerciseName, it) }
                }
                Text(
                    when (reading.kind) {
                        Kind.EXACT -> UiText.res(R.string.plates_reading_exact, say(reading.build.total))
                        Kind.BETWEEN -> UiText.res(R.string.plates_reading_between, say(reading.build.total))
                        Kind.UNDER_BAR -> UiText.res(R.string.plates_reading_under_bar, say(rack.bar))
                        Kind.OVER -> UiText.res(R.string.plates_reading_over, say(reading.build.total))
                    }.asString().uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                Barbell(reading.build, unit, UiText.res(R.string.plates_bar_named, say(rack.bar)).asString(), showGap = reading.kind == Kind.BETWEEN)
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.plates_each_side).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        if (reading.build.perSide.isEmpty()) stringResource(R.string.plates_bar_only) else reading.build.perSide.joinToString(" + ") { PlateRack.number(it) },
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.Ink,
                    )
                    Text(name, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                }
                Text(
                    UiText.res(R.string.plates_summary, say(reading.build.sideTotal), say(reading.build.sideTotal * 2), say(rack.bar)).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
                verdict(reading.kind, target, rack, ::say)?.let { (headline, detail) ->
                    Column(Modifier.padding(top = 12.dp)) {
                        Text(headline.asString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink)
                        Text(detail.asString(), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fun use(total: Double) = onUse(Loads.toKg(total, unit))
                val below = reading.build.total
                val above = reading.above?.total
                when (reading.kind) {
                    Kind.EXACT ->
                        if (setLoad != null && setLoad == below) {
                            IronvellumButton(stringResource(R.string.common_done), onClick = onDismiss)
                        } else {
                            IronvellumButton(UiText.res(R.string.plates_use, say(below)).asString(), onClick = { use(below) })
                        }
                    Kind.BETWEEN, Kind.UNDER_BAR -> {
                        if (above != null) IronvellumButton(UiText.res(R.string.plates_use, say(above)).asString(), quiet = true, onClick = { use(above) })
                        IronvellumButton(UiText.res(R.string.plates_use, say(below)).asString(), onClick = { use(below) })
                    }
                    Kind.OVER -> IronvellumButton(UiText.res(R.string.plates_use, say(below)).asString(), onClick = { use(below) })
                }
            }
        },
    )
}

/** The headline and detail under the build when the load is not an exact one, or null when it is. */
private fun verdict(kind: Kind, target: Double, rack: PlateRack, say: (Double) -> String): Pair<UiText, UiText>? = when (kind) {
    Kind.EXACT -> null
    Kind.BETWEEN -> UiText.res(R.string.plates_between_headline, say(target)) to
        (rack.step?.let { UiText.res(R.string.plates_between_detail, say(rack.smallest ?: it), say(it)) }
            ?: UiText.res(R.string.plates_between_detail_none))
    Kind.UNDER_BAR -> UiText.res(R.string.plates_under_bar_headline, say(target)) to UiText.res(R.string.plates_under_bar_detail, say(rack.bar))
    Kind.OVER -> UiText.res(R.string.plates_over_headline, say(target)) to UiText.res(R.string.plates_over_detail, say(rack.heaviest))
}

private const val VIEW_W = 364f
private const val VIEW_H = 152f
private const val PLATES_FROM = 84f
private const val PLATES_TO = 346f
private const val BAR_Y = 70f

/**
 * An iron line-art barbell, one side, in the viewBox the mock was drawn in: a flat diagonal Iron
 * gradient on the strokes and a faint wash in the plates, no sheen. Plate height follows its size;
 * a run of equal plates is named once under it ("25×2"). [showGap] draws the dashed "?" a build that
 * falls short would need.
 */
@Composable
private fun Barbell(build: PlateRack.Build, unit: LoadUnit, barLabel: String, showGap: Boolean) {
    val measurer = rememberTextMeasurer()
    val muted = IronvellumColors.InkMuted
    val ink = IronvellumColors.Ink
    val largest = PlateSizes.sizes(unit).first()
    val said = if (build.perSide.isEmpty()) {
        stringResource(R.string.plates_barbell_empty_description)
    } else {
        UiText.res(
            R.string.plates_barbell_description,
            build.perSide.joinToString(" and ") { PlateRack.number(it) },
            unit.label,
        ).asString()
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(VIEW_W / VIEW_H)
            .semantics { contentDescription = said },
    ) {
        val s = size.width / VIEW_W
        val iron = Metal.Common.span(extent = size.width)
        val stroke = Stroke(width = 2f * s)
        fun box(x: Float, y: Float, w: Float, h: Float, r: Float) {
            drawRoundRect(iron, Offset(x * s, y * s), Size(w * s, h * s), CornerRadius(r * s), alpha = METAL_WASH)
            drawRoundRect(iron, Offset(x * s, y * s), Size(w * s, h * s), CornerRadius(r * s), style = stroke)
        }
        inkStroke(Offset(12f * s, BAR_Y * s), Offset(352f * s, BAR_Y * s), iron, 4f * s)
        drawLabel(measurer, barLabel, Offset(12f * s, 44f * s), muted, s, 11f, FontWeight.Normal)
        box(70f, BAR_Y - 15f, 6f, 30f, 1.5f)

        val heightOf = { p: Double -> 56f + 65f * (p / largest).toFloat() }
        val widthOf = { p: Double -> 12f + 8f * (p / largest).toFloat() }
        val gap = 3f
        val natural = build.perSide.sumOf { (widthOf(it) + gap).toDouble() }.toFloat()
        val fit = if (natural > PLATES_TO - PLATES_FROM) (PLATES_TO - PLATES_FROM) / natural else 1f
        var x = PLATES_FROM
        var i = 0
        while (i < build.perSide.size) {
            val p = build.perSide[i]
            var run = 0
            val runStart = x
            while (i < build.perSide.size && build.perSide[i] == p) {
                val w = widthOf(p) * fit
                val h = heightOf(p)
                box(x, BAR_Y - h / 2f, w, h, 3f)
                x += w + gap * fit
                run++
                i++
            }
            val label = PlateRack.number(p) + if (run > 1) "×$run" else ""
            drawLabel(measurer, label, Offset((runStart + x - gap * fit) / 2f * s, 134f * s), ink, s, 13f, FontWeight.SemiBold, centred = true)
        }
        if (showGap) {
            val w = 5f
            drawRoundRect(
                muted, Offset(x * s, (BAR_Y - 15f) * s), Size(w * s, 30f * s), CornerRadius(2f * s),
                style = Stroke(width = 1.4f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * s, 3f * s))),
            )
            drawLabel(measurer, "?", Offset((x + w / 2f) * s, 134f * s), muted, s, 13f, FontWeight.Normal, centred = true)
            x += w + gap
        }
        box(x + 4f, BAR_Y - 12f, 9f, 24f, 2f)
    }
}

/** Text at a viewBox point, scaled with the drawing; [centred] puts the point at the text's middle. */
private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    at: Offset,
    color: Color,
    scale: Float,
    sizeUnits: Float,
    weight: FontWeight,
    centred: Boolean = false,
) {
    val result = measurer.measure(text, TextStyle(color = color, fontSize = (sizeUnits * scale).toSp(), fontWeight = weight))
    drawText(result, topLeft = if (centred) Offset(at.x - result.size.width / 2f, at.y) else at)
}
