package com.ironvellum.app.data.db

import android.database.Cursor
import androidx.room.*

@Entity(tableName = "local_connector_identity")
internal data class LocalIdentity(@PrimaryKey val slot: Int = 1, val datasetId: String)

@Entity(tableName = "local_connector_objects", primaryKeys = ["kind", "rowId"])
internal data class LocalObject(val kind: String, val rowId: Long, val opaqueId: String)

@Entity(tableName = "local_connector_exports", primaryKeys = ["datasetId", "snapshotId"])
internal data class LocalSnapshot(val datasetId: String, val snapshotId: String, val contextSha256: String, val exportedAtMs: Long)

/** No preset FK. The historical receipt survives deletion of its saved workout. */
@Entity(tableName = "local_connector_receipts", primaryKeys = ["datasetId", "draftId"])
internal data class LocalReceipt(val datasetId: String, val draftId: String, val proposalSha256: String,
    val snapshotId: String, val localPresetId: Long, val savedAtMs: Long)

/** The receipt still prevents re-import after its preset is deleted. */
@Entity(tableName = "portable_workout_receipts", primaryKeys = ["datasetId", "fingerprint"])
internal data class PortableWorkoutReceipt(
    val datasetId: String, val fingerprint: String, val localPresetId: Long, val savedAtMs: Long,
)

internal data class WorkoutTimes(val id: Long, val startedAtMs: Long, val completedAtMs: Long)
internal data class ReadPresetRow(val id: Long, val name: String)
internal data class ReadMovementRow(val id: Long, val name: String, val custom: Boolean, val metric: String)

@Dao
internal interface LocalConnectorDao {
    @Query("SELECT datasetId FROM local_connector_identity WHERE slot=1") fun dataset(): String?
    @Insert fun insert(identity: LocalIdentity)
    @Query("SELECT opaqueId FROM local_connector_objects WHERE kind=:kind AND rowId=:id") fun opaque(kind: String, id: Long): String?
    @Insert fun insert(mapping: LocalObject)
    @Insert fun insert(snapshot: LocalSnapshot)
    @Query("SELECT * FROM local_connector_exports WHERE datasetId=:dataset AND snapshotId=:snapshot")
    fun snapshot(dataset: String, snapshot: String): LocalSnapshot?
    @Query("SELECT snapshotId FROM local_connector_exports ORDER BY rowid DESC LIMIT 1") fun lastSnapshot(): String?
    @Insert fun insert(receipt: LocalReceipt)
    @Query("SELECT * FROM local_connector_receipts WHERE datasetId=:dataset AND draftId=:draft")
    fun receipt(dataset: String, draft: String): LocalReceipt?
    @Query("DELETE FROM local_connector_identity") fun clearIdentity()
    @Query("DELETE FROM local_connector_objects") fun clearObjects()
    @Query("DELETE FROM local_connector_exports") fun clearSnapshots()
    @Query("DELETE FROM local_connector_receipts") fun clearReceipts()
    @Insert fun insert(receipt: PortableWorkoutReceipt)
    @Query("SELECT * FROM portable_workout_receipts WHERE datasetId=:dataset AND fingerprint=:fingerprint")
    fun portableReceipt(dataset: String, fingerprint: String): PortableWorkoutReceipt?
    @Query("DELETE FROM portable_workout_receipts") fun clearPortableReceipts()

    /** Provable lower bound for output bytes before Room materializes any training pages.
     * Every serialized record has more than the fixed framing allowance. Text only grows when escaped. */
    @Query("""SELECT
        (SELECT total(80) FROM sessions WHERE completedAtMs IS NOT NULL) +
        (SELECT total(120 + length(CAST(s.modifiers AS BLOB)) + coalesce(length(CAST(s.grade AS BLOB)),0))
         FROM set_logs s JOIN sessions w ON w.id=s.sessionId WHERE w.completedAtMs IS NOT NULL) +
        (SELECT total(80 + length(CAST(name AS BLOB))) FROM presets) +
        (SELECT total(120 + length(CAST(modifiers AS BLOB)) + coalesce(length(CAST(setScheme AS BLOB)),0))
         FROM preset_entries)""")
    fun minimumOutputBytes(): Double

    @Query("""SELECT id,startedAtMs,completedAtMs FROM sessions WHERE completedAtMs IS NOT NULL
        ORDER BY id""") fun completed(): Cursor

    @Query("""SELECT exerciseId,exercisePosition,setIndex,kind,modifiers,supersetGroup,rpe,done,warmupDone,
        reps,weightKg,durationSec,distanceM,grade FROM set_logs WHERE sessionId=:session ORDER BY exercisePosition,setIndex,id""")
    fun sets(session: Long): Cursor

    @Query("SELECT id,name FROM presets ORDER BY id") fun presets(): Cursor

    @Query("""SELECT exerciseId,targetSets,targetReps,targetWeightKg,modifiers,position,supersetGroup,setScheme,warmup
        FROM preset_entries WHERE presetId=:preset ORDER BY position,id""") fun entries(preset: Long): Cursor

    /** No complete, unreferenced name crosses the cursor window. Trim exactly the characters
     * supplied by Kotlin, then take only a UTF-8 value small enough to be a seed key. */
    @Query("""SELECT candidate FROM (SELECT trim(name,:padding) AS candidate FROM exercises)
        WHERE length(CAST(candidate AS BLOB)) <= :maxBytes""")
    fun candidateNames(padding: String, maxBytes: Int): Cursor
    @Query("""SELECT id,CASE WHEN length(CAST(name AS BLOB)) <= :maxBytes THEN name END,custom,metric
        FROM exercises WHERE id=:id""") fun movement(id: Long, maxBytes: Int): Cursor
    @Query("SELECT kind,mode,routineId FROM preset_preparations WHERE presetId=:id ORDER BY kind LIMIT 3")
    fun choices(id: Long): Cursor
    @Query("""SELECT sessionId,kind,
        CASE WHEN length(CAST(snapshotJson AS BLOB)) <= 262144 THEN CAST(snapshotJson AS BLOB) END,
        CASE WHEN length(CAST(progressJson AS BLOB)) <= 262144 THEN CAST(progressJson AS BLOB) END
        FROM session_preparations WHERE sessionId=:id ORDER BY kind""")
    fun preparations(id: Long): Cursor
    @Query("""SELECT id,kind,CASE WHEN length(CAST(name AS BLOB)) <= :maxBytes THEN name END,
        CASE WHEN length(CAST(stepsJson AS BLOB)) <= 262144 AND length(CAST(name AS BLOB)) <= :maxBytes - 262144
            THEN CAST(stepsJson AS BLOB) END FROM preparation_routines WHERE id=:id""") fun routine(id: Long, maxBytes: Int): Cursor

    @Query("SELECT substr(CAST(stepsJson AS BLOB),:start,65536) FROM preparation_routines WHERE id=:id")
    fun routineChunk(id: Long, start: Int): ByteArray
    @Query("SELECT substr(CAST(snapshotJson AS BLOB),:start,65536) FROM session_preparations WHERE sessionId=:id AND kind=:kind")
    fun snapshotChunk(id: Long, kind: String, start: Int): ByteArray
    @Query("SELECT substr(CAST(progressJson AS BLOB),:start,65536) FROM session_preparations WHERE sessionId=:id AND kind=:kind")
    fun progressChunk(id: Long, kind: String, start: Int): ByteArray
}
