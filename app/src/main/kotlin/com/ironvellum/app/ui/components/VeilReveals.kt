package com.ironvellum.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ironvellum.app.domain.Crests
import com.ironvellum.app.domain.DrawChange
import com.ironvellum.app.domain.Gacha
import com.ironvellum.app.domain.RelicOutcome
import com.ironvellum.app.domain.RelicReveal
import com.ironvellum.app.domain.Reward
import com.ironvellum.app.domain.RewardRarity
import com.ironvellum.app.domain.VaultSlot
import com.ironvellum.app.domain.VaultState
import com.ironvellum.app.domain.fmt
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.METAL_WASH
import com.ironvellum.app.ui.theme.Metal
import com.ironvellum.app.ui.theme.RarityTint
import com.ironvellum.app.ui.theme.TileShape
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkDot
import com.ironvellum.app.ui.theme.rememberPrismDrift

/*
 * The celebrations of the Veil: a relic drawn, a crest earned, echoes paid. Each is one page of a
 * full-screen dialog that shares the achievement overlay's clock, dock and motion gate, and ends in
 * the Veil's own dock: "Inscribe again (N left)" while more are banked, and "Done". A relic or echoes
 * page also says what the draw did to the Veil: the rate before and after, the strongest relic, the
 * house or the echo run. Every number comes from [DrawChange], measured by the rules and not guessed here.
 */

private const val REVEAL_END = 2_000L
private const val SEAL_AT = 550L

/** One inscription's reveal: the page it tells, what the draw changed, and a serial so a second draw restarts the clocks. */
internal data class InscribedDraw(val page: CelebrationPage.Inscribed, val change: DrawChange, val serial: Int)

/** The reveal of [change] against the vault AFTER the draw; [crestsOwned] is how many crests are held now. */
internal fun inscribedDraw(change: DrawChange, vaultAfter: VaultState?, crestsOwned: Int?, serial: Int): InscribedDraw =
    InscribedDraw(inscribedPage(change.result, vaultAfter, crestsOwned), change, serial)

/**
 * The Veil's reveal over the screen: one page for [draw], restarted for each draw. [left] is what is still
 * banked: with some, the dock offers another draw; with none, only Done (or, for a crest not yet worn,
 * Wear crest). The Veil stays behind it and picks up its landing when this closes.
 */
@Composable
internal fun InscribedOverlay(
    draw: InscribedDraw,
    left: Int,
    onAgain: () -> Unit,
    onDone: () -> Unit,
    wornCrestId: String?,
    onWearCrest: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(IronvellumColors.Abyss),
        ) {
            // Keyed so each draw starts its own clock and haptics.
            key(draw.serial) {
                val page = draw.page
                val again = "Inscribe again ($left left)"
                val dock: @Composable () -> Unit = {
                    if (left > 0) {
                        CelebrationDock(primary = again, onPrimary = onAgain, link = "Done", onLink = onDone)
                    } else {
                        CelebrationDock(primary = "Done", onPrimary = onDone)
                    }
                }
                when {
                    page.relic != null && page.houseSlots.isNotEmpty() ->
                        RelicRevealPage(draw.change, page.relic, page.houseSlots, dock)
                    page.crestId != null -> {
                        val id = page.crestId
                        val canWear = id != wornCrestId
                        CrestRevealPage(
                            step = 0,
                            steps = 1,
                            crestId = id,
                            line = page.crestLine,
                            dock = {
                                when {
                                    left > 0 -> CelebrationDock(primary = again, onPrimary = onAgain, link = "Done", onLink = onDone)
                                    canWear -> CelebrationDock(
                                        primary = "Wear crest",
                                        onPrimary = {
                                            onWearCrest(id)
                                            onDone()
                                        },
                                        link = "Done",
                                        onLink = onDone,
                                    )
                                    else -> CelebrationDock(primary = "Done", onPrimary = onDone)
                                }
                            },
                        )
                    }
                    else -> EchoesRevealPage(draw, dock)
                }
            }
        }
    }
}

/**
 * The relic page: tag, the relic on its tier's plate, name, tier and house, what it does, then what the
 * draw changed: the rate, the strongest relic and how its house is coming on.
 */
