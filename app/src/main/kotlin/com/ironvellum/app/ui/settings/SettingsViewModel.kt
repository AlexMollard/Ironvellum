package com.ironvellum.app.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ironvellum.app.data.CrashJournal
import com.ironvellum.app.data.HealthSnapshot
import com.ironvellum.app.data.HealthSync
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.data.cloud.CloudConfig
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.cloud.ProbeResult
import com.ironvellum.app.data.cloud.trueNameProblem
import com.ironvellum.app.domain.BodyLimits
import com.ironvellum.app.domain.CsvWorkoutReader
import com.ironvellum.app.domain.Exercise
import com.ironvellum.app.domain.ExerciseMetric
import com.ironvellum.app.domain.ImportAliases
import com.ironvellum.app.domain.Sex
import com.ironvellum.app.domain.TrainingMode
import com.ironvellum.app.domain.DecimalInput
import com.ironvellum.app.ui.components.formatBodyValue
import com.ironvellum.app.ui.components.formatDate
import com.ironvellum.app.ui.ironvellumAccount
import com.ironvellum.app.ui.ironvellumCloudSync
import com.ironvellum.app.ui.ironvellumHealthSync
import com.ironvellum.app.ui.ironvellumRepository
import com.ironvellum.app.ui.launchGuarded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val HEALTH_PERMISSIONS = setOf(
    HealthPermission.getReadPermission(WeightRecord::class),
    HealthPermission.getReadPermission(BodyFatRecord::class),
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(DistanceRecord::class),
    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    HealthPermission.getReadPermission(SleepSessionRecord::class),
    HealthPermission.getReadPermission(RestingHeartRateRecord::class),
)

/** Asked on top of [HEALTH_PERMISSIONS]; a declined history grant must not
 * make a working sync report "not connected", and its absence hides data
 * recorded before the app was installed. */
private val HISTORY_PERMISSION = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

/**
 * What the Connect button asks for: the data reads, plus history reading so
 * steps recorded before the install are visible, plus background reading
 * where this Health Connect build supports it so the daily worker can sync
 * while the app is closed. Both extras are kept out of [HEALTH_PERMISSIONS]
 * on purpose: that set is the "is anything connected" check, and a declined
 * background or history grant must not make a working foreground sync
 * report "not connected".
 */
internal fun healthPermissionRequest(context: Context): Set<String> {
    val background = runCatching {
        HealthConnectClient.getOrCreate(context).features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND,
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)
    return if (background) {
        HEALTH_PERMISSIONS + HISTORY_PERMISSION + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    } else {
        HEALTH_PERMISSIONS + HISTORY_PERMISSION
    }
}

data class SyncUi(
    val available: Boolean = true,
    /** True for the whole Connect & Sync run: today's snapshot, then the history read. */
    val syncing: Boolean = false,
    val result: HealthSnapshot? = null,
    val message: String? = null,
    /** What the history read wrote, or why it wrote nothing. */
    val historyMessage: String? = null,
    /** Days each metric actually filled: "steps 31 · distance 0 · ...". Shown under Advanced. */
    val coverage: String? = null,
)

/** Whether Health Connect is usable here and has granted any read; the hub row's value. */
enum class HealthLink { UNAVAILABLE, UPDATE_REQUIRED, NOT_CONNECTED, CONNECTED }

/** Inline result of committing a text field: a Saved tick, or the reason it was refused. */
data class FieldStatus(val saved: Boolean = false, val error: String? = null)

data class ImportUi(
    val importing: Boolean = false,
    /** On success a count summary, on failure the reason verbatim from the Result. */
    val summary: String? = null,
    /** Reader warnings, first entry plus a count — shown, never swallowed. */
    val problems: String? = null,
)

/** State of the Settings → Advanced cloud controls: probe/switch progress and result. */
data class CloudUi(
    val testing: Boolean = false,
    /** Result of the last TEST, or null before the first one. */
    val probe: ProbeResult? = null,
    /** True while a confirmed switch (probe already passed) is being applied. */
    val switching: Boolean = false,
)

