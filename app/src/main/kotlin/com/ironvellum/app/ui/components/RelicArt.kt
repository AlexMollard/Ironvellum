package com.ironvellum.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.ironvellum.app.domain.RelicHouse
import com.ironvellum.app.domain.RewardRarity
import com.ironvellum.app.ui.theme.DotShape
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.RarityTint
import com.ironvellum.app.ui.theme.inkArc
import com.ironvellum.app.ui.theme.inkDot
import com.ironvellum.app.ui.theme.inkStroke
import kotlin.math.cos
import kotlin.math.sin

/*
 * The art of the sixteen house relics, drawn as Compose paths from the geometry of the approved
 * sigils: concentric setting rings, ruled spokes, one filled and stroked star, an ink-dot core, all
 * in a 100 unit square. A relic is `house.form`, so its art is a frozen row of the table below and
 * never a hash of its name. The house sets the motif (the mark on the rim), the form sets the shape
 * (points, depth, turn, rings, spokes). Rarity is not in the sigil: it is the metal the sigil is
 * tinted in and the ring it sits on (docs/DESIGN.md, "rarity, not accents").
 */

/** One sigil's geometry. A relic's row never changes once released; add rows, never edit them. */
internal data class RelicSpec(
    /** Tips of the star. */
    val points: Int,
    /** Depth of the star's valleys as a share of its tip radius: low is sharp, high is round. */
    val inner: Float,
    /** The whole figure's turn, in degrees. */
    val rotation: Float,
    /** Concentric setting rings, 1 to 3. */
    val rings: Int,
    /** Ruled spokes behind the star. */
    val spokes: Int,
    /** The core dot's radius in sigil units. */
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

private fun DrawScope.at(c: Offset, r: Float, deg: Float, u: Float): Offset {
    val a = Math.toRadians(deg.toDouble())
    return Offset(c.x + r * u * cos(a).toFloat(), c.y + r * u * sin(a).toFloat())
}

/**
 * The house's mark on the rim: Iron four bolts, Vigil twelve dial ticks, Craft eight studs, Return
 * three beacons. Drawn before the figure turns, so every relic of a house wears it the same way up.
 */
private fun DrawScope.drawRimMotif(house: RelicHouse?, c: Offset, u: Float, color: Color) {
    when (house) {
        RelicHouse.Iron -> repeat(4) {
            val a = it * 90f
            inkStroke(at(c, RIM - 3.5f, a, u), at(c, RIM + 3f, a, u), color.copy(alpha = 0.75f), 4.2f * u)
        }
        RelicHouse.Vigil -> repeat(12) {
            val a = it * 30f
            inkStroke(at(c, RIM - 2.5f, a, u), at(c, RIM + 1f, a, u), color.copy(alpha = 0.6f), 1.5f * u)
        }
        RelicHouse.Craft -> repeat(8) {
            inkDot(at(c, RIM, it * 45f + 22.5f, u), 2.1f * u, color.copy(alpha = 0.7f))
        }
        RelicHouse.Return -> repeat(3) {
            val a = it * 120f - 90f
            inkDot(at(c, RIM, a, u), 3f * u, color.copy(alpha = 0.8f))
        }
        null -> Unit
    }
}

/** One sigil, centred on [c], [u] pixels to a unit. */
private fun DrawScope.drawSigil(spec: RelicSpec, house: RelicHouse?, c: Offset, u: Float, color: Color) {
    drawRimMotif(house, c, u, color)
    rotate(degrees = spec.rotation, pivot = c) {
        repeat(spec.rings) { ring ->
            inkArc(c, (RIM - 8f * ring) * u, 0f, 360f, color.copy(alpha = 0.22f + 0.12f * ring), 2.2f * u)
        }
        repeat(spec.spokes) { i ->
            val a = 360f * i / spec.spokes - 7f
            inkStroke(c, at(c, 43f, a, u), color.copy(alpha = 0.35f), 1.6f * u)
        }
        val star = Path()
        val vertices = spec.points * 2
        for (i in 0 until vertices) {
            val r = if (i % 2 == 0) TIP else TIP * spec.inner
            val p = at(c, r, -90f + 360f * i / vertices, u)
            if (i == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
        }
        star.close()
        drawPath(star, color.copy(alpha = 0.16f))
        drawPath(star, color.copy(alpha = 0.85f), style = Stroke(width = 3.2f * u, join = StrokeJoin.Round))
        inkDot(c, spec.core * u, color)
    }
}

/**
 * The plate a relic sits on, from the quietest tier to the richest: Common one ring, Rare two, Fabled
 * a ring of beads, Masterwork a third ring. Outside the figure's own 75% of the box.
 */
private fun DrawScope.drawPlate(tier: RewardRarity, c: Offset, side: Float, color: Color) {
    val u = side / 160f
    inkArc(c, 77f * u, 0f, 360f, color.copy(alpha = if (tier == RewardRarity.Common) 0.45f else 0.6f), 1.5f * u)
    if (tier == RewardRarity.Common) return
    inkArc(c, 71f * u, 0f, 360f, color.copy(alpha = 0.3f), 1f * u)
    if (tier >= RewardRarity.Epic) {
        repeat(40) { inkDot(at(c, 71f, it * 9f, u), 1.4f * u, color.copy(alpha = 0.55f)) }
    }
    if (tier == RewardRarity.Masterwork) inkArc(c, 65f * u, 0f, 360f, color.copy(alpha = 0.3f), 1f * u)
}

/** Turns [content] one revolution in 48 s while [spin]; with it off no transition is ever created. */
@Composable
private fun Turning(spin: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    if (!spin) {
        Box(modifier) { content() }
        return
    }
    val turn by rememberInfiniteTransition(label = "relicTurn").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(48_000, easing = LinearEasing), RepeatMode.Restart),
        label = "relicTurnDegrees",
    )
    Box(modifier.graphicsLayer { rotationZ = turn }) { content() }
}

/**
 * A house relic's art. [owned] false draws the silhouette of a relic not yet drawn. [ringed] adds the
 * rarity plate and, for Fabled and Masterwork, a soft static glow behind it: for the large uses, the
 * reveal, the active relic and the centrepiece. [spin] is the slow turn of the one relic setting the
 * rate; callers pass false whenever motion is off.
 */
@Composable
fun HouseRelicSigil(
    relicId: String,
    tier: RewardRarity,
    modifier: Modifier = Modifier,
    owned: Boolean = true,
    ringed: Boolean = false,
    spin: Boolean = false,
) {
    val spec = RELIC_SPECS[relicId] ?: return
    val house = RelicHouse.byId(relicId.substringBefore('.'))
    val color = if (owned) RarityTint.of(tier) else IronvellumColors.Rune
    Box(modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if (ringed && owned && RarityTint.glows(tier)) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.radialGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent)), DotShape),
            )
        }
        Turning(spin, Modifier.matchParentSize()) {
            Canvas(Modifier.matchParentSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val side = minOf(size.width, size.height)
                if (ringed && owned) drawPlate(tier, c, side, color)
                val u = (if (ringed) side * 0.75f else side) / 100f
                drawSigil(spec, house, c, u, color)
            }
        }
    }
}

/** A house's emblem for its tile: the house's rim motif around its own star. [color] is the tile's ink. */
@Composable
fun HouseEmblem(house: RelicHouse, color: Color, modifier: Modifier = Modifier) {
    val spec = HOUSE_EMBLEMS.getValue(house)
    Canvas(modifier.clearAndSetSemantics {}) {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawSigil(spec, house, c, minOf(size.width, size.height) / 100f, color)
    }
}
