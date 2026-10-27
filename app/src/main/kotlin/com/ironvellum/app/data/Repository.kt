package com.ironvellum.app.data
import androidx.room.withTransaction
import com.ironvellum.app.R
import com.ironvellum.app.data.db.ExerciseDao
import com.ironvellum.app.data.db.ExerciseEntity
import com.ironvellum.app.data.db.FavouriteExerciseEntity
import com.ironvellum.app.domain.BlockKey
import com.ironvellum.app.domain.LastLogged
import com.ironvellum.app.domain.LastLoggedIndex
import com.ironvellum.app.domain.LiftLoading
import com.ironvellum.app.domain.LoadKit
import com.ironvellum.app.domain.SchemePlan
import com.ironvellum.app.domain.SchemeType
import com.ironvellum.app.domain.TopMark
import com.ironvellum.app.domain.WornLoad
import com.ironvellum.app.data.db.GachaStateEntity
import com.ironvellum.app.data.db.HealthDayDao
import com.ironvellum.app.data.db.HealthDayEntity
import com.ironvellum.app.data.db.IdleDao
import com.ironvellum.app.data.db.IdleStateEntity
import com.ironvellum.app.data.db.MeasurementDao
import com.ironvellum.app.data.db.MeasurementEntity
import com.ironvellum.app.data.db.OwnedCrestFrameEntity
import com.ironvellum.app.data.db.PresetDao
import com.ironvellum.app.data.db.PresetEntity
import com.ironvellum.app.data.db.PresetEntryEntity
import com.ironvellum.app.data.db.PresetWithEntries
import com.ironvellum.app.data.db.ProfileDao
import com.ironvellum.app.data.db.ProfileEntity
import com.ironvellum.app.data.db.SessionDao
import com.ironvellum.app.data.db.SessionEntity
import com.ironvellum.app.data.db.SessionExerciseNoteEntity
import com.ironvellum.app.data.db.SessionWithSets
import com.ironvellum.app.data.db.SetLogEntity
import com.ironvellum.app.data.db.SkillPracticeDao
import com.ironvellum.app.data.db.SkillPracticeEntity
import com.ironvellum.app.data.db.StatDao
import com.ironvellum.app.data.db.StatEntity
import com.ironvellum.app.data.db.SyncStateDao
import com.ironvellum.app.data.db.SyncStateEntity
import com.ironvellum.app.data.db.TitleDao
import com.ironvellum.app.data.db.TitleUnlockEntity
import com.ironvellum.app.data.db.GachaDao
import com.ironvellum.app.data.db.OwnedRelicEntity
import com.ironvellum.app.domain.ActivityScore
import com.ironvellum.app.domain.CsvWorkoutReader
import com.ironvellum.app.domain.ArmyClass
import com.ironvellum.app.domain.Evidence
import com.ironvellum.app.domain.CustomExercises
import com.ironvellum.app.domain.CustomSave
import com.ironvellum.app.domain.CustomSpec
import com.ironvellum.app.domain.Effort
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseHistory
import com.ironvellum.app.domain.ExerciseHistoryCalculator
import com.ironvellum.app.domain.ExerciseLogInputs
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.ExerciseTwins
import com.ironvellum.app.domain.isStrength
import com.ironvellum.app.domain.ExportReader
import com.ironvellum.app.domain.ExportWriter
import com.ironvellum.app.domain.Gacha
import com.ironvellum.app.domain.HealthDay
import com.ironvellum.app.domain.CrestDef
import com.ironvellum.app.domain.CrestEffects
import com.ironvellum.app.domain.CrestSource
import com.ironvellum.app.domain.Crests
import com.ironvellum.app.domain.HouseEffects
import com.ironvellum.app.domain.Idle
import com.ironvellum.app.domain.IdleRate
import com.ironvellum.app.domain.IdleState
import com.ironvellum.app.domain.MeasurementEntry
import com.ironvellum.app.domain.MeasurementSite
import com.ironvellum.app.domain.BodyLimits
import com.ironvellum.app.domain.MovementDifficulty
import com.ironvellum.app.domain.MuscleGroup
import com.ironvellum.app.domain.PlannedPreset
import com.ironvellum.app.domain.MuscleMap
import com.ironvellum.app.domain.ProgramGenerator
import com.ironvellum.app.domain.ProgramRules
import com.ironvellum.app.domain.LoggedLift
import com.ironvellum.app.domain.Equipment
import com.ironvellum.app.domain.StrengthProfile
import com.ironvellum.app.domain.PlayerProfile
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.StarterBasis
import com.ironvellum.app.domain.VolumeLevel
import com.ironvellum.app.domain.PresetEntry
import com.ironvellum.app.domain.Progression
import com.ironvellum.app.domain.ProgressionSuggestion
import com.ironvellum.app.domain.Suggestions
import com.ironvellum.app.domain.WarmupRamp
import com.ironvellum.app.domain.WarmupStep
import com.ironvellum.app.domain.Rank
import com.ironvellum.app.domain.RankBreakdown
import com.ironvellum.app.domain.RankUp
import com.ironvellum.app.domain.Reward
import com.ironvellum.app.domain.RollResult
import com.ironvellum.app.domain.RoutineCode
import com.ironvellum.app.domain.RoutinePlan
import com.ironvellum.app.domain.RiteSwap
import com.ironvellum.app.domain.SessionOverride
import com.ironvellum.app.domain.RoutineUpdate
import com.ironvellum.app.domain.modifiersAfterLoadChange
import com.ironvellum.app.domain.CarryEdits
import com.ironvellum.app.domain.CarryFigure
import com.ironvellum.app.domain.CarrySet
import com.ironvellum.app.domain.carriesFrom
import com.ironvellum.app.domain.carryFiguresFor
import com.ironvellum.app.domain.restoredIndexes
import com.ironvellum.app.domain.SessionSet
import com.ironvellum.app.domain.SessionXp
import com.ironvellum.app.domain.SetKind
import com.ironvellum.app.domain.SetScheme
import com.ironvellum.app.domain.Supersets
import com.ironvellum.app.domain.SetRecords
import com.ironvellum.app.domain.SealedEdit
import com.ironvellum.app.domain.SealedReopen
import com.ironvellum.app.domain.TrialDraft
import com.ironvellum.app.domain.SkillClaimResult
import com.ironvellum.app.domain.SkillPractice
import com.ironvellum.app.domain.Skills
import com.ironvellum.app.domain.StatEntry
import com.ironvellum.app.domain.StrengthIndex
import com.ironvellum.app.domain.TitleDef
import com.ironvellum.app.domain.Titles
import com.ironvellum.app.domain.TwinConversion
import com.ironvellum.app.domain.Veil
import com.ironvellum.app.domain.VeilGrant
import com.ironvellum.app.data.cloud.WireLimits
import com.ironvellum.app.data.cloud.localNameToAdopt
import com.ironvellum.app.domain.TrainingMode
import com.ironvellum.app.domain.UiText
import com.ironvellum.app.domain.UnlockedTitle
import com.ironvellum.app.domain.Circle
import com.ironvellum.app.domain.WorkoutPreset
import com.ironvellum.app.domain.SessionAudience
import com.ironvellum.app.domain.WeighIn
import com.ironvellum.app.domain.WorkoutCsvWriter
import com.ironvellum.app.domain.WorkoutSession
import com.ironvellum.app.domain.XpRescore
import com.ironvellum.app.domain.EXERCISE_NOTE_MAX
import com.ironvellum.app.domain.ExerciseNote
import com.ironvellum.app.domain.Xp
import com.ironvellum.app.domain.OwnedRelic
import com.ironvellum.app.domain.RelicHouses
import com.ironvellum.app.domain.RelicRow
import com.ironvellum.app.domain.Relics
import com.ironvellum.app.domain.VaultState
import com.ironvellum.app.domain.Variation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

/**
 * Evidence from real training, not practice: a completed session's best set of
 * a movement. Informs the skill dialog's standard line; never claims or mints
 * XP on its own.
 */
data class SkillTrainingEvidence(
    /** Reps performed, or seconds held for a hold movement. */
    val value: Int,
    val weightKg: Double?,
    val achievedAtMs: Long,
)

/** The stored kind; an unknown name reads as a plain working set. */
internal fun SetLogEntity.setKind(): SetKind = SetKind.fromName(kind)

/** A warm-up is the one kind that is stored unticked and never counts. */
internal val SetLogEntity.isWarmup: Boolean get() = setKind() == SetKind.WARMUP

/** The block the row belongs to: a rite may name one movement twice, so the movement alone is not enough. */
internal val SetLogEntity.blockKey: BlockKey get() = BlockKey(exercisePosition, exerciseId)

/**
 * Which set-position record stands as a movement's evidence. [SetRecords.records]
 * keeps one per set position. A count-only technique standard ("Hang 60s")
 * wants the most reps or seconds, with score only breaking ties: the top
 * score let a short weighted set hide a longer one that met it. A standard
 * with a load in it ("+25 kg", "at double bodyweight"), and any movement
 * that is no technique, keeps the top score, so a heavy set is never traded
 * for a light warm-up with more reps.
 */
internal fun techniqueEvidence(records: List<SetRecords.Record>, skill: Skills.SkillDef?): SetRecords.Record {
    val countOnly = skill != null &&
        !skill.standard.contains("bodyweight", ignoreCase = true) &&
        Skills.loadBar(skill.name) == null
    return if (countOnly) records.maxWith(compareBy({ it.reps }, { it.score })) else records.maxBy { it.score }
}

/**
 * What [Repository.observeSkillTrainingEvidence] reads: the best set per normalised name, from [history], for the
 * skills tree. An easier version (assisted, incline) does not show the technique; a harder one still does.
 *
 * A dedicated exercise is also its base done with its modifier or load ([ExerciseTwins]), so the sets logged that way
 * count for it: Pull-up with added load is judged against the Weighted Pull-up bar, Push-up with archer counts for
 * Archer Push-up. The twin form is read from [ExerciseTwins.twinned] sets and merged with the plain read, so the base
 * movement keeps the evidence it always had.
 */
internal fun skillTrainingEvidence(
    history: List<Pair<WorkoutSession, List<SessionSet>>>,
    bodyweightAt: (Long) -> Double,
    exercises: List<Exercise>,
): Map<String, SkillTrainingEvidence> {
    val metricByName = exercises.associate { Titles.normaliseName(it.name) to it.metric }
    val weightedByName = exercises.associate { Titles.normaliseName(it.name) to it.isWeighted }
    val skillByName = Skills.ALL.associateBy { Titles.normaliseName(it.name) }
    fun recordsOf(sessions: List<Pair<WorkoutSession, List<SessionSet>>>) =
        SetRecords.records(sessions, bodyweightAt) { set -> metricByName[Titles.normaliseName(set.exerciseName)] }.values
    val twinned = ExerciseTwins.twinned(
        history,
        isBodyweight = { weightedByName[Titles.normaliseName(it)] == false },
        exists = { Titles.normaliseName(it) in metricByName },
    )
    return (recordsOf(history) + recordsOf(twinned))
        .filter { !Variation.eases(it.variation) }
        .groupBy { Titles.normaliseName(it.exerciseName) }
        .mapValues { (name, records) ->
            val top = techniqueEvidence(records, skillByName[name])
            SkillTrainingEvidence(
                value = top.reps,
                weightKg = top.weightKg,
                achievedAtMs = top.achievedAtMs,
            )
        }
}

