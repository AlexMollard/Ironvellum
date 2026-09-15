package com.monarch.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.monarch.app.domain.Gacha
import com.monarch.app.ui.social.crestFrameTreatment
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.inkBorder
import com.monarch.app.ui.theme.InkPlateShape
import androidx.compose.ui.platform.LocalDensity
import com.monarch.app.ui.theme.MonarchColors
import com.monarch.app.ui.theme.MonarchTracking

/**
 * Horizontal rail of the ten collectable crest frames. Each plate reuses the
 * shared [crestFrameTreatment] so the rail shows the EXACT art a hunter wears
 * on their avatar, at a size where it is actually readable. Locked frames keep
 * their shape and rings but sink to a whisper of alpha — the silhouette teases
 * the drop without leaking its colours.
 */
@Composable
fun CrestRail(
    owned: Set<String>,
    equipped: String?,
    onEquip: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(Gacha.CREST_FRAMES.size) { index ->
            val frame = Gacha.CREST_FRAMES[index]
            CrestPlate(
                frameId = frame.id,
                slot = index,
                frameName = frame.name,
                isOwned = frame.id in owned,
                isEquipped = frame.id == equipped,
                onEquip = onEquip,
            )
        }
    }
}

@Composable
private fun CrestPlate(
    frameId: String,
    slot: Int,
    frameName: String,
    isOwned: Boolean,
    isEquipped: Boolean,
    onEquip: (String?) -> Unit,
) {
    // A frame id that fell out of the catalogue degrades to a plain plate
    // rather than crashing or vanishing from the rail.
    val treatment = crestFrameTreatment(frameId)
    val plateTop = treatment?.plateTop ?: MonarchColors.VaultHigh
    val plateBottom = treatment?.plateBottom ?: MonarchColors.Vault
    val frameColor = treatment?.frameColor ?: MonarchColors.Rune
    val frameWidth = treatment?.frameWidth ?: 2.dp
    val initialColor = treatment?.initialColor ?: MonarchColors.InkMuted
    val outerRing = treatment?.outerRing

    // Slow breathing on the equipped plate only: one gentle alpha pulse on a
    // thin outer ring, 2.4s period, reversed — never a hard flash.
    val breathing = rememberInfiniteTransition(label = "crestBreath")
    val breathAlpha by breathing.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "crestBreathAlpha",
    )

    val plateSize = 96.dp
    val plateCut = with(LocalDensity.current) { (plateSize / 4).toPx() }
    val shape = InkPlateShape(plateCut, salt = 31)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(plateSize + 24.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                // FIXED footprint, always the widest ring's size. Sizing to
                // content made ringed and equipped frames taller than plain
                // ones, so the rail's height changed while scrolling and every
                // section below it shifted down.
                .size(plateSize + 24.dp)
                .then(
                    if (isOwned) {
                        Modifier.clickable { onEquip(if (isEquipped) null else frameId) }
                    } else {
                        Modifier
                    },
                )
                // 0.28 hid the art entirely; the plate must still be worth
                // looking at before it is earned.
                .alpha(if (isOwned) 1f else 0.46f),
        ) {
            // Optional equipped breathing ring — outermost, only when worn.
            if (isEquipped) {
                Box(
                    modifier = Modifier
                        .size(plateSize + 20.dp)
                        .inkBorder(MonarchColors.SovereignGold.copy(alpha = breathAlpha), shape, 1.dp),
                )
            }
            // The treatment's own second/outer ring (elite frames).
            if (outerRing != null) {
                Box(
                    modifier = Modifier
                        .size(plateSize + 8.dp)
                        .inkBorder(outerRing, shape, 1.dp),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(plateSize)
                    .background(
                        Brush.verticalGradient(listOf(plateTop, plateBottom)),
                        shape,
                    )
                    // Diagonal sheen: a flat gradient read as a coloured
                    // rectangle; this makes the plate read as struck metal.
                    .background(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = 0.10f),
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.22f),
                        ),
                        shape,
                    )
                    .inkBorder(frameColor, shape, frameWidth),
            ) {
                // Engraved inner hairline, inset from the frame — the detail
                // that separates a badge from a bordered box.
                Box(
                    Modifier
                        .size(plateSize - 10.dp)
                        .inkBorder(frameColor.copy(alpha = 0.35f), shape, 1.dp),
                )
                // Procedural heraldic emblem in the frame's own colours: a
                // letter read as a placeholder and made all ten plates alike.
                CrestEmblem(
                    seed = frameId,
                    variant = slot,
                    primary = initialColor,
                    secondary = frameColor,
                    modifier = Modifier.size(plateSize * 0.72f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            frameName,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = if (isEquipped) MonarchColors.SovereignGold else MonarchColors.Ink,
            letterSpacing = MonarchTracking.InlineLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            when {
                isEquipped -> "EQUIPPED"
                isOwned -> "TAP TO WEAR"
                else -> "NOT YET DRAWN"
            },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = when {
                isEquipped -> MonarchColors.SovereignGold
                else -> MonarchColors.InkMuted
            },
            letterSpacing = MonarchTracking.InlineLabel,
            maxLines = 1,
        )
    }
}
