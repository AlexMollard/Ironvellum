package com.ironvellum.app.ui.components

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.CustomExercises
import com.ironvellum.app.domain.CustomLogged
import com.ironvellum.app.domain.CustomNeed
import com.ironvellum.app.domain.CustomSave
import com.ironvellum.app.domain.CustomSpec
import com.ironvellum.app.domain.CustomType
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.program.MuscleSelector
import com.ironvellum.app.ui.theme.IronvellumColors
import com.ironvellum.app.ui.train.UnderlineField
import kotlinx.coroutines.launch

/**
 * The exercise form, as a full-screen sheet over whatever picker opened it: Name, Type, Logged as, Needs and the
 * muscles tapped on the figure. [editing] null makes a new movement (with [prefillName] typed in); otherwise it
 * edits that one of the lifter's own, and offers Delete when nothing uses it.
 *
 * The name is checked live against [existingNames] (the catalogue and the lifter's own) and again by the
 * Repository inside the save, so a duplicate never lands even if two sheets race. Weighted is never asked: it
 * follows from the gear ([CustomExercises.isWeighted]).
 */
@Composable
internal fun CustomExerciseSheet(
    editing: Exercise?,
    prefillName: String,
    existingNames: List<String>,
    onSave: suspend (CustomSpec) -> CustomSave,
    onDelete: suspend () -> Boolean,
    usage: suspend (Long) -> Int,
    onSaved: (Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(editing?.name ?: CustomExercises.cleanName(prefillName).take(CustomExercises.MAX_NAME)) }
    var typeName by rememberSaveable { mutableStateOf(editing?.let { CustomType.of(it.muscleGroup)?.name }) }
    var loggedName by rememberSaveable { mutableStateOf((editing?.let { CustomLogged.of(it.metric) } ?: CustomLogged.REPS).name) }
    var needsText by rememberSaveable { mutableStateOf(CustomExercises.encodeNeeds(editing?.needs.orEmpty())) }
    var sharesText by rememberSaveable { mutableStateOf(CustomExercises.encodeShares(editing?.shares.orEmpty())) }
    var rejected by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val type = typeName?.let { n -> CustomType.entries.firstOrNull { it.name == n } }
    val logged = CustomLogged.entries.firstOrNull { it.name == loggedName } ?: CustomLogged.REPS
    val needs = CustomExercises.decodeNeeds(needsText)
    val shares = CustomExercises.decodeShares(sharesText)
    val problem = remember(name, existingNames, editing) {
        CustomExercises.nameProblem(name, existingNames, own = editing?.name)
    }
    // Blank is not an error to shout about; it just keeps Save off.
    val shownProblem = if (name.isBlank()) null else problem ?: rejected
    val canSave = problem == null && type != null && !saving
    val usedBy by produceState<Int?>(initialValue = null, editing?.id) {
        value = if (editing != null) usage(editing.id) else 0
    }

    fun save() {
        val chosen = type ?: return
        saving = true
        rejected = null
        scope.launch {
            val spec = CustomSpec(
                name = name,
                type = chosen,
                logged = logged,
                needs = needs,
                shares = CustomExercises.shares(
                    main = shares.filterValues { it >= CustomExercises.MAIN_SHARE }.keys.toList(),
                    assist = shares.filterValues { it < CustomExercises.MAIN_SHARE }.keys.toList(),
                ),
            )
            when (val result = onSave(spec)) {
                is CustomSave.Saved -> onSaved(result.exercise)
                is CustomSave.Rejected -> {
                    rejected = result.message
                    saving = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(IronvellumColors.VaultHigh)
                .statusBarsPadding()
                .imePadding(),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                PushedHeader(
                    stringResource(if (editing == null) R.string.customex_title_new else R.string.customex_title_edit),
                    onDismiss,
                    Modifier.padding(top = 8.dp),
                )
                UnderlineField(
                    label = stringResource(R.string.customex_name_label),
                    value = name,
                    onValueChange = { name = it.take(CustomExercises.MAX_NAME); rejected = null },
                    counter = "${name.length} / ${CustomExercises.MAX_NAME}",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                shownProblem?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = IronvellumColors.DangerRed,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                FilterGroup(stringResource(R.string.customex_type_label)) {
                    CustomType.entries.forEach { t ->
                        FilterChip(t.label, type == t) { typeName = t.name }
                    }
                }
                FilterGroup(stringResource(R.string.customex_logged_label)) {
                    CustomLogged.entries.forEach { l ->
                        FilterChip(l.label, logged == l) { loggedName = l.name }
                    }
                }
                Hint(logged.hint)

                FilterGroup(stringResource(R.string.customex_needs_label)) {
                    CustomNeed.entries.forEach { n ->
                        val on = if (n == CustomNeed.NOTHING) needs.isEmpty() else n in needs
                        FilterChip(n.label, on) {
                            needsText = CustomExercises.encodeNeeds(
                                when {
                                    n == CustomNeed.NOTHING -> emptySet()
                                    n in needs -> needs - n
                                    else -> needs + n
                                },
                            )
                        }
                    }
                }
                Hint(UiText.themed(R.string.customex_needs_note, R.string.customex_needs_note__t).asString())

                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.customex_muscles_label), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
                Spacer(Modifier.height(4.dp))
                MuscleSelector(shares, onChange = { sharesText = CustomExercises.encodeShares(it) })

                if (editing != null) {
                    Spacer(Modifier.height(16.dp))
                    InkDivider()
                    val used = usedBy
                    when {
                        used == null -> Unit
                        used > 0 -> Text(
                            UiText.themedPlural(R.plurals.customex_used_by, R.plurals.customex_used_by__t, used).asString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = IronvellumColors.InkMuted,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                        else -> Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable(role = Role.Button) { confirmDelete = true }
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = IronvellumColors.DangerRed,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(stringResource(R.string.customex_delete_button), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.DangerRed)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            DockedActionBar(
                primary = stringResource(if (editing == null) R.string.customex_save_new_button else R.string.customex_save_edit_button),
                onPrimary = ::save,
                primaryEnabled = canSave,
                reserveLink = false,
            )
        }
    }

    if (confirmDelete) {
        IronvellumDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.customex_delete_title)) },
            text = { Text(stringResource(R.string.customex_delete_body, name.ifBlank { stringResource(R.string.customex_delete_this_exercise) })) },
            confirmButton = {
                IronvellumButton(
                    label = stringResource(R.string.customex_delete_button),
                    onClick = {
                        confirmDelete = false
                        scope.launch {
                            if (onDelete()) onDismiss() else rejected = Words.text(UiText.res(R.string.customex_error_used_now))
                        }
                    },
                    danger = true,
                )
            },
            dismissButton = {
                IronvellumButton(label = stringResource(R.string.customex_keep_button), onClick = { confirmDelete = false }, quiet = true)
            },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
}
