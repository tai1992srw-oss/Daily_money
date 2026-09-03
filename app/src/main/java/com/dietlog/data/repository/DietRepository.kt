package com.dietlog.data.repository

import com.dietlog.data.diet.ActivityData
import com.dietlog.data.diet.DAY_START_HOUR
import com.dietlog.data.diet.DaySummary
import com.dietlog.data.diet.DietDay
import com.dietlog.data.diet.DietLogApi
import com.dietlog.data.diet.DietSettings
import com.dietlog.data.diet.DietSettingsStore
import com.dietlog.data.diet.HealthConnectManager
import com.dietlog.data.diet.PlaceMeal
import com.dietlog.data.diet.PlaceVisit
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

            // 前日の行を確定値で締める。今日の分しか書かないと、5時境界をまたいだ後の
            // 消費カロリーが前日最後の同期の値のまま凍結されるため
            runCatching {
                val yesterday = healthConnect.readYesterdayActivity()
                if (yesterday.totalKcal > 0 || yesterday.steps > 0) {
                    api.postActivity(
                        settings.apiUrl,
                        settings.token,
                        yesterday,
                        date = logicalToday().minusDays(1).toString()
                    )
                }
            }
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

    /** 今日を含む直近 [days] 日分の日別サマリー（体重グラフ用）。 */
    suspend fun fetchRecent(days: Int): List<DaySummary> {
        val settings = settingsStore.settings.first()
        val to = logicalToday()
        val from = to.minusDays((days - 1).toLong())
        return api.fetchRange(settings.apiUrl, settings.token, from.toString(), to.toString())
    }

    /** 指定月の日別サマリー（カレンダー表示用）。 */
    suspend fun fetchMonth(month: YearMonth): List<DaySummary> {
        val settings = settingsStore.settings.first()
        val from = month.atDay(1).toString()
        val to = month.atEndOfMonth().toString()
        return api.fetchRange(settings.apiUrl, settings.token, from, to)
    }

    /** 記録に店名が入っている食事から作った「行った店」の一覧（お店タブ用）。 */
    suspend fun fetchPlaces(): List<PlaceVisit> {
        val settings = settingsStore.settings.first()
        return api.fetchPlaces(settings.apiUrl, settings.token)
    }

    /** 指定した店で食べた記録の一覧（お店タブの詳細画面用）。 */
    suspend fun fetchPlaceMeals(name: String): List<PlaceMeal> {
        val settings = settingsStore.settings.first()
        return api.fetchPlaceMeals(settings.apiUrl, settings.token, name)
    }
}
