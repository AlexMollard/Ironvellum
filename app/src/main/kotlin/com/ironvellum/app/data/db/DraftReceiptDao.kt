package com.ironvellum.app.data.db

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query

/** Historical local ID deliberately has no FK: deleting a workout must never allow re-import. */
@Entity(tableName = "mcp_draft_receipts", primaryKeys = ["backend", "userId", "draftId"])
internal data class DraftReceipt(
    val backend: String,
    val userId: String,
    val draftId: String,
    val contentHash: String,
    val localPresetId: Long,
    val savedAtMs: Long,
    val ackPending: Boolean,
)

internal const val MAX_DRAFT_CATALOGUE_ROWS = 5_000
private const val CANDIDATE_LIMIT = MAX_DRAFT_CATALOGUE_ROWS + 1

@Dao
internal interface DraftReceiptDao {
    @Query("SELECT * FROM mcp_draft_receipts WHERE backend=:backend AND userId=:user AND draftId=:id")
    fun receipt(backend: String, user: String, id: String): DraftReceipt?

    @Query("""SELECT * FROM mcp_draft_receipts WHERE backend=:backend AND userId=:user AND ackPending=1
        ORDER BY CASE WHEN draftId > :after THEN 0 ELSE 1 END, draftId LIMIT 20""")
    fun pending(backend: String, user: String, after: String = ""): List<DraftReceipt>

    @Query("UPDATE mcp_draft_receipts SET ackPending=0 WHERE backend=:backend AND userId=:user AND draftId=:id AND contentHash=:hash")
    fun acknowledged(backend: String, user: String, id: String, hash: String): Int

    @Query("SELECT id, name, custom, metric FROM exercises LIMIT $CANDIDATE_LIMIT")
    fun movementCandidates(): Cursor
    @Insert fun insertPreset(preset: PresetEntity): Long
    @Insert fun insertEntries(entries: List<PresetEntryEntity>)
    @Insert fun insertChoices(choices: List<PresetPreparationEntity>)
    @Insert fun insertReceipt(receipt: DraftReceipt)
}
