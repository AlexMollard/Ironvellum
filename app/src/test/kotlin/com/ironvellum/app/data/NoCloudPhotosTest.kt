package com.ironvellum.app.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Progress photos stay on the phone and in the lifter's own backup folder. This pins the promise in source: no file
 * of the cloud client, the web snapshot or the Supabase project (schema, functions) may name a photo, the photo
 * classes, or the photo table. (The cloud copy of the archive is pinned separately in ProgressPhotosArchiveTest: it
 * carries no photo rows.) A hit here means someone wired photos towards a server: stop and ask the owner.
 */
class NoCloudPhotosTest {
    private val main = File("src/main/kotlin/com/ironvellum/app")
    private val supabase = File("../supabase")

    private val forbidden = Regex("""photo|jpeg|jpg|progress_photos|PhotoStore|PhotoLibrary|PhotoBackup""", RegexOption.IGNORE_CASE)

    private fun cloudSources(): List<File> =
        File(main, "data/cloud").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() +
            main.resolve("domain").listFiles { f -> f.name.startsWith("WebSnapshot") }!!.toList() +
            (if (supabase.isDirectory) supabase.walkTopDown().filter { it.isFile && it.extension in setOf("sql", "ts", "toml", "json") }.toList() else emptyList())

    @Test fun theScanSeesTheCloudCode() {
        val files = cloudSources()
        assertTrue("expected the cloud client and the snapshot builder, found ${files.size} files", files.size >= 8)
        assertTrue(files.any { it.name == "CloudSync.kt" })
        assertTrue(files.any { it.name.startsWith("WebSnapshotBuilder") })
    }

    @Test fun noCloudOrSnapshotCodeNamesAPhoto() {
        val hits = cloudSources().flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line -> if (forbidden.containsMatchIn(line)) "${file.path}:${i + 1}: ${line.trim()}" else null }
        }
        assertTrue("cloud or snapshot code references photos:\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test fun theCloudClientNeverReadsThePhotoApiOfTheRepository() {
        // Repository exposes photos through one property; the cloud client must not touch it.
        val hits = File(main, "data/cloud").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { Regex("""photoLibrary|\.photos\b""").containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertTrue("cloud files reading the photo library: $hits", hits.isEmpty())
    }
}
