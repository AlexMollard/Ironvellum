package com.ironvellum.app.ui.train

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.ui.asString
import com.ironvellum.app.ui.components.InkPanel
import com.ironvellum.app.ui.components.PanelLabel
import com.ironvellum.app.ui.components.PhotoActions
import com.ironvellum.app.ui.components.PhotoFrame
import com.ironvellum.app.ui.components.PhotoRemoveDialog
import com.ironvellum.app.ui.components.rememberPhotoSource
import com.ironvellum.app.ui.theme.IronvellumColors
import java.io.File

/**
 * The day's photo on a workout's detail: the one picture kept for that day (a second workout the same day shows the same
 * one), with take, choose and remove. With no photo it is the quiet "Add a photo" card; the photo can be replaced here
 * even when the day's step at the end of the workout was not offered.
 */
@Composable
internal fun WorkoutPhotoCard(
    file: File?,
    dateText: String,
    saving: Boolean,
    failed: Boolean,
    onPicked: (Uri) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = rememberPhotoSource(onPicked)
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    InkPanel(modifier.fillMaxWidth()) {
        PanelLabel(stringResource(R.string.workoutdetail_photo_label))
        if (file != null) {
            PhotoFrame(
                file,
                stringResource(R.string.workoutdetail_photo_description, dateText),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Column(Modifier.padding(top = 8.dp)) {
            Text(
                UiText.themed(R.string.workoutdetail_photo_caption, R.string.workoutdetail_photo_caption__t).asString(),
                style = MaterialTheme.typography.bodySmall,
                color = IronvellumColors.InkMuted,
            )
            if (failed) {
                Text(stringResource(R.string.photo_offer_failed), style = MaterialTheme.typography.bodySmall, color = IronvellumColors.DangerRed, modifier = Modifier.padding(top = 4.dp))
            }
            PhotoActions(
                source = source,
                kept = file != null,
                enabled = !saving,
                onRemove = { confirmRemove = true },
                modifier = Modifier.padding(top = 4.dp),
                takeLabel = stringResource(if (file != null) R.string.photo_retake_button else R.string.photo_take_button),
                pickLabel = stringResource(if (file != null) R.string.photo_repick_button else R.string.photo_pick_button),
            )
        }
    }
    if (confirmRemove) {
        PhotoRemoveDialog(onRemove = { confirmRemove = false; onRemove() }, onKeep = { confirmRemove = false })
    }
}
