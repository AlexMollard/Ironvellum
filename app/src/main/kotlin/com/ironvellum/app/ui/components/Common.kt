package com.ironvellum.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkArc
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkHairline
import com.ironvellum.app.ui.theme.inkRail
import com.ironvellum.app.ui.theme.inkTick
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The app's card: flat [IronvellumColors.Vault] with a 1dp [IronvellumColors.Rune]
 * border and the theme's 8dp cut corners (`MaterialTheme.shapes.medium`).
 *
 * The most reused surface in Ironvellum (~90 call sites), which is exactly why the
 * treatment lives HERE and not in the screens - every screen inherits it
 * and none of them can drift. It has no accent: a card is a neutral group, and
 * colour is spent on what is inside it (docs/DESIGN.md sections 2 and 3).
 */
@Composable
fun InkPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val body: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content,
        )
    }
    val surfaceModifier = modifier
        .background(IronvellumColors.Vault, shape)
        .inkBorder(IronvellumColors.Rune, shape, 1.dp)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = surfaceModifier,
        ) {
            body()
        }
    } else {
        Surface(
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = surfaceModifier,
        ) {
            body()
        }
    }
}

/**
 * A pill for choosing one value from a short list (the report-reason choice). It does not
 * switch pages: tabs that do are [InkTabs]. One shared implementation, so every pill reads alike.
 * The chosen pill is Ink with a 2dp Emerald underline; the rest are InkMuted on a Rune outline.
 */
@Composable
fun IronvellumTabPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier
            .clip(shape)
            .inkBorder(IronvellumColors.Rune, shape, 1.dp)
            .selectedUnderline(selected)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            // The underline is invisible to a screen reader, which would otherwise
            // read every pill identically and give no clue which one is chosen.
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = if (selected) IronvellumColors.Ink else IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The app's progress rail, drawn as ink.
 *
 * There were four near-identical hand-rolled versions of this - Codex deed
 * progress, the dashboard factor bars, the leaderboard intensity bar and the
 * idle taper - each a track Box with a fraction-width Box inside. They are one
 * component now, so a rail cannot look machined on one screen and drawn on
 * another.
 */
@Composable
fun InkRail(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    track: Color = IronvellumColors.Rune,
    fill: Brush = SolidColor(IronvellumColors.Emerald),
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        inkRail(fraction = fraction, track = track, fill = fill)
    }
}
/**
 * A section label: `labelSmall` caps in [IronvellumColors.InkMuted], nothing else.
 * [topPadding] is the gap above it (24dp by default); callers that already sit
 * under a spacer of their own pass a smaller one.
 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, topPadding: Dp = 24.dp) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChakraPetch,
        color = IronvellumColors.InkMuted,
        letterSpacing = IronvellumTracking.InlineLabel,
        // Marks the section so a screen reader can jump between them
        // instead of swiping through every control in between.
        modifier = modifier
            .padding(top = topPadding, bottom = 10.dp)
            .semantics { heading() },
    )
}

/**
 * Primary action: flat emerald with Abyss text, 8dp cut corners, sentence case
 * and no press-scale (docs/DESIGN.md sections 2 and 6).
 *
 * `quiet` is the SECOND action in a card: no box at all, just a [IronvellumColors.SystemGreen]
 * text link. Four bright emerald slabs stacked down a screen all shouted
 * equally and nothing read as the main thing to do.
 *
 * `danger` is the quiet shape in [IronvellumColors.DangerRed], for an action that replaces or
 * deletes data; it always sits behind a confirm.
 *
 * `gold` is the seal / XP variant and is deliberately left as it was (owner
 * decision pending), see [GoldButton].
 */
/** Set by [IronvellumDialog]: its action slots show every plain button as a text button. */
internal val LocalTextButtons = compositionLocalOf { false }

@Composable
fun IronvellumButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gold: Boolean = false,
    enabled: Boolean = true,
    quiet: Boolean = false,
    danger: Boolean = false,
) {
    if (gold) {
        GoldButton(label, onClick, modifier, enabled, quiet, danger)
        return
    }
    val shape = MaterialTheme.shapes.medium
    val boxless = quiet || danger || LocalTextButtons.current
    Box(
        modifier
            .then(if (boxless) Modifier.heightIn(min = 48.dp) else Modifier)
            .clip(shape)
            .then(
                if (boxless) {
                    Modifier
                } else {
                    Modifier.background(if (enabled) IronvellumColors.Emerald else IronvellumColors.Rune)
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = when {
                !enabled -> IronvellumColors.InkMuted
                danger -> IronvellumColors.DangerRed
                quiet || boxless -> IronvellumColors.SystemGreen
                else -> IronvellumColors.Abyss
            },
            // labelLarge carries the theme's 0.5sp tracking: no override.
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 18.dp),
        )
    }
}

