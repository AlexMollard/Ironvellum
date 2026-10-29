package com.ironvellum.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironvellum.app.domain.PhotoKind
import com.ironvellum.app.domain.Pose
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Progress photos through Room and the file store: one workout photo a day, the rows that ride in the archive and the
 * pictures that do not, a deleted workout taking its picture with it, and what an import does to the photos of an
 * archive that has no photos section. Pictures are generated colour fields, never real ones.
 */
@RunWith(AndroidJUnit4::class)
class ProgressPhotosTest {

    private lateinit var context: Context
    private lateinit var db: IronvellumDatabase
    private lateinit var repo: Repository
    private lateinit var store: PhotoStore
    private lateinit var dir: File
    private val library get() = repo.photoLibrary

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        SeededDb.fresh(context, TEST_DB)
        db = IronvellumDatabase.create(context, TEST_DB)
        dir = File(context.cacheDir, "photos-test-" + UUID.randomUUID())
        store = PhotoStore(File(dir, "photos"))
        repo = Repository(db, photoStore = store)
        repo.ensureSeeded()
        repo.applyStarterTemplate()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
        dir.deleteRecursively()
    }

    /** A solid-colour JPEG [width] x [height] with an EXIF rotation and a GPS position, so the import has something to strip. */
    private fun testPicture(width: Int, height: Int, rotation: Int = ExifInterface.ORIENTATION_NORMAL, colour: Int = Color.RED): Uri {
        dir.mkdirs()
        val file = File(dir, "src-" + UUID.randomUUID() + ".jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(colour) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, rotation.toString())
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "48/1,51/1,30/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "2/1,17/1,40/1")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
            saveAttributes()
        }
        assertNotNull("the fixture carries a GPS position", ExifInterface(file.path).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        return Uri.fromFile(file)
    }

    private suspend fun completedSession(): Long {
        val preset = db.presetDao().observePresets().first().first().preset
        val id = repo.startSessionFromPreset(preset.id)
        val set = db.sessionDao().setsFor(id).first()
        repo.updateSet(set.id, reps = 8, weightKg = 20.0, done = true)
        repo.completeSession(id)
        return id
    }

    private fun bounds(file: File) = BitmapFactory.Options().also { it.inJustDecodeBounds = true; BitmapFactory.decodeFile(file.path, it) }

    @Test
    fun importScalesToTheLongEdgeAppliesRotationAndDropsTheGpsPosition() = runBlocking {
        val id = completedSession()
        // 3000x1500 stored landscape with a quarter turn: it is a portrait picture, 1500 wide and 3000 tall.
        library.saveWorkout(context, testPicture(3000, 1500, ExifInterface.ORIENTATION_ROTATE_90), id, day = 20_000)
        val photo = library.photos.first().single()
        val file = store.file(photo.fileName)!!
        val b = bounds(file)
        assertEquals("the long edge is 2048", 2048, maxOf(b.outWidth, b.outHeight))
        assertTrue("upright: taller than wide (was ${b.outWidth}x${b.outHeight})", b.outHeight > b.outWidth)
        val exif = ExifInterface(file.path)
        assertNull("no GPS position survives", exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertEquals("a JPEG", listOf(0xFF.toByte(), 0xD8.toByte()), file.readBytes().take(2))
    }

    @Test
    fun aSmallPictureIsNotEnlarged() = runBlocking {
        val id = completedSession()
        library.saveWorkout(context, testPicture(800, 600), id, day = 20_000)
        val b = bounds(store.file(library.photos.first().single().fileName)!!)
        assertEquals(800 to 600, b.outWidth to b.outHeight)
    }

    @Test
    fun aSecondPhotoOnTheSameDayReplacesTheFirstAndItsFileGoes() = runBlocking {
        val id = completedSession()
        library.saveWorkout(context, testPicture(400, 300), id, day = 20_000)
        val first = library.photos.first().single().fileName
        library.saveWorkout(context, testPicture(400, 300, colour = Color.BLUE), id, day = 20_000)
        val rows = library.photos.first()
        assertEquals(1, rows.size)
        assertFalse("the replaced file is deleted", store.exists(first))
        assertEquals(setOf(rows.single().fileName), store.names())
    }

    @Test
    fun anotherDayKeepsItsOwnPhotoAndAPoseIsAPerWeekPerPoseSlot() = runBlocking {
        val id = completedSession()
        library.saveWorkout(context, testPicture(200, 200), id, day = 20_000)
        library.saveWorkout(context, testPicture(200, 200), id, day = 20_001)
        library.savePose(context, testPicture(200, 200), week = 19_996, pose = Pose.FRONT)
        library.savePose(context, testPicture(200, 200), week = 19_996, pose = Pose.SIDE)
        library.savePose(context, testPicture(200, 200), week = 19_996, pose = Pose.FRONT)
        val rows = library.photos.first()
        assertEquals(4, rows.size)
        assertEquals(2, rows.count { it.kind == PhotoKind.POSE })
        assertEquals(4, store.names().size)
    }

    @Test
    fun deletingASinglePhotoRemovesItsRowAndFile() = runBlocking {
        val id = completedSession()
        library.saveWorkout(context, testPicture(200, 200), id, day = 20_000)
        val photo = library.photos.first().single()
        library.delete(photo.id)
        assertTrue(library.photos.first().isEmpty())
        assertTrue(store.names().isEmpty())
    }

    @Test
    fun deletingAWorkoutDeletesItsPhotoRowAndFile() = runBlocking {
        val id = completedSession()
        library.saveWorkout(context, testPicture(200, 200), id, day = 20_000)
        library.savePose(context, testPicture(200, 200), week = 19_996, pose = Pose.BACK)
        repo.deleteWorkout(id)
        val rows = library.photos.first()
        assertEquals("only the pose is left", listOf(Pose.BACK), rows.map { it.pose })
        assertEquals(setOf(rows.single().fileName), store.names())
    }

    @Test
    fun theArchiveCarriesRowsNotPicturesAndTheCloudCopyCarriesNothing() = runBlocking {
        val id = completedSession()
        library.saveWorkout(context, testPicture(300, 200), id, day = 20_000)
        library.savePose(context, testPicture(300, 200), week = 19_996, pose = Pose.FRONT)
        val local = repo.exportJson()
        assertTrue(local.contains("\"photos\":["))
        assertEquals(2, Regex("\\.jpg").findAll(local).count())
        assertFalse("no picture bytes in the archive", local.contains("/9j/"))
        val cloud = repo.exportArchive(includeDeviceOnly = false).json
        assertFalse(cloud.contains("photos"))
        assertFalse(cloud.contains(".jpg"))
    }

    @Test
    fun restoringAnArchiveBringsBackTheRowsAndRelinksWorkoutPhotosToTheirWorkout() = runBlocking {
        val id = completedSession()
        val startedAt = db.sessionDao().byId(id)!!.startedAtMs
        library.saveWorkout(context, testPicture(300, 200), id, day = 20_000)
        library.savePose(context, testPicture(300, 200), week = 19_996, pose = Pose.SIDE)
        val json = repo.exportJson()
        val names = store.names()

        repo.importArchive(json).getOrThrow()

        val rows = library.photos.first()
        assertEquals(2, rows.size)
        val workout = rows.single { it.kind == PhotoKind.WORKOUT }
        assertEquals("the photo is attached to the restored workout", startedAt, db.sessionDao().byId(workout.sessionId!!)!!.startedAtMs)
        assertEquals("pictures already here are kept", names, store.names())
        assertTrue(rows.all { library.hasFile(it) })
    }

    @Test
    fun restoringOnAFreshPhoneKeepsTheRowsAndAsksForThePicturesFromTheBackupFolder() = runBlocking {
        val id = completedSession()
        library.saveWorkout(context, testPicture(300, 200), id, day = 20_000)
        val json = repo.exportJson()
        val name = library.photos.first().single().fileName
        store.delete(name) // the new phone has no pictures yet

        repo.importArchive(json).getOrThrow()

        assertEquals(setOf(name), library.missingFiles())
        // The backup folder's copy: a file named as the row names it comes back.
        val source = File(dir, "folder-copy.jpg")
        source.outputStream().use { Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, it) }
        store.copyIn(name, source.inputStream())
        assertTrue(library.missingFiles().isEmpty())
    }

    @Test
    fun anOldArchiveWithoutPhotosRestoresAndKeepsThisDevicesPhotos() = runBlocking {
        val id = completedSession()
        val startedAt = db.sessionDao().byId(id)!!.startedAtMs
        library.saveWorkout(context, testPicture(300, 200), id, day = 20_000)
        library.savePose(context, testPicture(300, 200), week = 19_996, pose = Pose.FRONT)
        val old = repo.exportJson()
            .replace(Regex(",\"photos\":\\[.*?\\](?=,\"exercises\")"), "")
            .replace("\"formatVersion\":14", "\"formatVersion\":13")
        assertFalse(old.contains("\"photos\""))

        repo.importArchive(old).getOrThrow()

        val rows = library.photos.first()
        assertEquals("both photos survive", 2, rows.size)
        assertEquals(startedAt, db.sessionDao().byId(rows.single { it.kind == PhotoKind.WORKOUT }.sessionId!!)!!.startedAtMs)
        assertEquals(rows.map { it.fileName }.toSet(), store.names())
    }

    @Test
    fun anArchiveWithAnEmptyPhotosSectionClearsThePhotosAndSweepsTheirFiles() = runBlocking {
        val id = completedSession()
        library.saveWorkout(context, testPicture(300, 200), id, day = 20_000)
        library.savePose(context, testPicture(300, 200), week = 19_996, pose = Pose.FRONT)
        val emptied = repo.exportJson().replace(Regex("\"photos\":\\[.*?\\](?=,\"exercises\")"), "\"photos\":[]")

        repo.importArchive(emptied).getOrThrow()

        assertTrue(library.photos.first().isEmpty())
        assertTrue("no orphan pictures are left behind", store.names().isEmpty())
    }

    @Test
    fun aRestoreCannotWriteOutsideThePhotoFolder() {
        assertNull(store.file("../../databases/ironvellum.db"))
        assertNotNull(store.file("w-1-abc.jpg"))
        val failed = runCatching { store.copyIn("../x.jpg", "x".byteInputStream()) }
        assertTrue(failed.isFailure)
    }

    private companion object {
        const val TEST_DB = "progress-photos-test.db"
    }
}
