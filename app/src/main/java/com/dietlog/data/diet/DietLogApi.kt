package com.dietlog.data.diet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * スプレッドシート「ダイエットログ」の GAS Web アプリと通信するクライアント。
 * GAS は 302 リダイレクト経由でレスポンスを返すため、リダイレクトを手動で追跡する
 * （初回のみ POST、リダイレクト先へは GET。GAS側の処理は初回POSTで完了している）。
 */
@Singleton
class DietLogApi @Inject constructor() {

    suspend fun fetchDay(apiUrl: String, token: String, date: String? = null): DietDay =
        withContext(Dispatchers.IO) {
            var url = "$apiUrl?token=${encode(token)}&action=today"
            if (date != null) url += "&date=${encode(date)}"
            parseDay(request(url, postBody = null))
        }

    suspend fun fetchRange(
        apiUrl: String,
        token: String,
        from: String,
        to: String
    ): List<DaySummary> = withContext(Dispatchers.IO) {
        val url = "$apiUrl?token=${encode(token)}&action=range&from=${encode(from)}&to=${encode(to)}"
        val json = JSONObject(request(url, postBody = null))
        if (!json.optBoolean("ok")) {
            throw IOException("API エラー: ${json.optString("error", "unknown")}")
        }
        parseSummaries(json.getJSONArray("days"))
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
        activity.distanceKm?.let { payload.put("distance_km", it) }
        activity.sleepH?.let { payload.put("sleep_h", it) }
        activity.weightKg?.let { payload.put("weight_kg", it) }

        val json = JSONObject(request(apiUrl, postBody = payload.toString()))
        if (!json.optBoolean("ok")) {
            throw IOException("活動データの書き込みに失敗: ${json.optString("error")}")
        }
    }

    // ------------------------------------------------------------------ parse

    private fun parseDay(body: String): DietDay {
        val json = JSONObject(body)
        if (!json.optBoolean("ok")) {
            throw IOException("API エラー: ${json.optString("error", "unknown")}")
        }

        val meals = mutableListOf<MealRecord>()
        json.optJSONArray("meals")?.let { arr ->
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
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

        val advice = mutableListOf<AdviceEntry>()
        json.optJSONArray("advice")?.let { arr ->
            for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                advice.add(
                    AdviceEntry(
                        time = a.optString("time"),
                        type = a.optString("type"),
                        content = a.optString("content")
                    )
                )
            }
        }

        return DietDay(
            date = json.optString("date"),
            meals = meals,
            totals = totals,
            sheetActivity = json.optJSONObject("activity")?.let { parseActivity(it) },
            advice = advice
        )
    }

    private fun parseActivity(obj: JSONObject): ActivityData = ActivityData(
        steps = if (obj.isNull("steps")) 0L else obj.optLong("steps"),
        totalKcal = if (obj.isNull("total_kcal")) 0 else obj.optInt("total_kcal"),
        activeKcal = if (obj.isNull("active_kcal")) 0 else obj.optInt("active_kcal"),
        distanceKm = doubleOrNull(obj, "distance_km")?.takeIf { it in 0.0..1000.0 },
        sleepH = doubleOrNull(obj, "sleep_h")?.takeIf { it in 0.0..24.0 },
        weightKg = doubleOrNull(obj, "weight_kg")?.takeIf { it in 20.0..300.0 }
    )

    private fun parseSummaries(arr: JSONArray): List<DaySummary> {
        val result = mutableListOf<DaySummary>()
        for (i in 0 until arr.length()) {
            val d = arr.getJSONObject(i)
            result.add(
                DaySummary(
                    date = d.optString("date"),
                    intakeKcal = d.optInt("intake_kcal"),
                    burnedKcal = intOrNull(d, "burned_kcal"),
                    balanceKcal = intOrNull(d, "balance_kcal"),
                    steps = if (d.isNull("steps")) null else d.optLong("steps"),
                    weightKg = doubleOrNull(d, "weight_kg")?.takeIf { it in 20.0..300.0 },
                    sleepH = doubleOrNull(d, "sleep_h")?.takeIf { it in 0.0..24.0 },
                    meals = d.optInt("meals"),
                    adviceCount = d.optInt("advice_count")
                )
            )
        }
        return result
    }

    private fun doubleOrNull(obj: JSONObject, key: String): Double? =
        if (obj.isNull(key)) null else obj.optDouble(key).takeIf { !it.isNaN() }

    private fun intOrNull(obj: JSONObject, key: String): Int? =
        if (obj.isNull(key)) null else obj.optInt(key)

    private fun encode(v: String): String = URLEncoder.encode(v, "UTF-8")

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
