package com.ironvellum.app.ui.components

import android.content.Context
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkHairline
import androidx.core.content.edit
import kotlinx.coroutines.delay

/**
 * The one sheet every "info" pop-up uses, so an explainer, an exercise guide, a
 * technique and a deed all read the same: a header, an optional strip of
 * figures, a few labelled sections, and the actions pinned underneath.
 *
 * Layout, top to bottom, always in this order:
 *  1. Header: [title] (a heading for screen readers), optional [subtitle] and a
 *     row of fact [chips].
 *  2. Optional [summary] strip: [InfoFigures] or [InfoProgress].
 *  3. Sections from [content], in the order they are declared, each under a
 *     PanelLabel. A long one is folded behind "Show more".
 *  4. [actions], at most two buttons, pinned: they stay put while 1-3 scroll.
 *
 * [InfoSheetSize.Full] is for rich detail (up to about 90% of the screen);
 * [InfoSheetSize.Compact] is for "What's this?" explainers (up to 60%, nothing
 * folded). Both are only as tall as their content. The sheet dismisses by swipe,
 * back or a tap outside, and sits above the navigation bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = IronvellumColors.InkMuted,
    chips: List<InfoChip> = emptyList(),
    size: InfoSheetSize = InfoSheetSize.Full,
    titleColor: Color = IronvellumColors.Ink,
    summary: (@Composable ColumnScope.() -> Unit)? = null,
    actions: List<InfoAction> = emptyList(),
    /**
     * Swipeable pages instead of one long scroll: the header and [actions] stay put, a tab row
     * and a pager sit between them, and the sheet holds its full height. Empty pages are
     * dropped and a single survivor renders flat, without tabs. Replaces [summary] and
     * [content] when two or more pages remain; give each page its own summary.
     */
    pages: List<InfoPage> = emptyList(),
    content: InfoSheetScope.() -> Unit = {},
) {
    require(actions.size <= 2) { "An info sheet pins at most two actions" }
    val compact = size == InfoSheetSize.Compact
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * size.maxHeightFraction
    // An empty page is dropped; a lone survivor is not worth a tab row, so it renders flat.
    val built = pages
        .map { BuiltPage(it.label, it.summary, InfoSheetBuilder().apply(it.content).sections) }
        .filter { it.sections.isNotEmpty() || it.summary != null }
    val paged = built.size >= 2
    val flat = built.singleOrNull() ?: BuiltPage("", summary, InfoSheetBuilder().apply(content).sections)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Wide enough to be full width on a tablet or a landscape phone too.
        sheetMaxWidth = Dp.Unspecified,
        shape = MaterialTheme.shapes.extraSmall,
        containerColor = SheetPaper,
        contentColor = IronvellumColors.Ink,
        // The stock handle is a machined pill; the scrim, a swipe and back all dismiss.
        dragHandle = null,
    ) {
        Column(
            modifier
                .fillMaxWidth()
                // Pages hold the sheet at its full height, so changing page never makes it jump.
                .then(if (paged) Modifier.height(maxHeight) else Modifier.heightIn(max = maxHeight))
                // TalkBack announces the pane by name when it opens.
                .semantics { paneTitle = title },
        ) {
            Box(Modifier.fillMaxWidth().height(2.dp).inkHairline(IronvellumColors.Rune, thickness = 2.dp))
            if (paged) {
                val pagerState = rememberPagerState { built.size }
                val context = LocalContext.current
                // The very first paged sheet a lifter opens leans toward page 2 and back, once, so
                // the swipe is discoverable. Not under reduced motion; marked seen as it starts.
                LaunchedEffect(pagerState) {
                    if (InfoSheetHints.peeked(context) || !animatorsOn(context)) return@LaunchedEffect
                    delay(PEEK_DELAY_MS)
                    val nudge = pagerState.layoutInfo.pageSize * PEEK_FRACTION
                    if (nudge <= 0f) return@LaunchedEffect
                    InfoSheetHints.markPeeked(context)
                    pagerState.animateScrollBy(nudge, tween(PEEK_LEG_MS))
                    pagerState.animateScrollBy(-nudge, tween(PEEK_LEG_MS))
                }
                Column(Modifier.padding(horizontal = LedgerSpace.Gutter)) {
                    Spacer(Modifier.height(20.dp))
                    SheetHeader(title, subtitle, subtitleColor, chips, compact, titleColor)
                    Spacer(Modifier.height(6.dp))
                }
                InkTabbedPager(
                    labels = built.map { it.label },
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    tabsModifier = Modifier.padding(horizontal = LedgerSpace.Gutter),
                ) { index ->
                    PageBody(built[index], compact)
                }
            } else {
                val scroll = rememberScrollState()
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .fadeWhenMore(scroll.canScrollForward)
                        .nestedScroll(KeepForwardScrollInside)
                        .verticalScroll(scroll)
                        .padding(horizontal = LedgerSpace.Gutter),
                ) {
                    Spacer(Modifier.height(20.dp))
                    SheetHeader(title, subtitle, subtitleColor, chips, compact, titleColor)
                    SummaryStrip(flat.summary)
                    SectionList(flat.sections, compact)
                    Spacer(Modifier.height(20.dp))
                }
            }
            if (actions.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(1.dp).inkHairline(IronvellumColors.Rune, thickness = 1.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LedgerSpace.Gutter, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    actions.forEach { action ->
                        IronvellumButton(
                            label = action.label,
                            onClick = action.onClick,
                            enabled = action.enabled,
                            quiet = action.quiet,
                            gold = action.gold,
                            danger = action.danger,
                            modifier = Modifier.weight(1f).heightIn(min = LedgerSpace.Target),
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

private const val PEEK_DELAY_MS = 500L
private const val PEEK_LEG_MS = 300
private const val PEEK_FRACTION = 0.15f

/** Which one-time hints the lifter has already seen. */
private object InfoSheetHints {
    private const val PREFS = "info_sheet_hints"
    private const val KEY_PEEKED = "page_peek"

    fun peeked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PEEKED, false)

    fun markPeeked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_PEEKED, true) }
    }
}

private class BuiltPage(val label: String, val summary: (@Composable ColumnScope.() -> Unit)?, val sections: List<SheetSection>)

/** One page of a paged sheet: its own vertical scroll, so a bounce or a fling never reaches its neighbours. */
@Composable
private fun PageBody(page: BuiltPage, compact: Boolean) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .fadeWhenMore(scroll.canScrollForward)
            .nestedScroll(KeepForwardScrollInside)
            .verticalScroll(scroll)
            .padding(horizontal = LedgerSpace.Gutter),
    ) {
        Spacer(Modifier.height(16.dp))
        SummaryStrip(page.summary, leadingGap = false)
        SectionList(page.sections, compact, afterTabs = page.summary == null)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SummaryStrip(summary: (@Composable ColumnScope.() -> Unit)?, leadingGap: Boolean = true) {
    if (summary == null) return
    if (leadingGap) Spacer(Modifier.height(16.dp))
    val strip = MaterialTheme.shapes.small
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF101614), strip)
            .inkBorder(IronvellumColors.Rune, strip, 1.dp)
            .padding(12.dp),
        content = summary,
    )
}

