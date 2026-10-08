package com.ironvellum.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.Gacha
import com.ironvellum.app.domain.RelicOutcome
import com.ironvellum.app.domain.RelicReveal
import com.ironvellum.app.domain.RewardRarity
import com.ironvellum.app.domain.VaultSlot
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.social.crestFrameTreatment
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.DotShape
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.RarityTint
import com.ironvellum.app.ui.theme.TileShape
import com.ironvellum.app.ui.theme.inkArc
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkDot

/*
 * The two celebrations of the Veil: a relic drawn (its sigil on its rarity plate, what it does, how
 * its house is coming on) and a crest earned (the drawn crest, not a stand-in seal). Both are step
 * pages of the achievement overlay and share its clock, its dock and its motion gate.
 */

private const val REVEAL_END = 2_000L
private const val SEAL_AT = 550L

/** The relic page: tag, plate, name, tier and house, effect, the house's four cells, set progress. */
@Composable
internal fun RelicRevealPage(
    step: Int,
    steps: Int,
    reveal: RelicReveal,
    slots: List<VaultSlot>,
    onContinue: () -> Unit,
) {
    val motion = animatorsOn(LocalContext.current)
    val t = rememberClock(REVEAL_END, motion)
    Haptics(motion, *inscribedBeats(reveal.tier).toTypedArray())
    val tint = RarityTint.of(reveal.tier)
    StepPage(step = step, steps = steps, dock = { CelebrationDock(primary = "Continue", onPrimary = onContinue) }) {
        Text(
            "Inscribed",
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.5.sp,
            color = IronvellumColors.SovereignGold,
            modifier = Modifier.reveal(t >= 100, motion),
        )
        Spacer(Modifier.height(16.dp))
        HouseRelicSigil(
            relicId = reveal.relic.id,
            tier = reveal.tier,
            ringed = true,
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer {
                    alpha = phase(t, 0, 400)
                    val s = stamp(t, SEAL_AT)
                    scaleX = s
                    scaleY = s
                },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            reveal.name,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
            color = IronvellumColors.Ink,
            modifier = Modifier.reveal(t >= 650, motion),
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.reveal(t >= 800, motion)) {
            Text(rarityWord(reveal.tier), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = tint)
            Text(" · ${reveal.relic.house.title}", style = MaterialTheme.typography.bodyMedium, color = Dim)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            outcomeLine(reveal),
            style = MaterialTheme.typography.bodyMedium,
            color = Dim,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp).reveal(t >= 950, motion),
        )
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().reveal(t >= 1_100, motion), horizontalAlignment = Alignment.CenterHorizontally) {
            SetStrip(slots, justDrawn = reveal.relic.id, description = reveal.setProgress)
            Spacer(Modifier.height(10.dp))
            Text(reveal.setProgress, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = IronvellumColors.Ink)
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp).reveal(t >= 1_300, motion),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            reveal.bonusReached?.let {
                Text("Set bonus reached · $it", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.SovereignGold, textAlign = TextAlign.Center)
            }
            reveal.nextBonus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Dim, textAlign = TextAlign.Center) }
            Text("Relic vault · ${reveal.vaultProgress}", style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** What the draw did, in the app's voice. Every relic lifts the whole rate; a duplicate refines or pays echoes. */
private fun outcomeLine(reveal: RelicReveal): String = when (reveal.outcome) {
    RelicOutcome.New -> reveal.effect
    RelicOutcome.Refined -> "${reveal.effect} A duplicate refined it to ×${"%.2f".fmt(reveal.multiplier)}."
    RelicOutcome.Maxed -> "Already at the best its tier allows, so it paid ${reveal.echoes} ${if (reveal.echoes == 1) "echo" else "echoes"} instead."
}

/** The house's four cells: held in their metal, the one just drawn raised, the rest silhouettes. */
@Composable
internal fun SetStrip(slots: List<VaultSlot>, justDrawn: String?, description: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        slots.forEach { slot ->
            val held = slot.isOwned
            val tint = if (held) RarityTint.of(slot.tier) else IronvellumColors.Rune
            val fresh = slot.relic.id == justDrawn
            Box(
                Modifier
                    .size(52.dp)
                    .background(if (fresh) IronvellumColors.VaultHigh else if (held) IronvellumColors.Vault else IronvellumColors.Abyss, TileShape)
                    .inkBorder(if (fresh) tint else if (held) tint.copy(alpha = 0.5f) else IronvellumColors.Rune, TileShape, 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                HouseRelicSigil(slot.relic.id, slot.tier, Modifier.size(38.dp), owned = held)
            }
        }
    }
}

/** The crest page: the drawn crest on a plate in its own frame colour, its name and where it came from. */
@Composable
internal fun CrestRevealPage(
    step: Int,
    steps: Int,
    crestId: String,
    line: String?,
    dock: @Composable () -> Unit,
) {
    val motion = animatorsOn(LocalContext.current)
    val t = rememberClock(REVEAL_END, motion)
    Haptics(motion, *inscribedBeats(RewardRarity.Rare).toTypedArray())
    val name = Gacha.CREST_FRAMES.firstOrNull { it.id == crestId }?.name?.replace(" Crest", " crest") ?: crestId
    StepPage(step = step, steps = steps, dock = dock) {
        Text(
            "Crest earned",
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.5.sp,
            color = IronvellumColors.SovereignGold,
            modifier = Modifier.reveal(t >= 100, motion),
        )
        Spacer(Modifier.height(20.dp))
        CrestPlate(
            crestId,
            Modifier
                .size(240.dp)
                .graphicsLayer {
                    alpha = phase(t, 0, 400)
                    val s = stamp(t, SEAL_AT)
                    scaleX = s
                    scaleY = s
                },
        )
        Spacer(Modifier.height(18.dp))
        Text(
            name.replaceFirstChar { it.uppercase() },
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
            color = IronvellumColors.Ink,
            modifier = Modifier.reveal(t >= 650, motion),
        )
        if (line != null) {
            Spacer(Modifier.height(4.dp))
            Text(line, style = MaterialTheme.typography.bodyMedium, color = Dim, textAlign = TextAlign.Center, modifier = Modifier.reveal(t >= 800, motion))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "This one is yours to keep, whatever you wear.",
            style = MaterialTheme.typography.bodyMedium,
            color = Dim,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp).reveal(t >= 950, motion),
        )
    }
}

