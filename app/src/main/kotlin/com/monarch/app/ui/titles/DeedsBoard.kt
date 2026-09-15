package com.monarch.app.ui.titles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monarch.app.domain.Titles
import com.monarch.app.domain.TitleDef
import com.monarch.app.domain.TitleRarity
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.components.InkRail
import com.monarch.app.ui.theme.inkBorder
import com.monarch.app.ui.theme.MonarchColors

/**
 * The deeds half of the codex: what you wear, what you are closest to earning,
 * a showcase of what you hold, then the rest grouped by the kind of deed it
 * demands. The grouped half is filterable, collapsible and proximity-ordered
 * so it stays one screen tall instead of ninety near-identical panels.
 */

/** Status rail filters; counts are derived independently per chip. */
private enum class DeedFilter(val label: String) {
    IN_PROGRESS("IN PROGRESS"),
    CLOSE("CLOSE"),
    CLAIMED("CLAIMED"),
    LOCKED("LOCKED"),
    ALL("ALL"),
}

private fun DeedFilter.matches(def: TitleDef, unlocked: Map<String, Long>, progress: Titles.Progress): Boolean {
    val claimed = def.id in unlocked
    return when (this) {
        DeedFilter.IN_PROGRESS -> !claimed && progress.fraction < 0.5f
        DeedFilter.CLOSE -> !claimed && progress.fraction >= 0.5f
        DeedFilter.CLAIMED -> claimed
        DeedFilter.LOCKED -> !claimed
        DeedFilter.ALL -> true
    }
}

/** Rarity accent per tier — same tokens the crest will use, so board and avatar agree. */
private fun rarityColor(rarity: TitleRarity): Color = when (rarity) {
    TitleRarity.Common -> MonarchColors.InkMuted
    TitleRarity.Rare -> MonarchColors.SystemGreen
    TitleRarity.Epic -> MonarchColors.Emerald
    TitleRarity.Sovereign -> MonarchColors.SovereignGold
}

