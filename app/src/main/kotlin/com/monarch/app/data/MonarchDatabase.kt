package com.monarch.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.monarch.app.data.db.ExerciseDao
import com.monarch.app.data.db.ExerciseEntity
import com.monarch.app.data.db.GachaDao
import com.monarch.app.data.db.GachaStateEntity
import com.monarch.app.data.db.HealthDayDao
import com.monarch.app.data.db.HealthDayEntity
import com.monarch.app.data.db.IdleDao
import com.monarch.app.data.db.IdleStateEntity
import com.monarch.app.data.db.MeasurementDao
import com.monarch.app.data.db.MeasurementEntity
import com.monarch.app.data.db.OwnedCrestFrameEntity
import com.monarch.app.data.db.PresetDao
import com.monarch.app.data.db.PresetEntity
import com.monarch.app.data.db.PresetEntryEntity
import com.monarch.app.data.db.ProfileDao
import com.monarch.app.data.db.ProfileEntity
import com.monarch.app.data.db.SessionDao
import com.monarch.app.data.db.SessionEntity
import com.monarch.app.data.db.SetLogEntity
import com.monarch.app.data.db.SkillPracticeDao
import com.monarch.app.data.db.SkillPracticeEntity
import com.monarch.app.data.db.StatDao
import com.monarch.app.data.db.StatEntity
import com.monarch.app.data.db.SyncStateDao
import com.monarch.app.data.db.SyncStateEntity
import com.monarch.app.data.db.TitleDao
import com.monarch.app.data.db.TitleUnlockEntity
import com.monarch.app.data.db.OwnedRelicEntity

