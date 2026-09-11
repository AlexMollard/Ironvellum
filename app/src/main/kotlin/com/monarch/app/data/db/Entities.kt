package com.monarch.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String,
    val isWeighted: Boolean,
)

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String,
    val scheduledDay: Int?,
)

@Entity(
    tableName = "preset_entries",
    foreignKeys = [
        ForeignKey(
            entity = PresetEntity::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("presetId")],
)
data class PresetEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long,
    val exerciseId: Long,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double?,
    val modifiers: String,
    val position: Int,
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long?,
    val label: String,
    val startedAtMs: Long,
    val completedAtMs: Long?,
    val xpAwarded: Int,
    val strengthScore: Int = 0,
)

@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val exercisePosition: Int = 0,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double?,
    val modifiers: String,
    val done: Boolean,
)

@Entity(tableName = "stats")
data class StatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val takenAtMs: Long,
    val weightKg: Double,
    val heightCm: Double,
    val bodyFatPct: Double?,
)
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Long = 1,
    val name: String,
    val totalXp: Long,
    val currentTitleId: String?,
    val lifetimeStrength: Long = 0,
    val trainingMode: String = "STRENGTH",
)

@Entity(tableName = "skill_practices")
data class SkillPracticeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val skillName: String,
    val practicedAtMs: Long,
    /** 1 = mastery claim, 0 = practice rep toward the standard. */
    val claimed: Boolean = false,
    /** What was actually achieved: seconds held, reps done, or metres. */
    val value: Int = 0,
    /** Added load, when the attempt was weighted. */
    val weightKg: Double? = null,
)

@Entity(tableName = "title_unlocks")
data class TitleUnlockEntity(
    @PrimaryKey val titleId: String,
    val unlockedAtMs: Long,
)

data class PresetWithEntries(
    @Embedded val preset: PresetEntity,
    @Relation(parentColumn = "id", entityColumn = "presetId")
    val entries: List<PresetEntryEntity>,
)

data class SessionWithSets(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val sets: List<SetLogEntity>,
)

@Entity(tableName = "health_days")
data class HealthDayEntity(
    @PrimaryKey val epochDay: Long,
    val steps: Int,
    val distanceKm: Double,
    val activeKcal: Int,
    val sleepMinutes: Int,
    val restingHr: Int?,
)
