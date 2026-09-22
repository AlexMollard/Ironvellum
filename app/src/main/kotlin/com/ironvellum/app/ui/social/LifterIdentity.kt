package com.ironvellum.app.ui.social

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.TitleRarity
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.InkPlateShape
import androidx.compose.ui.platform.LocalDensity
import com.ironvellum.app.ui.theme.IronvellumColors

/** One lifter's identity, rendered the same way on every social surface. */
internal enum class IdentitySize { Compact, Standard, Hero }

/**
 * The single identity row: avatar crest + name + worn title + LV chip on one
 * line. Name lives in a weight(1f) slot so long display names ellipsize
 * instead of pushing the trailing content off screen; avatar, LV chip and
 * trailing slot all stay at intrinsic width.
 */
@Composable
internal fun IdentityRow(
    displayName: String,
    userId: String,
    wornTitle: String?,
    level: Int?,
    size: IdentitySize = IdentitySize.Standard,
    isMe: Boolean = false,
    avatarUrl: String? = null,
    // The equipped title's id, not its display name: the crest resolves the
    // rarity from the catalogue so the badge reflects WHAT was earned.
    titleId: String? = null,
    // Equipped gacha crest frame; null = today's rarity/level rendering.
    frameId: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val badgeSize = when (size) {
        IdentitySize.Compact -> 32.dp
        IdentitySize.Standard -> 40.dp
        IdentitySize.Hero -> 56.dp
    }
    val nameSize = when (size) {
        IdentitySize.Compact -> 13.sp
        IdentitySize.Standard -> 16.sp
        IdentitySize.Hero -> 22.sp
    }
    // The worn title always sits directly under the name. An earlier version
    // dropped it below the whole row at Hero size, which pushed it far from the
    // name it belongs to; the column is kept wide instead by callers putting
    // bulky chips elsewhere (the feed's ally chip lives on the action row).
    Column(
        modifier = modifier
            // Tappable only when the caller asked for it, so a plain status row
            // never reads as a button.
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LifterAvatar(
                userId = userId,
                displayName = displayName,
                size = badgeSize,
                isMe = isMe,
                avatarUrl = avatarUrl,
                level = level,
                titleId = titleId,
                frameId = frameId,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    displayName.ifBlank { "LIFTER" },
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    fontSize = nameSize,
                    color = if (isMe) IronvellumColors.SovereignGold else IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (wornTitle != null) {
                    WornTitle(wornTitle)
                }
            }
            if (level != null) {
                LevelChip(level)
            }
            // Trailing slot (ally chip, like count, rank…) at intrinsic width.
            if (trailing != null) {
                trailing()
            }
        }
    }
}

@Composable
private fun WornTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = ChakraPetch,
        color = IronvellumColors.SovereignGold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun LevelChip(level: Int) {
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .inkBorder(IronvellumColors.Rune, shape, 1.dp)
            .background(Brush.verticalGradient(listOf(IronvellumColors.VaultHigh, IronvellumColors.Vault)), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            "LV $level",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.SystemGreen,
        )
    }
}

// Moved verbatim from LeaderboardScreen.kt so every social surface shares one
// monogram home; the board now calls these through this file.

internal fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "??"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

// Palette plates the crest can draw on — greens/gold/dark-warm only, no blue.

/**
 * The visual treatment one gacha crest frame applies to a lifter crest. The
 * frame OWNS the plate gradient, the border (colour + weight), the monogram
 * colour and — for the elite frames — an extra outer ring drawn by wrapping
 * the avatar in a thin second border of the same cut-corner shape.
 */
internal data class CrestFrameTreatment(
    val plateTop: Color,
    val plateBottom: Color,
    val frameColor: Color,
    val frameWidth: Dp,
    val initialColor: Color,
    /** Extra outer ring; the level ring yields this channel to the frame. */
    val outerRing: Color? = null,
)

/**
 * Per-frame look, built ONLY from IronvellumColors tokens plus shape/layer
 * composition — no bitmaps, no custom art. Unknown ids return null so an
 * equipped id that fell out of the catalogue degrades to today's rendering.
 */
