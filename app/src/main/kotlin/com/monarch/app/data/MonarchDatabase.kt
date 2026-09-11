package com.monarch.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 11,
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
        fun create(context: Context): MonarchDatabase =
            Room.databaseBuilder(context, MonarchDatabase::class.java, "monarch.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