/**
 * The gold button, kept exactly as the one IronvellumButton looked before the
 * Clean migration (gradient fill, uppercase label, press scale). Used by the
 * seal and XP flows; the owner has yet to decide its Clean treatment.
 */
// shortcut: legacy look kept verbatim; restyle or delete once the owner decides the gold variant.
@Composable
private fun GoldButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    quiet: Boolean,
    danger: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(90), label = "press")
    val colors = listOf(Color(0xFFF2C14E), Color(0xFFC98A2B))
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .background(
                when {
                    !enabled -> Brush.linearGradient(listOf(Color(0xFF1E3026), Color(0xFF16211B)))
                    quiet || danger -> Brush.linearGradient(listOf(Color(0xFF141C18), Color(0xFF101714)))
                    else -> Brush.linearGradient(colors)
                },
            )
            .inkBorder(
                when {
                    !enabled -> IronvellumColors.Rune
                    danger -> IronvellumColors.DangerRed.copy(alpha = 0.7f)
                    quiet -> IronvellumColors.Rune
                    else -> Color(0x5934D399)
                },
                shape,
                1.dp,
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            // The fills are bright emerald/gold: ink-dark type is the only
            // readable choice. Inheriting the theme colour rendered grey-on-gold.
            color = when {
                !enabled -> IronvellumColors.InkMuted
                danger -> IronvellumColors.DangerRed
                quiet -> IronvellumColors.SystemGreen
                else -> IronvellumColors.Abyss
            },
            style = MaterialTheme.typography.labelLarge,
            letterSpacing = IronvellumTracking.InlineLabel,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 18.dp),
        )
    }
}

fun formatDate(ms: Long, pattern: String = "MMM d · HH:mm"): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern))

/**
 * One decimal place - the precision every instrument feeding this app actually
 * has. Bathroom scales, tape measures and body-fat calipers resolve to 0.1;
 * nothing downstream knows more than that.
 *
 * Rendering the raw Double instead printed `79.0999984741211 kg` on the owner's
 * phone: Health Connect had handed over a widened Float. This is the display
 * guard and the one that always applies — [com.ironvellum.app.data.HealthSync]
 * additionally rounds at the write boundary, but only for rows read from that
 * point on. Rows already on disk keep their float and are corrected here.
 *
 * [Locale.US] on purpose, not the reader's locale: this same text prefills the
 * log-weight field, which is parsed back with `toDoubleOrNull()`. That parser
 * only accepts `.`, so a locale rendering `79,1` would blank the field every
 * time a European lifter opened the dialog.
 */
fun formatBodyValue(value: Double): String = String.format(Locale.US, "%.1f", value)

/**
 * A load exactly as it can be racked: up to two places, trailing zeros
 * dropped, so 24.0 reads "24", 15.2 "15.2" and the 1.25 kg isolation step's
 * 8.75 "8.75". One place printed 8.75 kg as 8.8, a load nobody can pick up.
 */
fun formatLoadKg(kg: Double): String = String.format(Locale.US, "%.2f", kg).trimEnd('0').trimEnd('.')

/**
 * Explicit both forms rather than appending "s": every count label in this app
 * is upper-case HUD text, so a derived plural would read "WORKOUTs". The owner's
 * own log header read "1 WORKOUTS".
 */
fun plural(count: Int, one: String, many: String): String = if (count == 1) one else many

/** One set's figure text plus the noun it is counted in. */
data class SetFigure(val figure: String, val noun: String)

/**
 * One set's figure and its noun, in the vocabulary SessionScreen's columns
 * use: REPS / SECONDS (holds) / ATTEMPTS / KM + MINUTES (distance work) /
 * MINUTES (timed work). The per-screen copies this replaces all branched
 * hold-vs-not, so every activity metric fell into the rep branch: a climb's
 * attempts read "7 REPS" and a run read "0 REPS". A figure its metric does
 * not carry (a DURATION set's `reps`, which is always 0) is never printed.
 *
 * Legacy hold rows wrote their seconds into `reps`, hence the fallback.
 * Pure function, no Compose state, callable from anywhere.
 */
