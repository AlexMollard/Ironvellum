package com.ironvellum.app.data

import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.DraftEnvelope
import com.ironvellum.app.data.cloud.DraftStatus
import com.ironvellum.app.data.db.*
import com.ironvellum.app.domain.PreparationKind
import com.ironvellum.app.domain.WorkoutDraft
import java.util.concurrent.Callable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class DraftCatalogueTooLarge : IllegalArgumentException("Draft movement catalogue exceeds $MAX_DRAFT_CATALOGUE_ROWS rows")

internal class DraftStore(private val db: IronvellumDatabase) {
    private val dao get() = db.draftReceiptDao()

    private fun existing(scope: AccountRepository.Scope, envelope: DraftEnvelope): DraftReceipt? {
        val receipt = dao.receipt(scope.backend, scope.userId, envelope.id)
        require(receipt == null || receipt.contentHash == envelope.contentHash) { "Draft receipt conflict" }
        return receipt
    }

    /** The query returns at most one overflow sentinel. A partial catalogue cannot establish uniqueness. */
    private fun resolve(draft: WorkoutDraft) = dao.movementCandidates().use { cursor ->
        if (cursor.count > MAX_DRAFT_CATALOGUE_ROWS) throw DraftCatalogueTooLarge()
        val rows = generateSequence {
            if (cursor.moveToNext()) DraftMovementCandidate(cursor.getLong(0), cursor.getString(1),
                cursor.getInt(2) != 0, cursor.getString(3)) else null
        }
        DraftMovements.resolve(draft, rows)
    }

    /** Preflight runs before approval. Repeat the checks in the writer transaction to catch concurrent edits. */
    suspend fun validate(scope: AccountRepository.Scope, envelope: DraftEnvelope) = withContext(Dispatchers.IO) {
        if (existing(scope, envelope) == null) resolve(envelope.draft)
        Unit
    }

    private suspend fun <T> write(accounts: AccountRepository, scope: AccountRepository.Scope, block: () -> T): T =
        withContext(Dispatchers.IO) {
            db.openHelper.writableDatabase
            val job = checkNotNull(currentCoroutineContext()[Job])
            accounts.commitIfCurrent(scope, job) {
                db.runInTransaction(Callable {
                    job.ensureActive()
                    block()
                })
            }
        }

    suspend fun save(accounts: AccountRepository, scope: AccountRepository.Scope, envelope: DraftEnvelope): DraftReceipt =
        write(accounts, scope) {
            require(envelope.status == DraftStatus.APPROVED || envelope.status == DraftStatus.ACCEPTED)
            existing(scope, envelope)?.let { return@write it }
            val draft = envelope.draft
            val ids = resolve(draft)
            val presetId = dao.insertPreset(PresetEntity(name = draft.name, note = "", scheduledDay = null))
            dao.insertEntries(draft.entries.mapIndexed { position, entry ->
                PresetEntryEntity(presetId = presetId, exerciseId = ids[position], targetSets = entry.sets,
                    targetReps = entry.reps, targetWeightKg = entry.loadKg?.toDouble(), modifiers = "", position = position)
            })
            dao.insertChoices(listOf(
                PresetPreparationEntity(presetId, PreparationKind.WARMUP.name, draft.warmup.name),
                PresetPreparationEntity(presetId, PreparationKind.COOLDOWN.name, draft.cooldown.name),
            ))
            DraftReceipt(scope.backend, scope.userId, envelope.id, envelope.contentHash, presetId,
                System.currentTimeMillis(), ackPending = true).also(dao::insertReceipt)
        }

    suspend fun pending(scope: AccountRepository.Scope) = withContext(Dispatchers.IO) { dao.pending(scope.backend, scope.userId) }

    suspend fun markAcknowledged(accounts: AccountRepository, scope: AccountRepository.Scope, receipt: DraftReceipt) =
        write(accounts, scope) {
            require(receipt.backend == scope.backend && receipt.userId == scope.userId)
            check(dao.acknowledged(scope.backend, scope.userId, receipt.draftId, receipt.contentHash) == 1)
        }
}
