package com.monarch.app.data
import androidx.room.withTransaction
import com.monarch.app.data.db.ExerciseDao
import com.monarch.app.data.db.ExerciseEntity
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
import com.monarch.app.data.db.SessionWithSets
import com.monarch.app.data.db.SetLogEntity
import com.monarch.app.data.db.SkillPracticeDao
import com.monarch.app.data.db.SkillPracticeEntity
import com.monarch.app.data.db.StatDao
import com.monarch.app.data.db.StatEntity
import com.monarch.app.data.db.SyncStateDao
import com.monarch.app.data.db.SyncStateEntity
import com.monarch.app.data.db.TitleDao
import com.monarch.app.data.db.TitleUnlockEntity
import com.monarch.app.data.db.GachaDao
import com.monarch.app.data.db.OwnedRelicEntity
import com.monarch.app.domain.ActivityScore
import com.monarch.app.domain.ArmyClass
import com.monarch.app.domain.Exercise
import com.monarch.app.domain.ExerciseHistory
import com.monarch.app.domain.ExerciseHistoryCalculator
import com.monarch.app.domain.ExerciseMetric
import com.monarch.app.domain.ExportReader
import com.monarch.app.domain.ExportWriter
import com.monarch.app.domain.Gacha
import com.monarch.app.domain.HealthDay
import com.monarch.app.domain.Idle
import com.monarch.app.domain.IdleRate
import com.monarch.app.domain.IdleState
import com.monarch.app.domain.MeasurementEntry
import com.monarch.app.domain.MeasurementSite
import com.monarch.app.domain.BodyLimits
import com.monarch.app.domain.MuscleGroup
import com.monarch.app.domain.PlayerProfile
import com.monarch.app.domain.Sex
import com.monarch.app.domain.PresetEntry
import com.monarch.app.domain.Progression
import com.monarch.app.domain.Reward
import com.monarch.app.domain.RollResult
import com.monarch.app.domain.SessionSet
import com.monarch.app.domain.SkillClaimResult
import com.monarch.app.domain.SkillPractice
import com.monarch.app.domain.Skills
import com.monarch.app.domain.StatEntry
import com.monarch.app.domain.StrengthIndex
import com.monarch.app.domain.TitleDef
import com.monarch.app.domain.Titles
import com.monarch.app.data.cloud.WireLimits
import com.monarch.app.domain.TrainingMode
import com.monarch.app.domain.UnlockedTitle
import com.monarch.app.domain.WorkoutPreset
import com.monarch.app.domain.WorkoutSession
import com.monarch.app.domain.Xp
import com.monarch.app.domain.Relics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull

class Repository(
    private val db: MonarchDatabase,
    private val health: HealthSync? = null,
) {

    private val exerciseDao = db.exerciseDao()
    private val presetDao = db.presetDao()
    private val sessionDao = db.sessionDao()
    private val statDao = db.statDao()
    private val profileDao = db.profileDao()
    private val titleDao = db.titleDao()
    private val skillPracticeDao = db.skillPracticeDao()
    private val healthDayDao: HealthDayDao = db.healthDayDao()
    private val measurementDao: MeasurementDao = db.measurementDao()
    private val syncStateDao: SyncStateDao = db.syncStateDao()
    private val idleDao: IdleDao = db.idleDao()
    private val gachaDao = db.gachaDao()
    // ---------------------------------------------------------------- seeding

    suspend fun ensureSeeded() {
        if (profileDao.get() == null) {
            profileDao.upsert(ProfileEntity(name = "Hunter", totalXp = 0, currentTitleId = null))
        }
        val existingNames = exerciseDao.observeAll().first().map { it.name }.toSet()
        val missing = Seed.exercises.filterNot { it.name in existingNames }
        if (missing.isNotEmpty()) {
            exerciseDao.insertAll(missing)
        }
        if (presetDao.count() == 0) {
            val idByName = exerciseDao.observeAll().first().associate { it.name to it.id }
            Seed.presets.forEach { spec ->
                val presetId = presetDao.insertPreset(
                    PresetEntity(name = spec.name, note = spec.note, scheduledDay = spec.scheduledDay),
                )
                presetDao.insertEntries(
                    spec.entries.mapIndexed { position, entry ->
                        PresetEntryEntity(
                            presetId = presetId,
                            exerciseId = idByName.getValue(entry.exercise),
                            targetSets = entry.sets,
                            targetReps = entry.reps,
                            targetWeightKg = entry.weightKg,
                            modifiers = entry.modifiers,
                            position = position,
                        )
                    },
                )
            }
        }
    }

    // ---------------------------------------------------------------- health history

    /** Per-day Health Connect activity history, newest first. */
    fun observeHealthDays(): Flow<List<HealthDay>> =
        healthDayDao.observeAll().map { list ->
            list.map {
                HealthDay(
                    date = LocalDate.ofEpochDay(it.epochDay),
                    steps = it.steps,
                    distanceKm = it.distanceKm,
                    activeKcal = it.activeKcal,
                    sleepMinutes = it.sleepMinutes,
                    restingHr = it.restingHr,
                )
            }
        }

    /**
     * Pull the last [days] days from Health Connect into the local cache.
     * Reports days actually written plus any metric that could not be read, so
     * the UI never claims a successful sync it did not get.
     */
    suspend fun syncHealthHistory(days: Int = 90): HealthSync.HistoryRead {
        val health = health ?: return HealthSync.HistoryRead(
            problems = listOf("Health Connect not wired"),
        )
        if (!health.available()) return HealthSync.HistoryRead(
            problems = listOf("Health Connect unavailable"),
        )
        val read = runCatching { health.readDailyHistory(days) }
            .getOrElse { HealthSync.HistoryRead(problems = listOf("read failed: ${it.javaClass.simpleName}")) }
        healthDayDao.purgeEmpty()
        if (read.days.isNotEmpty()) {
            healthDayDao.upsertAll(
                read.days.map {
                    HealthDayEntity(
                        epochDay = it.date.toEpochDay(),
                        steps = it.steps,
                        distanceKm = it.distanceKm,
                        activeKcal = it.activeKcal,
                        sleepMinutes = it.sleepMinutes,
                        restingHr = it.restingHr,
                    )
                },
            )
        }
        importBodyReadings(read.bodyReadings)
        return read
    }

    /**
     * Writes Health Connect weigh-ins into the stat history, skipping days the
     * history already covers so repeat syncs never duplicate a reading.
     * Height comes from the profile (Settings); when it is unset the row uses
     * the 0.0 sentinel — weight history still matters on its own.
     */
    private suspend fun importBodyReadings(readings: List<HealthSync.BodyReading>): Int {
        if (readings.isEmpty()) return 0
        val zone = java.time.ZoneId.systemDefault()
        val existing = statDao.observeAll().first()
        val coveredDays = existing.map {
            java.time.Instant.ofEpochMilli(it.takenAtMs).atZone(zone).toLocalDate()
        }.toSet()
        val height = profileDao.get()?.heightCm ?: 0.0
        val fresh = readings.filterNot { it.date in coveredDays }
        fresh.forEach {
            statDao.insert(
                StatEntity(
                    takenAtMs = it.takenAtMs,
                    weightKg = it.weightKg,
                    heightCm = height,
                    bodyFatPct = it.bodyFatPct,
                ),
            )
        }
        return fresh.size
    }


    /**
     * Metric and category live on the Exercise, not the set, so the ledger
     * needs the whole catalogue to tell a run from a set of pull-ups.
     */
    private suspend fun exerciseCatalogue(): Map<Long, Exercise> =
        exerciseDao.observeAll().first().associate { it.id to it.toDomain() }

    // ---------------------------------------------------------------- mapping

    /**
     * An unrecognised group must never take the app down: a seed typo or an
     * archive from a newer build crashed every screen that reads the catalogue,
     * because valueOf throws on the way out of the database.
     */
    private fun ExerciseEntity.toDomain() = Exercise(
        id = id,
        name = name,
        muscleGroup = MuscleGroup.entries.firstOrNull { it.name == muscleGroup } ?: MuscleGroup.CORE,
        isWeighted = isWeighted,
        metric = ExerciseMetric.entries.firstOrNull { it.name == metric } ?: ExerciseMetric.REPS,
        category = category,
    )

    // ---------------------------------------------------------------- exercises

    fun observeExercises(): Flow<List<Exercise>> =
        exerciseDao.observeAll().map { list -> list.map { it.toDomain() } }

    // ---------------------------------------------------------------- presets

    fun observePresets(): Flow<List<WorkoutPreset>> = combine(
        presetDao.observePresets(),
        exerciseDao.observeAll(),
    ) { presets, exercises ->
        val names = exercises.associate { it.id to it.name }
        presets.map { pw ->
            WorkoutPreset(
                id = pw.preset.id,
                name = pw.preset.name,
                note = pw.preset.note,
                scheduledDay = pw.preset.scheduledDay,
                entries = pw.entries.sortedBy { it.position }.map { e ->
                    PresetEntry(
                        id = e.id,
                        exerciseId = e.exerciseId,
                        exerciseName = names[e.exerciseId] ?: "Unknown",
                        targetSets = e.targetSets,
                        targetReps = e.targetReps,
                        targetWeightKg = e.targetWeightKg,
                        modifiers = e.modifiers,
                        position = e.position,
                    )
                },
            )
        }
    }

    data class PresetDraftEntry(
        val exerciseId: Long,
        val targetSets: Int,
        val targetReps: Int,
        val targetWeightKg: Double?,
        val modifiers: String = "",
    )

    suspend fun savePreset(
        presetId: Long?,
        name: String,
        note: String,
        scheduledDay: Int?,
        entries: List<PresetDraftEntry>,
    ): Long = db.withTransaction {
        val id = if (presetId == null) {
            presetDao.insertPreset(PresetEntity(name = name, note = note, scheduledDay = scheduledDay))
        } else {
            presetDao.updatePreset(PresetEntity(id = presetId, name = name, note = note, scheduledDay = scheduledDay))
            presetDao.clearEntries(presetId)
            presetId
        }
        presetDao.insertEntries(
            entries.mapIndexed { position, e ->
                PresetEntryEntity(
                    presetId = id,
                    exerciseId = e.exerciseId,
                    targetSets = e.targetSets,
                    targetReps = e.targetReps,
                    targetWeightKg = e.targetWeightKg,
                    modifiers = e.modifiers,
                    position = position,
                )
            },
        )
        id
    }

    suspend fun deletePreset(presetId: Long) = presetDao.deletePreset(presetId)

    // ---------------------------------------------------------------- sessions

    suspend fun startSessionFromPreset(presetId: Long): Long = db.withTransaction {
        val pw = presetDao.presetWithEntries(presetId) ?: error("Preset $presetId not found")
        val sessionId = sessionDao.insertSession(
            SessionEntity(
                presetId = presetId,
                label = pw.preset.name,
                startedAtMs = System.currentTimeMillis(),
                completedAtMs = null,
                xpAwarded = 0,
            ),
        )
        val mode = profileDao.get()?.trainingMode?.let {
            runCatching { TrainingMode.valueOf(it) }.getOrDefault(TrainingMode.STRENGTH)
        } ?: TrainingMode.STRENGTH
        val exerciseById = exerciseDao.let { dao -> pw.entries.map { it.exerciseId }.distinct().mapNotNull { dao.byId(it) } }
            .associateBy { it.id }

        val sets = pw.entries.sortedBy { it.position }.flatMapIndexed { entryPos, entry ->
            val exercise = exerciseById[entry.exerciseId]
            val recent = sessionDao.recentDoneSets(entry.exerciseId)
            val latestSessionId = recent.firstOrNull()?.sessionId
            val lastAttempts = recent.takeWhile { it.sessionId == latestSessionId }
                .sortedBy { it.setIndex }
                .map { Progression.Attempt(it.weightKg, it.reps) }
            val recommendation = Progression.fromSets(
                mode = mode,
                targetReps = entry.targetReps,
                minSets = entry.targetSets,
                sets = lastAttempts,
                muscleGroup = exercise?.muscleGroup ?: "",
                exerciseName = exercise?.name ?: "",
            )
            (0 until entry.targetSets).map { index ->
                SetLogEntity(
                    sessionId = sessionId,
                    exerciseId = entry.exerciseId,
                    exercisePosition = entryPos,
                    setIndex = index,
                    reps = recommendation.reps,
                    modifiers = entry.modifiers,
                    weightKg = recommendation.weightKg ?: entry.targetWeightKg,
                    done = false,
                )
            }
        }
        sessionDao.insertSets(sets)
        sessionId
    }

    suspend fun startFreeformSession(label: String): Long =
        sessionDao.insertSession(
            SessionEntity(
                presetId = null,
                label = label.ifBlank { "Freeform" },
                startedAtMs = System.currentTimeMillis(),
                completedAtMs = null,
                xpAwarded = 0,
            ),
        )

    fun observeSession(sessionId: Long): Flow<WorkoutSession?> =
        sessionDao.observeSession(sessionId).map { it?.toDomain() }

    fun observeSessionSets(sessionId: Long): Flow<List<SessionSet>> = combine(
        sessionDao.observeSets(sessionId),
        exerciseDao.observeAll(),
    ) { sets, exercises ->
        val names = exercises.associate { it.id to it.name }
        sets.map { s ->
            SessionSet(
                id = s.id,
                exerciseId = s.exerciseId,
                exerciseName = names[s.exerciseId] ?: "Unknown",
                exercisePosition = s.exercisePosition,
                setIndex = s.setIndex,
                reps = s.reps,
                weightKg = s.weightKg,
                modifiers = s.modifiers,
                done = s.done,
                durationSec = s.durationSec,
                distanceM = s.distanceM,
                grade = s.grade,
            )
        }.sortedWith(compareBy({ it.exercisePosition }, { it.setIndex }))
    }

    suspend fun updateSet(
        setId: Long,
        reps: Int,
        weightKg: Double?,
        done: Boolean,
        durationSec: Int? = null,
        distanceM: Double? = null,
        grade: String? = null,
    ) {
        val current = sessionDao.setById(setId) ?: return
        sessionDao.updateSet(
            current.copy(
                reps = reps,
                weightKg = weightKg,
                done = done,
                durationSec = durationSec,
                distanceM = distanceM,
                grade = grade,
            ),
        )
    }
    suspend fun addExtraSet(sessionId: Long, exerciseId: Long, reps: Int, weightKg: Double?, modifiers: String): Long {
        val index = sessionDao.setsFor(sessionId).count { it.exerciseId == exerciseId }
        val existing = sessionDao.setsFor(sessionId).firstOrNull { it.exerciseId == exerciseId }
        return sessionDao.insertSets(
            listOf(
                SetLogEntity(
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    exercisePosition = existing?.exercisePosition ?: 99,
                    setIndex = index,
                    reps = reps,
                    weightKg = weightKg,
                    modifiers = existing?.modifiers ?: modifiers,
                    done = false,
                ),
            ),
        ).first()
    }

    /**
     * Removes a set and closes the gap in setIndex — leaving holes would break
     * ordering and the "Set N" labels. The last set of an exercise is kept so a
     * movement never silently vanishes mid-session; remove the exercise instead.
     * Completed sessions are immutable: their XP and strength are already banked.
     */
    suspend fun removeSet(setId: Long): Boolean = db.withTransaction {
        val set = sessionDao.setById(setId) ?: return@withTransaction false
        val session = sessionDao.byId(set.sessionId) ?: return@withTransaction false
        if (session.completedAtMs != null) return@withTransaction false
        val siblings = sessionDao.setsFor(set.sessionId).filter { it.exerciseId == set.exerciseId }
        if (siblings.size <= 1) return@withTransaction false
        sessionDao.deleteSet(setId)
        siblings.asSequence()
            .filter { it.id != setId }
            .sortedBy { it.setIndex }
            .forEachIndexed { index, row ->
                if (row.setIndex != index) sessionDao.updateSet(row.copy(setIndex = index))
            }
        true
    }

    /**
     * Modifiers describe the movement, so editing them applies to every set of
     * that exercise — including ones already ticked off, which is the point:
     * you realise mid-session that you were working at a deficit all along.
     */
    suspend fun setExerciseModifiers(sessionId: Long, exerciseId: Long, modifiers: String) {
        val session = sessionDao.byId(sessionId) ?: return
        if (session.completedAtMs != null) return
        sessionDao.setModifiers(sessionId, exerciseId, modifiers)
    }

    suspend fun abandonSession(sessionId: Long) = sessionDao.deleteAbandoned(sessionId)

    data class CompletionResult(
        val xpAwarded: Int,
        val levelBefore: Int,
        val levelAfter: Int,
        val classBefore: String,
        val classAfter: String,
        val newTitles: List<TitleDef>,
        val totalXp: Long,
        val questBonus: Boolean,
        val durationMinutes: Long,
    )

    suspend fun completeSession(sessionId: Long): CompletionResult = db.withTransaction {
        val session = sessionDao.byId(sessionId) ?: error("Session $sessionId not found")
        check(session.completedAtMs == null) { "Session already completed" }
        val doneSets = sessionDao.setsFor(sessionId).filter { it.done }
        val latestBodyweight = statDao.observeAll().first().firstOrNull()?.weightKg
        // Split by metric: lifting XP keeps its existing curve; activities earn
        // XP from ActivityScore and contribute NOTHING to the strength score.
        val metrics = exerciseDao.observeAll().first().associate { it.id to it.metric }
        val liftingSets = doneSets.filter { (metrics[it.exerciseId] ?: "REPS") == "REPS" }
        val activitySets = doneSets.filter { (metrics[it.exerciseId] ?: "REPS") != "REPS" }
        val liftingXp = Xp.award(liftingSets.size, liftingSets.sumOf { it.reps })
        val activityXp = activitySets.sumOf { s ->
            val metric = runCatching { ExerciseMetric.valueOf(metrics[s.exerciseId] ?: "REPS") }
                .getOrDefault(ExerciseMetric.REPS)
            val per = ActivityScore.xp(metric, s.durationSec, s.distanceM, s.weightKg, latestBodyweight ?: 0.0)
            if (metric == ExerciseMetric.ATTEMPTS_GRADE) per * s.reps else per
        }
        val xp = liftingXp + activityXp
        val sessionStrength = StrengthIndex.sessionScore(
            liftingSets.map { it.reps to it.weightKg },
            latestBodyweight,
        ) ?: 0

        // Quest bonus: completing the preset scheduled for today.
        val today = LocalDate.now().dayOfWeek.value
        val questBonus = session.presetId?.let { pid ->
            presetDao.presetWithEntries(pid)?.preset?.scheduledDay == today
        } == true
        val totalXpGain = xp + if (questBonus) Xp.QUEST_BONUS else 0

        val before = profileDao.get() ?: error("Profile missing")
        val levelBefore = Xp.levelFor(before.totalXp)
        val newTotal = before.totalXp + totalXpGain

        val lifetimeStrength = before.lifetimeStrength + sessionStrength
        profileDao.setLifetimeStrength(lifetimeStrength)

        val finishedAt = System.currentTimeMillis()
        val durationMinutes = ((finishedAt - session.startedAtMs) / 60000L).coerceAtLeast(1)
        sessionDao.updateSession(
            session.copy(completedAtMs = finishedAt, xpAwarded = totalXpGain, strengthScore = sessionStrength),
        )
        profileDao.addXp(totalXpGain.toLong())


        // Full ledger: a partial one here made every step/skill title dead.
        val ledger = Titles.ledgerOf(
            totalXp = newTotal,
            history = observeHistory().first(),
            healthDays = observeHealthDays().first(),
            practices = observeSkillPractices().first(),
            exercises = exerciseCatalogue(),
        )
        val newly = Titles.newlyUnlocked(ledger, titleDao.heldIds().toSet())
        val now = finishedAt
        titleDao.insertAll(newly.map { TitleUnlockEntity(it.id, now) })
        profileDao.setCurrentTitle(newly.firstOrNull()?.id ?: before.currentTitleId)

        // A level-up from ANY source banks shadow draws — workout XP included.
        // Without this, levelling through sessions never paid out at all.
        val levelsGained = Xp.levelFor(newTotal) - levelBefore
        if (levelsGained > 0) {
            val g = gachaDao.get() ?: GachaStateEntity()
            gachaDao.upsert(g.copy(rolls = g.rolls + levelsGained))
        }

        CompletionResult(
            xpAwarded = totalXpGain,
            levelBefore = levelBefore,
            levelAfter = Xp.levelFor(newTotal),
            classBefore = ArmyClass.forLevel(levelBefore).title,
            classAfter = ArmyClass.forLevel(Xp.levelFor(newTotal)).title,
            newTitles = newly,
            totalXp = newTotal,
            questBonus = questBonus,
            durationMinutes = durationMinutes,
        )
    }

    fun observeRecentSessions(limit: Int = 5): Flow<List<WorkoutSession>> =
        sessionDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    /** Full activity log: every completed session with its sets. */
    fun observeHistory(): Flow<List<Pair<WorkoutSession, List<SessionSet>>>> = combine(
        sessionDao.observeCompletedWithSets(),
        exerciseDao.observeAll(),
    ) { sessions, exercises ->
        val names = exercises.associate { it.id to it.name }
        sessions.map { sws ->
            sws.session.toDomain() to sws.sets.map { s ->
                SessionSet(
                    id = s.id,
                    exerciseId = s.exerciseId,
                    exerciseName = names[s.exerciseId] ?: "Unknown",
                    // Without this every set defaulted to position 0, so the
                    // session record grouped a five-exercise workout under one
                    // heading with SET 1 repeating once per exercise.
                    exercisePosition = s.exercisePosition,
                    setIndex = s.setIndex,
                    reps = s.reps,
                    weightKg = s.weightKg,
                    modifiers = s.modifiers,
                    done = s.done,
                    durationSec = s.durationSec,
                    distanceM = s.distanceM,
                    grade = s.grade,
                )
            }
        }
    }

    /** Complete record of one movement: every logged set plus derived stats. */
    fun observeExerciseHistory(exerciseId: Long): Flow<ExerciseHistory?> = combine(
        sessionDao.observeExerciseSets(exerciseId),
        exerciseDao.observeAll(),
        statDao.observeAll(),
    ) { rows, exercises, stats ->
        val exercise = exercises.firstOrNull { it.id == exerciseId }?.toDomain() ?: return@combine null
        val bodyweight = stats.firstOrNull()?.weightKg
        ExerciseHistoryCalculator.build(exercise, rows, bodyweight)
    }


    /**
     * Server caps: title <= 80, public note <= 500 (see supabase migration 0002).
     * Trimming locally means a sync can never fail on a value we already accepted.
     * The private note has no server constraint — it never leaves the device.
     */
    suspend fun setSessionTitle(sessionId: Long, title: String) {
        sessionDao.setTitle(sessionId, title.trim().take(WireLimits.SESSION_TITLE_MAX))
    }

    suspend fun setSessionNote(sessionId: Long, note: String) {
        sessionDao.setNote(sessionId, note.trim().take(WireLimits.SESSION_NOTE_MAX))
    }

    suspend fun setSessionPrivateNote(sessionId: Long, privateNote: String) {
        sessionDao.setPrivateNote(sessionId, privateNote)
    }

    private fun SessionEntity.toDomain() = WorkoutSession(
        id = id,
        presetId = presetId,
        label = label,
        startedAtMs = startedAtMs,
        completedAtMs = completedAtMs,
        xpAwarded = xpAwarded,
        strengthScore = strengthScore,
        title = title,
        note = note,
        privateNote = privateNote,
    )

    // ---------------------------------------------------------------- stats

    fun observeStats(): Flow<List<StatEntry>> =
        statDao.observeAll().map { list ->
            list.map { StatEntry(it.id, it.takenAtMs, it.weightKg, it.heightCm, it.bodyFatPct) }
        }

    /**
     * Height is profile-owned (Settings), so the caller never supplies it: the
     * profile height is stamped onto the row. With no height on record the row
     * still lands (weight history stands alone) using the 0.0 sentinel, which
     * every BMI/FFMI consumer already guards via BodyStats' <= 0 checks.
     */
    /**
     * Defence in depth: the screens gate on [BodyLimits], and so does this —
     * an archive import or a future caller must not be able to write a figure
     * the maths cannot survive.
     */
    suspend fun addStat(weightKg: Double, bodyFatPct: Double?) {
        require(BodyLimits.validWeight(weightKg)) { "weight $weightKg kg is outside ${BodyLimits.WEIGHT_KG}" }
        require(BodyLimits.validBodyFat(bodyFatPct)) { "body fat $bodyFatPct% is outside ${BodyLimits.BODY_FAT_PCT}" }
        val heightCm = profileDao.get()?.heightCm ?: 0.0
        statDao.insert(
            StatEntity(
                takenAtMs = System.currentTimeMillis(),
                weightKg = weightKg,
                heightCm = heightCm,
                bodyFatPct = bodyFatPct,
            ),
        )
    }

    suspend fun deleteStat(id: Long) = statDao.delete(id)

    // ---------------------------------------------------------------- measurements
    // Device-only by design: the cloud schema has no measurement table, so
    // nothing here may grow a sync path.

    fun observeMeasurements(): Flow<List<MeasurementEntry>> =
        measurementDao.observeAll().map { list ->
            list.mapNotNull { e ->
                // Unknown site strings must never crash the app � drop the row.
                val site = MeasurementSite.entries.firstOrNull { it.name == e.site }
                    ?: return@mapNotNull null
                MeasurementEntry(id = e.id, site = site, valueCm = e.valueCm, takenAtMs = e.takenAtMs)
            }
        }

    suspend fun logMeasurement(site: MeasurementSite, valueCm: Double) {
        measurementDao.insert(
            MeasurementEntity(site = site.name, valueCm = valueCm, takenAtMs = System.currentTimeMillis()),
        )
    }

    suspend fun deleteMeasurement(id: Long) = measurementDao.delete(id)

    // ------------------------------------------------------------- sync state

    /** Fingerprints of the sessions the last successful push uploaded. */
    suspend fun pushWatermark(): Map<Long, Int> =
        syncStateDao.all().associate { it.sessionId to it.fingerprint }

    /** Called only after a push succeeds; prunes sessions deleted since. */
    suspend fun recordPushWatermark(fingerprints: Map<Long, Int>) {
        syncStateDao.upsertAll(fingerprints.map { SyncStateEntity(it.key, it.value) })
        syncStateDao.pruneExcept(fingerprints.keys.toList())
    }

    // ---------------------------------------------------------------- profile & titles

    fun observeProfile(): Flow<PlayerProfile?> =
        profileDao.observe().map {
            it?.let { p ->
                PlayerProfile(
                    name = p.name,
                    totalXp = p.totalXp,
                    currentTitleId = p.currentTitleId,
                    // An unknown stored mode must not kill every screen that
                    // observes the profile; line 321 already reads it this way.
                    trainingMode = runCatching { TrainingMode.valueOf(p.trainingMode) }
                        .getOrDefault(TrainingMode.STRENGTH),
                    inkStyle = p.inkStyle,
                )
            }
        }

    suspend fun setTrainingMode(mode: TrainingMode) = profileDao.setTrainingMode(mode.name)

    /** Hand-drawn chrome on or off; mirrored into InkStyle so draw code can read it. */
    fun observeInkStyle(): Flow<Boolean> = profileDao.observe().map { it?.inkStyle ?: false }

    suspend fun setInkStyle(on: Boolean) = profileDao.setInkStyle(on)

    /**
     * Body profile (height + sex) for Settings and the stat-log estimator.
     * Unknown stored sex strings fall back to MALE rather than crashing.
     */
    fun observeBodyProfile(): Flow<Pair<Double?, Sex>> =
        profileDao.observe().map { p ->
            val sex = p?.sex?.let { s -> runCatching { Sex.valueOf(s) }.getOrNull() } ?: Sex.MALE
            p?.heightCm to sex
        }

    suspend fun setHeight(heightCm: Double) {
        require(BodyLimits.validHeight(heightCm)) { "height $heightCm cm is outside ${BodyLimits.HEIGHT_CM}" }
        profileDao.setHeight(heightCm)
    }

    suspend fun setSex(sex: Sex) = profileDao.setSex(sex.name)

    fun observeUnlockedTitles(): Flow<List<UnlockedTitle>> =
        titleDao.observeAll().map { list -> list.map { UnlockedTitle(it.titleId, it.unlockedAtMs) } }

    suspend fun equipTitle(titleId: String?) {
        require(titleId == null || Titles.byId(titleId) != null) { "Unknown title $titleId" }
        profileDao.setCurrentTitle(titleId)
    }

    /**
     * Unlocks every title the current ledger already satisfies. Awards used to
     * happen only on session completion with a partial ledger, so anything
     * driven by steps, activity or skills could never fire; this also repairs
     * accounts whose data arrived by import or health sync.
     */
    suspend fun reconcileTitles(): List<TitleDef> {
        val ledger = currentLedger()
        val newly = Titles.newlyUnlocked(ledger, titleDao.heldIds().toSet())
        if (newly.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        titleDao.insertAll(newly.map { TitleUnlockEntity(it.id, now) })
        if (profileDao.get()?.currentTitleId == null) {
            profileDao.setCurrentTitle(newly.first().id)
        }
        // Every earned title gets its moment: reconciliation runs before any UI
        // exists, so the awards wait here until a screen can show them.
        _pendingCelebrations.value = _pendingCelebrations.value + newly
        return newly
    }

    private val _pendingCelebrations = MutableStateFlow<List<TitleDef>>(emptyList())

    /** Titles unlocked outside a session (startup/health sync), awaiting their screen. */
    val pendingCelebrations: StateFlow<List<TitleDef>> = _pendingCelebrations.asStateFlow()

    fun clearPendingCelebrations() {
        _pendingCelebrations.value = emptyList()
    }

    /**
     * Every done activity set from completed sessions, for the title ledger:
     * lifetime minutes, distance, distinct activities, best runs/swims, hardest
     * grade and sport sessions are all derivable from this one read.
     */
    suspend fun completedActivitySets() = sessionDao.completedActivitySets()

    /** Ledger over everything logged: sessions, health days and skill practice. */
    suspend fun currentLedger(): Titles.Ledger = Titles.ledgerOf(
        totalXp = profileDao.get()?.totalXp ?: 0L,
        history = observeHistory().first(),
        healthDays = observeHealthDays().first(),
        practices = observeSkillPractices().first(),
        exercises = exerciseCatalogue(),
    )

    // ---------------------------------------------------------------- skills

    fun observeSkillPractices(): Flow<List<SkillPractice>> =
        skillPracticeDao.observeAll().map { list ->
            list.map {
                SkillPractice(
                    skillName = it.skillName,
                    practicedAtMs = it.practicedAtMs,
                    claimed = it.claimed,
                    value = it.value,
                    weightKg = it.weightKg,
                )
            }
        }

    /**
     * A measured practice attempt toward the standard — records what was
     * actually achieved (seconds, reps or metres) and any added load. No XP,
     * no mastery: claiming is a separate, deliberate act.
     */
    suspend fun logSkillPractice(skillName: String, value: Int, weightKg: Double? = null) {
        require(Skills.forName(skillName) != null) { "Unknown skill $skillName" }
        require(value >= 0) { "Practice value cannot be negative" }
        skillPracticeDao.insert(
            SkillPracticeEntity(
                skillName = skillName,
                practicedAtMs = System.currentTimeMillis(),
                value = value,
                weightKg = weightKg,
            ),
        )
    }

    /** Claiming mastery: awards skill XP, may level you up and unlock titles. */
    suspend fun claimSkill(skillName: String): SkillClaimResult = db.withTransaction {
        val def = Skills.forName(skillName) ?: error("Unknown skill $skillName")
        check(skillPracticeDao.claim(skillName) == null) { "$skillName already mastered" }
        val now = System.currentTimeMillis()
        skillPracticeDao.insert(
            SkillPracticeEntity(skillName = skillName, practicedAtMs = now, claimed = true),
        )
        val before = profileDao.get() ?: error("Profile missing")
        val levelBefore = Xp.levelFor(before.totalXp)
        val newTotal = before.totalXp + def.xp
        profileDao.addXp(def.xp.toLong())
        val ledger = Titles.ledgerOf(
            totalXp = newTotal,
            history = observeHistory().first(),
            healthDays = observeHealthDays().first(),
            practices = observeSkillPractices().first(),
            exercises = exerciseCatalogue(),
        )
        val newly = Titles.newlyUnlocked(ledger, titleDao.heldIds().toSet())
        titleDao.insertAll(newly.map { TitleUnlockEntity(it.id, now) })
        if (newly.isNotEmpty()) {
            profileDao.setCurrentTitle(newly.first().id)
        }
        SkillClaimResult(
            skill = def,
            xpAwarded = def.xp,
            levelBefore = levelBefore,
            levelAfter = Xp.levelFor(newTotal),
            totalXp = newTotal,
            newTitles = newly,
            unlockedNext = Skills.unlockedBy(skillName),
        )
    }

    /** Undo an accidental claim: removes mastery and takes the XP back. */
    suspend fun unclaimSkill(skillName: String) = db.withTransaction {
        val def = Skills.forName(skillName) ?: error("Unknown skill $skillName")
        if (skillPracticeDao.claim(skillName) == null) return@withTransaction
        skillPracticeDao.deleteClaims(skillName)
        profileDao.addXp(-def.xp.toLong())
    }

    suspend fun rename(name: String) {
        require(name.isNotBlank()) { "Name cannot be blank" }
        profileDao.setName(name.trim().take(WireLimits.DISPLAY_NAME_MAX))
    }

    // ---------------------------------------------------------------- export

    suspend fun exportJson(): String {
        val profile = profileDao.get()?.let {
            PlayerProfile(
                it.name,
                it.totalXp,
                it.currentTitleId,
                // Export must not be the one action a bad stored mode can kill:
                // it is how a hunter rescues their data.
                runCatching { TrainingMode.valueOf(it.trainingMode) }
                    .getOrDefault(TrainingMode.STRENGTH),
                it.inkStyle,
            )
        } ?: PlayerProfile()
        val names = exerciseDao.observeAll().first().associate { it.id to it.name }
        val presets = presetDao.observePresets().first().map { pw ->
            WorkoutPreset(
                id = pw.preset.id,
                name = pw.preset.name,
                note = pw.preset.note,
                scheduledDay = pw.preset.scheduledDay,
                entries = pw.entries.sortedBy { it.position }.map { e ->
                    PresetEntry(
                        id = e.id,
                        exerciseId = e.exerciseId,
                        exerciseName = names[e.exerciseId] ?: "Unknown",
                        targetSets = e.targetSets,
                        targetReps = e.targetReps,
                        targetWeightKg = e.targetWeightKg,
                        modifiers = e.modifiers,
                        position = e.position,
                    )
                },
            )
        }
        val sessions = sessionDao.observeCompletedWithSets().first().map { sws ->
            sws.session.toDomain() to sws.sets.map { s ->
                SessionSet(
                    id = s.id,
                    exerciseId = s.exerciseId,
                    exerciseName = names[s.exerciseId] ?: "Unknown",
                    setIndex = s.setIndex,
                    reps = s.reps,
                    weightKg = s.weightKg,
                    modifiers = s.modifiers,
                    done = s.done,
                    durationSec = s.durationSec,
                    distanceM = s.distanceM,
                    grade = s.grade,
                )
            }
        }
        val stats = statDao.observeAll().first().map { StatEntry(it.id, it.takenAtMs, it.weightKg, it.heightCm, it.bodyFatPct) }
        val skills = skillPracticeDao.observeAll().first().map {
            SkillPractice(it.skillName, it.practicedAtMs, it.claimed, it.value, it.weightKg)
        }
        val healthDays = healthDayDao.observeAll().first().map {
            HealthDay(
                date = LocalDate.ofEpochDay(it.epochDay),
                steps = it.steps,
                distanceKm = it.distanceKm,
                activeKcal = it.activeKcal,
                sleepMinutes = it.sleepMinutes,
                restingHr = it.restingHr,
            )
        }
        val titles = titleDao.observeAll().first().map { UnlockedTitle(it.titleId, it.unlockedAtMs) }
        val measurements = measurementDao.observeAll().first().mapNotNull { e ->
            val site = MeasurementSite.entries.firstOrNull { it.name == e.site } ?: return@mapNotNull null
            MeasurementEntry(id = e.id, site = site, valueCm = e.valueCm, takenAtMs = e.takenAtMs)
        }
        return ExportWriter.write(
            profile = profile,
            trainingMode = profile.trainingMode,
            presets = presets,
            sessions = sessions,
            stats = stats,
            titles = titles,
            skills = skills,
            healthDays = healthDays,
            measurements = measurements,
            exportedAtMs = System.currentTimeMillis(),
        )
    }

    data class ImportResult(
        val presets: Int,
        val sessions: Int,
        val sets: Int,
        val stats: Int,
        val titles: Int,
        val skills: Int,
        val healthDays: Int,
        val measurements: Int = 0,
        val problems: List<String>,
    )

    /**
     * Full restore from an export archive: wipes user data and replays the
     * archive inside one transaction, so a mid-way failure cannot leave a
     * half-restored database. The seeded exercise catalogue survives; archive
     * exercises are matched by NAME (ids differ between installs) and created
     * when missing.
     */
    suspend fun importArchive(json: String): Result<ImportResult> = Result.runCatching {
        val archive = ExportReader.read(json).getOrThrow()
        db.withTransaction {
                // User data goes; the seeded exercise catalogue stays.
                presetDao.clearAll()
                sessionDao.clearAll()
                statDao.clearAll()
                titleDao.clearAll()
                skillPracticeDao.clearAll()
                healthDayDao.clearAll()
                measurementDao.clearAll()

                // The archive carries no height or sex (device-only profile
                // fields), so the local values survive a restore.
                val local = profileDao.get()
                profileDao.upsert(
                    ProfileEntity(
                        name = archive.profile.name,
                        totalXp = archive.profile.totalXp,
                        currentTitleId = archive.profile.currentTitleId,
                        trainingMode = archive.trainingMode.name,
                        heightCm = local?.heightCm,
                        sex = local?.sex ?: "MALE",
                    ),
                )

                // Resolve exercises by name: reuse the seeded catalogue entry on a
                // case-insensitive hit, otherwise create the movement. The archive
                // carries no muscle group or weighted flag, so new rows get
                // defaults and every guess is reported, never silent.
                val exerciseIdByName = mutableMapOf<String, Long>()
                val problems = mutableListOf<String>()
                suspend fun resolveExercise(rawName: String): Long? {
                    val name = rawName.trim()
                    if (name.isEmpty()) return null
                    exerciseIdByName[name.lowercase()]?.let { return it }
                    val existing = exerciseDao.byName(name)
                    if (existing != null) {
                        exerciseIdByName[name.lowercase()] = existing.id
                        return existing.id
                    }
                    val guessedMuscleGroup = MuscleGroup.PULL.name
                    val newId = exerciseDao.insertAll(
                        listOf(ExerciseEntity(name = name, muscleGroup = guessedMuscleGroup, isWeighted = true)),
                    ).first()
                    exerciseIdByName[name.lowercase()] = newId
                    problems.add(
                        "exercise \"$name\" not in catalogue — created with guessed " +
                            "muscleGroup=$guessedMuscleGroup and isWeighted=true",
                    )
                    return newId
                }

                var restoredSets = 0
                val presetIdByOldId = mutableMapOf<Long, Long>()
                archive.presets.forEach { preset ->
                    val newPresetId = presetDao.insertPreset(
                        PresetEntity(name = preset.name, note = preset.note, scheduledDay = preset.scheduledDay),
                    )
                    preset.id.takeIf { it > 0 }?.let { presetIdByOldId[it] = newPresetId }
                    presetDao.insertEntries(
                        preset.entries.sortedBy { it.position }.mapNotNull { entry ->
                            val exerciseId = resolveExercise(entry.exerciseName)
                            if (exerciseId == null) {
                                problems.add("preset \"${preset.name}\" entry dropped: no exercise name")
                                return@mapNotNull null
                            }
                            PresetEntryEntity(
                                presetId = newPresetId,
                                exerciseId = exerciseId,
                                targetSets = entry.targetSets,
                                targetReps = entry.targetReps,
                                targetWeightKg = entry.targetWeightKg,
                                modifiers = entry.modifiers,
                                position = entry.position,
                            )
                        },
                    )
                }

                archive.sessions.forEach { (session, sets) ->
                    val newSessionId = sessionDao.insertSession(
                        SessionEntity(
                            presetId = session.presetId?.let { presetIdByOldId[it] },
                            label = session.label,
                            startedAtMs = session.startedAtMs,
                            completedAtMs = session.completedAtMs,
                            xpAwarded = session.xpAwarded,
                            strengthScore = session.strengthScore,
                            title = session.title,
                            note = session.note,
                            privateNote = session.privateNote,
                        ),
                    )
                    restoredSets += sessionDao.insertSets(
                        sets.mapNotNull { s ->
                            val exerciseId = resolveExercise(s.exerciseName)
                            if (exerciseId == null) {
                                problems.add(
                                    "session \"${session.label}\" set dropped: no exercise name",
                                )
                                return@mapNotNull null
                            }
                            SetLogEntity(
                                sessionId = newSessionId,
                                exerciseId = exerciseId,
                                exercisePosition = s.exercisePosition,
                                setIndex = s.setIndex,
                                reps = s.reps,
                                weightKg = s.weightKg,
                                modifiers = s.modifiers,
                                done = s.done,
                                durationSec = s.durationSec,
                                distanceM = s.distanceM,
                                grade = s.grade,
                            )
                        },
                    ).size
                }

                // Drop implausible readings rather than refusing the restore:
                // an archive written before these bounds existed may carry a
                // fat-fingered figure, and losing one weigh-in beats losing
                // the whole import. Kept out of the count so the summary does
                // not claim rows it discarded.
                val usableStats = archive.stats.filter {
                    BodyLimits.validWeight(it.weightKg) && BodyLimits.validBodyFat(it.bodyFatPct)
                }
                usableStats.forEach { s ->
                    statDao.insert(
                        StatEntity(
                            takenAtMs = s.takenAtMs,
                            weightKg = s.weightKg,
                            heightCm = s.heightCm,
                            bodyFatPct = s.bodyFatPct,
                        ),
                    )
                }
                val statCount = usableStats.size
                archive.titles.forEach {
                    titleDao.insertAll(listOf(TitleUnlockEntity(it.titleId, it.unlockedAtMs)))
                }
                archive.skills.forEach {
                    skillPracticeDao.insert(
                        SkillPracticeEntity(
                            skillName = it.skillName,
                            practicedAtMs = it.practicedAtMs,
                            claimed = it.claimed,
                            value = it.value,
                            weightKg = it.weightKg,
                        ),
                    )
                }
                archive.measurements.forEach { m ->
                    measurementDao.insert(
                        MeasurementEntity(site = m.site.name, valueCm = m.valueCm, takenAtMs = m.takenAtMs),
                    )
                }
                if (archive.healthDays.isNotEmpty()) {
                    healthDayDao.upsertAll(
                        archive.healthDays.map {
                            HealthDayEntity(
                                epochDay = it.date.toEpochDay(),
                                steps = it.steps,
                                distanceKm = it.distanceKm,
                                activeKcal = it.activeKcal,
                                sleepMinutes = it.sleepMinutes,
                                restingHr = it.restingHr,
                            )
                        },
                    )
                }

                ImportResult(
                    presets = archive.presets.size,
                    sessions = archive.sessions.size,
                    sets = restoredSets,
                    stats = statCount,
                    titles = archive.titles.size,
                    skills = archive.skills.size,
                    healthDays = archive.healthDays.size,
                    measurements = archive.measurements.size,
                    problems = problems,
                )
            }
    }

    // ------------------------------------------------------------------ idle

    fun observeIdle(): Flow<IdleState> =
        idleDao.observe().map { (it ?: IdleStateEntity()).toIdleState() }

    fun observeIdleInputs(): Flow<IdleInputs> = combine(
        sessionDao.observeCompletedWithSets(),
        skillPracticeDao.observeAll(),
    ) { completed, practices -> idleInputs(completed, practices) }

    fun observeIdleRate(): Flow<IdleRate> = combine(
        observeIdle(),
        observeIdleInputs(),
    ) { state, inputs ->
        Idle.rate(state, inputs.sessionsLast7d, inputs.volumeLast7d, inputs.skillsUnlocked, inputs.streakDays)
    }

    /** State plus the live rate, so the screen never recomputes the formula. */
    /**
     * One-shot idle snapshot for the cloud push. The observable flow is for the
     * screen; a sync needs a single value and must not keep a subscription
     * open, so this takes the first emission and stops.
     */
    suspend fun idleSnapshotOnce(): IdleSnapshot? = observeIdleSnapshot().firstOrNull()

    fun observeIdleSnapshot(): Flow<IdleSnapshot> = combine(
        observeIdle(),
        observeIdleRate(),
    ) { state, rate -> IdleSnapshot(state, rate) }

    /**
     * Banks everything accrued since the last collect. Read-modify-write runs
     * inside a transaction, so two racing collects cannot both be paid for the
     * same interval: the second reads the bumped lastCollectedAtMs and gets 0.
     */
    suspend fun collectIdle(nowMs: Long): Long = db.withTransaction {
        val current = idleDao.get() ?: IdleStateEntity()
        val state = current.toIdleState()
        val inputs = idleInputs(sessionDao.observeCompletedWithSets().first(), skillPracticeDao.observeAll().first())
        val rate = Idle.rate(state, inputs.sessionsLast7d, inputs.volumeLast7d, inputs.skillsUnlocked, inputs.streakDays)
        val gained = Idle.accrued(state, rate, nowMs)
        if (gained > 0) {
            idleDao.upsert(current.copy(essence = state.essence + gained, lastCollectedAtMs = nowMs))
        }
        gained
    }

    /** Gacha hook: adds army size, never lowers an existing relic multiplier. */
    suspend fun grantIdle(shadows: Int, relicMultiplier: Double) = db.withTransaction {
        val current = idleDao.get() ?: IdleStateEntity()
        idleDao.upsert(
            current.copy(
                shadows = current.shadows + shadows,
                relicMultiplier = maxOf(current.relicMultiplier, relicMultiplier),
            ),
        )
    }

    fun observeRolls(): Flow<Int> =
        gachaDao.observeRolls().map { it?.rolls ?: 0 }

    fun observeOwnedFrames(): Flow<Set<String>> =
        gachaDao.observeOwnedFrames().map { it.toSet() }

    /** Every relic drawn, strongest first; the rate uses the top one. */
    fun observeRelics(): Flow<List<RelicHolding>> = gachaDao.observeRelics().map { rows ->
        rows.map { RelicHolding(it.id, it.name, it.multiplier, it.drawnAtMs) }
    }

    fun observeEquippedFrame(): Flow<String?> = gachaDao.observeEquipped()

    /**
     * Equips (or unequips with null) a crest frame. Cosmetic only, so a frame
     * the hunter does not own is silently refused (returns false) rather than
     * thrown — the ownership check reads owned_crest_frames inside the same
     * transaction as the write, so a concurrent draw can never race it.
     */
    suspend fun equipFrame(frameId: String?): Boolean = db.withTransaction {
        if (frameId != null && frameId !in gachaDao.ownedFrameIds()) {
            return@withTransaction false
        }
        val current = gachaDao.get() ?: GachaStateEntity()
        gachaDao.upsert(current.copy(equippedFrame = frameId))
        true
    }

    /** Called on level-up: banks one more roll to spend on the Shadow screen. */
    /**
     * Recomputes the live rate multiplier from everything in the vault. Must be
     * called inside the same transaction as any relic insert: the multiplier is
     * derived state, and a relic that is owned but not applied is a lie.
     */
    private suspend fun applyRelicVault() {
        val effective = Relics.effectiveMultiplier(gachaDao.relicMultipliers())
        val state = idleDao.get() ?: IdleStateEntity()
        idleDao.upsert(state.copy(relicMultiplier = effective))
    }

    suspend fun grantRoll(count: Int = 1) = db.withTransaction {
        val current = gachaDao.get() ?: GachaStateEntity()
        gachaDao.upsert(current.copy(rolls = current.rolls + count))
    }

    /**
     * Spends one banked roll and applies the payout atomically: the decrement,
     * the draw, and the shadows/relic/frame grant all run inside one
     * transaction, so a crash can never consume a roll without paying it out.
     * Returns null when nothing is banked.
     */
    suspend fun spendRoll(seed: Long): RollResult? = db.withTransaction {
        val current = gachaDao.get() ?: GachaStateEntity()
        if (current.rolls <= 0) return@withTransaction null
        gachaDao.upsert(current.copy(rolls = current.rolls - 1))
        val result = Gacha.roll(seed)
        when (val reward = result.reward) {
            is Reward.Shadows -> grantIdle(reward.count, 1.0)
            is Reward.Relic -> {
                // Keep the relic itself, not just its number, then DERIVE the
                // live multiplier from the whole vault so every relic counts.
                gachaDao.insertRelic(
                    OwnedRelicEntity(
                        name = reward.name,
                        multiplier = reward.multiplier,
                        drawnAtMs = System.currentTimeMillis(),
                    ),
                )
                applyRelicVault()
            }
            is Reward.CrestFrame -> gachaDao.insertFrame(
                OwnedCrestFrameEntity(frameId = reward.id, ownedAtMs = System.currentTimeMillis()),
            )
        }
        result
    }

    private fun idleInputs(
        completed: List<SessionWithSets>,
        practices: List<SkillPracticeEntity>,
    ): IdleInputs {
        val weekAgoMs = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val recent = completed.filter { (it.session.completedAtMs ?: 0L) >= weekAgoMs }
        val volume = recent.sumOf { (_, sets) ->
            sets.filter { it.done }.sumOf { (it.weightKg ?: 0.0) * it.reps }
        }
        val completedDates = completed.mapNotNull { (session, _) ->
            session.completedAtMs?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            }
        }.toSet()
        return IdleInputs(
            sessionsLast7d = recent.size,
            volumeLast7d = volume,
            skillsUnlocked = practices.filter { it.claimed }.map { it.skillName }.distinct().size,
            streakDays = Titles.trainingStreakDays(completedDates),
        )
    }

    private fun IdleStateEntity.toIdleState() =
        IdleState(essence = essence, shadows = shadows, relicMultiplier = relicMultiplier, lastCollectedAtMs = lastCollectedAtMs)
}

/** Raw training inputs feeding Idle.rate — exposed for the rate WHY-breakdown. */
data class IdleInputs(
    val sessionsLast7d: Int,
    val volumeLast7d: Double,
    val skillsUnlocked: Int,
    val streakDays: Int,
)

/** Idle state plus the rate the System is currently paying. */
data class IdleSnapshot(
    val state: IdleState,
    val rate: IdleRate,
)

/** One drawn relic, for the Shadow screen's vault. */
data class RelicHolding(
    val id: Long,
    val name: String,
    val multiplier: Double,
    val drawnAtMs: Long,
)
