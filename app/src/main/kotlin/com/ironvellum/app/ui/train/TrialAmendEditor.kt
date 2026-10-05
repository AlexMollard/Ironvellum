package com.ironvellum.app.ui.train

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.SealedEdit
import com.ironvellum.app.domain.TrialDraft
import com.ironvellum.app.domain.isStrength
import com.ironvellum.app.ui.components.ExercisePickerSheet
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.SectionHeader
import com.ironvellum.app.ui.components.decimalKeyboard
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.theme.IronvellumTracking

/**
 * The amend mode of a sealed trial: the same set rows, steppers and typed
 * load as the live trial, over a draft held by the view model. Nothing is
 * written until Save is confirmed; Cancel drops the draft.
 */
@Composable
internal fun TrialAmendEditor(
    draft: TrialDraft,
    exercises: Map<Long, Exercise>,
    onChange: ((TrialDraft) -> TrialDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var swapFor by remember { mutableStateOf<Int?>(null) }
    var loadFor by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    SectionHeader("AMEND THE TRIAL")
    Text(
        "Fix what was logged. Within 48 h of sealing, a correction can raise XP to at most double what the trial first paid; after that it can only lower it.",
        style = MaterialTheme.typography.bodySmall,
        color = IronvellumColors.InkMuted,
    )
    Spacer(Modifier.height(10.dp))

    draft.blocks.forEachIndexed { blockIdx, block ->
        val exercise = exercises[block.exerciseId]
        val metric = exercise?.metric ?: ExerciseMetric.REPS
        val isHold = metric == ExerciseMetric.HOLD
        InkPanel(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    block.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "CHANGE",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.SystemGreen,
                    letterSpacing = IronvellumTracking.InlineLabel,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClickLabel = "Change ${block.exerciseName}") { swapFor = blockIdx }
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                )
                IconButton(onClick = { onChange { it.addSet(blockIdx) } }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add set", tint = IronvellumColors.SystemGreen)
                }
            }
            if (block.modifiers.isNotBlank()) {
                Text(
                    block.modifiers.split(",").joinToString(" · ") { it.trim() },
                    style = MaterialTheme.typography.labelSmall,
                    color = IronvellumColors.SystemGreen,
                )
            }
            // Warm-ups read W and working sets count from 1 after them.
            val labels = block.sets.runningFold(0) { n, s -> if (s.warmup) n else n + 1 }.drop(1)
            block.sets.forEachIndexed { setIdx, set ->
                // A warm-up never counts, so its tick does not stick.
                fun update(change: (TrialDraft.DraftSet) -> TrialDraft.DraftSet) =
                    onChange { it.updateSet(blockIdx, setIdx) { s -> change(s).let { r -> r.copy(done = r.done && !r.warmup) } } }
                SetRow(
                    label = if (set.warmup) "W" else "${labels[setIdx]}",
                    exerciseName = block.exerciseName,
                    setIndex = setIdx,
                    // No PR line while amending: the records include this
                    // very trial, so "NEW PEAK" would compare it to itself.
                    records = emptyMap(),
                    bodyweight = null,
                    reps = if (isHold) (set.durationSec ?: 0) else set.reps,
                    weightKg = set.weightKg,
                    done = set.done,
                    isHold = isHold,
                    metric = metric,
                    isWeighted = exercise?.isWeighted ?: false,
                    durationSec = set.durationSec,
                    distanceM = set.distanceM,
                    grade = set.grade.orEmpty(),
                    scoresStrength = metric.isStrength,
                    showColumnLabels = setIdx == 0,
                    onRemove = { onChange { it.removeSet(blockIdx, setIdx) } },
                    onChange = { value, w, d ->
                        update {
                            if (isHold) it.copy(reps = 0, durationSec = value, weightKg = w, done = d)
                            else it.copy(reps = value, weightKg = w, done = d)
                        }
                    },
                    onLoadTap = { loadFor = blockIdx to setIdx },
                    onActivityChange = { reps, seconds, metres, grade, w, d ->
                        update { it.copy(reps = reps, durationSec = seconds, distanceM = metres, grade = grade, weightKg = w, done = d) }
                    },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    val canSave = draft.tickedCount > 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IronvellumButton("Cancel", quiet = true, onClick = onCancel, modifier = Modifier.weight(1f))
        IronvellumButton("Save", gold = true, enabled = canSave, onClick = onSave, modifier = Modifier.weight(1f))
    }
    if (!canSave) {
        Text(
            "Tick at least one set to save. A trial with nothing done is deleted, not amended.",
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }

    swapFor?.let { blockIdx ->
        // The block can vanish under an open sheet (its last set removed);
        // then nothing draws.
        val block = draft.blocks.getOrNull(blockIdx)
        if (block != null) {
            // A movement already in another block is left out: two blocks of
            // one movement would collide in the records and on the cloud.
            val taken = draft.blocks.filterIndexed { i, _ -> i != blockIdx }.map { it.exerciseId }.toSet()
            ExercisePickerSheet(
                exercises = exercises.values.filter { it.id !in taken },
                onPick = { picked ->
                    onChange { it.swapExercise(blockIdx, exercises[block.exerciseId], picked) }
                    swapFor = null
                },
                onDismiss = { swapFor = null },
                title = "CHANGE EXERCISE",
            )
        }
    }

    loadFor?.let { (blockIdx, setIdx) ->
        val set = draft.blocks.getOrNull(blockIdx)?.sets?.getOrNull(setIdx)
        if (set != null) {
            AmendLoadDialog(
                exerciseName = draft.blocks[blockIdx].exerciseName,
                setNo = setIdx + 1,
                initialKg = set.weightKg,
                onConfirm = { kg ->
                    onChange { d -> d.updateSet(blockIdx, setIdx) { it.copy(weightKg = kg.takeIf { v -> v > 0.0 }) } }
                    loadFor = null
                },
                onDismiss = { loadFor = null },
            )
        }
    }
}

/** The live trial's typed-load entry, for one set of the draft. */
@Composable
private fun AmendLoadDialog(
    exerciseName: String,
    setNo: Int,
    initialKg: Double?,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = initialKg?.let { loadText(it) }.orEmpty()
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val parsed = parseLoadKg(field.text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        containerColor = Color(0xFF0D1110),
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(exerciseName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "SET $setNo",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = ChakraPetch,
                    color = IronvellumColors.InkMuted,
                )
            }
        },
        text = {
            OutlinedTextField(
                shape = MaterialTheme.shapes.small,
                value = field,
                onValueChange = { field = cleanLoadInput(it) },
                singleLine = true,
                label = { Text("Load (kg)") },
                placeholder = { Text("Leave blank for bodyweight") },
                isError = parsed.isFailure,
                supportingText = if (parsed.isFailure) {
                    { Text("Enter 0 to ${MAX_LOAD_KG.toInt()} kg.") }
                } else {
                    null
                },
                keyboardOptions = decimalKeyboard(),
                colors = fieldColors(accent = IronvellumColors.SystemGreen),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            IronvellumButton("Set load", enabled = parsed.isSuccess, onClick = { parsed.getOrNull()?.let(onConfirm) })
        },
    )
}

/**
 * The confirmation before an amendment is written: the XP change it will
 * make, said plainly, including the cases where the sets moved but XP cannot.
 */
internal fun amendXpLine(settlement: SealedEdit.Settlement): String = when {
    settlement.applied > 0 && settlement.raiseCapped -> "+${settlement.applied} XP (capped at double what it first paid)"
    settlement.applied > 0 -> "+${settlement.applied} XP"
    settlement.applied < 0 -> "−${-settlement.applied} XP"
    settlement.raiseRefused -> "XP unchanged — edits after 48 h can't raise it"
    else -> "XP unchanged"
}

@Composable
internal fun AmendConfirmDialog(
    settlement: SealedEdit.Settlement,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        containerColor = Color(0xFF0D1110),
        onDismissRequest = onDismiss,
        title = { Text("Amend this trial?") },
        text = {
            Column {
                Text(
                    amendXpLine(settlement),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = ChakraPetch,
                    color = if (settlement.applied < 0) IronvellumColors.InkMuted else IronvellumColors.Emerald,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Allies see it marked amended. Deeds already earned stay earned, and no inscriptions are paid.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        },
        confirmButton = { IronvellumButton("Amend", onClick = onConfirm) },
        dismissButton = { IronvellumButton("Keep editing", quiet = true, onClick = onDismiss) },
    )
}
