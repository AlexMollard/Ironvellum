package com.ironvellum.app.ui.idle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.Repository
import com.ironvellum.app.domain.Gacha
import com.ironvellum.app.domain.Veil
import com.ironvellum.app.ui.components.CrestPlate
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.TileShape
import com.ironvellum.app.ui.theme.inkBorder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The crests held and the one worn. */
data class CrestUi(val owned: Set<String> = emptySet(), val worn: String? = null)

class CrestCollectionViewModel(private val repo: Repository) : ViewModel() {
    val ui: StateFlow<CrestUi> = combine(repo.observeOwnedFrames(), repo.observeEquippedFrame()) { owned, worn ->
        CrestUi(owned, worn)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CrestUi())

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

/**
 * The crest collection: the ten crests in two columns. A held crest wears or lifts off with a tap; one
 * not yet held sinks to a whisper and says how it is won, a level for the seven of the ladder and a
 * chance draw for the rest.
 */
@Composable
fun CrestCollectionScreen(
    onBack: () -> Unit,
    viewModel: CrestCollectionViewModel =
        viewModel(factory = viewModelFactory { initializer { CrestCollectionViewModel(ironvellumRepository()) } }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        PushedHeader("Crest collection", onBack = onBack, modifier = Modifier.padding(top = 8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = IronvellumColors.Ink, fontWeight = FontWeight.SemiBold)) {
                    append("${ui.owned.size} of ${Gacha.CREST_FRAMES.size}")
                }
                append(" earned")
                ui.worn?.let { append(" · wearing ${crestName(it).substringBefore(' ').replaceFirstChar { c -> c.uppercase() }}") }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        Gacha.CREST_FRAMES.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { frame ->
                    CrestTile(
                        frameId = frame.id,
                        owned = frame.id in ui.owned,
                        worn = frame.id == ui.worn,
                        onToggle = { viewModel.wear(if (frame.id == ui.worn) null else frame.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Text(
            "Tap an earned crest to wear it. It shows on your folio.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun CrestTile(frameId: String, owned: Boolean, worn: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val level = crestLevel(frameId)
    val source = level?.let { "Level $it" } ?: "Chance draw"
    val status = when {
        worn -> "Worn · $source"
        owned -> "Earned · $source"
        else -> source
    }
    val name = crestName(frameId).replaceFirstChar { it.uppercase() }
    Column(
        modifier
            .background(if (worn) IronvellumColors.VaultHigh else IronvellumColors.Vault, TileShape)
            .inkBorder(if (worn) IronvellumColors.Emerald else IronvellumColors.Rune, TileShape, 1.dp)
            .then(
                if (owned) {
                    Modifier.clickable(role = Role.Button, onClickLabel = if (worn) "Stop wearing $name" else "Wear $name", onClick = onToggle)
                } else {
                    Modifier
                },
            )
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$name, ${status.lowercase()}" }
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CrestPlate(frameId, Modifier.size(72.dp), owned = owned)
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (owned) IronvellumColors.Ink else IronvellumColors.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        Text(status, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted, textAlign = TextAlign.Center)
    }
}
