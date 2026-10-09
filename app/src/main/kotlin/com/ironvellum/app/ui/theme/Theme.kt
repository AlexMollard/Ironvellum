package com.ironvellum.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironvellum.app.R

object IronvellumColors {
    // Snapshot state keeps existing drawing callbacks and non-composable
    // helpers reactive without a second palette at hundreds of call sites.
    internal var accents by androidx.compose.runtime.mutableStateOf(AccentPalette.Default)
    // Paper tones: warm charcoal, not blue-grey. Ink needs something to sit on.
    val Abyss = Color(0xFF0C0C0B) // deepest paper
    val Vault = Color(0xFF171715) // panel paper
    val VaultHigh = Color(0xFF1E1E1B) // raised paper

    // Colour survives ONLY where it carries information a monochrome UI would
    // destroy: progression, rarity, earned moments, danger.
    val Emerald get() = Color(accents.primary) // XP and success
    val EmeraldBright get() = Color(accents.bright)
    val EmeraldDeep get() = Color(accents.deep)
    val SystemGreen get() = Color(accents.muted) // muted green accent
    val SovereignGold get() = Color(accents.secondary) // earned moments only
    val DangerRed = Color(0xFFEF5350)

    // Ink itself: the structural palette is monochrome by design.
    // Exactly tools/art.py INK_TINT (232, 232, 228): generated artwork and UI
    // text must be the same ink, or the "one hand" claim is decorative only.
    val Ink = Color(0xFFE8E8E4)
    val InkMuted = Color(0xFFA3A099)
    val Rune = Color(0xFF32302B) // structure lines
    val Bracket = Color(0xFF4A473F) // brush ticks
}

/** The three sanctioned letter-spacing values; use these, never ad-hoc sp literals. */
object IronvellumTracking {
    val InlineLabel = 2.sp
    val SectionHeader = 4.sp
    val ScreenTitle = 6.sp
}
val ChakraPetch = FontFamily(
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

private fun ironvellumColorScheme(palette: AccentPalette) = darkColorScheme(
    primary = Color(palette.primary),
    onPrimary = Color(accentForeground(palette.primary)),
    primaryContainer = IronvellumColors.VaultHigh,
    onPrimaryContainer = IronvellumColors.Ink,
    secondary = Color(palette.muted),
    onSecondary = Color(accentForeground(palette.muted)),
    secondaryContainer = IronvellumColors.VaultHigh,
    onSecondaryContainer = IronvellumColors.Ink,
    tertiary = Color(palette.secondary),
    onTertiary = Color(accentForeground(palette.secondary)),
    background = IronvellumColors.Abyss,
    onBackground = IronvellumColors.Ink,
    surface = IronvellumColors.Vault,
    onSurface = IronvellumColors.Ink,
    surfaceVariant = IronvellumColors.VaultHigh,
    onSurfaceVariant = IronvellumColors.InkMuted,
    outline = IronvellumColors.Rune,
    outlineVariant = IronvellumColors.Rune,
    error = IronvellumColors.DangerRed,
    onError = Color.Black,
)

private val IronvellumTypography: Typography
    get() {
        val base = Typography()
        fun display(size: Int) = TextStyle(
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            fontSize = size.sp,
        )

        fun head(size: Int) = TextStyle(
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            fontSize = size.sp,
        )

        fun title(size: Int, weight: FontWeight) = TextStyle(
            fontFamily = ChakraPetch,
            fontWeight = weight,
            fontSize = size.sp,
        )

        fun label(size: Int, spacing: Double) = TextStyle(
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Medium,
            fontSize = size.sp,
            letterSpacing = spacing.sp,
        )
        return base.copy(
            displayLarge = display(52),
            displayMedium = display(42),
            displaySmall = display(34),
            headlineLarge = head(30),
            headlineMedium = head(26),
            headlineSmall = title(22, FontWeight.Bold),
            titleLarge = title(20, FontWeight.Bold),
            titleMedium = title(16, FontWeight.SemiBold),
            titleSmall = title(14, FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.copy(fontFamily = ChakraPetch),
            bodyMedium = base.bodyMedium.copy(fontFamily = ChakraPetch),
            bodySmall = base.bodySmall.copy(fontFamily = ChakraPetch),
            labelLarge = label(14, 0.5),
            labelMedium = label(12, 0.5),
            // Material's own label size and tracking: this style carries whole
            // sentences (captions, hints, deed bars), and 10sp at 2sp tracking
            // read as a row of loose letters. Caps labels that want the wide
            // look pass IronvellumTracking themselves.
            labelSmall = label(11, 0.5),
        )
    }

// Cut corners mark containers and the primary control, nothing else
// (docs/DESIGN.md section 2): medium and large keep the 8dp cut, so cards and
// the primary button read as the app's one shaped thing. extraSmall and small
// are plain rectangles for everything that sits inside a card - chips, fields,
// rows, badges - so they carry no corner at all.
// Shapes demands a CornerBasedShape, so "square" is a zero-radius one.
private val SquareShape = RoundedCornerShape(0.dp)

private val IronvellumShapes = Shapes(
    extraSmall = SquareShape,
    small = SquareShape,
    medium = InkEdgeShape(),
    large = InkEdgeShape(),
    extraLarge = InkEdgeShape(),
)

/**
 * Neutral text-field colours: the focused border and label are muted ink, not
 * emerald (emerald is the screen's one action), and the cursor is the quiet
 * green. Material has no theme-level hook for these, so fields opt in.
 */
@Composable
fun ironvellumFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = IronvellumColors.InkMuted,
    unfocusedBorderColor = IronvellumColors.Rune,
    focusedLabelColor = IronvellumColors.InkMuted,
    unfocusedLabelColor = IronvellumColors.InkMuted,
    cursorColor = IronvellumColors.SystemGreen,
    focusedTextColor = IronvellumColors.Ink,
    unfocusedTextColor = IronvellumColors.Ink,
)

/** Ironvellum is always dark — the Ledger never sleeps. Dynamic color is deliberately unused. */
@Composable
fun IronvellumTheme(palette: AccentPalette = IronvellumColors.accents, content: @Composable () -> Unit) {
    // The text scale is pinned in MainActivity.attachBaseContext, which every
    // window of the app inherits — including dialogs, which compose in their
    // own window and so ignored a CompositionLocal installed here.
    MaterialTheme(
        colorScheme = ironvellumColorScheme(palette),
        typography = IronvellumTypography,
        shapes = IronvellumShapes,
    ) {
        CompositionLocalProvider(LocalIndication provides InkPressIndication, content = content)
    }
}

/** The single scale every Ironvellum layout is designed and verified at. */
const val FIXED_FONT_SCALE = 1f
