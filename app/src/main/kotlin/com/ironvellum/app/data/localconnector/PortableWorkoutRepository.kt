package com.ironvellum.app.data.localconnector

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.db.PortableWorkoutReceipt
import com.ironvellum.app.domain.MAX_PROPOSAL_BYTES
import com.ironvellum.app.domain.PortableWorkout
import com.ironvellum.app.domain.WorkoutDraft
import java.util.Collections

internal sealed interface PortableReview {
    val draft: WorkoutDraft
    val unresolvedKeys: Set<String>
}

private class PinnedPortableReview(
    val datasetId: String,
    val fingerprint: String,
    val staged: StagedWorkout,
) : PortableReview {
    override val draft get() = staged.draft
    override val unresolvedKeys: Set<String> = Collections.unmodifiableSet(
        draft.entries.indices.filter { staged.ids[it] == null }.mapTo(linkedSetOf()) { draft.entries[it].exerciseKey }
    )
}

internal sealed interface PortableInspection {
    data class Ready(val review: PortableReview) : PortableInspection
    data class AlreadySaved(val localPresetId: Long) : PortableInspection
    data class Rejected(val reason: LocalFailure) : PortableInspection
}

internal class PortableWorkoutRepository(
    private val db: IronvellumDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.localConnectorDao()
    private val targets = LocalDraftTargets(db.draftReceiptDao())

    suspend fun inspect(bytes: ByteArray): PortableInspection {
        if (bytes.size > MAX_PROPOSAL_BYTES) return PortableInspection.Rejected(LocalFailure.FILE_LIMIT)
        val portable = try { PortableWorkout.parse(bytes.copyOf()) }
        catch (_: IllegalArgumentException) { return PortableInspection.Rejected(LocalFailure.INVALID_FILE) }
        return try {
            db.withTransaction {
                val dataset = dao.ensureDataset()
                dao.portableReceipt(dataset, portable.fingerprint)?.let {
                    return@withTransaction PortableInspection.AlreadySaved(it.localPresetId)
                }
                val staged = StagedWorkout(portable.draft, targets.availableIds(portable.draft))
                PortableInspection.Ready(PinnedPortableReview(dataset, portable.fingerprint, staged))
            }
        } catch (e: Refused) { PortableInspection.Rejected(e.reason) }
        catch (_: SQLiteException) { PortableInspection.Rejected(LocalFailure.STORAGE) }
    }

    private fun receipt(review: PinnedPortableReview): PortableWorkoutReceipt? {
        if (dao.dataset() != review.datasetId) throw Refused(LocalFailure.WRONG_DATASET)
        return dao.portableReceipt(review.datasetId, review.fingerprint)
    }

    suspend fun reviewEdits(
        review: PortableReview,
        draft: WorkoutDraft,
        selectedMovementIds: Map<Int, Long> = emptyMap(),
    ): PortableInspection = try {
        val selected = selectedMovementIds.toMap()
        db.withTransaction {
            val pinned = review as PinnedPortableReview
            receipt(pinned)?.let { return@withTransaction PortableInspection.AlreadySaved(it.localPresetId) }
            val edited = targets.reviewEdits(pinned.staged, draft, selected)
            PortableInspection.Ready(PinnedPortableReview(pinned.datasetId, pinned.fingerprint, edited))
        }
    } catch (e: Refused) { PortableInspection.Rejected(e.reason) }
    catch (_: SQLiteException) { PortableInspection.Rejected(LocalFailure.STORAGE) }

    suspend fun save(review: PortableReview): LocalSave = try {
        db.withTransaction {
            val pinned = review as PinnedPortableReview
            receipt(pinned)?.let { return@withTransaction LocalSave.AlreadySaved(it.localPresetId) }
            if (pinned.unresolvedKeys.isNotEmpty()) throw Refused(LocalFailure.MOVEMENT)
            val movements = targets.requiredIds(pinned.draft)
            if (movements != pinned.staged.ids) throw Refused(LocalFailure.MOVEMENT)
            val preset = targets.insert(pinned.draft, movements)
            dao.insert(PortableWorkoutReceipt(pinned.datasetId, pinned.fingerprint, preset, clock()))
            LocalSave.Saved(preset)
        }
    } catch (e: Refused) { LocalSave.Rejected(e.reason) }
    catch (_: SQLiteException) { LocalSave.Rejected(LocalFailure.STORAGE) }
}
