package com.dietlog.data.diet

/** 日付の切り替わり時刻。午前5時までは前日扱い（GAS側と揃えること）。 */
const val DAY_START_HOUR = 5

data class DietSettings(
    val apiUrl: String = "",
    val token: String = "",
    val targetKcal: Int = 1800
) {
    val isConfigured: Boolean
        get() = apiUrl.isNotBlank() && token.isNotBlank()
}

data class MealRecord(
    val time: String,
    val meal: String,
    val description: String,
    val kcal: Int,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val note: String
)

data class MealTotals(
    val kcal: Int = 0,
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val carbsG: Double = 0.0
)

data class AdviceEntry(
    val time: String,
    val type: String,
    val content: String
)

data class ActivityData(
    val steps: Long = 0,
    val totalKcal: Int = 0,
    val activeKcal: Int = 0,
    val distanceKm: Double? = null,
    val sleepH: Double? = null,
    val weightKg: Double? = null
)

data class DietDay(
    val date: String,
    val meals: List<MealRecord>,
    val totals: MealTotals,
    val sheetActivity: ActivityData?,
    val advice: List<AdviceEntry>
)

/** カレンダー・レビュー用の日別サマリー。 */
data class DaySummary(
    val date: String,
    val intakeKcal: Int,
    val burnedKcal: Int?,
    val balanceKcal: Int?,
    val steps: Long?,
    val weightKg: Double?,
    val sleepH: Double?,
    val meals: Int,
    val adviceCount: Int
)
