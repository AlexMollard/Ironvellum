package com.ironvellum.app.ui.titles

import com.ironvellum.app.ui.components.PushedHeader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.DeedLadder
import com.ironvellum.app.domain.DeedLadders
import com.ironvellum.app.domain.RungState
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.TitleDef
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.LedgerSpace
import com.ironvellum.app.ui.components.RangeChips
import com.ironvellum.app.ui.components.SettingsGroup
import com.ironvellum.app.ui.components.StatSize
import com.ironvellum.app.ui.components.StatValue
import com.ironvellum.app.ui.components.TapRow
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.DotShape
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder
import kotlinx.coroutines.launch

/**
 * The deeds half of the codex. No filters: a header with the overall count,
 * the title you wear, the few deeds closest to earned, eight categories to
 * drill into, and a wall of what you hold. Inside a category the deeds are
 * grouped as ladders, one row per series, so a long catalogue stays a short
 * screen. Tapping any deed opens its detail sheet.
 */
@Composable
fun DeedsBoard(
    unlocked: Map<String, Long>,
    equippedId: String?,
    ledger: Titles.Ledger,
    onEquip: (String) -> Unit,
    /** Whose bar the deed wording states; a woman reads her own, not a footnote. */
    sex: Sex,
    // The caller supplies the height bound: this list is the scroll container,
    // so it must never be handed infinite height by a scrolling parent.
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val earnedIds = remember(unlocked) { unlocked.keys.toSet() }
    val progressOf = remember(ledger) {
        Titles.ALL.associate { it.id to Titles.progress(it.rule, ledger) }
    }
    var openCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetId by rememberSaveable { mutableStateOf<String?>(null) }
    // Hoisted so drilling into a category and back lands on the same scroll.
    val homeState = rememberLazyListState()

    // System back climbs one level: category to board, never straight out.
    BackHandler(enabled = openCategory != null) { openCategory = null }

    val category = openCategory
    if (category == null) {
        DeedsHome(
            unlocked = unlocked,
            earnedIds = earnedIds,
            equippedId = equippedId,
            progressOf = progressOf,
            ledger = ledger,
            sex = sex,
            listState = homeState,
            onOpenCategory = { openCategory = it },
            onOpenDeed = { sheetId = it },
            modifier = modifier,
        )
    } else {
        CategoryScreen(
            category = category,
            unlocked = unlocked,
            earnedIds = earnedIds,
            progressOf = progressOf,
            ledger = ledger,
            sex = sex,
            onBack = { openCategory = null },
            onOpenDeed = { sheetId = it },
            modifier = modifier,
        )
    }

    sheetId?.let { id ->
        val def = Titles.byId(id)
        val progress = progressOf[id]
        if (def != null && progress != null) {
            DeedDetailSheet(
                def = def,
                progress = progress,
                ledger = ledger,
                sex = sex,
                earnedAtMs = unlocked[id],
                worn = id == equippedId,
                onWear = {
                    onEquip(id)
                    sheetId = null
                },
                onDismiss = { sheetId = null },
            )
        }
    }
}

// ---------------------------------------------------------------- home ----

/** How many nearly-won deeds the shelf shows. */
private const val SHELF_SIZE = 4

