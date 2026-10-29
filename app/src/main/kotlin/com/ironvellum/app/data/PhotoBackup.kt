package com.ironvellum.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.ironvellum.app.domain.PhotoRules

/**
 * The progress photos' copy in the lifter's own backup folder: a sub-folder [FOLDER] beside the weekly archives, one
 * file per photo. Photos are copied once and never rewritten, so each weekly run only adds the new ones (the archive
 * keeps the newest five, the photos are not pruned with them: an older archive may still name them).
 *
 * The folder is the lifter's, picked through the Storage Access Framework; nothing here talks to a network.
 */
object PhotoBackup {
    const val FOLDER = "ironvellum_photos"

    private const val IMAGE = "image/jpeg"

    /** Copies the [names] that [store] holds and the folder lacks; returns how many were copied. Throws after trying them all if any could not be. */
    fun mirror(context: Context, tree: Uri, store: PhotoStore, names: Collection<String>): Int {
        val resolver = context.contentResolver
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val folder = children(context, tree, DocumentsContract.getTreeDocumentId(tree))[FOLDER]?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it) }
            ?: DocumentsContract.createDocument(resolver, root, DocumentsContract.Document.MIME_TYPE_DIR, FOLDER)
            ?: error("the folder refused a photos folder")
        val have = children(context, tree, DocumentsContract.getDocumentId(folder)).keys
        var copied = 0
        var failed = 0
        names.filter { it !in have && store.exists(it) }.forEach { name ->
            val file = store.file(name) ?: return@forEach
            val doc = runCatching { DocumentsContract.createDocument(resolver, folder, IMAGE, name) }.getOrNull()
            if (doc == null) { failed++; return@forEach }
            val ok = runCatching { resolver.openOutputStream(doc, "wt")!!.use { out -> file.inputStream().use { it.copyTo(out) } } }.isSuccess
            if (ok) copied++ else { failed++; runCatching { DocumentsContract.deleteDocument(resolver, doc) } }
        }
        check(failed == 0) { "$failed photos could not be copied" }
        return copied
    }

    /**
     * Copies the [wanted] photos found in [tree] (the backup folder, or the photos folder itself) into [store] and returns
     * how many came back. A file that is not a readable picture, or whose name is not ours, is skipped.
     */
    fun restore(context: Context, tree: Uri, store: PhotoStore, wanted: Set<String>): Int {
        if (wanted.isEmpty()) return 0
        val resolver = context.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val folderId = children(context, tree, rootId)[FOLDER] ?: rootId
        val byName = children(context, tree, folderId)
        var restored = 0
        wanted.filter(PhotoRules::isOurName).forEach { name ->
            val id = byName[name] ?: return@forEach
            val ok = runCatching {
                resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id))!!.let { store.copyIn(name, it) }
            }.isSuccess
            if (ok) restored++
        }
        return restored
    }

    /** The files and folders directly under [parentId], by display name. */
    private fun children(context: Context, tree: Uri, parentId: String): Map<String, String> {
        val out = HashMap<String, String>()
        context.contentResolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c -> while (c.moveToNext()) out[c.getString(1)] = c.getString(0) }
        return out
    }
}