internal fun crestFrameTreatment(frameId: String): CrestFrameTreatment? = when (frameId) {
    "iron" -> CrestFrameTreatment(IronvellumColors.VaultHigh, IronvellumColors.Vault, IronvellumColors.Rune, 2.dp, IronvellumColors.InkMuted)
    "bronze" -> CrestFrameTreatment(IronvellumColors.Rune, IronvellumColors.Vault, IronvellumColors.SovereignGold.copy(alpha = 0.55f), 2.dp, IronvellumColors.SovereignGold)
    "silver" -> CrestFrameTreatment(IronvellumColors.Rune, IronvellumColors.Vault, IronvellumColors.Ink, 2.dp, IronvellumColors.Ink)
    "gold" -> CrestFrameTreatment(IronvellumColors.VaultHigh, IronvellumColors.Vault, IronvellumColors.SovereignGold, 3.dp, IronvellumColors.SovereignGold)
    "jade" -> CrestFrameTreatment(IronvellumColors.VaultHigh, IronvellumColors.Vault, IronvellumColors.EmeraldBright, 2.dp, IronvellumColors.EmeraldBright, IronvellumColors.Emerald)
    "crimson" -> CrestFrameTreatment(IronvellumColors.VaultHigh, IronvellumColors.Vault, IronvellumColors.DangerRed, 3.dp, IronvellumColors.DangerRed)
    "obsidian" -> CrestFrameTreatment(IronvellumColors.Abyss, IronvellumColors.Vault, IronvellumColors.Bracket, 3.dp, IronvellumColors.Ink)
    "aurora" -> CrestFrameTreatment(IronvellumColors.VaultHigh, IronvellumColors.Vault, IronvellumColors.EmeraldBright, 2.dp, IronvellumColors.EmeraldBright, IronvellumColors.SovereignGold)
    // Inverted: near-black plate with a pale border and pale initials.
    "void" -> CrestFrameTreatment(IronvellumColors.Abyss, IronvellumColors.Abyss, IronvellumColors.InkMuted, 2.dp, IronvellumColors.Ink)
    // Double gold ring — the top of the catalogue.
    "masterwork" -> CrestFrameTreatment(IronvellumColors.VaultHigh, IronvellumColors.Vault, IronvellumColors.SovereignGold, 3.dp, IronvellumColors.SovereignGold, IronvellumColors.SovereignGold)
    else -> null
}

private val CrestPlates = listOf(
    IronvellumColors.EmeraldBright to IronvellumColors.Vault,
    IronvellumColors.SystemGreen to IronvellumColors.VaultHigh,
    IronvellumColors.SovereignGold to IronvellumColors.Vault,
    IronvellumColors.Rune to IronvellumColors.VaultHigh,
    IronvellumColors.VaultHigh to IronvellumColors.Vault,
)

/**
 * Deterministic lifter crest, seeded ONLY by userId: the same lifter gets the
 * same crest on every screen, every launch. avatarUrl is reserved for a real
 * uploaded picture later — until an in-house loader exists we still render the
 * generated crest rather than pulling in an image dependency.
 */