@Composable
internal fun RelicRevealPage(
    change: DrawChange,
    reveal: RelicReveal,
    slots: List<VaultSlot>,
    dock: @Composable () -> Unit,
) {
    val motion = animatorsOn(LocalContext.current)
    val t = rememberClock(REVEAL_END, motion)
    Haptics(motion, *inscribedBeats(reveal.tier).toTypedArray())
    val tint = RarityTint.of(reveal.tier)
    StepPage(step = 0, steps = 1, dock = dock) {
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
            animate = motion,
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer {
                    alpha = phase(t, 0, 400)
                    val s = stamp(t, SEAL_AT)
                    scaleX = s
                    scaleY = s
                },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            reveal.name,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
            color = IronvellumColors.Ink,
            modifier = Modifier.reveal(t >= 650, motion),
        )
        Row(Modifier.padding(top = 2.dp).reveal(t >= 800, motion)) {
            Text(rarityWord(reveal.tier), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = tint)
            Text(" · ${reveal.relic.house.title}", style = MaterialTheme.typography.bodyMedium, color = Dim)
        }
        Text(
            outcomeLine(reveal),
            style = MaterialTheme.typography.bodyMedium,
            color = Dim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 320.dp).reveal(t >= 950, motion),
        )
        ChangePanel(Modifier.padding(top = 16.dp).reveal(t >= 1_100, motion)) {
            RateRow(change)
            InkDivider()
            ChangeRow("Strongest relic") { ChangeValue(strongestText(change)) }
            InkDivider()
            HouseRow(change, reveal, slots)
        }
    }
}

/** What the draw did, in the app's voice. Every relic lifts the whole rate; a duplicate refines or pays echoes. */
private fun outcomeLine(reveal: RelicReveal): String = when (reveal.outcome) {
    RelicOutcome.New -> reveal.effect
    RelicOutcome.Refined -> "${reveal.effect} A duplicate refined it to ×${"%.2f".fmt(reveal.multiplier)}."
    RelicOutcome.Maxed -> "Already at the best its tier allows, so it paid ${reveal.echoes} ${if (reveal.echoes == 1) "echo" else "echoes"} instead."
}

/** "Still Hourglass of Vigil", "New: Crown of Iron", or the first relic's name. */
private fun strongestText(change: DrawChange): String {
    val after = change.strongestAfter ?: return "None yet"
    return when {
        change.strongestBefore == null -> "${after.name}, your first"
        change.strongestChanged -> "New: ${after.name}"
        else -> "Still ${after.name}"
    }
}

/**
 * The echoes page: the count on a plate of the draw's rarity (the number is the object), what it
 * did to the rate, and the run pity is counting. A maxed relic's echoes are told on the relic page.
 */
@Composable
internal fun EchoesRevealPage(draw: InscribedDraw, dock: @Composable () -> Unit) {
    val motion = animatorsOn(LocalContext.current)
    val t = rememberClock(REVEAL_END, motion)
    val rarity = draw.page.rarity
    Haptics(motion, *inscribedBeats(rarity).toTypedArray())
    val change = draw.change
    val tint = RarityTint.of(rarity)
    StepPage(step = 0, steps = 1, dock = dock) {
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
        TierPlate(
            rarity,
            animate = motion,
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer {
                    alpha = phase(t, 0, 400)
                    val s = stamp(t, SEAL_AT)
                    scaleX = s
                    scaleY = s
                },
        ) {
            Text(
                if (change.result.reward is Reward.Figures) count(change.echoesPaid) else draw.page.name,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 64.sp,
                textAlign = TextAlign.Center,
                style = TextStyle(fontFeatureSettings = "tnum"),
                color = IronvellumColors.Ink,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            draw.page.name,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
            color = IronvellumColors.Ink,
            modifier = Modifier.reveal(t >= 650, motion),
        )
        Row(Modifier.padding(top = 2.dp).reveal(t >= 800, motion)) {
            Text(rarityWord(rarity), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = tint)
            Text(" · added to your echoes, ${count(change.echoesAfter)} in all", style = MaterialTheme.typography.bodyMedium, color = Dim)
        }
        ChangePanel(Modifier.padding(top = 18.dp).reveal(t >= 1_000, motion)) {
            RateRow(change)
            InkDivider()
            RunRow(change.echoRun)
        }
    }
}

/** The line under the echo run: what pity does next. */
private fun runNote(run: Int): String =
    if (run >= Gacha.PITY_AFTER) {
        "Your next inscription is a relic or a crest."
    } else {
        "After ${Gacha.PITY_AFTER}, your next inscription is a relic or a crest."
    }

/** The echo run as [Gacha.PITY_AFTER] squares, filled as they count, and what it means. */
@Composable
private fun RunRow(run: Int) {
    val filled = run.coerceIn(0, Gacha.PITY_AFTER)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.clearAndSetSemantics { contentDescription = "$filled of ${Gacha.PITY_AFTER} echo draws in a row" },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(Gacha.PITY_AFTER) { i ->
                val on = i < filled
                Box(
                    Modifier
                        .size(22.dp)
                        .background(if (on) IronvellumColors.Rune else Color.Transparent, TileShape)
                        .inkBorder(if (on) IronvellumColors.InkMuted else IronvellumColors.Bracket, TileShape, 2.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                "$run echo ${if (run == 1) "draw" else "draws"} in a row",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = IronvellumColors.Ink,
            )
            Text(runNote(run), style = MaterialTheme.typography.bodySmall, color = Dim)
        }
    }
}

/** The panel "what this draw changed" sits in: a Vault surface with a Rune border, its rows divided by rules. */
@Composable
private fun ChangePanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    InkPanel(modifier.fillMaxWidth().widthIn(max = 420.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
        content()
    }
}

/** A row of the panel: the label muted on the left, the value on the right. */
@Composable
private fun ChangeRow(label: String, value: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Dim)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { value() }
    }
}