/** The sections in order, each under its PanelLabel. [afterTabs] drops the gap above the first: the page already has one. */
@Composable
private fun SectionList(sections: List<SheetSection>, compact: Boolean, afterTabs: Boolean = false) {
    sections.forEachIndexed { index, section ->
        val gap = when {
            index == 0 && afterTabs -> 0.dp
            index == 0 && section.label == null -> 14.dp
            else -> 20.dp
        }
        Spacer(Modifier.height(gap))
        section.Render(index, collapsible = !compact)
    }
}

/** A short notice with one OK: the replacement for an AlertDialog that only says something. */
@Composable
fun InfoNotice(title: String, message: String, onDismiss: () -> Unit, titleColor: Color = IronvellumColors.Ink) {
    InfoSheet(
        title = title,
        onDismiss = onDismiss,
        size = InfoSheetSize.Compact,
        titleColor = titleColor,
        actions = listOf(InfoAction("OK", onDismiss, quiet = true)),
    ) {
        text(null, message)
    }
}

/** How much room a sheet may take. Both are content-sized when the content is short. */
enum class InfoSheetSize(internal val maxHeightFraction: Float) {
    /** Rich detail: an exercise, a technique, a deed, a chart. Up to about 90% of the screen. */
    Full(0.9f),

    /** A short "What's this?" explainer. Up to 60%, and nothing is folded away. */
    Compact(0.6f),
}

/** One pinned button. Quiet, gold and danger are IronvellumButton's own looks. */
class InfoAction(
    val label: String,
    val onClick: () -> Unit,
    val quiet: Boolean = false,
    val gold: Boolean = false,
    val danger: Boolean = false,
    val enabled: Boolean = true,
)

