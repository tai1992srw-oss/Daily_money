package com.dietlog.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietlog.data.diet.DaySummary
import com.dietlog.data.diet.DietDay
import com.dietlog.data.repository.DietRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: DietRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CalendarUiState(
            month = YearMonth.from(repository.logicalToday()),
            today = repository.logicalToday()
        )
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = repository.settings.first()
            if (settings.isConfigured) {
                loadMonth(_uiState.value.month)
            } else {
                _uiState.update { it.copy(isLoading = false, configured = false) }
            }
        }
    }

    fun refresh() {
        loadMonth(_uiState.value.month)
    }

    fun previousMonth() {
        loadMonth(_uiState.value.month.minusMonths(1))
    }

    fun nextMonth() {
        loadMonth(_uiState.value.month.plusMonths(1))
    }

    fun selectDay(date: LocalDate) {
        val dateStr = date.toString()
        _uiState.update { it.copy(selectedDate = dateStr, selectedDay = null, detailLoading = true) }
        viewModelScope.launch {
            runCatching { repository.fetchDay(dateStr) }
                .onSuccess { day ->
                    _uiState.update { it.copy(selectedDay = day, detailLoading = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(selectedDate = null, detailLoading = false) }
                }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedDate = null, selectedDay = null, detailLoading = false) }
    }

    private fun loadMonth(month: YearMonth) {
        _uiState.update {
            it.copy(
                month = month,
                isLoading = true,
                error = null,
                today = repository.logicalToday()
            )
        }
        viewModelScope.launch {
            runCatching { repository.fetchMonth(month) }
                .onSuccess { summaries ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            configured = true,
                            days = summaries.associateBy { it.date }
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

data class CalendarUiState(
    val month: YearMonth,
    val today: LocalDate,
    val days: Map<String, DaySummary> = emptyMap(),
    val isLoading: Boolean = true,
    val configured: Boolean = true,
    val error: String? = null,
    val selectedDate: String? = null,
    val selectedDay: DietDay? = null,
    val detailLoading: Boolean = false
)
