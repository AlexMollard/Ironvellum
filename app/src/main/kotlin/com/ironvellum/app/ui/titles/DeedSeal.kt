package com.ironvellum.app.ui.titles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.TitleRarity
import com.ironvellum.app.ui.components.animatorsOn
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.RarityTint
import kotlin.math.cos
import kotlin.math.sin

/** A point on the seal's 64-unit grid. */
internal data class SealPoint(val x: Float, val y: Float)

/**
 * One tier's seal as polygons on a 64-unit grid, so the shape of a deed says its tier without
 * colour: Common is a plain octagon; Rare adds a raised inner rim and 8 rivets; Fabled (Epic)
 * a 12-point scalloped edge with a laurel of 10 diamond leaves; Masterwork a 16-vertex faceted
 * star with a gem-cut centre. Pure data, so a unit test can pin the mapping.
 */
internal class SealGeometry(
    val outer: List<SealPoint>,
    /** The inner ring of Rare and Fabled; empty otherwise. */
    val rim: List<SealPoint> = emptyList(),
    val rivets: List<SealPoint> = emptyList(),
    val leaves: List<List<SealPoint>> = emptyList(),
    /** Masterwork's gem-cut centre; empty otherwise. */
    val core: List<SealPoint> = emptyList(),
    /** Masterwork's facet lines, from the core's corners to the star's points. */
    val spokes: List<Pair<SealPoint, SealPoint>> = emptyList(),
    /** Side of the square the category glyph is drawn in, centred on the seal. */
    val glyphSize: Float,
)

private const val SEAL_C = 32f

private fun polar(n: Int, startDeg: Float, radius: (Int) -> Float): List<SealPoint> = List(n) { i ->
    val a = Math.toRadians((startDeg + i * 360f / n).toDouble())
    val r = radius(i)
    SealPoint(SEAL_C + r * cos(a).toFloat(), SEAL_C + r * sin(a).toFloat())
}

private fun diamond(cx: Float, cy: Float, deg: Float): List<SealPoint> {
    val a = Math.toRadians(deg.toDouble())
    val c = cos(a).toFloat()
    val s = sin(a).toFloat()
    return listOf(0f to -4.2f, 2.1f to 0f, 0f to 4.2f, -2.1f to 0f).map { (x, y) ->
        SealPoint(cx + x * c - y * s, cy + x * s + y * c)
    }
}

private fun laurel(): List<List<SealPoint>> {
    fun leaf(angle: Float, rotation: Float): List<SealPoint> {
        val a = Math.toRadians(angle.toDouble())
        return diamond(SEAL_C + 19.5f * cos(a).toFloat(), SEAL_C + 19.5f * sin(a).toFloat(), rotation)
    }
    val left = listOf(120f, 150f, 180f, 210f, 240f).map { leaf(it, it + 50f) }
    val right = listOf(60f, 30f, 0f, -30f, -60f).map { leaf(it, it + 130f) }
    return left + right
}

private val Seals: Map<TitleRarity, SealGeometry> by lazy {
    val prismOuter = polar(16, -90f) { if (it % 2 == 0) 31f else 25f }
    val prismCore = polar(8, -90f) { 19f }
    mapOf(
        TitleRarity.Common to SealGeometry(
            outer = polar(8, 22.5f) { 29f },
            glyphSize = 28f,
        ),
        TitleRarity.Rare to SealGeometry(
            outer = polar(8, 22.5f) { 29f },
            rim = polar(8, 22.5f) { 24f },
            rivets = polar(8, 22.5f) { 26.5f },
            glyphSize = 28f,
        ),
        TitleRarity.Epic to SealGeometry(
            outer = polar(24, -90f) { if (it % 2 == 0) 30.5f else 26.5f },
            rim = polar(12, -90f) { 23f },
            leaves = laurel(),
            glyphSize = 22f,
        ),
        TitleRarity.Masterwork to SealGeometry(
            outer = prismOuter,
            core = prismCore,
            spokes = prismCore.mapIndexed { k, p -> p to prismOuter[2 * k] },
            glyphSize = 22f,
        ),
    )
}

internal fun sealGeometry(rarity: TitleRarity): SealGeometry = Seals.getValue(rarity)

