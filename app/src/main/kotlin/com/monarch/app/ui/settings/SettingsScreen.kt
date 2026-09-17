package com.monarch.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monarch.app.data.HealthSnapshot
import com.monarch.app.data.CrashJournal
import com.monarch.app.data.HealthSync
import com.monarch.app.data.Repository
import com.monarch.app.domain.BodyLimits
import com.monarch.app.domain.HealthDay
import com.monarch.app.domain.Sex
import com.monarch.app.domain.TrainingMode
import com.monarch.app.ui.components.MonarchButton
import com.monarch.app.ui.components.formatDate
import com.monarch.app.ui.components.InkSpinner
import com.monarch.app.ui.components.InkSegmented
import com.monarch.app.ui.components.SystemWindow
import com.monarch.app.ui.monarchHealthSync
import com.monarch.app.ui.monarchRepository
import com.monarch.app.ui.theme.ChakraPetch
import com.monarch.app.ui.theme.MonarchTracking
import com.monarch.app.ui.theme.MonarchColors
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import com.monarch.app.ui.launchGuarded
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.runtime.mutableIntStateOf

private val HEALTH_PERMISSIONS = setOf(
    HealthPermission.getReadPermission(WeightRecord::class),
    HealthPermission.getReadPermission(BodyFatRecord::class),
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(DistanceRecord::class),
    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    HealthPermission.getReadPermission(SleepSessionRecord::class),
    HealthPermission.getReadPermission(RestingHeartRateRecord::class),
)

data class SyncUi(
    val available: Boolean = true,
    val syncing: Boolean = false,
    val result: HealthSnapshot? = null,
    val message: String? = null,
    val historySyncing: Boolean = false,
    val historyWritten: Int? = null,
    /** Days each metric actually filled: "steps 31 · distance 0 · ...". */
    val coverage: String? = null,
)

data class ImportUi(
    val importing: Boolean = false,
    /** On success a count summary, on failure the reason verbatim from the Result. */
    val summary: String? = null,
    /** Reader warnings, first entry plus a count — shown, never swallowed. */
    val problems: String? = null,
)

