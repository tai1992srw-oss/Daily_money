package com.dietlog.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietlog.data.diet.MealRecord
import com.dietlog.data.diet.PlaceMeal
import com.dietlog.data.diet.PlaceVisit
import com.dietlog.data.repository.DietRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** お店タブの並び順。 */
enum class PlaceSort(val label: String) {
    AREA("エリア順"),
    VISITS("よく行く順"),
    RECENT("最近行った順")
}

@HiltViewModel
class PlacesViewModel @Inject constructor(
    private val repository: DietRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlacesUiState())
    val uiState: StateFlow<PlacesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun refresh() = load()

    fun setSort(sort: PlaceSort) {
        _uiState.update { it.copy(sort = sort) }
    }

    /** 店をタップ → 詳細画面。その店で食べた全記録を読み込む。 */
    fun selectPlace(place: PlaceVisit) {
        _uiState.update {
            it.copy(
                selectedPlace = place,
                placeMeals = emptyList(),
                placeMealsLoading = true,
                placeMealsError = null
            )
        }
        viewModelScope.launch {
            runCatching { repository.fetchPlaceMeals(place.name.ifBlank { place.url }) }
                .onSuccess { meals ->
                    _uiState.update { it.copy(placeMealsLoading = false, placeMeals = meals) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            placeMealsLoading = false,
                            placeMealsError = e.message ?: "読み込みに失敗しました"
                        )
                    }
                }
        }
    }

    /** 詳細画面を閉じて一覧に戻る。 */
    fun closePlace() {
        _uiState.update {
            it.copy(selectedPlace = null, placeMeals = emptyList(), placeMealsError = null)
        }
    }

    /** エリアの絞り込み。同じ都道府県をもう一度押すと解除。 */
    fun togglePrefecture(prefecture: String) {
        _uiState.update {
            it.copy(selectedPrefecture = if (it.selectedPrefecture == prefecture) null else prefecture)
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val settings = repository.settings.first()
            if (!settings.isConfigured) {
                _uiState.update { it.copy(isLoading = false, configured = false) }
                return@launch
            }
            runCatching { repository.fetchPlaces() }
                .onSuccess { places ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            configured = true,
                            places = places,
                            // 消えた都道府県で絞り込んだままにしない
                            selectedPrefecture = it.selectedPrefecture
                                ?.takeIf { pref -> places.any { p -> p.prefecture == pref } }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "読み込みに失敗しました")
                    }
                }
        }
    }
}

data class PlacesUiState(
    val places: List<PlaceVisit> = emptyList(),
    val sort: PlaceSort = PlaceSort.AREA,
    val selectedPrefecture: String? = null,
    val isLoading: Boolean = true,
    val configured: Boolean = true,
    val error: String? = null,
    /** タップされた店。null なら一覧表示。 */
    val selectedPlace: PlaceVisit? = null,
    val placeMeals: List<PlaceMeal> = emptyList(),
    val placeMealsLoading: Boolean = false,
    val placeMealsError: String? = null
) {
    /** 詳細画面用：日付ごとにまとめた訪問履歴（新しい順）。 */
    val placeVisitDays: List<PlaceVisitDay>
        get() = placeMeals
            .groupBy { it.date }
            .map { (date, meals) ->
                PlaceVisitDay(
                    date = date,
                    meals = meals.map { it.record }.sortedBy { it.time },
                    totalKcal = meals.sumOf { it.record.kcal }
                )
            }
            .sortedByDescending { it.date }

    /** 絞り込みボタンに出す都道府県。店数の多い順。 */
    val prefectures: List<String>
        get() = places.groupingBy { it.prefecture }.eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

    private val filtered: List<PlaceVisit>
        get() = selectedPrefecture?.let { pref -> places.filter { it.prefecture == pref } } ?: places

    /**
     * 画面に出す並び。エリア順のときだけ都道府県ごとの見出しを付ける
     * （見出し = null なら1つの塊として並べる）。
     */
    val sections: List<PlaceSection>
        get() = when (sort) {
            PlaceSort.AREA -> filtered
                .sortedWith(compareBy({ it.prefecture }, { it.locality }, { it.name }))
                .groupBy { it.prefecture }
                .map { (prefecture, items) -> PlaceSection(prefecture, items) }
                // エリア未設定は最後に回す
                .sortedBy { if (it.title == PlaceVisit.UNKNOWN_AREA) 1 else 0 }

            PlaceSort.VISITS -> listOf(
                PlaceSection(
                    null,
                    filtered.sortedWith(compareByDescending<PlaceVisit> { it.visits }
                        .thenByDescending { it.lastDate })
                )
            )

            PlaceSort.RECENT -> listOf(
                PlaceSection(null, filtered.sortedByDescending { it.lastDate })
            )
        }

    val totalVisits: Int
        get() = filtered.sumOf { it.visits }
}

data class PlaceSection(val title: String?, val places: List<PlaceVisit>)

/** お店詳細画面の1日分（1回の訪問）。 */
data class PlaceVisitDay(
    val date: String,
    val meals: List<MealRecord>,
    val totalKcal: Int
)
