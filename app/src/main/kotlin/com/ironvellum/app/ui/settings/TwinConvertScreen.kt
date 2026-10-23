package com.ironvellum.app.ui.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.data.AutoBackup
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.TwinConvertBackup
import com.ironvellum.app.domain.TwinConversion
import com.ironvellum.app.ui.components.DockedActionBar
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the convert screen shows besides the groups: running, done, or why it stopped. */
data class TwinConvertUi(
    val running: Boolean = false,
    val result: Repository.TwinConvertResult? = null,
    val error: String? = null,
)

/**
 * Holds the convert: the groups still to move ([TwinConversion]) and the one action. Settings and History each open
 * the same confirm screen from it, so both read one answer.
 */
class TwinConvertViewModel(private val repo: Repository, private val appContext: Context) : ViewModel() {

    val groups: StateFlow<List<TwinConversion.Group>> =
        repo.observeTwinMoves().map(TwinConversion::groups).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _ui = MutableStateFlow(TwinConvertUi())
    val ui: StateFlow<TwinConvertUi> = _ui.asStateFlow()

    /** Forgets a finished or failed run, so the screen opens on what is left to move. */
    fun reset() {
        if (!_ui.value.running) _ui.value = TwinConvertUi()
    }

    fun convert() {
        if (_ui.value.running) return
        _ui.value = TwinConvertUi(running = true)
        viewModelScope.launch {
            _ui.value = runCatching {
                repo.convertTwins(backup = { json ->
                    TwinConvertBackup.write(appContext, json)
                    // Also into the lifter's backup folder when there is one; the copy above already stands if it fails.
                    if (AutoBackup.state(appContext).value.on) runCatching { AutoBackup.run(appContext, repo) }
                })
            }.fold(
                onSuccess = { TwinConvertUi(result = it) },
                onFailure = { TwinConvertUi(error = it.message ?: it.javaClass.simpleName) },
            )
        }
    }
}

@Composable
fun rememberTwinConvertViewModel(): TwinConvertViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        key = "twin_convert",
        factory = viewModelFactory { initializer { TwinConvertViewModel(ironvellumRepository(), context) } },
    )
}

/** The confirm screen: each group as "from to to, N sets", then one button that backs up and moves them all. */
@Composable
fun TwinConvertOverlay(viewModel: TwinConvertViewModel, onDismiss: () -> Unit) {
    val groups = viewModel.groups.collectAsStateWithLifecycle().value
    val ui = viewModel.ui.collectAsStateWithLifecycle().value
    BackHandler(onBack = onDismiss)
    val moved = ui.result
    // After a run the live list is empty; show what was moved.
    val shown = moved?.groups ?: groups
    val totalSets = shown.sumOf { it.sets }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.weight(1f).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            PushedHeader(stringResource(R.string.twinconvert_title), onBack = onDismiss)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(if (shown.isEmpty()) R.string.twinconvert_empty else R.string.twinconvert_intro),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            if (shown.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                InkRowPanel {
                    shown.forEachIndexed { i, group ->
                        if (i > 0) InkDivider()
                        GroupRow(group)
                    }
                }
            }
            moved?.let {
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(
                        R.string.twinconvert_done,
                        pluralStringResource(R.plurals.common_sets_count, it.sets, it.sets),
                        pluralStringResource(R.plurals.common_days_count, it.sessions, it.sessions),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = IronvellumColors.SystemGreen,
                )
            }
            ui.error?.let {
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.twinconvert_failed, it), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.DangerRed)
            }
            if (moved == null && shown.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.twinconvert_note), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted)
            }
            if (ui.running) {
                Spacer(Modifier.height(14.dp))
                InkSpinner()
            }
            Spacer(Modifier.height(16.dp))
        }
        DockedActionBar(
            primary = when {
                ui.running -> stringResource(R.string.twinconvert_running)
                moved != null || shown.isEmpty() -> stringResource(R.string.common_done)
                else -> pluralStringResource(R.plurals.twinconvert_button, totalSets, totalSets)
            },
            onPrimary = if (moved != null || shown.isEmpty()) onDismiss else viewModel::convert,
            primaryEnabled = !ui.running,
            reserveLink = false,
        )
    }
}

@Composable
private fun GroupRow(group: TwinConversion.Group) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            stringResource(R.string.twinconvert_group, group.from, group.to),
            style = MaterialTheme.typography.bodyLarge,
            color = IronvellumColors.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(
                R.string.twinconvert_group_count,
                pluralStringResource(R.plurals.common_sets_count, group.sets, group.sets),
                pluralStringResource(R.plurals.common_days_count, group.sessions, group.sessions),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
    }
}

/** The one-time card on History: sets can move; Review opens the confirm screen, Not now closes it for good. */
@Composable
fun TwinConvertCard(sets: Int, onReview: () -> Unit, onDismiss: () -> Unit) {
    InkPanel(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.twinconvert_card_title), style = MaterialTheme.typography.titleSmall, color = IronvellumColors.Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            pluralStringResource(R.plurals.twinconvert_card_body, sets, sets),
            style = MaterialTheme.typography.bodySmall,
            color = IronvellumColors.InkMuted,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IronvellumButton(stringResource(R.string.twinconvert_card_review), onClick = onReview)
            IronvellumButton(stringResource(R.string.twinconvert_card_dismiss), quiet = true, onClick = onDismiss)
        }
    }
}
