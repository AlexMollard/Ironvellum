package com.ironvellum.app

import android.app.Application
import android.os.StrictMode
import com.ironvellum.app.data.CircleBonus
import com.ironvellum.app.data.CirclePayoutStore
import com.ironvellum.app.data.DbSnapshot
import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.HealthSync
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.data.Notifications
import com.ironvellum.app.data.Reminders
import com.ironvellum.app.ui.theme.InkStyle
import com.ironvellum.app.data.HealthSyncWorker
import com.ironvellum.app.data.cloud.CloudSyncWorker
import com.ironvellum.app.data.cloud.InboxWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.ironvellum.app.data.CrashJournal
import com.ironvellum.app.data.PrefsHighestBandStore


class IronvellumApp : Application() {

    val database: IronvellumDatabase by lazy { IronvellumDatabase.create(this) }
    val healthSync: HealthSync by lazy { HealthSync(this) }
    val repository: Repository by lazy { Repository(database, healthSync, PrefsHighestBandStore(this)) }

    /**
     * Cloud objects are app-scoped so the auth session is shared: two clients
     * would mean two sessions and a sign-in that only half the app can see.
     */
    val accountRepository: AccountRepository by lazy { AccountRepository() }
    val cloudSync: CloudSync by lazy { CloudSync(repository, accountRepository) }

    /** Every circle read goes through this, so the weekly bonus is settled wherever the circle is read. */
    val circleBonus: CircleBonus by lazy {
        CircleBonus(cloudSync, accountRepository, repository, CirclePayoutStore.from(this))
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
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
        // Mirror the stored display preference into the holder the ink
        // primitives read. Collected for the process lifetime so a flip in
        // Settings redraws every surface immediately.
        appScope.launch {
            repository.observeInkStyle().collect { InkStyle.enabled = it }
        }
        appScope.launch {
            repository.ensureSeeded()
            // Signed in, the cloud name is the profile name. Started once the
            // profile row exists, so the first account load cannot be dropped.
            appScope.launch {
                accountRepository.account.collect { acct ->
                    if (acct != null) {
                        runCatching { repository.adoptCloudName(acct.displayName, acct.profileLoaded) }
                    }
                }
            }
            runCatching { repository.syncHealthHistory() }
            // Titles used to be awarded only at the moment a workout finished,
            // so anything satisfied by imported health data stayed locked.
            runCatching { repository.reconcileTitles() }
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
        HealthSyncWorker.schedule(this)
        // Moves an upgraded install off the old periodic job, and books a run
        // for one whose queue was lost; a queued run is kept as it is.
        Reminders.ensureScheduled(this)
        CloudSyncWorker.schedule(this)
        // No backend at all is known now, without waiting for any restore.
        if (Cloud.config.value == null) InboxWorker.cancel(this)
    }
}