/** One swipeable page of a paged [InfoSheet]: a tab [label], an optional [summary] strip and its sections. */
class InfoPage(
    val label: String,
    val summary: (@Composable ColumnScope.() -> Unit)? = null,
    val content: InfoSheetScope.() -> Unit,
)

/** A fact in the header: "Compound", "Tier III", "Rare". The word carries the meaning; [color] only echoes it. */
class InfoChip(val text: String, val color: Color = IronvellumColors.InkMuted)

/**
 * Declares the sheet's sections. Each call adds one, in order. [label] is the
 * PanelLabel above it; null leaves the section headless (an opening line).
 */
interface InfoSheetScope {
    /** A paragraph. Folded behind "Show more" past [collapseAfterLines] lines. */
    fun text(label: String?, body: String, color: Color = IronvellumColors.Ink, collapseAfterLines: Int = DEFAULT_LINES)

    /** A bulleted list. Folded past [collapseAfter] items. */
    fun bullets(label: String?, items: List<String>, color: Color = IronvellumColors.Ink, collapseAfter: Int = DEFAULT_ITEMS)

    /** A numbered list, with an optional [lead] line above it. Folded past [collapseAfter] items. */
    fun steps(label: String?, items: List<String>, lead: String? = null, collapseAfter: Int = DEFAULT_ITEMS)

    /** Label and value pairs, one per line. Folded past [collapseAfter] rows. */
    fun rows(label: String?, items: List<Pair<String, String>>, collapseAfter: Int = DEFAULT_ITEMS)

    /** Anything else. It is never folded: the caller keeps it short. */
    fun section(label: String?, content: @Composable ColumnScope.() -> Unit)

    companion object {
        /** A section longer than this many lines or items is folded. */
        const val DEFAULT_LINES = 4
        const val DEFAULT_ITEMS = 4
    }
}

/** A figure for [InfoFigures]. */
class InfoFigure(
    val label: String,
    val value: String,
    val unit: String? = null,
    val color: Color = IronvellumColors.Ink,
)

/** One to three big figures side by side, each under its PanelLabel. */
@Composable
fun InfoFigures(figures: List<InfoFigure>, modifier: Modifier = Modifier) {
    require(figures.size in 1..3) { "A summary strip shows one to three figures" }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        figures.forEach { figure ->
            Column(Modifier.weight(1f)) {
                PanelLabel(figure.label)
                StatValue(figure.value, size = StatSize.Tile, color = figure.color, unit = figure.unit)
            }
        }
    }
}

/** A progress bar with its line ("12 / 25 · 13 to go") and an optional caption of what is counted. */
@Composable
fun InfoProgress(
    fraction: Float,
    line: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    fill: Brush = SolidColor(IronvellumColors.Emerald),
) {
    Column(modifier.fillMaxWidth()) {
        InkRail(fraction = fraction, height = 6.dp, fill = fill)
        Spacer(Modifier.height(8.dp))
        Text(line, style = MaterialTheme.typography.titleSmall, color = IronvellumColors.Ink)
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------------ parts ----

private val SheetPaper = Color(0xFF0D1110)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetHeader(title: String, subtitle: String?, subtitleColor: Color, chips: List<InfoChip>, compact: Boolean, titleColor: Color) {
    Text(
        title,
        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.Bold,
        color = titleColor,
        // First in the reading order, and a heading, whatever sits above it.
        modifier = Modifier.semantics {
            heading()
            traversalIndex = -1f
        },
    )
    if (subtitle != null) {
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = subtitleColor,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    if (chips.isNotEmpty()) {
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.forEach { FactChip(it) }
        }
    }
}

@Composable
private fun FactChip(chip: InfoChip) {
    val shape = MaterialTheme.shapes.extraSmall
    Text(
        chip.text,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = ChakraPetch,
        color = chip.color,
        modifier = Modifier
            .background(Color(0xFF151C19), shape)
            .inkBorder(chip.color.copy(alpha = 0.6f), shape, 1.dp)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Dims the foot of a scroll area while there is more under it, so a cut-off section reads as scrollable. */
private fun Modifier.fadeWhenMore(more: Boolean): Modifier = if (!more) this else this.drawWithContent {
    drawContent()
    val h = 28.dp.toPx()
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, SheetPaper), startY = size.height - h, endY = size.height),
        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - h),
        size = androidx.compose.ui.geometry.Size(size.width, h),
    )
}

