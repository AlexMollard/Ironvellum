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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.ArmyClass
import com.ironvellum.app.ui.components.CrestPlate
import com.ironvellum.app.ui.components.crestLook
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.METAL_WASH
import com.ironvellum.app.ui.theme.Metal
import com.ironvellum.app.ui.theme.RarityTint
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.DotShape
import androidx.compose.ui.platform.LocalDensity
import com.ironvellum.app.ui.theme.IronvellumColors

/** One lifter's identity, rendered the same way on every social surface. */
internal enum class IdentitySize {
    Compact, Standard, Hero,

    /** A feed card header: 40dp crest, 15sp name, "You . Title" under it, "Level n" at the right. */
    Card,

    /** The lifter's own card: 48dp crest, 18sp name, "Title . Level n" under it, no level at the right. */
    Profile,
}

/**
 * The metal an avatar's ring and faint wash are drawn in: iron for everyone, gold for first place on a
 * board, and a heavier gold for the lifter's own mark. An equipped crest frame replaces all three.
 */
internal enum class AvatarRing(val metal: Metal, val width: Dp, val initials: Color) {
    Iron(Metal.Common, 1.5.dp, IronvellumColors.Ink),
    First(Metal.Fabled, 1.5.dp, IronvellumColors.Ink),
    Own(Metal.Fabled, 3.dp, RarityTint.Gold),
}

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
    // Equipped gacha crest frame; null = today's rarity/level rendering.
    frameId: String? = null,
    ring: AvatarRing = AvatarRing.Iron,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val badgeSize = when (size) {
        IdentitySize.Compact -> 32.dp
        IdentitySize.Standard, IdentitySize.Card -> 40.dp
        IdentitySize.Hero -> 56.dp
        IdentitySize.Profile -> 48.dp
    }
    val nameSize = when (size) {
        IdentitySize.Compact -> 13.sp
        IdentitySize.Standard -> 16.sp
        IdentitySize.Hero -> 22.sp
        IdentitySize.Card -> 15.sp
        IdentitySize.Profile -> 18.sp
    }
    val plain = size == IdentitySize.Card || size == IdentitySize.Profile
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
            LifterAvatar(displayName = displayName, size = badgeSize, frameId = frameId, ring = ring)
            Column(Modifier.weight(1f)) {
                Text(
                    displayName.ifBlank { "IRONBOUND" },
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = ChakraPetch,
                    fontWeight = if (plain) FontWeight.SemiBold else FontWeight.Bold,
                    fontSize = nameSize,
                    color = IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (plain) {
                    // One muted line: no gold title, no separate "You" line.
                    val sub = listOfNotNull(
                        "You".takeIf { isMe && size == IdentitySize.Card },
                        wornTitle,
                        level?.takeIf { size == IdentitySize.Profile }?.let { "${ArmyClass.forLevel(it).title} $it" },
                    ).joinToString(" · ")
                    if (sub.isNotEmpty()) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else if (wornTitle != null) {
                    WornTitle(wornTitle)
                }
                // Your own row says so in words, not in gold.
                if (isMe && !plain) {
                    Text(
                        "You",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = ChakraPetch,
                        color = IronvellumColors.InkMuted,
                        maxLines = 1,
                    )
                }
            }
            if (level != null && size == IdentitySize.Card) {
                Text(
                    "Level $level",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    maxLines = 1,
                    softWrap = false,
                )
            } else if (level != null && size != IdentitySize.Profile) {
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
    Text(
        "LV $level",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.Bold,
        color = IronvellumColors.InkMuted,
    )
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

/**
 * The one lifter mark on every Allies surface, as the mockups draw it: a round Vault-high plate, a flat
 * gradient ring over a faint wash, and the lifter's initials in Chakra Petch semi-bold, sized to the plate.
 * Without a crest the ring is iron, or gold for first place and the lifter's own ([ring]). A worn crest
 * ([frameId]) restyles the ring in the crest's own ramp (doubled for the top crests) and rides on the
 * plate as a small badge showing its mark, so 28 crests stay tellable apart. An id the catalogue does not
 * know (a crest from a newer build) leaves the plain avatar; there is no stand-in crest. An uploaded picture
 * is not supported yet, so the monogram is drawn.
 */
@Composable
internal fun LifterAvatar(
    displayName: String,
    size: Dp,
    frameId: String? = null,
    ring: AvatarRing = AvatarRing.Iron,
) {
    val look = frameId?.let { crestLook(it) }
    val metal = look?.ring ?: ring.metal
    val width = if (look == null) ring.width else if (size >= 44.dp) 2.5.dp else 2.dp
    val initialsColor = when {
        look == null -> ring.initials
        look.ring === Metal.Fabled -> IronvellumColors.SovereignGold
        else -> IronvellumColors.Ink
    }
    Box(Modifier.size(size)) {
        Box(
            Modifier
                .matchParentSize()
                .clip(DotShape)
                .background(IronvellumColors.VaultHigh, DotShape)
                .background(metal.bounds(METAL_WASH), DotShape)
                .inkBorder(metal.bounds(), DotShape, width)
                .then(
                    if (look?.double == true) {
                        Modifier.padding(width * 1.6f).inkBorder(metal.bounds(0.55f), DotShape, width * 0.55f)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials(displayName),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.SemiBold,
                fontSize = avatarInitialsSize(size),
                color = initialsColor,
                maxLines = 1,
                softWrap = false,
            )
        }
        if (look != null && frameId != null) {
            val badge = maxOf(16.dp, size * 0.42f)
            val off = badge * 0.45f
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(off, off)
                    .size(badge + 4.dp)
                    .background(IronvellumColors.Abyss, DotShape),
                contentAlignment = Alignment.Center,
            ) {
                CrestPlate(frameId, Modifier.size(badge))
            }
        }
    }
}

/** The mockups' initials sizes per plate: 56 to 18sp, 52 to 17, 48 to 16, 44 to 15, 40 to 14, 36 to 13, 32 to 12. */
private fun avatarInitialsSize(size: Dp) = when {
    size >= 56.dp -> 18.sp
    size >= 52.dp -> 17.sp
    size >= 48.dp -> 16.sp
    size >= 44.dp -> 15.sp
    size >= 40.dp -> 14.sp
    size >= 36.dp -> 13.sp
    else -> 12.sp
}
