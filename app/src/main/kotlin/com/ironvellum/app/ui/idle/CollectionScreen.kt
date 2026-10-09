package com.ironvellum.app.ui.idle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.CollectionTab
import com.ironvellum.app.domain.Gacha
import com.ironvellum.app.domain.HouseProgress
import com.ironvellum.app.domain.VaultSlot
import com.ironvellum.app.domain.VaultState
import com.ironvellum.app.domain.Veil
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.ui.components.CrestPlate
import com.ironvellum.app.ui.components.HouseRelicSigil
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.ui.components.InkTabs
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.UnheldEdge
import com.ironvellum.app.ui.components.UnheldGround
import com.ironvellum.app.ui.components.rarityWord
import com.ironvellum.app.ui.dashboard.rememberTodayMotion
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.Metal
import com.ironvellum.app.ui.theme.TileShape
import com.ironvellum.app.ui.theme.inkBorder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The crests held and the one worn. */
data class CrestUi(val owned: Set<String> = emptySet(), val worn: String? = null)

class CollectionViewModel(private val repo: Repository) : ViewModel() {
    val vault: StateFlow<VaultState?> = repo.observeVault()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val crests: StateFlow<CrestUi> = combine(repo.observeOwnedFrames(), repo.observeEquippedFrame()) { owned, worn ->
        CrestUi(owned, worn)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CrestUi())

