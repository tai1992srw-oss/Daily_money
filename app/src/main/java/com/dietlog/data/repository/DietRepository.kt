package com.dietlog.data.repository

import com.dietlog.data.diet.ActivityData
import com.dietlog.data.diet.DAY_START_HOUR
import com.dietlog.data.diet.DaySummary
import com.dietlog.data.diet.DietDay
import com.dietlog.data.diet.DietLogApi
import com.dietlog.data.diet.DietSettings
import com.dietlog.data.diet.DietSettingsStore
import com.dietlog.data.diet.HealthConnectManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

data class DietSyncResult(
    val day: DietDay,
    val activity: ActivityData?,
    val activityUploadError: Boolean
)

@Singleton
class DietRepository @Inject constructor(
    private val api: DietLogApi,
    private val settingsStore: DietSettingsStore,
    val healthConnect: HealthConnectManager
) {
    val settings: Flow<DietSettings> = settingsStore.settings

    suspend fun saveSettings(settings: DietSettings) = settingsStore.save(settings)

    /** 午前5時境界での「今日」。 */
    fun logicalToday(): LocalDate {
        val now = LocalDateTime.now()
        return if (now.hour < DAY_START_HOUR) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }

    /**
     * 1回の同期で行うこと:
     * 1. Health Connect から今日の活動データを読む（権限があれば）
     * 2. 読めた場合はスプレッドシートへ書き込む（失敗しても表示は続行）
     * 3. スプレッドシートから今日の食事ログ・アドバイスを取得する
     */
    suspend fun sync(): DietSyncResult {
        val settings = settingsStore.settings.first()

        val activity: ActivityData? = runCatching {
            if (healthConnect.isAvailable() && healthConnect.hasAllPermissions()) {
                healthConnect.readTodayActivity()
            } else {
                null
            }
        }.getOrNull()

        var uploadFailed = false
        if (activity != null) {
            runCatching {
                api.postActivity(settings.apiUrl, settings.token, activity)
            }.onFailure { uploadFailed = true }
        }

        val day = api.fetchDay(settings.apiUrl, settings.token)
        return DietSyncResult(
            day = day,
            // ローカルの Health Connect の値を優先し、なければシート側の値
            activity = activity ?: day.sheetActivity,
            activityUploadError = uploadFailed
        )
    }

    /** 指定日の詳細（カレンダーの日別表示用）。 */
    suspend fun fetchDay(date: String): DietDay {
        val settings = settingsStore.settings.first()
        return api.fetchDay(settings.apiUrl, settings.token, date)
    }

    /** 指定月の日別サマリー（カレンダー表示用）。 */
    suspend fun fetchMonth(month: YearMonth): List<DaySummary> {
        val settings = settingsStore.settings.first()
        val from = month.atDay(1).toString()
        val to = month.atEndOfMonth().toString()
        return api.fetchRange(settings.apiUrl, settings.token, from, to)
    }
}
