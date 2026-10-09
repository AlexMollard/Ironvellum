package com.ironvellum.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.RelicHouse
import com.ironvellum.app.domain.RewardRarity
import com.ironvellum.app.domain.TitleRarity
import com.ironvellum.app.ui.theme.Metal
import com.ironvellum.app.ui.theme.METAL_WASH
import com.ironvellum.app.ui.theme.rememberPrismDrift
import com.ironvellum.app.ui.titles.SealGeometry
import com.ironvellum.app.ui.titles.SealPoint
import com.ironvellum.app.ui.titles.sealGeometry
import kotlin.math.cos
import kotlin.math.sin

/*
 * The art of the sixteen house relics, drawn as Compose paths from the geometry of the approved
 * sigils: concentric setting rings, ruled spokes, one washed and stroked star, a ringed core, all in
 * a 100 unit square. A relic is `house.form`, so its art is a frozen row of the table below and
 * never a hash of its name. The house sets the motif (the mark on the rim), the form sets the shape
 * (points, depth, turn of the spokes, rings, spokes). The star stands upright, as the mockups draw it.
 *
 * Rarity is the metal: every line is a flat diagonal gradient in the tier's own metal ([Metal]), with
 * a faint wash inside the star. No solid metal fills, no bevel, highlight, sheen or glint. A relic on
 * a plate sits inside its tier's seal outline, the one DeedSeal draws for deeds (docs/DESIGN.md,
 * "rarity, not accents"). Only the Masterwork prism ever moves, and only while the caller says
 * motion is on.
 */

/** One sigil's geometry. A relic's row never changes once released; add rows, never edit them. */
internal data class RelicSpec(
    /** Tips of the star. */
    val points: Int,
    /** Depth of the star's valleys as a share of its tip radius: low is sharp, high is round. */
    val inner: Float,
    /** The turn of the ruled spokes behind the star, in degrees. The star itself stands upright. */
    val rotation: Float,
    /** Concentric setting rings, 1 to 3. */
    val rings: Int,
    /** Ruled spokes behind the star. */
    val spokes: Int,
    /** The core ring's size in sigil units. */
    val core: Float,
)

/** The sixteen relics, keyed by `house.form`. */
internal val RELIC_SPECS: Map<String, RelicSpec> = mapOf(
    "iron.band" to RelicSpec(4, 0.50f, 15f, 2, 4, 7.5f),
    "iron.crown" to RelicSpec(5, 0.40f, 30f, 3, 8, 7.0f),
    "iron.chain" to RelicSpec(6, 0.62f, 0f, 1, 6, 8.1f),
    "iron.plate" to RelicSpec(3, 0.50f, 70f, 2, 5, 7.2f),

    "vigil.bell" to RelicSpec(8, 0.70f, 10f, 2, 8, 8.5f),
    "vigil.seal" to RelicSpec(7, 0.55f, 40f, 1, 7, 7.8f),
    "vigil.key" to RelicSpec(5, 0.65f, 100f, 3, 6, 8.2f),
    "vigil.hourglass" to RelicSpec(6, 0.42f, 20f, 2, 5, 7.1f),

    "craft.quill" to RelicSpec(3, 0.60f, 0f, 3, 4, 8.0f),
    "craft.compass" to RelicSpec(8, 0.40f, 22f, 1, 8, 7.0f),
    "craft.seal" to RelicSpec(4, 0.71f, 45f, 2, 6, 8.5f),
    "craft.chisel" to RelicSpec(7, 0.38f, 60f, 3, 5, 6.9f),

    "return.lantern" to RelicSpec(6, 0.50f, 80f, 3, 7, 7.5f),
    "return.thread" to RelicSpec(4, 0.38f, 5f, 2, 8, 6.9f),
    "return.gate" to RelicSpec(8, 0.55f, 33f, 3, 6, 7.8f),
    "return.door" to RelicSpec(5, 0.72f, 140f, 1, 4, 8.6f),
)

/** What a house's tile shows: one emblem per house, never a relic, since the houses do not share forms. */
internal val HOUSE_EMBLEMS: Map<RelicHouse, RelicSpec> = mapOf(
    RelicHouse.Iron to RelicSpec(4, 0.50f, 0f, 2, 4, 8.0f),
    RelicHouse.Vigil to RelicSpec(8, 0.62f, 0f, 2, 8, 8.0f),
    RelicHouse.Craft to RelicSpec(6, 0.45f, 0f, 2, 6, 8.0f),
    RelicHouse.Return to RelicSpec(5, 0.60f, 0f, 2, 5, 8.0f),
)

/** The tip radius of every star, in sigil units. */
private const val TIP = 39f

/** The outermost setting ring, in sigil units. */
private const val RIM = 46f

/** How much of a plate's box the sigil on it takes: it sits inside the plate's own rim. */
private const val ON_PLATE = 0.62f