@Database(
    entities = [
        ExerciseEntity::class,
        PresetEntity::class,
        PresetEntryEntity::class,
        SessionEntity::class,
        SetLogEntity::class,
        StatEntity::class,
        ProfileEntity::class,
        TitleUnlockEntity::class,
        SkillPracticeEntity::class,
        HealthDayEntity::class,
        MeasurementEntity::class,
        SyncStateEntity::class,
        IdleStateEntity::class,
        GachaStateEntity::class,
        OwnedCrestFrameEntity::class,
        OwnedRelicEntity::class,
    ],
    version = 21,
    exportSchema = false,
)
abstract class MonarchDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun presetDao(): PresetDao
    abstract fun sessionDao(): SessionDao
    abstract fun statDao(): StatDao
    abstract fun profileDao(): ProfileDao
    abstract fun titleDao(): TitleDao
    abstract fun skillPracticeDao(): SkillPracticeDao
    abstract fun healthDayDao(): HealthDayDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun idleDao(): IdleDao
    abstract fun gachaDao(): GachaDao

    companion object {
        // Height and sex move onto the profile (set once in Settings) so the
        // stat log no longer asks for height on every reading. heightCm is
        // backfilled from the newest stat row that actually carries one; with
        // no stats the column simply stays NULL and Settings prompts for it.
        // Column types must match ProfileEntity exactly or Room refuses to
        // open the database (no destructive fallback by design).
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN heightCm REAL")
                db.execSQL("ALTER TABLE profile ADD COLUMN sex TEXT NOT NULL DEFAULT 'MALE'")
                db.execSQL(
                    "UPDATE profile SET heightCm = " +
                        "(SELECT heightCm FROM stats WHERE heightCm > 0 " +
                        "ORDER BY takenAtMs DESC, id DESC LIMIT 1)",
                )
            }
        }

        // Equipped crest frame: which catalogue frame the hunter wears on
        // their crest. Nullable column, default NULL = nothing worn. Column
        // type must match GachaStateEntity.equippedFrame (String?) exactly.
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE gacha_state ADD COLUMN equippedFrame TEXT")
            }
        }

        // Relic vault: every relic a draw produced. The rate still uses the
        // strongest multiplier; this table is what lets the hunter SEE them.
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `owned_relics` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`multiplier` REAL NOT NULL, " +
                        "`drawnAtMs` INTEGER NOT NULL)",
                )
                // Backfill: a relic drawn before this table existed lives only
                // as idle_state.relicMultiplier. Without this the vault reads
                // empty while the army panel still shows the multiplier.
                db.execSQL(
                    "INSERT INTO `owned_relics` (`name`, `multiplier`, `drawnAtMs`) " +
                        "SELECT 'Nameless Relic', `relicMultiplier`, `lastCollectedAtMs` " +
                        "FROM `idle_state` WHERE `relicMultiplier` > 1.0",
                )
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Gacha: banked rolls (single row, mirroring idle_state) and
                // owned crest frames. Column types must match the entities
                // exactly or Room refuses to open the database (no destructive
                // fallback by design).
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gacha_state` (" +
                        "`id` INTEGER PRIMARY KEY NOT NULL, " +
                        "`rolls` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `gacha_state` " +
                        "(`id`, `rolls`) VALUES (1, 0)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `owned_crest_frames` (" +
                        "`frameId` TEXT PRIMARY KEY NOT NULL, " +
                        "`ownedAtMs` INTEGER NOT NULL)",
                )
            }
        }

        // Version 11 is the shipped baseline. Every future schema change
        // REQUIRES an explicit Migration registered via addMigrations(...):
        // Room must be allowed to throw on an unknown schema rather than
        // silently delete a user's training history (fallbackToDestructiveMigration
        // was removed for exactly that reason — an upgrade wiped all data).
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Feed fields: title and public note sync; privateNote is device-only.
                db.execSQL("ALTER TABLE sessions ADD COLUMN title TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sessions ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sessions ADD COLUMN privateNote TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Activity dimension: defaults keep every existing row a plain lifting entry.
                db.execSQL("ALTER TABLE exercises ADD COLUMN metric TEXT NOT NULL DEFAULT 'REPS'")
                db.execSQL("ALTER TABLE exercises ADD COLUMN category TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE set_logs ADD COLUMN durationSec INTEGER")
                db.execSQL("ALTER TABLE set_logs ADD COLUMN distanceM REAL")
                db.execSQL("ALTER TABLE set_logs ADD COLUMN grade TEXT")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Body measurements are device-only by design: the cloud schema
                // has no table for them, so there is no sync path to grow here.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `measurements` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`site` TEXT NOT NULL, " +
                        "`valueCm` REAL NOT NULL, " +
                        "`takenAtMs` INTEGER NOT NULL)",
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Measurement goals removed: sizes are tracked without targets.
                db.execSQL("DROP TABLE IF EXISTS measurement_goals")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Push watermark. Without it every cold start re-uploaded the
                // entire completed history on the first sync.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_state` (" +
                        "`sessionId` INTEGER PRIMARY KEY NOT NULL, " +
                        "`fingerprint` INTEGER NOT NULL)",
                )
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Idle game, single row. Column types must match IdleStateEntity
                // exactly or Room refuses to open the database (there is no
                // destructive fallback by design). Seed the row so the first
                // collect has a baseline; lastCollectedAtMs = 0 means the very
                // first collect pays a capped amount at the floor rate once.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `idle_state` (" +
                        "`id` INTEGER PRIMARY KEY NOT NULL, " +
                        "`essence` INTEGER NOT NULL, " +
                        "`shadows` INTEGER NOT NULL, " +
                        "`relicMultiplier` REAL NOT NULL, " +
                        "`lastCollectedAtMs` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `idle_state` " +
                        "(`id`, `essence`, `shadows`, `relicMultiplier`, `lastCollectedAtMs`) " +
                        "VALUES (1, 0, 0, 1.0, 0)",
                )
            }
        }

        fun create(context: Context): MonarchDatabase =
            Room.databaseBuilder(context, MonarchDatabase::class.java, "monarch.db")
                .addMigrations(
                    MIGRATION_11_12,
                    MIGRATION_12_13,
                    MIGRATION_13_14,
                    MIGRATION_14_15,
                    MIGRATION_15_16,
                    MIGRATION_16_17,
                    MIGRATION_17_18,
                    MIGRATION_18_19,
                    MIGRATION_19_20,
                    MIGRATION_20_21,
                )
                .build()
    }
}
