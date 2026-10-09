package com.ironvellum.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import com.ironvellum.app.domain.RewardRarity
import com.ironvellum.app.domain.TitleRarity

/*
 * The one way a tier's metal is drawn: a FLAT DIAGONAL linear gradient from a lighter tone through the
 * tier's [RarityTint] to a deeper one, on outlines, rims and a faint ~12% wash. No solid metal fills, no
 * bevel, highlight, sheen or glint. DeedSeal is the reference; relic and crest art and the collection
 * tiles take the same brush from here so every tier reads alike wherever it appears.
 */

/** Where a tier's stops sit along the diagonal. */
private val FlatAt = floatArrayOf(0f, 0.5f, 1f)

/** The prism's stops: lilac, a cool blue at 35%, a rose at 65%, back to lilac so a drift loops seamlessly. */
private val PrismAt = floatArrayOf(0f, 0.35f, 0.65f, 1f)

/** The strength of the wash a metal lays inside its outline. */
const val METAL_WASH = 0.12f

/** One metal ladder rung: its colour stops and whether it is the Masterwork prism. */
class Metal internal constructor(
    private val colors: List<Color>,
    private val at: FloatArray,
    val prism: Boolean = false,
) {

    /** Stops as a Brush across the box it is drawn in, corner to corner. */
    fun bounds(): Brush = Brush.linearGradient(colorStops = stops())

    /**
     * Stops as a Brush across a square of [extent] units at [origin], slid [shift] units along the diagonal.
     * Repeated, so a prism shifted over 0..[extent] wraps without a jump.
     */
    fun span(extent: Float, shift: Float = 0f, origin: Offset = Offset.Zero): Brush = Brush.linearGradient(
        colorStops = stops(),
        start = Offset(origin.x + shift, origin.y + shift),
        end = Offset(origin.x + shift + extent, origin.y + shift + extent),
        tileMode = TileMode.Repeated,
    )

    /** The tone the metal is named by: a thin line or a glyph that cannot carry a gradient. */
    val tone: Color get() = colors[if (prism) 0 else 1]

    private fun stops(): Array<Pair<Float, Color>> = Array(colors.size) { at[it] to colors[it] }

    companion object {
        val Common = Metal(listOf(Color(0xFFA9AEB5), RarityTint.Iron, Color(0xFF6F747B)), FlatAt)
        val Rare = Metal(listOf(Color(0xFFE0A877), RarityTint.Bronze, Color(0xFFA86F44)), FlatAt)
        val Fabled = Metal(listOf(Color(0xFFE8C672), RarityTint.Gold, Color(0xFFB88A30)), FlatAt)
        val Masterwork = Metal(
            listOf(RarityTint.Prismatic, Color(0xFF9DD5F0), Color(0xFFF0B6E4), RarityTint.Prismatic),
            PrismAt,
            prism = true,
        )

        fun of(rarity: TitleRarity): Metal = when (rarity) {
            TitleRarity.Common -> Common
            TitleRarity.Rare -> Rare
            TitleRarity.Epic -> Fabled
            TitleRarity.Masterwork -> Masterwork
        }

        fun of(rarity: RewardRarity): Metal = when (rarity) {
            RewardRarity.Common -> Common
            RewardRarity.Rare -> Rare
            RewardRarity.Epic -> Fabled
            RewardRarity.Masterwork -> Masterwork
        }
    }
}

/** One slow drift of the prism, in milliseconds. */
private const val PRISM_DRIFT_MS = 18_000

/**
 * How far along its diagonal a Masterwork prism has drifted, 0..[extent], or null when [enabled] is false
 * (no transition is then created). Read the value inside a draw lambda, never in composition.
 */
@Composable
fun rememberPrismDrift(extent: Float, enabled: Boolean): State<Float>? =
    if (enabled) {
        rememberInfiniteTransition(label = "prism").animateFloat(
            0f, extent, infiniteRepeatable(tween(PRISM_DRIFT_MS, easing = LinearEasing)), label = "shift",
        )
    } else {
        null
    }
