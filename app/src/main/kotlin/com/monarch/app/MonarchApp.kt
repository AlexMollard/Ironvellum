package com.monarch.app

import android.app.Application
import com.monarch.app.data.DbSnapshot
import com.monarch.app.data.cloud.AccountRepository
import com.monarch.app.data.cloud.CloudSync
import com.monarch.app.data.HealthSync
import com.monarch.app.data.MonarchDatabase
import com.monarch.app.data.Repository
import com.monarch.app.data.HealthSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.monarch.app.data.CrashJournal


class MonarchApp : Application() {

    val database: MonarchDatabase by lazy { MonarchDatabase.create(this) }
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
    }
}
