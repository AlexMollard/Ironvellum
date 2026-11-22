package com.ironvellum.app.ui.ai

import android.content.ClipData
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.data.Units
import com.ironvellum.app.data.localconnector.LocalCatalogue
import com.ironvellum.app.domain.*
import com.ironvellum.app.ui.components.*
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.math.BigDecimal

/** Editable strings stay with the review in memory, including invalid input and exact decimal precision. */
internal class WorkoutImportEdits(private val original: WorkoutDraft, private val unmatched: Set<String> = emptySet()) {
    var name by mutableStateOf(original.name)
    val entries = original.entries.map(::Entry)
    val selections get() = entries.mapIndexedNotNull { index, entry ->
        entry.selection?.let { index to it.id }
    }.toMap()
    val unresolved get() = entries.any(::needsChoice)
    fun needsChoice(entry: Entry) = entry.selection == null && entry.key in unmatched

    val canSave get() = !unresolved && validDraft != null
    val validDraft: WorkoutDraft? get() = try {
        WorkoutDraft.parseEdited(buildJsonObject {
            original.toJson().forEach { (key, value) -> put(key, value) }
            put("name", name)
            put("entries", buildJsonArray {
                entries.forEach { entry ->
                    add(buildJsonObject {
                        put("exerciseKey", entry.key)
                        put("sets", entry.setCount)
                        put("reps", entry.repCount)
                        put("loadKg", JsonPrimitive(entry.weight))
                    })
                }
            })
        })
    } catch (_: IllegalArgumentException) {
        null
    }

    internal class Entry(val original: WorkoutDraft.Entry) {
        var selection by mutableStateOf<Exercise?>(null)
        val key get() = selection?.let { Titles.normaliseName(it.name) } ?: original.exerciseKey
        var sets by mutableStateOf(original.sets.toString())
        var reps by mutableStateOf(original.reps.toString())
        var load by mutableStateOf(original.loadKg?.let(::exactLoad).orEmpty())
        val setCount get() = sets.toIntOrNull()?.takeIf { it in 1..50 } ?: throw IllegalArgumentException()
        val repCount get() = reps.toIntOrNull()?.takeIf { it in 1..1000 } ?: throw IllegalArgumentException()
        val weight: BigDecimal? get() = if (load.isBlank()) null else {
            // A wire token has at most 128 characters. Its expanded spelling can also include leading decimal zeroes.
            require(load.length <= 512)
            load.replace(',', '.').toBigDecimal().also {
                require(it >= BigDecimal.ZERO && it <= 1000.toBigDecimal())
            }.stripTrailingZeros().also { LocalJsonReader(it.toString()).document() }
        }
    }
}

private fun exactLoad(value: BigDecimal): String = if (value.scale() > 128) value.toString() else value.toPlainString()

@Composable
internal fun WorkoutPaste(text: String, problem: Problem?, update: (String) -> Unit, review: () -> Unit, open: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copyLabel = stringResource(R.string.workout_import_copy)
    AiTitle(localText(R.string.workout_import_prompt, R.string.workout_import_prompt__t))
    AiDetail(stringResource(R.string.workout_import_prompt_hint))
    OutlinedTextField(
        text, update, Modifier.fillMaxWidth(), minLines = 5, maxLines = 8,
        label = { Text(localText(R.string.workout_import_code, R.string.workout_import_code__t)) },
        shape = MaterialTheme.shapes.medium,
        isError = problem != null,
    )
    problem?.let { AiStateCard(stringResource(it.title), Icons.Filled.Info, stringResource(it.detail)) }
    IronvellumButton(stringResource(R.string.workout_import_paste), {
        scope.launch { clipboard.getClipEntry()?.clipData?.getItemAt(0)?.text?.let { update(it.toString()) } }
    }, Modifier.fillMaxWidth(), quiet = true)
    Primary(localText(R.string.ai_review, R.string.ai_review__t), review, text.isNotBlank())
    IronvellumButton(stringResource(R.string.workout_import_open_instead), open, Modifier.fillMaxWidth(), quiet = true)
    AiCard {
        AiTitle(stringResource(R.string.workout_import_format))
        AiDetail(localText(R.string.workout_import_format_hint, R.string.workout_import_format_hint__t))
        IronvellumButton(copyLabel, {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copyLabel, PortableWorkout.instructions())))
            }
        }, Modifier.fillMaxWidth(), quiet = true)
    }
}

