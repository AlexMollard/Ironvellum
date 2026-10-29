package com.ironvellum.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import com.ironvellum.app.domain.PhotoRules
import java.io.File
import java.io.InputStream

/**
 * Where progress photos live: one folder of JPEGs in app-private storage (`filesDir/photos`), named by
 * [PhotoRules.workoutFileName] and [PhotoRules.poseFileName]. Nothing here is ever sent to a server: the only copies
 * that leave the phone are the ones the lifter's own backup folder receives ([PhotoBackup]). Android's own cloud backup
 * and device transfer leave this folder out (backup_rules.xml, data_extraction_rules.xml).
 *
 * A name that is not one this app writes is refused everywhere, so a restore or a folder copy can never reach outside
 * the folder.
 */
class PhotoStore(private val dir: File) {
    constructor(context: Context) : this(File(context.filesDir, DIR))

    /** The file for [name], or null when [name] is not one of ours. */
    fun file(name: String): File? = if (PhotoRules.isOurName(name)) File(dir, name) else null

    fun exists(name: String): Boolean = file(name)?.isFile == true

    fun delete(name: String) {
        file(name)?.delete()
    }

    fun names(): Set<String> = dir.listFiles()?.filter { it.isFile }?.map { it.name }?.filter(PhotoRules::isOurName)?.toSet().orEmpty()

    /** Deletes every photo file of ours that is not in [keep]: what a deleted workout or a replaced restore leaves behind. */
    fun sweep(keep: Set<String>): Int = (names() - keep).onEach(::delete).size

    /**
     * Reads the picture at [source], scales it so its long edge is at most [PhotoRules.MAX_EDGE], applies its EXIF
     * orientation and writes it as [name] at [PhotoRules.JPEG_QUALITY]. Re-encoding keeps pixels and nothing else, so the
     * EXIF block (and the GPS position in it) is gone. The picture is decoded at the target size, never at full size:
     * a 200 MP frame would not fit in memory. Written to a temporary file first, so a failure leaves no half photo.
     */
    fun importFrom(context: Context, source: Uri, name: String) {
        val target = requireNotNull(file(name)) { "not a photo name: $name" }
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, source)) { decoder, info, _ ->
            val size = PhotoRules.targetSize(info.size.width, info.size.height)
            decoder.setTargetSize(size.first, size.second)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            writeAtomically(target) { out -> check(decoded.compress(Bitmap.CompressFormat.JPEG, PhotoRules.JPEG_QUALITY, out)) { "could not encode the photo" } }
        } finally {
            decoded.recycle()
        }
    }

    /**
     * Copies a photo this app wrote earlier (a backup folder's copy) in as [name]. The bytes must be a readable
     * picture of a sane size; anything else is refused and nothing is written.
     */
    fun copyIn(name: String, input: InputStream) {
        val target = requireNotNull(file(name)) { "not a photo name: $name" }
        // At most one byte past the limit is read: enough to tell, never enough to fill memory from a hostile file.
        val bytes = input.use { it.readBytesUpTo(PhotoRules.MAX_FILE_BYTES + 1) }
        require(bytes.size <= PhotoRules.MAX_FILE_BYTES) { "photo is too large" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not a picture" }
        writeAtomically(target) { it.write(bytes) }
    }

    private fun InputStream.readBytesUpTo(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun writeAtomically(target: File, write: (java.io.OutputStream) -> Unit) {
        dir.mkdirs()
        val temp = File(dir, target.name + ".part")
        try {
            temp.outputStream().use(write)
            check(temp.renameTo(target)) { "could not keep the photo" }
        } finally {
            temp.delete()
        }
    }

    companion object {
        const val DIR = "photos"
    }
}