/** A relic not yet drawn, as a silhouette: one flat tone, no metal. */
private val Silhouette = Color(0xFF3A3935)

/** The plate's own dark ground, so a metal reads the same over any panel (DeedSeal's). */
private val PlateGround = Color(0xFF121211)

private fun at(c: Offset, r: Float, deg: Float, u: Float): Offset {
    val a = Math.toRadians(deg.toDouble())
    return Offset(c.x + r * u * cos(a).toFloat(), c.y + r * u * sin(a).toFloat())
}

private fun DrawScope.ring(c: Offset, radius: Float, brush: Brush, alpha: Float, width: Float) {
    val path = Path().apply { addOval(Rect(c, radius)) }
    drawPath(path, brush, alpha = alpha, style = Stroke(width))
}

private fun DrawScope.seg(from: Offset, to: Offset, brush: Brush, alpha: Float, width: Float) {
    val path = Path().apply { moveTo(from.x, from.y); lineTo(to.x, to.y) }
    drawPath(path, brush, alpha = alpha, style = Stroke(width, cap = StrokeCap.Round))
}

/**
 * The house's mark on the rim: Iron four bolts, Vigil twelve dial ticks, Craft eight studs, Return
 * three beacons. Studs and beacons are small rings, not discs, so nothing is a solid metal fill.
 */
private fun DrawScope.drawRimMotif(house: RelicHouse?, c: Offset, u: Float, brush: Brush) {
    when (house) {
        RelicHouse.Iron -> repeat(4) {
            val a = it * 90f
            seg(at(c, RIM - 3.5f, a, u), at(c, RIM + 3f, a, u), brush, 0.75f, 4.2f * u)
        }
        RelicHouse.Vigil -> repeat(12) {
            val a = it * 30f
            seg(at(c, RIM - 2.5f, a, u), at(c, RIM + 1f, a, u), brush, 0.6f, 1.5f * u)
        }
        RelicHouse.Craft -> repeat(8) {
            ring(at(c, RIM, it * 45f + 22.5f, u), 1.6f * u, brush, 0.7f, 1.3f * u)
        }
        RelicHouse.Return -> repeat(3) {
            ring(at(c, RIM, it * 120f - 90f, u), 2.3f * u, brush, 0.8f, 1.5f * u)
        }
        null -> Unit
    }
}

/** One sigil, centred on [c], [u] pixels to a unit, every line in [brush]. */
private fun DrawScope.drawSigil(spec: RelicSpec, house: RelicHouse?, c: Offset, u: Float, brush: Brush) {
    drawRimMotif(house, c, u, brush)
    repeat(spec.rings) { r ->
        ring(c, (RIM - 8f * r) * u, brush, 0.22f + 0.12f * r, 2.2f * u)
    }
    repeat(spec.spokes) { i ->
        val a = spec.rotation + 360f * i / spec.spokes - 7f
        seg(c, at(c, 43f, a, u), brush, 0.35f, 1.6f * u)
    }
    val star = Path()
    val vertices = spec.points * 2
    for (i in 0 until vertices) {
        val r = if (i % 2 == 0) TIP else TIP * spec.inner
        val p = at(c, r, -90f + 360f * i / vertices, u)
        if (i == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
    }
    star.close()
    drawPath(star, brush, alpha = METAL_WASH, style = Fill)
    drawPath(star, brush, alpha = 0.9f, style = Stroke(width = 3.2f * u, join = StrokeJoin.Round))
    ring(c, spec.core * 0.68f * u, brush, 0.9f, 2.4f * u)
}

private fun List<SealPoint>.toPath(): Path = Path().also { p ->
    forEachIndexed { i, pt -> if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y) }
    p.close()
}

private fun RewardRarity.seal(): TitleRarity = when (this) {
    RewardRarity.Common -> TitleRarity.Common
    RewardRarity.Rare -> TitleRarity.Rare
    RewardRarity.Epic -> TitleRarity.Epic
    RewardRarity.Masterwork -> TitleRarity.Masterwork
}

/**
 * The plate a relic or an echo count sits on: its tier's seal outline from DeedSeal's geometry, without
 * the glyph and the laurel (the thing on it is the picture). Common is a plain octagon, Rare adds a rim
 * and eight rivet rings, Fabled the scalloped edge and a rim, Masterwork the faceted star and gem-cut
 * core. A dark ground, a faint wash and then lines in [brush], drawn on the 64 unit grid.
 */