@Composable
internal fun WorkoutImportReview(page: LocalPage.Review, exercises: List<Exercise>, enabled: Boolean) {
    val edits = page.edits
    val draft = page.local?.draft ?: checkNotNull(page.portable).draft
    val setTotal = edits.entries.sumOf { it.sets.toIntOrNull()?.takeIf { count -> count in 1..50 } ?: 0 }
    var pick by remember { mutableStateOf<WorkoutImportEdits.Entry?>(null) }
    val nameLabel = localText(R.string.workout_import_name, R.string.workout_import_name__t)
    OutlinedTextField(edits.name, { edits.name = it.take(161) }, Modifier.fillMaxWidth(), enabled = enabled,
        label = { Text(nameLabel) }, singleLine = true, shape = MaterialTheme.shapes.medium,
        trailingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) })
    AiDetail(stringResource(R.string.local_ai_draft_counts,
        pluralStringResource(R.plurals.effort_movements, edits.entries.size, edits.entries.size),
        pluralStringResource(R.plurals.common_sets_count, setTotal, setTotal)))
    val local = page.local
    if (local == null) {
        AiDisclosure(localText(R.string.workout_import_source, R.string.workout_import_source__t)) {
            AiDetail(localText(R.string.workout_import_source_hint, R.string.workout_import_source_hint__t))
        }
    } else {
        AiDetail(stringResource(R.string.local_ai_sender, local.proposal.sender))
        if (!local.isLatestExport) {
            AiStateCard(stringResource(R.string.local_ai_older, aiDate(local.exportedAtMs)), Icons.Filled.Info)
        }
        AiDisclosure(stringResource(R.string.local_ai_draft_details)) {
            AiDetail(stringResource(R.string.local_ai_prepared, aiDate(local.exportedAtMs)))
            AiDetail(stringResource(R.string.local_ai_created, aiDate(local.proposal.createdAtMs)))
            AiDetail(stringResource(R.string.ai_expiry, aiDate(local.proposal.expiresAtMs)))
        }
    }
    AiTitle(stringResource(R.string.ai_movements))
    edits.entries.forEach { entry ->
        val name = entry.selection?.name ?: LocalCatalogue.movements[entry.key]?.name
            ?: entry.key.replaceFirstChar { it.titlecase() }
        MovementEdit(entry, name, edits.needsChoice(entry), enabled) { pick = entry }
    }
    SettingsGroup(stringResource(R.string.ai_preparation), rows = true) {
        listOf(R.string.ai_warmup to draft.warmup, R.string.ai_cooldown to draft.cooldown).forEachIndexed { index, (label, choice) ->
            if (index != 0) InkDivider()
            ListRow(stringResource(R.string.ai_prep_line, stringResource(label),
                stringResource(if (choice == WorkoutDraft.Preparation.AUTO) R.string.ai_auto else R.string.ai_none)))
        }
    }
    if (!edits.unresolved && edits.validDraft == null) AiDetail(stringResource(R.string.workout_import_invalid_targets))
    pick?.let { entry ->
        DraftMovementPicker(exercises, onDismiss = { pick = null }) { selected ->
            entry.selection = selected
            pick = null
        }
    }
}

