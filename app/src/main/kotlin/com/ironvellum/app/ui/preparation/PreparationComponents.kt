package com.ironvellum.app.ui.preparation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.domain.*
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.*
import com.ironvellum.app.ui.program.ExerciseInspection
import com.ironvellum.app.ui.theme.IronvellumColors

@Composable internal fun preparationText(plain: Int, themed: Int, vararg args: Any) = UiText.themed(plain, themed, *args).asString()
@Composable internal fun preparationCount(plain: Int, themed: Int, count: Int, vararg remaining: Any) =
    UiText.themedPlural(plain, themed, count, count, *remaining).asString()
@Composable internal fun kindLabel(kind: PreparationKind): String = preparationText(
    if (kind == PreparationKind.WARMUP) R.string.preparation_warmup else R.string.preparation_cooldown,
    if (kind == PreparationKind.WARMUP) R.string.preparation_warmup__t else R.string.preparation_cooldown__t,
)

/** The pushed header, scrollable ink content and thumb-reachable action shared by this flow. */
@Composable internal fun PreparationFrame(
    title: String, onBack: () -> Unit, primary: String? = null, onPrimary: () -> Unit = {},
    enabled: Boolean = true, content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().imePadding()) {
        Spacer(Modifier.height(8.dp))
        PushedHeader(title, onBack, Modifier.padding(horizontal = 16.dp), titleMaxLines = Int.MAX_VALUE)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            content()
            Spacer(Modifier.height(24.dp))
        }
        if (primary != null) DockedActionBar(primary, onPrimary, primaryEnabled = enabled, reserveLink = false)
    }
}

@Composable internal fun PreparationCaption(text: String, error: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = if (error) IronvellumColors.DangerRed else IronvellumColors.InkMuted, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable internal fun preparationTarget(step: PreparationStep): String {
    val target = if (step.measure == PreparationMeasure.REPS) preparationCount(R.plurals.preparation_target_reps, R.plurals.preparation_target_reps__t, step.target)
        else preparationCount(R.plurals.preparation_target_seconds, R.plurals.preparation_target_seconds__t, step.target)
    val repeated = when (step.repeat) {
        PreparationRepeat.ONCE -> target
        PreparationRepeat.SIDES -> preparationText(R.string.preparation_each_side, R.string.preparation_each_side__t, target)
        PreparationRepeat.DIRECTIONS -> preparationText(R.string.preparation_each_direction, R.string.preparation_each_direction__t, target)
    }
    return if (step.rounds == 1) repeated else preparationCount(R.plurals.preparation_target_rounds, R.plurals.preparation_target_rounds__t, step.rounds, repeated)
}

@Composable internal fun PreparationPreview(snapshot: PreparationSnapshot, exercises: List<Exercise>) {
    var inspecting by remember { mutableStateOf<String?>(null) }
    Text(snapshot.name, style = MaterialTheme.typography.titleMedium, color = IronvellumColors.Ink, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
    PreparationCaption(preparationCount(R.plurals.preparation_exercise_count, R.plurals.preparation_exercise_count__t, snapshot.steps.size))
    snapshot.steps.forEach { step ->
        ListRow(step.exerciseName, subline = preparationTarget(step), onClickLabel = stringResource(R.string.session_info_description, step.exerciseName), onClick = { inspecting = step.exerciseName })
        if (step.note.isNotBlank()) PreparationCaption(step.note)
        InkDivider()
    }
    inspecting?.let { name -> ExerciseInspection(exercises, name, "") { inspecting = null } }
}

@Composable internal fun PreparationConfirm(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    IronvellumDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) },
        confirmButton = { IronvellumButton(confirm, onConfirm, danger = danger) },
        dismissButton = { IronvellumButton(preparationText(R.string.preparation_keep_editing, R.string.preparation_keep_editing__t), onDismiss, quiet = true) },
    )
}
