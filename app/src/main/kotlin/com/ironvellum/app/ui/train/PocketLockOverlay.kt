package com.ironvellum.app.ui.train

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.PocketLock
import com.ironvellum.app.domain.RestTimer
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking

/**
 * The pocket lock: a near-black cover over the live trial. It shows only the rest
 * countdown (or, between rests, the set in hand), swallows every touch and the back
 * button, and lets go when a finger is held for [PocketLock.HOLD_MS], with a thin bar
 * filling as it goes. Holding is not possible for everyone, so TalkBack gets an
 * "Unlock the trial" action and so does a long-click. The trial underneath keeps
 * running: a rest still counts down and ends, and the notification still works.
 */
@Composable
internal fun PocketLockOverlay(
    rest: RestTimer?,
    nextLine: String?,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = {})
    val unlock by rememberUpdatedState(onUnlock)
    val now = restNow(rest)
    val restLabel = rest?.takeIf { !it.isOver(now) }?.label(now)
    val summary = PocketLock.summary(restLabel, nextLine)
    val progress = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            progress.animateTo(1f, tween(PocketLock.HOLD_MS, easing = LinearEasing))
            unlock()
        } else {
            progress.snapTo(0f)
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(IronvellumColors.Abyss.copy(alpha = 0.97f))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                })
            }
            .semantics(mergeDescendants = true) {
                contentDescription = "Trial locked. ${summary ?: "Hold to unlock."}"
                customActions = listOf(CustomAccessibilityAction("Unlock the trial") { unlock(); true })
                onLongClick(label = "Unlock the trial") { unlock(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(28.dp))
            if (restLabel != null) {
                Text(
                    "REST",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.InlineLabel,
                )
                Text(
                    restLabel,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    fontSize = 56.sp,
                    color = IronvellumColors.Ink.copy(alpha = 0.85f),
                    maxLines = 1,
                )
            }
            if (nextLine != null) {
                Text(
                    if (restLabel != null) "Next · $nextLine" else nextLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = IronvellumColors.Ink.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                "Hold to unlock",
                style = MaterialTheme.typography.labelMedium,
                color = IronvellumColors.InkMuted,
            )
        }
        // What the hold has covered so far.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(IronvellumColors.Rune),
        ) {
            Box(Modifier.fillMaxWidth(progress.value).fillMaxHeight().background(IronvellumColors.Emerald))
        }
    }
}
