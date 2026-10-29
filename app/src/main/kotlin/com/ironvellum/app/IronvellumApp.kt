package com.ironvellum.app

import android.app.Application
import android.os.StrictMode
import com.ironvellum.app.data.CircleBonus
import com.ironvellum.app.data.CirclePayoutStore
import com.ironvellum.app.data.DbSnapshot
import com.ironvellum.app.data.Words
import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.HealthSync
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.Notifications
import com.ironvellum.app.data.Reminders
import com.ironvellum.app.data.WeeklyRecaps
import com.ironvellum.app.data.AutoBackup
import com.ironvellum.app.data.HealthSyncWorker
import com.ironvellum.app.data.HealthChangeSource
import com.ironvellum.app.data.HealthResumeSync
import com.ironvellum.app.data.PrefsTokenStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import android.util.Log
import com.ironvellum.app.data.cloud.CloudSyncWorker
import com.ironvellum.app.data.cloud.InboxWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.ironvellum.app.data.CrashJournal
import androidx.room.InvalidationTracker
import com.ironvellum.app.widget.TodayWidget
import com.ironvellum.app.data.PrefsHighestBandStore


class IronvellumApp : Application() {

    val appearanceStore by lazy { com.ironvellum.app.data.AppearanceStore(this) }

    val database: IronvellumDatabase by lazy { IronvellumDatabase.create(this) }
    val healthSync: HealthSync by lazy { HealthSync(this) }
    val healthWriteBack: com.ironvellum.app.data.HealthWriteBack by lazy { com.ironvellum.app.data.HealthWriteBack(this, healthSync) }
    val repository: Repository by lazy {
        Repository(
            database, healthSync, PrefsHighestBandStore(this), writeBack = healthWriteBack,
            loadKit = { kotlinx.coroutines.withContext(Dispatchers.IO) { com.ironvellum.app.data.ProgramAnswersStore.kit(this@IronvellumApp) } },
            adjustments = com.ironvellum.app.data.PrefsSessionAdjustments(getSharedPreferences("suggestion_dismissals", MODE_PRIVATE)),
            twinKept = { com.ironvellum.app.data.TrialPrefs(this).twinKept },
            photoStore = com.ironvellum.app.data.PhotoStore(this),
            archiveSettings = com.ironvellum.app.data.ArchiveSettings(this, permitted = { file ->
                // A restored switch is only a wish: it turns on once this device has granted what it needs.
                when (file) {
                    "reminders" -> Notifications.access(this, Notifications.CHANNEL_SUMMONS).canPost
                    "health_write" -> healthWriteBack.permitted()
                    else -> true
                }
            }) { restored, includeDeviceOnly ->
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if ("appearance" in restored) {
                        appearanceStore.reload()
                        com.ironvellum.app.data.Units.init(this@IronvellumApp)
                        com.ironvellum.app.data.EffortSetting.init(this@IronvellumApp)
                        com.ironvellum.app.data.LiftingExperienceSetting.init(this@IronvellumApp)
                        com.ironvellum.app.data.FatigueAdviceSetting.init(this@IronvellumApp)
                        Words.init(this@IronvellumApp, getDatabasePath(IronvellumDatabase.NAME).exists(), com.ironvellum.app.data.FirstRunPrefs.welcomeSeen(this@IronvellumApp))
                        Words.setThemed(this@IronvellumApp, Words.themed)
                    }
                    if (includeDeviceOnly && "onboarding" in restored) {
                        com.ironvellum.app.data.FirstRunPrefs.setSexUnset(this@IronvellumApp, getSharedPreferences("onboarding", MODE_PRIVATE).getBoolean("sex_unset", false))
                    }
                    if ("weekly_recap" in restored) com.ironvellum.app.data.ReviewDismissals.init(this@IronvellumApp)
                    if ("progress_photos" in restored) com.ironvellum.app.data.PhotoPrefs.init(this@IronvellumApp)
                    if ("program_answers" in restored) {
                        com.ironvellum.app.data.ProgramAnswersStore.get(this@IronvellumApp)
                        com.ironvellum.app.data.PlateStore.load(this@IronvellumApp)
                    }
                    if ("health_write" in restored) {
                        healthWriteBack.setEnabled(getSharedPreferences("health_write", MODE_PRIVATE).getBoolean("enabled", false))
                    }
                    if ("reminders" in restored) {
                        if (Reminders.enabled(this@IronvellumApp)) Reminders.enable(this@IronvellumApp) else Reminders.disable(this@IronvellumApp)
                    }
                    if ("inbox_notifier" in restored) {
                        if (!com.ironvellum.app.data.InboxNotifier.enabled(this@IronvellumApp)) com.ironvellum.app.data.InboxNotifier.cancel(this@IronvellumApp)
                        InboxWorker.sync(this@IronvellumApp, accountRepository.account.value != null && Cloud.config.value != null)
                    }
                }
            },
        )
    }

    /** Foreground sync: the start-up sync and every return to the app go through it, so they never double up. */
    val healthResume: HealthResumeSync by lazy {
        HealthResumeSync(
            source = HealthChangeSource(this, healthSync),
            store = PrefsTokenStore(this),
            importDays = { days -> repository.syncHealthHistory(days) },
            log = { Log.i("HealthResume", it.toString()) },
        )
    }

    /**
     * Cloud objects are app-scoped so the auth session is shared: two clients
     * would mean two sessions and a sign-in that only half the app can see.
     */
    val accountRepository: AccountRepository by lazy { AccountRepository() }
    val cloudSync: CloudSync by lazy { CloudSync(repository, accountRepository, appearanceStore) }

    /** Every circle read goes through this, so the weekly bonus is settled wherever the circle is read. */
    val circleBonus: CircleBonus by lazy {
        CircleBonus(cloudSync, accountRepository, repository, CirclePayoutStore.from(this))
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appearanceStore // Restore accents before any window or notification reads them.
        com.ironvellum.app.data.Units.init(this) // And the load unit, before any screen or notification prints a load.
        com.ironvellum.app.data.ReviewDismissals.init(this) // Which reviews were closed on Today, before it draws.
        com.ironvellum.app.data.PhotoPrefs.init(this) // Which weekday asks for pose photos, and whether Today's prompt was closed.
        com.ironvellum.app.data.EffortSetting.init(this) // And whether effort is asked for, before the trial screen reads it.
        com.ironvellum.app.data.LiftingExperienceSetting.init(this) // And how long they have lifted, before the fatigue estimate reads it.
        com.ironvellum.app.data.FatigueAdviceSetting.init(this) // And whether fatigue may advise, before Today reads it.
        // Which vocabulary to print, decided before the database file or any screen exists: a lifter who already has a database keeps
        // the themed names, a fresh install starts plain. Must run before DbSnapshot or Room can create the file.
        Words.init(
            this,
            databaseExists = getDatabasePath(com.ironvellum.app.data.IronvellumDatabase.NAME).exists(),
            welcomeSeen = com.ironvellum.app.data.FirstRunPrefs.welcomeSeen(this),
        )
        // Every channel must exist before anything posts on O+; the workers
        // and the trial service can all start in a cold process, so the
        // cold-start call is the reliable one.
        Notifications.ensureChannels(this)
        // Debug only, and log rather than crash: main-thread disk or network
        // work is how an app earns an ANR on a cold morning with a big
        // database, and nothing else in this project would notice it.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build(),
            )
        }
        // Journal a crash before anything else can fail: the handler must be
        // in place before the first work runs in this process. It wraps and
        // delegates to the system handler, so the crash dialog still appears
        // and the process still dies.
        runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            CrashJournal.installMeta(
                versionName = info.versionName ?: "unknown",
                versionCode = info.longVersionCode,
            )
            CrashJournal.install(this)
        }
        // Before anything touches Room: a failed migration leaves the data on
        // disk but unreachable, so the byte copy has to happen first.
        DbSnapshot.capture(this)
        // Load the stored backend override (Settings → CLOUD) before anything
        // restores a session or touches the cloud, so the first client is
        // built against the backend the lifter actually chose.
        Cloud.init(this)
        // The lifter's vest and belt, off the main thread: the load steppers read them from the store's flow.
        appScope.launch { runCatching { com.ironvellum.app.data.ProgramAnswersStore.get(this@IronvellumApp) } }
        appScope.launch {
            repository.ensureSeeded()
            // The home-screen widgets show the plan, the live trial (its sets ticked) and the finished ones: any write to them redraws.
            database.invalidationTracker.addObserver(object : InvalidationTracker.Observer("sessions", "set_logs", "presets", "preset_entries") {
                override fun onInvalidated(tables: Set<String>) = TodayWidget.refresh(this@IronvellumApp)
            })
            // Signed in, the cloud name is the profile name. Started once the
            // profile row exists, so the first account load cannot be dropped.
            appScope.launch {
                accountRepository.account.collect { acct ->
                    if (acct != null) {
                        runCatching { repository.adoptCloudName(acct.displayName, acct.profileLoaded) }
                    }
                }
            }
            runCatching { healthResume.startup(90) }
            // Titles used to be awarded only at the moment a workout finished,
            // so anything satisfied by imported health data stayed locked.
            // Prerequisites of claims made before skills could be passed: marked and paid once, then a no-op.
            runCatching { repository.reconcileSkillPasses() }
            runCatching { repository.reconcileTitles() }
            // The Veil's one-time catch-up for levels that never paid out. Guarded
            // by a stored version, so every launch after the first is a no-op.
            runCatching { repository.applyVeilGrant() }
            // Restore a stored sign-in before any screen asks who we are,
            // otherwise the social surfaces flash "signed out" on every launch.
            val restored = runCatching { accountRepository.restore() }.getOrNull()?.isSuccess == true
            // The inbox poll follows the account from here on. Not before the
            // restore: a worker that cold-started this process is waiting for
            // that account, and a null read too early would cancel it. A
            // failed restore (offline token refresh) proves nothing either, so
            // until an account is seen only a sign-in may change the schedule.
            appScope.launch {
                var settled = restored
                combine(accountRepository.account, Cloud.config) { acct, cfg -> acct != null && cfg != null }
                    .distinctUntilChanged()
                    .collect { live ->
                        if (live) settled = true
                        if (settled) InboxWorker.sync(this@IronvellumApp, live)
                    }
            }
        }
        // Built now, so its throttle clock starts with the process and the first ON_START rides on the start-up sync.
        healthResume
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) appScope.launch { runCatching { healthResume.onResume() } }
            },
        )
        HealthSyncWorker.schedule(this)
        // Moves an upgraded install off the old periodic job, and books a run
        // for one whose queue was lost; a queued run is kept as it is.
        Reminders.ensureScheduled(this)
        WeeklyRecaps.ensureScheduled(this)
        TodayWidget.ensureScheduled(this)
        CloudSyncWorker.schedule(this)
        AutoBackup.ensureScheduled(this)
        // No backend at all is known now, without waiting for any restore.
        if (Cloud.config.value == null) InboxWorker.cancel(this)
    }
}
