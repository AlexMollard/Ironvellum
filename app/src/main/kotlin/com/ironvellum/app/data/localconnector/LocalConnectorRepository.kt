package com.ironvellum.app.data.localconnector

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.ironvellum.app.data.*
import com.ironvellum.app.data.db.*
import com.ironvellum.app.domain.*
import java.util.UUID

internal data class LocalExportSummary(val datasetId: String, val snapshotId: String, val exportedAtMs: Long, val coverage: LocalCoverage)
internal class LocalContextExport(bytes: ByteArray, val summary: LocalExportSummary) {
    private val content = bytes.copyOf()
    val bytes: ByteArray get() = content.copyOf()
}

internal enum class LocalFailure { INVALID_FILE, UNSUPPORTED_FORMAT, FILE_LIMIT, WRONG_DATASET, UNKNOWN_EXPORT,
    CONFLICT, EXPIRED, FUTURE, MOVEMENT, CATALOGUE_LIMIT, STORAGE, INVALID_HISTORY }
internal class LocalExportException(val reason: LocalFailure, cause: Throwable? = null) : IllegalStateException(reason.name, cause)
private class Refused(val reason: LocalFailure) : RuntimeException()

internal sealed interface LocalDraftReview {
    val proposal: LocalProposal
    val exportedAtMs: Long
    val isLatestExport: Boolean
}

private class PinnedLocalReview(
    private val content: ByteArray, override val proposal: LocalProposal, override val exportedAtMs: Long,
    override val isLatestExport: Boolean,
    private val reviewedIds: List<Long>,
) : LocalDraftReview {
    fun verifiedProposal() = LocalConnectorFiles.proposal(content).also { check(it.sha256 == proposal.sha256) }
    fun matches(ids: List<Long>) = ids == reviewedIds
}

internal sealed interface LocalInspection {
    data class Ready(val review: LocalDraftReview) : LocalInspection
    data class AlreadySaved(val localPresetId: Long) : LocalInspection
    data class Rejected(val reason: LocalFailure) : LocalInspection
}

internal sealed interface LocalSave {
    data class Saved(val localPresetId: Long) : LocalSave
    data class AlreadySaved(val localPresetId: Long) : LocalSave
    data class Rejected(val reason: LocalFailure) : LocalSave
}

