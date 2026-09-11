package com.monarch.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
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
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchColors
import kotlin.math.sin
import kotlin.random.Random

/** Anything worth a full-screen moment: skills, titles, levels, class ranks. */
data class Achievement(
    val banner: String,
    val name: String,
    val tagline: String = "",
    val subtitle: String = "",
    val xp: Int? = null,
    val notes: List<String> = emptyList(),
    val accent: Color = MonarchColors.SovereignGold,
)

/**
 * One celebration engine for every achievement in the app: own window (so it
 * can never render behind the nav bar), gold burst, XP count-up, and paging
 * when several unlock at once.
 */
@Composable
fun AchievementOverlay(items: List<Achievement>, onDone: () -> Unit) {
    if (items.isEmpty()) return
    var page by remember(items) { mutableStateOf(0) }
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
        var xpShown by remember(item.name) { mutableStateOf(0) }
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
                    drawCircle(
                        color = if (sp > 0.55f) item.accent else MonarchColors.SystemGreen,
                        radius = 1.6f + sp * 3.4f,
                        center = Offset(x, y.coerceIn(0f, size.height)),
                        alpha = (1f - phase) * 0.85f,
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
                    color = MonarchColors.SystemGreen,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF6B4C11), Color(0xFF241905))),
                            CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp),
                        )
                        .border(2.dp, item.accent, CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp))
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                                color = MonarchColors.InkMuted,
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
                        color = MonarchColors.SovereignGold,
                    )
                }
                item.notes.forEach { note ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MonarchColors.SystemGreen,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(26.dp))
                MonarchButton(
                    if (page < items.lastIndex) "Next (${page + 1}/${items.size})" else "Continue",
                    onClick = { if (page < items.lastIndex) page++ else onDone() },
                    gold = true,
                )
            }
        }
    }
}
