package com.monarch.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.monarch.app.data.db.ExerciseDao
import com.monarch.app.data.db.ExerciseEntity
import com.monarch.app.data.db.HealthDayDao
import com.monarch.app.data.db.HealthDayEntity
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
import com.monarch.app.data.db.TitleDao
import com.monarch.app.data.db.TitleUnlockEntity

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
    ],
    version = 13,
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

    companion object {
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

        fun create(context: Context): MonarchDatabase =
            Room.databaseBuilder(context, MonarchDatabase::class.java, "monarch.db")
                .addMigrations(MIGRATION_11_12, MIGRATION_12_13)
                .build()
    }
}