@Composable
private fun MovementEdit(entry: WorkoutImportEdits.Entry, name: String, unresolved: Boolean, enabled: Boolean, choose: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val warning = IronvellumColors.Caution
    AiCard(if (unresolved) Modifier.border(1.dp, warning, shape) else Modifier,
        PaddingValues(12.dp)) {
        if (unresolved) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = warning)
                AiTitle(stringResource(R.string.workout_import_unmatched, name))
            }
            AiDetail(stringResource(R.string.workout_import_gallery))
            IronvellumButton(stringResource(R.string.workout_import_choose_movement), choose,
                Modifier.fillMaxWidth(), enabled = enabled, quiet = true)
            InkDivider()
            val target = entry.original
            AiDetail(stringResource(R.string.local_ai_draft_counts,
                stringResource(R.string.ai_targets,
                    pluralStringResource(R.plurals.common_sets_count, target.sets, target.sets),
                    pluralStringResource(R.plurals.common_reps_count, target.reps, target.reps)),
                target.loadKg?.let { stringResource(R.string.ai_load, exactLoad(it)) } ?: stringResource(R.string.ai_no_load)))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { AiTitle(name) }
                val change = stringResource(R.string.workout_import_change, name)
                TextButton(choose, enabled = enabled, modifier = Modifier.semantics { contentDescription = change }) {
                    Text(stringResource(R.string.settings_import_review_change))
                }
            }
            val fields: @Composable (Modifier) -> Unit = { modifier ->
                TargetField(stringResource(R.string.preseteditor_sets_label), entry.sets,
                    { entry.sets = it }, enabled, modifier, runCatching { entry.setCount }.isFailure)
                TargetField(stringResource(R.string.preseteditor_reps_label), entry.reps,
                    { entry.reps = it }, enabled, modifier, runCatching { entry.repCount }.isFailure)
                TargetField(stringResource(R.string.workout_import_load), entry.load,
                    { entry.load = it }, enabled, modifier, runCatching { entry.weight }.isFailure, KeyboardType.Decimal)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 280.dp && LocalDensity.current.fontScale <= 1.3f) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { fields(Modifier.weight(1f)) }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { fields(Modifier.fillMaxWidth()) }
                }
            }
            val kg = runCatching { entry.weight }.getOrNull()
            if (kg != null) {
                if (entry.load.length > 12) AiDetail(stringResource(R.string.ai_load, exactLoad(kg)))
                val unit = Units.forExercise(entry.key)
                if (unit != LoadUnit.KG) AiDetail(Loads.withUnit(kg.toDouble(), unit))
            }
        }
    }
}

@Composable
private fun TargetField(label: String, value: String, update: (String) -> Unit, enabled: Boolean, modifier: Modifier,
                        invalid: Boolean, type: KeyboardType = KeyboardType.Number) {
    OutlinedTextField(value, update, modifier, enabled = enabled, label = { Text(label) },
        isError = invalid, singleLine = true, shape = MaterialTheme.shapes.medium, keyboardOptions = KeyboardOptions(keyboardType = type))
}

@Composable
private fun DraftMovementPicker(exercises: List<Exercise>, onDismiss: () -> Unit, onPick: (Exercise) -> Unit) {
    var query by remember { mutableStateOf("") }
    val eligible = remember(exercises) {
        exercises.groupBy { Titles.normaliseName(it.name) }.mapNotNull { (key, rows) ->
            rows.singleOrNull()?.takeIf { !it.custom && it.metric == ExerciseMetric.REPS && LocalCatalogue.acceptsDraft(key) }
        }.sortedBy { it.name }
    }
    val matching = remember(eligible, query) { eligible.filter { it.name.contains(query.trim(), ignoreCase = true) } }
    InkPickerSheet(stringResource(R.string.workout_import_choose_movement), onDismiss, query, { query = it },
        stringResource(R.string.picker_search_label), matching.size,
        countLabel = { pluralStringResource(R.plurals.picker_exercise_count, it, it) }) {
        items(matching, key = { it.id }) { exercise ->
            ListRow(exercise.name, onClick = { onPick(exercise) })
            InkDivider()
        }
    }
}