class Repository(
    private val db: IronvellumDatabase,
    private val health: HealthSync? = null,
    private val highestBand: HighestBandStore? = null,
    /** The lifter's vest and belt (the app reads them from the gear prefs): the increments of loaded bodyweight lifts. */
    private val loadKit: suspend () -> LoadKit = { LoadKit.NONE },
    /** Told when a finished workout is sealed, changed or removed, after the write has committed. */
    private val writeBack: SessionWriteBack? = null,
    /** How each workout differed from its routine (Today's fatigue advice); remembered past the process. */
    private val adjustments: SessionAdjustments = SessionAdjustments.None,
    private val archiveSettings: ArchiveSettings? = null,
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
    private val favouriteExerciseDao = db.favouriteExerciseDao()

    /** The figures the lifter moved by hand in live trials, so a tick never overwrites them. */
    private val carryEdits = CarryEdits()
    // ---------------------------------------------------------------- seeding

    suspend fun ensureSeeded() {
        if (profileDao.get() == null) {
            profileDao.upsert(ProfileEntity(name = "Ironbound", totalXp = 0, currentTitleId = null))
        }
        val existingNames = exerciseDao.observeAll().first().syncCustomExercises().map { it.name }.toSet()
        val missing = Seed.exercises.filterNot { it.name in existingNames }
        if (missing.isNotEmpty()) {
            exerciseDao.insertAll(missing)
        }
        // Gym-line rungs above tier I (Pause Squat .. Triple-Bodyweight
        // Deadlift) were once seeded unweighted, and the insert above never
        // touches an existing row. Re-stamp exactly those names on every
        // launch: idempotent, a no-op once repaired, and it also mends rows
        // an older archive restores. Scoring never reads the flag (XP and the
        // strength index take the logged kilos), so no history restates.
        exerciseDao.markWeighted(
            Skills.ALL.filter { it.line in Skills.GYM_LINES && Skills.isWeighted(it) }.map { it.name },
        )
        // Presets are NOT seeded here any more. A fresh install used to wake
        // up owning somebody else's training week; setup now asks a few
        // questions and writes a plan through applyRoutine, or applies
        // applyStarterTemplate when the lifter explicitly picks it. The
        // catalogue above stays automatic: every screen reads it, and an
        // upgrade must gain new movements without touching history.
        // Sessions logged before the first weigh-in banked no strength at
        // all. Waiting for the next weigh-in to notice would leave a lifter
        // who already measured himself staring at zeros forever; the count
        // query above the repair makes this free when there is nothing owed.
        rescoreUnweighedSessions()
        // A scoring-formula change makes every STORED score stale — session
        // rows and the leaderboard's lifetime sum keep the old numbers until
        // something recomputes them — so it must run EXACTLY ONCE per formula
        // change, not on every launch. The migration only adds the
        // scoringVersion column at 0; this check consumes the marker: below
        // the current StrengthIndex.SCORING_VERSION means "stored scores have
        // never been restated under this formula". Restating is Kotlin work
        // (StrengthIndex over stored sets), which the SQL migration cannot
        // express — hence code, not SQL.
        profileDao.get()?.let { profile ->
            if (profile.scoringVersion < StrengthIndex.SCORING_VERSION) {
                rescoreStrengthScores(onlyUnscored = false)
                // Re-read before stamping: the rescore just rewrote
                // lifetimeStrength, and stamping the pre-rescore row would
                // silently write the stale total back over the fresh sum.
                profileDao.get()?.let { fresh ->
                    profileDao.upsert(fresh.copy(scoringVersion = StrengthIndex.SCORING_VERSION))
                }
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

    private val _healthSyncedAtMs = MutableStateFlow<Long?>(null)

    /** When Health Connect last returned any days this process, or null if it has not. */
    fun observeHealthSyncedAt(): StateFlow<Long?> = _healthSyncedAtMs

    /**
     * Pull the last [days] days from Health Connect into the local cache.
     * Reports days actually written plus any metric that could not be read, so
     * the UI never claims a successful sync it did not get.
     */
    suspend fun syncHealthHistory(days: Int = 90): HealthSync.HistoryRead {
        val health = health ?: return HealthSync.HistoryRead(
            problems = listOf(UiText.res(R.string.health_not_wired)),
        )
        if (!health.available()) return HealthSync.HistoryRead(
            problems = listOf(UiText.res(R.string.health_unavailable)),
        )
        val read = runCatching { health.readDailyHistory(days) }
            .getOrElse { HealthSync.HistoryRead(problems = listOf(UiText.res(R.string.health_read_failed, it.javaClass.simpleName))) }
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
        if (read.days.isNotEmpty()) _healthSyncedAtMs.value = System.currentTimeMillis()
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
        exerciseDao.observeAll().first().syncCustomExercises().associate { it.id to it.toDomain() }

    /** Live catalogue names for the CSV import's movement picker. */
    fun observeCatalogueNames(): Flow<List<String>> =
        exerciseDao.observeAll().map { rows -> rows.map { it.name } }

    suspend fun catalogueNamesOnce(): List<String> =
        exerciseDao.observeAll().first().map { it.name }

    /** NOCASE lookup, same rule the log screen resolves names by. */
    suspend fun exerciseIdByName(name: String): Long? = exerciseDao.byName(name)?.id

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
        custom = custom,
        needs = if (custom) CustomExercises.decodeNeeds(gear) else emptySet(),
        shares = if (custom) CustomExercises.decodeShares(muscles) else emptyMap(),
    )

    // ---------------------------------------------------------------- exercises

    fun observeExercises(): Flow<List<Exercise>> =
        exerciseDao.observeAll().map { list -> list.syncCustomExercises().map { it.toDomain() } }

    /**
     * Saves a movement the lifter made: its group and category come from the type, its metric from Logged as, and
     * weighted is derived from the gear ([CustomExercises.isWeighted]). The name is checked against the whole
     * catalogue and the lifter's own, case-insensitively, inside the same transaction as the insert.
     */
    suspend fun createCustomExercise(spec: CustomSpec): CustomSave = db.withTransaction {
        val catalogue = exerciseDao.observeAll().first()
        CustomExercises.nameProblem(spec.name, catalogue.map { it.name })
            ?.let { return@withTransaction CustomSave.Rejected(it) }
        val row = customRow(spec, id = 0)
        val id = exerciseDao.insertAll(listOf(row)).first()
        (catalogue + row.copy(id = id)).syncCustomExercises()
        CustomSave.Saved(row.copy(id = id).toDomain())
    }

    /**
     * Edits one of the lifter's own movements. Its metric is locked once any set is logged against it, because
     * reps, seconds and metres are stored in different columns and a change would reread history.
     */
    suspend fun updateCustomExercise(id: Long, spec: CustomSpec): CustomSave = db.withTransaction {
        val before = exerciseDao.byId(id)?.takeIf { it.custom }
            ?: return@withTransaction CustomSave.Rejected(Words.text(UiText.res(R.string.custom_not_yours)))
        val catalogue = exerciseDao.observeAll().first()
        CustomExercises.nameProblem(spec.name, catalogue.map { it.name }, own = before.name)
            ?.let { return@withTransaction CustomSave.Rejected(it) }
        if (spec.logged.metric.name != before.metric && exerciseDao.loggedSets(id) > 0) {
            return@withTransaction CustomSave.Rejected(Words.text(UiText.res(R.string.custom_metric_locked)))
        }
        val row = customRow(spec, id)
        exerciseDao.update(row)
        catalogue.map { if (it.id == id) row else it }.syncCustomExercises()
        CustomSave.Saved(row.toDomain())
    }

    /** How many logged sets, rite entries and trial notes hold the exercise; delete is only possible at zero. */
    suspend fun customExerciseUsage(id: Long): Int = exerciseDao.usage(id)

    /** Deletes one of the lifter's own movements when nothing references it; false (and nothing deleted) otherwise. */
    suspend fun deleteCustomExercise(id: Long): Boolean = db.withTransaction {
        if (exerciseDao.usage(id) > 0) return@withTransaction false
        val deleted = exerciseDao.deleteCustom(id) > 0
        exerciseDao.observeAll().first().syncCustomExercises()
        deleted
    }

    private fun customRow(spec: CustomSpec, id: Long) = ExerciseEntity(
        id = id,
        name = CustomExercises.cleanName(spec.name),
        muscleGroup = spec.type.group.name,
        isWeighted = CustomExercises.isWeighted(spec.needs),
        metric = spec.logged.metric.name,
        category = spec.type.category,
        custom = true,
        gear = CustomExercises.encodeNeeds(spec.needs),
        muscles = CustomExercises.encodeShares(spec.shares),
    )

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
                        supersetGroup = e.supersetGroup,
                        scheme = SetScheme.decode(e.setScheme),
                        warmup = e.warmup,
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
        /** Shared with the adjacent entries done as one superset; saved only where they really are neighbours. */
        val supersetGroup: Int? = null,
        /** How the lift's sets are laid out; Straight, the default, stores nothing. */
        val scheme: SetScheme = SetScheme(),
        /** A warm-up exercise: kept at the head of the rite and out of every superset. */
        val warmup: Boolean = false,
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
        // Warm-ups lead the rite, so the trial's warm-up step and the rite list agree on the order whatever the caller sent.
        val ordered = entries.filter { it.warmup } + entries.filterNot { it.warmup }
        // A group is kept only while its members are neighbours: a number left by a removal or a reorder is dropped here.
        // A warm-up is never in one.
        val groups = Supersets.effective(ordered.map { if (it.warmup) null else it.supersetGroup })
        presetDao.insertEntries(
            ordered.mapIndexed { position, e ->
                PresetEntryEntity(
                    presetId = id,
                    exerciseId = e.exerciseId,
                    targetSets = e.targetSets,
                    targetReps = e.targetReps,
                    targetWeightKg = e.targetWeightKg,
                    modifiers = e.modifiers,
                    position = position,
                    supersetGroup = groups[position],
                    setScheme = e.scheme.normalised().encode(),
                    warmup = e.warmup,
                )
            },
        )
        id
    }

    suspend fun deletePreset(presetId: Long) = presetDao.deletePreset(presetId)

    /**
     * Puts one exercise on the end of rite [presetId], or into a new rite
     * named after it when [presetId] is null, and returns the rite's name.
     * Appends a row rather than going through [savePreset], which rewrites
     * every entry of the rite to add one.
     */
    suspend fun addExerciseToRite(
        exerciseName: String,
        presetId: Long?,
        targetSets: Int,
        targetReps: Int,
        level: VolumeLevel = VolumeLevel.STANDARD,
    ): String {
        val exerciseId = exerciseIdByName(exerciseName) ?: error("$exerciseName is not in the catalogue")
        // Worked out before the transaction: it reads history, and the write below needs none of it.
        val startKg = addedLoadKg(exerciseId, targetReps, level)
        return db.withTransaction {
            val (id, name, position) = if (presetId == null) {
                Triple(presetDao.insertPreset(PresetEntity(name = exerciseName, note = "", scheduledDay = null)), exerciseName, 0)
            } else {
                val rite = presetDao.presetWithEntries(presetId) ?: error("${Words.rite.c} $presetId no longer exists")
                Triple(presetId, rite.preset.name, (rite.entries.maxOfOrNull { it.position } ?: -1) + 1)
            }
            presetDao.insertEntries(
                listOf(
                    PresetEntryEntity(
                        presetId = id,
                        exerciseId = exerciseId,
                        targetSets = targetSets,
                        targetReps = targetReps,
                        targetWeightKg = startKg,
                        modifiers = "",
                        position = position,
                    ),
                ),
            )
            name
        }
    }

    /** Zero presets means setup has never run; the UI uses this to tell an
     *  empty first-run from a configured lifter. */
    suspend fun presetCount(): Int = presetDao.count()

    /**
     * Replaces every preset with [plan]'s, in one transaction. A re-run of
     * setup is a deliberate act: the lifter has seen the proposal and chosen
     * it, so keeping half her old week and half the new one would be worse
     * than replacing. Completed sessions carry denormalised set rows and a
     * nullable presetId, so history survives the swap intact.
     *
     * Any entry name missing from the catalogue aborts the whole apply with
     * [IllegalArgumentException] and the transaction rolls back - the plan is
     * generated from this very catalogue, so a miss is a bug, and writing a
     * preset that silently drops a movement would look like a working week.
     */
    suspend fun applyRoutine(plan: RoutinePlan, replaceExisting: Boolean = true) {
        val presets = plan.presets.map { spec ->
            PlannedPresetRows(
                name = spec.name,
                // Papers are listed under Sources in the builder; a saved
                // note is read on the dashboard in one line.
                note = Evidence.split(spec.note).first,
                scheduledDay = spec.scheduledDay,
                entries = spec.entries.map { entry ->
                    PlannedEntryRows(
                        exerciseName = entry.exerciseName,
                        targetSets = entry.sets,
                        targetReps = entry.reps,
                        targetWeightKg = entry.targetWeightKg,
                        // Was hardcoded to "", so "Add to my presets" silently
                        // stripped every generated modifier the preview showed.
                        modifiers = entry.modifiers,
                    )
                },
            )
        }
        writeRoutinePresets(presets, clearFirst = replaceExisting)
    }

    /**
     * Writes the owner's original bodyweight starter week
     * ([Seed.bodyweightStarterTemplate]) as the lifter's presets. This is the
     * only path left to those four presets: chosen in setup, never imposed.
     * Unlike [applyRoutine] it carries the hand-written modifiers and loads.
     */
    suspend fun applyStarterTemplate() {
        writeRoutinePresets(
            Seed.bodyweightStarterTemplate.map { spec ->
                PlannedPresetRows(
                    name = spec.name,
                    note = spec.note,
                    scheduledDay = spec.scheduledDay,
                    entries = spec.entries.map { entry ->
                        PlannedEntryRows(
                            exerciseName = entry.exercise,
                            targetSets = entry.sets,
                            targetReps = entry.reps,
                            targetWeightKg = entry.weightKg,
                            modifiers = entry.modifiers,
                        )
                    },
                )
            },
        )
    }

    /**
     * Writes ONE planned preset, new ([presetId] null) or overwriting the
     * existing row. Names resolve before anything is written - an unknown
     * movement aborts with every unknown listed, so "Improve a preset" can
     * never half-save a day - and the planned entry's modifiers travel through,
     * because improve promises to keep a CSV import's "sissy squat" tags.
     */
    suspend fun savePlannedPreset(presetId: Long?, preset: PlannedPreset): Long {
        val idByName = exerciseDao.observeAll().first().associate { it.name to it.id }
        val unknown = preset.entries.map { it.exerciseName }.filterNot { it in idByName }
        require(unknown.isEmpty()) { "Unknown exercises in ${Words.rite.s} \"${preset.name}\": $unknown" }
        // The Forge never makes a superset, but it must not undo the lifter's own: a movement keeps its group
        // from the rite being tempered, and savePreset drops it where the pair is no longer neighbours.
        val kept = presetId?.let { presetDao.presetWithEntries(it) }
        // Keyed by (movement, Nth entry of it): a rite may name a movement twice, and each entry keeps its own.
        val keptEntries = kept?.working?.sortedBy { it.position }?.let { rows -> rows.zip(nthOfMovement(rows.map { it.exerciseId })) }.orEmpty()
        val keptGroups = keptEntries
            .filter { (e, _) -> e.supersetGroup != null }.associate { (e, nth) -> (e.exerciseId to nth) to e.supersetGroup }
        // Likewise a lift's set scheme: the Forge tempers loads and counts, it never undoes how a lift is run. The
        // scheme follows the lift and is fitted to the set count the Forge chose.
        val keptSchemes = keptEntries
            .filter { (e, _) -> e.setScheme != null }.associate { (e, nth) -> (e.exerciseId to nth) to SetScheme.decode(e.setScheme) }
        // The Forge plans the worked exercises only (it never sees a warm-up, [toPlanned] leaves them out), so the
        // rite's warm-ups are carried over as they are, whatever it did to the rest.
        val keptWarmups = kept?.entries?.filter { it.warmup }?.sortedBy { it.position }?.map {
            PresetDraftEntry(
                exerciseId = it.exerciseId,
                targetSets = it.targetSets,
                targetReps = it.targetReps,
                targetWeightKg = it.targetWeightKg,
                modifiers = it.modifiers,
                scheme = SetScheme.decode(it.setScheme),
                warmup = true,
            )
        }.orEmpty()
        return savePreset(
            presetId = presetId,
            name = preset.name,
            note = Evidence.split(preset.note).first,
            scheduledDay = preset.scheduledDay,
            entries = keptWarmups + preset.entries.let { planned ->
                val ids = planned.map { idByName.getValue(it.exerciseName) }
                val nth = nthOfMovement(ids)
                planned.mapIndexed { i, entry ->
                    val key = ids[i] to nth[i]
                    PresetDraftEntry(
                        exerciseId = ids[i],
                        targetSets = entry.sets,
                        targetReps = entry.reps,
                        targetWeightKg = entry.targetWeightKg,
                        modifiers = entry.modifiers,
                        supersetGroup = keptGroups[key],
                        scheme = keptSchemes[key]?.fitTo(entry.sets) ?: SetScheme(),
                    )
                }
            },
        )
    }

    /**
     * Several rites rewritten as one all-or-nothing write, so a change that spans rites (a movement moved
     * between two of them, a swap of days) cannot land half, and Undo puts every one of them back together.
     */
    suspend fun savePlannedPresets(updates: List<Pair<Long, PlannedPreset>>) = db.withTransaction {
        updates.forEach { (id, preset) -> savePlannedPreset(id, preset) }
    }

    /** What an import did: workouts written and exercise names the catalogue lacks. */
    data class ImportedRoutine(val added: Int, val skippedExercises: List<String>)

    /**
     * Every preset in shareable form, week order then name. Notes lose their
     * citations exactly as [savePlannedPreset] strips them: a share code is
     * pasted into a chat, and a wall of paper references would bloat it and
     * the receiving card for text nobody reads there.
     */
    suspend fun sharedRoutine(): List<RoutineCode.SharedWorkout> =
        observePresets().first()
            .sortedWith(compareBy<WorkoutPreset> { it.scheduledDay ?: Int.MAX_VALUE }.thenBy { it.name })
            .map { preset ->
                RoutineCode.SharedWorkout(
                    name = preset.name,
                    note = Evidence.split(preset.note).first,
                    scheduledDay = preset.scheduledDay,
                    // Like a superset or a scheme, a warm-up exercise stays with the rite it was made in.
                    entries = preset.working.map { entry ->
                        RoutineCode.SharedEntry(
                            exerciseName = entry.exerciseName,
                            sets = entry.targetSets,
                            reps = entry.targetReps,
                            targetWeightKg = entry.targetWeightKg,
                            modifiers = entry.modifiers,
                        )
                    },
                )
            }

    /**
     * Writes a decoded share code. Exercises resolve by normalised catalogue
     * name; one this catalogue lacks is dropped from its workout and reported,
     * because refusing the whole code over one custom exercise would make
     * most shared routines unimportable. A workout left with no exercises is
     * skipped rather than saved empty.
     *
     * [replace] swaps the routine in the existing single transaction; the add
     * path writes workouts UNSCHEDULED so the lifter's week is never
     * disturbed by someone else's day numbers. If nothing at all resolves,
     * nothing is written - "Replace" must never wipe a routine for an empty
     * result.
     */
    suspend fun importSharedRoutine(
        workouts: List<RoutineCode.SharedWorkout>,
        replace: Boolean,
    ): ImportedRoutine {
        val canonical = exerciseDao.observeAll().first()
            .associate { Titles.normaliseName(it.name) to it.name }
        val skipped = LinkedHashSet<String>()
        val rows = workouts.mapNotNull { workout ->
            val entries = workout.entries.mapNotNull { entry ->
                val name = canonical[Titles.normaliseName(entry.exerciseName)]
                if (name == null) {
                    skipped += entry.exerciseName
                    null
                } else {
                    PlannedEntryRows(
                        exerciseName = name,
                        targetSets = entry.sets,
                        targetReps = entry.reps,
                        targetWeightKg = entry.targetWeightKg,
                        modifiers = entry.modifiers,
                    )
                }
            }
            if (entries.isEmpty()) {
                null
            } else {
                PlannedPresetRows(
                    name = workout.name,
                    note = Evidence.split(workout.note).first,
                    scheduledDay = if (replace) workout.scheduledDay else null,
                    entries = entries,
                )
            }
        }
        if (rows.isNotEmpty()) writeRoutinePresets(rows, clearFirst = replace)
        return ImportedRoutine(added = rows.size, skippedExercises = skipped.toList())
    }

    /**
     * The lifter's own estimated 1RM per movement, from every DONE, weighted
     * set she actually logged - the same completed-session stream the history
     * screen reads, so the generator and the record can never disagree about
     * what her best bench was.
     */
    suspend fun strengthProfile(): StrengthProfile =
        ProgramRules.strengthProfile(
            sessionDao.completedLoadedSets()
                // An easier version (assisted, incline) is not the movement's 1RM; a harder one still counts.
                .filter { !Variation.eases(it.modifiers) }
                .map { LoggedLift(it.exerciseName, it.weightKg, it.reps) },
        )

    /** The lifter's latest weigh-in, or null before the first. */
    suspend fun latestBodyweightKg(): Double? = statDao.observeAll().first().firstOrNull()?.weightKg

    /** The starting point for loads a lifter has no history for, or null while their bodyweight is unknown. */
    suspend fun starterBasis(sex: Sex, level: VolumeLevel): StarterBasis? =
        latestBodyweightKg()?.takeIf { it > 0.0 }?.let { StarterBasis(it, sex, level) }

    /**
     * The load a loaded movement starts at when it is added outside a plan (a rite entry, a mid-trial extra): the
     * same rule the plan builder uses ([ProgramGenerator.fillLoad]), so the lifter's logged lifts win and a lift
     * with none falls back to the bodyweight starter. Null for a bodyweight lift, a movement that is not strength
     * work, or when there is nothing to start from (no weigh-in and no history); the set then stays unloaded.
     */
    suspend fun addedLoadKg(exerciseId: Long, reps: Int, level: VolumeLevel = VolumeLevel.STANDARD): Double? {
        val exercise = exerciseDao.byId(exerciseId)?.toDomain() ?: return null
        if (!exercise.isWeighted || !exercise.metric.isStrength) return null
        val sex = observeBodyProfile().first().second
        val strength = strengthProfile().copy(starter = starterBasis(sex, level))
        val rir = ProgramRules.loadRir(MuscleMap.profile(exercise.name)?.compound ?: false)
        val equipment = Equipment.FULL_GYM.copy(kit = loadKit())
        return ProgramGenerator.fillLoad(exercise, strength, reps, rir, equipment)?.kg
    }

    /** The first completed session's day, or null for a lifter with no history. */
    suspend fun firstSessionEpochDay(): Long? =
        sessionDao.firstCompletedStartMs()
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() }

    /** Name-resolved rows awaiting the write; the resolve step runs before any
     *  insert so a bad name can abort before the first preset exists. */
    private data class PlannedPresetRows(
        val name: String,
        val note: String,
        val scheduledDay: Int?,
        val entries: List<PlannedEntryRows>,
    )

    private data class PlannedEntryRows(
        val exerciseName: String,
        val targetSets: Int,
        val targetReps: Int,
        val targetWeightKg: Double?,
        val modifiers: String,
    )

    private suspend fun writeRoutinePresets(presets: List<PlannedPresetRows>, clearFirst: Boolean = true) {
        db.withTransaction {
            val idByName = exerciseDao.observeAll().first().associate { it.name to it.id }
            // The plan is the caller's argument, so an unknown movement is a bad
            // argument, not bad state: every preset is validated before the first
            // row is touched, so a half-built week can never reach the board.
            val resolved = presets.map { preset ->
                val missing = preset.entries.filterNot { it.exerciseName in idByName }
                require(missing.isEmpty()) {
                    "Unknown exercises in ${Words.rite.s} \"${preset.name}\": ${missing.map { it.exerciseName }}"
                }
                preset
            }
            if (clearFirst) presetDao.clearAll()
            resolved.forEach { preset ->
                val presetId = presetDao.insertPreset(
                    PresetEntity(name = preset.name, note = preset.note, scheduledDay = preset.scheduledDay),
                )
                presetDao.insertEntries(
                    preset.entries.mapIndexed { position, entry ->
                        PresetEntryEntity(
                            presetId = presetId,
                            exerciseId = idByName.getValue(entry.exerciseName),
                            targetSets = entry.targetSets,
                            targetReps = entry.targetReps,
                            targetWeightKg = entry.targetWeightKg,
                            modifiers = entry.modifiers,
                            position = position,
                        )
                    },
                )
            }
        }
    }

    // ---------------------------------------------------------------- sessions

    /**
     * One live session at a time. Starting a second one left the first stranded
     * forever: it never completes, so it pays no XP, and the dashboard's resume
     * row only ever surfaces the most recent — the older trial became
     * invisible work. A live session with nothing ticked off is an accidental
     * start and is discarded; one with logged sets is real training, so it is
     * returned instead of being replaced, and the caller lands back in it.
     *
     * Returns the id of a live session that must be used instead of a new one,
     * or null when the field is clear.
     */
    private suspend fun claimLiveSession(): Long? {
        val live = sessionDao.liveSession() ?: return null
        val hasLoggedWork = sessionDao.setsFor(live.id).any { it.done }
        if (hasLoggedWork) return live.id
        sessionDao.deleteAbandoned(live.id)
        return null
    }

    private suspend fun trainingMode(): TrainingMode =
        profileDao.get()?.trainingMode?.let {
            runCatching { TrainingMode.valueOf(it) }.getOrDefault(TrainingMode.STRENGTH)
        } ?: TrainingMode.STRENGTH

    /**
     * What the progression engine prescribes for [entry], the [nth] block of its movement in the rite (0 for the
     * first), or null for work it does not progress (a run, yoga, bouldering). Only completed trials feed it, so
     * while a trial is live this answers the same as it did at the start; [resolveSuggestions] relies on that.
     * It only ever becomes a suggestion: a trial starts on the rite's own figures.
     */
    private suspend fun recommendationFor(
        entry: PresetEntryEntity,
        exercise: ExerciseEntity?,
        mode: TrainingMode,
        nth: Int,
    ): Progression.Recommendation? {
        val metric = runCatching { ExerciseMetric.valueOf(exercise?.metric ?: "REPS") }
            .getOrDefault(ExerciseMetric.REPS)
        // The test is "is strength work", not "is a hold". Left hold-only,
        // a run (DISTANCE_TIME) was handed a rep band and a load step and
        // told to hit N reps and add 2.5 kg. An activity movement — Yoga's
        // DURATION, Bouldering's ATTEMPTS_GRADE, that run — skips the
        // recommendation and carries the preset's own target through
        // unchanged; nothing better exists to prescribe it.
        if (!metric.isStrength) return null
        // Every logged session for this movement, newest first — not just
        // the last one. `fromSets` wrapped a single session, so the stall
        // counter was always 0 and the deload at STALLS_BEFORE_DELOAD could
        // never fire: a lifter grinding the same failed load got told to
        // repeat it forever.
        //
        // A hold's figure is seconds (durationSec, reps stay 0) and its
        // preset target is seconds too: the HOLD lever grows the seconds and
        // never adds load to a longer plank.
        val name = exercise?.name ?: ""
        val lever = Progression.leverFor(name, exercise?.isWeighted ?: false, metric)
        // A drop and a back-off are lighter by design: the engine reads the sets that carry the prescription, or the
        // last set of a trial would be taken for where the lifter had got to.
        // Like with like: an assisted pull-up's history never prescribes the plain pull-up, nor the reverse.
        val variation = Variation.key(entry.modifiers)
        val history = blockHistory(entry.exerciseId, nth, variation)
            .map { rows ->
                rows.filter { it.setKind() != SetKind.DROP && it.setKind() != SetKind.BACKOFF }
                    .sortedBy { it.setIndex }
                    .map { Progression.attemptOf(it.weightKg, it.reps, it.durationSec, hold = lever == Progression.Lever.HOLD) }
            }
            .filter { it.isNotEmpty() }
        return Progression.fromSessions(
            mode = mode,
            targetReps = entry.targetReps,
            minSets = entry.targetSets,
            sessions = history,
            muscleGroup = exercise?.muscleGroup ?: "",
            exerciseName = name,
            lever = lever,
            unit = Units.current,
            kit = loadKit(),
            // The lifter's own range, when the rite has one, replaces the training mode's band.
            range = SetScheme.decode(entry.setScheme).repsRange(SetKind.WORKING),
        )
    }

    /**
     * Each past trial's sets of the [nth] block of [exerciseId] (0 for the first), newest trial first: the movement's
     * Nth block by position, the rule [entriesByBlock] reads a rite by. A routine that names a movement twice has two
     * histories, not one with the two blocks' sets interleaved. A trial with fewer blocks of it has none to give.
     * Only sets done in [variation] ([Variation.key]) are read, [SetHistory.DEPTH] of them, so a deficit lift's history
     * is its own and a long run of the plain lift cannot starve it.
     */
    private suspend fun blockHistory(exerciseId: Long, nth: Int, variation: String): List<List<SetLogEntity>> =
        SetHistory.blocks(sessionDao.doneSetsNewestFirst(exerciseId), nth, variation)

    /** For each block of a trial's [rows], how many blocks of the same movement come before it: 0 for a movement's first. */
    private fun nthOfBlocks(rows: List<SetLogEntity>): Map<BlockKey, Int> {
        val seen = mutableMapOf<Long, Int>()
        return rows.map { it.blockKey }.distinct().sortedBy { it.position }.associateWith { block ->
            seen.getOrDefault(block.exerciseId, 0).also { seen[block.exerciseId] = it + 1 }
        }
    }

    /**
     * What the engine would change about [entry], the [nth] block of its movement, as a suggestion against the
     * figures the rite writes; null when it has nothing to add. A top scheme asks where its top set would start from
     * the lift's last top set; every other lift asks the engine.
     */
    private suspend fun suggestionFor(
        entry: PresetEntryEntity,
        exercise: ExerciseEntity?,
        mode: TrainingMode,
        nth: Int,
    ): ProgressionSuggestion? {
        exercise ?: return null
        val metric = runCatching { ExerciseMetric.valueOf(exercise.metric) }.getOrDefault(ExerciseMetric.REPS)
        val scheme = SetScheme.decode(entry.setScheme)
        if (metric == ExerciseMetric.REPS && scheme.type == SchemeType.TOP_BACKOFF) {
            val top = SchemePlan.suggestTop(
                lastTopMark(entry.exerciseId, entry.modifiers, nth), scheme.topReps, entry.targetWeightKg,
                nextTopLoad(liftLoadingFor(exercise, loadKit())),
            )
            return Suggestions.forTop(entry.targetWeightKg, scheme.topReps.first, top)
        }
        val recommendation = recommendationFor(entry, exercise, mode, nth) ?: return null
        return Suggestions.forRecommendation(entry.targetWeightKg, entry.targetReps, recommendation, hold = metric == ExerciseMetric.HOLD)
    }

    /** A block's suggestion with the rite entry it was read from. */
    private class ResolvedSuggestion(val block: BlockKey, val suggestion: ProgressionSuggestion)

    /** Every block of a live trial that has a suggestion, rebuilt from the rite the trial began from. A sealed trial has none. */
    private suspend fun resolveSuggestions(sessionId: Long): List<ResolvedSuggestion> {
        val session = sessionDao.byId(sessionId)?.takeIf { it.completedAtMs == null } ?: return emptyList()
        val presetId = session.presetId ?: return emptyList()
        val pw = presetDao.presetWithEntries(presetId) ?: return emptyList()
        val mode = trainingMode()
        val rows = sessionDao.setsFor(sessionId)
        val nth = nthOfBlocks(rows)
        return entriesByBlock(rows, beganWith(sessionId, pw)).mapNotNull { (block, entry) ->
            suggestionFor(entry, exerciseDao.byId(entry.exerciseId), mode, nth[block] ?: 0)
                ?.let { ResolvedSuggestion(block, it) }
        }
    }

    /**
     * What the progression engine would change for each block of a live trial, by block. A trial starts on exactly the
     * figures its rite writes; these are offered on the block, never applied ([acceptSuggestion]). Each block reads its
     * own rite entry and its own history, so a movement named twice has two suggestions.
     */
    suspend fun trialSuggestions(sessionId: Long): Map<BlockKey, ProgressionSuggestion> =
        resolveSuggestions(sessionId).associate { it.block to it.suggestion }

    /**
     * Applies [block]'s suggestion to the sets of the block still to log, and to nothing else: a set already ticked
     * keeps what was done. A top set's back-offs and a drop chain follow the new load as they would have, and a
     * warm-up ramp not yet used is re-planned from it. Null, and nothing written, when there is no suggestion that
     * moves a figure, the trial is sealed, or the block has no set left to change. Otherwise the figures of every set
     * still to log as they were before, for [undoSuggestion]; the end-of-workout offer reads what was done, so it sees
     * the accepted figures without anything saved here.
     */
    suspend fun acceptSuggestion(sessionId: Long, block: BlockKey): List<SetFigures>? = db.withTransaction {
        if (isSealed(sessionId)) return@withTransaction null
        val resolved = resolveSuggestions(sessionId).firstOrNull { it.block == block } ?: return@withTransaction null
        val suggestion = resolved.suggestion
        if (!suggestion.movesFigures) return@withTransaction null
        val exercise = exerciseDao.byId(block.exerciseId) ?: return@withTransaction null
        val rows = sessionDao.setsFor(sessionId).filter { it.blockKey == block }
        val targets = rows.filter { suggestion.touches(it.asSet()) }
        if (targets.isEmpty()) return@withTransaction null
        val before = rows.filter { !it.done }.map { SetFigures(it.id, it.reps, it.weightKg, it.durationSec) }
        targets.forEach { row ->
            sessionDao.updateSet(
                row.copy(
                    reps = if (suggestion.hold) row.reps else suggestion.reps,
                    durationSec = if (suggestion.hold) suggestion.reps else row.durationSec,
                    // A drop's load follows the set before it (refillDrops below), a back-off's follows its top.
                    weightKg = if (row.setKind() == SetKind.DROP) row.weightKg else suggestion.weightKg,
                ),
            )
        }
        sessionDao.setsFor(sessionId).filter { it.blockKey == block && it.setKind() == SetKind.TOP && !it.done }.forEach { refillBackoffs(it) }
        refillDrops(sessionId, block)
        if (suggestion.movesLoad) replanWarmups(sessionId, block, exercise)
        before
    }

    /**
     * Puts the sets [before] ([acceptSuggestion]) back to the figures they had, hand edits, back-off and drop loads and
     * the warm-up ramp included. Only a set still to log is touched: one logged since keeps what was logged.
     */
    suspend fun undoSuggestion(sessionId: Long, before: List<SetFigures>) = db.withTransaction {
        if (isSealed(sessionId)) return@withTransaction
        before.forEach { figures ->
            val row = sessionDao.setById(figures.id)?.takeIf { it.sessionId == sessionId && !it.done } ?: return@forEach
            sessionDao.updateSet(row.copy(reps = figures.reps, weightKg = figures.weightKg, durationSec = figures.durationSec))
        }
    }

    /** The unlogged warm-ups of [block], when nothing is logged yet and the ramp keeps its shape, re-planned from the first working load. */
    private suspend fun replanWarmups(sessionId: Long, block: BlockKey, exercise: ExerciseEntity) {
        val rows = sessionDao.setsFor(sessionId).filter { it.blockKey == block }
        if (rows.any { it.done && !it.isWarmup }) return
        val warmups = rows.filter { it.isWarmup && !it.done }.sortedBy { it.setIndex }
        if (warmups.isEmpty()) return
        val first = rows.filter { !it.isWarmup }.minByOrNull { it.setIndex }?.weightKg
        val ramp = WarmupRamp.planFor(
            exercise.name, exercise.muscleGroup, ExerciseMetric.REPS, first,
            kit = loadKit(), weighted = exercise.isWeighted, unit = Units.current,
        )
        if (ramp.size != warmups.size) return
        warmups.zip(ramp).forEach { (row, step) -> sessionDao.updateSet(row.copy(weightKg = step.weightKg, reps = step.reps)) }
    }

    private fun SetLogEntity.asSet() = SessionSet(
        id = id, exerciseId = exerciseId, exercisePosition = exercisePosition, setIndex = setIndex, reps = reps,
        weightKg = weightKg, done = done, durationSec = durationSec, kind = setKind(),
    )

    /** For each id, how many of the same id came before it in [ids]: 0 for a movement's first entry, 1 for its second. */
    private fun nthOfMovement(ids: List<Long>): List<Int> {
        val seen = mutableMapOf<Long, Int>()
        return ids.map { id -> seen.getOrDefault(id, 0).also { seen[id] = it + 1 } }
    }

    /**
     * The rite entry behind each block of a trial, [entries] being the rite's worked ones: a movement's Nth block (in
     * trial order) is its Nth entry (in rite order), the rule [RoutineUpdate.propose] reads a finished trial by. Blocks
     * the rite never named (added mid-trial) have no entry.
     */
    private fun entriesByBlock(rows: List<SetLogEntity>, entries: List<PresetEntryEntity>): Map<BlockKey, PresetEntryEntity> {
        val byMovement = entries.sortedBy { it.position }.groupBy { it.exerciseId }
        val seen = mutableMapOf<Long, Int>()
        return buildMap {
            rows.map { it.blockKey }.distinct().sortedBy { it.position }.forEach { block ->
                val nth = seen.getOrDefault(block.exerciseId, 0)
                seen[block.exerciseId] = nth + 1
                byMovement[block.exerciseId]?.getOrNull(nth)?.let { put(block, it) }
            }
        }
    }

    private fun SetLogEntity.carrySet() = CarrySet(id, setIndex, weightKg, done, reps, durationSec, isWarmup)

    /**
     * Called with a set's row [before] a write and the row [after] it. Any
     * figure the write moved is noted as the lifter's own; and when the write
     * ticks the set, its figures flow to the later sets of the movement
     * ([carriesFrom]). Runs inside the caller's transaction. It writes the
     * rows directly and skips [followLoad]: ticking never touches a "weighted"
     * tag, and a set that inherits a load only follows a set that already
     * carries it. A tick alone moves no figure, so it carries only what was
     * moved by hand before it.
     */
    private suspend fun carryFigures(before: SetLogEntity, after: SetLogEntity, figures: Set<CarryFigure>) {
        carryEdits.note(before.carrySet(), after.carrySet())
        if (before.done || !after.done) return
        // A top set and a drop hand nothing on (a top's load is not its back-offs', a drop is a step down from the
        // set before it), and only sets of the same kind follow each other: a back-off never takes the top's load.
        if (after.setKind() == SetKind.TOP || after.setKind() == SetKind.DROP) return
        val rows = sessionDao.setsFor(after.sessionId).filter { it.blockKey == after.blockKey && it.kind == after.kind }
        carriesFrom(after.carrySet(), rows.map { it.carrySet() }, figures, carryEdits).forEach { carry ->
            val row = rows.first { it.id == carry.id }
            sessionDao.updateSet(
                row.copy(
                    weightKg = carry.weightKg ?: row.weightKg,
                    reps = carry.reps ?: row.reps,
                    durationSec = carry.durationSec ?: row.durationSec,
                ),
            )
        }
    }

    /**
     * Starts a trial from a rite on exactly the figures the rite writes: every set's load, reps, count and scheme.
     * Nothing the progression engine thinks is applied; what it would change is offered on the block
     * ([trialSuggestions]) for the lifter to accept.
     *
     * [override] changes this one workout (a swapped lift, a lightened exercise): its entries start as
     * [SessionOverride.applyTo] makes them and the rite itself is never written. It is remembered under the new
     * session ([beganWith]) so what the workout was meant to be can be read back, and the end-of-workout offer to
     * update the rite stays quiet about changes the lifter chose here.
     */
    suspend fun startSessionFromPreset(presetId: Long, override: SessionOverride = SessionOverride.NONE): Long {
        var resumed = false
        val sessionId = db.withTransaction {
            claimLiveSession()?.let { resumed = true; return@withTransaction it }
            startFromPreset(presetId, override)
        }
        if (!resumed) adjustments.put(sessionId, override)
        return sessionId
    }

    private suspend fun startFromPreset(presetId: Long, override: SessionOverride): Long {
        val pw = presetDao.presetWithEntries(presetId) ?: error("${Words.rite.c} $presetId not found")
        val sessionId = sessionDao.insertSession(
            SessionEntity(
                presetId = presetId,
                label = pw.preset.name,
                startedAtMs = System.currentTimeMillis(),
                completedAtMs = null,
                xpAwarded = 0,
            ),
        )
        // A warm-up exercise is not a set row: it is ticked in the trial's warm-up step, so it never reaches the log,
        // the score, the records or the progression, and the worked entries are numbered without it.
        val working = pw.working.map { it.under(override) }
        val exerciseById = exerciseDao.let { dao -> working.map { it.exerciseId }.distinct().mapNotNull { dao.byId(it) } }
            .associateBy { it.id }

        val ordered = working.sortedBy { it.position }
        // Only entries that really are neighbours start the trial as a superset.
        val groups = Supersets.effective(ordered.map { it.supersetGroup })
        val sets = ordered.flatMapIndexed { entryPos, entry ->
            val exercise = exerciseById[entry.exerciseId]
            val metric = runCatching { ExerciseMetric.valueOf(exercise?.metric ?: "REPS") }
                .getOrDefault(ExerciseMetric.REPS)
            // A scheme lays the lift's sets out its own way. Only counted-in-reps lifts take one (a hold, like
            // any activity, is Straight), and a superset takes no drops: its members take turns, so a drop's
            // "straight on" has nowhere to go.
            val scheme = SetScheme.decode(entry.setScheme).let { decoded ->
                when {
                    metric != ExerciseMetric.REPS -> SetScheme()
                    decoded.type == SchemeType.DROPS && groups[entryPos] != null -> SetScheme(warmupRamp = decoded.warmupRamp)
                    else -> decoded
                }
            }
            if (exercise != null && (!scheme.isStraight || scheme.warmupRamp)) {
                return@flatMapIndexed schemeSets(sessionId, entryPos, entry, exercise, scheme, groups[entryPos])
            }
            // Seconds-in-durationSec stays HOLD-only: only a hold's preset
            // target is a duration. An activity entry keeps its own target
            // reps as the logged figure — the honest passthrough, since its
            // preset has no duration field to carry instead.
            val isHold = metric == ExerciseMetric.HOLD
            (0 until entry.targetSets).map { index ->
                SetLogEntity(
                    sessionId = sessionId,
                    exerciseId = entry.exerciseId,
                    exercisePosition = entryPos,
                    setIndex = index,
                    // A hold's prescription is seconds; its reps stay 0.
                    reps = if (isHold) 0 else entry.targetReps,
                    durationSec = if (isHold) entry.targetReps else null,
                    modifiers = entry.modifiers,
                    weightKg = entry.targetWeightKg,
                    done = false,
                    supersetGroup = groups[entryPos],
                )
            }
        }
        sessionDao.insertSets(sets)
        return sessionId
    }

    /** This entry as [override] makes it for one workout; the entry itself is a row of the rite and is never written. */
    private suspend fun PresetEntryEntity.under(override: SessionOverride): PresetEntryEntity {
        if (override.entries[id] == null) return this
        val entry = PresetEntry(
            id = id, exerciseId = exerciseId, targetSets = targetSets, targetReps = targetReps, targetWeightKg = targetWeightKg,
            modifiers = modifiers, position = position, supersetGroup = supersetGroup, scheme = SetScheme.decode(setScheme), warmup = warmup,
        )
        val lift = override.entries.getValue(id).swap?.exerciseId ?: exerciseId
        val counted = runCatching { ExerciseMetric.valueOf(exerciseDao.byId(lift)?.metric ?: "REPS") }.getOrDefault(ExerciseMetric.REPS) == ExerciseMetric.REPS
        val out = override.applyTo(entry, counted)
        return copy(
            exerciseId = out.exerciseId, targetSets = out.targetSets, targetReps = out.targetReps, targetWeightKg = out.targetWeightKg,
            modifiers = out.modifiers, setScheme = out.scheme.normalised().encode(),
        )
    }

    /** The working entries session [sessionId] began from: [pw]'s own, as that workout's [SessionOverride] changed them. */
    private suspend fun beganWith(sessionId: Long, pw: PresetWithEntries): List<PresetEntryEntity> {
        val override = adjustments.get(sessionId)
        return pw.working.map { it.under(override) }
    }

    /** The lift as the scheme planner rounds loads for it: its step, the vest and belt it is worn with, the body it adds to. */
    private suspend fun liftLoadingFor(exercise: ExerciseEntity, kit: LoadKit): LiftLoading {
        val metric = runCatching { ExerciseMetric.valueOf(exercise.metric) }.getOrDefault(ExerciseMetric.REPS)
        return LiftLoading(
            stepKg = Progression.weightStepKg(exercise.muscleGroup, exercise.name),
            worn = WornLoad.forLift(kit, exercise.name, exercise.isWeighted, metric),
            unit = Units.current,
            bodyweightKg = statDao.observeAll().first().firstOrNull()?.weightKg,
            bodyweightLift = !exercise.isWeighted,
            bodyShare = MovementDifficulty.bodyShare(exercise.name),
        )
    }

    /** The load a top set climbs to when its reps are full: the kit's next step, else the lift's own. */
    private fun nextTopLoad(lift: LiftLoading): (Double?) -> Double? = { kg ->
        val from = kg ?: 0.0
        lift.worn?.nextKg(from, lift.unit) ?: Progression.nextLoadKg(from, lift.stepKg, lift.unit)
    }

    /**
     * What the lifter's last sealed top set of the lift was: the one a new top set is asked to beat. Read from the
     * [nth] block of the movement (0 for the first), so a lift named twice in a rite keeps a mark for each.
     */
    private suspend fun lastTopMark(exerciseId: Long, modifiers: String, nth: Int = 0): TopMark? {
        val variation = Variation.key(modifiers)
        return blockHistory(exerciseId, nth, variation).firstNotNullOfOrNull { rows ->
            rows.firstOrNull { it.setKind() == SetKind.TOP }
        }?.let { TopMark(it.reps, it.weightKg) }
    }

    /**
     * The last top set of each block in [modifiersByBlock] (the modifiers it is done with now), for the trial's "last
     * top" line: the last one done by the block's movement in the same [Variation]. A lift that never had one is left out.
     */
    suspend fun lastTopMarks(modifiersByBlock: Map<BlockKey, String>): Map<BlockKey, TopMark> {
        val seen = mutableMapOf<Long, Int>()
        return modifiersByBlock.keys.sortedBy { it.position }.mapNotNull { block ->
            val nth = seen.getOrDefault(block.exerciseId, 0).also { seen[block.exerciseId] = it + 1 }
            lastTopMark(block.exerciseId, modifiersByBlock.getValue(block), nth)?.let { block to it }
        }.toMap()
    }

    /**
     * The sets of one lift whose rite entry has a scheme, or a warm-up ramp of its own: the ramp first (unticked, as
     * a warm-up always is), then the scheme's sets worked out from the entry's own load and reps. A top set starts on
     * the rite's load; back-offs start on their figure from it, and are refilled from the load actually logged
     * ([refillBackoffs]). Where the lift's last top set says to start higher, that is a suggestion ([trialSuggestions]).
     */
    private suspend fun schemeSets(
        sessionId: Long,
        position: Int,
        entry: PresetEntryEntity,
        exercise: ExerciseEntity,
        scheme: SetScheme,
        group: Int?,
    ): List<SetLogEntity> {
        val kit = loadKit()
        val lift = liftLoadingFor(exercise, kit)
        val planned = SchemePlan.plan(
            scheme = scheme,
            entrySets = entry.targetSets,
            entryReps = entry.targetReps,
            entryKg = entry.targetWeightKg,
            lift = lift,
        )
        val ramp = if (scheme.warmupRamp) {
            WarmupRamp.planFor(
                exercise.name, exercise.muscleGroup, ExerciseMetric.REPS, planned.firstOrNull()?.weightKg,
                kit = kit, weighted = exercise.isWeighted, unit = Units.current,
            )
        } else {
            emptyList()
        }
        fun row(index: Int, kind: SetKind, reps: Int, kg: Double?) = SetLogEntity(
            sessionId = sessionId,
            exerciseId = entry.exerciseId,
            exercisePosition = position,
            setIndex = index,
            reps = reps,
            weightKg = kg,
            modifiers = entry.modifiers,
            done = false,
            kind = kind.name,
            supersetGroup = group,
        )
        return ramp.mapIndexed { i, step -> row(i, SetKind.WARMUP, step.reps, step.weightKg) } +
            planned.mapIndexed { i, set -> row(ramp.size + i, set.kind, set.reps, set.weightKg) }
    }

    /**
     * The scheme a lift's back-offs follow in a trial: the rite entry's own when it is a top scheme (its percent or
     * fixed load), else the plain default, 10% lighter, which is what "Make set 1 a top set" promises.
     */
    private suspend fun backoffSchemeFor(sessionId: Long, block: BlockKey): SetScheme =
        presetScheme(sessionId, block).takeIf { it.type == SchemeType.TOP_BACKOFF } ?: SetScheme(type = SchemeType.TOP_BACKOFF)

    /**
     * After a top set is logged: the back-offs still to do take their load from it, by the lift's percent (or fixed
     * load), rounded to the lifter's kit; an unweighted bodyweight lift takes two reps off instead. A figure the
     * lifter moved by hand on a waiting back-off stays as they set it.
     */
    private suspend fun refillBackoffs(top: SetLogEntity) {
        val rows = sessionDao.setsFor(top.sessionId).filter { it.blockKey == top.blockKey }
        val waiting = rows.filter { it.setKind() == SetKind.BACKOFF && !it.done && it.setIndex > top.setIndex }
        if (waiting.isEmpty()) return
        val exercise = exerciseDao.byId(top.exerciseId) ?: return
        val lift = liftLoadingFor(exercise, loadKit())
        val backoff = SchemePlan.backoff(top.weightKg, top.reps, backoffSchemeFor(top.sessionId, top.blockKey), lift)
        waiting.forEach { row ->
            sessionDao.updateSet(
                row.copy(
                    weightKg = if (carryEdits.edited(row.id, CarryFigure.LOAD)) row.weightKg else backoff.weightKg,
                    reps = if (carryEdits.edited(row.id, CarryFigure.REPS)) row.reps else backoff.reps,
                ),
            )
        }
    }

    /**
     * The Drops of a lift still to do take their load from the set before them (the lift's drop percent lighter,
     * rounded to the kit): a trial begins with them planned from the rite's load, which is nothing at all on a lift
     * with no load yet, so they would otherwise stay at 0 whatever the lifter then lifts. A load typed on a drop stays.
     */
    private suspend fun refillDrops(sessionId: Long, block: BlockKey) {
        val rows = sessionDao.setsFor(sessionId).filter { it.blockKey == block && !it.isWarmup }.sortedBy { it.setIndex }
        if (rows.none { it.setKind() == SetKind.DROP && !it.done }) return
        val exercise = exerciseDao.byId(block.exerciseId) ?: return
        val lift = liftLoadingFor(exercise, loadKit())
        val percent = presetScheme(sessionId, block).takeIf { it.type == SchemeType.DROPS }?.dropPercent
            ?: SetScheme.DEFAULT_DROP_PERCENT
        val loads = SchemePlan.dropLoads(
            rows.map { SchemePlan.DropRow(it.setKind() == SetKind.DROP, it.done, it.weightKg, carryEdits.edited(it.id, CarryFigure.LOAD)) },
            percent, lift,
        )
        rows.forEachIndexed { i, row -> if (loads[i] != row.weightKg) sessionDao.updateSet(row.copy(weightKg = loads[i])) }
    }

    /**
     * "Make set 1 a top set" on a lift of a live trial: its first set becomes the top set and the rest back-offs,
     * 10% lighter than it. Today only: the rite is not touched (the end-of-trial offer may carry it there). False,
     * and nothing written, when the lift has fewer than two sets, already has a top set, is a superset's, or the
     * trial is sealed.
     */
    suspend fun makeTopSet(sessionId: Long, block: BlockKey): Boolean = db.withTransaction {
        if (isSealed(sessionId)) return@withTransaction false
        val rows = sessionDao.setsFor(sessionId).filter { it.blockKey == block && !it.isWarmup }.sortedBy { it.setIndex }
        if (rows.size < 2 || rows.any { it.setKind() == SetKind.TOP }) return@withTransaction false
        val top = rows.first().copy(kind = SetKind.TOP.name)
        sessionDao.updateSet(top)
        rows.drop(1).forEach { sessionDao.updateSet(it.copy(kind = SetKind.BACKOFF.name)) }
        refillBackoffs(top)
        true
    }

    /**
     * "Add a drop" after [setId] in a live trial: a new Drop set straight after it, a step lighter than it, and the
     * sets behind it move on. False, and nothing written, in a superset (its members take turns, there is no
     * straight-on), for a warm-up or in a sealed trial.
     */
    suspend fun addDropAfter(setId: Long): Boolean = db.withTransaction {
        val parent = sessionDao.setById(setId) ?: return@withTransaction false
        if (isSealed(parent.sessionId) || parent.isWarmup || parent.supersetGroup != null) return@withTransaction false
        val exercise = exerciseDao.byId(parent.exerciseId) ?: return@withTransaction false
        val lift = liftLoadingFor(exercise, loadKit())
        val percent = presetScheme(parent.sessionId, parent.blockKey).takeIf { it.type == SchemeType.DROPS }?.dropPercent
            ?: SetScheme.DEFAULT_DROP_PERCENT
        val rows = sessionDao.setsFor(parent.sessionId).filter { it.blockKey == parent.blockKey }
        rows.filter { it.setIndex > parent.setIndex }.forEach { sessionDao.updateSet(it.copy(setIndex = it.setIndex + 1)) }
        sessionDao.insertSets(
            listOf(
                parent.copy(
                    id = 0,
                    setIndex = parent.setIndex + 1,
                    kind = SetKind.DROP.name,
                    weightKg = SchemePlan.drop(parent.weightKg, percent, lift),
                    done = false,
                    rpe = null,
                ),
            ),
        )
        true
    }

    /** The block's own rite scheme in the trial's rite, or Straight when there is none. */
    private suspend fun presetScheme(sessionId: Long, block: BlockKey): SetScheme {
        val presetId = sessionDao.byId(sessionId)?.presetId ?: return SetScheme()
        val working = presetDao.presetWithEntries(presetId)?.let { beganWith(sessionId, it) } ?: return SetScheme()
        val entry = entriesByBlock(sessionDao.setsFor(sessionId), working)[block]
        return SetScheme.decode(entry?.setScheme)
    }

    /**
     * The warm-up exercises of the rite the trial began from, in rite order, for the trial's warm-up step. Read live
     * from the rite: it is a reminder list, and a warm-up is never a set row, so nothing of it is in the log.
     */
    suspend fun trialWarmups(sessionId: Long): List<WarmupStep.Item> {
        val presetId = sessionDao.byId(sessionId)?.presetId ?: return emptyList()
        val pw = presetDao.presetWithEntries(presetId) ?: return emptyList()
        return pw.entries.filter { it.warmup }.sortedBy { it.position }.mapNotNull { entry ->
            val exercise = exerciseDao.byId(entry.exerciseId) ?: return@mapNotNull null
            WarmupStep.Item(
                exerciseId = entry.exerciseId,
                name = exercise.name,
                metric = runCatching { ExerciseMetric.valueOf(exercise.metric) }.getOrDefault(ExerciseMetric.REPS),
                sets = entry.targetSets,
                reps = entry.targetReps,
                weightKg = entry.targetWeightKg,
            )
        }
    }

    /** The rite scheme of each block of the trial that has one, for the trial's labels; a lift with none is left out. */
    suspend fun trialSchemes(sessionId: Long): Map<BlockKey, SetScheme> {
        val presetId = sessionDao.byId(sessionId)?.presetId ?: return emptyMap()
        val working = presetDao.presetWithEntries(presetId)?.let { beganWith(sessionId, it) }.orEmpty()
        return entriesByBlock(sessionDao.setsFor(sessionId), working).mapNotNull { (block, entry) ->
            SetScheme.decode(entry.setScheme).takeIf { !it.isStraight || it.workingReps != null }?.let { block to it }
        }.toMap()
    }

    /** The rest suggested after a top set of [block] in the trial: its rite scheme's own, 3:00 by default. */
    suspend fun restAfterTopSeconds(sessionId: Long, block: BlockKey): Int =
        SchemePlan.restAfterTop(presetScheme(sessionId, block))

    /** Takes out the Drop sets after [setId] that are still to do ("End drops"), closing the gap in the lift's set numbers. */
    suspend fun endDropsAfter(setId: Long) = db.withTransaction {
        val from = sessionDao.setById(setId) ?: return@withTransaction
        if (isSealed(from.sessionId)) return@withTransaction
        val rows = sessionDao.setsFor(from.sessionId).filter { it.blockKey == from.blockKey }.sortedBy { it.setIndex }
        val gone = rows.filter { it.setIndex > from.setIndex }.takeWhile { it.setKind() == SetKind.DROP }.filter { !it.done }
        gone.forEach { sessionDao.deleteSet(it.id) }
        rows.filter { it.id !in gone.map { g -> g.id } }.forEachIndexed { index, row ->
            if (row.setIndex != index) sessionDao.updateSet(row.copy(setIndex = index))
        }
    }

    suspend fun startFreeformSession(label: String): Long = db.withTransaction {
        claimLiveSession()?.let { return@withTransaction it }
        sessionDao.insertSession(
            SessionEntity(
                presetId = null,
                label = label.ifBlank { Words.openTrial.c },
                startedAtMs = System.currentTimeMillis(),
                completedAtMs = null,
                xpAwarded = 0,
            ),
        )
    }

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
                kind = s.setKind(),
                supersetGroup = s.supersetGroup,
                rpe = s.rpe,
            )
        }.sortedWith(compareBy({ it.exercisePosition }, { it.setIndex }))
    }

    /**
     * Edits the counted fields of a set and NOTHING else.
     *
     * This used to take `durationSec`, `distanceM` and `grade` as parameters
     * defaulting to null, and its only caller passed four arguments — so
     * every tick of a checkbox wrote nulls over a set's seconds, distance and
     * climbing grade. Holds keep their figure in `durationSec`, so the bug
     * would have erased a hold the moment it was ticked.
     */
    suspend fun updateSet(setId: Long, reps: Int, weightKg: Double?, done: Boolean) = db.withTransaction {
        val current = sessionDao.setById(setId) ?: return@withTransaction
        if (isSealed(current.sessionId)) return@withTransaction
        val next = current.copy(reps = reps, weightKg = weightKg, done = done && !current.isWarmup).ratedOnlyIfDone()
        sessionDao.updateSet(next)
        followLoad(current, weightKg)
        carryFigures(current, next, carryFiguresFor(ExerciseMetric.REPS))
        // A drop still to do follows the load on the set before it, as that load stands now.
        if (!next.isWarmup && (current.weightKg != next.weightKg || current.done != next.done)) {
            refillDrops(next.sessionId, next.blockKey)
        }
        // A top set that is logged, or has its figures moved while logged, hands its load on to the back-offs still to do.
        if (next.setKind() == SetKind.TOP && next.done && (!current.done || current.weightKg != next.weightKg || current.reps != next.reps)) {
            refillBackoffs(next)
        }
    }

    /**
     * Changes a live-trial set's kind. A warm-up is stored unticked, so it drops
     * out of everything that counts ticked sets (XP, strength, records); the
     * writers above keep it unticked whatever a stale screen sends. Making a set
     * a warm-up unticks it; counting it again ticks it, since a set called a
     * warm-up was one the lifter had done. Moving between the counting kinds
     * (Working, Drop, Failure, Rest-pause) leaves the tick as it is.
     */
    suspend fun setKind(setId: Long, kind: SetKind) = db.withTransaction {
        val current = sessionDao.setById(setId) ?: return@withTransaction
        if (isSealed(current.sessionId)) return@withTransaction
        val wasWarmup = current.isWarmup
        val done = when {
            kind == SetKind.WARMUP -> false
            wasWarmup -> true
            else -> current.done
        }
        sessionDao.updateSet(current.copy(kind = kind.name, done = done).ratedOnlyIfDone())
    }

    /** A rating belongs to a logged working set: taking the tick off, or making it a warm-up, takes the rating off too. */
    private fun SetLogEntity.ratedOnlyIfDone(): SetLogEntity = if (done && !isWarmup) this else copy(rpe = null)

    /**
     * Rates a logged set of a live trial ([com.ironvellum.app.domain.Effort]: 5 to 10, or null to clear). A set that is
     * not logged, a warm-up and a sealed trial are left alone: a sealed trial is amended, not rated here.
     */
    suspend fun setRpe(setId: Long, rpe: Int?) = db.withTransaction {
        val current = sessionDao.setById(setId) ?: return@withTransaction
        if (isSealed(current.sessionId) || !current.done || current.isWarmup) return@withTransaction
        sessionDao.updateSet(current.copy(rpe = Effort.clamp(rpe)))
    }

    /**
     * Adds the warm-up ramp ([WarmupRamp]) before [block]'s first working set in a
     * live trial and returns the ids it made, lightest first: empty when the movement is
     * not loaded work, has no working load set, already has warm-ups or the trial is
     * sealed. The movement's own sets move down to make room, so the warm-ups sit first
     * and the working sets keep their order. Warm-ups are stored unticked, so they never
     * earn XP; records judge a working set by its place less the warm-ups ahead of it.
     */
    suspend fun addWarmupRamp(sessionId: Long, block: BlockKey): List<Long> {
        // Read ahead of the transaction: the gear prefs are a disk read, not a database one.
        val kit = loadKit()
        return db.withTransaction {
            if (isSealed(sessionId)) return@withTransaction emptyList()
            val exercise = exerciseDao.byId(block.exerciseId) ?: return@withTransaction emptyList()
            val metric = runCatching { ExerciseMetric.valueOf(exercise.metric) }.getOrDefault(ExerciseMetric.REPS)
            val rows = sessionDao.setsFor(sessionId).filter { it.blockKey == block }.sortedBy { it.setIndex }
            if (rows.isEmpty() || rows.any { it.isWarmup }) return@withTransaction emptyList()
            val first = rows.first()
            val plan = WarmupRamp.planFor(
                exercise.name, exercise.muscleGroup, metric, first.weightKg,
                kit = kit, weighted = exercise.isWeighted, unit = Units.current,
            )
            if (plan.isEmpty()) return@withTransaction emptyList()
            rows.forEach { sessionDao.updateSet(it.copy(setIndex = it.setIndex + plan.size)) }
            val made = plan.mapIndexed { index, step ->
                SetLogEntity(
                    sessionId = sessionId,
                    exerciseId = block.exerciseId,
                    exercisePosition = first.exercisePosition,
                    setIndex = index,
                    reps = step.reps,
                    weightKg = step.weightKg,
                    modifiers = first.modifiers,
                    done = false,
                    kind = SetKind.WARMUP.name,
                    supersetGroup = first.supersetGroup,
                )
            }
            sessionDao.insertSets(made)
        }
    }

    /**
     * Takes [setIds] back out of a live trial (Undo for the ramp) and closes the gap in
     * their movement's set numbers. Refused when the trial is sealed.
     */
    suspend fun removeSets(setIds: List<Long>) = db.withTransaction {
        val rows = setIds.mapNotNull { sessionDao.setById(it) }
        val first = rows.firstOrNull() ?: return@withTransaction
        if (isSealed(first.sessionId)) return@withTransaction
        rows.forEach { sessionDao.deleteSet(it.id) }
        sessionDao.setsFor(first.sessionId).filter { it.blockKey == first.blockKey }
            .sortedBy { it.setIndex }
            .forEachIndexed { index, row -> if (row.setIndex != index) sessionDao.updateSet(row.copy(setIndex = index)) }
    }

    /**
     * Sealed trials are immutable to the live-trial set editors: their XP and
     * strength are banked, and changing a row here would leave both stale.
     * Amending goes through [editSealedTrial], which settles the difference.
     */
    private suspend fun isSealed(sessionId: Long): Boolean =
        sessionDao.byId(sessionId)?.completedAtMs != null

    /** Edits a static hold: its figure is seconds, and reps stays 0. */
    suspend fun updateHoldSet(setId: Long, seconds: Int, weightKg: Double?, done: Boolean) = db.withTransaction {
        val current = sessionDao.setById(setId) ?: return@withTransaction
        if (isSealed(current.sessionId)) return@withTransaction
        val next = current.copy(
            reps = 0,
            durationSec = seconds.coerceAtLeast(0),
            weightKg = weightKg,
            done = done && !current.isWarmup,
        ).ratedOnlyIfDone()
        sessionDao.updateSet(next)
        followLoad(current, weightKg)
        carryFigures(current, next, carryFiguresFor(ExerciseMetric.HOLD))
    }

    /**
     * Keeps "weighted" in step with the load on a bodyweight movement (the
     * catalogue's isWeighted false: pull-up, dip, push-up) after [before] was
     * rewritten with [weightKg]: added when a set gains load, removed when the
     * last loaded set loses it. Every set edit routes here, the typed load's
     * "all sets" path included, each inside its caller's transaction so those
     * concurrent writes cannot interleave the read and the write. Only a
     * changed load counts, so ticking a set never touches a tag set by hand;
     * barbell work and completed sessions are left alone.
     */
    private suspend fun followLoad(before: SetLogEntity, weightKg: Double?) {
        if (before.weightKg == weightKg) return
        val session = sessionDao.byId(before.sessionId) ?: return
        if (session.completedAtMs != null) return
        if (exerciseDao.byId(before.exerciseId)?.isWeighted != false) return
        val rows = sessionDao.setsFor(before.sessionId).filter { it.blockKey == before.blockKey }
        val current = rows.firstOrNull()?.modifiers ?: return
        val next = modifiersAfterLoadChange(
            current = current,
            becameLoaded = (before.weightKg ?: 0.0) <= 0.0 && (weightKg ?: 0.0) > 0.0,
            anyLoaded = rows.any { (it.weightKg ?: 0.0) > 0.0 },
        )
        if (next != current) sessionDao.setModifiers(before.sessionId, before.exerciseId, before.exercisePosition, next)
    }

    /**
     * Edits an activity set: attempts, duration, distance and climbing grade.
     *
     * Separate from [updateSet] for the reason its comment gives — that one
     * must never touch these columns. Here the caller is metric-aware and
     * hands back the whole shape, including the fields its own metric does
     * not use, so nothing is silently nulled by an edit to a neighbour.
     */
    suspend fun updateActivitySet(
        setId: Long,
        reps: Int,
        durationSec: Int?,
        distanceM: Double?,
        grade: String?,
        weightKg: Double?,
        done: Boolean,
    ) = db.withTransaction {
        val current = sessionDao.setById(setId) ?: return@withTransaction
        if (isSealed(current.sessionId)) return@withTransaction
        val next = current.copy(
            reps = reps.coerceAtLeast(0),
            durationSec = durationSec?.coerceAtLeast(0),
            distanceM = distanceM?.coerceAtLeast(0.0),
            grade = grade?.take(WireLimits.GRADE_MAX)?.ifBlank { null },
            weightKg = weightKg,
            done = done && !current.isWarmup,
        ).ratedOnlyIfDone()
        sessionDao.updateSet(next)
        followLoad(current, weightKg)
        carryFigures(current, next, carryFiguresFor(ExerciseMetric.DURATION))
    }

    /**
     * Also the "add a movement mid-session" path. The read of the existing rows
     * and the insert share one transaction: computing setIndex from a separate
     * read let two quick taps mint the same index and break the "Set N" labels.
     * A movement the session did not start with lands after every other one —
     * a fixed 99 collided, so two added movements shared a position and merged
     * into one block.
     *
     * A set goes to one block: the one at [atPosition] when given (a rite may hold the movement twice, and the set
     * belongs to the block it was added on), else the movement's first.
     */
    suspend fun addExtraSet(
        sessionId: Long,
        exerciseId: Long,
        reps: Int,
        weightKg: Double?,
        modifiers: String,
        /** Seconds, when the movement is a static hold. */
        durationSec: Int? = null,
        atPosition: Int? = null,
    ): Long = db.withTransaction {
        val rows = sessionDao.setsFor(sessionId)
        val ofMovement = rows.filter { it.exerciseId == exerciseId }
        val block = atPosition ?: ofMovement.minOfOrNull { it.exercisePosition }
        val mine = ofMovement.filter { it.exercisePosition == block }
        val existing = mine.firstOrNull()
        val position = existing?.exercisePosition
            ?: ((rows.maxOfOrNull { it.exercisePosition } ?: -1) + 1)
        sessionDao.insertSets(
            listOf(
                SetLogEntity(
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    exercisePosition = position,
                    setIndex = mine.size,
                    reps = reps,
                    durationSec = durationSec,
                    weightKg = weightKg,
                    modifiers = existing?.modifiers ?: modifiers,
                    done = false,
                    // A set added to a linked movement stays in its superset.
                    supersetGroup = existing?.supersetGroup,
                ),
            ),
        ).first()
    }

    /**
     * Moves one movement's whole block up or down the session order ([Supersets.move]: a movement steps
     * over a whole superset rather than into it, and leaves its own by moving out past its edge).
     * Completed sessions are immutable, like every other set edit.
     */
    suspend fun moveSessionExercise(sessionId: Long, exercisePosition: Int, up: Boolean) = db.withTransaction {
        relayout(sessionId) { blocks ->
            val at = blocks.indexOfFirst { it.position == exercisePosition }
            Supersets.move(blocks.map { it.group }, at, up)
        }
    }

    /**
     * Links [block] with the one after it in a live trial, so the two (or three) are done a set
     * each in turn. False when there is no next movement, they already are, or a group would pass three.
     */
    suspend fun linkSupersetWithNext(sessionId: Long, block: BlockKey): Boolean = db.withTransaction {
        relayout(sessionId) { blocks ->
            val at = blocks.indexOfFirst { it.key == block }
            Supersets.link(blocks.map { it.group }, at, at + 1)
        }
    }

    /** Dissolves the superset holding [block] in a live trial; the movements keep their places. */
    suspend fun unlinkSuperset(sessionId: Long, block: BlockKey) = db.withTransaction {
        relayout(sessionId) { blocks ->
            val at = blocks.indexOfFirst { it.key == block }
            if (Supersets.runAt(blocks.map { it.group }, at) == null) {
                null
            } else {
                Supersets.Layout(blocks.indices.toList(), Supersets.unlink(blocks.map { it.group }, at))
            }
        }
    }

    /**
     * Writes a new order and superset numbers onto a live trial's rows: each movement's sets take the slot's
     * position (renumbered gap-free, so a block never shares one) and group. [plan] gets the movements in
     * trial order and answers null for no change; a sealed trial is left alone. True when it wrote.
     */
    private suspend fun relayout(sessionId: Long, plan: (List<Supersets.Block>) -> Supersets.Layout?): Boolean {
        if (isSealed(sessionId)) return false
        val rows = sessionDao.setsFor(sessionId)
        val blocks = Supersets.blocks(
            rows.map { SessionSet(id = it.id, exerciseId = it.exerciseId, exercisePosition = it.exercisePosition, setIndex = it.setIndex, reps = it.reps, done = it.done, kind = it.setKind(), supersetGroup = it.supersetGroup) },
        )
        val layout = plan(blocks) ?: return false
        layout.order.forEachIndexed { slot, old ->
            val key = blocks[old].key
            val group = layout.groups[slot]
            rows.filter { it.blockKey == key }.forEach { row ->
                if (row.exercisePosition != slot || row.supersetGroup != group) {
                    sessionDao.updateSet(row.copy(exercisePosition = slot, supersetGroup = group))
                }
            }
        }
        return true
    }

    /**
     * Removes a set and closes the gap in setIndex — leaving holes would break
     * ordering and the "Set N" labels. The last set of an exercise is kept so a
     * movement never silently vanishes mid-session; remove the exercise instead.
     * Completed sessions are immutable: their XP and strength are already banked.
     */
    suspend fun removeSet(setId: Long): RemovedSets? = db.withTransaction {
        val set = sessionDao.setById(setId) ?: return@withTransaction null
        val session = sessionDao.byId(set.sessionId) ?: return@withTransaction null
        if (session.completedAtMs != null) return@withTransaction null
        val siblings = sessionDao.setsFor(set.sessionId).filter { it.blockKey == set.blockKey }
        if (siblings.size <= 1) return@withTransaction null
        sessionDao.deleteSet(setId)
        siblings.asSequence()
            .filter { it.id != setId }
            .sortedBy { it.setIndex }
            .forEachIndexed { index, row ->
                if (row.setIndex != index) sessionDao.updateSet(row.copy(setIndex = index))
            }
        RemovedSets(listOf(set), siblings.associate { it.id to it.setIndex })
    }

    /**
     * What a removal took out of a live trial, for Undo: the rows themselves
     * (ids, positions, logged state and figures as they stood) and where every
     * set of the movement sat just before, the removed ones included.
     */
    data class RemovedSets(val rows: List<SetLogEntity>, val order: Map<Long, Int>)

    /**
     * Puts [removed] back exactly: the same ids, positions, logged state and
     * figures, and the movement's sets in their old order, in one transaction.
     * Refused (false) when the trial is sealed or gone; rows already present
     * are left alone, so a second Undo changes nothing. A set added to the
     * movement in the meantime follows the restored ones.
     */
    suspend fun restoreSets(removed: RemovedSets): Boolean = db.withTransaction {
        val first = removed.rows.firstOrNull() ?: return@withTransaction false
        val session = sessionDao.byId(first.sessionId) ?: return@withTransaction false
        if (session.completedAtMs != null) return@withTransaction false
        val missing = removed.rows.filter { sessionDao.setById(it.id) == null }
        if (missing.isEmpty()) return@withTransaction false
        sessionDao.insertSets(missing)
        val mine = sessionDao.setsFor(first.sessionId).filter { it.blockKey == first.blockKey }
        val order = restoredIndexes(removed.order, mine.map { it.id to it.setIndex })
        mine.forEach { row ->
            val index = order.getValue(row.id)
            if (row.setIndex != index) sessionDao.updateSet(row.copy(setIndex = index))
        }
        true
    }

    /**
     * Drops every set of one block from a live session: the deliberate way
     * to take a movement (a one-off added by mistake) back out. Completed
     * sessions are immutable, as in [removeSet].
     */
    suspend fun removeSessionExercise(sessionId: Long, block: BlockKey): RemovedSets? = db.withTransaction {
        val session = sessionDao.byId(sessionId) ?: return@withTransaction null
        if (session.completedAtMs != null) return@withTransaction null
        val sets = sessionDao.setsFor(sessionId).filter { it.blockKey == block }
        sets.forEach { sessionDao.deleteSet(it.id) }
        if (sets.isEmpty()) null else RemovedSets(sets, sets.associate { it.id to it.setIndex })
    }

    /**
     * Modifiers describe the movement as done in one block, so editing them applies to every set of
     * that block (and not to another block of the same movement) — including ones already ticked off,
     * which is the point: you realise mid-session that you were working at a deficit all along.
     */
    suspend fun setExerciseModifiers(sessionId: Long, block: BlockKey, modifiers: String) {
        val session = sessionDao.byId(sessionId) ?: return
        if (session.completedAtMs != null) return
        sessionDao.setModifiers(sessionId, block.exerciseId, block.position, modifiers)
    }

    /** How [switchBlockToTwin] went. */
    enum class TwinSwitch {
        /** Nothing moved: sealed trial, unknown exercise, or no set left to move. */
        NONE,

        /** No set of the block was ticked, so the whole block became the twin in place. */
        MOVED,

        /** Ticked sets stayed as they were; the unticked ones are a new block of the twin right after. */
        SPLIT,

        /** As [SPLIT], and the block's superset was full, so it was dissolved to make room. */
        SPLIT_UNLINKED,
    }

    /**
     * "Switch" on the dedicated-exercise prompt in a live trial: the unticked working sets of [block] become the
     * exercise [twinId], recorded with [modifiers] (the block's modifiers less the ones the twin's name carries).
     * Ticked sets keep their exercise and modifiers, so what was done stays as it was logged. A block with no ticked
     * set moves whole, warm-ups included, in place. Otherwise the block splits: the moved sets become a block of the
     * twin at the next position (later blocks shift down one, set numbers restart at 1 in both), and the warm-ups
     * stay with the ticked sets. A superset holds three at most, so a full one is dissolved first.
     */
    suspend fun switchBlockToTwin(sessionId: Long, block: BlockKey, twinId: Long, modifiers: String): TwinSwitch = db.withTransaction {
        if (isSealed(sessionId) || twinId == block.exerciseId || exerciseDao.byId(twinId) == null) return@withTransaction TwinSwitch.NONE
        var rows = sessionDao.setsFor(sessionId)
        val mine = rows.filter { it.blockKey == block }.sortedBy { it.setIndex }
        if (mine.isEmpty()) return@withTransaction TwinSwitch.NONE
        if (mine.none { it.done && !it.isWarmup }) {
            mine.forEach { sessionDao.updateSet(it.copy(exerciseId = twinId, modifiers = modifiers)) }
            return@withTransaction TwinSwitch.MOVED
        }
        val moving = mine.filter { !it.done && !it.isWarmup }
        if (moving.isEmpty()) return@withTransaction TwinSwitch.NONE

        val blocks = Supersets.blocks(
            rows.map { SessionSet(id = it.id, exerciseId = it.exerciseId, exercisePosition = it.exercisePosition, setIndex = it.setIndex, reps = it.reps, done = it.done, kind = it.setKind(), supersetGroup = it.supersetGroup) },
        )
        val at = blocks.indexOfFirst { it.key == block }
        val full = (Supersets.runAt(blocks.map { it.group }, at)?.count() ?: 0) >= Supersets.MAX_SIZE
        if (full) {
            relayout(sessionId) { current -> Supersets.Layout(current.indices.toList(), Supersets.unlink(current.map { it.group }, at)) }
            rows = sessionDao.setsFor(sessionId)
        }
        val position = rows.first { it.id == mine.first().id }.exercisePosition
        val movingIds = moving.map { it.id }.toSet()
        rows.filter { it.exercisePosition > position }.forEach { sessionDao.updateSet(it.copy(exercisePosition = it.exercisePosition + 1)) }
        val staying = rows.filter { it.exercisePosition == position && it.exerciseId == block.exerciseId && it.id !in movingIds }.sortedBy { it.setIndex }
        staying.forEachIndexed { index, row -> if (row.setIndex != index) sessionDao.updateSet(row.copy(setIndex = index)) }
        rows.filter { it.id in movingIds }.sortedBy { it.setIndex }.forEachIndexed { index, row ->
            sessionDao.updateSet(row.copy(exerciseId = twinId, exercisePosition = position + 1, setIndex = index, modifiers = modifiers))
        }
        if (full) TwinSwitch.SPLIT_UNLINKED else TwinSwitch.SPLIT
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
        val strengthScore: Int,
        val questBonus: Boolean,
        val durationMinutes: Long,
        val rankUp: String? = null,
        /** Inscriptions this seal's level-up banked ([Xp.rollsDue]); 0 when it did not level up or the levels had paid. */
        val inscriptionsBanked: Int = 0,
        /** The award in segments, one per movement and named bonus; [xpAwarded] is their sum. */
        val breakdown: Xp.Breakdown = Xp.Breakdown.EMPTY,
    )

    /**
     * The lifter's completed trials priced into one [SessionXp], with
     * [excludeSessionId] left out so a trial is never its own benchmark.
     */
    private suspend fun xpBook(scoring: SessionScoring, excludeSessionId: Long?, beforeMs: Long): SessionXp =
        scoring.book(
            completed = observeHistory().first(),
            bodyweightAt = SetRecords.bodyweightLookup(observeStats().first()),
            excludeSessionId = excludeSessionId,
            beforeMs = beforeMs,
        )

    /**
     * The trial-level facts of [session] (quest, week covered, rite as
     * planned, welcome back, first of the week), decided from the other
     * completed trials alone so a reseal can never pay one twice.
     */
    private suspend fun trialFacts(
        session: SessionEntity,
        scoring: SessionScoring,
        book: SessionXp,
        sets: List<SetLogEntity>,
        quest: Boolean,
    ): Xp.TrialFacts {
        val planned = session.presetId?.let { pid ->
            presetDao.presetWithEntries(pid)?.let { beganWith(session.id, it) }
                ?.groupBy { it.exerciseId }
                ?.mapValues { (_, rows) -> rows.sumOf { it.targetSets } }
        }
        return book.factsFor(session.toDomain(), scoring.domainSets(sets), planned, quest)
    }

    suspend fun completeSession(sessionId: Long): CompletionResult = db.withTransaction {
        val session = sessionDao.byId(sessionId) ?: error("${Words.trial.c} $sessionId not found")
        check(session.completedAtMs == null) { "${Words.trial.c} already ${Words.sealed.s}" }
        val doneSets = sessionDao.setsFor(sessionId).filter { it.done }
        val latestBodyweight = statDao.observeAll().first().firstOrNull()?.weightKg
        // Strength work (reps AND static holds) earns difficulty-weighted XP
        // and feeds the strength score; activities earn XP from ActivityScore
        // and contribute NOTHING to strength. SessionScoring owns the split.
        val scoring = SessionScoring(exerciseDao.observeAll().first())
        val sessionStrength = scoring.strength(doneSets, latestBodyweight, profileSex())

        // Quest bonus: completing the preset scheduled for today — ONCE, and
        // only if the trial contains at least one conquered set (doneSets,
        // gathered above). A tap-through completion used to mint the bonus
        // (and level-up rolls) for nothing.
        val today = LocalDate.now().dayOfWeek.value
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val questBonus = doneSets.isNotEmpty() && session.presetId?.let { pid ->
            presetDao.presetWithEntries(pid)?.preset?.scheduledDay == today &&
                sessionDao.countCompletedSince(pid, todayStart) == 0
        } == true
        val book = xpBook(scoring, excludeSessionId = sessionId, beforeMs = session.startedAtMs)
        val breakdown = scoring.breakdown(doneSets, latestBodyweight, book, trialFacts(session, scoring, book, doneSets, questBonus))
        val totalXpGain = breakdown.total

        val before = profileDao.get() ?: error("Profile missing")
        val levelBefore = Xp.levelFor(before.totalXp)
        val newTotal = before.totalXp + totalXpGain

        val lifetimeStrength = before.lifetimeStrength + sessionStrength
        profileDao.setLifetimeStrength(lifetimeStrength)

        val finishedAt = System.currentTimeMillis()
        val durationMinutes = ((finishedAt - session.startedAtMs) / 60000L).coerceAtLeast(1)
        // Before the seal: observeHistory holds sealed trials only, so this one is not in it yet.
        val rankBefore = if (highestBand != null) rankNow() else null
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
            sex = profileSex(),
            bodyweightAt = bodyweightLookup(),
        )
        val newly = Titles.newlyUnlocked(ledger, titleDao.heldIds().toSet())
        val now = finishedAt
        titleDao.insertAll(unlockRows(newly, ledger, now))
        settleNewTitles(newly)

        // A level-up from ANY source banks inscriptions — workout XP included.
        // Without this, levelling through sessions never paid out at all.
        val inscriptionsBanked = bankLevelRolls(levelBefore, Xp.levelFor(newTotal))

        val rankUp = highestBand?.let { store ->
            val outcome = RankUp.check(store.get(), rankBefore, rankNow())
            store.set(outcome.highest)
            outcome.rankUp
        }

        CompletionResult(
            xpAwarded = totalXpGain,
            levelBefore = levelBefore,
            levelAfter = Xp.levelFor(newTotal),
            classBefore = ArmyClass.forLevel(levelBefore).title,
            classAfter = ArmyClass.forLevel(Xp.levelFor(newTotal)).title,
            newTitles = newly,
            totalXp = newTotal,
            strengthScore = sessionStrength,
            questBonus = questBonus,
            durationMinutes = durationMinutes,
            rankUp = rankUp,
            inscriptionsBanked = inscriptionsBanked,
            breakdown = breakdown,
        )
    }.also { writeBackSaved(sessionId) }

    /**
     * Hands the committed workout to the Health Connect write-back. Runs after the transaction and never throws:
     * a seal or an edit that landed must not report failure because a copy elsewhere could not be made.
     */
    private suspend fun writeBackSaved(sessionId: Long) {
        val sink = writeBack ?: return
        try {
            sessionDao.byId(sessionId)?.let { sink.saved(it.toDomain()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Ironvellum", "write-back skipped", e)
        }
    }

    /**
     * What each movement of the trial's rite prescribed: its target reps, or
     * seconds for a hold, by exercise id. Empty for a free trial or a rite
     * since deleted. The share card scores a set against it; a rite updated
     * after the trial scores the trial against the newer prescription.
     */
    suspend fun exerciseTargets(sessionId: Long): Map<Long, Int> {
        val presetId = sessionDao.byId(sessionId)?.presetId ?: return emptyMap()
        val pw = presetDao.presetWithEntries(presetId) ?: return emptyMap()
        return buildMap {
            beganWith(sessionId, pw).sortedBy { it.position }.filter { it.targetReps > 0 }.forEach { putIfAbsent(it.exerciseId, it.targetReps) }
        }
    }

    /** The preset a finished session came from, and the entries that session would change. */
    data class RoutineUpdateOffer(
        val presetId: Long,
        val presetName: String,
        val changes: List<RoutineUpdate.Change>,
        /** Lifts swapped for others during the trial, each offered as a rite change of its own. */
        val swaps: List<RiteSwap.Swap> = emptyList(),
        /** Lifts added during the trial, each offered unticked. */
        val additions: List<RoutineUpdate.Addition> = emptyList(),
        /** Supersets made during the trial that the rite does not hold, each offered unticked. */
        val links: List<RoutineUpdate.Link> = emptyList(),
    )

    /**
     * What updating the session's preset to match it would change, or null:
     * the session is not completed, came from no preset, its preset is gone,
     * or what was done matches the plan. Read-only; see [applyRoutineUpdate].
     */
    suspend fun routineUpdateFor(sessionId: Long): RoutineUpdateOffer? {
        val session = sessionDao.byId(sessionId)?.takeIf { it.completedAtMs != null } ?: return null
        val pw = session.presetId?.let { presetDao.presetWithEntries(it) } ?: return null
        val catalogue = exerciseDao.observeAll().first().associateBy { it.id }
        // What the workout was meant to be: the rite's entries as the override chosen at its start changed them, so a
        // swap or a lightened exercise picked on Today is not offered back as an edit to the rite.
        // A warm-up exercise is never offered a change: it was not worked, so the trial says nothing about it.
        val entries = beganWith(sessionId, pw).sortedBy { it.position }.map { e ->
            PresetEntry(
                id = e.id,
                exerciseId = e.exerciseId,
                exerciseName = catalogue[e.exerciseId]?.name ?: "Unknown",
                targetSets = e.targetSets,
                targetReps = e.targetReps,
                targetWeightKg = e.targetWeightKg,
                modifiers = e.modifiers,
                position = e.position,
                scheme = SetScheme.decode(e.setScheme),
            )
        }
        val sets = sessionDao.setsFor(sessionId).map { s ->
            SessionSet(
                id = s.id,
                exerciseId = s.exerciseId,
                exerciseName = catalogue[s.exerciseId]?.name ?: "Unknown",
                exercisePosition = s.exercisePosition,
                setIndex = s.setIndex,
                reps = s.reps,
                weightKg = s.weightKg,
                modifiers = s.modifiers,
                done = s.done,
                durationSec = s.durationSec,
                kind = s.setKind(),
                supersetGroup = s.supersetGroup,
            )
        }
        // A lift swapped in at the start stands in a rite entry that is another lift: its figures are no edit to that entry.
        val swappedIn = adjustments.get(sessionId).entries.filterValues { it.swap != null }.keys
        val changes = RoutineUpdate.propose(entries, sets) { id ->
            catalogue[id]?.metric?.let { runCatching { ExerciseMetric.valueOf(it) }.getOrNull() }
        }.filter { it.before.id !in swappedIn }
        val swaps = RiteSwap.detect(entries, sets) { id ->
            catalogue[id]?.metric?.let { runCatching { ExerciseMetric.valueOf(it) }.getOrNull() }
        }
        val metricOf = { id: Long ->
            catalogue[id]?.metric?.let { runCatching { ExerciseMetric.valueOf(it) }.getOrNull() }
        }
        val additions = RoutineUpdate.additions(
            riteIds = pw.entries.map { it.exerciseId }.toSet() + entries.map { it.exerciseId },
            sets = sets,
            excluded = swaps.map { it.toId }.toSet(),
            metricOf = metricOf,
        )
        val links = RoutineUpdate.links(entries, sets, additions.map { it.entry.exerciseId }.toSet())
        if (changes.isEmpty() && swaps.isEmpty() && additions.isEmpty() && links.isEmpty()) return null
        return RoutineUpdateOffer(pw.preset.id, pw.preset.name, changes, swaps, additions, links)
    }

    /**
     * Writes the accepted changes into the preset in one transaction. Only the
     * listed entries are touched, and on each only the fields the change moves
     * (narrow a change to the lifter's ticks with [RoutineUpdate.Change.only]);
     * an entry since deleted or moved to another preset is skipped. Logged
     * sessions are never touched.
     */
    suspend fun applyRoutineUpdate(
        presetId: Long,
        accepted: List<RoutineUpdate.Change>,
        swaps: List<RiteSwap.Swap> = emptyList(),
        additions: List<RoutineUpdate.Addition> = emptyList(),
        links: List<RoutineUpdate.Link> = emptyList(),
    ) = db.withTransaction {
        val current = presetDao.presetWithEntries(presetId)?.entries?.associateBy { it.id } ?: return@withTransaction
        keepSwaps(presetId, swaps)
        accepted.forEach { change ->
            val row = current[change.before.id] ?: return@forEach
            val (before, after) = change
            presetDao.updateEntry(
                row.copy(
                    targetSets = if (after.targetSets != before.targetSets) after.targetSets else row.targetSets,
                    targetReps = if (after.targetReps != before.targetReps) after.targetReps else row.targetReps,
                    targetWeightKg = if (after.targetWeightKg != before.targetWeightKg) after.targetWeightKg else row.targetWeightKg,
                    modifiers = if (after.modifiers != before.modifiers) after.modifiers else row.modifiers,
                    setScheme = if (after.scheme != before.scheme) after.scheme.normalised().encode() else row.setScheme,
                ),
            )
        }
        if (additions.isNotEmpty() || links.isNotEmpty()) arrangeRite(presetId, additions, links)
    }

    /**
     * Puts the kept [additions] into the rite and makes the kept [links] ([RoutineUpdate.arrange] decides the
     * order and groups), writing only the rows that moved and inserting the new ones.
     */
    private suspend fun arrangeRite(presetId: Long, additions: List<RoutineUpdate.Addition>, links: List<RoutineUpdate.Link>) {
        val rows = presetDao.presetWithEntries(presetId)?.entries?.associateBy { it.id } ?: return
        val entries = rows.values.map { e ->
            PresetEntry(
                id = e.id, exerciseId = e.exerciseId, targetSets = e.targetSets, targetReps = e.targetReps,
                targetWeightKg = e.targetWeightKg, modifiers = e.modifiers, position = e.position,
                supersetGroup = e.supersetGroup, scheme = SetScheme.decode(e.setScheme), warmup = e.warmup,
            )
        }
        val arranged = RoutineUpdate.arrange(entries, additions, links)
        val fresh = mutableListOf<PresetEntryEntity>()
        arranged.forEach { e ->
            val row = rows[e.id]
            if (row == null) {
                fresh += PresetEntryEntity(
                    presetId = presetId, exerciseId = e.exerciseId, targetSets = e.targetSets, targetReps = e.targetReps,
                    targetWeightKg = e.targetWeightKg, modifiers = e.modifiers, position = e.position,
                    supersetGroup = e.supersetGroup, setScheme = e.scheme.normalised().encode(),
                )
            } else {
                val scheme = if (e.scheme != SetScheme.decode(row.setScheme)) e.scheme.normalised().encode() else row.setScheme
                if (row.position != e.position || row.supersetGroup != e.supersetGroup || scheme != row.setScheme) {
                    presetDao.updateEntry(row.copy(position = e.position, supersetGroup = e.supersetGroup, setScheme = scheme))
                }
            }
        }
        if (fresh.isNotEmpty()) presetDao.insertEntries(fresh)
    }

    /**
     * Puts each swapped-in lift in its entry's place, keeping the entry's
     * sets, reps and order. An entry since deleted, moved to another rite or
     * already changed to some other lift is left alone.
     */
    suspend fun keepSwaps(presetId: Long, swaps: List<RiteSwap.Swap>) = db.withTransaction {
        val current = presetDao.presetWithEntries(presetId)?.entries?.associateBy { it.id } ?: return@withTransaction
        swaps.forEach { swap ->
            val row = current[swap.entry.id]?.takeIf { it.exerciseId == swap.entry.exerciseId } ?: return@forEach
            presetDao.updateEntry(
                row.copy(
                    exerciseId = swap.after.exerciseId,
                    targetReps = swap.after.targetReps,
                    targetWeightKg = swap.after.targetWeightKg,
                    modifiers = swap.after.modifiers,
                ),
            )
        }
    }

    /** The unfinished session, if any: what Today and Train offer to continue. */
    fun observeLiveSession(): Flow<WorkoutSession?> =
        sessionDao.observeLiveSession().map { it?.toDomain() }

    /**
     * The unfinished session to reopen on launch, or null. Only one started
     * within [RESUME_WINDOW_MS]: an old trial left open is offered on Today,
     * not forced on the lifter.
     */
    suspend fun resumableSessionId(nowMs: Long): Long? =
        sessionDao.liveSession()?.takeIf { nowMs - it.startedAtMs in 0..RESUME_WINDOW_MS }?.id

    /**
     * Full activity log: every completed session with its sets.
     *
     * Distinct, because Room re-runs the query on ANY write to its tables, and a tick in a live trial writes
     * `set_logs`: without this every consumer re-priced the whole history on each tick, which at three years is
     * most of a second of work on the main thread for a log that had not changed.
     */
    fun observeHistory(): Flow<List<Pair<WorkoutSession, List<SessionSet>>>> = combine(
        sessionDao.observeCompletedWithSets(),
        exerciseDao.observeAll(),
    ) { sessions, exercises ->
        val names = exercises.associate { it.id to it.name }
        sessions.map { it.toHistoryRow(names) }
    }.distinctUntilChanged()

    // ------------------------------------------------------- dedicated-exercise convert

    /** What converting would move now, for the confirm screen and the one-time card ([TwinConversion]). */
    fun observeTwinMoves(): Flow<List<TwinConversion.Move>> = combine(observeHistory(), observeExercises()) { history, exercises ->
        TwinConversion.plan(
            history.flatMap { (session, sets) ->
                sets.map { TwinConversion.Row(session.id, it.exercisePosition, it.exerciseId, it.weightKg, it.modifiers, it.warmup) }
            },
            exercises,
        )
    }.distinctUntilChanged()

    /** What [convertTwins] did. */
    data class TwinConvertResult(val groups: List<TwinConversion.Group>, val sets: Int, val sessions: Int)

    /**
     * Moves every past set logged as a base exercise with a modifier or load onto its dedicated exercise
     * ([TwinConversion]), keeping the other modifiers. The whole archive is handed to [backup] first, and a failure
     * there stops everything. Then ONE transaction writes every group, or none: a throw rolls it all back.
     *
     * Each touched trial gets `editedAtMs` = [nowMs], so cloud sync re-uploads it and its amended-trial pass deletes
     * the rows under the old names, and `sealedXp` = what it was sealed at ([sealedBase]), so being marked amended
     * changes neither what an amendment may raise nor whether it can be reopened. Stored XP and strength scores are
     * left as they were paid; records, history, fatigue and deeds read the sets, so they follow.
     *
     * [afterGroup] is a test seam, called with the number of groups written so far inside the transaction.
     */
    suspend fun convertTwins(
        backup: suspend (String) -> Unit,
        nowMs: Long = System.currentTimeMillis(),
        afterGroup: (Int) -> Unit = {},
    ): TwinConvertResult {
        if (twinMovesNow().isEmpty()) return TwinConvertResult(emptyList(), 0, 0)
        backup(exportJson())
        val result = db.withTransaction {
            val moves = twinMovesNow()
            val groups = TwinConversion.groups(moves)
            groups.forEachIndexed { index, group ->
                moves.filter { it.from == group.from && it.to == group.to }.forEach { move ->
                    sessionDao.setsFor(move.block.sessionId)
                        .filter { it.exercisePosition == move.block.position && it.exerciseId == move.block.exerciseId }
                        .forEach { sessionDao.updateSet(it.copy(exerciseId = move.toId, modifiers = move.modifiers)) }
                }
                afterGroup(index + 1)
            }
            val touched = moves.map { it.block.sessionId }.distinct()
            touched.forEach { id ->
                val session = sessionDao.byId(id) ?: return@forEach
                sessionDao.updateSession(session.copy(editedAtMs = nowMs, sealedXp = sealedBase(session)))
            }
            TwinConvertResult(groups, moves.sumOf { it.sets }, touched.size) to touched
        }
        result.second.forEach { writeBackSaved(it) }
        return result.first
    }

    private suspend fun twinMovesNow(): List<TwinConversion.Move> {
        val rows = sessionDao.completedSessions().flatMap { session ->
            sessionDao.setsFor(session.id).map {
                TwinConversion.Row(session.id, it.exercisePosition, it.exerciseId, it.weightKg, it.modifiers, it.isWarmup)
            }
        }
        return TwinConversion.plan(rows, exerciseDao.observeAll().first().syncCustomExercises().map { it.toDomain() })
    }

    /** One completed session with its sets, as [observeHistory] holds it; null while it is live or gone. */
    fun observeWorkout(sessionId: Long): Flow<Pair<WorkoutSession, List<SessionSet>>?> = combine(
        sessionDao.observeCompletedWithSetsById(sessionId),
        exerciseDao.observeAll(),
    ) { row, exercises ->
        val names = exercises.associate { it.id to it.name }
        row?.toHistoryRow(names)
    }

    /** Every completed session without its sets, newest first: for what only reads dates and rites. */
    suspend fun completedSessions(): List<WorkoutSession> = sessionDao.completedSessions().map { it.toDomain() }

    private fun SessionWithSets.toHistoryRow(names: Map<Long, String>): Pair<WorkoutSession, List<SessionSet>> =
        session.toDomain() to sets.map { s ->
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
                kind = s.setKind(),
                supersetGroup = s.supersetGroup,
                rpe = s.rpe,
            )
        }

    /** Complete record of one movement in one [Variation] (the plain one by default): its logged sets plus derived stats. */
    fun observeExerciseHistory(exerciseId: Long, variation: String? = null): Flow<ExerciseHistory?> = combine(
        sessionDao.observeExerciseSets(exerciseId),
        exerciseDao.observeAll(),
        statDao.observeAll(),
    ) { rows, exercises, stats ->
        val exercise = exercises.firstOrNull { it.id == exerciseId }?.toDomain() ?: return@combine null
        val bodyweight = stats.firstOrNull()?.weightKg
        val readings = stats.map { StatEntry(it.id, it.takenAtMs, it.weightKg, it.heightCm, it.bodyFatPct) }
        ExerciseHistoryCalculator.build(exercise, rows, bodyweight, variation = variation, bodyweightAt = SetRecords.bodyweightLookup(readings))
    }

    /**
     * What the History tab builds one movement's content from: its done sets and the weigh-ins, rebuilt when either
     * changes. The tab builds the log itself because the variation it shows is its own choice.
     */
    fun observeExerciseLog(exercise: Exercise): Flow<ExerciseLogInputs> =
        combine(sessionDao.observeExerciseLog(exercise.id), observeStats()) { rows, stats ->
            ExerciseLogInputs(rows, SetRecords.bodyweightLookup(stats))
        }


    /**
     * Server caps: title <= 80, public note <= 500 (see supabase/migrations/0001_baseline.sql).
     * Trimming locally means a sync can never fail on a value we already accepted.
     * The private note has no server constraint — it never leaves the device.
     */
    suspend fun setSessionTitle(sessionId: Long, title: String) {
        sessionDao.setTitle(sessionId, title.trim().take(WireLimits.SESSION_TITLE_MAX))
        writeBackSaved(sessionId)
    }

    suspend fun setSessionNote(sessionId: Long, note: String) {
        sessionDao.setNote(sessionId, note.trim().take(WireLimits.SESSION_NOTE_MAX))
        writeBackSaved(sessionId)
    }

    /** This trial's per-exercise notes, by exercise id. */
    fun observeExerciseNotes(sessionId: Long): Flow<Map<Long, String>> =
        sessionDao.observeExerciseNotes(sessionId).map { rows -> rows.associate { it.exerciseId to it.note } }

    /** Writes one exercise's note for this trial; blank clears it. A trial that is gone takes no note. */
    suspend fun setExerciseNote(sessionId: Long, exerciseId: Long, note: String) = db.withTransaction {
        if (sessionDao.byId(sessionId) == null) return@withTransaction
        val text = note.trim().take(EXERCISE_NOTE_MAX)
        if (text.isEmpty()) {
            sessionDao.deleteExerciseNote(sessionId, exerciseId)
        } else {
            sessionDao.putExerciseNote(SessionExerciseNoteEntity(sessionId, exerciseId, text))
        }
    }

    /** For each exercise that has one, the note it was last given at a sealed trial other than this one. */
    suspend fun lastExerciseNotes(sessionId: Long, exerciseIds: Collection<Long>): Map<Long, String> =
        exerciseIds.mapNotNull { id -> sessionDao.lastExerciseNote(id, sessionId)?.let { id to it } }.toMap()

    suspend fun setSessionPrivateNote(sessionId: Long, privateNote: String) {
        sessionDao.setPrivateNote(sessionId, privateNote.take(WireLimits.PRIVATE_NOTE_MAX))
    }

    /**
     * Changes the workout's row only; the push fingerprint includes the
     * audience, so the next push re-uploads this workout with it.
     */
    suspend fun setSessionAudience(sessionId: Long, audience: SessionAudience) {
        sessionDao.setAudience(sessionId, audience.wire)
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
        imported = imported,
        audience = SessionAudience.fromWire(audience),
        editedAtMs = editedAtMs,
        sealedXp = sealedXp,
    )

    // ---------------------------------------------------------------- stats

    fun observeStats(): Flow<List<StatEntry>> =
        statDao.observeAll().map { list ->
            list.map { StatEntry(it.id, it.takenAtMs, it.weightKg, it.heightCm, it.bodyFatPct) }
        }

    /** The Strength Rank written out ([Rank.breakdown]); null while unranked. */
    fun observeRankBreakdown(): Flow<RankBreakdown?> =
        combine(observeHistory(), observeStats(), observeBodyProfile(), observeSkillPractices(), Units.flow) { history, stats, body, practices, unit ->
            Rank.breakdown(history, SetRecords.bodyweightLookup(stats), body.second, System.currentTimeMillis(), practices, unit)
        }

    private suspend fun rankNow(): String? = Rank.current(
        observeHistory().first(),
        bodyweightLookup(),
        profileSex(),
        System.currentTimeMillis(),
        observeSkillPractices().first(),
    )

    /**
     * Height is profile-owned (Settings), so the caller never supplies it: the
     * profile height is stamped onto the row. With no height on record the row
     * still lands (weight history stands alone) using the 0.0 sentinel, which
     * every BMI/FFMI consumer already guards via BodyStats' <= 0 checks.
     */
    /**
     * Defence in depth: the screens check [BodyLimits], and so does this —
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
        // A weigh-in is new information about work already done: sessions
        // completed before any measurement could not be scored at all.
        rescoreUnweighedSessions()
    }

    /**
     * Records the weight typed on a step that can be walked more than once:
     * today's reading is corrected in place when there is one, otherwise a
     * new one is added. Same validation as [addStat].
     */
    suspend fun logTodaysWeight(weightKg: Double) {
        val latest = statDao.observeAll().first().firstOrNull()
        if (latest == null || !WeighIn.sameDay(latest.takenAtMs, System.currentTimeMillis())) {
            addStat(weightKg, null)
            return
        }
        require(BodyLimits.validWeight(weightKg)) { "weight $weightKg kg is outside ${BodyLimits.WEIGHT_KG}" }
        statDao.updateWeight(latest.id, weightKg, profileDao.get()?.heightCm ?: latest.heightCm)
        rescoreUnweighedSessions()
    }

    /**
     * Repairs completed sessions that banked no strength score because no
     * bodyweight was on record when they finished. The index is body-scaled,
     * so with nothing to scale by [StrengthIndex.sessionScore] returns null
     * and the session stored a zero — permanently, since nothing ever looked
     * again. The work was real; the measurement was merely late.
     *
     * Scored against the bodyweight in force at the session, falling back to
     * the EARLIEST reading on record — the rule [SetRecords.bodyweightLookup]
     * already applies to a set logged before any weigh-in, reused here so the
     * two cannot disagree about the same session.
     *
     * XP is deliberately NOT revisited: it was awarded under an assumed
     * bodyweight and banked XP is never restated. Only the strength score,
     * which was never computed at all, is filled in.
     */
    private suspend fun rescoreUnweighedSessions() {
        if (sessionDao.unscoredStrengthSessionCount() == 0) return
        rescoreStrengthScores(onlyUnscored = true)
    }

    /**
     * Recomputes completed sessions' strength scores from their stored sets
     * under the CURRENT formula and re-sums the lifetime figure.
     *
     * [onlyUnscored] keeps the weigh-in trigger cheap: it repairs only the
     * sessions that banked no score (guarded upstream by the precise count
     * query). The formula-change path ([ensureSeeded] against
     * [StrengthIndex.SCORING_VERSION]) passes false — every stored score was
     * computed under the old formula and is stale by definition.
     *
     * Recomputed from the sessions rather than nudged by a delta: the sum IS
     * the lifetime figure, and it is what the cloud push recomputes too, so a
     * drifted running total is corrected here. XP is never touched: it was
     * awarded under an assumed bodyweight and banked XP is never restated.
     */
    private suspend fun rescoreStrengthScores(onlyUnscored: Boolean) {
        db.withTransaction {
            val stats = statDao.observeAll().first()
            val bodyweightAt = SetRecords.bodyweightLookup(
                stats.map { StatEntry(it.id, it.takenAtMs, it.weightKg, it.heightCm, it.bodyFatPct) },
            )
            val metrics = exerciseDao.observeAll().first().syncCustomExercises().associate { row ->
                row.id to runCatching { ExerciseMetric.valueOf(row.metric) }
                    .getOrDefault(ExerciseMetric.REPS)
            }
            val names = exerciseDao.observeAll().first().associate { it.id to it.name }
            val groups = exerciseDao.observeAll().first().associate { row ->
                row.id to (runCatching { MuscleGroup.valueOf(row.muscleGroup) }.getOrNull() ?: MuscleGroup.CORE)
            }
            // The sex in force is the CURRENT one: a lifter's history is restated
            // onto the scale they are ranked on today, because a leaderboard that
            // mixed two scales for one person would be the exact incomparability
            // SCORING_VERSION exists to prevent.
            val sex = profileSex()
            sessionDao.observeCompletedWithSets().first().forEach { row ->
                if (onlyUnscored && row.session.strengthScore != 0) return@forEach
                val efforts = row.sets
                    .filter { it.done && (metrics[it.exerciseId] ?: ExerciseMetric.REPS).isStrength }
                    .map { set ->
                        StrengthIndex.Effort(
                            exerciseName = names[set.exerciseId] ?: "",
                            reps = set.reps,
                            holdSeconds = set.durationSec
                                .takeIf { metrics[set.exerciseId] == ExerciseMetric.HOLD },
                            addedKg = set.weightKg,
                            muscleGroup = groups[set.exerciseId] ?: MuscleGroup.CORE,
                        )
                    }
                if (efforts.isEmpty()) return@forEach
                val score =
                    StrengthIndex.sessionScore(efforts, bodyweightAt(row.session.startedAtMs), sex) ?: 0
                if (score != 0 && score != row.session.strengthScore) {
                    sessionDao.updateSession(row.session.copy(strengthScore = score))
                }
            }
            profileDao.setLifetimeStrength(sessionDao.lifetimeStrength())
        }
    }

    suspend fun deleteStat(id: Long) = statDao.delete(id)

    /**
     * Puts a deleted reading back with its original id, time, height and body
     * fat, for the history page's Undo. The id is free again once the row is
     * gone, so nothing else can have taken it.
     */
    suspend fun restoreStat(stat: StatEntry) = statDao.insert(
        StatEntity(
            id = stat.id,
            takenAtMs = stat.takenAtMs,
            weightKg = stat.weightKg,
            heightCm = stat.heightCm,
            bodyFatPct = stat.bodyFatPct,
        ),
    )

    // ---------------------------------------------------------------- measurements
    // Device-only by design: the cloud schema has no measurement table, so
    // nothing here may grow a sync path.

    fun observeMeasurements(): Flow<List<MeasurementEntry>> =
        measurementDao.observeAll().map { list ->
            list.mapNotNull { e ->
                // Unknown site strings must never crash the app - drop the row.
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

    /** Puts a deleted tape reading back with its original id, site, value and time, for Undo. */
    suspend fun restoreMeasurement(entry: MeasurementEntry) = measurementDao.insert(
        MeasurementEntity(id = entry.id, site = entry.site.name, valueCm = entry.valueCm, takenAtMs = entry.takenAtMs),
    )

    // ------------------------------------------------------------- sync state

    /** Fingerprints of the sessions the last successful push uploaded. */
    suspend fun pushWatermark(): Map<Long, Int> =
        syncStateDao.all()
            .filter { it.fingerprint != SyncStateEntity.TOMBSTONE }
            .associate { it.sessionId to it.fingerprint }

    /** Trials deleted on this device whose cloud rows the next push must delete. */
    suspend fun tombstonedSessions(): List<Long> = syncStateDao.tombstoned()

    /** Called only after the cloud rows are confirmed deleted. */
    suspend fun clearTombstones(ids: List<Long>) = syncStateDao.clearTombstones(ids)

    /**
     * Called only after a push succeeds; prunes sessions deleted since. Both
     * writes share a transaction: a crash between them left watermarks for
     * sessions that no longer exist, and the next push read them as already
     * uploaded.
     */
    suspend fun recordPushWatermark(fingerprints: Map<Long, Int>) = db.withTransaction {
        // A tombstone written while this push ran (a delete, or a reopen) must
        // outlive it, or the cloud row it still has to remove would be forgotten.
        val tombstoned = syncStateDao.tombstoned().toSet()
        syncStateDao.upsertAll(fingerprints.filterKeys { it !in tombstoned }.map { SyncStateEntity(it.key, it.value) })
        syncStateDao.pruneExcept(fingerprints.keys.toList())
    }

    /**
     * Forgets what the cloud is believed to hold, so the next push re-uploads
     * every completed session.
     *
     * Without this the app cannot recover from the cloud losing data: erasing
     * cloud data left these fingerprints behind, every one still matched, and
     * so a lifter who erased and signed back in never re-uploaded a single
     * session - their cloud stayed empty for good.
     */
    suspend fun clearPushWatermark() = syncStateDao.clearAll()

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
                )
            }
        }

    suspend fun setTrainingMode(mode: TrainingMode) = profileDao.setTrainingMode(mode.name)

    /**
     * Body profile (height + sex) for Settings and the stat-log estimator.
     * Unknown stored sex strings fall back to MALE rather than crashing.
     */
    fun observeBodyProfile(): Flow<Pair<Double?, Sex>> =
        profileDao.observe().map { p ->
            val sex = p?.sex?.let { s -> runCatching { Sex.valueOf(s) }.getOrNull() } ?: Sex.MALE
            p?.heightCm to sex
        }

    /** The profile's sex alone, which picks the body the muscle figures draw. Changes only when the sex does. */
    fun observeSex(): Flow<Sex> = observeBodyProfile().map { it.second }.distinctUntilChanged()

    suspend fun setHeight(heightCm: Double) {
        require(BodyLimits.validHeight(heightCm)) { "height $heightCm cm is outside ${BodyLimits.HEIGHT_CM}" }
        profileDao.setHeight(heightCm)
    }

    suspend fun setSex(sex: Sex) = profileDao.setSex(sex.name)

    /**
     * Movements this lifter has actually logged, newest first, for the picker
     * to float above a catalogue that is now 200+ rows deep.
     */
    fun observeRecentExerciseIds(limit: Int = RECENT_EXERCISE_LIMIT): Flow<List<Long>> =
        sessionDao.observeRecentExerciseIds(limit)

    /**
     * Each lift's top set from its most recent completed workout (see
     * [LastLogged]), per exercise and variation, for the "last time" lines. One query, not one
     * per row; a lift never logged is simply absent.
     */
    fun observeLastLogged(): Flow<LastLoggedIndex> =
        sessionDao.observeLatestWorkoutSets().map { LastLogged.topSets(it) }

    fun observeFavouriteExerciseIds(): Flow<Set<Long>> =
        favouriteExerciseDao.observeIds().map { it.toSet() }

    suspend fun setFavourite(exerciseId: Long, favourite: Boolean) {
        if (favourite) {
            favouriteExerciseDao.add(FavouriteExerciseEntity(exerciseId, System.currentTimeMillis()))
        } else {
            favouriteExerciseDao.remove(exerciseId)
        }
    }

    /**
     * The sex the strength normalisation scores against. An unreadable stored
     * string means MALE, the 1.0 scale every stored score was already on, so a
     * corrupt row under-credits nobody's history by silently rescaling it.
     */
    private suspend fun profileSex(): Sex =
        profileDao.get()?.sex?.let { runCatching { Sex.valueOf(it) }.getOrNull() } ?: Sex.MALE

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
        // Insert and equip together: a crash between them left the titles held
        // but nothing worn, and the unlock never came round again.
        db.withTransaction {
            titleDao.insertAll(unlockRows(newly, ledger, now))
            settleNewTitles(newly)
        }
        // Every earned title gets its moment: reconciliation runs before any UI
        // exists, so the awards wait here until a screen can show them.
        _pendingCelebrations.value = _pendingCelebrations.value + newly
        return newly
    }

    /** The unlock rows for [newly]: a deed the history can date is dated to the set that earned it, the rest to [now]. */
    private fun unlockRows(newly: List<TitleDef>, ledger: Titles.Ledger, now: Long): List<TitleUnlockEntity> =
        newly.map { TitleUnlockEntity(it.id, Titles.earnedAtMs(it, ledger)?.coerceAtMost(now) ?: now) }

    /**
     * The one worn-title rule for every path that unlocks deeds: the lifter's
     * choice stands, and a new deed is worn only when nothing is. Of several
     * unlocked at once the rarest wins (first in catalogue order on a tie),
     * so an import does not dress the lifter in the easiest of twenty.
     * Call inside the transaction that inserted [newly].
     */
    private suspend fun settleNewTitles(newly: List<TitleDef>) {
        if (newly.isEmpty()) return
        // A deed that has a crest pays it in the same transaction that recorded the deed.
        reconcileCrests()
        if (profileDao.get()?.currentTitleId != null) return
        profileDao.setCurrentTitle(newly.maxByOrNull { it.rarity.ordinal }!!.id)
    }

    /**
     * Banks the inscriptions owed for a rise in level, inside the caller's
     * transaction: each level pays once, ever ([Xp.rollsDue]), however many
     * a single gain crosses and however often a refund drops the level back.
     * Returns how many it banked.
     */
    private suspend fun bankLevelRolls(levelBefore: Int, levelAfter: Int): Int {
        val g = gachaDao.get() ?: GachaStateEntity()
        val due = Xp.rollsDue(levelBefore, levelAfter, g.rollLevelMark)
        val mark = maxOf(g.rollLevelMark, levelAfter)
        if (due > 0 || mark != g.rollLevelMark) {
            gachaDao.upsert(g.copy(rolls = g.rolls + due, rollLevelMark = mark))
        }
        // Ladder crests ride the same once-per-level mark: every rung up to the level reached is owed, so a
        // refund and a climb back never pay one twice and a rung won another way is simply already held.
        reconcileCrests(maxOf(levelAfter, mark))
        return due
    }

    private fun crestName(frameId: String): String = Crests.byId(frameId)?.name ?: frameId

    /**
     * The ONE place a crest outside a draw is awarded, idempotent and additive: every crest the lifter's
     * level, held deeds and completed houses owe ([Crests.earned]) that is not yet held is inserted
     * (IGNORE, so only a real insert counts), under the source that says how it came. Nothing is ever
     * removed: a deed lost to a recompute or a level lost to a refund keeps its crest. Called from every
     * place the inputs can change (level banked, deed unlocked, a draw that completes a house, the grant
     * pass, a restore). Returns the crests that were new; with [queue] they await their celebration.
     * Call inside the caller's transaction.
     */
    private suspend fun reconcileCrests(level: Int? = null, queue: Boolean = true): List<CrestDef> {
        val g = gachaDao.get() ?: GachaStateEntity()
        val standing = maxOf(level ?: 0, g.rollLevelMark, Xp.levelFor(profileDao.get()?.totalXp ?: 0L))
        val relics = gachaDao.relics().map { it.toOwned() }
        val completed = RelicHouses.effects(relics).standings.filter { it.fullReached }.map { it.house }.toSet()
        val owned = gachaDao.ownedFrameIds().toSet()
        val now = System.currentTimeMillis()
        val added = Crests.earned(standing, titleDao.heldIds().toSet(), completed)
            .filter { it.id !in owned }
            .filter { gachaDao.insertFrame(OwnedCrestFrameEntity(it.id, now, CrestSource.awardedFor(it).id)) != -1L }
        if (queue && added.isNotEmpty()) addPendingVeilGrant(VeilGrant(crests = added.map { it.name }))
        return added
    }

    private val _pendingVeilGrant = MutableStateFlow<VeilGrant?>(null)

    /** What the Veil paid outside a draw (milestone crests, the one-time grant), awaiting its screen. */
    val pendingVeilGrant: StateFlow<VeilGrant?> = _pendingVeilGrant.asStateFlow()

    private fun addPendingVeilGrant(grant: VeilGrant) {
        _pendingVeilGrant.update { (it ?: VeilGrant()) + grant }
    }

    fun clearPendingVeilGrant() {
        _pendingVeilGrant.value = null
    }

    /**
     * The one-time Veil pass, guarded by [Veil.GRANT_VERSION] so a version runs once per
     * lifter and never again. It only ADDS: the crests the lifter's level, deeds and houses owe
     * ([reconcileCrests]) and, for a lifter who never had the first pass, relics up to the floor the pacing
     * implies and the inscriptions for levels that never paid ([Veil.retroGrant]). Nothing is ever taken away.
     * Queues one celebration. Returns what was paid, or null when it had already
     * run or there was nothing to pay.
     */
    suspend fun applyVeilGrant(): VeilGrant? = db.withTransaction {
        val g = gachaDao.get() ?: GachaStateEntity()
        if (g.veilGrantVersion >= Veil.GRANT_VERSION) return@withTransaction null
        val level = Xp.levelFor(profileDao.get()?.totalXp ?: 0L)
        val now = System.currentTimeMillis()
        // The inscription and relic part runs for a lifter who never had the retro pass. A lifter already at
        // version 1 was paid it, and running it again would pay levels twice: they get the crests only.
        val retro = g.veilGrantVersion < Veil.RETRO_VERSION
        val heldRelics = gachaDao.relics()
        val grant = if (retro) {
            Veil.retroGrant(
                level = level,
                banked = g.rolls,
                relicCount = heldRelics.size,
                drawnCrests = gachaDao.drawnFrameCount(),
                echoes = idleDao.get()?.shadows ?: 0,
                ownedRelicIds = heldRelics.map { it.relicId }.toSet(),
            )
        } else {
            Veil.RetroGrant(0, emptyList())
        }
        grant.relics.forEach {
            gachaDao.insertRelic(OwnedRelicEntity(name = it.name, multiplier = it.multiplier, drawnAtMs = now, relicId = it.relicId))
        }
        if (grant.relics.isNotEmpty()) applyRelicVault()
        // Crests last, so a house the relics above just completed pays its crest too.
        val crests = reconcileCrests(level, queue = false)
        gachaDao.upsert(
            g.copy(
                rolls = g.rolls + grant.inscriptions,
                // The level is marked paid only by the pass that paid it: every level up to it is now
                // accounted for, so the live level-up path cannot pay any of them a second time.
                rollLevelMark = if (retro) maxOf(g.rollLevelMark, level) else g.rollLevelMark,
                veilGrantVersion = Veil.GRANT_VERSION,
            ),
        )
        if (grant.isEmpty && crests.isEmpty()) return@withTransaction null
        VeilGrant(
            retro = retro,
            inscriptions = grant.inscriptions,
            relics = grant.relics.map { it.name },
            crests = crests.map { it.name },
        ).also { addPendingVeilGrant(it) }
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

    /**
     * ISO weekdays (1 = Monday) that carry a scheduled preset. The streak rule
     * needs them: an unscheduled day is rest, a scheduled day that was skipped
     * breaks the streak.
     */

    /** Ledger over everything logged: sessions, health days and skill practice. */
    suspend fun currentLedger(): Titles.Ledger = Titles.ledgerOf(
        totalXp = profileDao.get()?.totalXp ?: 0L,
        history = observeHistory().first(),
        healthDays = observeHealthDays().first(),
        practices = observeSkillPractices().first(),
        exercises = exerciseCatalogue(),
        sex = profileSex(),
        bodyweightAt = bodyweightLookup(),
    )

    /**
     * Weigh-in history as a bodyweight-at-time lookup for the strength-title
     * ledger — the same rule [SetRecords.bodyweightLookup] applies to a set
     * logged before any weigh-in. Load deeds compare against the bodyweight
     * in force at the session, never today's, or a lifter who gained weight
     * would silently lose a title she earned.
     */
    private suspend fun bodyweightLookup(): (Long) -> Double =
        SetRecords.bodyweightLookup(observeStats().first())

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
                    passed = it.passed,
                )
            }
        }

    /**
     * Best effort a lifter actually logged in real training for each movement,
     * derived from completed sessions. Reuses [SetRecords.records] - the same
     * per-set machinery the live PR badges read - so the tree and the session
     * screen can never disagree about what her best set was. Keyed by
     * normalised exercise name; only movements that are also a skill appear
     * interesting, but the map carries every strength movement so callers
     * decide the join.
     */
    fun observeSkillTrainingEvidence(): Flow<Map<String, SkillTrainingEvidence>> =
        combine(observeHistory(), observeStats(), observeExercises()) { history, stats, exercises ->
            skillTrainingEvidence(history, SetRecords.bodyweightLookup(stats), exercises)
        }

    /**
     * A measured practice attempt toward the standard — records what was
     * actually achieved (seconds, reps or metres) and any added load. No XP,
     * no mastery: claiming is a separate, deliberate act.
     */
    suspend fun logSkillPractice(skillName: String, value: Int, weightKg: Double? = null) {
        require(Skills.forName(skillName) != null) { "Unknown ${Words.technique.s} $skillName" }
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

    /**
     * Claiming mastery: awards skill XP, may level you up and unlock titles.
     * Level-up inscriptions are banked here, in the claim's own transaction.
     *
     * A claim needs no prerequisite standing first: every skill it stands on, across lines, that is not already
     * claimed or passed is marked passed and paid its claim XP, so the claim opens whatever those open
     * ([Skills.claimPlan]). A skill that is itself passed is claimed for real and pays nothing more.
     */
    suspend fun claimSkill(skillName: String): SkillClaimResult = db.withTransaction {
        val def = Skills.forName(skillName) ?: error("Unknown ${Words.technique.s} $skillName")
        val existing = skillPracticeDao.claim(skillName)
        check(existing == null || existing.passed) { "$skillName already mastered" }
        val rows = observeSkillPractices().first()
        val standingBefore = rows.filter { it.claimed }.mapTo(HashSet()) { it.skillName }
        val plan = Skills.claimPlan(skillName, rows)
        val now = System.currentTimeMillis()
        if (existing != null) {
            // Passed becomes claimed: the row, its date and what it paid stay, so a later unclaim refunds the same.
            skillPracticeDao.setPassed(skillName, false)
        } else {
            // The claim row records what it paid in `value` (unused on claims),
            // so an unclaim refunds exactly that even after the skill re-tiers.
            skillPracticeDao.insert(
                SkillPracticeEntity(skillName = skillName, practicedAtMs = now, claimed = true, value = def.xp),
            )
        }
        plan.passes.forEach { insertPassed(it, now) }
        val before = profileDao.get() ?: error("Profile missing")
        val levelBefore = Xp.levelFor(before.totalXp)
        val newTotal = before.totalXp + plan.xp
        profileDao.addXp(plan.xp.toLong())
        bankLevelRolls(levelBefore, Xp.levelFor(newTotal))
        val ledger = Titles.ledgerOf(
            totalXp = newTotal,
            history = observeHistory().first(),
            healthDays = observeHealthDays().first(),
            practices = observeSkillPractices().first(),
            exercises = exerciseCatalogue(),
            sex = profileSex(),
            bodyweightAt = bodyweightLookup(),
        )
        val newly = Titles.newlyUnlocked(ledger, titleDao.heldIds().toSet())
        titleDao.insertAll(unlockRows(newly, ledger, now))
        settleNewTitles(newly)
        val standingAfter = standingBefore + skillName + plan.passes.map { it.name }
        SkillClaimResult(
            skill = def,
            xpAwarded = plan.xp,
            levelBefore = levelBefore,
            levelAfter = Xp.levelFor(newTotal),
            totalXp = newTotal,
            newTitles = newly,
            // Open now and not before, whatever line they sit on: a dependant of a skill this claim passed counts.
            unlockedNext = Skills.ALL.filter {
                it.name !in standingAfter && Skills.unlocked(it, standingAfter) && !Skills.unlocked(it, standingBefore)
            },
            passed = plan.passes,
        )
    }

    /** A prerequisite marked passed: a claim row the lifter did not make, stamped with the XP it pays. */
    private suspend fun insertPassed(def: Skills.SkillDef, now: Long) {
        skillPracticeDao.insert(
            SkillPracticeEntity(skillName = def.name, practicedAtMs = now, claimed = true, value = def.xp, passed = true),
        )
    }

    /**
     * Marks passed, and pays, every prerequisite the real claims on the ledger imply and nothing yet holds: the
     * one-time catch-up for claims made before passing existed, and a repair after a restore. Idempotent, because
     * what it adds is exactly what it then no longer finds missing. Returns the skills it passed.
     */
    suspend fun reconcileSkillPasses(): List<Skills.SkillDef> {
        val owed = db.withTransaction {
            val rows = observeSkillPractices().first()
            val real = rows.filter { it.claimed && !it.passed }.map { it.skillName }
            val owed = Skills.owedPasses(real, rows.filter { it.claimed }.mapTo(HashSet()) { it.skillName })
            if (owed.isNotEmpty()) {
                val now = System.currentTimeMillis()
                owed.forEach { insertPassed(it, now) }
                val levelBefore = Xp.levelFor(profileDao.get()?.totalXp ?: 0L)
                profileDao.addXp(owed.sumOf { it.xp }.toLong())
                bankLevelRolls(levelBefore, Xp.levelFor(profileDao.get()?.totalXp ?: 0L))
            }
            owed
        }
        if (owed.isNotEmpty()) reconcileTitles()
        return owed
    }

    /**
     * Undo an accidental claim: removes mastery and takes the XP back. The
     * subtraction is exactly what the claim PAID ([Skills.claimRefund]: the
     * figure stamped on the claim row, or the tier it was claimed at for a
     * row from before stamping), never the current tier's [SkillDef.xp] - a
     * re-tiered skill would otherwise refund more or less than it paid. A
     * reclaim then pays the current tier, so unclaim/reclaim moves a claim
     * onto the new tier without minting. A ledger already below that figure
     * (spent down by a deleted workout) cannot pay it back, so the unclaim is
     * refused rather than clamped: a clamped refund followed by a full reclaim
     * was an XP farm. Rolls and titles the claim already granted are
     * deliberately kept: a title once earned is not taken back.
     *
     * The skills this claim passed revert with it, and are refunded what they paid, unless another real claim still
     * implies them ([Skills.unclaimPlan]); the whole refund is checked against the ledger first, so one refusal covers
     * the lot. A claim that is itself still implied stands down to passed and refunds nothing.
     */
    suspend fun unclaimSkill(skillName: String) = db.withTransaction {
        Skills.forName(skillName) ?: error("Unknown ${Words.technique.s} $skillName")
        val claim = skillPracticeDao.claim(skillName) ?: return@withTransaction
        // A passed skill is not the lifter's to give back: it reverts with the claim that implies it.
        if (claim.passed) return@withTransaction
        // A claimed skill that others REQUIRE cannot stand down while they
        // stand on it: dropping Dead Hang would leave Scapular Pull claimed
        // with its own floor gone, and the tree's invariant quietly broken.
        val rows = observeSkillPractices().first()
        val dependents = rows
            .filter { it.claimed && !it.passed && Skills.forName(it.skillName)?.prerequisites?.contains(skillName) == true }
            .map { it.skillName }
        check(dependents.isEmpty()) {
            "$skillName is still required by ${dependents.joinToString()} - unclaim those first"
        }
        val plan = Skills.unclaimPlan(skillName, rows) ?: return@withTransaction
        val total = profileDao.get()?.totalXp ?: 0L
        check(total >= plan.refund) {
            val also = if (plan.reverts.isEmpty()) "" else " and the ${plan.reverts.size} it passed"
            "$skillName$also paid ${plan.refund} XP but only $total XP is left - it stays ${Words.claimed.s}"
        }
        if (plan.staysPassed) skillPracticeDao.setPassed(skillName, true) else skillPracticeDao.deleteClaims(skillName)
        plan.reverts.forEach { skillPracticeDao.deleteClaims(it) }
        profileDao.addXp(-plan.refund.toLong())
    }

    /**
     * Removes a completed workout from the chronicle and UNDOES what completing
     * it paid: its XP comes back off the ledger and its strength score off the
     * lifetime sum, so a deleted session stops counting in every direction at
     * once. Titles already earned stay earned - the codex records that a
     * threshold was reached, and unpicking that would cascade through every
     * other ledger reader.
     *
     * The set rows go explicitly (no FK cascade) and the push watermark turns
     * into a tombstone in the same transaction: the next push deletes the cloud
     * row, so a deleted trial stops counting in a circle's week and in allies'
     * feeds. The device stays authoritative, per the data-authority rule, and
     * deletes are only ever recorded here, never inferred from the cloud.
     */
    suspend fun deleteWorkout(sessionId: Long) {
        val gone = db.withTransaction { deleteWorkoutRows(sessionId) }
        gone?.let { writeBack?.removed(it.id, it.startedAtMs) }
    }

    private suspend fun deleteWorkoutRows(sessionId: Long): SessionEntity? {
        val session = sessionDao.byId(sessionId) ?: return null
        check(session.completedAtMs != null) {
            "${Words.trial.c} $sessionId is still live - abandon it instead"
        }
        // Clamped so a ledger since spent down cannot go negative.
        val total = profileDao.get()?.totalXp ?: 0L
        profileDao.addXp(-minOf(session.xpAwarded.toLong(), total))
        val lifetime = profileDao.get()?.lifetimeStrength ?: 0L
        profileDao.setLifetimeStrength(maxOf(0L, lifetime - session.strengthScore))
        sessionDao.deleteSetsFor(sessionId)
        syncStateDao.upsertAll(listOf(SyncStateEntity(sessionId, SyncStateEntity.TOMBSTONE)))
        sessionDao.deleteCompleted(sessionId)
        return session
    }

    /**
     * "Sealed too soon? Keep going": reverses [completeSession] for one trial in
     * ONE transaction, within [SealedReopen.WINDOW_MS] of the seal, and returns
     * the decision instead of throwing so the screen can say why it refused.
     *
     * What the seal applied, and what undoes it:
     * - the row's completedAtMs, xpAwarded (quest bonus included) and
     *   strengthScore: cleared, so the quest bonus is judged afresh on re-seal
     *   (it is derived from completed trials, not stored);
     * - the profile's totalXp and lifetimeStrength: the same two figures
     *   subtracted, unclamped (a ledger that cannot pay it back refuses);
     * - the push watermark: a tombstone, as for a delete, so the cloud row
     *   goes on the next push and a re-seal uploads it fresh.
     *
     * Kept, as everywhere else: deeds and the title worn, inscriptions
     * (rollLevelMark stops a re-seal paying a level twice) and the highest
     * rank band, so the end state equals a single seal.
     */
    suspend fun reopenSealedTrial(
        sessionId: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): SealedReopen.Decision = db.withTransaction {
        val session = sessionDao.byId(sessionId)
            ?: return@withTransaction SealedReopen.Decision.Refuse(SealedReopen.Refusal.NOT_SEALED)
        val profile = profileDao.get() ?: error("Profile missing")
        val decision = SealedReopen.decide(
            completedAtMs = session.completedAtMs,
            xpAwarded = session.xpAwarded,
            strengthScore = session.strengthScore,
            amended = session.editedAtMs != null || session.sealedXp != null,
            anotherLive = sessionDao.liveSession() != null,
            totalXp = profile.totalXp,
            lifetimeStrength = profile.lifetimeStrength,
            nowMs = nowMs,
        )
        if (decision is SealedReopen.Decision.Reopen) {
            sessionDao.updateSession(
                session.copy(completedAtMs = null, xpAwarded = 0, strengthScore = 0, sealedXp = null),
            )
            profileDao.addXp(-decision.xp.toLong())
            profileDao.setLifetimeStrength(profile.lifetimeStrength - decision.strength)
            syncStateDao.upsertAll(listOf(SyncStateEntity(sessionId, SyncStateEntity.TOMBSTONE)))
        }
        decision
    }.also { decision ->
        // Unsealed, so its Health Connect entry goes; a re-seal writes a fresh one.
        if (decision is SealedReopen.Decision.Reopen) {
            sessionDao.byId(sessionId)?.let { writeBack?.removed(it.id, it.startedAtMs) }
        }
    }

    /** What amending a sealed trial did, for the screen that asked. */
    data class AmendResult(
        val settlement: SealedEdit.Settlement,
        val strengthScore: Int,
        val newTitles: List<TitleDef>,
    )

    /** The rows and figures an amendment would write, computed but not applied. */
    private class AmendPlan(
        val session: SessionEntity,
        val rows: List<SetLogEntity>,
        val settlement: SealedEdit.Settlement,
        val strengthScore: Int,
    )

    /**
     * The XP change [editSealedTrial] would make for [draft], without writing
     * anything: the Save confirmation reads it. Same plan, same inputs.
     */
    suspend fun previewSealedEdit(
        sessionId: Long,
        draft: TrialDraft,
        nowMs: Long = System.currentTimeMillis(),
    ): SealedEdit.Settlement = db.withTransaction { planAmend(sessionId, draft, nowMs).settlement }

    /**
     * Amends a sealed trial in ONE transaction (owner-approved Option A, a
     * delta correction): the sets are replaced from [draft] and renumbered
     * gap-free, the difference in what the sets are worth is settled by
     * [SealedEdit.settle], and the trial's strength is rescored and the
     * lifetime sum re-summed.
     *
     * - Deeds are never revoked; inside the raise window a new one can still
     *   be earned, after it none is evaluated.
     * - No amendment pays inscriptions, whatever level it crosses.
     * - The trial keeps its cloud row (upserted on local id, so comments and
     *   tributes survive); the push fingerprint changes and it re-pushes.
     */
    suspend fun editSealedTrial(
        sessionId: Long,
        draft: TrialDraft,
        nowMs: Long = System.currentTimeMillis(),
    ): AmendResult = db.withTransaction {
        val plan = planAmend(sessionId, draft, nowMs)
        sessionDao.deleteSetsFor(sessionId)
        sessionDao.insertSets(plan.rows)
        sessionDao.updateSession(
            plan.session.copy(
                xpAwarded = plan.settlement.xpAwarded,
                strengthScore = plan.strengthScore,
                editedAtMs = nowMs,
                // Recorded once, by the first amendment: the cap's base.
                sealedXp = sealedBase(plan.session),
            ),
        )
        if (plan.settlement.applied != 0) profileDao.addXp(plan.settlement.applied.toLong())
        // Re-summed, not nudged: the sum IS the lifetime figure.
        profileDao.setLifetimeStrength(sessionDao.lifetimeStrength())

        val newly = if (plan.settlement.withinWindow) {
            val profile = profileDao.get() ?: error("Profile missing")
            val ledger = Titles.ledgerOf(
                totalXp = profile.totalXp,
                history = observeHistory().first(),
                healthDays = observeHealthDays().first(),
                practices = observeSkillPractices().first(),
                exercises = exerciseCatalogue(),
                sex = profileSex(),
                bodyweightAt = bodyweightLookup(),
            )
            Titles.newlyUnlocked(ledger, titleDao.heldIds().toSet()).also { found ->
                titleDao.insertAll(unlockRows(found, ledger, nowMs))
                settleNewTitles(found)
            }
        } else {
            emptyList()
        }
        AmendResult(plan.settlement, plan.strengthScore, newly)
    }.also { writeBackSaved(sessionId) }

    /**
     * What the raise cap is measured against. `sealedXp` is device-only: a
     * trial restored from the cloud arrives with `editedAtMs` set (it was
     * amended) but no `sealedXp`, and its stored XP may already be raised.
     * Taking that as the sealed figure would let the cap restart from the
     * raised value, so such a trial gets half its XP: no headroom, no
     * further raise. Cuts are unaffected.
     */
    private fun sealedBase(session: SessionEntity): Int =
        session.sealedXp ?: if (session.editedAtMs != null) session.xpAwarded / 2 else session.xpAwarded

    private suspend fun planAmend(sessionId: Long, draft: TrialDraft, nowMs: Long): AmendPlan {
        val session = sessionDao.byId(sessionId) ?: error("${Words.trial.c} $sessionId not found")
        val completedAt = session.completedAtMs
        check(completedAt != null) { "${Words.trial.c} $sessionId is still live - edit it on the ${Words.trial.s} screen" }
        require(draft.tickedCount >= 1) {
            "An amended ${Words.trial.s} needs at least one ticked set - delete the ${Words.trial.s} instead"
        }
        val catalogue = exerciseDao.observeAll().first()
        val known = catalogue.map { it.id }.toSet()
        require(draft.blocks.all { it.exerciseId in known }) { "A movement is not in the catalogue" }

        val rows = draft.blocks.filter { it.sets.isNotEmpty() }.flatMapIndexed { position, block ->
            block.sets.mapIndexed { index, s ->
                SetLogEntity(
                    sessionId = sessionId,
                    exerciseId = block.exerciseId,
                    exercisePosition = position,
                    setIndex = index,
                    reps = s.reps.coerceAtLeast(0),
                    weightKg = s.weightKg?.takeIf { it > 0.0 },
                    modifiers = block.modifiers,
                    // A warm-up never counts, however the editor left its tick.
                    done = s.done && !s.warmup,
                    durationSec = s.durationSec?.coerceAtLeast(0),
                    distanceM = s.distanceM?.coerceAtLeast(0.0),
                    grade = s.grade?.take(WireLimits.GRADE_MAX)?.ifBlank { null },
                    kind = s.kind.name,
                    supersetGroup = block.supersetGroup,
                    // Only a logged working set carries a rating.
                    rpe = Effort.clamp(s.rpe).takeIf { s.done && !s.warmup },
                )
            }
        }
        // Both sides priced at the weigh-in in force when the trial began, so
        // an untouched set list moves nothing.
        val stats = observeStats().first()
        val bodyweight = if (stats.isEmpty()) null else SetRecords.bodyweightLookup(stats)(session.startedAtMs)
        val scoring = SessionScoring(catalogue)
        val book = xpBook(scoring, excludeSessionId = sessionId, beforeMs = session.startedAtMs)
        val settlement = SealedEdit.settle(
            xpAwarded = session.xpAwarded,
            sealedXp = sealedBase(session),
            oldSetsXp = scoring.xp(sessionDao.setsFor(sessionId), bodyweight, book),
            newSetsXp = scoring.xp(rows, bodyweight, book),
            totalXp = profileDao.get()?.totalXp ?: 0L,
            completedAtMs = completedAt,
            nowMs = nowMs,
        )
        return AmendPlan(session, rows, settlement, scoring.strength(rows, bodyweight, profileSex()))
    }

    // ------------------------------------------------------------ XP rescore (debug builds only)

    /**
     * What [applyXpRescore] would do, without writing anything: every non-imported sealed trial priced with the
     * current rules ([XpRescore.reprice]) and the profile's new total and level ([XpRescore.plan]).
     * Debug builds only: a release build refuses, and its UI never offers it.
     */
    suspend fun previewXpRescore(): XpRescore.Plan {
        check(com.ironvellum.app.BuildConfig.DEBUG) { "The XP rescore exists in debug builds only" }
        return planXpRescore()
    }

    /**
     * Re-prices every non-imported sealed trial and moves the profile's total by the sum of the deltas, in ONE
     * transaction. Each trial's `xpAwarded` becomes its new price and a recorded `sealedXp` shifts by the same delta;
     * `editedAtMs` is untouched (a rescore is not an amendment) and the push fingerprint, which includes `xpAwarded`,
     * re-uploads the changed trials by itself. The total never goes below the floor of the current level, a rise
     * banks inscriptions only through [bankLevelRolls] (each level pays once, ever), and a second run changes nothing.
     */
    suspend fun applyXpRescore(): XpRescore.Plan = db.withTransaction {
        check(com.ironvellum.app.BuildConfig.DEBUG) { "The XP rescore exists in debug builds only" }
        val plan = planXpRescore()
        plan.changed.forEach { change ->
            val row = sessionDao.byId(change.sessionId) ?: return@forEach
            sessionDao.updateSession(row.copy(xpAwarded = change.newXp, sealedXp = change.newSealedXp))
        }
        if (plan.applied != 0L) profileDao.addXp(plan.applied)
        if (plan.levelAfter > plan.levelBefore) bankLevelRolls(plan.levelBefore, plan.levelAfter)
        plan
    }

    private suspend fun planXpRescore(): XpRescore.Plan {
        val scoring = SessionScoring(exerciseDao.observeAll().first())
        val history = observeHistory().first()
        val stats = observeStats().first()
        val presets = presetDao.observePresets().first()
        val bodyweightAt = SetRecords.bodyweightLookup(stats)
        val planned = presets.associate { pw ->
            pw.preset.id to pw.working.groupBy { it.exerciseId }.mapValues { (_, rows) -> rows.sumOf { it.targetSets } }
        }
        val days = presets.associate { it.preset.id to it.preset.scheduledDay }
        val changes = withContext(kotlinx.coroutines.Dispatchers.Default) {
            XpRescore.reprice(history, scoring.exercises, bodyweightAt, planned::get, days::get)
        }
        val total = profileDao.get()?.totalXp ?: 0L
        return XpRescore.plan(changes, total, (gachaDao.get() ?: GachaStateEntity()).rollLevelMark)
    }

    suspend fun rename(name: String) {
        require(name.isNotBlank()) { "Name cannot be blank" }
        profileDao.setName(name.trim().take(WireLimits.DISPLAY_NAME_MAX))
    }

    /** Signed in, the cloud name is the profile name; see [localNameToAdopt] for when it is not. */
    suspend fun adoptCloudName(cloudName: String, profileLoaded: Boolean) {
        localNameToAdopt(profileDao.get()?.name, cloudName, profileLoaded)?.let { profileDao.setName(it) }
    }

    // ---------------------------------------------------------------- export

    /** The archive plus everything the export itself had to leave behind. */
    data class ExportResult(val json: String, val problems: List<String>)

    suspend fun exportJson(): String = exportArchive().json

    private suspend fun completedSessionsWithSets(names: Map<Long, String>): List<Pair<WorkoutSession, List<SessionSet>>> =
        sessionDao.observeCompletedWithSets().first().map { sws ->
            sws.session.toDomain() to sws.sets.map { s ->
                SessionSet(
                    id = s.id,
                    exerciseId = s.exerciseId,
                    exerciseName = names[s.exerciseId] ?: "Unknown",
                    // Which block a set belongs to: without it a restored
                    // backup lands every set on position 0 and merges the
                    // movements into one block.
                    exercisePosition = s.exercisePosition,
                    setIndex = s.setIndex,
                    reps = s.reps,
                    weightKg = s.weightKg,
                    modifiers = s.modifiers,
                    done = s.done,
                    durationSec = s.durationSec,
                    distanceM = s.distanceM,
                    grade = s.grade,
                    kind = s.setKind(),
                    supersetGroup = s.supersetGroup,
                    rpe = s.rpe,
                )
            }
        }

    /** One period of trials as a Strong-format CSV, or null when the period holds none. */
    suspend fun exportCsv(period: WorkoutCsvWriter.Period, today: LocalDate, zone: ZoneId): String? {
        val names = exerciseDao.observeAll().first().associate { it.id to it.name }
        val sessions = WorkoutCsvWriter.inPeriod(completedSessionsWithSets(names), period, today, zone)
        return if (sessions.isEmpty()) null else WorkoutCsvWriter.write(sessions, zone)
    }

    // includeDeviceOnly = false is the CLOUD copy (see ExportWriter.write): the
    // private note, body readings, measurements, height, sex and Health
    // Connect days are promised never to leave this device, so a cloud backup
    // omits them. Default true keeps exportJson()/EXPORT ARCHIVE complete.
    suspend fun exportArchive(includeDeviceOnly: Boolean = true): ExportResult {
        val profileEntity = profileDao.get()
        val profile = profileEntity?.let {
            PlayerProfile(
                it.name,
                it.totalXp,
                it.currentTitleId,
                // Export must not be the one action a bad stored mode can kill:
                // it is how a lifter rescues their data.
                runCatching { TrainingMode.valueOf(it.trainingMode) }
                    .getOrDefault(TrainingMode.STRENGTH),
            )
        } ?: PlayerProfile()
        val exerciseRows = exerciseDao.observeAll().first()
        val names = exerciseRows.associate { it.id to it.name }
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
                        supersetGroup = e.supersetGroup,
                        scheme = SetScheme.decode(e.setScheme),
                        warmup = e.warmup,
                    )
                },
            )
        }
        val notesBySession = sessionDao.allExerciseNotes().groupBy { it.sessionId }
        val sessions = completedSessionsWithSets(names).map { (session, sets) ->
            val notes = notesBySession[session.id].orEmpty().map { ExerciseNote(names[it.exerciseId] ?: "Unknown", it.note) }
            session.copy(exerciseNotes = notes) to sets
        }
        val stats = statDao.observeAll().first().map { StatEntry(it.id, it.takenAtMs, it.weightKg, it.heightCm, it.bodyFatPct) }
        val skills = skillPracticeDao.observeAll().first().map {
            SkillPractice(it.skillName, it.practicedAtMs, it.claimed, it.value, it.weightKg, it.passed)
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
        val exportProblems = mutableListOf<String>()
        val measurements = measurementDao.observeAll().first().mapNotNull { e ->
            val site = MeasurementSite.entries.firstOrNull { it.name == e.site }
            if (site == null) {
                // The import side reports every guess it makes; the export must
                // be equally honest about rows it cannot carry.
                exportProblems.add(
                    Words.text(
                        UiText.themed(R.string.data_export_unknown_site, R.string.data_export_unknown_site__t, e.site, e.valueCm),
                    ),
                )
                return@mapNotNull null
            }
            MeasurementEntry(id = e.id, site = site, valueCm = e.valueCm, takenAtMs = e.takenAtMs)
        }
        // Real catalogue attributes: on restore a user-created movement comes
        // back as built, not as a guessed PULL/weighted default.
        val exercises = exerciseRows.map {
            ExportWriter.ExerciseMeta(
                name = it.name,
                muscleGroup = it.muscleGroup,
                isWeighted = it.isWeighted,
                metric = it.metric,
                category = it.category,
                custom = it.custom,
                gear = it.gear,
                muscles = it.muscles,
            )
        }
        val idleRow = idleDao.get()
        val idle = idleRow?.let {
            ExportWriter.IdleSnapshot(
                it.essence,
                it.shadows,
                it.relicMultiplier,
                it.lastCollectedAtMs,
                lifetimeEssence = maxOf(it.lifetimeEssence, it.essence),
            )
        }
        val gachaRow = gachaDao.get()
        val gacha = gachaRow?.let {
            ExportWriter.GachaSnapshot(
                rolls = it.rolls,
                equippedFrame = it.equippedFrame,
                figureStreak = it.figureStreak,
                rollLevelMark = it.rollLevelMark,
                relicPity = it.relicPity,
                drawsSpent = it.drawsSpent,
                offeringsMade = it.offeringsMade,
                veilGrantVersion = it.veilGrantVersion,
            )
        }
        val crestFrames = gachaDao.ownedFrames().map {
            ExportWriter.CrestFrameSnapshot(it.frameId, it.ownedAtMs, it.source)
        }
        val relics = gachaDao.observeRelics().first().map {
            ExportWriter.RelicSnapshot(it.name, it.multiplier, it.drawnAtMs, it.relicId, it.refinements)
        }
        val json = ExportWriter.write(
            profile = profile,
            trainingMode = profile.trainingMode,
            presets = presets,
            sessions = sessions,
            stats = stats,
            titles = titles,
            skills = skills,
            healthDays = healthDays,
            measurements = measurements,
            exercises = exercises,
            heightCm = profileEntity?.heightCm,
            sex = profileEntity?.sex,
            idle = idle,
            gacha = gacha,
            crestFrames = crestFrames,
            relics = relics,
            exportedAtMs = System.currentTimeMillis(),
            settings = archiveSettings?.snapshot(includeDeviceOnly),
            includeDeviceOnly = includeDeviceOnly,
        )
        return ExportResult(json, exportProblems)
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
        val problems: List<UiText>,
    )

    /** The Room transaction committed, but preference persistence or refresh failed afterward. */
    class SettingsRestoreException(cause: Throwable) :
        MessageException(UiText.res(R.string.settings_restore_settings_failed), cause)

    /**
     * Full restore from an export archive: wipes user data and replays the
     * archive inside one transaction, so a mid-way failure cannot leave a
     * half-restored database. The seeded exercise catalogue survives; archive
     * exercises are matched by NAME (ids differ between installs) and created
     * when missing.
     *
     * A CLOUD copy ([ExportReader.Archive.deviceDataOmitted]) never carried
     * the device-only data, so the device's own readings, measurements and
     * Health Connect days are kept rather than cleared: restoring training
     * must never wipe a body history the backup was not allowed to hold.
     */
    suspend fun importArchive(json: String): Result<ImportResult> = Result.runCatching {
        val archive = ExportReader.read(json).getOrThrow()
        archive.settings?.let(ArchiveSettings::validate)
        val result = db.withTransaction {
                // User data goes; the seeded exercise catalogue stays.
                presetDao.clearAll()
                sessionDao.clearAll()
                titleDao.clearAll()
                skillPracticeDao.clearAll()
                if (!archive.deviceDataOmitted) {
                    statDao.clearAll()
                    healthDayDao.clearAll()
                    measurementDao.clearAll()
                }

                // v5 archives carry height/sex; a v4 archive carries
                // none, so absence falls back to the LOCAL value instead of
                // resetting it — height and sex feed every BMI/FFMI/calorie
                // estimate.
                val local = profileDao.get()
                profileDao.upsert(
                    ProfileEntity(
                        name = archive.profile.name,
                        totalXp = archive.profile.totalXp,
                        currentTitleId = archive.profile.currentTitleId,
                        trainingMode = archive.trainingMode.name,
                        heightCm = archive.heightCm ?: local?.heightCm,
                        sex = archive.sex ?: local?.sex ?: "MALE",
                    ),
                )

                // Resolve exercises by name: reuse the seeded catalogue entry on a
                // case-insensitive hit, otherwise create the movement. A v5
                // archive carries the real muscle group/weighted/metric/category;
                // a v4 archive carries none, so new rows get the historical
                // defaults and every guess is reported, never silent.
                val exerciseIdByName = mutableMapOf<String, Long>()
                val metaByName = archive.exercises.associateBy { it.name.lowercase() }
                val problems = mutableListOf<UiText>()
                suspend fun resolveExercise(rawName: String): Long? {
                    val name = rawName.trim()
                    if (name.isEmpty()) return null
                    exerciseIdByName[name.lowercase()]?.let { return it }
                    val existing = exerciseDao.byName(name)
                    if (existing != null) {
                        exerciseIdByName[name.lowercase()] = existing.id
                        return existing.id
                    }
                    val meta = metaByName[name.lowercase()]
                    val guessedMuscleGroup = MuscleGroup.PULL.name
                    val muscleGroup = meta?.muscleGroup ?: guessedMuscleGroup
                    val metric = meta?.metric ?: ExerciseMetric.REPS.name
                    val newId = exerciseDao.insertAll(
                        listOf(
                            ExerciseEntity(
                                name = name,
                                muscleGroup = runCatching { MuscleGroup.valueOf(muscleGroup) }
                                    .getOrDefault(MuscleGroup.PULL).name,
                                isWeighted = meta?.isWeighted ?: true,
                                metric = runCatching { ExerciseMetric.valueOf(metric) }
                                    .getOrDefault(ExerciseMetric.REPS).name,
                                category = meta?.category ?: "",
                                custom = meta?.custom ?: false,
                                gear = meta?.gear ?: "",
                                muscles = meta?.muscles ?: "",
                            ),
                        ),
                    ).first()
                    exerciseIdByName[name.lowercase()] = newId
                    if (meta == null) {
                        problems.add(UiText.res(R.string.data_import_exercise_guessed, name, guessedMuscleGroup))
                    }
                    return newId
                }

                // A custom exercise nothing references yet is still the lifter's: restore it, not only the ones
                // a set or rite names.
                archive.exercises.filter { it.custom }.forEach { resolveExercise(it.name) }

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
                                problems.add(
                                    UiText.themed(
                                        R.string.data_import_routine_entry_dropped,
                                        R.string.data_import_routine_entry_dropped__t,
                                        preset.name,
                                    ),
                                )
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
                                supersetGroup = entry.supersetGroup,
                                setScheme = entry.scheme.normalised().encode(),
                                warmup = entry.warmup,
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
                            privateNote = session.privateNote.take(WireLimits.PRIVATE_NOTE_MAX),
                            imported = session.imported,
                            audience = session.audience.wire,
                            editedAtMs = session.editedAtMs,
                            sealedXp = session.sealedXp,
                        ),
                    )
                    session.exerciseNotes.forEach { n ->
                        val exerciseId = resolveExercise(n.exerciseName) ?: return@forEach
                        sessionDao.putExerciseNote(SessionExerciseNoteEntity(newSessionId, exerciseId, n.note.take(EXERCISE_NOTE_MAX)))
                    }
                    restoredSets += sessionDao.insertSets(
                        sets.mapNotNull { s ->
                            val exerciseId = resolveExercise(s.exerciseName)
                            if (exerciseId == null) {
                                problems.add(
                                    UiText.themed(
                                        R.string.data_import_set_dropped,
                                        R.string.data_import_set_dropped__t,
                                        session.label,
                                    ),
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
                                kind = s.kind.name,
                                supersetGroup = s.supersetGroup,
                                rpe = Effort.clamp(s.rpe),
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
                    val ok = BodyLimits.validWeight(it.weightKg) && BodyLimits.validBodyFat(it.bodyFatPct)
                    // Say so. Dropping a weigh-in silently meant a restore could
                    // lose readings and still report a clean import.
                    if (!ok) {
                        problems.add(
                            UiText.themed(
                                R.string.data_import_stat_dropped,
                                R.string.data_import_stat_dropped__t,
                                Instant.ofEpochMilli(it.takenAtMs).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                                it.weightKg.toString(),
                                (it.bodyFatPct ?: "-").toString(),
                            ),
                        )
                    }
                    ok
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
                            passed = it.passed,
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

                // Idle/gacha/cosmetic state: a v5 archive REPLACES all four
                // tables (delete-then-insert inside this transaction, so a
                // mid-way failure cannot half-restore). A v4 archive carries
                // none of these keys — absence means "no information", never
                // "empty set", so the local rows survive untouched.
                if (archive.idle != null || archive.gacha != null) {
                    // A restore never un-pays a level: keep the mark, and treat
                    // the level the restored ledger stands at as already paid.
                    val paidThrough = maxOf(
                        gachaDao.get()?.rollLevelMark ?: 0,
                        Xp.levelFor(profileDao.get()?.totalXp ?: 0L),
                        archive.gacha?.rollLevelMark ?: 0,
                    )
                    idleDao.clearAll()
                    gachaDao.clearRolls()
                    gachaDao.clearFrames()
                    gachaDao.clearRelics()
                    // The rate multiplier is derived from the vault, never trusted
                    // from the file: a stored number could outrun the relics it
                    // claims to come from. Each restored relic is held to the band
                    // the roller can produce, and an impossible one is dropped.
                    // Every relic is then placed in a house by RelicHouses.place: a v8 row keeps its
                    // id, a row from before houses (v7, or an early v8) is mapped like the migration
                    // maps it, and duplicates merge, so the vault is one row per relic either way.
                    val restoredRelics = RelicHouses.place(
                        archive.relics.mapIndexedNotNull { i, r ->
                            Relics.restorable(r.multiplier)?.let { m ->
                                RelicRow(i.toLong(), r.relicId.ifBlank { null }, r.name, m, r.drawnAtMs, r.refinements.coerceAtLeast(0))
                            }
                        },
                    )
                    archive.idle?.let {
                        val essence = it.essence.coerceAtLeast(0)
                        idleDao.upsert(
                            IdleStateEntity(
                                essence = essence,
                                shadows = it.shadows.coerceAtLeast(0),
                                relicMultiplier = Relics.effectiveMultiplier(restoredRelics.map { r -> r.multiplier }),
                                lastCollectedAtMs = it.lastCollectedAtMs,
                                lifetimeEssence = maxOf(it.lifetimeEssence, essence),
                            ),
                        )
                    }
                    gachaDao.upsert(
                        GachaStateEntity(
                            rolls = (archive.gacha?.rolls ?: 0).coerceAtLeast(0),
                            equippedFrame = archive.gacha?.equippedFrame,
                            figureStreak = (archive.gacha?.figureStreak ?: 0).coerceAtLeast(0),
                            rollLevelMark = paidThrough,
                            relicPity = (archive.gacha?.relicPity ?: 0).coerceAtLeast(0),
                            drawsSpent = (archive.gacha?.drawsSpent ?: 0).coerceAtLeast(0),
                            offeringsMade = (archive.gacha?.offeringsMade ?: 0).coerceAtLeast(0),
                            // An archive from before v8 carries 0, so the one-time grant
                            // runs for it on the next launch; a v8 archive is already paid.
                            veilGrantVersion = (archive.gacha?.veilGrantVersion ?: 0).coerceAtLeast(0),
                        ),
                    )
                    archive.crestFrames.forEach {
                        gachaDao.insertFrame(OwnedCrestFrameEntity(frameId = it.frameId, ownedAtMs = it.ownedAtMs, source = it.source))
                    }
                    restoredRelics.forEach {
                        gachaDao.insertRelic(
                            OwnedRelicEntity(
                                name = RelicHouses.byId(it.relicId)!!.name,
                                multiplier = it.multiplier,
                                drawnAtMs = it.drawnAtMs,
                                relicId = it.relicId,
                                refinements = it.refinements,
                            ),
                        )
                    }
                    // Crests the restored level, deeds and houses owe, on top of the archive's own (additive).
                    reconcileCrests(queue = false)
                }

                exerciseDao.observeAll().first().syncCustomExercises()
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
        if (archive.settings != null && archiveSettings != null) {
            try {
                archiveSettings.restore(archive.settings, includeDeviceOnly = !archive.deviceDataOmitted)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw SettingsRestoreException(e)
            }
        }
        result
    }

    // ------------------------------------------------------------------ CSV import

    data class MergeResult(
        val sessions: Int,
        val sets: Int,
        val skipped: Int,
        val xpAwarded: Int,
        val newMovements: List<String>,
    )

    /**
     * Creates (or returns the existing) catalogue row for an unmatched import
     * name the lifter chose to KEEP AS NEW MOVEMENT. The metric comes from the
     * columns the file actually carries data in — never importArchive's blind
     * PULL/REPS guess.
     */
    suspend fun ensureImportMovement(name: String, metric: ExerciseMetric): Long {
        exerciseDao.byName(name)?.let { return it.id }
        val existing = exerciseDao.observeAll().first().firstOrNull { it.name.equals(name, true) }
        if (existing != null) return existing.id
        return exerciseDao.insertAll(
            listOf(
                ExerciseEntity(
                    name = name,
                    muscleGroup = MuscleGroup.CORE.name,
                    isWeighted = false,
                    metric = metric.name,
                    // The same kind of row the form makes, with no muscles or gear chosen yet.
                    custom = true,
                ),
            ),
        ).first()
    }

    /**
     * APPENDS parsed Strong/Hevy/FitNotes history to the lifter's log. This is the one
     * import path that clears nothing: pre-existing sessions, presets, stats
     * and titles are untouched, and [importArchive] (a destructive replace) is
     * never involved.
     *
     * - Idempotence key (startedAtMs, lowercased trimmed label): a workout
     *   already in the log is skipped, so re-importing the same file adds 0.
     * - Sessions land completed with strengthScore = 0 and imported = true;
     *   [rescoreStrengthScores] then scores them from their stored sets with
     *   the app's own formula and interpolated bodyweight.
     * - XP decision (owner, 2026-09-23): imported history DOES earn level XP,
     *   computed per session with the same Xp.award / ActivityScore.xp calls
     *   completeSession uses, summed and added to totalXp ONCE. No quest
     *   bonus, NO gacha rolls for level-ups — history is not fresh play.
     * - Titles re-evaluate through the existing engine, dated at import time.
     * - Workout notes go to the PRIVATE note only; a public note is never
     *   filled from an import.
     */
    suspend fun mergeImported(
        parsed: CsvWorkoutReader.ParsedImport,
        mapping: Map<String, Long>,
    ): MergeResult = db.withTransaction {
        val scoring = SessionScoring(exerciseDao.observeAll().first())
        val latestBodyweight = statDao.observeAll().first().firstOrNull()?.weightKg
        // Each imported workout is judged against everything before it: the log as it stood,
        // then the workouts of this file already merged. No bonus is paid on an import.
        val bodyweightAt = SetRecords.bodyweightLookup(observeStats().first())
        val priced = observeHistory().first().toMutableList()

        var insertedSets = 0
        var skipped = 0
        var totalXp = 0L
        var sessionsInserted = 0

        parsed.workouts.forEach { workout ->
            val key = workout.startedAtMs
            val normalisedLabel = workout.label.lowercase().trim()
            if (sessionDao.countImportKey(key, normalisedLabel) > 0) {
                skipped++
                return@forEach
            }
            val mapped = workout.sets.mapNotNull { set ->
                val exerciseId = mapping[set.exerciseName.trim().lowercase()] ?: return@mapNotNull null
                // The set's own note stays a modifier; its RPE is the effort field, not text.
                // The app's own CSV names the modifiers in a column of their own, and that wins over the note.
                val mods = set.modifiers.trim().ifEmpty { set.notes.trim() }
                Triple(set, exerciseId, mods)
            }
            val sessionId = sessionDao.insertSession(
                SessionEntity(
                    presetId = null,
                    label = workout.label.trim().ifBlank { "Imported ${Words.trial.s}" },
                    startedAtMs = workout.startedAtMs,
                    completedAtMs = workout.completedAtMs ?: workout.startedAtMs,
                    xpAwarded = 0,
                    strengthScore = 0,
                    title = "",
                    note = "",
                    // Workout notes are private: an import never speaks publicly.
                    privateNote = workout.notes.take(WireLimits.PRIVATE_NOTE_MAX),
                    imported = true,
                ),
            )
            val placements = CsvWorkoutReader.blockPlacements(mapped.map { it.second }, mapped.map { Variation.key(it.third) })
            val setEntities = mapped.zip(placements) { (set, exerciseId, mods), (position, index) ->
                val kind = SetKind.fromImport(set.setType)
                SetLogEntity(
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    exercisePosition = position,
                    setIndex = index,
                    reps = set.reps,
                    weightKg = set.weightKg,
                    modifiers = mods,
                    // A warm-up is kept, unticked, so nothing that counts sets counts it.
                    done = kind.countsAsWork,
                    durationSec = set.durationSec,
                    distanceM = set.distanceM,
                    grade = null,
                    kind = kind.name,
                    supersetGroup = set.supersetGroup,
                    rpe = Effort.fromImported(set.rpe).takeIf { kind.countsAsWork },
                )
            }
            sessionDao.insertSets(setEntities)
            insertedSets += setEntities.size
            sessionsInserted++

            // Per-session XP, priced exactly as completeSession prices it.
            val sessionXp = scoring.xp(setEntities, latestBodyweight, scoring.book(priced, bodyweightAt, beforeMs = workout.startedAtMs))
            priced += WorkoutSession(
                id = sessionId,
                label = workout.label,
                startedAtMs = workout.startedAtMs,
                completedAtMs = workout.completedAtMs ?: workout.startedAtMs,
                imported = true,
            ) to scoring.domainSets(setEntities)
            if (sessionXp > 0) {
                sessionDao.updateSession(sessionDao.byId(sessionId)!!.copy(xpAwarded = sessionXp))
            }
            totalXp += sessionXp
        }

        if (totalXp > 0) profileDao.addXp(totalXp)

        // Titles re-evaluate through the existing engine, dated at import time.
        // Same ledger call completeSession uses; nothing is "newly dated".
        val profile = profileDao.get() ?: error("Profile missing")
        val ledger = Titles.ledgerOf(
            totalXp = profile.totalXp,
            history = observeHistory().first(),
            healthDays = observeHealthDays().first(),
            practices = observeSkillPractices().first(),
            exercises = exerciseCatalogue(),
            sex = profileSex(),
            bodyweightAt = bodyweightLookup(),
        )
        val newly = Titles.newlyUnlocked(ledger, titleDao.heldIds().toSet())
        titleDao.insertAll(unlockRows(newly, ledger, System.currentTimeMillis()))
        settleNewTitles(newly)

        MergeResult(
            sessions = sessionsInserted,
            sets = insertedSets,
            skipped = skipped,
            xpAwarded = totalXp.toInt(),
            newMovements = emptyList(),
        )
    }.also {
        // Scores and the lifetime sum are recomputed from the stored sets
        // after the transaction, exactly like the weigh-in repair path.
        rescoreStrengthScores(onlyUnscored = true)
    }

    // ------------------------------------------------------------------ idle

    fun observeIdle(): Flow<IdleState> =
        idleDao.observe().map { (it ?: IdleStateEntity()).toIdleState() }

    fun observeIdleInputs(): Flow<IdleInputs> = combine(
        sessionDao.observeCompletedWithSets(),
        skillPracticeDao.observeAll(),
    ) { completed, practices ->
        idleInputs(completed, practices)
    }

    fun observeIdleRate(): Flow<IdleRate> = combine(
        observeIdle(),
        observeIdleInputs(),
        observeHouseEffects(),
    ) { state, inputs, houses ->
        Idle.rate(state, inputs.sessionsLast7d, inputs.volumeLast7d, inputs.skillsUnlocked, inputs.streakDays, houses)
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
        // No baseline yet. The seeded row carries lastCollectedAtMs = 0, which
        // Idle.accruedExact deliberately reads as "nothing earned" (the epoch
        // would otherwise pay a 56-year absence). Nothing else ever wrote the
        // timestamp, so `gained > 0` never held and a brand new lifter accrued
        // zero essence forever. Start the clock instead of banking nothing.
        if (current.lastCollectedAtMs <= 0L) {
            idleDao.upsert(current.copy(lastCollectedAtMs = nowMs))
            return@withTransaction 0L
        }
        val state = current.toIdleState()
        val inputs = idleInputs(
            sessionDao.observeCompletedWithSets().first(),
            skillPracticeDao.observeAll().first(),
        )
        // The same effects (houses AND the worn crest) the rate on screen is built from, or the banked
        // essence and the shown rate drift. A change of crest collects here first, at the crest it replaces.
        val rate = Idle.rate(
            state, inputs.sessionsLast7d, inputs.volumeLast7d, inputs.skillsUnlocked, inputs.streakDays,
            rateEffectsNow(),
        )
        val gained = Idle.accrued(state, rate, nowMs)
        if (gained > 0) {
            val balance = Veil.Balance(current.essence, current.lifetimeEssence).earn(gained)
            idleDao.upsert(
                current.copy(
                    essence = balance.essence,
                    lifetimeEssence = balance.lifetime,
                    lastCollectedAtMs = nowMs,
                ),
            )
        }
        gained
    }

    /** Gacha hook: adds to the roll, never lowers an existing relic multiplier. */
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

    /**
     * The pity counters as the roller reads them ([spendRoll]): what the last draw counted and so what
     * the next one will do. Read once after a draw for its reveal, and by the buy sheet.
     */
    fun observePity(): Flow<Gacha.Pity> = combine(gachaDao.observeRolls(), gachaDao.observeRelics()) { g, relics ->
        val state = g ?: GachaStateEntity()
        Gacha.Pity(
            figureStreak = state.figureStreak,
            relicStreak = state.relicPity,
            draws = state.drawsSpent,
            hasRelic = relics.isNotEmpty(),
        )
    }

    fun observeOwnedFrames(): Flow<Set<String>> =
        gachaDao.observeOwnedFrames().map { it.toSet() }

    /** Every relic held, strongest first. */
    fun observeRelics(): Flow<List<RelicHolding>> = gachaDao.observeRelics().map { rows ->
        rows.map { RelicHolding(it.id, it.name, it.multiplier, it.drawnAtMs, it.relicId, it.refinements) }
    }

    private fun OwnedRelicEntity.toOwned() = OwnedRelic(relicId, multiplier, refinements, drawnAtMs)

    /**
     * What the relic houses and the worn crest change in the rate right now: the one value the rate on
     * screen ([observeIdleRate]), a collect ([collectIdle]) and the cloud push all read.
     */
    fun observeHouseEffects(): Flow<HouseEffects> =
        combine(gachaDao.observeRelics(), gachaDao.observeEquipped()) { rows, worn ->
            RelicHouses.effects(rows.map { it.toOwned() }, worn)
        }

    /** [observeHouseEffects] once, inside a transaction. */
    private suspend fun rateEffectsNow(): HouseEffects =
        RelicHouses.effects(gachaDao.relics().map { it.toOwned() }, gachaDao.get()?.equippedFrame)

    /** What each crest would add to the rate now, in essence an hour ([Crests.worth]). */
    fun observeCrestWorth(): Flow<Map<String, Double>> = combine(
        observeIdle(),
        observeIdleInputs(),
        gachaDao.observeRelics(),
    ) { state, inputs, rows ->
        Crests.worth(state, inputs.sessionsLast7d, inputs.volumeLast7d, inputs.skillsUnlocked, inputs.streakDays, rows.map { it.toOwned() })
    }

    /** The worn crest's effects: what prices an inscription and what the draws pay. */
    fun observeCrestEffects(): Flow<CrestEffects> = observeHouseEffects().map { it.crest }

    /**
     * The vault grid: per house and form whether it is held, its tier and whether it is the active
     * relic, plus each house's progress, the set bonuses it has reached and the effects in force.
     */
    fun observeVault(): Flow<VaultState> =
        gachaDao.observeRelics().map { rows -> RelicHouses.vault(rows.map { it.toOwned() }) }

    /** [observeVault] once, for the reveal that follows a draw. */
    suspend fun vaultNow(): VaultState = RelicHouses.vault(gachaDao.relics().map { it.toOwned() })

    fun observeEquippedFrame(): Flow<String?> = gachaDao.observeEquipped()

    /** When the worn crest last changed (null: never), for the once-a-day rule's message. */
    fun observeEquipChangedAt(): Flow<Long?> = gachaDao.observeEquipChangedAt()

    /**
     * Wears (or takes off, with null) a crest. A crest the lifter does not own is refused
     * ([Crests.Equip.NotOwned]) rather than thrown — the ownership check reads owned_crest_frames inside
     * the same transaction as the write, so a concurrent draw can never race it. The worn crest changes
     * once a local day ([Crests.canChange]; the first change is always allowed, taking it off counts), and
     * a change first collects what is owed at the crest being replaced.
     */
    suspend fun equipFrame(frameId: String?, nowMs: Long = System.currentTimeMillis()): Crests.Equip = db.withTransaction {
        if (frameId != null && frameId !in gachaDao.ownedFrameIds()) {
            return@withTransaction Crests.Equip.NotOwned
        }
        val current = gachaDao.get() ?: GachaStateEntity()
        // Wearing what is already worn changes nothing, so it costs nothing either.
        if (current.equippedFrame == frameId) return@withTransaction Crests.Equip.Worn
        if (!Crests.canChange(current.equippedChangedAtMs, nowMs)) return@withTransaction Crests.Equip.TooSoon
        // What is owed is collected at the OLD crest's rate before the new one takes over, so a swap can
        // never be timed to reprice time already spent.
        collectIdle(nowMs)
        gachaDao.upsert((gachaDao.get() ?: current).copy(equippedFrame = frameId, equippedChangedAtMs = nowMs))
        Crests.Equip.Worn
    }

    /** Called on level-up: banks one more roll to spend on the Muster screen. */
    /**
     * Recomputes the live rate multiplier from everything in the vault. Must be
     * called inside the same transaction as any relic insert: the multiplier is
     * derived state, and a relic that is owned but not applied is a lie.
     */
    private suspend fun applyRelicVault() {
        // The stored relic number is the whole vault stacked: the factor of the whole rate. The
        // houses ([RelicHouses.effects]) add only their set bonuses on top of it.
        val effective = Relics.effectiveMultiplier(gachaDao.relicMultipliers())
        val state = idleDao.get() ?: IdleStateEntity()
        idleDao.upsert(state.copy(relicMultiplier = effective))
    }

    suspend fun grantRoll(count: Int = 1) = db.withTransaction {
        val current = gachaDao.get() ?: GachaStateEntity()
        gachaDao.upsert(current.copy(rolls = current.rolls + count))
    }

    /** Extra inscriptions bought with essence so far; sets the price of the next ([Veil.offeringCost]). */
    fun observeOfferingsMade(): Flow<Int> =
        gachaDao.observeRolls().map { it?.offeringsMade ?: 0 }

    /**
     * Spends essence on one extra inscription, atomically: the price check, the
     * debit and the banked roll share one transaction, so two taps cannot both
     * buy on one balance. Returns false (and changes nothing) when the banked
     * essence is short of the price. Lifetime essence is left alone, so the
     * board number never falls.
     */
    suspend fun buyInscription(): Boolean = db.withTransaction {
        val g = gachaDao.get() ?: GachaStateEntity()
        val idle = idleDao.get() ?: IdleStateEntity()
        // The price of the worn crest: the same Veil.offeringCost every screen shows it with.
        val price = Veil.offeringCost(g.offeringsMade, rateEffectsNow().crest)
        val paid = Veil.Balance(idle.essence, idle.lifetimeEssence).buy(price)
            ?: return@withTransaction false
        idleDao.upsert(idle.copy(essence = paid.essence, lifetimeEssence = paid.lifetime))
        gachaDao.upsert(g.copy(rolls = g.rolls + 1, offeringsMade = g.offeringsMade + 1))
        true
    }

    /**
     * Spends one banked roll and applies the payout atomically: the decrement,
     * the draw, and the figures/relic/frame grant all run inside one
     * transaction, so a crash can never consume a roll without paying it out.
     * Returns null when nothing is banked.
     */
    suspend fun spendRoll(seed: Long): RollResult? = db.withTransaction {
        val current = gachaDao.get() ?: GachaStateEntity()
        if (current.rolls <= 0) return@withTransaction null
        // Owned frames are excluded from the draw: a duplicate was silently
        // deduped on insert, so the roll was spent and nothing was granted.
        val held = gachaDao.relics()
        val pity = Gacha.Pity(
            figureStreak = current.figureStreak,
            relicStreak = current.relicPity,
            draws = current.drawsSpent,
            hasRelic = held.isNotEmpty(),
        )
        val result = Gacha.roll(
            seed, gachaDao.ownedFrameIds().toSet(), pity, held.associate { it.relicId to it.multiplier },
            perks = rateEffectsNow().crest.draw,
        )
        // The streaks are written in the SAME transaction as the payout, so a
        // crash between the two can never leave pity counting a draw that was
        // never paid.
        val next = pity.after(result.reward)
        gachaDao.upsert(
            current.copy(
                rolls = current.rolls - 1,
                figureStreak = next.figureStreak,
                relicPity = next.relicStreak,
                drawsSpent = next.draws,
            ),
        )
        when (val reward = result.reward) {
            is Reward.Figures -> grantIdle(reward.count, 1.0)
            is Reward.Relic -> {
                // Keep the relic itself, not just its number, then DERIVE the
                // live multiplier from the whole vault so every relic counts.
                val row = held.firstOrNull { it.relicId == reward.relicId }
                if (row == null) {
                    gachaDao.insertRelic(
                        OwnedRelicEntity(
                            name = reward.name,
                            multiplier = reward.multiplier,
                            drawnAtMs = System.currentTimeMillis(),
                            relicId = reward.relicId,
                        ),
                    )
                } else {
                    // A duplicate refines the relic it repeats; at its tier's cap it pays echoes.
                    gachaDao.updateRelic(row.copy(multiplier = reward.multiplier, refinements = row.refinements + 1))
                    if (reward.echoes > 0) grantIdle(reward.echoes, 1.0)
                }
                applyRelicVault()
                // A relic that completes a house pays that house's crest.
                reconcileCrests()
            }
            is Reward.CrestFrame -> gachaDao.insertFrame(
                OwnedCrestFrameEntity(frameId = reward.id, ownedAtMs = System.currentTimeMillis(), source = CrestSource.Draw.id),
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
            sealedTrials = completed.size,
        )
    }

    /** `shadows` is the on-disk column name; `figures` is what the app calls it now. */
    private fun IdleStateEntity.toIdleState() =
        IdleState(
            essence = essence,
            figures = shadows,
            relicMultiplier = relicMultiplier,
            lastCollectedAtMs = lastCollectedAtMs,
            lifetimeEssence = maxOf(lifetimeEssence, essence),
        )

    /** Serialises the circle bonus: two concurrent reads must not both see "unpaid". */
    private val circlePayoutMutex = Mutex()

    /**
     * Pays the weekly circle bonus for every settled week the server lists and
     * this lifter has not been paid for. The XP lands in the local profile
     * through the same [Xp] plumbing a completed session uses (zero server
     * writes). Each once-per-lifter+week flag is written BEFORE the XP and under
     * a lock, so two concurrent circle reads (the ALLIES tab and the Veil banner
     * both fire on launch) cannot both pay; if the XP cannot be added the flags
     * are taken back. Returns what was paid, or null when nothing is owed.
     */
    suspend fun payCircleBonus(
        myUserId: String,
        bonuses: List<CircleBonusWeek>,
        payoutStore: CirclePayoutStore,
    ): CircleBonusPaid? = circlePayoutMutex.withLock {
        payoutStore.adoptLegacy(myUserId)
        val owed = CirclePayout.owedWeeks(bonuses) { payoutStore.hasPaid(myUserId, it) }
        if (owed.isEmpty()) return@withLock null
        owed.forEach { payoutStore.markPaid(myUserId, it) }
        val xp = CirclePayout.BONUS_XP * owed.size
        try {
            // NonCancellable: once the weeks are marked the write must land
            // whole, or a cancel arriving as it commits would unmark weeks
            // whose XP is already in and pay them twice. XP and inscriptions
            // share one transaction, like every other level-up.
            withContext(NonCancellable) {
                db.withTransaction {
                    val levelBefore = Xp.levelFor(profileDao.get()?.totalXp ?: 0L)
                    profileDao.addXp(xp.toLong())
                    bankLevelRolls(levelBefore, Xp.levelFor(profileDao.get()?.totalXp ?: 0L))
                }
            }
        } catch (e: Throwable) {
            // A cancellation is not a failed write: the flags stay, the XP is in.
            if (e !is CancellationException) owed.forEach { payoutStore.unmark(myUserId, it) }
            throw e
        }
        reconcileTitles()
        CircleBonusPaid(xp, bonuses.lastOrNull { it.week == owed.last() }?.circleName)
    }

    companion object {
        /**
         * Enough to cover a week's training without the recent strip becoming
         * a second catalogue to scroll.
         */
        const val RECENT_EXERCISE_LIMIT = 12

        /** A trial started this recently reopens on launch, as though never left. */
        const val RESUME_WINDOW_MS = 3L * 60 * 60 * 1000
    }
}

/** Raw training inputs feeding Idle.rate — exposed for the rate WHY-breakdown. */
data class IdleInputs(
    val sessionsLast7d: Int,
    val volumeLast7d: Double,
    val skillsUnlocked: Int,
    val streakDays: Int,
    /** Every trial ever sealed, so a lapsed lifter is told apart from a new one. */
    val sealedTrials: Int = 0,
)

/** Idle state plus the rate the Ledger is currently paying. */
data class IdleSnapshot(
    val state: IdleState,
    val rate: IdleRate,
)

/** One drawn relic, for the Muster screen's vault. */
data class RelicHolding(
    val id: Long,
    val name: String,
    val multiplier: Double,
    val drawnAtMs: Long,
    val relicId: String = "",
    val refinements: Int = 0,
)
