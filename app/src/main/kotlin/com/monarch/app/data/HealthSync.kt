package com.monarch.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.monarch.app.domain.HealthDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

data class HealthSnapshot(
    val weightKg: Double?,
    val bodyFatPct: Double?,
    val stepsToday: Long,
    /**
     * End of the newest step record. Samsung Health pushes to Health Connect in
     * batches, so the total can trail the phone's own tally by an hour or more —
     * showing it without this timestamp reads as a wrong step count.
     */
    val stepsAsOfMs: Long? = null,
)

/**
 * Read-only bridge into Health Connect — where Samsung Health already syncs
 * weight, body composition and steps. Monarch only reads; nothing is written.
 */
class HealthSync(private val context: Context) {

    /** Distinguishes "not on this device" from "provider needs updating". */
    enum class Status { READY, UPDATE_REQUIRED, UNSUPPORTED }

    fun status(): Status = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Status.READY
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.UPDATE_REQUIRED
        else -> Status.UNSUPPORTED
    }

    fun available(): Boolean = status() == Status.READY

    /**
     * Which of our read permissions Health Connect has actually granted. A
     * missing grant is the common cause of an empty read and must not be
     * reported as "Health Connect unavailable".
     */
    suspend fun grantedPermissions(): Set<String> =
        runCatching {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())

    suspend fun readSnapshot(): HealthSnapshot? {
        if (!available()) return null
        val client = HealthConnectClient.getOrCreate(context)
        val now = Instant.now()
        // Body readings are sparse — people weigh in weekly at best, so a
        // 30-day window silently found nothing. Look back a year.
        val start = now.minus(Duration.ofDays(365))

        val weight = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, now),
                ascendingOrder = false,
                pageSize = 1,
            ),
        ).records.firstOrNull()?.weight?.inKilograms

        val bodyFat = client.readRecords(
            ReadRecordsRequest(
                recordType = BodyFatRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, now),
                ascendingOrder = false,
                pageSize = 1,
            ),
        ).records.firstOrNull()?.percentage?.value

        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val steps = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
            ),
        )[StepsRecord.COUNT_TOTAL] ?: 0L

        val stepsAsOf = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                    ascendingOrder = false,
                    pageSize = 1,
                ),
            ).records.firstOrNull()?.endTime?.toEpochMilli()
        }.getOrNull()

        return HealthSnapshot(weight, bodyFat, steps, stepsAsOf)
    }

    /** One body reading Health Connect holds for a given day. */
    data class BodyReading(
        val date: LocalDate,
        val takenAtMs: Long,
        val weightKg: Double,
        val bodyFatPct: Double?,
    )

    /**
     * Days that actually carry data, the body readings found in the same
     * window, per-metric coverage (how many days each metric filled), and why
     * any metric was unavailable. Steps arriving while distance stays empty is
     * normal — Samsung Health shares each type separately.
     */
    data class HistoryRead(
        val days: List<HealthDay> = emptyList(),
        val bodyReadings: List<BodyReading> = emptyList(),
        val coverage: Map<String, Int> = emptyMap(),
        val problems: List<String> = emptyList(),
        /** Apps that wrote the step records, and when the newest one ends. */
        val stepSources: List<String> = emptyList(),
        val newestStepAtMs: Long? = null,
    )

    suspend fun readDailyHistory(days: Int): HistoryRead {
        if (!available()) return HistoryRead(problems = listOf("Health Connect unavailable"))
        // Nothing granted at all: every aggregate below would be rejected by the
        // platform, so the daily worker was throwing seven SecurityExceptions
        // per run against a service that can never answer it. A PARTIAL grant
        // still proceeds — each metric is isolated, so the ones allowed read.
        if (grantedPermissions().isEmpty()) {
            return HistoryRead(problems = listOf("Health Connect permissions not granted"))
        }
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val end = LocalDate.now(zone)
        val start = end.minusDays((days - 1).coerceAtLeast(0).toLong())
        val from = start.atStartOfDay(zone).toInstant()
        val to = end.plusDays(1).atStartOfDay(zone).toInstant()
        // Period-grouped aggregation rejects Instant ranges outright:
        // "Either use TimeRangeFilter with LocalDateTime or
        // AggregateGroupByDurationRequest" — this silently zeroed every day.
        val fromLocal = start.atStartOfDay()
        val toLocal = end.plusDays(1).atStartOfDay()
        val problems = mutableListOf<String>()

        fun note(metric: String, error: Throwable) {
            problems += "$metric: ${error.javaClass.simpleName}${error.message?.let { " $it" } ?: ""}"
        }

        // One request per metric: a single denied permission (calories was the
        // culprit) fails the whole grouped call, which zeroed every day.
        suspend fun <T : Any> daily(
            label: String,
            metric: androidx.health.connect.client.aggregate.AggregateMetric<T>,
        ): Map<LocalDate, T> = runCatching {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(metric),
                    timeRangeFilter = TimeRangeFilter.between(fromLocal, toLocal),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            ).mapNotNull { bucket ->
                bucket.result[metric]?.let { bucket.startTime.toLocalDate() to it }
            }.toMap()
        }.onFailure { note(label, it) }.getOrDefault(emptyMap())

        val stepsByDate = daily("steps", StepsRecord.COUNT_TOTAL)
        val distanceByDate = daily("distance", DistanceRecord.DISTANCE_TOTAL)
        val kcalByDate = daily("active calories", ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)

        val sleepByDate: Map<LocalDate, Long> = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                ),
            ).records
                .groupBy { it.startTime.atZone(zone).toLocalDate() }
                .mapValues { (_, sessions) ->
                    sessions.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
                }
        }.onFailure { note("sleep", it) }.getOrDefault(emptyMap())

        val restingHrByDate: Map<LocalDate, Int> = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                ),
            ).records
                .groupBy { it.time.atZone(zone).toLocalDate() }
                .mapValues { (_, samples) -> samples.map { it.beatsPerMinute }.average().toInt() }
        }.onFailure { note("resting heart rate", it) }.getOrDefault(emptyMap())

        // Only days with real signal: fabricating empty rows made the sync
        // report "90 days" while every screen showed nothing.
        val result = (0 until days).mapNotNull { offset ->
            val date = start.plusDays(offset.toLong())
            val day = HealthDay(
                date = date,
                steps = (stepsByDate[date] ?: 0L).toInt(),
                distanceKm = distanceByDate[date]?.inMeters?.div(1000.0) ?: 0.0,
                activeKcal = kcalByDate[date]?.inKilocalories?.toInt() ?: 0,
                sleepMinutes = (sleepByDate[date] ?: 0L).toInt(),
                restingHr = restingHrByDate[date],
            )
            day.takeIf {
                it.steps > 0 || it.distanceKm > 0.0 || it.activeKcal > 0 ||
                    it.sleepMinutes > 0 || it.restingHr != null
            }
        }

        // Body composition lives in its own records, not the daily aggregates:
        // importing them here is what fills the stat history from Samsung Health.
        val weights = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                ),
            ).records
        }.onFailure { note("weight", it) }.getOrDefault(emptyList())

        val bodyFatByDate = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = BodyFatRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                ),
            ).records
                .groupBy { it.time.atZone(zone).toLocalDate() }
                .mapValues { (_, samples) -> samples.last().percentage.value }
        }.onFailure { note("body fat", it) }.getOrDefault(emptyMap())

        // One reading per day (the last of that day) so repeat weigh-ins do not
        // spam the stat history.
        val bodyReadings = weights
            .groupBy { it.time.atZone(zone).toLocalDate() }
            .map { (date, samples) ->
                val latest = samples.maxBy { it.time }
                BodyReading(
                    date = date,
                    takenAtMs = latest.time.toEpochMilli(),
                    weightKg = latest.weight.inKilograms,
                    bodyFatPct = bodyFatByDate[date],
                )
            }
            .sortedBy { it.date }

        val coverage = mapOf(
            "steps" to stepsByDate.count { it.value > 0L },
            "distance" to distanceByDate.size,
            "calories" to kcalByDate.size,
            "sleep" to sleepByDate.size,
            "heart rate" to restingHrByDate.size,
            "weight" to bodyReadings.size,
        )

        // Provenance: a step count that trails the watch/phone tally means
        // Samsung Health has not pushed its latest batch yet, which is invisible
        // from the total alone.
        val rawSteps = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        end.atStartOfDay(zone).toInstant(),
                        to,
                    ),
                ),
            ).records
        }.getOrDefault(emptyList())
        val stepSources = rawSteps.map { it.metadata.dataOrigin.packageName }.distinct().sorted()
        val newestStepAtMs = rawSteps.maxOfOrNull { it.endTime }?.toEpochMilli()

        if (result.isEmpty() && bodyReadings.isEmpty() && problems.isEmpty()) {
            problems += "Health Connect returned no activity for the last $days days"
        }
        return HistoryRead(result, bodyReadings, coverage, problems, stepSources, newestStepAtMs)
    }
}
