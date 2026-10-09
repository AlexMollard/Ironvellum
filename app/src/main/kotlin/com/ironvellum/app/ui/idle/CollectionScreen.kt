package com.ironvellum.app.ui.idle

import androidx.activity.compose.BackHandler
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
import com.ironvellum.app.domain.Crests
import com.ironvellum.app.domain.HouseProgress
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.VaultSlot
import com.ironvellum.app.domain.VaultState
import com.ironvellum.app.domain.Veil
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.ui.components.CrestPlate
import com.ironvellum.app.ui.components.HouseRelicSigil
import com.ironvellum.app.ui.components.InkRail
import com.ironvellum.app.data.cloud.CloudSyncWorker
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CollectionViewModel(private val repo: Repository) : ViewModel() {
    val vault: StateFlow<VaultState?> = repo.observeVault()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val deeds = MutableStateFlow<Map<String, Titles.Progress>>(emptyMap())

    private val held = combine(
        repo.observeOwnedFrames(),
        repo.observeEquippedFrame(),
        repo.observeCrestWorth(),
        repo.observeEquipChangedAt(),
        repo.observeVault(),
    ) { owned, worn, worth, changedAt, vault ->
        CrestUi(
            owned = owned,
            worn = worn,
            worth = worth,
            houseRelics = vault.houses.associate { it.house to it.owned },
            mayChange = Crests.canChange(changedAt, System.currentTimeMillis()),
        )
    }

    val crests: StateFlow<CrestUi> = combine(
        held,
        deeds,
        repo.observeProfile().map { Xp.levelFor(it?.totalXp ?: 0L) },
    ) { crests, deeds, level -> crests.copy(deeds = deeds, level = level) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CrestUi())

    private val _notice = MutableStateFlow<String?>(null)

    /** What the last attempt to wear a crest said, when it did not work. */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        // The deed crests show their deed's progress. The ledger is read when the crests change (a deed
        // pays a crest), not on every set logged.
        viewModelScope.launch {
            repo.observeOwnedFrames().collect {
                val ledger = repo.currentLedger()
                deeds.value = Crests.ALL.mapNotNull { it.deed }.associate { it.id to Titles.progress(it.rule, ledger) }
            }
        }
    }

    /** Wears [frameId]; the repository collects first and refuses a crest not owned or a second change in a day. */
    fun wear(frameId: String?, onWorn: () -> Unit = {}) {
        viewModelScope.launch {
            _notice.value = when (repo.equipFrame(frameId)) {
                Crests.Equip.Worn -> { onWorn(); null }
                Crests.Equip.TooSoon -> "You changed your crest today. Come back tomorrow."
                Crests.Equip.NotOwned -> "That crest is not yours yet."
            }
        }
    }

    fun clearNotice() {
        _notice.value = null
    }
}

/** A name the collection draws for something not yet held: legible, but quiet. */
private val UnheldName = Color(0xFF8A8780)

/**
 * Everything collected, on one pushed page with two tabs: the sixteen relics by house, and the 28 crests.
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
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }
    var openCrest by rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var housesOpen by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val animate = rememberTodayMotion()
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext

    // A crest's own page replaces the lists; back returns to them.
    val sheet = Crests.byId(openCrest)
    if (sheet != null) {
        BackHandler { openCrest = null; viewModel.clearNotice() }
        CrestSheet(
            def = sheet,
            crests = crests,
            animate = animate,
            notice = notice,
            // Allies read the worn crest off the profile row: push it now rather than at the next daily run.
            onWear = { viewModel.wear(sheet.id) { CloudSyncWorker.pushNow(appContext) } },
            onBack = { openCrest = null; viewModel.clearNotice() },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        PushedHeader("Collection", onBack = onBack, modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp))
        InkTabs(
            labels = listOf(
                "Relics ${vault?.ownedCount ?: 0} of ${vault?.total ?: 16}",
                "Crests ${crests.owned.size} of ${Crests.ALL.size}",
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
                CrestsPage(crests, animate, onOpen = { openCrest = it })
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
