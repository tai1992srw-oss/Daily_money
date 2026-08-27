package com.dietlog.data.diet

/** 日付の切り替わり時刻。午前5時までは前日扱い（GAS側と揃えること）。 */
const val DAY_START_HOUR = 5

data class DietSettings(
    val apiUrl: String = "",
    val token: String = "",
    val targetKcal: Int = 1750,
    val targetProteinG: Int = 125,
    /** 維持カロリー（キープライン）。これを超えると増量圏。 */
    val maintenanceKcal: Int = 2230
) {
    val isConfigured: Boolean
        get() = apiUrl.isNotBlank() && token.isNotBlank()

    /** ゆるく減量ライン（週−0.25kgペース ≒ 維持−250kcal）。 */
    val easyKcal: Int
        get() = maintenanceKcal - 250

    /** タンパク質の最低ライン（目標=1.6g/kg 相当に対する 1.2g/kg ≒ ×0.75）。 */
    val minProteinG: Int
        get() = (targetProteinG * 3) / 4

    /** タンパク質の上限ライン（2.0g/kg ≒ ×1.25。これ以上は追加メリットなし）。 */
    val maxProteinG: Int
        get() = (targetProteinG * 5) / 4
}

data class MealRecord(
    val time: String,
    val meal: String,
    val description: String,
    val kcal: Int,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val note: String,
    /** Drive に保存した食事写真（リンク共有）。1件に何枚でも付けられる。 */
    val photoUrls: List<String> = emptyList(),
    /** 店名。Google マップ / 食べログの URL から取り込む。 */
    val placeName: String = "",
    val placeUrl: String = "",
    /** 「埼玉県戸田市」のような地域。お店タブのまとめに使う。 */
    val placeArea: String = ""
) {
    val hasPlace: Boolean
        get() = placeName.isNotBlank() || placeUrl.isNotBlank()
}

/** 行った店ごとの集計（お店タブ）。 */
data class PlaceVisit(
    val name: String,
    val url: String,
    val area: String,
    /** 訪問した日数。同じ日に複数回記録しても1回。 */
    val visits: Int,
    /** その店で記録した食事の件数。 */
    val meals: Int,
    val totalKcal: Int,
    val firstDate: String,
    val lastDate: String
) {
    /** 地域のまとめ見出し。「埼玉県戸田市」→「埼玉県」。 */
    val prefecture: String
        get() = PREFECTURE_PATTERN.find(area)?.value ?: UNKNOWN_AREA

    /** 都道府県より細かい部分。「埼玉県戸田市」→「戸田市」。 */
    val locality: String
        get() = area.removePrefix(prefecture).trim()

    companion object {
        const val UNKNOWN_AREA = "エリア未設定"
        private val PREFECTURE_PATTERN =
            Regex("^(北海道|東京都|京都府|大阪府|.{2,3}?県)")
    }
}

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
