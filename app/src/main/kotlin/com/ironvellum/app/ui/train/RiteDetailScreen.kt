package com.ironvellum.app.ui.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.domain.Evidence
import com.ironvellum.app.domain.MovementDifficulty
import com.ironvellum.app.domain.SessionClock
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.NavChip
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.program.RiteMusclesSheet
import com.ironvellum.app.ui.program.toPlanned
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking
import com.ironvellum.app.ui.theme.inkBorder
import com.ironvellum.app.ui.theme.inkHairline

/**
 * One rite, whole: every movement, its note, and the way to begin or edit it.
 * The Train list and Today's card both open here, so a rite is always looked
 * at before it is started.
 */
@Composable
fun RiteDetailScreen(
    presetId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onStartSession: (Long) -> Unit,
    viewModel: PresetsViewModel =
        viewModel(factory = viewModelFactory {
            val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
            initializer { PresetsViewModel(ironvellumRepository(), appContext) }
        }),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val preset = ui.presets.firstOrNull { it.id == presetId }
    var showMuscles by rememberSaveable { mutableStateOf(false) }

    // Deleted from the editor: there is nothing left to show. Only once it has
    // been seen, since the list is empty until the first emission.
    var seen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(preset != null) {
        if (preset != null) seen = true else if (seen) onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "RITE",
                style = MaterialTheme.typography.labelLarge,
                color = IronvellumColors.SystemGreen,
                letterSpacing = IronvellumTracking.ScreenTitle,
            )
            NavChip("BACK", Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
        }
        if (preset == null) return@Column
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                preset.name.uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                color = IronvellumColors.EmeraldBright,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
            )
            preset.scheduledDay?.let {
                Box(
                    Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .inkBorder(IronvellumColors.SovereignGold, MaterialTheme.shapes.extraSmall, 1.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(dayLabel(it), style = MaterialTheme.typography.labelSmall, color = IronvellumColors.SovereignGold)
                }
            }
        }
        Text(
            SessionClock.planLine(preset.toPlanned().entries, ui.focus, ui.pace.secondsPerSet(preset.id)),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.InlineLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Evidence.split(preset.note).first.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        // The act comes before the list, so a long rite never pushes it off
        // screen. A trial of this rite already under way is continued.
        val resume = live?.takeIf { it.presetId == preset.id }
        IronvellumButton(
            label = if (resume != null) "Continue ${preset.name}" else "Begin ${preset.name}",
            onClick = { if (resume != null) onStartSession(resume.id) else viewModel.begin(preset.id, onStartSession) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IronvellumButton("Edit", onClick = { onEdit(preset.id) }, quiet = true, modifier = Modifier.weight(1f))
            IronvellumButton("Muscles", onClick = { showMuscles = true }, quiet = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        preset.entries.forEachIndexed { index, entry ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().height(2.dp).inkHairline(IronvellumColors.Rune, seed = index))
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(entry.exerciseName, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
                    if (entry.modifiers.isNotBlank()) {
                        Text(
                            entry.modifiers.split(",").joinToString(" · ") { it.trim() },
                            style = MaterialTheme.typography.labelSmall,
                            color = IronvellumColors.SystemGreen,
                        )
                    }
                }
                Text(
                    "${entry.targetSets}×${entry.targetReps}" +
                        (if (MovementDifficulty.isHoldByName(entry.exerciseName)) "s" else "") +
                        (entry.targetWeightKg?.let { "  @${formatKg(it)}" } ?: ""),
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (showMuscles && preset != null) {
        RiteMusclesSheet(title = preset.name, entries = preset.toPlanned().entries, onDismiss = { showMuscles = false })
    }
}