/**
 * The category glyphs on a 24-unit grid, one stroked path set each: the mockup's SVG paths
 * verbatim. An unknown name falls back to Activities.
 */
private val GlyphPaths: Map<String, List<String>> = mapOf(
    "Trials" to listOf("M3 7h13l5 2-2 2h-4c0 2-1 3-3 3v2h3v3H7v-3h3v-2C6 14 4 11 3 7z"),
    "Level" to listOf("M5 12l7-7 7 7M5 19l7-7 7 7"),
    "Volume" to listOf("M6 5v14M10 5v14M14 5v14M18 5v14M3.5 16L20.5 8"),
    "Strength" to listOf(
        "M9 9V7.5a3 3 0 0 1 6 0V9",
        "M12 9c-4 0-7 2.7-7 6.2C5 18 6.7 20 9 20h6c2.3 0 4-2 4-4.8C19 11.7 16 9 12 9z",
    ),
    "Steps" to listOf(
        "M8 3.5c-1.8 0-2.8 2.4-2.8 4.6 0 1.8.9 2.9 2.8 2.9s2.8-1.1 2.8-2.9c0-2.2-1-4.6-2.8-4.6zM6.2 13h3.6v1.5a1.8 1.8 0 0 1-3.6 0z",
        "M16 9c-1.8 0-2.8 2.4-2.8 4.6 0 1.8.9 2.9 2.8 2.9s2.8-1.1 2.8-2.9c0-2.2-1-4.6-2.8-4.6zM14.2 18.5h3.6v1a1.8 1.8 0 0 1-3.6 0z",
    ),
    "Recovery" to listOf("M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z", "M17.5 3.5v4M15.5 5.5h4"),
    "Mastery" to listOf("M12 6.5C9.8 5 7 4.6 4 5v12.5c3-.4 5.8 0 8 1.5 2.2-1.5 5-1.9 8-1.5V5c-3-.4-5.8 0-8 1.5zM12 6.5V19"),
    "Activities" to listOf("M2.5 19L9 8l4 6 2.5-3.5L21.5 19z"),
)

private fun glyphPath(category: String): Path {
    val d = GlyphPaths[category] ?: GlyphPaths.getValue("Activities")
    return Path().also { all -> d.forEach { all.addPath(PathParser().parsePathString(it).toPath()) } }
}

private fun List<SealPoint>.toPath(): Path = Path().also { p ->
    forEachIndexed { i, pt -> if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y) }
    p.close()
}

private class SealPaths(val geometry: SealGeometry, category: String?) {
    val outer = geometry.outer.toPath()
    val rim = geometry.rim.takeIf { it.isNotEmpty() }?.toPath()
    val rivets = geometry.rivets.map { c ->
        List(6) { i ->
            val a = Math.toRadians(i * 60.0)
            SealPoint(c.x + 1.6f * cos(a).toFloat(), c.y + 1.6f * sin(a).toFloat())
        }.toPath()
    }
    val leaves = geometry.leaves.map { it.toPath() }
    val core = geometry.core.takeIf { it.isNotEmpty() }?.toPath()
    val spokes = Path().also { p ->
        geometry.spokes.forEach { (a, b) -> p.moveTo(a.x, a.y); p.lineTo(b.x, b.y) }
    }
    val glyph = category?.let(::glyphPath)
}

/** The seal's own dark ground, so the tier's tint reads the same over any panel. */
private val SealGround = Color(0xFF121211)

private val PrismStops = listOf(Color(0xFFBFB0F7), Color(0xFF9DD5F0), Color(0xFFF0B6E4), Color(0xFFBFB0F7))
private val PrismAt = floatArrayOf(0f, 0.35f, 0.65f, 1f)

/**
 * A flat diagonal gradient across the 64-unit grid, as the mockup's prism: no light source,
 * just a lighter tone, the tier's tint, then a deeper tone. [shift] slides it along the diagonal;
 * the prism's first and last stops match, so a shift of 0..64 loops seamlessly.
 */
private fun flat(colors: List<Color>, at: FloatArray, shift: Float = 0f): Brush = Brush.linearGradient(
    colorStops = colors.mapIndexed { i, c -> at[i] to c }.toTypedArray(),
    start = Offset(shift, shift),
    end = Offset(shift + 64f, shift + 64f),
    tileMode = TileMode.Repeated,
)