@Composable
private fun RarityChip(rarity: TitleRarity, modifier: Modifier = Modifier) {
    val accent = rarityColor(rarity)
    val shape = MaterialTheme.shapes.extraSmall
    // Sovereign must be unmistakable at a glance: gold jewel fill plus a
    // heavier border. Epic gets a green jewel fill; Rare and Common stay quiet.
    val bg = when (rarity) {
        TitleRarity.Sovereign -> Brush.verticalGradient(listOf(Color(0xFF5A3F0C), Color(0xFF1E1606)))
        TitleRarity.Epic -> Brush.verticalGradient(listOf(Color(0xFF123526), Color(0xFF0C211A)))
        else -> Brush.verticalGradient(listOf(Color(0xFF161C1A), Color(0xFF111614)))
    }
    Box(
        modifier
            .background(bg, shape)
            .border(if (rarity == TitleRarity.Sovereign) 2.dp else 1.dp, accent, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            rarity.name.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontFamily = ChakraPetch,
            color = accent,
            letterSpacing = 1.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun DeedsBoard(
    unlocked: Map<String, Long>,
    equippedId: String?,
    ledger: Titles.Ledger,
    onEquip: (String) -> Unit,
    // The caller supplies the height bound: this list is the scroll container,
    // so it must never be handed infinite height by a scrolling parent.
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val earned = Titles.ALL.filter { it.id in unlocked }
    val locked = Titles.ALL.filter { it.id !in unlocked }
    val equipped = equippedId?.let { Titles.byId(it) }

    var query by remember { mutableStateOf(TextFieldValue("")) }
    var filter by remember { mutableStateOf(DeedFilter.IN_PROGRESS) }
    // Rarity narrows independently of the status filter; null = every tier.
    var rarityFilter by remember { mutableStateOf<TitleRarity?>(null) }
    // Toggling reorders each section's rows by rarity instead of proximity.
    var byRarity by remember { mutableStateOf(false) }

    // progress per deed computed once; every chip count, sort and row reuses it
    val progressOf = remember(ledger) {
        Titles.ALL.associate { it.id to Titles.progress(it.rule, ledger) }
    }

    // Nearest deed's category is the one section that starts expanded, so the
    // default screen answers "what next" without ninety panels of scroll.
    val nearestCategory = locked
        .maxByOrNull { progressOf[it.id]?.fraction ?: 0f }
        ?.let { Titles.category(it.rule) }
    val expandedCategories = remember {
        mutableStateOf(setOfNotNull(nearestCategory))
    }
    val searching = query.text.isNotBlank()
    // Claimed showcase starts collapsed: on a phone the five sealed cards
    // alone push the category sections off-screen. Wearing stays one tap away.
    var claimedOpen by remember { mutableStateOf(false) }

    // Closest unearned deed — the thing worth chasing today, shown inside the
    // merged hero panel under every filter.
    val next = locked
        .filter { rarityFilter == null || it.rarity == rarityFilter }
        .map { it to progressOf.getValue(it.id) }
        .maxByOrNull { it.second.fraction }

    LazyColumn(
        modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Controls sit ABOVE the hero on purpose: a filter you cannot see
        // without scrolling is a filter nobody uses. They are plain items in
        // this LazyColumn — exactly one scroll container on the screen.
        item(key = "search") {
            DeedSearchField(query, onQueryChange = { query = it })
        }
        item(key = "rail") {
            // Rail scrolls horizontally; a fixed Row squeezed the last chips
            // into one letter per line off the screen edge.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DeedFilter.entries.forEach { f ->
                    val count = Titles.ALL.count { f.matches(it, unlocked, progressOf.getValue(it.id)) }
                    DeedFilterChip(
                        label = "${f.label} $count",
                        selected = filter == f,
                        onClick = { filter = f },
                    )
                }
                // Rarity rail: tapping the selected tier again clears it.
                // Same horizontalScroll idiom — no chip may wrap.
                Spacer(Modifier.width(6.dp))
                TitleRarity.entries.forEach { r ->
                    val count = Titles.ALL.count { it.rarity == r }
                    DeedFilterChip(
                        label = "${r.name.uppercase()} $count",
                        selected = rarityFilter == r,
                        onClick = { rarityFilter = if (rarityFilter == r) null else r },
                    )
                }
                DeedFilterChip(
                    label = if (byRarity) "ORDER · RARITY" else "ORDER · PROXIMITY",
                    selected = byRarity,
                    onClick = { byRarity = !byRarity },
                )
        }
        }

        // --- merged hero: what you wear + what to chase next, one panel -----
        item(key = "hero") {
            SystemWindow(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            equipped?.name?.uppercase() ?: "NO TITLE WORN",
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = ChakraPetch,
                            fontWeight = FontWeight.Bold,
                            color = if (equipped != null) MonarchColors.SovereignGold else MonarchColors.InkMuted,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        // The worn title's tier — the same chip every deed row shows.
                        if (equipped != null) {
                            Spacer(Modifier.width(8.dp))
                            RarityChip(equipped.rarity)
                        }
                    }
                    Text(
                        equipped?.description ?: "Earn a deed below, then wear it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MonarchColors.InkMuted,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        CodexStat("HELD", "${earned.size}/${Titles.ALL.size}")
                        CodexStat("CAMPAIGNS", ledger.workouts.toString())
                        CodexStat("SETS", ledger.sets.toString())
                        CodexStat("REPS", ledger.reps.toString())
                    }
                    if (next != null) {
                        Spacer(Modifier.height(10.dp))
                        // Slim divider between the worn half and the chase half.
                        ProgressTrack(0.02f, tall = false)
                        Spacer(Modifier.height(8.dp))
                        ClosestDeedCard(next.first, next.second)
                    }
                }
            }
        }

        // --- high seats: locked Epic + Sovereign deeds, nearest first -------
        // The chase is only legible if the rarest locked deeds are visible
        // without digging through collapsed sections.
        val highSeats = locked
            .filter { it.rarity.ordinal >= TitleRarity.Epic.ordinal }
            .map { it to progressOf.getValue(it.id) }
            .sortedByDescending { it.second.fraction }
            .take(3)
        if (highSeats.isNotEmpty() && !searching) {
            item(key = "high-seats") {
                SystemWindow(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            "UNCLAIMED HIGH SEATS",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = ChakraPetch,
                            color = MonarchColors.InkMuted,
                            letterSpacing = 2.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        highSeats.forEach { (def, progress) ->
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RarityChip(def.rarity)
                                Text(
                                    def.name.uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = ChakraPetch,
                                    color = rarityColor(def.rarity),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${(progress.fraction * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = ChakraPetch,
                                    color = MonarchColors.SystemGreen,
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- claimed showcase, collapsed by default --------------------------
        if (earned.isNotEmpty()) {
            item(key = "claimed-head") {
                CategoryHeader(
                    category = "CLAIMED ${earned.size}",
                    claimed = earned.size,
                    total = earned.size,
                    fraction = 1f,
                    open = claimedOpen,
                    onClick = { claimedOpen = !claimedOpen },
                )
            }
            if (claimedOpen) {
                item(key = "claimed-grid") {
                    Column {
                        earned.chunked(2).forEach { pair ->
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                pair.forEach { def ->
                                    Box(Modifier.weight(1f)) {
                                        SealCard(
                                            def = def,
                                            unlockedAtMs = unlocked[def.id],
                                            worn = def.id == equippedId,
                                            onClick = { onEquip(def.id) },
                                        )
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // --- collapsible category sections ----------------------------------
        val visibleSections = locked
            .filter { filter.matches(it, unlocked, progressOf.getValue(it.id)) }
            .filter { rarityFilter == null || it.rarity == rarityFilter }
            .filter {
                !searching ||
                    it.name.contains(query.text.trim(), ignoreCase = true) ||
                    it.description.contains(query.text.trim(), ignoreCase = true)
            }
            .groupBy { Titles.category(it.rule) }
            .mapValues { (_, defs) ->
                if (byRarity) {
                    // Rarity order: prized deeds first, then the nearest ones.
                    defs.sortedWith(
                        compareBy<TitleDef> { it.id in unlocked }
                            .thenByDescending { it.rarity.ordinal }
                            .thenByDescending { progressOf.getValue(it.id).fraction },
                    )
                } else {
                    // proximity order: closest first, then small honest targets;
                    // claimed sink to the bottom of their section
                    defs.sortedWith(
                        compareBy<TitleDef> { it.id in unlocked }
                            .thenByDescending { progressOf.getValue(it.id).fraction }
                            .thenBy { progressOf.getValue(it.id).target },
                    )
                }
            }

        // A search term filters every section, so only matching ones appear —
        // and while searching every surviving section is force-expanded.
        val sections = visibleSections.entries.sortedBy { it.key }
        sections.forEach { (category, defs) ->
            // A search term force-expands every surviving section; otherwise
            // only categories the user (or the nearest deed) opened show rows.
            val open = searching || category in expandedCategories.value
            item(key = "cat:$category") {
                CategoryHeader(
                    category = category,
                    claimed = defs.count { it.id in unlocked },
                    total = defs.size,
                    fraction = defs.map { progressOf.getValue(it.id).fraction }.average().toFloat(),
                    open = open,
                    onClick = {
                        expandedCategories.value =
                            if (category in expandedCategories.value) {
                                expandedCategories.value - category
                            } else {
                                expandedCategories.value + category
                            }
                    },
                )
            }
            if (open) {
                items(defs, key = { it.id }) { def ->
                    DeedRow(def, progressOf.getValue(def.id), unlocked[def.id])
                }
            }
        }
    }
}

@Composable
private fun ClosestDeedCard(def: TitleDef, progress: Titles.Progress) {
    // Compact by design: this now lives INSIDE the merged hero panel, not in
    // its own section, so it must stay a few lines tall.
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                def.name.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = MonarchColors.Ink,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            RarityChip(def.rarity)
            Spacer(Modifier.width(8.dp))
            Text(
                "${(progress.fraction * 100).toInt()}%",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                color = MonarchColors.SystemGreen,
            )
        }
        Spacer(Modifier.height(6.dp))
        ProgressTrack(progress.fraction, tall = false)
        Spacer(Modifier.height(4.dp))
        Text(
            "${progress.current} / ${progress.target} · ${progress.remaining} ${progress.unit} to go",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = ChakraPetch,
            color = MonarchColors.SovereignGold,
        )
    }
}

@Composable
private fun DeedSearchField(query: TextFieldValue, onQueryChange: (TextFieldValue) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF141A18), MaterialTheme.shapes.extraSmall)
            .inkBorder(MonarchColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = MonarchColors.InkMuted)
        Box(Modifier.padding(start = 8.dp).fillMaxWidth()) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MonarchColors.Ink),
                cursorBrush = Brush.horizontalGradient(listOf(MonarchColors.SystemGreen, MonarchColors.SystemGreen)),
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.text.isEmpty()) {
                Text(
                    "search deeds",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MonarchColors.InkMuted,
                )
            }
        }
    }
}

@Composable
private fun DeedFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(
                if (selected) {
                    Brush.verticalGradient(listOf(Color(0xFF2C7A5A), Color(0xFF1B4D3A)))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF161C1A), Color(0xFF111614)))
                },
                MaterialTheme.shapes.extraSmall,
            )
            .inkBorder(if (selected) MonarchColors.SystemGreen else MonarchColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = if (selected) MonarchColors.Ink else MonarchColors.InkMuted,
            letterSpacing = 1.sp,
            // A chip label must never wrap: "CLIMBING" broke into one letter
            // per line when the row ran out of width.
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun CategoryHeader(
    category: String,
    claimed: Int,
    total: Int,
    fraction: Float,
    open: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF141A18), MaterialTheme.shapes.extraSmall)
            .inkBorder(MonarchColors.Rune, MaterialTheme.shapes.extraSmall, 1.dp)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (open) "▾ $category" else "▸ $category",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = MonarchColors.SovereignGold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$claimed/$total",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.InkMuted,
            )
        }
        Spacer(Modifier.height(4.dp))
        ProgressTrack(fraction, tall = false)
    }
}

