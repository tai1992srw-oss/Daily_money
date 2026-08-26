package com.dietlog.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dietlog.data.diet.ActivityData
import com.dietlog.data.diet.DaySummary
import com.dietlog.data.diet.DietDay
import com.dietlog.data.diet.DietSettings
import com.dietlog.data.repository.DietRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DietViewModel @Inject constructor(
    private val repository: DietRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DietUiState())
    val uiState: StateFlow<DietUiState> = _uiState.asStateFlow()

    val healthConnectPermissions: Set<String>
        get() = repository.healthConnect.permissions

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings, settingsLoaded = true) }
            }
        }
        viewModelScope.launch {
            val settings = repository.settings.first()
            checkHealthConnect()
            if (settings.isConfigured) {
                refreshInternal()
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshInternal() }
    }

    fun onPermissionResult() {
        refresh()
    }

    fun saveSettings(apiUrl: String, token: String, targetKcal: Int, targetProteinG: Int) {
        viewModelScope.launch {
            repository.saveSettings(DietSettings(apiUrl, token, targetKcal, targetProteinG))
            _uiState.update { it.copy(showSettings = false) }
            refreshInternal()
        }
    }

    fun showSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun hideSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    private suspend fun refreshInternal() {
        val settings = repository.settings.first()
        if (!settings.isConfigured) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        checkHealthConnect()
        runCatching { repository.sync() }
            .onSuccess { result ->
                // 体重グラフ用の履歴。取れなくても今日の表示は続行する
                val history = runCatching { repository.fetchRecent(HISTORY_DAYS) }
                    .getOrDefault(emptyList())
                    .sortedBy { it.date }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        day = result.day,
                        activity = result.activity,
                        uploadError = result.activityUploadError,
                        history = history
                    )
                }
            }
            .onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "同期に失敗しました")
                }
            }
    }

    private suspend fun checkHealthConnect() {
        val available = repository.healthConnect.isAvailable()
        val granted = available && runCatching {
            repository.healthConnect.hasAllPermissions()
        }.getOrDefault(false)
        _uiState.update { it.copy(hcAvailable = available, hcGranted = granted) }
    }

    companion object {
        private const val HISTORY_DAYS = 30
    }
}

data class DietUiState(
    val settings: DietSettings = DietSettings(),
    val settingsLoaded: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val day: DietDay? = null,
    val activity: ActivityData? = null,
    val uploadError: Boolean = false,
    val hcAvailable: Boolean = false,
    val hcGranted: Boolean = false,
    val showSettings: Boolean = false,
    val history: List<DaySummary> = emptyList()
)