internal class LocalConnectorRepository(private val db: IronvellumDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    suspend fun savedPresetName(id: Long): String? = db.presetDao().presetName(id)

    private val dao = db.localConnectorDao()
    private val targets = db.draftReceiptDao()
    private fun uuid() = UUID.randomUUID().toString()

    private fun movementRows(): List<DraftMovementCandidate> = targets.movementCandidates().use { cursor ->
        if (cursor.count > MAX_DRAFT_CATALOGUE_ROWS) throw Refused(LocalFailure.CATALOGUE_LIMIT)
        buildList {
            while (cursor.moveToNext()) add(DraftMovementCandidate(cursor.getLong(0), cursor.getString(1),
                cursor.getInt(2) != 0, cursor.getString(3)))
        }
    }

    private fun ids(draft: WorkoutDraft): List<Long> = try {
        if (draft.entries.any { !LocalCatalogue.acceptsDraft(it.exerciseKey) }) throw Refused(LocalFailure.MOVEMENT)
        DraftMovements.resolve(draft, movementRows().asSequence())
    } catch (_: DraftMovementUnavailable) { throw Refused(LocalFailure.MOVEMENT) }

    private fun opaque(kind: String, id: Long) = dao.opaque(kind, id) ?: ("local:" + uuid()).also {
        dao.insert(LocalObject(kind, id, it))
    }

    suspend fun exportContext(): LocalContextExport = try {
        db.withTransaction {
            val dataset = dao.dataset() ?: uuid().also { dao.insert(LocalIdentity(datasetId = it)) }
            val snapshot = uuid()
            val generated = clock()
            if (generated !in 0..MAX_LOCAL_TIME) throw Refused(LocalFailure.INVALID_HISTORY)
            val context = ReadTrainingProjection(dao, ::opaque).context(dataset, snapshot, generated)
            val bytes = context.encode()
            val coverage = context.coverage
            dao.insert(LocalSnapshot(dataset, snapshot, LocalConnectorFiles.sha256(bytes), generated))
            LocalContextExport(bytes, LocalExportSummary(dataset, snapshot, generated, coverage))
        }
    } catch (e: Refused) { throw LocalExportException(e.reason) }
    catch (_: SQLiteException) { throw LocalExportException(LocalFailure.STORAGE) }

    private fun snapshot(p: LocalProposal): LocalSnapshot {
        return dao.snapshot(p.datasetId, p.snapshotId)?.takeIf { it.contextSha256 == p.contextSha256 }
            ?: throw Refused(LocalFailure.UNKNOWN_EXPORT)
    }

    private fun receipt(p: LocalProposal): LocalReceipt? {
        if (p.datasetId != dao.dataset()) throw Refused(LocalFailure.WRONG_DATASET)
        return dao.receipt(p.datasetId, p.draftId)?.also {
            if (it.proposalSha256 != p.sha256) throw Refused(LocalFailure.CONFLICT)
        }
    }

    private fun checkTime(p: LocalProposal): Long = clock().also { now ->
        if (p.expiresAtMs <= now) throw Refused(LocalFailure.EXPIRED)
        if (p.createdAtMs > now + 300_000L) throw Refused(LocalFailure.FUTURE)
    }

    suspend fun inspectDraft(bytes: ByteArray): LocalInspection {
        if (bytes.size > MAX_PROPOSAL_BYTES) return LocalInspection.Rejected(LocalFailure.FILE_LIMIT)
        val original = bytes.copyOf()
        val proposal = try { LocalConnectorFiles.proposal(original) }
        catch (_: UnsupportedLocalFormat) { return LocalInspection.Rejected(LocalFailure.UNSUPPORTED_FORMAT) }
        catch (_: IllegalArgumentException) { return LocalInspection.Rejected(LocalFailure.INVALID_FILE) }
        return try {
            db.withTransaction {
                receipt(proposal)?.let { return@withTransaction LocalInspection.AlreadySaved(it.localPresetId) }
                val source = snapshot(proposal)
                checkTime(proposal)
                LocalInspection.Ready(PinnedLocalReview(original, proposal, source.exportedAtMs,
                    dao.lastSnapshot() == source.snapshotId, ids(proposal.draft)))
            }
        } catch (e: Refused) { LocalInspection.Rejected(e.reason) }
        catch (_: SQLiteException) { LocalInspection.Rejected(LocalFailure.STORAGE) }
    }

    suspend fun saveDraft(review: LocalDraftReview): LocalSave = try {
        db.withTransaction {
            val pinned = review as PinnedLocalReview
            val p = pinned.verifiedProposal()
            receipt(p)?.let { return@withTransaction LocalSave.AlreadySaved(it.localPresetId) }
            snapshot(p)
            checkTime(p)
            val draft = p.draft
            val movements = ids(draft)
            if (!pinned.matches(movements)) throw Refused(LocalFailure.MOVEMENT)
            val preset = targets.insertPreset(PresetEntity(name = draft.name, note = "", scheduledDay = null))
            targets.insertEntries(draft.entries.mapIndexed { position, entry ->
                PresetEntryEntity(presetId = preset, exerciseId = movements[position], targetSets = entry.sets,
                    targetReps = entry.reps, targetWeightKg = entry.loadKg?.toDouble(), modifiers = "", position = position)
            })
            targets.insertChoices(listOf(PresetPreparationEntity(preset, "WARMUP", draft.warmup.name),
                PresetPreparationEntity(preset, "COOLDOWN", draft.cooldown.name)))
            dao.insert(LocalReceipt(p.datasetId, p.draftId, p.sha256, p.snapshotId, preset, checkTime(p)))
            LocalSave.Saved(preset)
        }
    } catch (e: Refused) { LocalSave.Rejected(e.reason) }
    catch (_: SQLiteException) { LocalSave.Rejected(LocalFailure.STORAGE) }
}
