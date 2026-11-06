package com.ironvellum.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "preparation_routines")
data class PreparationRoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val name: String,
    val stepsJson: String,
)

@Entity(tableName = "preset_preparations", primaryKeys = ["presetId", "kind"], foreignKeys = [
    ForeignKey(entity = PresetEntity::class, parentColumns = ["id"], childColumns = ["presetId"], onDelete = ForeignKey.CASCADE),
])
data class PresetPreparationEntity(val presetId: Long, val kind: String, val mode: String, val routineId: Long? = null)

@Entity(tableName = "session_preparations", primaryKeys = ["sessionId", "kind"], foreignKeys = [
    ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
])
data class SessionPreparationEntity(val sessionId: Long, val kind: String, val snapshotJson: String, val progressJson: String)
