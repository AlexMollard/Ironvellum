package com.ironvellum.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import java.time.ZoneId
import java.util.TimeZone

/**
 * The Ledger's layout and type tokens, in one place so a screen cannot invent
 * a seventeenth spacing. Panels sit [Panel] apart, sections [Section] apart,
 * and nothing tappable is shorter than [Target].
 */
object LedgerSpace {
    val Gutter = 16.dp
    val Panel = 12.dp
    val Section = 24.dp
    val RowV = 12.dp
    val Target = 48.dp
}

/**
 * Tints for meaning that [IronvellumColors.Bracket] (about 1.9:1 on the panel)
 * cannot carry. Both are InkMuted thinned over the panel, so they stay in the
 * monochrome palette; LedgerContrastTest holds them to 4.5:1 (text) and 3:1
 * (graphics) against [IronvellumColors.Vault].
 */
object LedgerContrast {
    /** Numbers of days that have not happened yet: dimmer than the past, still readable. */
    val FutureText = IronvellumColors.InkMuted.copy(alpha = 0.8f)

    /** Bars, bands and chevrons that say something: an empty slot, the lowest band, a disabled arrow. */
    val Graphic = IronvellumColors.InkMuted.copy(alpha = 0.6f)
}

/**
 * The device's zone, re-read when the system announces a change
 * (ACTION_TIMEZONE_CHANGED) and on every resume. Cached once, a trip across a
 * border bucketed the calendar, the BODY strip and the charts by the old zone
 * while "today" had already moved to the new one.
 */
@Composable
fun rememberZoneId(): ZoneId {
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // The framework resets the JVM default itself; doing it here too removes the race with that update.
                TimeZone.setDefault(null)
                zone = ZoneId.systemDefault()
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) zone = ZoneId.systemDefault()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return zone
}

/** Three value sizes, no more: one hero per screen, tiles, and inline figures. */
enum class StatSize { Hero, Tile, Inline }

/**
 * A figure. Ink by default: value colour carries meaning (green for a gain,
 * gold only for a record), so a plain number is never coloured for decoration.
 */
@Composable
fun StatValue(
    text: String,
    modifier: Modifier = Modifier,
    size: StatSize = StatSize.Tile,
    color: Color = IronvellumColors.Ink,
    unit: String? = null,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text,
            // A hero is the one display figure; a tile reads as titleLarge, an inline figure as titleMedium.
            style = when (size) {
                StatSize.Hero -> MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 39.sp)
                StatSize.Tile -> MaterialTheme.typography.titleLarge
                StatSize.Inline -> MaterialTheme.typography.titleMedium
            },
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
        if (unit != null) {
            Text(
                unit,
                style = MaterialTheme.typography.titleMedium,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

/** The one small label above a figure or a panel: muted, tracked, and a heading for screen readers. */
@Composable
fun PanelLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = ChakraPetch,
        color = IronvellumColors.InkMuted,
        letterSpacing = IronvellumTracking.InlineLabel,
        modifier = modifier.semantics { heading() },
    )
}

/** A 1dp rule between rows of one panel. */
@Composable
fun InkDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(IronvellumColors.Rune))
}

/**
 * One row inside a panel: label, an optional supporting line, a trailing value and a chevron
 * when it opens something. It is [ListRow]; rows are divided by [InkDivider] inside a single
 * InkPanel rather than each being a bordered box.
 */
@Composable
fun InkListRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
) = ListRow(label, modifier, value = value, subline = supporting, onClick = onClick)

/** Small range switch (30D / 90D / ALL): every option a full 48dp target, state announced; the open one is Ink over a 2dp Emerald underline. */
@Composable
fun <T> RangeChips(
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .heightIn(min = LedgerSpace.Target)
                    .widthIn(min = LedgerSpace.Target)
                    .clickable(role = Role.RadioButton) { onPick(value) }
                    .semantics { this.selected = on }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = if (on) IronvellumColors.Ink else IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    modifier = Modifier.selectedUnderline(on).padding(vertical = 6.dp),
                )
            }
        }
    }
}
