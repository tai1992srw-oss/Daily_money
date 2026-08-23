package com.dailybudget.data.diet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * スプレッドシート「ダイエットログ」の GAS Web アプリと通信するクライアント。
 * GAS は 302 リダイレクト経由でレスポンスを返すため、リダイレクトを手動で追跡する。
 */
@Singleton
class DietLogApi @Inject constructor() {

    suspend fun fetchToday(apiUrl: String, token: String): DietDay = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(token, "UTF-8")
        val body = request("$apiUrl?token=$encoded&action=today", postBody = null)
        parseDay(body)
    }

    suspend fun postActivity(
        apiUrl: String,
        token: String,
        activity: ActivityData
    ): Unit = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("token", token)
            .put("action", "addActivity")
            .put("steps", activity.steps)
            .put("total_kcal", activity.totalKcal)
            .put("active_kcal", activity.activeKcal)
        val body = request(apiUrl, postBody = payload.toString())
        val json = JSONObject(body)
        if (!json.optBoolean("ok")) {
            throw IOException("活動データの書き込みに失敗: ${json.optString("error")}")
        }
    }

    private fun parseDay(body: String): DietDay {
        val json = JSONObject(body)
        if (!json.optBoolean("ok")) {
            throw IOException("API エラー: ${json.optString("error", "unknown")}")
        }

        val meals = mutableListOf<MealRecord>()
        val mealsArray = json.optJSONArray("meals")
        if (mealsArray != null) {
            for (i in 0 until mealsArray.length()) {
                val m = mealsArray.getJSONObject(i)
                meals.add(
                    MealRecord(
                        time = m.optString("time"),
                        meal = m.optString("meal"),
                        description = m.optString("description"),
                        kcal = m.optInt("kcal"),
                        proteinG = m.optDouble("protein_g", 0.0),
                        fatG = m.optDouble("fat_g", 0.0),
                        carbsG = m.optDouble("carbs_g", 0.0),
                        note = m.optString("note")
                    )
                )
            }
        }

        val totalsJson = json.optJSONObject("totals")
        val totals = if (totalsJson != null) {
            MealTotals(
                kcal = totalsJson.optInt("kcal"),
                proteinG = totalsJson.optDouble("protein_g", 0.0),
                fatG = totalsJson.optDouble("fat_g", 0.0),
                carbsG = totalsJson.optDouble("carbs_g", 0.0)
            )
        } else {
            MealTotals()
        }

        val activityJson = json.optJSONObject("activity")
        val activity = if (activityJson != null) {
            ActivityData(
                steps = activityJson.optLong("steps"),
                totalKcal = activityJson.optInt("total_kcal"),
                activeKcal = activityJson.optInt("active_kcal")
            )
        } else {
            null
        }

        return DietDay(
            date = json.optString("date"),
            meals = meals,
            totals = totals,
            sheetActivity = activity
        )
    }

    /**
     * postBody が null なら GET、あれば JSON を POST する。
     * 3xx はリダイレクト先へ GET で追跡する（GAS の応答パターン）。
     */
    private fun request(url: String, postBody: String?): String {
        var currentUrl = url
        var isFirst = true
        repeat(MAX_REDIRECTS) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                if (isFirst && postBody != null) {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(postBody.toByteArray(Charsets.UTF_8)) }
                } else {
                    conn.requestMethod = "GET"
                }

                val code = conn.responseCode
                if (code in 300..399) {
                    currentUrl = conn.getHeaderField("Location")
                        ?: throw IOException("リダイレクト先が不明です (HTTP $code)")
                    isFirst = false
                    return@repeat
                }
                if (code >= 400) {
                    throw IOException("HTTP $code")
                }
                return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("リダイレクトが多すぎます")
    }

    companion object {
        private const val MAX_REDIRECTS = 4
    }
}
