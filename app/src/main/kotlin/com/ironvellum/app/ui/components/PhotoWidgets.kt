package com.ironvellum.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironvellum.app.R
import com.ironvellum.app.ui.theme.IronvellumColors
import java.io.File

/** The confirm every photo removal goes through: the file is deleted from this phone, the backup folder's copy is not touched. */
@Composable
internal fun PhotoRemoveDialog(onRemove: () -> Unit, onKeep: () -> Unit) {
    IronvellumDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.photo_remove_title)) },
        text = { Text(stringResource(R.string.photo_remove_body), style = MaterialTheme.typography.bodyMedium, color = IronvellumColors.InkMuted) },
        confirmButton = { IronvellumButton(label = stringResource(R.string.photo_remove_button), onClick = onRemove, danger = true) },
        dismissButton = { IronvellumButton(label = stringResource(R.string.photo_keep_button), onClick = onKeep, quiet = true) },
    )
}

/**
 * The actions under a photo: take one, choose one, and (with a photo kept) remove it. Each chip is a 48dp target. A
 * phone with no camera app shows only Choose.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhotoActions(
    source: PhotoSource,
    kept: Boolean,
    enabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    takeLabel: String = stringResource(R.string.photo_take_button),
    pickLabel: String = stringResource(R.string.photo_pick_button),
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (source.canTake && enabled) InkChip(takeLabel, icon = Icons.Outlined.PhotoCamera, height = 48.dp, onClick = source.take)
        if (enabled) InkChip(pickLabel, icon = Icons.Outlined.PhotoLibrary, height = 48.dp, onClick = source.pick)
        if (kept && enabled) InkChip(stringResource(R.string.photo_remove_button), stringResource(R.string.photo_remove_description), Icons.Outlined.Delete, height = 48.dp, onClick = onRemove)
    }
}

/** A photo in its slot: the picture at its own shape inside a rounded field, or the empty field with [empty] on it. */
@Composable
internal fun PhotoFrame(file: File?, description: String, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 240.dp, scale: ContentScale = ContentScale.Fit) {
    PhotoImage(
        file = file,
        description = description,
        modifier = modifier.fillMaxWidth().heightIn(min = height, max = height).clip(MaterialTheme.shapes.small),
        contentScale = scale,
    )
}
