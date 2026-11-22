package com.ironvellum.app.data.db

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

internal data class WorkoutTimes(val id: Long, val startedAtMs: Long, val completedAtMs: Long)
internal data class RepsRow(val exerciseId: Long, val reps: Int, val weightKg: Double?)
internal data class SavedRow(val id: Long, val name: String, val warmup: String, val cooldown: String)
internal data class EntryRow(val exerciseId: Long, val targetSets: Int, val targetReps: Int, val targetWeightKg: Double?)

private const val SUPPORTED_SET = """exerciseId IN (:ids) AND kind='WORKING' AND modifiers='' AND durationSec IS NULL
    AND distanceM IS NULL AND grade IS NULL AND reps BETWEEN 1 AND 1000 AND (weightKg IS NULL OR weightKg BETWEEN 0 AND 1000)"""

private const val SUPPORTED_PRESET = """ FROM presets p WHERE length(p.name) BETWEEN 1 AND 80
    AND instr(p.name,char(0))=0 AND p.name NOT GLOB ('*['||char(1)||'-'||char(31)||char(127)||'-'||char(159)||']*')
    AND trim(p.name)=p.name
    AND (SELECT count(*) FROM preset_entries e WHERE e.presetId=p.id) BETWEEN 1 AND 20
    AND NOT EXISTS (SELECT 1 FROM preset_entries e WHERE e.presetId=p.id AND
        (e.exerciseId NOT IN (:ids) OR e.targetSets NOT BETWEEN 1 AND 50 OR e.targetReps NOT BETWEEN 1 AND 1000
        OR e.targetWeightKg NOT BETWEEN 0 AND 1000 OR e.modifiers!='' OR e.setScheme IS NOT NULL OR e.warmup!=0 OR e.supersetGroup IS NOT NULL))
    AND NOT EXISTS (SELECT 1 FROM preset_preparations a WHERE a.presetId=p.id AND
        (a.kind NOT IN ('WARMUP','COOLDOWN') OR a.mode NOT IN ('AUTO','NONE') OR a.routineId IS NOT NULL))"""

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

    @Query("SELECT count(*) FROM sessions WHERE completedAtMs IS NOT NULL") fun completedCount(): Long
    @Query("""SELECT id,startedAtMs,completedAtMs FROM sessions WHERE completedAtMs IS NOT NULL
        ORDER BY completedAtMs DESC,id DESC LIMIT 100""") fun completed(): List<WorkoutTimes>
    @Query("SELECT count(*) FROM set_logs WHERE sessionId=:session AND done=1") fun doneCount(session: Long): Long
    @Query("SELECT count(*) FROM set_logs WHERE sessionId=:session AND done=1 AND " + SUPPORTED_SET)
    fun supportedSets(session: Long, ids: List<Long>): Long
    @Query("SELECT exerciseId,reps,weightKg FROM set_logs WHERE sessionId=:session AND done=1 AND " + SUPPORTED_SET +
        " ORDER BY exercisePosition,setIndex,id LIMIT 200")
    fun sets(session: Long, ids: List<Long>): List<RepsRow>
    @Query("SELECT count(*) FROM presets") fun presetCount(): Long
    @Query("SELECT count(*)" + SUPPORTED_PRESET) fun supportedPresets(ids: List<Long>): Long
    @Query("""SELECT id,name,
        coalesce((SELECT mode FROM preset_preparations WHERE presetId=p.id AND kind='WARMUP'),'AUTO') AS warmup,
        coalesce((SELECT mode FROM preset_preparations WHERE presetId=p.id AND kind='COOLDOWN'),'AUTO') AS cooldown""" +
        SUPPORTED_PRESET + " ORDER BY p.id DESC LIMIT 50")
    fun presets(ids: List<Long>): List<SavedRow>
    @Query("""SELECT exerciseId,targetSets,targetReps,targetWeightKg FROM preset_entries
        WHERE presetId=:preset ORDER BY position,id LIMIT 20""") fun entries(preset: Long): List<EntryRow>
}
