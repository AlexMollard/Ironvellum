package com.ironvellum.app.ui.titles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import com.ironvellum.app.ui.theme.Metal
import com.ironvellum.app.ui.theme.RarityTint
import com.ironvellum.app.ui.theme.rememberPrismDrift
import kotlin.math.cos
import kotlin.math.sin

/** A point on the seal's 64-unit grid. */
internal data class SealPoint(val x: Float, val y: Float)

/** Each rarity retains its silhouette; ornaments leave the category mark clear. */
internal class SealGeometry(
    val outer: List<SealPoint>,
    val rim: List<SealPoint> = emptyList(),
    /** Six curved leaves and two open branches, in the seal's 64-unit space. */
    val leaves: List<String> = emptyList(),
    val branches: String? = null,
    /** Masterwork's quiet inset outline. */
    val core: List<SealPoint> = emptyList(),
    val glyphSize: Float,
)

private const val SEAL_C = 32f

private fun polar(n: Int, startDeg: Float, radius: (Int) -> Float): List<SealPoint> = List(n) { i ->
    val a = Math.toRadians((startDeg + i * 360f / n).toDouble())
    val r = radius(i)
    SealPoint(SEAL_C + r * cos(a).toFloat(), SEAL_C + r * sin(a).toFloat())
}

private val LaurelLeaves = listOf(
    "M23 51C17 51 15 47 15 44C20 44 23 47 23 51Z",
    "M17 44C12 43 10 39 11 36C16 37 18 40 17 44Z",
    "M15 36C10 34 10 29 12 27C16 29 17 33 15 36Z",
    "M41 51C47 51 49 47 49 44C44 44 41 47 41 51Z",
    "M47 44C52 43 54 39 53 36C48 37 46 40 47 44Z",
    "M49 36C54 34 54 29 52 27C48 29 47 33 49 36Z",
)

private val Seals: Map<TitleRarity, SealGeometry> by lazy {
    val prismOuter = polar(16, -90f) { if (it % 2 == 0) 31f else 25f }
    val prismCore = polar(8, -90f) { 23f }
    mapOf(
        TitleRarity.Common to SealGeometry(
            outer = polar(8, 22.5f) { 29f },
            glyphSize = 28f,
        ),
        TitleRarity.Rare to SealGeometry(
            outer = polar(8, 22.5f) { 29f },
            rim = polar(8, 22.5f) { 25f },
            glyphSize = 28f,
        ),
        TitleRarity.Epic to SealGeometry(
            outer = polar(24, -90f) { if (it % 2 == 0) 30.5f else 26.5f },
            leaves = LaurelLeaves,
            branches = "M29 54C16 51 11 38 16 24M35 54C48 51 53 38 48 24",
            glyphSize = 26f,
        ),
        TitleRarity.Masterwork to SealGeometry(
            outer = prismOuter,
            core = prismCore,
            glyphSize = 28f,
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
    "Volume" to listOf("M5 5V19M9.5 5V19M14 5V19M18.5 5V19M3 16.5L21 7.5"),
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
    val leaves = geometry.leaves.map { PathParser().parsePathString(it).toPath() }
    val branches = geometry.branches?.let { PathParser().parsePathString(it).toPath() }
    val core = geometry.core.takeIf { it.isNotEmpty() }?.toPath()
    val glyph = category?.let(::glyphPath)
}

/** The seal's own dark ground, so the tier's tint reads the same over any panel. */
private val SealGround = Color(0xFF121211)

private const val STAMP_MS = 300

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
    val still = remember(rarity, earned) { if (earned) Metal.of(rarity).span(64f) else SolidColor(tint) }
    val drift = rememberPrismDrift(64f, prism && motion)
    val stamp = remember { Animatable(if (stampIn && motion) 1.08f else 1f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(stampIn, motion) {
        if (stampIn && motion) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            stamp.animateTo(1f, tween(STAMP_MS, easing = FastOutSlowInEasing))
        }
    }
    Canvas(modifier.size(size).graphicsLayer { scaleX = stamp.value; scaleY = stamp.value }) {
        val metal = drift?.let { Metal.Masterwork.span(64f, it.value) } ?: still
        scale(this.size.minDimension / 64f, Offset.Zero) { drawSeal(paths, tint, metal, prism) }
    }
}

private fun DrawScope.drawSeal(p: SealPaths, tint: Color, metal: Brush, prism: Boolean) {
    fun stroke(w: Float) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(p.outer, SealGround, style = Fill)
    drawPath(p.outer, metal, alpha = 0.08f, style = Fill)
    drawPath(p.outer, metal, style = stroke(1.8f))
    p.rim?.let { drawPath(it, metal, alpha = 0.48f, style = stroke(0.85f)) }
    p.branches?.let { drawPath(it, metal, alpha = 0.85f, style = stroke(1.1f)) }
    p.leaves.forEach {
        drawPath(it, metal, alpha = 0.09f, style = Fill)
        drawPath(it, metal, alpha = 0.9f, style = stroke(1.1f))
    }
    p.core?.let { drawPath(it, metal, alpha = 0.45f, style = stroke(0.85f)) }
    p.glyph?.let { glyph ->
        val g = p.geometry.glyphSize
        translate((64f - g) / 2f, (64f - g) / 2f) {
            scale(g / 24f, Offset.Zero) { drawPath(glyph, SolidColor(if (prism) RarityTint.Prismatic else tint), style = stroke(1.8f)) }
        }
    }
}
