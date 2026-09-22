package com.ironvellum.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkDot
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.runtime.mutableIntStateOf

/** Anything worth a full-screen moment: skills, titles, levels, class ranks. */
data class Achievement(
    val banner: String,
    val name: String,
    val tagline: String = "",
    val subtitle: String = "",
    val xp: Int? = null,
    val notes: List<String> = emptyList(),
    val accent: Color = IronvellumColors.SovereignGold,
    /**
     * Seed for procedurally generated art shown above the name. Relics have
     * continuous values, so their art cannot ship as an asset — it is composed
     * from this seed instead. Null = a text-only moment.
     */
    val sigilSeed: String? = null,
)

/**
 * One celebration engine for every achievement in the app: own window (so it
 * can never render behind the nav bar), gold burst, XP count-up, and paging
 * when several unlock at once.
 */
@Composable
fun AchievementOverlay(items: List<Achievement>, onDone: () -> Unit) {
    if (items.isEmpty()) return
    var page by remember(items) { mutableIntStateOf(0) }
    val item = items[page.coerceIn(0, items.lastIndex)]

    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val shimmer = rememberInfiniteTransition(label = "burst")
        val wave by shimmer.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
            label = "wave",
        )
        val rise by animateFloatAsState(1f, tween(700), label = "rise")
        val sparks = remember(item.name) {
            List(36) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) }
        }
        var xpShown by remember(item.name) { mutableIntStateOf(0) }
        LaunchedEffect(item.name) {
            val target = item.xp ?: 0
            if (target > 0) {
                val steps = 26
                repeat(steps) { i ->
                    xpShown = (target * (i + 1)) / steps
                    kotlinx.coroutines.delay(28)
                }
                xpShown = target
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xE60A0F0D), Color(0xF2141F1A), Color(0xE60A0F0D)),
                    ),
                )
                .clickable {
                    if (page < items.lastIndex) page++ else onDone()
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                sparks.forEach { (sx, sy, sp) ->
                    val phase = (wave + sp) % 1f
                    val x = size.width * sx + sin((phase + sp) * 6.28f) * 26f
                    val y = size.height * (sy * 0.9f + 0.05f) - phase * size.height * 0.55f * rise
                    inkDot(
                        color = (if (sp > 0.55f) item.accent else IronvellumColors.SystemGreen)
                            .copy(alpha = (1f - phase) * 0.85f),
                        radius = 1.6f + sp * 3.4f,
                        center = Offset(x, y.coerceIn(0f, size.height)),
                        seed = (sx * 997 + sy * 131).toInt(),
                    )
                }
            }

            Column(
                Modifier.padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    item.banner,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    color = IronvellumColors.SystemGreen,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                val rewardShape = MaterialTheme.shapes.medium
                Box(
                    Modifier
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF6B4C11), Color(0xFF241905))),
                            rewardShape,
                        )
                        // One shape value, used twice: the fill and the border
                        // previously built two separate instances, so any future
                        // change had to be made in both places to stay aligned.
                        .inkBorder(item.accent, rewardShape, 2.dp)
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        item.sigilSeed?.let { seed ->
                            // The reveal's centrepiece: art generated from the
                            // reward itself, so a relic draw looks like a find.
                            RelicSigil(
                                name = seed,
                                accent = item.accent,
                                modifier = Modifier.size(96.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        if (item.tagline.isNotBlank()) {
                            Text(
                                item.tagline,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = ChakraPetch,
                                color = item.accent,
                                letterSpacing = 4.sp,
                            )
                        }
                        Text(
                            item.name.uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            color = item.accent,
                            textAlign = TextAlign.Center,
                        )
                        if (item.subtitle.isNotBlank()) {
                            Text(
                                item.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = IronvellumColors.InkMuted,
                                letterSpacing = 2.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                if (item.xp != null && item.xp > 0) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "+$xpShown XP",
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = ChakraPetch,
                        fontWeight = FontWeight.Bold,
                        color = IronvellumColors.SovereignGold,
                    )
                }
                item.notes.forEach { note ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IronvellumColors.SystemGreen,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(26.dp))
                IronvellumButton(
                    if (page < items.lastIndex) "Next (${page + 1}/${items.size})" else "Continue",
                    onClick = { if (page < items.lastIndex) page++ else onDone() },
                    gold = true,
                )
            }
        }
    }
}