@Composable
private fun DeedRow(def: TitleDef, progress: Titles.Progress, unlockedAtMs: Long?) {
    val claimed = unlockedAtMs != null
    SystemWindow(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                // claimed deeds sink to the bottom of their section and dim
                .alpha(if (claimed) 0.55f else 1f),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(0.dp).weight(1f)) {
                    Text(
                        def.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MonarchColors.Ink,
                    )
                    Text(
                        if (claimed) "claimed ${formatDate(unlockedAtMs!!, "d MMM yyyy")}" else def.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (claimed) MonarchColors.SovereignGold else MonarchColors.InkMuted,
                        maxLines = 1,
                    )
                }
                RarityChip(def.rarity)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${progress.current}/${progress.target}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = if (progress.fraction > 0f) MonarchColors.SystemGreen else MonarchColors.InkMuted,
                )
            }
            Spacer(Modifier.height(6.dp))
            ProgressTrack(progress.fraction, tall = false)
        }
    }
}

@Composable
private fun SealCard(def: TitleDef, unlockedAtMs: Long?, worn: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    // Seal border carries the rarity accent; worn keeps the gold treatment.
    val rim = if (worn) MonarchColors.SovereignGold else rarityColor(def.rarity)
    val rimWidth = if (worn || def.rarity == TitleRarity.Sovereign) 2.dp else 1.dp
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    if (worn) listOf(Color(0xFF5A3F0C), Color(0xFF1E1606))
                    else listOf(Color(0xFF1C2119), Color(0xFF10140F)),
                ),
                shape,
            )
            .border(rimWidth, rim, shape)
            .clickable { onClick() }
            .padding(10.dp),
    ) {
        Text(
            def.name,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.SovereignGold,
            maxLines = 2,
        )
        unlockedAtMs?.let {
            Text(
                formatDate(it, "d MMM yyyy"),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MonarchColors.InkMuted,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (worn) "WORN" else "TAP TO WEAR",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChakraPetch,
                fontSize = 9.sp,
                color = if (worn) MonarchColors.SovereignGold else MonarchColors.SystemGreen,
                letterSpacing = 1.sp,
            )
            RarityChip(def.rarity)
        }
    }
}

@Composable
private fun ProgressTrack(fraction: Float, tall: Boolean) {
    // Shared ink rail: this used to be its own track-plus-fill Box pair, one of
    // four copies of the same widget across the app.
    InkRail(
        fraction = fraction,
        height = if (tall) 10.dp else 6.dp,
        seed = if (tall) 5 else 9,
    )
}

@Composable
private fun CodexStat(label: String, value: String) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = ChakraPetch,
            fontWeight = FontWeight.Bold,
            color = MonarchColors.Ink,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            fontSize = 9.sp,
            color = MonarchColors.InkMuted,
            letterSpacing = 1.sp,
        )
    }
}