@Composable
private fun DeedsHome(
    unlocked: Map<String, Long>,
    earnedIds: Set<String>,
    equippedId: String?,
    progressOf: Map<String, Titles.Progress>,
    ledger: Titles.Ledger,
    sex: Sex,
    listState: LazyListState,
    onOpenCategory: (String) -> Unit,
    onOpenDeed: (String) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val earned = Titles.ALL.filter { it.id in earnedIds }
    val worn = equippedId?.let { Titles.byId(it) }

    // The next rung of each unfinished ladder, nearest first. Ties go to the
    // gentler deed, so a fresh account is pointed at easy wins.
    val shelf = remember(earnedIds, progressOf) {
        DeedLadders.ALL
            .mapNotNull { it.next(earnedIds) }
            .sortedWith(
                compareByDescending<TitleDef> { progressOf.getValue(it.id).fraction }
                    .thenBy { it.rarity.ordinal },
            )
            .take(SHELF_SIZE)
    }
    val wallIndex = 2 + (if (shelf.isNotEmpty()) 1 else 0) + 1

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
    ) {
        item(key = "header") {
            DeedsHeader(earned = earned.size, total = Titles.ALL.size)
        }

        item(key = "worn") {
            SettingsGroup(label = null, topSpace = LedgerSpace.Panel) {
                when {
                    worn != null -> TapRow(
                        onClickLabel = "Open ${worn.name}",
                        onClick = { onOpenDeed(worn.id) },
                    ) {
                        Text(
                            "Wearing",
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                        )
                        Text(
                            worn.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.SovereignGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Chevron()
                    }
                    earned.isNotEmpty() -> TapRow(
                        onClickLabel = "Show the deeds you have earned",
                        onClick = { scope.launch { listState.animateScrollToItem(wallIndex) } },
                    ) {
                        Text(
                            "Wear a title you've earned",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = ChakraPetch,
                            color = IronvellumColors.Ink,
                            modifier = Modifier.weight(1f),
                        )
                        Chevron()
                    }
                    else -> Row(Modifier.heightIn(min = LedgerSpace.Target), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "No title worn yet. Earn a deed and its title is yours to wear.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                        )
                    }
                }
            }
        }

        if (shelf.isNotEmpty()) {
            item(key = "close") {
                Column {
                    SectionLabel("CLOSE TO EARNING")
                    SettingsGroup(label = null, topSpace = 0.dp) {
                        shelf.forEachIndexed { i, def ->
                            if (i > 0) InkDivider()
                            NearRow(def, progressOf.getValue(def.id), ledger, sex) { onOpenDeed(def.id) }
                        }
                    }
                }
            }
        }

        item(key = "categories") {
            Column {
            SectionLabel("CATEGORIES")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeedLadders.CATEGORIES.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { c ->
                            val defs = DeedLadders.ALL.filter { it.category == c.name }.flatMap { it.rungs }
                            CategoryTile(
                                name = c.name,
                                blurb = c.blurb,
                                earned = defs.count { it.id in earnedIds },
                                total = defs.size,
                                onClick = { onOpenCategory(c.name) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            }
        }

        item(key = "wall") {
            Column {
                SectionLabel("EARNED  ${earned.size}")
                EarnedWall(
                    earned = earned.sortedWith(
                        compareByDescending<TitleDef> { it.rarity.ordinal }
                            .thenByDescending { unlocked[it.id] ?: 0L },
                    ),
                    equippedId = equippedId,
                    onOpenDeed = onOpenDeed,
                )
            }
        }
    }
}

@Composable
private fun DeedsHeader(earned: Int, total: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Deeds: $earned of $total earned"
            },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                "DEEDS",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                color = IronvellumColors.InkMuted,
                letterSpacing = IronvellumTracking.InlineLabel,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            StatValue("$earned / $total", size = StatSize.Inline)
        }
        Spacer(Modifier.height(8.dp))
        InkRail(
            fraction = if (total == 0) 0f else earned.toFloat() / total,
            height = 4.dp,
            fill = railFill(earned = earned == total),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    SectionHeader(text, topPadding = LedgerSpace.Section)
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = IronvellumColors.InkMuted,
    )
}

/** One of the few unearned deeds nearest to done: name, bar, how far to go. */
@Composable
private fun NearRow(def: TitleDef, progress: Titles.Progress, ledger: Titles.Ledger, sex: Sex, onClick: () -> Unit) {
    val toGo = deedProgressText(def, progress, ledger, earned = false).toGo
    val requirement = def.describeFor(sex)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = LedgerSpace.Target)
            .clip(MaterialTheme.shapes.extraSmall)
            .semantics(mergeDescendants = true) {
                contentDescription = "${def.name}, ${def.rarity.label}. $requirement $toGo."
            }
            .clickable(onClickLabel = "Open ${def.name}", role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                def.name,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            RarityMark(def.rarity)
        }
        Text(
            requirement,
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        BarWithToGo(progress.fraction, toGo)
    }
}

/**
 * A full-width rail with how far to go under it. Beside the rail the text took
 * the whole row at 360dp ("600,000 / 1,000,000 - 400,000 steps to go") and
 * squeezed the rail to nothing, or clipped at a larger font; under it, it wraps.
 */