    /** The lifter level, which says how far off the next milestone crest is. */
    val level: StateFlow<Int> = repo.observeProfile()
        .map { Xp.levelFor(it?.totalXp ?: 0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    /** Cosmetic only; the repository refuses a crest not owned. */
    fun wear(frameId: String?) {
        viewModelScope.launch { repo.equipFrame(frameId) }
    }
}

/** The level a milestone crest is earned at, or null for the three won by chance alone. */
internal fun crestLevel(frameId: String): Int? =
    Veil.CREST_LADDER.indexOf(frameId).takeIf { it >= 0 }?.let { (it + 1) * Veil.MILESTONE_EVERY }

/** "Iron crest": the catalogue's name in the app's sentence case. */
internal fun crestName(frameId: String): String =
    Gacha.CREST_FRAMES.firstOrNull { it.id == frameId }?.name?.replace(" Crest", " crest") ?: frameId

/** A name the collection draws for something not yet held: legible, but quiet. */
private val UnheldName = Color(0xFF8A8780)

/**
 * Everything collected, on one pushed page with two tabs: the sixteen relics by house, and the ten crests.
 * It replaces the relic vault and the crest collection, which were two screens for one thing.
 * [initialTab] is the tab to open on: the Veil's "View" lands on the one that just grew.
 */
@Composable
fun CollectionScreen(
    initialTab: CollectionTab,
    onBack: () -> Unit,
    viewModel: CollectionViewModel =
        viewModel(factory = viewModelFactory { initializer { CollectionViewModel(ironvellumRepository()) } }),
) {
    val vault by viewModel.vault.collectAsStateWithLifecycle()
    val crests by viewModel.crests.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }
    var housesOpen by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val animate = rememberTodayMotion()

    Column(Modifier.fillMaxSize()) {
        PushedHeader("Collection", onBack = onBack, modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp))
        InkTabs(
            labels = listOf(
                "Relics ${vault?.ownedCount ?: 0} of ${vault?.total ?: 16}",
                "Crests ${crests.owned.size} of ${Gacha.CREST_FRAMES.size}",
            ),
            selectedIndex = tab,
            onSelect = { tab = it },
            fill = true,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (tab == CollectionTab.Relics.ordinal) {
                RelicsPage(vault, animate, onShowHouses = { housesOpen = true })
            } else {
                CrestsPage(crests, level, animate, onToggle = viewModel::wear)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (housesOpen) HousesSheet { housesOpen = false }
}

@Composable
private fun RelicsPage(vault: VaultState?, animate: Boolean, onShowHouses: () -> Unit) {
    Text(
        "The strongest relic counts most. A house adds bonuses for holding several of its relics.",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
    )
    HousesLink(onShowHouses)
    vault?.houses?.forEach { HouseBlock(it, animate) }
}

@Composable
private fun HouseBlock(house: HouseProgress, animate: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            buildAnnotatedString {
                append(house.house.title)
                withStyle(SpanStyle(color = IronvellumColors.InkMuted, fontWeight = FontWeight.Normal, fontSize = 12.sp)) {
                    append(" · ${house.house.habit.lowercase()}")
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = IronvellumColors.Ink,
            modifier = Modifier.weight(1f),
        )
        Text("${house.owned} of ${house.size}", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        house.slots.forEach { slot -> RelicTile(slot, animate, Modifier.weight(1f)) }
    }
    house.bonuses.forEachIndexed { index, bonus ->
        Row(
            Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = if (index == 0) 6.dp else 0.dp).heightIn(min = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (bonus.reached) {
                Icon(Icons.Filled.Check, contentDescription = "Reached", tint = IronvellumColors.SystemGreen, modifier = Modifier.size(14.dp))
            } else {
                Text("·", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.size(14.dp))
            }
            Text(
                "${bonus.needed} relics · ${bonus.summary}",
                style = MaterialTheme.typography.bodySmall,
                color = if (bonus.reached) IronvellumColors.Ink else IronvellumColors.InkMuted,
                modifier = Modifier.weight(1f),
            )
            if (!bonus.reached) {
                Text("${bonus.needed - house.owned} to go", style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
            }
        }
    }
}

/**
 * One cell: a held relic is its sigil in its tier's metal on a gradient edge; one not yet drawn keeps its
 * NAME, muted, over a silhouette, so a lifter can see what there is to find. The strongest wears a badge.
 */
@Composable
private fun RelicTile(slot: VaultSlot, animate: Boolean, modifier: Modifier = Modifier) {
    val held = slot.isOwned
    val description = buildString {
        append("${slot.relic.name}, ${rarityWord(slot.tier).lowercase()}")
        if (!held) append(", not yet drawn") else if (slot.active) append(", strongest")
    }
    Box(
        modifier
            .background(if (slot.active) IronvellumColors.VaultHigh else if (held) IronvellumColors.Vault else UnheldGround, TileShape)
            .then(
                if (held) Modifier.inkBorder(Metal.of(slot.tier).bounds(), TileShape, 1.dp) else Modifier.inkBorder(UnheldEdge, TileShape, 1.dp),
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = if (slot.active) 14.dp else 8.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HouseRelicSigil(slot.relic.id, slot.tier, Modifier.size(44.dp), owned = held, animate = animate && held)
            Text(
                slot.relic.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (held) IronvellumColors.Ink else UnheldName,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
            )
        }
        if (slot.active) {
            Text(
                "Strongest",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.background(IronvellumColors.Emerald).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun CrestsPage(crests: CrestUi, level: Int, animate: Boolean, onToggle: (String?) -> Unit) {
    val worn = crests.worn
    Text(
        (worn?.let { "Wearing the ${crestName(it).substringBefore(' ')} crest. " } ?: "") +
            "Tap an earned crest to wear it; allies see it on your folio.",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp, start = 2.dp, end = 2.dp),
    )
    // The next rung of the ladder, which wears a progress bar: the same crest the Veil's card names.
    val nextLevel = (level / Veil.MILESTONE_EVERY + 1) * Veil.MILESTONE_EVERY
    val next = Veil.milestoneCrest(nextLevel, crests.owned)?.takeIf { nextLevel / Veil.MILESTONE_EVERY <= Veil.CREST_LADDER.size }
    Gacha.CREST_FRAMES.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { frame ->
                CrestTile(
                    frameId = frame.id,
                    owned = frame.id in crests.owned,
                    worn = frame.id == worn,
                    toGo = (nextLevel - level).takeIf { frame.id == next },
                    progress = ((level - (nextLevel - Veil.MILESTONE_EVERY)).toFloat() / Veil.MILESTONE_EVERY).coerceIn(0f, 1f),
                    animate = animate,
                    onToggle = { onToggle(if (frame.id == worn) null else frame.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One crest. Earned and worn crests read in ink; one not yet held keeps its name, muted, and says how it
 * is won: a level for the seven of the ladder (the next one with its bar), a chance draw for the rest.
 * [toGo] is set only on the next milestone crest.
 */
@Composable
private fun CrestTile(
    frameId: String,
    owned: Boolean,
    worn: Boolean,
    toGo: Int?,
    progress: Float,
    animate: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val level = crestLevel(frameId)
    val source = level?.let { "Level $it" } ?: "Chance draw"
    val status = when {
        worn -> "Worn · $source"
        owned -> "Earned · $source"
        toGo != null -> "Next · Level $level, $toGo to go"
        else -> source
    }
    val name = crestName(frameId).replaceFirstChar { it.uppercase() }
    val edge = when {
        worn -> IronvellumColors.Emerald
        owned -> IronvellumColors.Rune
        toGo != null -> IronvellumColors.Rune
        else -> UnheldEdge
    }
    Box(
        modifier
            .background(if (worn) IronvellumColors.VaultHigh else if (owned) IronvellumColors.Vault else UnheldGround, TileShape)
            .inkBorder(edge, TileShape, 1.dp)
            .then(
                if (owned) {
                    Modifier.clickable(role = Role.Button, onClickLabel = if (worn) "Stop wearing $name" else "Wear $name", onClick = onToggle)
                } else {
                    Modifier
                },
            )
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$name, ${status.lowercase()}" },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = if (worn) 16.dp else 12.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CrestPlate(frameId, Modifier.size(64.dp), owned = owned, animate = animate)
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (owned) IronvellumColors.Ink else UnheldName,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(status, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, textAlign = TextAlign.Center)
            if (toGo != null) InkRail(progress, Modifier.padding(top = 2.dp), height = 4.dp)
        }
        if (worn) {
            Text(
                "Worn",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.background(IronvellumColors.Emerald).padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}
