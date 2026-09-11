package com.monarch.app

import android.app.Application
import com.monarch.app.data.DbSnapshot
import com.monarch.app.data.HealthSync
import com.monarch.app.data.MonarchDatabase
import com.monarch.app.data.Repository
import com.monarch.app.data.HealthSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MonarchApp : Application() {

    val database: MonarchDatabase by lazy { MonarchDatabase.create(this) }
    val healthSync: HealthSync by lazy { HealthSync(this) }
    val repository: Repository by lazy { Repository(database, healthSync) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Before anything touches Room: a failed migration leaves the data on
        // disk but unreachable, so the byte copy has to happen first.
        DbSnapshot.capture(this)
        appScope.launch {
            repository.ensureSeeded()
            runCatching { repository.syncHealthHistory() }
            // Titles used to be awarded only at the moment a workout finished,
            // so anything satisfied by imported health data stayed locked.
            runCatching { repository.reconcileTitles() }
        }
        HealthSyncWorker.schedule(this)
    }
}
