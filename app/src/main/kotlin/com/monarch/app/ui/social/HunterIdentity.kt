package com.monarch.app.ui.social

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CutCornerShape
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
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors

/** One hunter's identity, rendered the same way on every social surface. */
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
    val accent = if (isMe) MonarchColors.SovereignGold else MonarchColors.EmeraldBright

    // At Hero size the worn title moves BELOW the row: sharing the name's
    // column with the LV and ally chips left it ~15 characters wide, so
    // "Shadow Marcher" truncated while a third of the card sat empty.
    val titleBelow = size == IdentitySize.Hero && wornTitle != null

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
            HunterAvatar(
                userId = userId,
                displayName = displayName,
                size = badgeSize,
                isMe = isMe,
                avatarUrl = avatarUrl,
                level = level,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    displayName.ifBlank { "HUNTER" },
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    fontSize = nameSize,
                    color = if (isMe) MonarchColors.SovereignGold else MonarchColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Non-hero sizes keep the title tucked under the name.
                if (wornTitle != null && !titleBelow) {
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
        if (titleBelow) {
            Spacer(Modifier.height(2.dp))
            WornTitle(wornTitle!!, Modifier.padding(start = badgeSize + 12.dp))
        }
    }
}

@Composable
private fun WornTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = ChakraPetch,
        color = MonarchColors.SovereignGold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun LevelChip(level: Int) {
    val shape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)
    Box(
        Modifier
            .border(1.dp, MonarchColors.Rune, shape)
            .background(Brush.verticalGradient(listOf(MonarchColors.VaultHigh, MonarchColors.Vault)), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            "LV $level",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.SystemGreen,
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
private val CrestPlates = listOf(
    MonarchColors.EmeraldBright to MonarchColors.Vault,
    MonarchColors.SystemGreen to MonarchColors.VaultHigh,
    MonarchColors.SovereignGold to MonarchColors.Vault,
    MonarchColors.Rune to MonarchColors.VaultHigh,
    MonarchColors.VaultHigh to MonarchColors.Vault,
)

/**
 * Deterministic hunter crest, seeded ONLY by userId: the same hunter gets the
 * same crest on every screen, every launch. avatarUrl is reserved for a real
 * uploaded picture later — until an in-house loader exists we still render the
 * generated crest rather than pulling in an image dependency.
 */
@Composable
internal fun HunterAvatar(
    userId: String,
    displayName: String,
    size: Dp,
    isMe: Boolean,
    avatarUrl: String?,
    level: Int? = null,
) {
    // Stable integer hash — never random, never recomposition-dependent.
    val seed = userId.fold(0) { acc, c -> acc * 31 + c.code }
    val (plateTop, plateBottom) = CrestPlates[Math.floorMod(seed, CrestPlates.size)]
    val shape = CutCornerShape(topStart = size / 4, bottomEnd = size / 4)
    // Rank ring colour steps with level; unknown level gets the neutral rune.
    val ring = when {
        isMe -> MonarchColors.SovereignGold
        level == null -> MonarchColors.Rune
        level < 10 -> MonarchColors.Rune
        level < 25 -> MonarchColors.SystemGreen
        level < 50 -> MonarchColors.EmeraldBright
        else -> MonarchColors.SovereignGold
    }

    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(plateTop, plateBottom), start = Offset.Zero, end = Offset.Infinite), shape)
            .border(2.dp, ring, shape),
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
            color = if (isMe) MonarchColors.SovereignGold else MonarchColors.Ink,
        )
    }
}