private fun DrawScope.drawPlate(g: SealGeometry, origin: Offset, side: Float, brush: Brush) {
    val k = side / 64f
    val line = maxOf(1.4f * k, 1.dp.toPx())
    translate(origin.x, origin.y) {
        scale(k, Offset.Zero) {
            val outer = g.outer.toPath()
            val prism = g.core.isNotEmpty()
            drawPath(outer, PlateGround, style = Fill)
            drawPath(outer, brush, alpha = if (prism) 0.26f else METAL_WASH, style = Fill)
            drawPath(outer, brush, style = Stroke(width = line / k, join = StrokeJoin.Round))
            if (g.rim.isNotEmpty()) {
                drawPath(g.rim.toPath(), brush, alpha = 0.55f, style = Stroke(width = line / k * 0.6f, join = StrokeJoin.Round))
            }
            g.rivets.forEach { ring(Offset(it.x, it.y), 1.3f, brush, 0.8f, line / k * 0.45f) }
            if (prism) {
                val core = g.core.toPath()
                drawPath(core, PlateGround, style = Fill)
                drawPath(core, brush, style = Stroke(width = line / k * 0.7f, join = StrokeJoin.Round))
                val spokes = Path().also { p -> g.spokes.forEach { (a, b) -> p.moveTo(a.x, a.y); p.lineTo(b.x, b.y) } }
                drawPath(spokes, brush, alpha = 0.7f, style = Stroke(width = line / k * 0.6f, cap = StrokeCap.Round))
            }
        }
    }
}

/**
 * A house relic's art. [owned] false draws the silhouette of a relic not yet drawn. [ringed] sets it on
 * its tier's plate, for the large uses: the reveal, the strongest relic and the Today centrepiece.
 * [animate] lets a Masterwork prism drift; callers pass false whenever motion is off.
 */
@Composable
fun HouseRelicSigil(
    relicId: String,
    tier: RewardRarity,
    modifier: Modifier = Modifier,
    owned: Boolean = true,
    ringed: Boolean = false,
    animate: Boolean = false,
) {
    val spec = RELIC_SPECS[relicId] ?: return
    val house = RelicHouse.byId(relicId.substringBefore('.'))
    val metal = Metal.of(tier)
    val drift = rememberPrismDrift(1f, animate && owned && metal.prism)
    val plate = remember(tier) { sealGeometry(tier.seal()) }
    Canvas(modifier.clearAndSetSemantics {}) {
        val side = minOf(size.width, size.height)
        val c = Offset(size.width / 2f, size.height / 2f)
        val origin = Offset(c.x - side / 2f, c.y - side / 2f)
        val shift = drift?.value ?: 0f
        if (!owned) {
            drawSigil(spec, house, c, side / 100f, SolidColor(Silhouette))
            return@Canvas
        }
        if (ringed) drawPlate(plate, origin, side, metal.span(64f, shift * 64f))
        val box = if (ringed) side * ON_PLATE else side
        val boxOrigin = Offset(c.x - box / 2f, c.y - box / 2f)
        drawSigil(spec, house, c, box / 100f, metal.span(box, shift * box, boxOrigin))
    }
}

/**
 * A tier's plate with [content] centred on it: the echo count of an inscription, where a relic would be.
 * [animate] lets a Masterwork prism drift.
 */
@Composable
fun TierPlate(
    tier: RewardRarity,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val metal = Metal.of(tier)
    val drift = rememberPrismDrift(1f, animate && metal.prism)
    val plate = remember(tier) { sealGeometry(tier.seal()) }
    Box(modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val side = minOf(size.width, size.height)
            val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)
            drawPlate(plate, origin, side, metal.span(64f, (drift?.value ?: 0f) * 64f))
        }
        content()
    }
}

/**
 * A house's emblem for its tile: the house's rim motif around its own star, in [color]. A [complete]
 * house wears the Fabled metal instead, flat and diagonal like every other gold on the screen.
 */
@Composable
fun HouseEmblem(house: RelicHouse, color: Color, modifier: Modifier = Modifier, complete: Boolean = false) {
    val spec = HOUSE_EMBLEMS.getValue(house)
    Canvas(modifier.clearAndSetSemantics {}) {
        val side = minOf(size.width, size.height)
        val c = Offset(size.width / 2f, size.height / 2f)
        val brush = if (complete) Metal.Fabled.span(side, 0f, Offset(c.x - side / 2f, c.y - side / 2f)) else SolidColor(color)
        drawSigil(spec, house, c, side / 100f, brush)
    }
}

/** A tier's small marker for a list of rarities: a ring in its metal over a faint wash. */
@Composable
fun TierDot(tier: RewardRarity, modifier: Modifier = Modifier) {
    val metal = Metal.of(tier)
    Canvas(modifier.clearAndSetSemantics {}) {
        val side = minOf(size.width, size.height)
        val c = Offset(size.width / 2f, size.height / 2f)
        val ring = Path().apply { addOval(Rect(c, side * 0.4f)) }
        val brush = metal.span(side, 0f, Offset(c.x - side / 2f, c.y - side / 2f))
        drawPath(ring, brush, alpha = METAL_WASH, style = Fill)
        drawPath(ring, brush, style = Stroke(side * 0.1f))
    }
}
