package com.ironvellum.app.data

import android.content.Context
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

/** What a getChanges walk found, reduced to what the resume sync needs. */
data class ChangeBatch(
    val count: Int,
    /** Oldest day any changed record touches, or null when none could be dated. */
    val earliest: LocalDate?,
    /** A deleted record carries only an id, so its day is unknown. */
    val hasDeletion: Boolean,
    val nextToken: String?,
    val expired: Boolean,
)

/** Why a run did what it did; logged as data (its toString), never shown on screen. */
enum class ResumeWhy { STARTUP, NO_TOKEN, TOKEN_EXPIRED, CHANGES, NO_CHANGES, NOT_READY }

data class ResumeRun(val why: ResumeWhy, val changes: Int = 0, val days: Int = 0)

/** A stored changes token and the record types it was minted for. */
data class StoredToken(val types: String, val token: String)

/**
 * Syncs Health Connect when the app returns to the foreground. Cheap by design:
 * - at most one run per [THROTTLE_MS], whoever asks (the cold-start sync counts as a run, so the first
 *   foreground after launch does not read twice);
 * - skipped when Health Connect is unavailable or nothing is granted;
 * - Health Connect's changes feed says how far back to look, and the read itself is the same
 *   [importDays] path the start-up and daily syncs use (aggregates dedupe across apps, raw records would
 *   not), so upsert and dedupe rules are identical;
 * - no usable token (none stored, expired, rejected, or minted for a different set of granted types, as when
 *   heart rate has just been allowed) means a [FALLBACK_DAYS] read and a fresh token.
 * The token is minted BEFORE the read it covers, so a change that lands mid-read is seen next time, and it is
 * stored only after the read returned.
 */
class HealthResumeSync(
    private val source: Source,
    private val store: TokenStore,
    private val importDays: suspend (days: Int) -> Unit,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val log: (ResumeRun) -> Unit = {},
) {
    interface Source {
        /** Health Connect is on this device and at least one read is granted. */
        suspend fun ready(): Boolean

        /** The currently granted record types, as a stable key. */
        suspend fun readTypes(): String
        suspend fun newToken(): String

        /** Everything since [token]; throws when the token is rejected. */
        suspend fun changes(token: String): ChangeBatch
    }

    interface TokenStore {
        fun load(): StoredToken?
        fun save(value: StoredToken)
        fun clear()
    }

    private val mutex = Mutex()

    // A process start is a run: the start-up sync follows straight away and the first ON_START arrives with it.
    @Volatile private var lastRunMs: Long = nowMs()

    private fun throttled(): Boolean = nowMs() - lastRunMs < THROTTLE_MS

    /** The cold-start sync: the full window, then a token for the future. */
    suspend fun startup(days: Int) = mutex.withLock {
        lastRunMs = nowMs()
        full(days, ResumeWhy.STARTUP)
    }

    suspend fun onResume() {
        if (throttled()) return
        mutex.withLock {
            if (throttled()) return
            lastRunMs = nowMs()
            if (!source.ready()) {
                log(ResumeRun(ResumeWhy.NOT_READY))
                return
            }
            val types = source.readTypes()
            val saved = store.load()
            if (saved == null || saved.types != types) {
                full(FALLBACK_DAYS, ResumeWhy.NO_TOKEN)
                return
            }
            val batch = runCatching { source.changes(saved.token) }.getOrNull()
            if (batch == null || batch.expired) {
                full(FALLBACK_DAYS, ResumeWhy.TOKEN_EXPIRED)
                return
            }
            if (batch.count == 0) {
                log(ResumeRun(ResumeWhy.NO_CHANGES))
            } else {
                val days = windowFor(batch)
                log(ResumeRun(ResumeWhy.CHANGES, batch.count, days))
                importDays(days)
            }
            batch.nextToken?.let { store.save(StoredToken(types, it)) }
        }
    }

    private fun windowFor(batch: ChangeBatch): Int {
        val span = batch.earliest?.let { ChronoUnit.DAYS.between(it, today()).toInt() + 1 } ?: FALLBACK_DAYS
        return (if (batch.hasDeletion) maxOf(span, FALLBACK_DAYS) else span).coerceIn(1, MAX_DAYS)
    }

    private suspend fun full(days: Int, why: ResumeWhy) {
        val token = runCatching {
            if (source.ready()) source.readTypes() to source.newToken() else null
        }.getOrNull()
        log(ResumeRun(why, days = days))
        importDays(days)
        if (token != null) store.save(StoredToken(token.first, token.second)) else store.clear()
    }

    companion object {
        const val THROTTLE_MS = 5 * 60_000L
        const val FALLBACK_DAYS = 14
        const val MAX_DAYS = 90
    }
}

