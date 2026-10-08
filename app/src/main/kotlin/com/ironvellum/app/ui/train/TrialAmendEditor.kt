package com.ironvellum.app.ui.train

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.SealedEdit
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.TrialDraft
import com.ironvellum.app.domain.isStrength
import com.ironvellum.app.domain.workingNumber
import com.ironvellum.app.ui.components.DockedActionBar
import com.ironvellum.app.ui.components.ExerciseInfoSheet
import com.ironvellum.app.ui.components.ExercisePickerSheet
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.IronvellumDialog
import com.ironvellum.app.ui.components.decimalKeyboard
import com.ironvellum.app.ui.theme.ChakraPetch
import com.ironvellum.app.ui.theme.IronvellumColors

/**
 * The amend mode of a sealed trial: the live trial screen over a draft held by
 * the view model. One exercise is open, the rest fold to a line, and the open
 * one uses the live card's set rows, steppers and edit row. Nothing is written
 * until Save is confirmed; Cancel drops the draft.
 *
 * Nothing live-only comes along: no rest timer, no warm-up step, no
 * carry-forward ticks and no exercise notes, which the draft does not hold.
 *
 * [previewXp] returns the XP the draft would change the trial by, for the Save
 * label. A host that docks the bar itself passes [showSaveBar] = false and
 * draws [AmendSaveBar] where it wants it.
 */
