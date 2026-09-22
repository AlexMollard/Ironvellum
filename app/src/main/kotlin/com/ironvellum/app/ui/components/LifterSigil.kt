package com.ironvellum.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.ui.social.crestFrameTreatment
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkHairline
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking

/**
 * Rank and worn crest as ONE insignia.
 *
 * They used to be two competing objects on the player card: a plain circular
 * level badge that matched nothing else in the app's cut-corner language, and a
 * small crest plate beside it that read as a coloured blob. Fusing them makes
 * the crest do real work — it supplies the plate and border the rank sits on,
 * so wearing a different crest visibly restyles your rank.
 *
 * With no crest equipped this is the same object in neutral palette colours, so
 * the player card never reflows when one is equipped or removed.
 */
@Composable
fun LifterSigil(level: Int, frameId: String?, modifier: Modifier = Modifier) {
    val treatment = frameId?.let { crestFrameTreatment(it) }
    val plateTop = treatment?.plateTop ?: IronvellumColors.VaultHigh
    val plateBottom = treatment?.plateBottom ?: IronvellumColors.Vault
    val frameColor = treatment?.frameColor ?: IronvellumColors.Rune
    val accent = treatment?.initialColor ?: IronvellumColors.SystemGreen
    // The sigil sits on the home player card, next to inked panels; a
    // geometric cut corner here is the one edge that would look machined.
    val shape = MaterialTheme.shapes.small

    Row(
        modifier
            .background(Brush.verticalGradient(listOf(plateTop, plateBottom)), shape)
            // Sheen, matching the crest plates in the collection so the two
            // surfaces read as the same material.
            .background(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.08f),
                    0.5f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.20f),
                ),
                shape,
            )
            // Capped at 2.dp: the collection's 3.dp frames are fine on a 96.dp
            // plate but shout at this size.
            .inkBorder(
                color = frameColor,
                shape = shape,
                width = minOf(treatment?.frameWidth ?: 2.dp, 2.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (frameId != null) {
            Box(contentAlignment = Alignment.Center) {
                CrestMark(frameId, Modifier.size(34.dp))
            }
        }
        if (frameId != null) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 30.dp)
                    // Vertical brush stroke; inkHairline reads its own orientation.
                    .inkHairline(frameColor.copy(alpha = 0.45f), seed = 21, thickness = 1.5.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "LV",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                letterSpacing = IronvellumTracking.InlineLabel,
                color = IronvellumColors.InkMuted,
            )
            Text(
                level.toString(),
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = IronvellumColors.Ink,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
