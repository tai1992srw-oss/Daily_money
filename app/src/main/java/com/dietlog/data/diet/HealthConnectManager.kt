package com.dietlog.data.diet

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Health Connect から今日の活動データ（歩数・消費カロリー・距離・睡眠・体重）を読み取る。
 * Pixel Watch のデータは Fitbit アプリ経由で Health Connect に同期される。
 * 「今日」は午前5時始まり（DAY_START_HOUR）で集計する。
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class)
    )

    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasAllPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun readTodayActivity(): ActivityData {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var dayStart = now.toLocalDate().atTime(DAY_START_HOUR, 0).atZone(zone)
        if (now.isBefore(dayStart)) dayStart = dayStart.minusDays(1)
        val start = dayStart.toInstant()
        val end = now.toInstant()

        val agg = client.aggregate(
            AggregateRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                    DistanceRecord.DISTANCE_TOTAL
                ),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )

        return ActivityData(
            steps = agg[StepsRecord.COUNT_TOTAL] ?: 0L,
            totalKcal = (agg[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                ?: 0.0).roundToInt(),
            activeKcal = (agg[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                ?: 0.0).roundToInt(),
            distanceKm = agg[DistanceRecord.DISTANCE_TOTAL]?.inKilometers?.let { round1(it) },
            sleepH = readSleepHours(start, end),
            weightKg = readLatestWeight(end)
        )
    }

    /** 直近90日で最新の体重。なければ null。 */
    private suspend fun readLatestWeight(now: Instant): Double? {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(now.minus(90, ChronoUnit.DAYS), now),
                ascendingOrder = false,
                pageSize = 1
            )
        )
        return response.records.firstOrNull()?.weight?.inKilograms?.let { round1(it) }
    }

    /** 昨夜の睡眠時間（前日18時〜現在に重なるセッションの合計、時間単位）。 */
    private suspend fun readSleepHours(dayStart: Instant, now: Instant): Double? {
        val from = dayStart.minus(11, ChronoUnit.HOURS) // 前日18時
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, now),
                pageSize = 50
            )
        )
        if (response.records.isEmpty()) return null
        val totalMinutes = response.records.sumOf {
            Duration.between(it.startTime, it.endTime).toMinutes()
        }
        return round1(totalMinutes / 60.0)
    }

    private fun round1(v: Double): Double = kotlin.math.round(v * 10) / 10.0
}
