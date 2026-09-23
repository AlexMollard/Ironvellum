package com.ironvellum.app.data.db

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
    /** Stored as the enum name; "REPS" for every pre-activity row. */
    val metric: String = "REPS",
    /** "" for lifting; otherwise e.g. "Cardio", "Sport". */
    val category: String = "",
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
    /** User-authored session title; syncs to the feed. */
    val title: String = "",
    /** PUBLIC note, visible to other players in the feed. */
    val note: String = "",
    /** Device-only note. NEVER uploaded - there is no server column for it. */
    val privateNote: String = "",
    /**
     * True for sessions merged in from a Strong/Hevy CSV import. Imported
     * history earns XP and strength locally but is excluded from the cloud
     * feed push — it still reaches the cloud inside the full-archive backup.
     */
    val imported: Boolean = false,
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
    /** Activity fields — all null for lifting sets. */
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    val grade: String? = null,
)

@Entity(tableName = "stats")
data class StatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val takenAtMs: Long,
    val weightKg: Double,
    /** Height is now profile-owned; 0.0 sentinel on a row means "profile height not set yet". */
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
    /** Set once in Settings, stamped onto every new stat row. Null = never set. */
    val heightCm: Double? = null,
    /** Sex enum name; feeds the Navy body-fat estimator. */
    val sex: String = "MALE",
    /**
     * The hand-drawn treatment is the app's own look, so it is what a lifter
     * gets; CLEAN is the opt-out. Dark panels with a neon accent are the house
     * style of every RPG fitness tracker, and the brushed edges are the one
     * part of this UI no other app in the genre has.
     */
    val inkStyle: Boolean = true,
    /**
     * Which strength-scoring formula last touched the stored sessions, as
     * [com.ironvellum.app.domain.StrengthIndex.SCORING_VERSION]. A marker only:
     * recomputing a score from stored sets is Kotlin work the SQL layer cannot
     * express, so the migration writes 0 here and `ensureSeeded` performs the
     * one-time restatement. 0 = pre-marker row (or fresh install).
     */
    val scoringVersion: Int = 0,
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

/** Projection for the activity ledger: one done activity set with its exercise's metric/category. */
data class ActivitySetRow(
    val exerciseId: Long,
    val exerciseName: String,
    val metric: String,
    val category: String,
    val reps: Int,
    val weightKg: Double?,
    val durationSec: Int?,
    val distanceM: Double?,
    val grade: String?,
)


@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stored as the enum name; mapped defensively on read. */
    val site: String,
    val valueCm: Double,
    val takenAtMs: Long,
)

/**
 * Push watermark: the fingerprint of each completed session as it was last
 * uploaded successfully. Persisted (not held in memory) because a process-local
 * watermark makes every cold start re-upload the whole training history.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val sessionId: Long,
    val fingerprint: Int,
)


/**
 * Idle game state, single row (id = 1, mirroring profile). The row is created
 * by MIGRATION_16_17; a null read means "first launch before any collect" and
 * callers treat it as the zeroed default.
 */
@Entity(tableName = "idle_state")
data class IdleStateEntity(
    @PrimaryKey val id: Long = 1,
    // Zeroed defaults: callers read `idleDao.get() ?: IdleStateEntity()` for the
    // pre-migration/first-launch case, which needs a no-arg construction.
    val essence: Long = 0,
    val shadows: Int = 0,
    val relicMultiplier: Double = 1.0,
    val lastCollectedAtMs: Long = 0,
)

/**
 * Gacha bank: unspent level-up rolls, single row (id = 1, mirroring idle_state).
 * A null read means "no rolls banked yet" — callers treat it as zero.
 */
@Entity(tableName = "gacha_state")
data class GachaStateEntity(
    @PrimaryKey val id: Long = 1,
    val rolls: Int = 0,
    // Cosmetic override: id from Gacha.CREST_FRAMES worn on the lifter crest.
    // Carried on the entity (nullable) so every copy()/upsert of the single
    // row preserves it instead of wiping the equipped choice.
    val equippedFrame: String? = null,
    // Figure-only draws since the last relic or frame. Persisted because pity
    // has to survive the app being closed between rank-ups: held in memory it
    // would reset on every launch and never actually fire.
    val figureStreak: Int = 0,
)

/** One owned crest frame per row; id is the stable catalogue id from Gacha. */
@Entity(tableName = "owned_crest_frames")
data class OwnedCrestFrameEntity(
    @PrimaryKey val frameId: String,
    val ownedAtMs: Long,
)

/**
 * A relic a draw produced. The idle rate uses the STRONGEST multiplier, but a
 * relic still has a name and a moment it was drawn — discarding those left the
 * lifter with a bare ×1.24 and no way to see what earned it.
 */
@Entity(tableName = "owned_relics")
data class OwnedRelicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val multiplier: Double,
    val drawnAtMs: Long,
)
