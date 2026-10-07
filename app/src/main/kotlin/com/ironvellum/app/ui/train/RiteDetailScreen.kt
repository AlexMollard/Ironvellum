package com.ironvellum.app.ui.train

import com.ironvellum.app.ui.components.PushedHeader
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.BoxWithConstraints
import com.ironvellum.app.ui.program.RiteMuscleMap
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.PlannedPreset
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.ironvellum.app.ui.components.InkChip
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.ListRow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.domain.Evidence
import com.ironvellum.app.domain.MovementDifficulty
import com.ironvellum.app.domain.SessionClock
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.program.RiteMusclesSheet
import com.ironvellum.app.ui.program.toPlanned
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking

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

    Column(Modifier.fillMaxSize()) {
        // The figure takes what the page has left, between its floor and full
        // size, so a short rite fits without scrolling and a long one scrolls
        // as little as it can. Measured content minus the viewport is exactly
        // what the figure must give up, so this settles in one pass.
        var figureHeight by remember { mutableStateOf(FIGURE_MAX) }
        val density = LocalDensity.current
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .onSizeChanged { content ->
                        val target = (figureHeight + viewport - with(density) { content.height.toDp() }).coerceIn(FIGURE_MIN, FIGURE_MAX)
                        if ((target - figureHeight).value.let { it > 1f || it < -1f }) figureHeight = target
                    }
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(20.dp))
                PushedHeader(
                    preset?.name ?: "Rite",
                    onBack,
                    actions = { if (preset != null) InkChip("Edit", "Edit ${preset.name}") { onEdit(preset.id) } },
                )
                if (preset == null) return@Column
                // "Wed · 6 exercises · ~54 min": the day folded into the plan line, no chip.
                val plan = listOfNotNull(
                    preset.scheduledDay?.let { dayLabel(it).lowercase().replaceFirstChar { c -> c.uppercase() } },
                    preset.entries.size.let { n -> if (n == 1) "1 exercise" else "$n exercises" },
                    SessionClock.planLine(preset.toPlanned().entries, ui.focus, ui.pace.secondsPerSet(preset.id))
                        .substringAfterLast(" · ").lowercase(),
                ).joinToString(" · ")
                Text(
                    plan,
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Evidence.split(preset.note).first.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.InkMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(SECTION_GAP))
                SectionHeader("MOVEMENTS")
                InkRowPanel(Modifier.fillMaxWidth()) {
                    preset.entries.forEachIndexed { index, entry ->
                        if (index > 0) InkDivider()
                        ListRow(
                            entry.exerciseName,
                            value = "${entry.targetSets}×${entry.targetReps}" +
                                (if (MovementDifficulty.isHoldByName(entry.exerciseName)) "s" else "") +
                                (entry.targetWeightKg?.let { "  @${formatKg(it)}" } ?: ""),
                            subline = entry.modifiers.takeIf { it.isNotBlank() }?.split(",")?.joinToString(" · ") { it.trim() },
                        )
                    }
                }
                // Where the rite lands, drawn on the page rather than behind a
                // button; each muscle's exact set count is one tap further.
                val sets = remember(preset) {
                    ProgramRules.weeklyVolume(listOf(PlannedPreset(preset.name, "", null, preset.toPlanned().entries)))
                }
                if (sets.values.any { it > 0.0 }) {
                    Spacer(Modifier.height(SECTION_GAP))
                    SectionHeader("WHERE IT LANDS", "Sets", "Sets per muscle") { showMuscles = true }
                    InkPanel(Modifier.fillMaxWidth()) {
                        RiteMuscleMap(sets, preset.name, Modifier.fillMaxWidth(), figureHeight = figureHeight)
                    }
                }
                Spacer(Modifier.height(SECTION_GAP))
            }
        }
        // Begin is pinned in reach of the thumb, so a long rite never pushes it
        // off screen. A trial of this rite already under way is continued.
        if (preset != null) {
            val resume = live?.takeIf { it.presetId == preset.id }
            IronvellumButton(
                label = if (resume != null) "Continue ${preset.name}" else "Begin ${preset.name}",
                onClick = { if (resume != null) onStartSession(resume.id) else viewModel.begin(preset.id, onStartSession) },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = GROUP_GAP, bottom = SECTION_GAP),
            )
        }
    }

    if (showMuscles && preset != null) {
        RiteMusclesSheet(title = preset.name, entries = preset.toPlanned().entries, onDismiss = { showMuscles = false })
    }
}

/** A section's label, with the one action that belongs to it, if any, as an InkChip on the right. */
@Composable
private fun SectionHeader(label: String, action: String? = null, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChakraPetch,
            color = IronvellumColors.InkMuted,
            letterSpacing = IronvellumTracking.SectionHeader,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (action != null) InkChip(action, actionLabel, onClick = onAction)
    }
}

/** The rite's figure: full size when the page has the room, never below the floor at which muscles stay legible. */
private val FIGURE_MAX = 220.dp
private val FIGURE_MIN = 140.dp

