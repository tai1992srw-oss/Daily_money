package com.dailybudget.data.diet

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

data class DietDay(
    val date: String,
    val meals: List<MealRecord>,
    val totals: MealTotals,
    val sheetActivity: ActivityData?
)

data class ActivityData(
    val steps: Long,
    val totalKcal: Int,
    val activeKcal: Int
)
