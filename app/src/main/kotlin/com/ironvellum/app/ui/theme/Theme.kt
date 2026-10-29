package com.ironvellum.app.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
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

/** Appearance > Theme. System follows the phone's dark mode. */
enum class ThemeMode {
    SYSTEM, DARK, LIGHT;

    /** True when Vellum (the light paper) is in force, given whether the phone is in dark mode. */
    fun isLight(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> !systemDark
        DARK -> false
        LIGHT -> true
    }
}

/** True when the phone is in dark mode: the one resolver the activity and the widgets share. */
fun isSystemDark(context: android.content.Context): Boolean =
    (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

/** The structural palette of one theme: paper, ink, lines and the two status tones. */
internal interface Paper {
    val Abyss: Color // deepest paper (the page)
    val Vault: Color // panel paper
    val VaultHigh: Color // raised paper (navigation, sheets)
    val Raised: Color // a button's fill: a step off both the page and a card (the chip)
    val Ink: Color
    val InkMuted: Color
    val Rune: Color // structure lines
    val Bracket: Color // brush ticks
    val DangerRed: Color
    val Caution: Color
    val Wells: WellTones
    val PlateGround: Color // the ground under relic, crest and seal art
    val PlateEdge: Color // the rim of a tile whose reward is not held yet
    val PlateDeep: Color // the infill a crest mark draws over its plate
    val Silhouette: Color // a reward not held yet: its flat outline
    val SilhouetteGround: Color
    val UnheldName: Color // the name under such a reward
    val UnheldNameBright: Color
    val RingTick: Color // the Veil ring's quiet ticks
    val FillGlow: Color // the XP page's glow while it fills
    val LevelGlow: Color // the XP page's glow at a level moment
    val OnGoldFill: Color // text on a gold gradient button
    val Scrim: Color // behind a dialog or sheet
}

/** The tinted grounds that were hard-coded greens: a chip, a readout, a quiet or disabled gold button. */
internal class WellTones(
    val top: Color, val bottom: Color,
    val disabledTop: Color, val disabledBottom: Color,
    val quietTop: Color, val quietBottom: Color,
    val readout: Color, val flat: Color,
)

internal object DarkPaper : Paper {
    // Paper tones: warm charcoal, not blue-grey. Ink needs something to sit on.
    override val Abyss = Color(0xFF0C0C0B)
    override val Vault = Color(0xFF171715)
    override val VaultHigh = Color(0xFF1E1E1B)
    override val Raised = Color(0xFF262623) // 1.29:1 on the page, 1.18:1 on a card
    // Ink itself: the structural palette is monochrome by design.
    // Exactly tools/art.py INK_TINT (232, 232, 228): generated artwork and UI
    // text must be the same ink, or the "one hand" claim is decorative only.
    override val Ink = Color(0xFFE8E8E4)
    override val InkMuted = Color(0xFFA3A099)
    override val Rune = Color(0xFF32302B)
    override val Bracket = Color(0xFF4A473F)
    override val DangerRed = Color(0xFFEF5350)
    // Heavy: a soft recovery flag on a muscle (coverage), never danger and never earned.
    // Amber, fixed - the accent palette is the lifter's to change, a warning is not.
    override val Caution = Color(0xFFE0B25A)
    override val Wells = WellTones(
        top = Color(0xFF151C19), bottom = Color(0xFF0F1412),
        disabledTop = Color(0xFF1E3026), disabledBottom = Color(0xFF16211B),
        quietTop = Color(0xFF141C18), quietBottom = Color(0xFF101714),
        readout = Color(0xFF101614), flat = Color(0xFF16201C),
    )
    override val PlateGround = Color(0xFF121211)
    override val PlateEdge = Color(0xFF232320)
    override val PlateDeep = Color(0xFF0B0B0D)
    override val Silhouette = Color(0xFF3A3935)
    override val SilhouetteGround = Color(0xFF0F0F0E)
    override val UnheldName = Color(0xFF8A8780)
    override val UnheldNameBright = Color(0xFFC9C6BE)
    override val RingTick = Color(0xFF5C5850)
    override val FillGlow = Color(0xFF1B2A22)
    override val LevelGlow = Color(0xFF3A2C10)
    override val OnGoldFill = Color(0xFF1A1405)
    override val Scrim = Color.Black.copy(alpha = 0.6f)
}

/**
 * Vellum, rebuilt (owner-approved 2026-10-23, option B): warm parchment layered in three steps, so a card is plainly
 * lighter than the page and a raised sheet sits between them. Each hex is the sRGB of the OKLCH (L, C, h) in its comment.
 */
internal object LightPaper : Paper {
    override val Abyss = Color(0xFFF7F3EA) // oklch(.965 .013 87) page
    override val Vault = Color(0xFFFEFBF5) // oklch(.988 .008 85) card
    override val VaultHigh = Color(0xFFF3ECDF) // oklch(.945 .019 83) card-high
    override val Raised = Color(0xFFE3D8C5) // a step darker than card-high: 1.27:1 on the page, 1.37:1 on a card
    override val Ink = Color(0xFF241B15) // oklch(.23 .018 55)
    override val InkMuted = Color(0xFF65584C) // oklch(.47 .025 65)
    override val Rune = Color(0xFFC9BDA9) // oklch(.803 .031 81) line
    override val Bracket = Color(0xFFAA9C8A) // oklch(.70 .03 75)
    override val DangerRed = Color(0xFFBE2323) // oklch(.52 .19 27)
    // shortcut: the approved caution (oklch .56 .16 34, #C0482D) reads 4.12:1 on the page; this is the same hue and
    // chroma at L .538, the smallest drop that reaches 4.5:1. Back to .56 if caution stops carrying text.
    override val Caution = Color(0xFFB84026) // oklch(.538 .16 34) red-orange, away from gold
    override val Wells = WellTones(
        top = Color(0xFFF8F4EC), bottom = Color(0xFFF4EEE1), // oklch(.968 .012 84), (.95 .018 83)
        disabledTop = Color(0xFFEAE2D4), disabledBottom = Color(0xFFE2D9CB), // oklch(.915 .02 82), (.89 .022 80)
        quietTop = Color(0xFFF5F0E6), quietBottom = Color(0xFFF1EADE), // oklch(.955 .014 84), (.94 .018 83)
        readout = Color(0xFFF2ECE1), flat = Color(0xFFF2ECE1), // oklch(.945 .016 83)
    )
    override val PlateGround = Color(0xFFFAF6EE) // oklch(.975 .012 85)
    override val PlateEdge = Color(0xFFDFD6C8) // oklch(.88 .022 80)
    override val PlateDeep = Color(0xFFF7F3EA) // the page, as a crest mark's infill
    override val Silhouette = Color(0xFFC7BCAC) // oklch(.80 .026 78)
    override val SilhouetteGround = Color(0xFFF2ECE1) // oklch(.945 .016 84)
    override val UnheldName = Color(0xFF6E6155) // oklch(.50 .025 65)
    override val UnheldNameBright = Color(0xFF51453C) // oklch(.40 .022 62)
    override val RingTick = Color(0xFFA39684) // oklch(.68 .03 76)
    override val FillGlow = Color(0xFFCFEED6) // oklch(.92 .045 152) the accent tint
    override val LevelGlow = Color(0xFFF4DFB4) // oklch(.91 .06 85) a gold wash
    override val OnGoldFill = Color(0xFFFEFBF5) // the card colour
    override val Scrim = Color(0xFF241B15).copy(alpha = 0.4f)
}

object IronvellumColors {
    // Snapshot state keeps existing drawing callbacks and non-composable
    // helpers reactive without a second palette at hundreds of call sites.
    // Each theme keeps its own accent pair; [accents] is the one in force.
    internal var light by androidx.compose.runtime.mutableStateOf(false)
    internal var darkAccents by androidx.compose.runtime.mutableStateOf(AccentPalette.Default)
    internal var lightAccents by androidx.compose.runtime.mutableStateOf(AccentPalette.LightDefault)
    internal val accents: AccentPalette get() = if (light) lightAccents else darkAccents

    private val paper: Paper get() = if (light) LightPaper else DarkPaper
    private val lightRoles get() = lightAccents.lightAccent()

    val Abyss get() = paper.Abyss
    val Vault get() = paper.Vault
    val VaultHigh get() = paper.VaultHigh
    val Raised get() = paper.Raised

    // Colour survives ONLY where it carries information a monochrome UI would
    // destroy: progression, rarity, earned moments, danger.
    // On Vellum the accent is a deep shade: Emerald is the button fill, and the
    // text-weight roles (Bright, SystemGreen, Deep) are its deeper text shade.
    val Emerald get() = if (light) Color(lightRoles.fill) else Color(darkAccents.primary) // XP and success
    val EmeraldBright get() = if (light) Color(lightRoles.text) else Color(darkAccents.bright)
    val EmeraldDeep get() = if (light) Color(lightRoles.text) else Color(darkAccents.deep)
    /** The accent as text or a glyph: its text shade on Vellum (the fill is under 4.5:1 on the page), the accent itself in dark. */
    val EmeraldText get() = if (light) Color(lightRoles.text) else Color(darkAccents.primary)
    val SystemGreen get() = if (light) Color(lightRoles.text) else Color(darkAccents.muted) // muted green accent
    val SovereignGold get() = if (light) Color(lightRoles.reward) else Color(darkAccents.secondary) // earned moments only
    /** Text on an Emerald fill. */
    val OnEmerald get() = Color(if (light) lightAccentForeground(lightRoles.fill) else accentForeground(darkAccents.primary))
    /** Text on a SovereignGold fill. */
    val OnGold get() = Color(if (light) lightAccentForeground(lightRoles.reward) else accentForeground(darkAccents.secondary))
    /** A wash of the accent: the Today box's ground and edge. */
    val AccentTint get() = if (light) Color(lightRoles.tint) else Emerald.copy(alpha = 0.10f)
    val AccentTintBorder get() = if (light) Color(lightRoles.border) else Emerald.copy(alpha = 0.35f)
    val DangerRed get() = paper.DangerRed
    val Caution get() = paper.Caution

    val Ink get() = paper.Ink
    val InkMuted get() = paper.InkMuted
    val Rune get() = paper.Rune // structure lines
    val Bracket get() = paper.Bracket // brush ticks
    internal val Wells get() = paper.Wells
    internal val PlateGround get() = paper.PlateGround
    internal val PlateEdge get() = paper.PlateEdge
    internal val PlateDeep get() = paper.PlateDeep
    internal val Silhouette get() = paper.Silhouette
    internal val SilhouetteGround get() = paper.SilhouetteGround
    internal val UnheldName get() = paper.UnheldName
    internal val UnheldNameBright get() = paper.UnheldNameBright
    internal val RingTick get() = paper.RingTick
    /** The XP page's glow: dark emerald while it fills (the accent's tint on Vellum), gold at a level. */
    internal val FillGlow get() = if (light) Color(lightRoles.tint) else paper.FillGlow
    internal val LevelGlow get() = paper.LevelGlow
    internal val OnGoldFill get() = paper.OnGoldFill
    internal val Scrim get() = paper.Scrim

    /** A chosen colour as it will draw: on Vellum a custom one is deepened until it reads on the paper. */
    fun swatch(colour: Int): Color = Color(if (light) deepenForLight(colour) else colour)
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

private fun ironvellumColorScheme(light: Boolean) = if (light) {
    lightColorScheme(
        primary = IronvellumColors.Emerald,
        onPrimary = IronvellumColors.OnEmerald,
        primaryContainer = IronvellumColors.VaultHigh,
        onPrimaryContainer = IronvellumColors.Ink,
        secondary = IronvellumColors.SystemGreen,
        onSecondary = Color(lightAccentForeground(IronvellumColors.SystemGreen.toArgb())),
        secondaryContainer = IronvellumColors.VaultHigh,
        onSecondaryContainer = IronvellumColors.Ink,
        tertiary = IronvellumColors.SovereignGold,
        onTertiary = IronvellumColors.OnGold,
        background = IronvellumColors.Abyss,
        onBackground = IronvellumColors.Ink,
        surface = IronvellumColors.Vault,
        onSurface = IronvellumColors.Ink,
        surfaceVariant = IronvellumColors.VaultHigh,
        onSurfaceVariant = IronvellumColors.InkMuted,
        outline = IronvellumColors.Rune,
        outlineVariant = IronvellumColors.Rune,
        error = IronvellumColors.DangerRed,
        onError = IronvellumColors.Vault,
    )
} else {
    val palette = IronvellumColors.darkAccents
    darkColorScheme(
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
}

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

/**
 * Ironvellum is dark ("the Ledger never sleeps") or Vellum, a warm parchment light theme; [light] is the resolved
 * Appearance > Theme choice. Dynamic color is deliberately unused.
 */
@Composable
fun IronvellumTheme(light: Boolean = false, content: @Composable () -> Unit) {
    // Every token is a getter over this snapshot state, so a flip re-draws the whole tree, including dialogs and
    // sheets, which compose in windows of their own. The store and MainActivity.onCreate set it before the first
    // frame; this follows a later change (the phone's dark mode flipping under System).
    SideEffect { if (IronvellumColors.light != light) IronvellumColors.light = light }
    // The text scale is pinned in MainActivity.attachBaseContext, which every
    // window of the app inherits — including dialogs, which compose in their
    // own window and so ignored a CompositionLocal installed here.
    MaterialTheme(
        colorScheme = ironvellumColorScheme(light),
        typography = IronvellumTypography,
        shapes = IronvellumShapes,
    ) {
        CompositionLocalProvider(LocalIndication provides InkPressIndication, content = content)
    }
}

/**
 * Caps the system font scale at [max] for [content]. The app honours the system font scale everywhere, so this is
 * for the rare element that cannot grow: a fixed-size slot such as the bottom bar labels, whose box the layout
 * around it is built on. Every use says in a comment why that slot cannot grow; prefer letting the container grow.
 */
@Composable
fun CappedFontScale(max: Float = 1.3f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= max) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(density.density, max), content = content)
    }
}