@Composable
internal fun LifterAvatar(
    userId: String,
    displayName: String,
    size: Dp,
    isMe: Boolean,
    avatarUrl: String? = null,
    level: Int? = null,
    titleId: String? = null,
    frameId: String? = null,
) {

    // Stable integer hash — never random, never recomposition-dependent.
    val seed = userId.fold(0) { acc, c -> acc * 31 + c.code }
    val plateCut = with(LocalDensity.current) { (size / 4).toPx() }
    val shape = InkPlateShape(plateCut, salt = 33)
    // Channel split: RARITY owns the plate gradient and the frame (colour +
    // weight), so a Masterwork crest is unmistakable at any size. The LEVEL
    // ring keeps the border only when the lifter wears no rarity (null or
    // Common), so the two never fight over the same visual channel. The
    // interior pattern stays seeded by userId regardless — two lifters in the
    // same title still look like different people.
    /**
     * Channel split when a gacha frame is equipped: the FRAME owns the plate
     * gradient, the border and the monogram colour (its whole look), while
     * the LEVEL ring is fully yielded — a worn frame replaces the level/rarity
     * border treatment rather than stacking on it, so the two never fight for
     * the same visual channel. With frameId null or unknown, the rarity owns
     * the plate gradient and the level ring keeps the border, byte-identical
     * to the pre-frame rendering.
     */
    val frameTreatment = frameId?.let { crestFrameTreatment(it) }
    val rarity = Titles.rarityOf(titleId)
    val (plateTop, plateBottom) = when {
        frameTreatment != null -> frameTreatment.plateTop to frameTreatment.plateBottom
        rarity == null -> CrestPlates[Math.floorMod(seed, CrestPlates.size)]
        rarity == TitleRarity.Common -> CrestPlates[Math.floorMod(seed, CrestPlates.size)]
        rarity == TitleRarity.Rare -> IronvellumColors.SystemGreen to IronvellumColors.Vault
        rarity == TitleRarity.Epic -> IronvellumColors.EmeraldBright to IronvellumColors.Vault
        else -> IronvellumColors.SovereignGold to IronvellumColors.Rune
    }
    val frame = when {
        frameTreatment != null -> frameTreatment.frameColor
        rarity == null || rarity == TitleRarity.Common -> when {
            isMe -> IronvellumColors.SovereignGold
            level == null -> IronvellumColors.Rune
            level < 10 -> IronvellumColors.Rune
            level < 25 -> IronvellumColors.SystemGreen
            level < 50 -> IronvellumColors.EmeraldBright
            else -> IronvellumColors.SovereignGold
        }
        rarity == TitleRarity.Rare -> IronvellumColors.EmeraldBright
        rarity == TitleRarity.Epic -> IronvellumColors.SovereignGold
        else -> IronvellumColors.SovereignGold
    }
    val frameWidth = when {
        frameTreatment != null -> frameTreatment.frameWidth
        rarity == TitleRarity.Epic || rarity == TitleRarity.Masterwork -> 3.dp
        else -> 2.dp
    }
    // Elite frames draw their second ring by padding the avatar inside a thin
    // wrapper border of the same cut-corner shape; 0 padding = no wrapper.
    val ringPad = if (frameTreatment?.outerRing != null) 3.dp else 0.dp
    val outerCut = with(LocalDensity.current) { ((size + ringPad * 2) / 4).toPx() }
    val outerColor = frameTreatment?.outerRing

    Box(
        Modifier
            .size(size + ringPad * 2)
            .then(
                if (outerColor != null) {
                    Modifier.inkBorder(
                        outerColor,
                        InkPlateShape(outerCut, salt = 33),
                        1.5.dp,
                    )
                } else {
                    Modifier
                },
            )
            .padding(ringPad)
            .clip(shape)
            .background(Brush.linearGradient(listOf(plateTop, plateBottom), start = Offset.Zero, end = Offset.Infinite), shape)
            .inkBorder(frame, shape, frameWidth),
        contentAlignment = Alignment.Center,
    ) {
        // Seeded geometric backdrop: a rotated triangle plus a rotated square
        // whose angles come from the hash — depth without any hand-drawn art.
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val center = Offset(w / 2f, w / 2f)
            rotate(seed % 90f) {
                val tri = Path().apply {
                    moveTo(center.x, center.y - w * 0.55f)
                    lineTo(center.x + w * 0.55f, center.y + w * 0.45f)
                    lineTo(center.x - w * 0.55f, center.y + w * 0.45f)
                    close()
                }
                drawPath(tri, Color.Black.copy(alpha = 0.25f))
            }
            rotate((seed / 7) % 180f) {
                drawRect(
                    Color.Black.copy(alpha = 0.20f),
                    topLeft = Offset(center.x - w * 0.42f, center.y - w * 0.42f),
                    size = this.size.copy(width = w * 0.84f, height = w * 0.84f),
                )
            }
        }
        Text(
            initials(displayName),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = frameTreatment?.initialColor
                ?: if (rarity == TitleRarity.Masterwork || isMe) IronvellumColors.SovereignGold else IronvellumColors.Ink,
        )
    }
}
