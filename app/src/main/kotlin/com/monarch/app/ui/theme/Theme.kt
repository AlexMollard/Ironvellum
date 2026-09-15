package com.monarch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monarch.app.R

object MonarchColors {
    // Paper tones: warm charcoal, not blue-grey. Ink needs something to sit on.
    val Abyss = Color(0xFF0C0C0B) // deepest paper
    val Vault = Color(0xFF171715) // panel paper
    val VaultHigh = Color(0xFF1E1E1B) // raised paper

    // Colour survives ONLY where it carries information a monochrome UI would
    // destroy: progression, rarity, earned moments, danger.
    val Emerald = Color(0xFF34D399) // XP and success
    val EmeraldBright = Color(0xFF6EE7B7)
    val SystemGreen = Color(0xFF6FAE8C) // muted green accent
    val SovereignGold = Color(0xFFF2C14E) // earned moments only
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
object MonarchTracking {
    val InlineLabel = 2.sp
    val SectionHeader = 4.sp
    val ScreenTitle = 6.sp
}
val ChakraPetch = FontFamily(
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

private val MonarchColorScheme = darkColorScheme(
    primary = MonarchColors.Emerald,
    onPrimary = Color.White,
    primaryContainer = MonarchColors.VaultHigh,
    onPrimaryContainer = MonarchColors.Ink,
    secondary = MonarchColors.SystemGreen,
    onSecondary = Color.Black,
    secondaryContainer = MonarchColors.VaultHigh,
    onSecondaryContainer = MonarchColors.Ink,
    tertiary = MonarchColors.SovereignGold,
    onTertiary = Color.Black,
    background = MonarchColors.Abyss,
    onBackground = MonarchColors.Ink,
    surface = MonarchColors.Vault,
    onSurface = MonarchColors.Ink,
    surfaceVariant = MonarchColors.VaultHigh,
    onSurfaceVariant = MonarchColors.InkMuted,
    outline = MonarchColors.Rune,
    error = MonarchColors.DangerRed,
    onError = Color.Black,
)

private val MonarchTypography: Typography
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
            headlineSmall = head(22),
            titleLarge = title(20, FontWeight.Bold),
            titleMedium = title(16, FontWeight.SemiBold),
            titleSmall = title(14, FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.copy(fontFamily = ChakraPetch),
            bodyMedium = base.bodyMedium.copy(fontFamily = ChakraPetch),
            bodySmall = base.bodySmall.copy(fontFamily = ChakraPetch),
            labelLarge = label(14, 4.0),
            labelMedium = label(12, 2.0),
            labelSmall = label(10, 2.0),
        )
    }

// Ink edges everywhere except extraSmall.
//
// The original rule was "corners under 8dp stay geometric, because the wander
// eats a small corner". Bounding the wander by the surface's short side
// removed that reason, so 6dp pills were inked too rather than being kept as
// silent exceptions. What survives is a different limit: a facet is ~55px, so
// a surface much smaller than that can only ever get the 3-facet minimum and
// renders as a lopsided polygon. extraSmall exists for those clips - icon
// touch targets and 2-3dp inset chips - not as a style choice.
//
// Distinct salts stop a button, a chip and a panel from sharing one traced
// outline.
private val MonarchShapes = Shapes(
    extraSmall = CutCornerShape(3.dp),
    small = InkEdgeShape(salt = 11),
    medium = InkEdgeShape(salt = 23),
    large = InkEdgeShape(salt = 37),
    extraLarge = InkEdgeShape(salt = 53),
)

/** Monarch is always dark — the System never sleeps. Dynamic color is deliberately unused. */
@Composable
fun MonarchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MonarchColorScheme,
        typography = MonarchTypography,
        shapes = MonarchShapes,
        content = content,
    )
}
