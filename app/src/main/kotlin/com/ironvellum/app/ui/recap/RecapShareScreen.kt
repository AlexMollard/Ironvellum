package com.ironvellum.app.ui.recap

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.R
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.WeeklyRecap
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkDivider
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.IronvellumButton
import com.ironvellum.app.ui.components.PushedHeader
import com.ironvellum.app.ui.components.SettingsCaption
import com.ironvellum.app.ui.components.SettingsSwitchRow
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.settings.shareFile
import com.ironvellum.app.ui.theme.IronvellumColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val PREFS = "share_card"
private const val KEY_RECORDS = "recap_records"
private const val KEY_STREAK = "recap_streak"

/** Shows the card exactly as it will leave the phone, with the two parts that can be left off, then saves or shares it. */
@Composable
fun RecapShareScreen(
    weekEpochDay: Long,
    onBack: () -> Unit,
    viewModel: RecapViewModel = viewModel(
        key = "recap_share_$weekEpochDay",
        factory = viewModelFactory { initializer { RecapViewModel(ironvellumRepository(), LocalDate.ofEpochDay(weekEpochDay)) } },
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val recap = ui.recap
    if (recap == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    RecapShareContent(recap, onBack)
}

@Composable
internal fun RecapShareContent(recap: WeeklyRecap, onBack: () -> Unit) {
    ShareCardContent(
        title = stringResource(R.string.recap_share_title),
        cardKey = recap,
        saveName = stringResource(R.string.recap_share_save_name, recap.weekStart.toString()),
        chooserTitle = stringResource(R.string.recap_share_chooser_title),
        fileName = stringResource(R.string.recap_share_file_name),
        recordsLabel = stringResource(R.string.recap_share_records_label),
        recordsCaption = stringResource(R.string.recap_share_records_caption),
        streakCaption = stringResource(R.string.recap_share_streak_caption),
        onBack = onBack,
        render = { context, options, palette -> RecapCard.render(context, recap, options, palette) },
    )
}

/**
 * The share screen of any card: the preview exactly as it will leave the phone, the two parts that can be left off, and
 * Save or Share. [render] draws the card for the options and palette in force; [cardKey] redraws it when the data changes.
 */
@Composable
internal fun ShareCardContent(
    title: String,
    cardKey: Any,
    saveName: String,
    chooserTitle: String,
    fileName: String,
    recordsLabel: String,
    recordsCaption: String,
    streakCaption: String,
    onBack: () -> Unit,
    render: (Context, RecapCardOptions, RecapCardPalette) -> Bitmap,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RECORDS, true)) }
    var streak by remember { mutableStateOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_STREAK, true)) }
    var status by remember { mutableStateOf<UiText?>(null) }
    val options = RecapCardOptions(records = records, streak = streak)
    // The card follows the app's theme and accent as they are now, so a change of either redraws it.
    val palette = RecapCardPalette.current()

    val picturesFolder = stringResource(R.string.recap_share_pictures_folder)
    var card by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(cardKey, options, palette) {
        card = withContext(Dispatchers.Default) { render(context, options, palette) }
    }

    Column(Modifier.fillMaxSize()) {
        PushedHeader(
            title = title,
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 20.dp),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            card?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = stringResource(R.string.recap_share_card_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                )
            }
            InkPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                SettingsSwitchRow(
                    label = recordsLabel,
                    caption = recordsCaption,
                    checked = records,
                    onCheckedChange = {
                        records = it
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_RECORDS, it) }
                    },
                )
                InkDivider()
                SettingsSwitchRow(
                    label = UiText.themed(R.string.recap_share_streak_label, R.string.recap_share_streak_label__t).asString(),
                    caption = streakCaption,
                    checked = streak,
                    onCheckedChange = {
                        streak = it
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_STREAK, it) }
                    },
                )
                InkDivider()
                LockedRow(stringResource(R.string.recap_share_body_label))
                InkDivider()
                LockedRow(stringResource(R.string.recap_share_photos_label))
            }
            status?.let { Text(it.asString(), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.InkMuted) }
            Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IronvellumButton(
                label = stringResource(R.string.recap_share_save_button),
                quiet = true,
                enabled = card != null,
                onClick = {
                    val bitmap = card ?: return@IronvellumButton
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { saveToPictures(context, RecapCard.png(bitmap), saveName, picturesFolder) }
                        status = UiText.res(if (saved) R.string.recap_share_saved else R.string.recap_share_save_failed)
                    }
                },
                modifier = Modifier.weight(1f),
            )
            IronvellumButton(
                label = stringResource(R.string.recap_share_send_button),
                enabled = card != null,
                onClick = {
                    val bitmap = card ?: return@IronvellumButton
                    scope.launch {
                        shareFile(context, chooserTitle, fileName, RecapCard.png(bitmap), "image/png")
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A thing the card never carries, said so beside a lock: no switch, nothing to turn on. */
@Composable
private fun LockedRow(label: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 8.dp).semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.Ink)
            SettingsCaption(stringResource(R.string.recap_share_never))
        }
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = IronvellumColors.InkMuted, modifier = Modifier.size(18.dp))
    }
}

/**
 * Writes [bytes] to the phone's Pictures folder through MediaStore: no storage permission is needed to add a file there.
 * False when the store refused it.
 */
internal fun saveToPictures(context: Context, bytes: ByteArray, name: String, folder: String): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, folder)
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return runCatching {
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }.onFailure { resolver.delete(uri, null, null) }.isSuccess
}