@Composable
private fun ChangeValue(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, textAlign = TextAlign.End)
}

/** "148.9 → 156.3 per hour +7.4", or "148.9 per hour, unchanged". */
@Composable
private fun RateRow(change: DrawChange) {
    ChangeRow("Rate") {
        val moved = change.rateDelta != 0.0
        Text(
            buildAnnotatedString {
                if (moved) {
                    append("${"%.1f".fmt(change.rateBefore)} → ${"%.1f".fmt(change.rateAfter)} per hour ")
                    withStyle(SpanStyle(color = IronvellumColors.SovereignGold, fontWeight = FontWeight.SemiBold)) {
                        append("${if (change.rateDelta > 0) "+" else "−"}${"%.1f".fmt(kotlin.math.abs(change.rateDelta))}")
                    }
                } else {
                    append("${"%.1f".fmt(change.rateAfter)} per hour, unchanged")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.Ink,
            textAlign = TextAlign.End,
        )
    }
}

/** The house: its four cells, then the bonus this draw reached (gold) or how far the next one is. */
@Composable
private fun HouseRow(change: DrawChange, reveal: RelicReveal, slots: List<VaultSlot>) {
    val line = change.bonusLine
    val head = line?.substringBefore(" · ") ?: reveal.setProgress
    val sub = line?.substringAfter(" · ", "")?.replaceFirstChar { it.uppercase() }?.takeIf { it.isNotEmpty() } ?: reveal.nextBonus
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SetStrip(slots, justDrawn = reveal.relic.id, description = reveal.setProgress)
        Column(Modifier.weight(1f)) {
            Text(
                head,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (line != null) IronvellumColors.SovereignGold else IronvellumColors.Ink,
            )
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Dim)
        }
    }
}

/** The house's four cells: held in their metal, the one just drawn raised, the rest silhouettes. */
@Composable
internal fun SetStrip(slots: List<VaultSlot>, justDrawn: String?, description: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        slots.forEach { slot ->
            val held = slot.isOwned
            val fresh = slot.relic.id == justDrawn
            Box(
                Modifier
                    .size(40.dp)
                    .background(if (fresh) IronvellumColors.VaultHigh else if (held) IronvellumColors.Vault else UnheldGround, TileShape)
                    .then(
                        if (held) {
                            Modifier.inkBorder(Metal.of(slot.tier).bounds(), TileShape, 1.dp)
                        } else {
                            Modifier.inkBorder(UnheldEdge, TileShape, 1.dp)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                HouseRelicSigil(slot.relic.id, slot.tier, Modifier.size(30.dp), owned = held)
            }
        }
    }
}

/** A cell not yet held: a dark ground and a quiet edge. */
internal val UnheldGround = Color(0xFF121211)
internal val UnheldEdge = Color(0xFF232320)

/** The crest page: the drawn crest on a plate in its tier's metal, its name and where it came from. */
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
    val def = Crests.byId(crestId)
    val name = def?.sentenceName ?: crestId
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
            animate = motion,
            modifier = Modifier
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
        if (def != null) {
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.widthIn(max = 320.dp).reveal(t >= 900, motion),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "VEIL PERK · ${def.kind.label.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = Dim,
                )
                Text(
                    def.perk,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = IronvellumColors.Ink,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(def.text, style = MaterialTheme.typography.bodyMedium, color = Dim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
            }
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
 * A crest medallion: a dark plate ringed in the crest's flat gradient, its drawn mark in the mark's own
 * ramp ([crestLook]). Used by the reveal, the collection and the sheet, so a crest looks the same where it
 * is won and where it is kept. [owned] false sinks it to a flat muted silhouette; [animate] lets a prism
 * drift. An id the catalogue does not know draws nothing.
 */
@Composable
fun CrestPlate(frameId: String, modifier: Modifier = Modifier, owned: Boolean = true, animate: Boolean = false) {
    val look = crestLook(frameId) ?: return
    val prism = look.art.prism || look.ring.prism
    val drift = rememberPrismDrift(1f, animate && owned && prism)
    Canvas(modifier.clearAndSetSemantics {}) {
        val side = minOf(size.width, size.height)
        val origin = Offset((size.width - side) / 2f, (size.height - side) / 2f)
        // 1.5dp on a small medallion, 2.5dp on a large one, as drawn.
        val ring = (if (side < 80.dp.toPx()) 1.5.dp else 2.5.dp).toPx()
        drawCrestMedallion(frameId, look, origin, side, ring, owned, drift?.value ?: 0f)
    }
}