class SettingsViewModel(
    private val repo: Repository,
    private val healthSync: HealthSync,
) : ViewModel() {

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _import = MutableStateFlow(ImportUi())
    val import: StateFlow<ImportUi> = _import.asStateFlow()
    private val _sync = MutableStateFlow(SyncUi(available = runCatching { healthSync.available() }.getOrDefault(false)))
    val sync: StateFlow<SyncUi> = _sync.asStateFlow()

    val profile = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Profile-owned height and sex, edited in the BODY PROFILE window. */
    val bodyProfile: StateFlow<Pair<Double?, Sex>> = repo.observeBodyProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null to Sex.MALE)

    val healthDays = repo.observeHealthDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setHeight(raw: String) {
        val cm = raw.trim().toDoubleOrNull()
        if (BodyLimits.validHeight(cm)) viewModelScope.launchGuarded("set height") { repo.setHeight(cm!!) }
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

    fun importArchive(json: String) {
        if (_import.value.importing) return
        viewModelScope.launch {
            _import.value = ImportUi(importing = true)
            _import.value = repo.importArchive(json).fold(
                { r ->
                    ImportUi(
                        summary = "restored ${r.presets} presets · ${r.sessions} sessions · " +
                            "${r.sets} sets · ${r.stats} readings · ${r.titles} titles · " +
                            "${r.skills} skill logs · ${r.healthDays} health days",
                        // surface the first problem plus how many more, not silence
                        problems = r.problems.takeIf { it.isNotEmpty() }?.let {
                            if (it.size == 1) it.first() else "${it.first()} (+${it.size - 1} more)"
                        },
                    )
                },
                { e -> ImportUi(summary = e.message ?: e.javaClass.simpleName) },
            )
        }
    }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launchGuarded("rename") { repo.rename(trimmed) }
    }

    fun setMode(mode: TrainingMode) {
        viewModelScope.launch { repo.setTrainingMode(mode) }
    }

    fun setInkStyle(on: Boolean) {
        viewModelScope.launch { repo.setInkStyle(on) }
    }

    fun syncFromHealth() {
        if (_sync.value.syncing) return
        viewModelScope.launch {
            _sync.value = _sync.value.copy(syncing = true, message = null)

            // Report the actual reason: device support, provider update, a
            // missing permission grant and a read error are different problems.
            when (healthSync.status()) {
                HealthSync.Status.UNSUPPORTED -> {
                    _sync.value = _sync.value.copy(
                        syncing = false,
                        available = false,
                        message = "Health Connect isn't available on this device.",
                    )
                    return@launch
                }
                HealthSync.Status.UPDATE_REQUIRED -> {
                    _sync.value = _sync.value.copy(
                        syncing = false,
                        available = false,
                        message = "Update Health Connect, then retry.",
                    )
                    return@launch
                }
                HealthSync.Status.READY -> Unit
            }

            if (healthSync.grantedPermissions().none { it in HEALTH_PERMISSIONS }) {
                _sync.value = _sync.value.copy(
                    syncing = false,
                    available = true,
                    message = "Permission not granted yet — allow Weight, Body fat and Steps in Health Connect, then retry.",
                )
                return@launch
            }

            val read = runCatching { healthSync.readSnapshot() }
            val snapshot = read.getOrNull()
            if (snapshot == null) {
                _sync.value = _sync.value.copy(
                    syncing = false,
                    available = true,
                    message = read.exceptionOrNull()
                        ?.let { "Health Connect read failed: ${it.javaClass.simpleName} ${it.message.orEmpty()}".trim() }
                        ?: "Health Connect returned no data.",
                )
                return@launch
            }
            val message: String
            if (snapshot.weightKg != null && !BodyLimits.validWeight(snapshot.weightKg)) {
                // Health Connect is another app's data, so it is a trust
                // boundary like the keyboard: addStat REFUSES an implausible
                // figure, and refusing by throwing here would crash the import
                // rather than report it.
                _sync.value = _sync.value.copy(
                    syncing = false,
                    available = true,
                    message = "Health Connect returned an implausible weight " +
                        "(${snapshot.weightKg} kg); not imported.",
                )
                return@launch
            }
            if (snapshot.weightKg != null) {
                // Height is stamped from the profile inside addStat; no height
                // on record yet means the row lands heightless (BMI stays "—").
                // Bounds were checked above, so a throw here means something
                // else went wrong; the import reports it rather than dying.
                val stored = runCatching { repo.addStat(snapshot.weightKg, snapshot.bodyFatPct) }
                if (stored.isFailure) {
                    _sync.value = _sync.value.copy(
                        syncing = false,
                        available = true,
                        message = "Could not store the imported reading: " +
                            stored.exceptionOrNull()?.message.orEmpty(),
                    )
                    return@launch
                }
                message = if (bodyProfile.value.first == null) {
                    "Imported into your stat history. Set your height below to unlock BMI."
                } else {
                    "Imported into your stat history."
                }
            } else {
                message = "No weight found in Health Connect yet."
            }
            _sync.value = _sync.value.copy(syncing = false, result = snapshot, message = message)
        }
    }

    fun syncActivityHistory() {
        if (_sync.value.historySyncing) return
        viewModelScope.launch {
            _sync.value = _sync.value.copy(historySyncing = true)
            val read = runCatching { repo.syncHealthHistory() }
                .getOrElse { HealthSync.HistoryRead(problems = listOf(it.javaClass.simpleName)) }
            _sync.value = _sync.value.copy(
                historySyncing = false,
                historyWritten = read.days.size,
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
                message = when {
                    read.days.isNotEmpty() || read.bodyReadings.isNotEmpty() ->
                        "Activity history updated — ${read.days.size} days, " +
                            "${read.bodyReadings.size} weigh-ins." +
                            read.problems.firstOrNull()?.let { " Partial: $it" }.orEmpty()
                    // never claim success on an empty write again
                    read.problems.isNotEmpty() -> "Nothing synced — ${read.problems.first()}"
                    else -> "Nothing synced — Health Connect holds no activity for this window."
                },
            )
        }
    }

}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel =
        viewModel(
            factory = viewModelFactory {
                initializer { SettingsViewModel(monarchRepository(), monarchHealthSync()) }
            },
        ),
) {
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val importUi by viewModel.import.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val healthDays by viewModel.healthDays.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Saveable with the profile name as the initial value only: on restore the
    // loaded profile must not overwrite a typed-but-unsaved edit.
    var name by rememberSaveable(profile?.name) { mutableStateOf(profile?.name ?: "") }
    var confirmImport by remember { mutableStateOf(false) }
    // Read off the main thread: these list a directory, and doing that during
    // composition is main-thread disk I/O on every visit to this screen —
    // which is what StrictMode reported. The counts arrive a frame later.
    var crashCount by remember { mutableIntStateOf(0) }
    var latestCrash by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            CrashJournal.crashCount() to CrashJournal.latestTimestamp()
        }.let { (count, latest) ->
            crashCount = count
            latestCrash = latest?.substringAfter("—")?.trim().orEmpty()
        }
    }
    val bodyProfile by viewModel.bodyProfile.collectAsStateWithLifecycle()
    var heightInput by remember(bodyProfile.first) { mutableStateOf(bodyProfile.first?.toString() ?: "") }
    val heightValid = BodyLimits.validHeight(heightInput.toDoubleOrNull())

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { _ ->
        viewModel.syncFromHealth()
    }


    // Import replaces everything, so the picker only fires after the confirm dialog.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                // stream reads can stall on slow providers — never block the main thread
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                        ?.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
                        .orEmpty()
                }
                if (json.isNotBlank()) viewModel.importArchive(json)
            }
        }
    }

    if (confirmImport) {
        AlertDialog(
            // Material's dialog container is a 28dp rounded rect - the most
            // obviously stock surface in the app. Give it the ink shape.
            shape = MaterialTheme.shapes.medium,
            onDismissRequest = { confirmImport = false },
            title = { Text("Restore this archive?") },
            text = {
                Text("Restoring replaces every preset, session, reading, title and skill log currently on this device. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }) { Text("Restore", color = MonarchColors.DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) { Text("Keep local data") }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "SYSTEM",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = ChakraPetch,
            color = MonarchColors.InkMuted,
            letterSpacing = 6.sp,
        )
        Spacer(Modifier.height(10.dp))

        SystemWindow(Modifier.fillMaxWidth()) {
            Text(
                profile?.name ?: "Hunter",
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Claim your name") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f),
                )
                MonarchButton(
                    label = "Claim",
                    onClick = { viewModel.rename(name) },
                    enabled = name.trim().isNotEmpty() && name.trim() != profile?.name,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Monarch is earned — a name is chosen.",
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.InkMuted,
            )
        }

        Spacer(Modifier.height(14.dp))

        // Height is set ONCE here, not re-asked on every stat log; sex feeds
        // the Navy body-fat estimator in the stats log.
        SystemWindow(Modifier.fillMaxWidth()) {
            Text(
                "BODY PROFILE",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.SystemGreen,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    shape = MaterialTheme.shapes.small,
                    value = heightInput,
                    onValueChange = { heightInput = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                    label = { Text(if (bodyProfile.first == null) "Height (cm) — required for BMI" else "Height (cm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                MonarchButton(
                    label = "Save",
                    onClick = { viewModel.setHeight(heightInput) },
                    enabled = heightValid,
                )
            }
            Spacer(Modifier.height(10.dp))
            InkSegmented(
                options = Sex.entries.map { it to it.name },
                selected = bodyProfile.second,
                onPick = { viewModel.setSex(it) },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Height powers BMI, FFMI and step estimates. Sex picks the body-fat formula.",
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.InkMuted,
            )
        }

        SystemWindow(Modifier.fillMaxWidth()) {
            Text(
                "TRAINING MODE",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.SystemGreen,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(10.dp))
            InkSegmented(
                options = TrainingMode.entries.map { it to it.name },
                selected = profile?.trainingMode ?: TrainingMode.STRENGTH,
                onPick = { viewModel.setMode(it) },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (profile?.trainingMode) {
                    TrainingMode.STRENGTH -> "Clear all sets → load rises, reps reset."
                    else -> "Double progression: reps climb, then load."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.InkMuted,
            )
        }

        Spacer(Modifier.height(14.dp))

        // Appearance: the ink treatment is the app's look, so this exists to
        // leave it rather than to opt in. Off restores the original cut-corner
        // geometry - plain rules, even rails, true arcs - not a broken version
        // of the brush.
        SystemWindow {
            Text(
                "APPEARANCE",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = ChakraPetch,
                color = MonarchColors.SystemGreen,
                letterSpacing = MonarchTracking.SectionHeader,
            )
            Spacer(Modifier.height(10.dp))
            val inkOn = profile?.inkStyle ?: true
            InkSegmented(
                options = listOf(true to "INK", false to "CLEAN"),
                selected = inkOn,
                onPick = { viewModel.setInkStyle(it) },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (inkOn) {
                    "Hand-drawn edges, paper grain and brushed rules."
                } else {
                    "Straight edges and even rules, as the app first shipped."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MonarchColors.InkMuted,
            )
        }

        Spacer(Modifier.height(14.dp))

        SystemWindow(Modifier.fillMaxWidth()) {
            Text(
                "SAMSUNG HEALTH",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.SystemGreen,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Read weight, body fat and steps from Health Connect. Read-only.",
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
            )
            Spacer(Modifier.height(10.dp))
            if (sync.syncing) {
                InkSpinner()
            } else {
                MonarchButton(
                    label = "Connect & Sync",
                    onClick = {
                        if (sync.available) {
                            permissionLauncher.launch(HEALTH_PERMISSIONS)
                        } else {
                            viewModel.syncFromHealth()
                        }
                    },
                )
            }
            sync.result?.let { snapshot ->
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        append("\u23F1 ${snapshot.stepsToday} steps today")
                        // Samsung Health batches its pushes: without the "as of"
                        // the count just looks wrong against the phone's tally.
                        snapshot.stepsAsOfMs?.let { append(" (as of ${formatDate(it, "HH:mm")})") }
                        snapshot.weightKg?.let { append("  ·  $it kg") }
                        snapshot.bodyFatPct?.let { append("  ·  $it% bf") }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.SystemGreen,
                )
            }
            if (healthDays.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${healthDays.size} days synced · ${String.format(numberLocale(), "%,d", healthDays.sumOf { it.steps })} steps · ${"%.0f".format(healthDays.sumOf { it.distanceKm })} km",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.SystemGreen,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (sync.historySyncing) {
                InkSpinner()
            } else {
                MonarchButton(
                    label = "Sync Activity History",
                    onClick = { viewModel.syncActivityHistory() },
                )
            }
            sync.historyWritten?.let { written ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "$written days of activity history written.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MonarchColors.InkMuted,
                )
            }
            sync.coverage?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MonarchColors.InkMuted)
            }
            sync.message?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MonarchColors.InkMuted)
            }
        }

        Spacer(Modifier.height(14.dp))

        SystemWindow(Modifier.fillMaxWidth()) {
            Text(
                "DATA",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.SystemGreen,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Full JSON archive: presets, routine, sessions, every set, readings, titles.",
                style = MaterialTheme.typography.bodySmall,
                color = MonarchColors.InkMuted,
            )
            Spacer(Modifier.height(10.dp))
            if (exporting) {
                InkSpinner()
            } else {
                MonarchButton(
                    label = "Export Archive",
                    onClick = {
                        viewModel.exportJson { json ->
                            scope.launch { shareExport(context, "Export Monarch data", "monarch_export.json", json) }
                        }
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            if (importUi.importing) {
                InkSpinner()
            } else {
                MonarchButton(
                    label = "Import Archive",
                    onClick = { confirmImport = true },
                    enabled = !exporting,
                )
            }
            importUi.summary?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MonarchColors.InkMuted)
            }
            importUi.problems?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MonarchColors.InkMuted)
            }
        }

        Spacer(Modifier.height(14.dp))

        SystemWindow(Modifier.fillMaxWidth()) {
            Text(
                "DIAGNOSTICS",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = ChakraPetch,
                color = MonarchColors.SystemGreen,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(6.dp))
            if (crashCount == 0) {
                Text(
                    "No crashes recorded.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MonarchColors.InkMuted,
                )
            } else {
                Text(
                    "$crashCount crash record${if (crashCount == 1) "" else "s"} · latest $latestCrash",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = ChakraPetch,
                    color = MonarchColors.SystemGreen,
                )
                Spacer(Modifier.height(10.dp))
                MonarchButton(
                    label = "Share Crash Log",
                    onClick = {
                        scope.launch {
                            val text = withContext(Dispatchers.IO) {
                                CrashJournal.recent().joinToString("\n\n")
                            }
                            // Same file route as the export: twenty stack traces
                            // is not 0.9 MB, but there is no reason for two
                            // sharing paths with different failure modes.
                            shareExport(context, "Share Monarch crash log", "monarch_crash_log.txt", text)
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                MonarchButton(
                    label = "Clear Crash Log",
                    onClick = {
                        // Deleting and re-counting are both disk work: do them
                        // in the background like the initial read, or the tap
                        // blocks the frame it was made on.
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                CrashJournal.clear()
                                CrashJournal.crashCount() to CrashJournal.latestTimestamp()
                            }.let { (count, latest) ->
                                crashCount = count
                                latestCrash = latest?.substringAfter("—")?.trim().orEmpty()
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Monarch v1.0  ·  all data on this device only",
            style = MaterialTheme.typography.labelSmall,
            color = MonarchColors.InkMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Shares the export as a FILE, not as an intent extra.
 *
 * Measured: five years of training exports ~0.9 MB of JSON, and binder caps a
 * transaction near 1 MB — `EXTRA_TEXT` would throw TransactionTooLargeException
 * on the one action whose whole purpose is getting a hunter's data out. Staged
 * in the cache directory the FileProvider exposes, so the receiving app reads it
 * through a content:// URI instead.
 */
private suspend fun shareExport(context: Context, title: String, fileName: String, text: String) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // One name, overwritten: the cache is not an archive, and a stale
        // export left behind is a copy of everything the hunter has done.
        val file = File(dir, fileName)
        file.writeText(text)
        FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_TITLE, fileName)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

/**
 * The reader's locale, read through Compose's own locale state rather than
 * `Locale.getDefault()`, so formatted numbers recompose when the device locale
 * changes instead of keeping the value captured at first composition.
 */
@Composable
private fun numberLocale(): Locale = ComposeLocale.current.platformLocale
