package com.ironvellum.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** What a progress photo is for: the one-a-day photo kept with a workout, or one pose of a weekly set. */
enum class PhotoKind { WORKOUT, POSE }

/** The three weekly poses, in the order they are shown and taken. */
enum class Pose(val key: String) {
    FRONT("front"),
    SIDE("side"),
    BACK("back"),
    ;

    companion object {
        fun fromKey(key: String): Pose? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One photo as the archive carries it: the row, never the picture. [day] is the epoch day the lifter's own zone put the
 * photo on when it was taken (a pose: the Monday of its week). A workout photo names its workout by [sessionStartedAtMs],
 * because a restore gives every workout a new id.
 */
data class PhotoSnapshot(
    val kind: PhotoKind,
    val day: Long,
    val sessionStartedAtMs: Long?,
    val pose: Pose?,
    val fileName: String,
    val takenAtMs: Long,
)

/** A kept photo as the screens see it. [sessionId] is the workout a WORKOUT photo is attached to; [pose] is set for a POSE. */
data class ProgressPhoto(
    val id: Long,
    val kind: PhotoKind,
    val day: Long,
    val sessionId: Long?,
    val pose: Pose?,
    val fileName: String,
    val takenAtMs: Long,
)

/** The rules of progress photos, kept pure so the JVM tests pin them. */
object PhotoRules {
    /** The day the pose prompt appears until the lifter picks another. */
    val DEFAULT_POSE_DAY: DayOfWeek = DayOfWeek.SUNDAY

    /** At most one workout photo a day: offered only when no workout photo is yet kept for [day]. */
    fun workoutPhotoOffered(workoutPhotoDays: Set<Long>, day: Long): Boolean = day !in workoutPhotoDays

    /**
     * The week a pose belongs to, named by its Monday (the week the rest of the app uses). Keyed on the week, not on the
     * day the photo was taken, so moving the pose day part way through a week cannot leave two sets for it.
     */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Today's quiet prompt: on the pose day, until the day's dismiss, and only while the week's set is incomplete. */
    fun poseDue(today: LocalDate, poseDay: DayOfWeek, dismissedEpochDay: Long?, posesThisWeek: Set<Pose>): Boolean =
        today.dayOfWeek == poseDay && dismissedEpochDay != today.toEpochDay() && posesThisWeek.size < Pose.entries.size

    /** The weeks that hold at least one pose, newest first. */
    fun weeksWithPoses(poses: List<PhotoSnapshot>): List<LocalDate> =
        poses.filter { it.kind == PhotoKind.POSE }.map { it.day }.distinct().sortedDescending().map(LocalDate::ofEpochDay)

    /** The pair the comparison opens on: the week before the newest on the left, the newest on the right; null when absent. */
    fun defaultComparison(weeks: List<LocalDate>): Pair<LocalDate?, LocalDate?> =
        weeks.getOrNull(1) to weeks.getOrNull(0)

    /** A name this app could have written: it is the only kind a restore or a folder copy will ever touch. */
    private val NAME = Regex("""[a-z0-9-]{1,80}\.jpg""")

    fun isOurName(name: String): Boolean = NAME.matches(name)

    /** The long edge a kept photo is scaled down to, and the JPEG quality it is written at. */
    const val MAX_EDGE = 2048
    const val JPEG_QUALITY = 85

    /** The most a backup folder's copy may weigh before a restore refuses it: a photo this app wrote is far smaller. */
    const val MAX_FILE_BYTES = 12_000_000

    /** [width] x [height] scaled so the long edge is at most [MAX_EDGE], never enlarged, keeping the shape. */
    fun targetSize(width: Int, height: Int): Pair<Int, Int> {
        val long = maxOf(width, height)
        if (long <= MAX_EDGE) return width to height
        val scale = MAX_EDGE.toDouble() / long
        return maxOf(1, Math.round(width * scale).toInt()) to maxOf(1, Math.round(height * scale).toInt())
    }

    private const val EXT = ".jpg"

    fun workoutFileName(day: Long, salt: String): String = listOf("w", day, salt).joinToString("-") + EXT

    fun poseFileName(week: Long, pose: Pose, salt: String): String = listOf("p", week, pose.key, salt).joinToString("-") + EXT

    /** The photos the folder backup and a restore must carry: every kept row's file name, once. */
    fun fileNames(rows: List<PhotoSnapshot>): Set<String> = rows.map { it.fileName }.toSet()
}