/**
 * The one SettingsViewModel the hub and every sub-screen share. [owner] is the
 * hub's back stack entry, so a sub-screen route sees the same probe, import
 * review and sync state the hub does instead of a fresh copy.
 */
@Composable
fun settingsViewModel(owner: ViewModelStoreOwner): SettingsViewModel = viewModel(
    viewModelStoreOwner = owner,
    factory = viewModelFactory {
        initializer {
            SettingsViewModel(ironvellumRepository(), ironvellumHealthSync(), ironvellumCloudSync(), ironvellumAccount())
        }
    },
)

class SettingsViewModel(
    private val repo: Repository,
    private val healthSync: HealthSync,
    private val cloudSync: CloudSync,
    private val accountRepo: AccountRepository,
) : ViewModel() {

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _import = MutableStateFlow(ImportUi())
    val import: StateFlow<ImportUi> = _import.asStateFlow()
    private val _importReview = MutableStateFlow<ImportReviewUi?>(null)
    val importReview: StateFlow<ImportReviewUi?> = _importReview.asStateFlow()

    /** Catalogue names for the review picker; refreshed when review opens. */
    val catalogueNames: StateFlow<List<String>> = repo.observeCatalogueNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** The catalogue rows themselves, for the universal picker the review maps names with. */
    val catalogueExercises: StateFlow<List<Exercise>> = repo.observeExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _sync = MutableStateFlow(SyncUi(available = runCatching { healthSync.available() }.getOrDefault(false)))
    val sync: StateFlow<SyncUi> = _sync.asStateFlow()

    private val _healthLink = MutableStateFlow(HealthLink.NOT_CONNECTED)
    val healthLink: StateFlow<HealthLink> = _healthLink.asStateFlow()

    /** The active backend, straight from Cloud — a switch elsewhere redraws here. */
    val cloudConfig: StateFlow<CloudConfig?> = Cloud.config

    private val _cloud = MutableStateFlow(CloudUi())
    val cloud: StateFlow<CloudUi> = _cloud.asStateFlow()

    val profile = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Signed in, the name shown on Today is the cloud true name; this tells the caption which. */
    val signedIn: StateFlow<Boolean> = accountRepo.account
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), accountRepo.account.value != null)

    private val _renameError = MutableStateFlow<String?>(null)
    val renameError: StateFlow<String?> = _renameError.asStateFlow()
    private val _renaming = MutableStateFlow(false)
    val renaming: StateFlow<Boolean> = _renaming.asStateFlow()

    /** True once the last commit of the name field stuck; cleared by the next keystroke. */
    private val _nameSaved = MutableStateFlow(false)
    val nameSaved: StateFlow<Boolean> = _nameSaved.asStateFlow()

    /** The name field changed: any old error or Saved tick no longer describes it. */
    fun clearRenameError() {
        _renameError.value = null
        _nameSaved.value = false
    }

    /** Profile-owned height and sex, edited on the Profile sub-screen. */
    val bodyProfile: StateFlow<Pair<Double?, Sex>> = repo.observeBodyProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null to Sex.MALE)

    val healthDays = repo.observeHealthDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _heightStatus = MutableStateFlow(FieldStatus())
    val heightStatus: StateFlow<FieldStatus> = _heightStatus.asStateFlow()

    fun clearHeightStatus() {
        _heightStatus.value = FieldStatus()
    }

    /** A Saved tick describes the last visit; reopening Profile starts without one. Errors stay. */
    fun clearSavedTicks() {
        _nameSaved.value = false
        _heightStatus.value = _heightStatus.value.copy(saved = false)
    }

    /**
     * Commits the height field. Blank or unchanged is not an edit and does
     * nothing; anything out of range is refused inline rather than dropped.
     */
    fun setHeight(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val cm = DecimalInput.parse(trimmed)
        if (!BodyLimits.validHeight(cm)) {
            _heightStatus.value = FieldStatus(
                error = "Height must be ${BodyLimits.HEIGHT_CM.start.toInt()}–${BodyLimits.HEIGHT_CM.endInclusive.toInt()} cm",
            )
            return
        }
        if (cm == bodyProfile.value.first) return
        viewModelScope.launchGuarded("set height") {
            repo.setHeight(cm!!)
            _heightStatus.value = FieldStatus(saved = true)
        }
    }

    fun setSex(sex: Sex) {
        viewModelScope.launch { repo.setSex(sex) }
    }


    fun exportJson(onReady: (String) -> Unit) {
        if (_exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            val json = repo.exportJson()
            _exporting.value = false
            onReady(json)
        }
    }

    /**
     * Opens the CSV review: parse off the main thread, pre-select Strong's
     * unit with the barbell heuristic, and auto-map every name the curated
     * aliases resolve. Unmatched names land in the review list.
     */
    fun startCsvImport(raw: String) {
        viewModelScope.launch {
            val firstPass = withContext(Dispatchers.IO) {
                CsvWorkoutReader.read(raw, strongWeightIsLbs = false)
            }
            if (firstPass.problems.isNotEmpty() && firstPass.workouts.isEmpty()) {
                _import.value = ImportUi(summary = firstPass.problems.first().message)
                return@launch
            }
            var unit: CsvWorkoutReader.WeightUnit? = null
            var guessed = false
            if (firstPass.source == CsvWorkoutReader.Source.STRONG) {
                guessed = true
                unit = if (looksLikePounds(firstPass)) {
                    CsvWorkoutReader.WeightUnit.LB
                } else {
                    CsvWorkoutReader.WeightUnit.KG
                }
            }
            applyCsvParse(raw, unit, guessed)
        }
    }

    fun setImportUnit(unit: CsvWorkoutReader.WeightUnit) {
        val review = _importReview.value ?: return
        if (review.selectedUnit == unit) return
        viewModelScope.launch { applyCsvParse(reviewRaw ?: return@launch, unit, guessed = false) }
    }

    fun chooseImportMapping(rawName: String, catalogueName: String?) {
        val review = _importReview.value ?: return
        _importReview.value = review.copy(choices = review.choices + (rawName to catalogueName))
    }

    fun dismissImportReview() {
        _importReview.value = null
        reviewRaw = null
    }

    fun confirmCsvImport() {
        val review = _importReview.value ?: return
        if (review.importing) return
        viewModelScope.launch {
            _importReview.value = review.copy(importing = true, result = null)
            val run = runCatching {
                val catalogue = repo.catalogueNamesOnce()
                val mapping = HashMap<String, Long>()
                val newNames = LinkedHashMap<String, ExerciseMetric>()
                review.parsed.workouts.forEach { w ->
                    w.sets.forEach { s ->
                        val key = s.exerciseName.trim().lowercase()
                        if (key.isEmpty() || mapping.containsKey(key)) return@forEach
                        val resolved = ImportAliases.resolve(s.exerciseName, catalogue)
                        if (resolved != null) {
                            mapping[key] = repo.exerciseIdByName(resolved)
                                ?: error("catalogue lost \"$resolved\"")
                        } else {
                            val choice = review.choices[s.exerciseName.trim()]
                            when {
                                choice != null ->
                                    mapping[key] = repo.exerciseIdByName(choice)
                                        ?: error("catalogue lost \"$choice\"")
                                else -> newNames.putIfAbsent(
                                    ImportAliases.stripEquipment(s.exerciseName),
                                    inferredMetric(review.parsed, s.exerciseName),
                                )
                            }
                        }
                    }
                }
                newNames.forEach { (name, metric) ->
                    mapping[name.trim().lowercase()] = repo.ensureImportMovement(name, metric)
                }
                repo.mergeImported(review.parsed, mapping)
            }
            _importReview.value = _importReview.value?.copy(
                importing = false,
                result = run.fold(
                    { r ->
                        "Imported ${r.sessions} trials · ${r.sets} sets · " +
                            "+${r.xpAwarded} XP · ${r.skipped} already in your Chronicle"
                    },
                    { "Import failed: ${it.message ?: "the file could not be read"}" },
                ),
            )
        }
    }

    private var reviewRaw: String? = null

    private fun inferredMetric(
        parsed: CsvWorkoutReader.ParsedImport,
        rawName: String,
    ): ExerciseMetric {
        val sets = parsed.workouts.flatMap { it.sets }.filter { it.exerciseName == rawName }
        return when {
            sets.any { (it.distanceM ?: 0.0) > 0.0 } -> ExerciseMetric.DISTANCE_TIME
            sets.any { (it.durationSec ?: 0) > 0 } -> ExerciseMetric.DURATION
            else -> ExerciseMetric.REPS
        }
    }

    /**
     * Strong's Weight column follows the exporter's app setting, so the unit
     * is a guess until the lifter confirms. The heuristic from the plan: a
     * barbell lift's median working weight at least 1.6x what a trained kg
     * lifter would typically log reads as pounds. Typical trained working
     * weights (kg): bench 60, incline 50, squat 100, deadlift 120, press 40,
     * row 60.
     */
    private fun looksLikePounds(parsed: CsvWorkoutReader.ParsedImport): Boolean {
        val catalogue = catalogueNames.value
        val typical = mapOf(
            "Bench Press" to 60.0,
            "Incline Bench Press" to 50.0,
            "Back Squat" to 100.0,
            "Squat" to 100.0,
            "Deadlift" to 120.0,
            "Overhead Press" to 40.0,
            "Barbell Row" to 60.0,
        )
        return parsed.workouts.asSequence()
            .flatMap { it.sets }
            .filter { (it.weightKg ?: 0.0) > 0.0 }
            .mapNotNull { set ->
                val resolved = ImportAliases.resolve(set.exerciseName, catalogue)
                typical[resolved]?.let { typicalKg -> set.weightKg!! / typicalKg }
            }
            .toList()
            .let { ratios -> ratios.any { it >= 1.6 } }
    }

    private suspend fun applyCsvParse(
        raw: String,
        unit: CsvWorkoutReader.WeightUnit?,
        guessed: Boolean,
    ) {
        reviewRaw = raw
        val parsed = withContext(Dispatchers.IO) {
            CsvWorkoutReader.read(raw, strongWeightIsLbs = unit == CsvWorkoutReader.WeightUnit.LB)
        }
        val catalogue = repo.catalogueNamesOnce()
        val unmatched = parsed.workouts.asSequence()
            .flatMap { it.sets }
            .map { it.exerciseName.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .filter { ImportAliases.resolve(it, catalogue) == null }
            .toList()
        _importReview.value = ImportReviewUi(
            source = parsed.source,
            parsed = parsed,
            selectedUnit = unit,
            unitGuessed = guessed,
            unmatched = unmatched.map { rawName ->
                UnmatchedImportName(
                    rawName = rawName,
                    inferredMetric = inferredMetric(parsed, rawName).name,
                )
            },
            choices = unmatched.associateWith { null },
        )
    }

    fun importArchive(json: String) {
        if (_import.value.importing) return
        viewModelScope.launch {
            _import.value = ImportUi(importing = true)
            _import.value = repo.importArchive(json).fold(
                { r ->
                    ImportUi(
                        summary = "restored ${r.presets} rites · ${r.sessions} sealed trials · " +
                            "${r.sets} sets · ${r.stats} readings · ${r.titles} deeds · " +
                            "${r.skills} Journal attempts · ${r.healthDays} health days",
                        // surface the first problem plus how many more, not silence
                        problems = r.problems.takeIf { it.isNotEmpty() }?.let {
                            if (it.size == 1) it.first() else "${it.first()} (+${it.size - 1} more)"
                        },
                    )
                },
                { e -> ImportUi(summary = e.message ?: "Import failed — the file may not be an Ironvellum archive") },
            )
        }
    }

    /**
     * Signed in, the cloud true name is the source of truth: it is written
     * first and the local name follows only on success, so a taken, invalid or
     * offline rename leaves both names as they were. Signed out, local only.
     */
    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || _renaming.value || trimmed == profile.value?.name) return
        if (accountRepo.account.value == null) {
            viewModelScope.launchGuarded("rename") {
                repo.rename(trimmed)
                _nameSaved.value = true
            }
            return
        }
        trueNameProblem(trimmed)?.let { _renameError.value = it; return }
        viewModelScope.launch {
            _renaming.value = true
            _renameError.value = null
            accountRepo.updateDisplayName(trimmed)
                .onSuccess {
                    // The name the cloud actually kept (it drops punctuation), not what was typed.
                    accountRepo.account.value?.let { repo.rename(it.displayName) }
                    _nameSaved.value = true
                }
                .onFailure { _renameError.value = it.message ?: "Could not change your name" }
            _renaming.value = false
        }
    }

    fun setMode(mode: TrainingMode) {
        viewModelScope.launch { repo.setTrainingMode(mode) }
    }

    fun setInkStyle(on: Boolean) {
        viewModelScope.launch { repo.setInkStyle(on) }
    }

    /** Ask a candidate backend whether it is ready. Never disturbs the live session. */
    fun testBackend(url: String, key: String) {
        val trimmedUrl = url.trim()
        val trimmedKey = key.trim()
        if (trimmedUrl.isBlank() || trimmedKey.isBlank() || _cloud.value.testing) return
        viewModelScope.launch {
            _cloud.value = CloudUi(testing = true)
            val result = Cloud.probe(CloudConfig(trimmedUrl, trimmedKey, isDefault = false))
            _cloud.value = CloudUi(probe = result)
        }
    }

    /**
     * Switch to a lifter's own project. The ordering guarantee lives inside
     * [Cloud.reconfigure]: sign out against the OLD backend, forget the push
     * watermark (beforeSwap), then swap the client and persist — so every
     * session re-uploads to the new backend after the lifter signs in there.
     */
    fun switchBackend(url: String, key: String) {
        val trimmedUrl = url.trim()
        val trimmedKey = key.trim()
        if (trimmedUrl.isBlank() || trimmedKey.isBlank() || _cloud.value.switching) return
        viewModelScope.launch {
            _cloud.value = CloudUi(switching = true)
            Cloud.reconfigure(CloudConfig(trimmedUrl, trimmedKey, isDefault = false)) {
                cloudSync.forgetPushedState()
            }
            _cloud.value = CloudUi()
        }
    }

    /** Drop the override and return to the maintainer's shared cloud. */
    fun useSharedCloud() {
        if (_cloud.value.switching) return
        viewModelScope.launch {
            _cloud.value = CloudUi(switching = true)
            Cloud.reconfigure(null) {
                cloudSync.forgetPushedState()
            }
            _cloud.value = CloudUi()
        }
    }

    /** Re-reads device support and the grant; the hub calls it on every visit. */
    fun refreshHealthLink() {
        viewModelScope.launch { _healthLink.value = readHealthLink() }
    }

    private suspend fun readHealthLink(): HealthLink = when (runCatching { healthSync.status() }.getOrNull()) {
        HealthSync.Status.READY ->
            if (runCatching { healthSync.grantedPermissions() }.getOrDefault(emptySet()).any { it in HEALTH_PERMISSIONS }) {
                HealthLink.CONNECTED
            } else {
                HealthLink.NOT_CONNECTED
            }
        HealthSync.Status.UPDATE_REQUIRED -> HealthLink.UPDATE_REQUIRED
        else -> HealthLink.UNAVAILABLE
    }

    /**
     * Connect & Sync: today's snapshot, then the activity history. The history
     * also syncs on every app start and in the daily worker; running it here
     * too means a first-time connect fills the Daily page straight away
     * instead of on the next launch.
     */
    fun syncFromHealth() {
        if (_sync.value.syncing) return
        viewModelScope.launch {
            _sync.value = _sync.value.copy(syncing = true, message = null, historyMessage = null)
            if (syncSnapshot()) syncHistory()
            _sync.value = _sync.value.copy(syncing = false)
            _healthLink.value = readHealthLink()
        }
    }

    /** Reads today's snapshot. False when there is nothing to read from: no provider, or no grant. */
    private suspend fun syncSnapshot(): Boolean {
        // Report the actual reason: device support, provider update, a
        // missing permission grant and a read error are different problems.
        when (healthSync.status()) {
            HealthSync.Status.UNSUPPORTED -> {
                _sync.value = _sync.value.copy(
                    available = false,
                    message = "Health Connect isn't available on this device.",
                )
                return false
            }
            HealthSync.Status.UPDATE_REQUIRED -> {
                _sync.value = _sync.value.copy(
                    available = false,
                    message = "Update Health Connect, then retry.",
                )
                return false
            }
            HealthSync.Status.READY -> Unit
        }

        if (healthSync.grantedPermissions().none { it in HEALTH_PERMISSIONS }) {
            _sync.value = _sync.value.copy(
                available = true,
                message = "Permission not granted yet — allow Weight, Body fat and Steps in Health Connect, then retry.",
            )
            return false
        }

        val read = runCatching { healthSync.readSnapshot() }
        val snapshot = read.getOrNull()
        if (snapshot == null) {
            _sync.value = _sync.value.copy(
                available = true,
                message = read.exceptionOrNull()
                    ?.let { "Health Connect read failed${it.message?.let { m -> ": $m" } ?: ""}" }
                    ?: "Health Connect returned no data.",
            )
            return true
        }
        val message: String
        if (snapshot.weightKg != null && !BodyLimits.validWeight(snapshot.weightKg)) {
            // Health Connect is another app's data, so it is a trust
            // boundary like the keyboard: addStat REFUSES an implausible
            // figure, and refusing by throwing here would crash the import
            // rather than report it.
            _sync.value = _sync.value.copy(
                available = true,
                message = "Health Connect returned an implausible weight " +
                    "(${formatBodyValue(snapshot.weightKg)} kg); not imported.",
            )
            return true
        }
        if (snapshot.weightKg != null) {
            // Height is stamped from the profile inside addStat; no height
            // on record yet means the row lands heightless (BMI stays "—").
            // Bounds were checked above, so a throw here means something
            // else went wrong; the import reports it rather than dying.
            val stored = runCatching { repo.addStat(snapshot.weightKg, snapshot.bodyFatPct) }
            if (stored.isFailure) {
                _sync.value = _sync.value.copy(
                    available = true,
                    message = "Could not store the imported reading: " +
                        (stored.exceptionOrNull()?.message ?: "unknown reason"),
                )
                return true
            }
            message = if (bodyProfile.value.first == null) {
                "Imported into your Ledger. Set your height in Profile to unlock BMI."
            } else {
                "Imported into your Ledger."
            }
        } else {
            message = "No weight found in Health Connect yet."
        }
        _sync.value = _sync.value.copy(available = true, result = snapshot, message = message)
        return true
    }

    private suspend fun syncHistory() {
        val read = runCatching { repo.syncHealthHistory() }
            .getOrElse { HealthSync.HistoryRead(problems = listOf(it.javaClass.simpleName)) }
        _sync.value = _sync.value.copy(
            coverage = buildString {
                append(
                    read.coverage.entries.joinToString(" · ") { (metric, days) -> "$metric $days" },
                )
                read.newestStepAtMs?.let {
                    append("\nnewest step record ")
                    append(formatDate(it, "EEE HH:mm"))
                    append(" from ")
                    append(
                        read.stepSources
                            .map { pkg -> pkg.substringAfterLast('.') }
                            .joinToString(", ")
                            .ifBlank { "unknown app" },
                    )
                }
            }.takeIf { it.isNotBlank() },
            historyMessage = when {
                read.days.isNotEmpty() || read.bodyReadings.isNotEmpty() ->
                    "Activity history updated — ${read.days.size} days, " +
                        "${read.bodyReadings.size} readings." +
                        read.problems.firstOrNull()?.let { " Partial: $it" }.orEmpty()
                // never claim success on an empty write again
                read.problems.isNotEmpty() -> "Nothing synced — ${read.problems.first()}"
                else -> "Nothing synced — Health Connect holds no activity for this window."
            },
        )
    }

    /** Crash records on disk and the newest one's time; read off the main thread. */
    suspend fun crashSummary(): Pair<Int, String> = withContext(Dispatchers.IO) {
        CrashJournal.crashCount() to CrashJournal.latestTimestamp()?.substringAfter("—")?.trim().orEmpty()
    }

    /** Deleting and re-counting are both disk work, so both stay off the frame. */
    suspend fun clearCrashLog(): Pair<Int, String> {
        withContext(Dispatchers.IO) { CrashJournal.clear() }
        return crashSummary()
    }
}
