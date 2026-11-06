package com.ironvellum.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PreparationDao {
    @Query("SELECT * FROM preparation_routines WHERE kind = :kind ORDER BY name, id")
    fun observeRoutines(kind: String): Flow<List<PreparationRoutineEntity>>
    @Query("SELECT * FROM preparation_routines")
    suspend fun routines(): List<PreparationRoutineEntity>
    @Query("SELECT * FROM preparation_routines WHERE id = :id")
    suspend fun routine(id: Long): PreparationRoutineEntity?
    @Insert suspend fun insertRoutine(row: PreparationRoutineEntity): Long
    @Update suspend fun updateRoutine(row: PreparationRoutineEntity)
    @Query("UPDATE preset_preparations SET mode = 'AUTO', routineId = NULL WHERE routineId = :id AND mode = 'SAVED'")
    suspend fun detach(id: Long)
    @Query("DELETE FROM preparation_routines WHERE id = :id") suspend fun deleteRoutine(id: Long)
    @Query("DELETE FROM preparation_routines") suspend fun clearRoutines()
    @Query("SELECT * FROM preset_preparations WHERE presetId = :presetId")
    suspend fun choices(presetId: Long): List<PresetPreparationEntity>
    @Query("SELECT * FROM preset_preparations") fun observeAllChoices(): Flow<List<PresetPreparationEntity>>
    @Query("SELECT * FROM preset_preparations WHERE presetId = :presetId")
    fun observeChoices(presetId: Long): Flow<List<PresetPreparationEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putChoice(row: PresetPreparationEntity)
    @Query("SELECT * FROM session_preparations WHERE sessionId = :sessionId AND kind = :kind")
    fun observeSession(sessionId: Long, kind: String): Flow<SessionPreparationEntity?>
    @Query("SELECT * FROM session_preparations WHERE sessionId = :sessionId AND kind = :kind")
    suspend fun session(sessionId: Long, kind: String): SessionPreparationEntity?
    @Query("SELECT * FROM session_preparations") suspend fun sessions(): List<SessionPreparationEntity>
    @Query("SELECT * FROM session_preparations WHERE sessionId = :sessionId")
    suspend fun sessionsFor(sessionId: Long): List<SessionPreparationEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSession(row: SessionPreparationEntity)
}
