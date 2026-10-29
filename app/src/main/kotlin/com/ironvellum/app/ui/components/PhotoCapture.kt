package com.ironvellum.app.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.FileProvider
import com.ironvellum.app.ui.theme.IronvellumColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * How a screen gets a photo: [take] opens the camera app (when [canTake]), [pick] opens the system photo picker. Neither
 * needs a permission: the camera app writes into one empty file this app hands it, and the picker returns only the one
 * picture the lifter chose. [onPicked] gets a content or file URI to read once.
 */
internal class PhotoSource(val canTake: Boolean, val take: () -> Unit, val pick: () -> Unit)

@Composable
internal fun rememberPhotoSource(onPicked: (Uri) -> Unit): PhotoSource {
    val context = LocalContext.current
    val picked by rememberUpdatedState(onPicked)
    // The camera app can take the process with it while it is in front: the file it was handed outlives that.
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) picked(uri) }
    val takeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val path = capturePath
        capturePath = null
        if (taken && path != null) picked(PhotoCapture.uriFor(context, File(path)))
    }
    val canTake = remember { PhotoCapture.cameraAvailable(context) }
    return remember(canTake, pickLauncher, takeLauncher) {
        PhotoSource(
            canTake = canTake,
            take = {
                // A camera app that is gone between the check and the tap must not crash the finish page.
                runCatching {
                    val target = PhotoCapture.newTarget(context)
                    capturePath = target.path
                    takeLauncher.launch(PhotoCapture.uriFor(context, target))
                }.onFailure { capturePath = null }
            },
            pick = { runCatching { pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
        )
    }
}

internal object PhotoCapture {
    private const val DIR = "photo-capture"

    fun cameraAvailable(context: Context): Boolean =
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null

    /** A fresh, empty file in the shared capture folder; anything an earlier capture left there is cleared first. */
    fun newTarget(context: Context): File {
        val dir = File(context.cacheDir, DIR)
        dir.deleteRecursively()
        dir.mkdirs()
        return File(dir, "capture.jpg").also { it.createNewFile() }
    }

    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
}

/**
 * A kept photo, decoded at about [maxEdge] pixels (never at full size) off the main thread. [file] null, or not yet
 * decoded, draws a plain field so the layout does not jump.
 */
@Composable
internal fun PhotoImage(
    file: File?,
    description: String,
    modifier: Modifier = Modifier,
    maxEdge: Int = 1024,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file?.path, file?.lastModified()) {
        value = if (file == null) null else withContext(Dispatchers.IO) { decodeScaled(file, maxEdge) }
    }
    Box(modifier.background(IronvellumColors.VaultHigh).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = contentScale, modifier = Modifier.matchParentSize()) }
    }
}

private fun decodeScaled(file: File, maxEdge: Int): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
