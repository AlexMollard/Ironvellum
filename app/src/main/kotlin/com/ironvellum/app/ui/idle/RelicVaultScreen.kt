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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ironvellum.app.domain.HouseProgress
import com.ironvellum.app.domain.VaultSlot
import com.ironvellum.app.domain.VaultState
import com.ironvellum.app.ui.components.HouseRelicSigil
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.rarityWord
import com.ironvellum.app.ui.dashboard.rememberTodayMotion
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.RarityTint
import com.ironvellum.app.ui.theme.TileShape
import com.ironvellum.app.ui.theme.inkBorder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** The vault's own state, kept apart from the Veil screen's so opening it banks nothing. */
class RelicVaultViewModel(repo: Repository) : ViewModel() {
    val vault: StateFlow<VaultState?> = repo.observeVault()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * The relic vault: four houses by four forms. A relic held shows in the metal of its tier, one not
 * yet drawn is a silhouette, and under each house sit its two set bonuses with how far off they are.
 */
@Composable
fun RelicVaultScreen(
    onBack: () -> Unit,
    viewModel: RelicVaultViewModel =
        viewModel(factory = viewModelFactory { initializer { RelicVaultViewModel(ironvellumRepository()) } }),
) {
    val vault by viewModel.vault.collectAsStateWithLifecycle()
    var housesOpen by rememberSaveable { mutableStateOf(false) }
    val animate = rememberTodayMotion()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        PushedHeader("Relic vault", onBack = onBack, modifier = Modifier.padding(top = 8.dp))
        val state = vault
        if (state != null) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = IronvellumColors.Ink, fontWeight = FontWeight.SemiBold)) {
                            append("${state.ownedCount} of ${state.total}")
                        }
                        append(" relics found")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.InkMuted,
                    modifier = Modifier.weight(1f),
                )
                HousesLink { housesOpen = true }
            }
            state.houses.forEach { HouseBlock(it, animate) }
        }
        Spacer(Modifier.height(120.dp))
    }
    if (housesOpen) HousesSheet { housesOpen = false }
}

/** The quiet link that opens the houses sheet, on a 48dp target. */
@Composable
internal fun HousesLink(onClick: () -> Unit) {
    Text(
        "How houses work",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.SystemGreen,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Open how houses work", onClick = onClick)
            .padding(horizontal = 4.dp)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}

@Composable
private fun HouseBlock(house: HouseProgress, animate: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
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
            Modifier.fillMaxWidth().padding(top = if (index == 0) 6.dp else 0.dp).heightIn(min = 22.dp),
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

/** One cell: the sigil in its tier's metal and the relic's name, or a silhouette and "Not yet drawn". */
@Composable
private fun RelicTile(slot: VaultSlot, animate: Boolean, modifier: Modifier = Modifier) {
    val held = slot.isOwned
    val tint = if (held) RarityTint.of(slot.tier) else IronvellumColors.Rune
    val description = if (held) {
        "${slot.relic.name}, ${rarityWord(slot.tier).lowercase()}${if (slot.active) ", active" else ""}"
    } else {
        "Relic not yet drawn"
    }
    Box(
        modifier
            .background(if (slot.active) IronvellumColors.VaultHigh else if (held) IronvellumColors.Vault else IronvellumColors.Abyss, TileShape)
            .inkBorder(tint, TileShape, 1.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                HouseRelicSigil(slot.relic.id, slot.tier, Modifier.size(52.dp), owned = held, animate = slot.active && animate)
                if (!held) Text("?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = IronvellumColors.InkMuted)
            }
            Text(
                if (held) slot.relic.name else "Not yet drawn",
                style = MaterialTheme.typography.labelSmall,
                color = if (held) IronvellumColors.Ink else IronvellumColors.InkMuted,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (slot.active) {
            Text(
                "Active",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.background(IronvellumColors.Emerald).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}
