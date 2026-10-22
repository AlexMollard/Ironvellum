package com.ironvellum.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug builds only. Puts test samples into Health Connect from adb without restarting the app, which
 * `am instrument` would (it kills the target process, and the point is to watch a live process resume):
 *
 *   am broadcast -n com.ironvellum.app/com.ironvellum.app.debug.HealthSeedReceiver -a seed --ef weightKg 80.3
 *   am broadcast -n com.ironvellum.app/com.ironvellum.app.debug.HealthSeedReceiver -a seed --ei hrDays 3 --ei bpm 55
 *
 * weightKg writes one weigh-in for now (or weightDaysAgo days back, a day the stat history does not cover yet). hrDays writes, for each of the last N days before today, 18 samples
 * between 02:00 and 04:50 (six 30-minute windows of three) around bpm. The emulator needs the write grants:
 * pm grant com.ironvellum.app android.permission.health.WRITE_WEIGHT (and WRITE_HEART_RATE).
 */
class HealthSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = HealthConnectClient.getOrCreate(context)
                val zone = ZoneId.systemDefault()
                val records = mutableListOf<androidx.health.connect.client.records.Record>()
                if (intent.hasExtra("weightKg")) {
                    val now = Instant.now().minusSeconds(intent.getIntExtra("weightDaysAgo", 0) * 86400L)
                    records += WeightRecord(
                        time = now,
                        zoneOffset = zone.rules.getOffset(now),
                        weight = Mass.kilograms(intent.getFloatExtra("weightKg", 80f).toDouble()),
                        metadata = Metadata.manualEntry(),
                    )
                }
                val hrDays = intent.getIntExtra("hrDays", 0)
                val bpm = intent.getIntExtra("bpm", 55)
                for (back in 1..hrDays) {
                    val day = LocalDate.now(zone).minusDays(back.toLong())
                    val start = day.atTime(2, 0).atZone(zone).toInstant()
                    val samples = (0 until 18).map { i ->
                        HeartRateRecord.Sample(start.plusSeconds(i * 600L), (bpm + i % 3).toLong())
                    }
                    records += HeartRateRecord(
                        startTime = start,
                        startZoneOffset = zone.rules.getOffset(start),
                        endTime = start.plusSeconds(18 * 600L),
                        endZoneOffset = zone.rules.getOffset(start),
                        samples = samples,
                        metadata = Metadata.manualEntry(),
                    )
                }
                if (records.isNotEmpty()) client.insertRecords(records)
                if (intent.getBooleanExtra("read", false)) {
                    val read = com.ironvellum.app.data.HealthSync(context).readDailyHistory(intent.getIntExtra("days", 5))
                    Log.i("HealthSeed", "read problems=${read.problems.map { it.toString() }} restingHr=${read.days.map { it.date to it.restingHr }}")
                }
                Log.i("HealthSeed", "inserted ${records.size} records")
            } catch (t: Throwable) {
                Log.e("HealthSeed", "seed failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