/** The real Health Connect behind [HealthResumeSync.Source]. */
class HealthChangeSource(
    private val context: Context,
    private val health: HealthSync,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : HealthResumeSync.Source {

    private suspend fun grantedTypes(): List<KClass<out Record>> {
        val granted = health.grantedPermissions()
        return TYPES.filter { HealthPermission.getReadPermission(it) in granted }
    }

    override suspend fun ready(): Boolean = health.available() && grantedTypes().isNotEmpty()

    override suspend fun readTypes(): String = grantedTypes().mapNotNull { it.simpleName }.sorted().joinToString(",")

    override suspend fun newToken(): String =
        HealthConnectClient.getOrCreate(context).getChangesToken(ChangesTokenRequest(grantedTypes().toSet()))

    override suspend fun changes(token: String): ChangeBatch {
        val client = HealthConnectClient.getOrCreate(context)
        var next = token
        var count = 0
        var earliest: LocalDate? = null
        var deletion = false
        var pages = 0
        while (true) {
            val response = client.getChanges(next)
            if (response.changesTokenExpired) return ChangeBatch(0, null, false, null, expired = true)
            for (change in response.changes) {
                count++
                when (change) {
                    is UpsertionChange -> dayOf(change.record)?.let { day ->
                        earliest = earliest?.let { minOf(it, day) } ?: day
                    }
                    is DeletionChange -> deletion = true
                }
            }
            next = response.nextChangesToken
            // shortcut: stop after MAX_PAGES; the rest arrives on the next resume from the saved token
            if (!response.hasMore || ++pages >= MAX_PAGES) break
        }
        return ChangeBatch(count, earliest, deletion, next, expired = false)
    }

    private fun dayOf(record: Record): LocalDate? = when (record) {
        // IntervalRecord and InstantaneousRecord are internal to the library, so the types are named.
        is StepsRecord -> record.startTime
        is DistanceRecord -> record.startTime
        is ActiveCaloriesBurnedRecord -> record.startTime
        is SleepSessionRecord -> record.startTime
        is HeartRateRecord -> record.startTime
        is WeightRecord -> record.time
        is BodyFatRecord -> record.time
        is RestingHeartRateRecord -> record.time
        else -> null
    }?.atZone(zone())?.toLocalDate()

    private companion object {
        const val MAX_PAGES = 20

        /** Every record type [HealthSync.readDailyHistory] reads. */
        val TYPES: List<KClass<out Record>> = listOf(
            WeightRecord::class,
            BodyFatRecord::class,
            StepsRecord::class,
            DistanceRecord::class,
            ActiveCaloriesBurnedRecord::class,
            SleepSessionRecord::class,
            RestingHeartRateRecord::class,
            HeartRateRecord::class,
        )
    }
}

/** The changes token lives in SharedPreferences, like the write-back switch. */
class PrefsTokenStore(context: Context) : HealthResumeSync.TokenStore {
    private val prefs = context.getSharedPreferences("health_resume", Context.MODE_PRIVATE)

    override fun load(): StoredToken? {
        val types = prefs.getString(KEY_TYPES, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return StoredToken(types, token)
    }

    override fun save(value: StoredToken) = prefs.edit {
        putString(KEY_TYPES, value.types)
        putString(KEY_TOKEN, value.token)
    }

    override fun clear() = prefs.edit { clear() }

    private companion object {
        const val KEY_TYPES = "types"
        const val KEY_TOKEN = "token"
    }
}