fun setFigure(
    metric: ExerciseMetric,
    reps: Int,
    durationSec: Int?,
    distanceM: Double?,
): SetFigure = when (metric) {
    ExerciseMetric.REPS -> SetFigure(reps.toString(), plural(reps, "REP", "REPS"))
    ExerciseMetric.HOLD -> {
        val seconds = durationSec ?: reps
        SetFigure(seconds.toString(), plural(seconds, "SECOND", "SECONDS"))
    }
    ExerciseMetric.DURATION -> {
        val minutes = (durationSec ?: 0) / 60
        // A 40-minute yoga set rounds cleanly; a 40-second one must not read
        // as zero work.
        SetFigure(if (durationSec != null && durationSec < 60) "<1" else minutes.toString(), "MINUTES")
    }
    ExerciseMetric.DISTANCE_TIME -> {
        val km = (distanceM ?: 0.0) / 1000.0
        val minutes = (durationSec ?: 0) / 60
        SetFigure("${formatBodyValue(km)} km · $minutes min", "KM · MIN")
    }
    // The attempt count rides `reps` (as ActivityScore reads it). The
    // free-text grade is presentation, so the caller shows it where it fits.
    ExerciseMetric.ATTEMPTS_GRADE -> SetFigure(reps.toString(), plural(reps, "ATTEMPT", "ATTEMPTS"))
}

/**
 * Same-unit totals for a stretch of sets: reps and attempts are never merged
 * (a climb's attempts are not repetitions), seconds held stay separate from
 * seconds worked, and distance work contributes to both km and minutes. The
 * lifetime ledger used to sum every non-hold set through `reps`, which turned
 * 7 boulder attempts into "7 REPS" and paid a 30-minute yoga set zero.
 */
data class MetricTotals(
    val reps: Int,
    val heldSeconds: Int,
    val attempts: Int,
    val km: Double,
    val secondsWorked: Int,
)

fun metricTotals(sets: List<SessionSet>, metricOf: (SessionSet) -> ExerciseMetric): MetricTotals {
    var reps = 0
    var heldSeconds = 0
    var attempts = 0
    var km = 0.0
    var secondsWorked = 0
    for (set in sets) {
        when (metricOf(set)) {
            ExerciseMetric.REPS -> reps += set.reps
            ExerciseMetric.HOLD -> heldSeconds += set.durationSec ?: set.reps
            ExerciseMetric.ATTEMPTS_GRADE -> attempts += set.reps
            ExerciseMetric.DURATION -> secondsWorked += set.durationSec ?: 0
            ExerciseMetric.DISTANCE_TIME -> {
                km += (set.distanceM ?: 0.0) / 1000.0
                secondsWorked += set.durationSec ?: 0
            }
        }
    }
    return MetricTotals(reps, heldSeconds, attempts, km, secondsWorked)
}

/**
 * The header chip (BACK, CLEAR): [InkChip] under its older name. [icon] is optional and
 * BACK passes none.
 */
@Composable
fun NavChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = InkChip(label = label, icon = icon, modifier = modifier, onClick = onClick)

/**
 * Busy indicator drawn as a brushed arc instead of Material's perfect ring.
 *
 * The stock CircularProgressIndicator is a compass-struck circle with an even
 * stroke - the last machine-exact mark in the app once every other surface was
 * inked. The sweep rotates; the stroke itself is a single brushed arc.
 */
@Composable
fun InkSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    color: Color = IronvellumColors.Emerald,
) {
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spinAngle",
    )
    // progressSemantics() is what Material's indeterminate indicator carries;
    // a bare Canvas announces nothing to a screen reader.
    Canvas(modifier.size(size).progressSemantics()) {
        val r = this.size.minDimension / 2f - 2.dp.toPx()
        inkArc(
            center = Offset(this.size.width / 2f, this.size.height / 2f),
            radius = r,
            startDeg = angle,
            sweepDeg = 250f,
            color = color,
            widthPx = 2.5.dp.toPx(),
        )
    }
}

/**
 * The app's segmented picker, for CHOOSING AN OPTION (ON/OFF, units, a who-sees-this answer).
 * A control that shows a different page or view is [InkTabs] instead.
 *
 * The open option is Ink with a 2dp Emerald underline, the rest InkMuted, all along one 1dp
 * Rune baseline: no boxes and no fills, so a choice never outweighs the screen's action.
 */
@Composable
fun <T> InkSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Min intrinsic height so one segment whose label wraps (large font scale)
    // makes its neighbours the same height rather than ragged.
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .drawBehind {
                val thick = 1.dp.toPx()
                drawRect(IronvellumColors.Rune, Offset(0f, size.height - thick), Size(size.width, thick))
            },
    ) {
        options.forEach { (value, label) ->
            val isOn = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 48.dp)
                    .selectedUnderline(isOn)
                    .clickable { onPick(value) }
                    // The underline carries no semantics of its own.
                    .semantics {
                        role = Role.Tab
                        // `this.` is load-bearing: the enclosing function's own
                        // `selected` parameter hides the semantics property.
                        this.selected = isOn
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = ChakraPetch,
                    color = if (isOn) IronvellumColors.Ink else IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    // Wraps or ellipsises at a large font scale instead of clipping.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
