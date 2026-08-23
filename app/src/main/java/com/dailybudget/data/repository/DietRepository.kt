package com.dailybudget.data.repository

import com.dailybudget.data.diet.ActivityData
import com.dailybudget.data.diet.DietDay
import com.dailybudget.data.diet.DietLogApi
import com.dailybudget.data.diet.DietSettings
import com.dailybudget.data.diet.DietSettingsStore
import com.dailybudget.data.diet.HealthConnectManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

    /**
     * 1回の同期で行うこと:
     * 1. Health Connect から今日の活動データを読む（権限があれば）
     * 2. 読めた場合はスプレッドシートへ書き込む（失敗しても表示は続行）
     * 3. スプレッドシートから今日の食事ログを取得する
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

        val day = api.fetchToday(settings.apiUrl, settings.token)
        return DietSyncResult(
            day = day,
            // ローカルの Health Connect の値を優先し、なければシート側の値
            activity = activity ?: day.sheetActivity,
            activityUploadError = uploadFailed
        )
    }
}