@Composable
internal fun TrialAmendEditor(
    draft: TrialDraft,
    exercises: Map<Long, Exercise>,
    onChange: ((TrialDraft) -> TrialDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    previewXp: (suspend (TrialDraft) -> Int)? = null,
    showSaveBar: Boolean = true,
) {
    var swapFor by remember { mutableStateOf<Int?>(null) }
    var loadFor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var typing by remember { mutableStateOf<Triple<Int, Int, FigureKind>?>(null) }
    var infoFor by remember { mutableStateOf<Int?>(null) }
    // The exercise held open, by position; and the set whose edit row is showing.
    var openAt by rememberSaveable { mutableIntStateOf(0) }
    var editingSetId by rememberSaveable { mutableStateOf<Long?>(null) }
    val open = openAt.coerceIn(0, (draft.blocks.size - 1).coerceAtLeast(0))
    LaunchedEffect(open) { editingSetId = null }

    Spacer(Modifier.height(6.dp))
    var previousFolded = false
    draft.blocks.forEachIndexed { blockIdx, block ->
        val exercise = exercises[block.exerciseId]
        val metric = exercise?.metric ?: ExerciseMetric.REPS
        val weighted = exercise?.isWeighted ?: false
        val shown = TrialBlock(
            id = block.exerciseId,
            position = blockIdx,
            sets = block.sets.mapIndexed { setIdx, set -> set.asSessionSet(block, blockIdx, setIdx) },
        )
        fun update(setIdx: Int, change: (TrialDraft.DraftSet) -> TrialDraft.DraftSet) =
            onChange { it.updateSet(blockIdx, setIdx, change) }
        if (blockIdx == open) {
            Spacer(Modifier.height(12.dp))
            OpenExerciseCard(
                block = shown,
                metric = metric,
                weighted = weighted,
                // Neither a reference line nor a PEAK tag: the records include
                // this very trial, so each would compare it to itself.
                reference = null,
                reason = null,
                note = null,
                lastNote = null,
                onNote = null,
                modifiersEditable = false,
                activeSetId = null,
                editingSetId = editingSetId,
                newPeaks = emptySet(),
                canMoveUp = false,
                canMoveDown = false,
                onInfo = { infoFor = blockIdx },
                onEditModifiers = {},
                onMove = {},
                onEditLoad = if (metric.isStrength || (metric == ExerciseMetric.DURATION && weighted)) {
                    { loadFor = blockIdx to (shown.sets.firstOrNull { it.isPending } ?: shown.sets.last()).id.toInt() }
                } else {
                    null
                },
                onAddSet = {},
                onRemoveSet = { set ->
                    editingSetId = null
                    onChange { it.removeSet(blockIdx, set.id.toInt()) }
                },
                onRemoveExercise = {
                    editingSetId = null
                    onChange { d -> (0 until block.sets.size).fold(d) { acc, _ -> acc.removeSet(blockIdx, 0) } }
                },
                onEdit = { set -> update(set.id.toInt()) { set.asDraftSet() } },
                onTypeLoad = { set -> loadFor = blockIdx to set.id.toInt() },
                onTypeFigure = { set, kind -> typing = Triple(blockIdx, set.id.toInt(), kind) },
                onToggleEdit = { set -> editingSetId = if (editingSetId == set.id) null else set.id },
                // A warm-up never counts, so marking one takes the tick off.
                onToggleWarmup = { set -> update(set.id.toInt()) { it.copy(warmup = !it.warmup, done = false) } },
                onUnlog = { set ->
                    update(set.id.toInt()) { it.copy(done = false) }
                    editingSetId = null
                },
                amend = true,
                onChangeExercise = { swapFor = blockIdx },
                onLog = { set ->
                    update(set.id.toInt()) { it.copy(done = true) }
                    editingSetId = null
                },
                footer = {
                    AmendAddSetRow {
                        // A copy of the last set, unticked; open it so it can be set and logged.
                        editingSetId = block.sets.size.toLong()
                        onChange { it.addSet(blockIdx) }
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            previousFolded = false
        } else {
            if (previousFolded) FolderRule()
            FoldedExerciseRow(
                name = block.exerciseName,
                subline = foldedSubline(shown, metric, weighted),
                done = shown.sets.count { it.done },
                total = shown.sets.count { !it.warmup },
                hasNote = false,
                onOpen = { openAt = blockIdx },
            )
            previousFolded = true
        }
    }

    val canSave = draft.tickedCount > 0
    if (showSaveBar) {
        Spacer(Modifier.height(24.dp))
        val xpDelta by produceState<Int?>(null, draft, canSave) {
            value = if (canSave && previewXp != null) runCatching { previewXp(draft) }.getOrNull() else null
        }
        AmendSaveBar(canSave = canSave, xpDelta = xpDelta, onSave = onSave, onCancel = onCancel)
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
                title = "Change exercise",
            )
        }
    }

    loadFor?.let { (blockIdx, setIdx) ->
        val block = draft.blocks.getOrNull(blockIdx)
        val set = block?.sets?.getOrNull(setIdx)
        if (block != null && set != null) {
            AmendLoadDialog(
                exerciseName = block.exerciseName,
                setLabel = if (set.warmup) "Warm-up" else "Set ${block.sets.take(setIdx + 1).count { !it.warmup }}",
                initialKg = set.weightKg,
                onConfirm = { kg ->
                    onChange { d -> d.updateSet(blockIdx, setIdx) { it.copy(weightKg = kg.takeIf { v -> v > 0.0 }) } }
                    loadFor = null
                },
                onDismiss = { loadFor = null },
            )
        }
    }

    typing?.let { (blockIdx, setIdx, kind) ->
        val block = draft.blocks.getOrNull(blockIdx)
        val set = block?.sets?.getOrNull(setIdx)
        if (block != null && set != null) {
            val shown = set.asSessionSet(block, blockIdx, setIdx)
            FigureEntryDialog(
                set = shown,
                number = shown.workingNumber(block.sets.mapIndexed { i, s -> s.asSessionSet(block, blockIdx, i) }),
                kind = kind,
                onApply = { n ->
                    onChange { d ->
                        d.updateSet(blockIdx, setIdx) { it.asSessionSet(block, blockIdx, setIdx).withTypedFigure(kind, n).asDraftSet() }
                    }
                    typing = null
                },
                onDismiss = { typing = null },
            )
        }
    }

    infoFor?.let { blockIdx ->
        val block = draft.blocks.getOrNull(blockIdx)
        val exercise = block?.let { exercises[it.exerciseId] }
        if (block != null && exercise != null) {
            ExerciseInfoSheet(
                exercise = exercise,
                lastLine = null,
                onDismiss = { infoFor = null },
                onPick = null,
                modifiers = block.modifiers,
            )
        } else {
            LaunchedEffect(blockIdx) { infoFor = null }
        }
    }
}

/** The set as the live card draws it. A set's id is its place in the block, which the draft addresses by. */
private fun TrialDraft.DraftSet.asSessionSet(block: TrialDraft.Block, blockIdx: Int, setIdx: Int) = SessionSet(
    id = setIdx.toLong(),
    exerciseId = block.exerciseId,
    exerciseName = block.exerciseName,
    exercisePosition = blockIdx,
    setIndex = setIdx,
    reps = reps,
    weightKg = weightKg,
    modifiers = block.modifiers,
    done = done,
    durationSec = durationSec,
    distanceM = distanceM,
    grade = grade,
    warmup = warmup,
    supersetGroup = block.supersetGroup,
)

/** A warm-up never counts, so its tick does not stick. */
private fun SessionSet.asDraftSet() = TrialDraft.DraftSet(
    reps = reps,
    weightKg = weightKg,
    durationSec = durationSec,
    distanceM = distanceM,
    grade = grade,
    done = done && !warmup,
    warmup = warmup,
)

/** The quiet row under an open exercise's sets that adds another. */
@Composable
private fun AmendAddSetRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = IronvellumColors.SystemGreen, modifier = Modifier.size(18.dp))
        Text("Add set", style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.SystemGreen)
    }
}