private val FlatAt = floatArrayOf(0f, 0.5f, 1f)

/** Lighter tone, the tier's [RarityTint] metal, a slightly deeper tone. */
private fun metalStops(rarity: TitleRarity): List<Color> = when (rarity) {
    TitleRarity.Common -> listOf(Color(0xFFA9AEB5), RarityTint.Iron, Color(0xFF6F747B))
    TitleRarity.Rare -> listOf(Color(0xFFE0A877), RarityTint.Bronze, Color(0xFFA86F44))
    TitleRarity.Epic -> listOf(Color(0xFFE8C672), RarityTint.Gold, Color(0xFFB88A30))
    TitleRarity.Masterwork -> PrismStops
}

private const val STAMP_MS = 300
private const val PRISM_DRIFT_MS = 18_000

/**
 * A deed's forged seal: a polygon stamped in its tier's metal ([RarityTint]) with the stroke
 * glyph of its [category] on it. Pass a null [category] for a bare seal (a tier header's mini
 * seal). A locked deed ([earned] false) keeps its tier's shape but takes the muted ink, so a
 * tier is only coloured once it is held. Still by default; [stampIn] gives the detail sheet's
 * big seal a one-off stamp, and an earned Masterwork's prism drifts slowly. Both stop when
 * system animations are off. Decorative: the row or sheet carries the words.
 */
@Composable
internal fun DeedSeal(
    rarity: TitleRarity,
    modifier: Modifier = Modifier,
    category: String? = null,
    size: Dp = 52.dp,
    earned: Boolean = true,
    stampIn: Boolean = false,
) {
    val paths = remember(rarity, category) { SealPaths(sealGeometry(rarity), category) }
    val tint = if (earned) RarityTint.of(rarity) else IronvellumColors.InkMuted
    val prism = earned && rarity == TitleRarity.Masterwork
    val motion = animatorsOn(LocalContext.current)
    val still = remember(rarity, earned) { if (earned) flat(metalStops(rarity), if (prism) PrismAt else FlatAt) else SolidColor(tint) }
    val drift = if (prism && motion) {
        rememberInfiniteTransition(label = "prism").animateFloat(
            0f, 64f, infiniteRepeatable(tween(PRISM_DRIFT_MS, easing = LinearEasing)), label = "shift",
        )
    } else null
    val stamp = remember { Animatable(if (stampIn && motion) 1.08f else 1f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(stampIn, motion) {
        if (stampIn && motion) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            stamp.animateTo(1f, tween(STAMP_MS, easing = FastOutSlowInEasing))
        }
    }
    Canvas(modifier.size(size).graphicsLayer { scaleX = stamp.value; scaleY = stamp.value }) {
        val metal = drift?.let { flat(PrismStops, PrismAt, it.value) } ?: still
        scale(this.size.minDimension / 64f, Offset.Zero) { drawSeal(paths, tint, metal, prism) }
    }
}

private fun DrawScope.drawSeal(p: SealPaths, tint: Color, metal: Brush, prism: Boolean) {
    fun stroke(w: Float) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val masterwork = p.core != null
    drawPath(p.outer, SealGround, style = Fill)
    drawPath(p.outer, metal, alpha = if (masterwork) 0.26f else 0.12f, style = Fill)
    drawPath(p.outer, metal, style = stroke(if (p.rivets.isNotEmpty()) 2.4f else 2f))
    p.rim?.let { drawPath(it, metal, alpha = if (p.rivets.isNotEmpty()) 0.6f else 0.55f, style = stroke(1.2f)) }
    p.rivets.forEach { drawPath(it, metal, style = Fill) }
    p.leaves.forEach { drawPath(it, metal, style = Fill) }
    p.core?.let {
        drawPath(it, SealGround, style = Fill)
        drawPath(it, metal, style = stroke(1.4f))
        drawPath(p.spokes, metal, alpha = 0.7f, style = stroke(1.2f))
    }
    p.glyph?.let { glyph ->
        val g = p.geometry.glyphSize
        translate((64f - g) / 2f, (64f - g) / 2f) {
            scale(g / 24f, Offset.Zero) { drawPath(glyph, SolidColor(if (prism) Color(0xFFBFB0F7) else tint), style = stroke(2f)) }
        }
    }
}
