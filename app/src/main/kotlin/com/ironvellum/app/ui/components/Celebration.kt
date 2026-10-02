package com.ironvellum.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ironvellum.app.domain.ArmyClass
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.TitleDef
import com.ironvellum.app.domain.TitleRarity
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkArc
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkDot
import com.ironvellum.app.ui.theme.inkStroke
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

/**
 * How the Ledger writes a moment down. The four rarities escalate the ink -
 * bone, moss with an iron seal, emerald that bleeds, gold leaf - and a level
 * has a page of its own that turns, amending the name when it crosses a tier.
 */
enum class Reveal(val ink: Color, internal val millis: Int) {
    Common(IronvellumColors.InkMuted, 1_100),
    Rare(IronvellumColors.SystemGreen, 1_700),
    Fabled(IronvellumColors.Emerald, 1_900),
    Masterwork(IronvellumColors.SovereignGold, 2_300),
    LevelUp(IronvellumColors.Emerald, 1_300),
    ;

    internal val sealed: Boolean get() = this == Rare || this == Fabled || this == Masterwork
}

fun TitleRarity.reveal(): Reveal = when (this) {
    TitleRarity.Common -> Reveal.Common
    TitleRarity.Rare -> Reveal.Rare
    TitleRarity.Epic -> Reveal.Fabled
    TitleRarity.Masterwork -> Reveal.Masterwork
}

/** Anything worth a full-screen moment: deeds, techniques, levels, inscriptions. */
data class Achievement(
    val banner: String,
    val name: String,
    /** The small line above the name: a rarity, a tier, "LEVEL". */
    val tagline: String = "",
    val subtitle: String = "",
    val xp: Int? = null,
    val notes: List<String> = emptyList(),
    /** Overrides the reveal's own ink; null keeps it. */
    val accent: Color? = null,
    /**
     * Seed for procedurally generated art shown above the name. Relics have
     * continuous values, so their art cannot ship as an asset — it is composed
     * from this seed instead. Null = a text-only moment.
     */
    val sigilSeed: String? = null,
    val reveal: Reveal = Reveal.Rare,
    /** The Ledger's line under the banner, in its own voice. */
    val narrator: String = "",
    /** A deed's title, which the reveal offers to wear. */
    val titleId: String? = null,
    /** What this replaces: the old level. */
    val from: String? = null,
    /** A level that crosses a tier: the old ascension and the new, on the same page. */
    val ascension: Pair<String, String>? = null,
)

/** The one way a deed is announced, wherever it was earned. */
fun deedAchievement(def: TitleDef, sex: Sex): Achievement = Achievement(
    banner = "DEED EARNED",
    name = def.name,
    tagline = def.rarity.label.uppercase(),
    subtitle = def.describeFor(sex).uppercase(),
    reveal = def.rarity.reveal(),
    narrator = if (def.rarity == TitleRarity.Masterwork) "THE LEDGER GILDS ITS PAGE" else "THE LEDGER RECORDS A DEED",
    titleId = def.id,
)

/**
 * Ascension is a band of levels, so a level that crosses a tier announces it
 * on its own page rather than as a second moment.
 */
fun levelUpAchievement(levelBefore: Int, levelAfter: Int, totalXp: Long): Achievement {
    val tierBefore = ArmyClass.forLevel(levelBefore).title
    val tierAfter = ArmyClass.forLevel(levelAfter).title
    val ascends = tierAfter != tierBefore
    return Achievement(
        banner = "LEVEL UP",
        name = "$levelAfter",
        tagline = "LEVEL",
        subtitle = String.format(Locale.ENGLISH, "%,d XP TOTAL", totalXp),
        reveal = Reveal.LevelUp,
        narrator = if (ascends) "THE LEDGER AMENDS YOUR NAME" else "THE LEDGER TURNS A PAGE",
        from = "$levelBefore",
        ascension = (tierBefore to tierAfter).takeIf { ascends },
    )
}

/** 0 before [a], 1 after [b], linear between: one beat of a reveal's timeline. */
private fun beat(p: Float, a: Float, b: Float): Float = ((p - a) / (b - a)).coerceIn(0f, 1f)

private const val SEAL_AT = 0.72f
private const val SEAL_LANDS = 0.84f

/**
 * One celebration engine for every achievement in the app, laid out like the
 * victory screen it follows: header, an ink panel, two buttons. The Ledger
 * writes each moment into the panel - ink bleeds, the name is brushed in, a
 * seal is pressed - and each physical act is felt as a haptic.
 *
 * A tap on the page finishes the writing; the next tap moves on. Several
 * moments page through with "Next (1/3)".
 */
