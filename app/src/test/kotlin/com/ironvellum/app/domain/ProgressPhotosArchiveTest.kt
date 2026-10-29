package com.ironvellum.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The archive carries photo rows (never pictures), only in the local copy, and an archive without them still restores. */
class ProgressPhotosArchiveTest {

    private val photos = listOf(
        PhotoSnapshot(PhotoKind.WORKOUT, 20_000, 1_000L, null, "w-20000-ab12cd34.jpg", 5_000),
        PhotoSnapshot(PhotoKind.POSE, 19_996, null, Pose.FRONT, "p-19996-front-ab12cd34.jpg", 6_000),
        PhotoSnapshot(PhotoKind.POSE, 19_996, null, Pose.BACK, "p-19996-back-ab12cd34.jpg", 6_100),
    )

    private fun write(photos: List<PhotoSnapshot>?, includeDeviceOnly: Boolean = true): String = ExportWriter.write(
        profile = PlayerProfile(name = "Test", totalXp = 0, currentTitleId = null),
        trainingMode = TrainingMode.STRENGTH,
        presets = emptyList(),
        sessions = listOf(WorkoutSession(id = 3, presetId = null, label = "Push", startedAtMs = 1_000, completedAtMs = 2_000) to emptyList()),
        stats = emptyList(),
        titles = emptyList(),
        skills = emptyList(),
        healthDays = emptyList(),
        exportedAtMs = 9,
        includeDeviceOnly = includeDeviceOnly,
        photos = photos,
    )

    @Test fun photoRowsRoundTripExactly() {
        val archive = ExportReader.read(write(photos)).getOrThrow()
        assertEquals(photos, archive.photos)
        assertEquals(ExportWriter.FORMAT_VERSION, archive.formatVersion)
    }

    @Test fun anEmptyListIsWrittenAsEmptyNotAbsent() {
        // Present-and-empty replaces the device's photos; absent keeps them. The two must not be confused.
        assertEquals(emptyList<PhotoSnapshot>(), ExportReader.read(write(emptyList())).getOrThrow().photos)
    }

    @Test fun anArchiveWithoutPhotosReadsAsAbsentSoARestoreKeepsTheDevicesOwn() {
        val old = write(null).replace("\"formatVersion\":${ExportWriter.FORMAT_VERSION}", "\"formatVersion\":13")
        val archive = ExportReader.read(old).getOrThrow()
        assertNull(archive.photos)
        assertEquals(13, archive.formatVersion)
        assertEquals(1, archive.sessions.size)
    }

    @Test fun theCloudCopyNeverCarriesPhotoRowsEvenWhenGivenSome() {
        val json = write(photos, includeDeviceOnly = false)
        assertFalse(json.contains("photos"))
        assertFalse(json.contains(".jpg"))
        assertNull(ExportReader.read(json).getOrThrow().photos)
    }

    @Test fun aRowThisBuildCannotReadIsDroppedNotFatal() {
        val json = write(photos).replace("\"kind\":\"POSE\"", "\"kind\":\"HOLOGRAM\"")
        val archive = ExportReader.read(json).getOrThrow()
        assertEquals(listOf(photos[0]), archive.photos)
    }

    @Test fun aNameThatIsNotOursIsDroppedSoARestoreCannotReachOutsideThePhotoFolder() {
        val json = write(photos).replace("p-19996-front-ab12cd34.jpg", "../../databases/ironvellum.db")
        assertEquals(listOf(photos[0], photos[2]), ExportReader.read(json).getOrThrow().photos)
        assertNotNull(ExportReader.read(json).getOrNull())
        assertTrue(ExportReader.read(json).getOrThrow().photos!!.all { PhotoRules.isOurName(it.fileName) })
    }
}