/**
 * A crest on its plate: two rings in the frame's own colour round a Vault disc, the drawn mark on it.
 * Used by the reveal and the collection, so a crest looks the same where it is won and where it is kept.
 * [owned] false sinks it to a whisper, the way the old rail did for a locked frame.
 */
@Composable
fun CrestPlate(frameId: String, modifier: Modifier = Modifier, owned: Boolean = true) {
    val ring = crestFrameTreatment(frameId)?.frameColor ?: IronvellumColors.SovereignGold
    Box(modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if (owned) {
            Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(ring.copy(alpha = 0.16f), Color.Transparent)), DotShape))
        }
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val u = minOf(size.width, size.height) / 260f
            val tone = if (owned) ring else IronvellumColors.Rune
            inkDot(c, 116f * u, IronvellumColors.Vault)
            inkArc(c, 126f * u, 0f, 360f, tone.copy(alpha = 0.6f), 1.5f * u)
            inkArc(c, 116f * u, 0f, 360f, tone.copy(alpha = 0.75f), 3f * u)
            inkArc(c, 104f * u, 0f, 360f, tone.copy(alpha = 0.3f), 1f * u)
        }
        CrestMark(
            frameId,
            Modifier
                .fillMaxSize(0.6f)
                .graphicsLayer { alpha = if (owned) 1f else 0.4f },
        )
    }
}