private abstract class SheetSection(val label: String?) {
    @Composable
    abstract fun Body(collapsible: Boolean, expanded: Boolean, onToggle: (() -> Unit)?)

    @Composable
    fun Render(index: Int, collapsible: Boolean) {
        var expanded by rememberSaveable(index, label) { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth()) {
            if (label != null) {
                PanelLabel(label)
                Spacer(Modifier.height(8.dp))
            }
            Body(collapsible, expanded) { expanded = !expanded }
        }
    }
}

private class InfoSheetBuilder : InfoSheetScope {
    val sections = mutableListOf<SheetSection>()

    override fun text(label: String?, body: String, color: Color, collapseAfterLines: Int) {
        sections += object : SheetSection(label) {
            @Composable
            override fun Body(collapsible: Boolean, expanded: Boolean, onToggle: (() -> Unit)?) {
                var overflows by remember(body) { mutableStateOf(false) }
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    maxLines = if (collapsible && !expanded) collapseAfterLines else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
                )
                if (collapsible && (overflows || expanded)) ShowMore(label, expanded, "", onToggle)
            }
        }
    }

    override fun bullets(label: String?, items: List<String>, color: Color, collapseAfter: Int) {
        listSection(label, items, collapseAfter) { _, item ->
            Text("•", style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.InkMuted, modifier = Modifier.width(18.dp))
            Text(item, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
        }
    }

    override fun steps(label: String?, items: List<String>, lead: String?, collapseAfter: Int) {
        listSection(label, items, collapseAfter, lead) { i, item ->
            Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.InkMuted, modifier = Modifier.width(26.dp))
            Text(item, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, modifier = Modifier.weight(1f))
        }
    }

    override fun rows(label: String?, items: List<Pair<String, String>>, collapseAfter: Int) {
        listSection(label, items.map { it.first }, collapseAfter) { i, item ->
            Text(item, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink, modifier = Modifier.weight(1f))
            Text(items[i].second, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.InkMuted)
        }
    }

    override fun section(label: String?, content: @Composable ColumnScope.() -> Unit) {
        sections += object : SheetSection(label) {
            @Composable
            override fun Body(collapsible: Boolean, expanded: Boolean, onToggle: (() -> Unit)?) {
                Column(content = content)
            }
        }
    }

    private fun listSection(
        label: String?,
        items: List<String>,
        collapseAfter: Int,
        lead: String? = null,
        row: @Composable androidx.compose.foundation.layout.RowScope.(Int, String) -> Unit,
    ) {
        if (items.isEmpty()) return
        sections += object : SheetSection(label) {
            @Composable
            override fun Body(collapsible: Boolean, expanded: Boolean, onToggle: (() -> Unit)?) {
                // Folding one hidden item would cost more room than it saves.
                val fold = collapsible && items.size > collapseAfter + 1
                val shown = if (fold && !expanded) items.take(collapseAfter) else items
                if (lead != null) {
                    Text(lead, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
                    Spacer(Modifier.height(8.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEachIndexed { i, item -> Row { row(i, item) } }
                }
                if (fold) ShowMore(label, expanded, "${items.size - collapseAfter}", onToggle)
            }
        }
    }
}

/** The fold toggle: a 48dp target that announces whether the section is open. */
@Composable
private fun ShowMore(label: String?, expanded: Boolean, hidden: String, onToggle: (() -> Unit)?) {
    val what = label?.lowercase() ?: "text"
    Row(
        Modifier
            .heightIn(min = LedgerSpace.Target)
            .clickable(
                role = Role.Button,
                onClickLabel = if (expanded) "Show less of $what" else "Show more of $what",
            ) { onToggle?.invoke() }
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (expanded) "Show less" else if (hidden.isNotEmpty()) "Show more ($hidden)" else "Show more",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = IronvellumColors.SystemGreen,
        )
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = IronvellumColors.SystemGreen,
        )
    }
}

/**
 * Stops the content's leftover forward scroll at the bottom from reaching the sheet. A fling that runs
 * out of content hands its spare velocity up the nested-scroll chain, and the sheet answers by settling
 * on it: it overshoots its expanded edge and keeps springing back and forth. Only forward (finger-up)
 * leftovers are eaten; a backward flick at the top still reaches the sheet, so swipe-to-dismiss works.
 */
private val KeepForwardScrollInside = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ) = if (available.y < 0f) available else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity) =
        if (available.y < 0f) available else Velocity.Zero
}
