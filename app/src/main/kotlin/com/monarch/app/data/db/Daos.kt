package com.monarch.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.monarch.app.domain.ExerciseSetRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY muscleGroup, name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun byId(id: Long): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(exercises: List<ExerciseEntity>): List<Long>
}

@Dao
interface PresetDao {
    @Transaction
    @Query("SELECT * FROM presets ORDER BY name")
    fun observePresets(): Flow<List<PresetWithEntries>>
    @Transaction
    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun presetWithEntries(id: Long): PresetWithEntries?

    @Query("SELECT COUNT(*) FROM presets")
    suspend fun count(): Int

    @Insert
    suspend fun insertPreset(preset: PresetEntity): Long

    @Update
    suspend fun updatePreset(preset: PresetEntity)

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun deletePreset(id: Long)

    @Insert
    suspend fun insertEntries(entries: List<PresetEntryEntity>)

    @Query("DELETE FROM preset_entries WHERE presetId = :presetId")
    suspend fun clearEntries(presetId: Long)
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: Long): SessionEntity?

    @Insert
    suspend fun insertSets(sets: List<SetLogEntity>): List<Long>

    @Update
    suspend fun updateSet(set: SetLogEntity)

    @Query("SELECT * FROM set_logs WHERE id = :id")
    suspend fun setById(id: Long): SetLogEntity?

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("UPDATE set_logs SET modifiers = :modifiers WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    suspend fun setModifiers(sessionId: Long, exerciseId: Long, modifiers: String)

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY exerciseId, setIndex")
    suspend fun setsFor(sessionId: Long): List<SetLogEntity>

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY exerciseId, setIndex")
    fun observeSets(sessionId: Long): Flow<List<SetLogEntity>>
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<SessionEntity?>

    @Query("DELETE FROM sessions WHERE id = :id AND completedAtMs IS NULL")
    suspend fun deleteAbandoned(id: Long)

    @Query(
        "SELECT s.* FROM set_logs s JOIN sessions x ON s.sessionId = x.id " +
            "WHERE s.exerciseId = :exerciseId AND s.done = 1 AND x.completedAtMs IS NOT NULL " +
            "ORDER BY x.startedAtMs DESC, s.id DESC LIMIT 40",
    )
    suspend fun recentDoneSets(exerciseId: Long): List<SetLogEntity>

    @Query("SELECT * FROM sessions ORDER BY startedAtMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionEntity>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE completedAtMs IS NOT NULL ORDER BY startedAtMs DESC")
    fun observeCompletedWithSets(): Flow<List<SessionWithSets>>

    @Query("SELECT COUNT(*) FROM sessions WHERE completedAtMs IS NOT NULL")
    suspend fun completedCount(): Int

    @Query(
        "SELECT COUNT(*) FROM set_logs s JOIN sessions x ON s.sessionId = x.id " +
            "WHERE s.done = 1 AND x.completedAtMs IS NOT NULL",
    )
    suspend fun completedSetCount(): Int

    @Query(
        "SELECT COALESCE(SUM(s.reps), 0) FROM set_logs s JOIN sessions x ON s.sessionId = x.id " +
            "WHERE s.done = 1 AND x.completedAtMs IS NOT NULL",
    )
    suspend fun completedRepSum(): Int

    /** Every logged set for one movement, joined with its session timestamp, newest first. */
    @Query(
        "SELECT s.sessionId AS sessionId, x.startedAtMs AS atMs, s.setIndex AS setIndex, " +
            "s.reps AS reps, s.weightKg AS weightKg, s.modifiers AS modifiers, s.done AS done " +
            "FROM set_logs s JOIN sessions x ON s.sessionId = x.id " +
            "WHERE s.exerciseId = :exerciseId ORDER BY x.startedAtMs DESC, s.setIndex ASC",
    )
    fun observeExerciseSets(exerciseId: Long): Flow<List<ExerciseSetRow>>
}

@Dao
interface StatDao {
    @Query("SELECT * FROM stats ORDER BY takenAtMs DESC")
    fun observeAll(): Flow<List<StatEntity>>

    @Insert
    suspend fun insert(stat: StatEntity)

    @Query("DELETE FROM stats WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ProfileDao {
    @Query("UPDATE profile SET lifetimeStrength = :strength WHERE id = 1")
    suspend fun setLifetimeStrength(strength: Long)
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Query("UPDATE profile SET totalXp = totalXp + :xp WHERE id = 1")
    suspend fun addXp(xp: Long)

    @Query("UPDATE profile SET name = :name WHERE id = 1")
    suspend fun setName(name: String)

    @Query("UPDATE profile SET trainingMode = :mode WHERE id = 1")
    suspend fun setTrainingMode(mode: String)

    @Query("UPDATE profile SET currentTitleId = :titleId WHERE id = 1")
    suspend fun setCurrentTitle(titleId: String?)
}

@Dao
interface SkillPracticeDao {
    @Query("SELECT * FROM skill_practices ORDER BY practicedAtMs DESC")
    fun observeAll(): Flow<List<SkillPracticeEntity>>

    @Query("SELECT * FROM skill_practices WHERE skillName = :name AND claimed = 1 LIMIT 1")
    suspend fun claim(name: String): SkillPracticeEntity?

    @Query("DELETE FROM skill_practices WHERE skillName = :name AND claimed = 1")
    suspend fun deleteClaims(name: String)

    @Insert
    suspend fun insert(practice: SkillPracticeEntity)
}

@Dao
interface TitleDao {
    @Query("SELECT * FROM title_unlocks")
    fun observeAll(): Flow<List<TitleUnlockEntity>>

    @Query("SELECT titleId FROM title_unlocks")
    suspend fun heldIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(unlocks: List<TitleUnlockEntity>)
}

@Dao
interface HealthDayDao {
    @Query("SELECT * FROM health_days ORDER BY epochDay DESC")
    fun observeAll(): Flow<List<HealthDayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(days: List<HealthDayEntity>)

    /** Clears rows a previous build wrote with no signal in them. */
    @Query(
        "DELETE FROM health_days WHERE steps = 0 AND distanceKm = 0 " +
            "AND activeKcal = 0 AND sleepMinutes = 0 AND restingHr IS NULL",
    )
    suspend fun purgeEmpty()

    @Query("SELECT COUNT(*) FROM health_days")
    suspend fun count(): Int
}