/**
 * The amend editor's one bar: "Save changes", with what it does to XP when
 * that is known, and Cancel under it. With nothing ticked there is nothing to
 * save: a trial with nothing done is deleted, not amended.
 */
@Composable
internal fun AmendSaveBar(canSave: Boolean, xpDelta: Int?, onSave: () -> Unit, onCancel: () -> Unit) {
    if (canSave) {
        val xp = when {
            xpDelta == null || xpDelta == 0 -> ""
            xpDelta > 0 -> " · +$xpDelta XP"
            else -> " · −${-xpDelta} XP"
        }
        DockedActionBar(
            primary = "Save changes$xp",
            onPrimary = onSave,
            link = "Cancel",
            linkColor = IronvellumColors.InkMuted,
            onLink = onCancel,
        )
    } else {
        Column(Modifier.fillMaxWidth().background(IronvellumColors.VaultHigh).padding(16.dp)) {
            Text(
                "Tick at least one set to save. A trial with nothing done is deleted, not amended.",
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IronvellumButton("Cancel", quiet = true, onClick = onCancel)
            }
        }
    }
}

/** The live trial's typed-load entry, for one set of the draft. */
@Composable
private fun AmendLoadDialog(
    exerciseName: String,
    setLabel: String,
    initialKg: Double?,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = initialKg?.let { loadText(it) }.orEmpty()
    var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val parsed = parseLoadKg(field.text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = IronvellumColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    setLabel,
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
    IronvellumDialog(
        onDismissRequest = onDismiss,
        title = { Text("Amend this trial?", color = IronvellumColors.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    amendXpLine(settlement),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = ChakraPetch,
                    // Gold is for XP gained; a cut or a standstill reads in plain ink.
                    color = if (settlement.applied > 0) IronvellumColors.SovereignGold else IronvellumColors.Ink,
                )
                Text(
                    "Allies see it marked amended. Deeds already earned stay earned, and no inscriptions are paid.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.InkMuted,
                )
                Text(
                    "Within 48 h of sealing, a change can raise XP to at most double what the trial first paid. After that it can only lower it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IronvellumColors.InkMuted,
                )
            }
        },
        confirmButton = { IronvellumButton("Save changes", onClick = onConfirm) },
        dismissButton = { IronvellumButton("Keep editing", quiet = true, onClick = onDismiss) },
    )
}
