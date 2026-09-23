package com.ironvellum.app

import android.app.Application
import android.os.StrictMode
import com.ironvellum.app.data.DbSnapshot
import com.ironvellum.app.data.cloud.AccountRepository
import com.ironvellum.app.data.cloud.Cloud
import com.ironvellum.app.data.cloud.CloudSync
import com.ironvellum.app.data.HealthSync
import com.ironvellum.app.data.IronvellumDatabase
import com.ironvellum.app.data.Repository
import com.ironvellum.app.ui.theme.InkStyle
import com.ironvellum.app.data.HealthSyncWorker
import com.ironvellum.app.data.cloud.CloudSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.ironvellum.app.data.CrashJournal


class IronvellumApp : Application() {

    val database: IronvellumDatabase by lazy { IronvellumDatabase.create(this) }
    val healthSync: HealthSync by lazy { HealthSync(this) }
    val repository: Repository by lazy { Repository(database, healthSync) }

    /**
     * Cloud objects are app-scoped so the auth session is shared: two clients
     * would mean two sessions and a sign-in that only half the app can see.
     */
    val accountRepository: AccountRepository by lazy { AccountRepository() }
    val cloudSync: CloudSync by lazy { CloudSync(repository, accountRepository) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
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
            runCatching { repository.syncHealthHistory() }
            // Titles used to be awarded only at the moment a workout finished,
            // so anything satisfied by imported health data stayed locked.
            runCatching { repository.reconcileTitles() }
            // Restore a stored sign-in before any screen asks who we are,
            // otherwise the social surfaces flash "signed out" on every launch.
            runCatching { accountRepository.restore() }
        }
        HealthSyncWorker.schedule(this)
        CloudSyncWorker.schedule(this)
    }
}