@Composable
private fun BarWithToGo(fraction: Float, toGo: String) {
    Column(Modifier.fillMaxWidth()) {
        InkRail(fraction = fraction, height = 4.dp, fill = railFill(earned = false))
        Spacer(Modifier.height(4.dp))
        Text(
            toGo,
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CategoryTile(
    name: String,
    blurb: String,
    earned: Int,
    total: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InkPanel(
        modifier
            .heightIn(min = LedgerSpace.Target)
            .semantics(mergeDescendants = true) {
                contentDescription = "$name. $blurb. $earned of $total deeds earned."
            },
        onClick = onClick,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.Ink,
            maxLines = 1,
        )
        // Two lines reserved so a row of tiles stays level whatever the blurb length.
        Text(
            blurb,
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$earned / $total",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                color = IronvellumColors.Ink,
                modifier = Modifier.weight(1f),
            )
            if (total > 0 && earned == total) GoldCheck()
        }
        Spacer(Modifier.height(6.dp))
        InkRail(
            fraction = if (total == 0) 0f else earned.toFloat() / total,
            height = 4.dp,
            fill = railFill(earned = total > 0 && earned == total),
        )
    }
}

@Composable
private fun GoldCheck() {
    Icon(
        Icons.Filled.Check,
        contentDescription = "Complete",
        tint = IronvellumColors.SovereignGold,
        modifier = Modifier.size(18.dp),
    )
}

/** Compact badges of every deed held; each opens its detail sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EarnedWall(earned: List<TitleDef>, equippedId: String?, onOpenDeed: (String) -> Unit) {
    if (earned.isEmpty()) {
        Text(
            "Nothing is written here yet. Seal a trial to earn the first deed.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        return
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        earned.forEach { def ->
            val worn = def.id == equippedId
            val shape = MaterialTheme.shapes.extraSmall
            // The 48dp target is the outer box; the badge drawn inside is smaller.
            Box(
                Modifier
                    .heightIn(min = LedgerSpace.Target)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${def.name}, ${def.rarity.label}, earned" +
                            if (worn) ", worn" else ""
                    }
                    .clickable(onClickLabel = "Open ${def.name}", role = Role.Button) { onOpenDeed(def.id) },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier
                        .background(Color(0xFF141A18), shape)
                        .inkBorder(
                            if (worn) IronvellumColors.SovereignGold else rarityColor(def.rarity, earned = true),
                            shape,
                            if (worn) 2.dp else 1.dp,
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = IronvellumColors.SovereignGold,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        def.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Masterwork is gold too, so worn needs a word, not a colour.
                    if (worn) {
                        Text(
                            "WORN",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            color = IronvellumColors.SovereignGold,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------ category ----

private enum class DeedRange(val label: String) { ToDo("To do"), Earned("Earned"), All("All") }

@Composable
private fun CategoryScreen(
    category: String,
    unlocked: Map<String, Long>,
    earnedIds: Set<String>,
    progressOf: Map<String, Titles.Progress>,
    ledger: Titles.Ledger,
    sex: Sex,
    onBack: () -> Unit,
    onOpenDeed: (String) -> Unit,
    modifier: Modifier,
) {
    var range by rememberSaveable(category) { mutableStateOf(DeedRange.ToDo) }
    val blurb = DeedLadders.CATEGORIES.firstOrNull { it.name == category }?.blurb.orEmpty()
    val ladders = remember(category) { DeedLadders.ALL.filter { it.category == category } }
    val total = ladders.sumOf { it.rungs.size }
    val earned = ladders.sumOf { l -> l.rungs.count { it.id in earnedIds } }

    val shown = remember(ladders, earnedIds, progressOf, range) {
        ladders
            .filter { l ->
                when (range) {
                    DeedRange.ToDo -> l.next(earnedIds) != null
                    DeedRange.Earned -> l.rungs.any { it.id in earnedIds }
                    DeedRange.All -> true
                }
            }
            // Unfinished ladders first, nearest to the next rung first.
            .sortedWith(
                compareBy<DeedLadder> { it.next(earnedIds) == null }
                    .thenByDescending { l -> l.next(earnedIds)?.let { progressOf.getValue(it.id).fraction } ?: 0f },
            )
    }

    LazyColumn(modifier, contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
        item(key = "head") {
            Column(
                Modifier.fillMaxWidth(),
            ) {
                PushedHeader(category, onBack)
                Column(
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "$category deeds: $earned of $total earned. $blurb."
                    },
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text(
                            blurb,
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                            modifier = Modifier.weight(1f),
                        )
                        StatValue("$earned / $total", size = StatSize.Inline)
                    }
                    Spacer(Modifier.height(8.dp))
                    InkRail(
                        fraction = if (total == 0) 0f else earned.toFloat() / total,
                        height = 4.dp,
                        fill = railFill(earned = total > 0 && earned == total),
                    )
                }
                Spacer(Modifier.height(8.dp))
                RangeChips(
                    options = DeedRange.entries.map { it to it.label },
                    selected = range,
                    onPick = { range = it },
                )
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    when (range) {
                        DeedRange.ToDo -> "Every deed in $category is earned."
                        DeedRange.Earned -> "Nothing is written here yet. Earned deeds appear on this page."
                        DeedRange.All -> "No deeds here."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.padding(top = LedgerSpace.Panel),
                )
            }
        }
        items(shown, key = { it.key }) { ladder ->
            Spacer(Modifier.height(LedgerSpace.Panel))
            LadderCard(
                ladder = ladder,
                earnedIds = earnedIds,
                unlocked = unlocked,
                progressOf = progressOf,
                ledger = ledger,
                sex = sex,
                onOpenDeed = onOpenDeed,
            )
        }
    }
}

@Composable
private fun LadderCard(
    ladder: DeedLadder,
    earnedIds: Set<String>,
    unlocked: Map<String, Long>,
    progressOf: Map<String, Titles.Progress>,
    ledger: Titles.Ledger,
    sex: Sex,
    onOpenDeed: (String) -> Unit,
) {
    val next = ladder.next(earnedIds)
    val held = ladder.rungs.count { it.id in earnedIds }
    val series = ladder.rungs.size > 1
    // A finished ladder opens its top rung; an open one opens the rung to win.
    val target = next ?: ladder.rungs.last()
    val progress = progressOf.getValue(target.id)
    val text = deedProgressText(target, progress, ledger, earned = next == null)

    val sentence = buildString {
        append(ladder.title).append(". ")
        if (series) append("$held of ${ladder.rungs.size} deeds earned. ")
        if (next != null) {
            if (series) append("Next: ${next.name}, ${next.rarity.label}. ")
            append(next.describeFor(sex)).append(' ')
            append("${text.counts}, ${text.toGo}.")
        } else {
            append("Complete.")
        }
    }

    InkPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = LedgerSpace.Target)
                .clip(MaterialTheme.shapes.extraSmall)
                .semantics(mergeDescendants = true) { contentDescription = sentence }
                .clickable(onClickLabel = "Open ${target.name}", role = Role.Button) { onOpenDeed(target.id) },
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ladder.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.Ink,
                    modifier = Modifier.weight(1f),
                )
                if (next == null) {
                    GoldCheck()
                } else if (series) {
                    Text(
                        "$held / ${ladder.rungs.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                    )
                }
            }
            if (next != null) {
                if (series) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Next · ${next.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        RarityMark(next.rarity)
                    }
                } else {
                    RarityMark(next.rarity)
                }
                Text(
                    next.describeFor(sex),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                BarWithToGo(progress.fraction, "${text.counts} · ${text.toGo}")
            } else {
                val whenEarned = unlocked[target.id]?.let { formatDate(it, "d MMM yyyy") }
                Text(
                    if (series) "All ${ladder.rungs.size} deeds earned" else "Earned ${whenEarned.orEmpty()}".trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.SovereignGold,
                )
            }
        }
        if (series) {
            RungStrip(ladder, earnedIds, onOpenDeed)
        }
    }
}

/**
 * One marker per rung: earned, the one to win next, or still locked. A tap on a
 * marker opens that rung's deed.
 *
 * Seven 48dp targets are 336dp, wider than a 360dp phone's panel, so they used
 * to wrap their last rung onto a second row. The markers are small now and the
 * strip is ONE target, 48dp tall and the full width: a tap picks the rung under
 * the finger. Screen readers get a single control with one action per rung.
 */
@Composable
private fun RungStrip(ladder: DeedLadder, earnedIds: Set<String>, onOpenDeed: (String) -> Unit) {
    val states = ladder.states(earnedIds)
    val count = ladder.rungs.size
    val summary = ladder.rungs.indices.joinToString(", ") { i ->
        "${ladder.rungs[i].name} ${states[i].word()}"
    }
    val next = ladder.next(earnedIds) ?: ladder.rungs.last()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = LedgerSpace.Target)
            .pointerInput(ladder) {
                detectTapGestures { at ->
                    val i = (at.x / size.width * count).toInt().coerceIn(0, count - 1)
                    onOpenDeed(ladder.rungs[i].id)
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = "$count deeds. $summary"
                role = Role.Button
                onClick(label = "Open ${next.name}") {
                    onOpenDeed(next.id)
                    true
                }
                customActions = ladder.rungs.map { rung ->
                    CustomAccessibilityAction("Open ${rung.name}") {
                        onOpenDeed(rung.id)
                        true
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        states.forEach { state ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { RungMarker(state) }
        }
    }
}

/** The ring of a deed still locked: 3:1 or better against the panel, dimmer than the green and gold of the others. */
internal val LockedRung = Color(0xFF7A776F)

/** The panel's lightest tone, where the ring has the least contrast. */
internal val RungPanelTop = Color(0xFF1A1A18)

private fun RungState.word(): String = when (this) {
    RungState.Earned -> "earned"
    RungState.Next -> "next to earn"
    RungState.Locked -> "locked"
}

@Composable
private fun RungMarker(state: RungState) {
    when (state) {
        RungState.Earned -> Box(
            Modifier.size(18.dp).clip(DotShape).background(IronvellumColors.SovereignGold),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(12.dp),
            )
        }
        RungState.Next -> Box(
            Modifier.size(18.dp).inkBorder(IronvellumColors.SystemGreen, DotShape, 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(8.dp).clip(DotShape).background(IronvellumColors.SystemGreen))
        }
        RungState.Locked -> Box(
            Modifier.size(18.dp).inkBorder(LockedRung, DotShape, 1.5.dp),
        )
    }
}
