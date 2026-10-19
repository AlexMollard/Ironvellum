package com.ironvellum.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironvellum.app.R
import com.ironvellum.app.data.Words
import com.ironvellum.app.domain.BackupFiles
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkRowPanel
import com.ironvellum.app.ui.components.InkSpinner
import com.ironvellum.app.ui.components.ListRow
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.theme.IronvellumColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.ironvellum.app.ui.asString

/**
 * The weekly file backup in Data settings: off until a folder is picked, then its
 * last result, "Back up now", a way to change the folder and a way to turn it off.
 * A lost folder permission or a failed write is shown here in red, never dropped.
 */
@Composable
internal fun AutoBackupPanel(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val state by remember { viewModel.backupState(context) }.collectAsStateWithLifecycle()
    val busy by viewModel.backingUp.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.chooseBackupFolder(context, uri)
    }

    InkRowPanel(Modifier.fillMaxWidth()) {
        if (!state.on) {
            ListRow(
                stringResource(R.string.settings_backup_title),
                value = stringResource(R.string.common_off),
                subline = stringResource(R.string.settings_backup_off_caption),
                onClick = { picker.launch(null) },
            )
        } else {
            ListRow(
                stringResource(R.string.settings_backup_title),
                value = stringResource(R.string.settings_backup_weekly),
                subline = stringResource(
                    R.string.settings_backup_folder_caption,
                    state.folderName.orEmpty(),
                    BackupFiles.KEEP,
                ),
            )
            InkDivider()
            ListRow(stringResource(R.string.settings_backup_last_label), value = lastBackupText(state.lastAtMs, state.error != null))
            InkDivider()
            if (busy) {
                InkSpinner(Modifier.padding(16.dp))
            } else {
                ListRow(stringResource(R.string.settings_backup_now_button), onClick = { viewModel.backUpNow(context) })
            }
            InkDivider()
            ListRow(stringResource(R.string.settings_backup_change_folder_button), onClick = { picker.launch(null) })
            InkDivider()
            ListRow(
                stringResource(R.string.settings_backup_stop_button),
                subline = stringResource(R.string.settings_backup_stop_caption),
                onClick = { viewModel.stopBackups(context) },
            )
        }
    }
    state.error?.let {
        Spacer(Modifier.height(8.dp))
        SettingsCaption(it.asString(), color = IronvellumColors.DangerRed)
    }
}

private val LAST_BACKUP_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

/** When the last good backup finished; before the first, "Failed" if an attempt failed, else "Not yet". */
internal fun lastBackupText(lastAtMs: Long, failedSince: Boolean): String {
    if (lastAtMs == 0L) return Words.text(UiText.res(if (failedSince) R.string.settings_backup_last_failed else R.string.settings_backup_last_never))
    return LAST_BACKUP_FORMAT.format(Instant.ofEpochMilli(lastAtMs).atZone(ZoneId.systemDefault()))
}