@Composable
fun AchievementOverlay(
    items: List<Achievement>,
    onDone: () -> Unit,
    /**
     * Makes each note a button; null keeps notes as plain text. Given the item
     * and the note's index, then the overlay moves on as a tap would.
     */
    onNote: ((item: Achievement, noteIndex: Int) -> Unit)? = null,
    /** The title worn now, so a deed already worn says WORN instead of offering it. */
    wornTitleId: String? = null,
    /** Wears a deed's title; null hides the button. */
    onWear: ((titleId: String) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    var page by remember(items) { mutableIntStateOf(0) }
    val item = items[page.coerceIn(0, items.lastIndex)]
    val last = page >= items.lastIndex
    val progress = remember(items, page) { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var wornHere by remember(items) { mutableStateOf<String?>(null) }

    LaunchedEffect(items, page) {
        launch { progress.animateTo(1f, tween(item.reveal.millis, easing = LinearEasing)) }
        var at = 0f
        for ((beatAt, type) in hapticsFor(item.reveal)) {
            delay(((beatAt - at) * item.reveal.millis).toLong())
            at = beatAt
            // A tap that finished the writing early also silences what is left.
            if (!progress.isRunning) break
            haptic.performHapticFeedback(type)
        }
    }

    fun advance() {
        when {
            progress.value < 1f -> scope.launch { progress.snapTo(1f) }
            !last -> page++
            else -> onDone()
        }
    }

    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val p = progress.value
        val ink = item.accent ?: item.reveal.ink
        Box(
            Modifier
                .fillMaxSize()
                .background(IronvellumColors.Abyss)
                .ledgerDawn(if (item.reveal == Reveal.Masterwork) 0.24f else 0.16f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { advance() },
        ) {
            LedgerEmbers()
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(24.dp))
                        LedgerHeader(
                            banner = item.banner,
                            narrator = item.narrator,
                            color = if (item.reveal == Reveal.LevelUp) IronvellumColors.Emerald else IronvellumColors.SovereignGold,
                        )
                        Spacer(Modifier.height(20.dp))
                        // The seal's landing jolts the page it is pressed into.
                        val jolt = beat(p, SEAL_LANDS, SEAL_LANDS + 0.1f).takeIf { item.reveal.sealed && it in 0.001f..0.999f }
                            ?.let { sin(it * 18f) * (1f - it) * 3f } ?: 0f
                        InkPanel(
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer { translationX = jolt.dp.toPx() },
                            accent = ink,
                        ) {
                            when (item.reveal) {
                                Reveal.LevelUp -> PageTurn(item, p)
                                else -> Inscription(item, p, ink)
                            }
                        }
                        Footnotes(item, p, wornHere == item.titleId || item.titleId == wornTitleId, onWear != null)
                        item.notes.forEachIndexed { index, note ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = IronvellumColors.SystemGreen,
                                textAlign = TextAlign.Center,
                                textDecoration = if (onNote != null) TextDecoration.Underline else null,
                                modifier = if (onNote != null) {
                                    Modifier
                                        .heightIn(min = 48.dp)
                                        .clickable(role = Role.Button, onClickLabel = "Open") {
                                            // Moves on like any tap, so the pages
                                            // still to come are not skipped.
                                            onNote(item, index)
                                            if (!last) page++ else onDone()
                                        }
                                        .wrapContentHeight()
                                } else {
                                    Modifier
                                },
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
                val titleId = item.titleId
                if (titleId != null && onWear != null) {
                    val worn = wornHere == titleId || titleId == wornTitleId
                    IronvellumButton(
                        label = if (worn) "Worn" else "Wear title",
                        quiet = true,
                        enabled = !worn,
                        onClick = {
                            onWear(titleId)
                            wornHere = titleId
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                }
                IronvellumButton(
                    if (!last) "Next (${page + 1}/${items.size})" else "Continue",
                    gold = true,
                    onClick = { if (!last) page++ else onDone() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Brush ticks while the name is written, then the seal's weight on landing. */
private fun hapticsFor(reveal: Reveal): List<Pair<Float, HapticFeedbackType>> {
    val writing = listOf(0.2f, 0.32f, 0.44f).map { it to HapticFeedbackType.SegmentFrequentTick }
    return when (reveal) {
        Reveal.Common -> writing + (0.6f to HapticFeedbackType.SegmentTick)
        Reveal.Rare -> writing + (SEAL_LANDS to HapticFeedbackType.Confirm)
        Reveal.Fabled -> writing + (SEAL_LANDS to HapticFeedbackType.Confirm) + (SEAL_LANDS + 0.07f to HapticFeedbackType.Confirm)
        Reveal.Masterwork -> writing + (SEAL_LANDS to HapticFeedbackType.LongPress)
        Reveal.LevelUp -> listOf(0.3f to HapticFeedbackType.SegmentTick, 0.75f to HapticFeedbackType.Confirm)
    }
}

/** The big word, its rule and the Ledger's line: the victory screen's header, shared. */
@Composable
internal fun LedgerHeader(banner: String, narrator: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            banner,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            letterSpacing = IronvellumTracking.ScreenTitle,
            color = color,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .width(56.dp)
                .height(2.dp)
                .background(color.copy(alpha = 0.6f)),
        )
        if (narrator.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                narrator,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                letterSpacing = IronvellumTracking.InlineLabel,
                color = IronvellumColors.InkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A deed, technique or inscription written into the page: bleed, name, underline, seal. */
@Composable
private fun Inscription(item: Achievement, p: Float, ink: Color) {
    val reveal = item.reveal
    Box(Modifier.fillMaxWidth()) {
        InkBleed(Modifier.matchParentSize(), reveal, ink, beat(p, 0f, 0.35f), item.name.hashCode())
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (item.tagline.isNotBlank()) {
                Text(
                    item.tagline,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = IronvellumTracking.SectionHeader,
                    color = ink,
                    textAlign = TextAlign.Center,
                )
            }
            item.sigilSeed?.let { seed ->
                // The reveal's centrepiece: art generated from the reward
                // itself, so a relic draw looks like a find.
                Spacer(Modifier.height(10.dp))
                RelicSigil(
                    name = seed,
                    accent = ink,
                    modifier = Modifier
                        .size(88.dp)
                        .graphicsLayer { alpha = beat(p, 0.05f, 0.4f) },
                )
            }
            Spacer(Modifier.height(6.dp))
            WrittenName(item.name.uppercase(), beat(p, 0.1f, 0.55f), reveal, ink, sheen = beat(p, 0.55f, 1f))
            Canvas(
                Modifier
                    .width(132.dp)
                    .height(12.dp),
            ) {
                val run = beat(p, 0.5f, 0.68f)
                if (run > 0f) {
                    inkStroke(
                        from = Offset(0f, size.height / 2f),
                        to = Offset(size.width * run, size.height / 2f),
                        color = ink.copy(alpha = 0.8f),
                        widthPx = (if (reveal == Reveal.Common) 2.2f else 3f).dp.toPx(),
                        seed = item.name.length,
                    )
                }
            }
            if (item.subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.InkMuted,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (reveal.sealed) Seal(Modifier.align(Alignment.TopEnd), reveal, ink, beat(p, SEAL_AT, SEAL_LANDS))
    }
}

/** The name brushed in left to right; Masterwork's is gold leaf with a sheen passing over it. */
@Composable
private fun WrittenName(text: String, written: Float, reveal: Reveal, ink: Color, sheen: Float) {
    val base = MaterialTheme.typography.headlineSmall
    val style = if (reveal == Reveal.Masterwork) {
        val x = -400f + 1_400f * sheen
        base.copy(
            brush = Brush.linearGradient(
                listOf(GoldLeafDark, GoldLeafLight, GoldLeafDark),
                start = Offset(x, 0f),
                end = Offset(x + 260f, 0f),
            ),
        )
    } else {
        base.copy(color = nameInk(reveal, ink))
    }
    Text(
        text,
        style = style,
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.Bold,
        letterSpacing = IronvellumTracking.InlineLabel,
        textAlign = TextAlign.Center,
        modifier = Modifier.drawWithContent {
            clipRect(right = size.width * written) { this@drawWithContent.drawContent() }
        },
    )
}

private val GoldLeafDark = Color(0xFFC98A2B)
private val GoldLeafLight = Color(0xFFFFF1C2)

private fun nameInk(reveal: Reveal, ink: Color): Color = when (reveal) {
    Reveal.Common -> IronvellumColors.Ink
    Reveal.Fabled -> IronvellumColors.EmeraldBright
    else -> ink
}

/**
 * Ink soaking into the page behind the name: one soft wash, wider and stronger
 * the rarer the deed. Fabled and Masterwork also throw a little spatter.
 */
@Composable
private fun InkBleed(modifier: Modifier, reveal: Reveal, ink: Color, spread: Float, seed: Int) {
    val (alpha, reach, spatter) = when (reveal) {
        Reveal.Common -> Triple(0.07f, 0.45f, 0)
        Reveal.Rare -> Triple(0.12f, 0.6f, 0)
        Reveal.Fabled -> Triple(0.2f, 0.85f, 6)
        else -> Triple(0.16f, 0.85f, 5)
    }
    val flecks = remember(seed, spatter) {
        val rng = Random(seed)
        // Out at the sides only: a fleck on the subtitle reads as a stray mark.
        List(spatter) { i ->
            val side = if (i % 2 == 0) -1f else 1f
            Triple(side * (0.62f + rng.nextFloat() * 0.3f), rng.nextFloat() * 1.6f - 0.8f, 1.5f + rng.nextFloat() * 2f)
        }
    }
    Canvas(modifier) {
        if (spread <= 0f) return@Canvas
        val c = Offset(size.width / 2f, size.height / 2f)
        // An ellipse that fades out inside the panel: a round wash clipped by
        // the panel's edges read as a hard-edged band.
        scale(scaleX = 1f, scaleY = size.height / size.width, pivot = c) {
            // The gradient reaches transparent inside its radius, so the
            // rectangle it fills never shows an edge.
            drawRect(
                Brush.radialGradient(
                    listOf(ink.copy(alpha = alpha), ink.copy(alpha = alpha * 0.4f), Color.Transparent),
                    center = c,
                    radius = (size.width * 0.5f * reach * spread).coerceAtLeast(1f),
                ),
                topLeft = Offset(0f, c.y - size.width / 2f),
                size = androidx.compose.ui.geometry.Size(size.width, size.width),
            )
        }
        // Spatter lands with the seal, flung out from its corner.
        val fling = beatOf(spread)
        flecks.forEachIndexed { i, (dx, dy, r) ->
            inkDot(
                center = Offset(c.x + dx * size.width * 0.42f, c.y + dy * size.height * 0.45f),
                radius = r.dp.toPx(),
                color = ink.copy(alpha = 0.55f * fling),
                seed = seed + i,
            )
        }
    }
}

/** Spatter shows only once the wash has nearly spread. */
private fun beatOf(spread: Float): Float = ((spread - 0.7f) / 0.3f).coerceIn(0f, 1f)

/** The seal pressed into the panel's corner: two brushed rings and a mark for its tier. */
@Composable
private fun Seal(modifier: Modifier, reveal: Reveal, ink: Color, pressed: Float) {
    Canvas(
        modifier
            .size(44.dp)
            .graphicsLayer {
                val s = 2.4f - 1.4f * pressed
                scaleX = s
                scaleY = s
                rotationZ = -28f + 18f * pressed
                alpha = if (pressed > 0f) (pressed * 3f).coerceAtMost(1f) else 0f
            },
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f - 2.dp.toPx()
        if (reveal == Reveal.Masterwork) inkDot(c, r, Color(0xFF2A1D06), seed = 7)
        inkArc(c, r, 0f, 360f, ink, 2.dp.toPx(), seed = 3, taperEnds = false)
        inkArc(c, r * 0.64f, 20f, 330f, ink.copy(alpha = 0.8f), 1.dp.toPx(), seed = 5)
        sealMark(reveal, c, r * 0.5f, ink)
    }
}

private fun DrawScope.sealMark(reveal: Reveal, c: Offset, r: Float, ink: Color) {
    val w = 1.6.dp.toPx()
    when (reveal) {
        Reveal.Rare -> {
            inkStroke(Offset(c.x, c.y - r), Offset(c.x, c.y + r), ink, w, seed = 1)
            inkStroke(Offset(c.x - r * 0.85f, c.y - r * 0.5f), Offset(c.x + r * 0.85f, c.y + r * 0.5f), ink, w, seed = 2)
            inkStroke(Offset(c.x + r * 0.85f, c.y - r * 0.5f), Offset(c.x - r * 0.85f, c.y + r * 0.5f), ink, w, seed = 3)
        }
        Reveal.Fabled -> drawPath(
            Path().apply {
                moveTo(c.x, c.y - r)
                lineTo(c.x + r * 0.45f, c.y)
                lineTo(c.x, c.y + r)
                lineTo(c.x - r * 0.45f, c.y)
                close()
            },
            ink,
        )
        Reveal.Masterwork -> drawPath(
            Path().apply {
                moveTo(c.x - r, c.y + r * 0.55f)
                lineTo(c.x - r * 0.75f, c.y - r * 0.45f)
                lineTo(c.x - r * 0.3f, c.y + r * 0.1f)
                lineTo(c.x, c.y - r * 0.75f)
                lineTo(c.x + r * 0.3f, c.y + r * 0.1f)
                lineTo(c.x + r * 0.75f, c.y - r * 0.45f)
                lineTo(c.x + r, c.y + r * 0.55f)
                close()
            },
            ink,
        )
        else -> {
            inkStroke(Offset(c.x, c.y - r), Offset(c.x, c.y + r), ink, w, seed = 4)
            inkStroke(Offset(c.x - r, c.y), Offset(c.x + r, c.y), ink, w, seed = 5)
        }
    }
}

/** A level: the old page turns away and the new number is underneath. */
@Composable
private fun PageTurn(item: Achievement, p: Float) {
    val turn = beat(p, 0.2f, 0.75f)
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .fillMaxWidth()
            .height(124.dp),
    ) {
        LevelLeaf(item.tagline, item.name, IronvellumColors.Emerald, Modifier.graphicsLayer { alpha = beat(p, 0.5f, 0.75f) })
        LevelLeaf(
            item.tagline,
            item.from.orEmpty(),
            IronvellumColors.InkMuted,
            Modifier
                .padding(6.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationY = -180f * turn
                    cameraDistance = 12f * density
                    alpha = if (turn < 0.5f) 1f else 0f
                }
                .background(IronvellumColors.VaultHigh, shape)
                .inkBorder(IronvellumColors.Bracket, shape, 1.dp),
        )
    }
    if (item.subtitle.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(
            item.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    item.ascension?.let { (old, new) -> Amendment(old, new, p) }
}

@Composable
private fun LevelLeaf(label: String, number: String, color: Color, modifier: Modifier) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            letterSpacing = IronvellumTracking.SectionHeader,
            color = IronvellumColors.InkMuted,
        )
        Text(
            number,
            style = MaterialTheme.typography.displayMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

/** The level's tier changing: the old ascension struck through, the new one written beneath. */
@Composable
private fun Amendment(old: String, new: String, p: Float) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "YOU ASCEND",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            letterSpacing = IronvellumTracking.SectionHeader,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.height(8.dp))
        val strike = beat(p, 0.45f, 0.65f)
        Text(
            old.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = IronvellumTracking.InlineLabel,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.drawWithContent {
                drawContent()
                if (strike > 0f) {
                    val y = size.height * 0.55f
                    inkStroke(
                        Offset(-4.dp.toPx(), y),
                        Offset(-4.dp.toPx() + (size.width + 8.dp.toPx()) * strike, y - 2.dp.toPx()),
                        IronvellumColors.Ink,
                        2.4.dp.toPx(),
                        seed = old.length,
                    )
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        WrittenName(new.uppercase(), beat(p, 0.6f, 0.9f), Reveal.LevelUp, IronvellumColors.SovereignGold, sheen = 0f)
    }
}

/** Under the panel: what the moment paid, and whether its title is worn. */
@Composable
private fun Footnotes(item: Achievement, p: Float, worn: Boolean, canWear: Boolean) {
    val xp = item.xp
    if (xp != null && xp > 0) {
        Spacer(Modifier.height(14.dp))
        Text(
            "+${(xp * beat(p, 0.3f, 0.9f)).toInt()} XP",
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = IronvellumColors.Emerald,
        )
    }
    if (item.titleId != null && canWear) {
        Spacer(Modifier.height(12.dp))
        Text(
            if (worn) "Worn on your folio" else "You may wear ${item.name}",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** The low gold light behind a Ledger page's header; the only warm light on screen. */
internal fun Modifier.ledgerDawn(strength: Float): Modifier = drawBehind {
    drawRect(
        Brush.radialGradient(
            listOf(IronvellumColors.SovereignGold.copy(alpha = strength), Color.Transparent),
            center = Offset(size.width / 2f, 0f),
            radius = size.width,
        ),
    )
}

/** A few gold embers drifting up behind a Ledger page; quiet, never over the text. */
@Composable
internal fun LedgerEmbers() {
    val drift = rememberInfiniteTransition(label = "embers")
    val rise by drift.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(4200)),
        label = "rise",
    )
    Canvas(Modifier.fillMaxSize()) {
        repeat(12) { i ->
            val seed = i * 0.618f
            val x = ((seed * 7.13f) % 1f) * size.width
            val cycle = (rise + seed) % 1f
            inkDot(
                center = Offset(x, cycle * size.height),
                radius = (2f + (i % 3)) * 1.6f,
                color = IronvellumColors.SovereignGold.copy(alpha = (1f - cycle) * 0.35f),
                seed = i,
            )
        }
    }
}
