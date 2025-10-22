package com.dailybudget.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailybudget.data.database.Transaction
import com.dailybudget.data.model.Category
import com.dailybudget.data.model.TransactionType
import com.dailybudget.data.repository.BudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: BudgetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Check and perform carry over if date changed
            repository.checkAndPerformCarryOver()

            // Collect today's budget data
            repository.getTodayBudgetFlow(LocalDate.now())
                .collect { data ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            availableBudget = data.availableBudget,
                            transactions = data.transactions,
                            isInitialized = data.isInitialized,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun onInitialSetupComplete(dailyBudget: Int) {
        viewModelScope.launch {
            repository.saveSettings(dailyBudget)
            _uiState.update { it.copy(showInitialSetup = false) }
        }
    }

    fun onAddExpense(amount: Int, memo: String, category: Category) {
        viewModelScope.launch {
            repository.addTransaction(
                type = TransactionType.EXPENSE,
                amount = amount,
                memo = memo,
                category = category
            )
            _uiState.update { it.copy(showExpenseInput = false) }
        }
    }

    fun onAddIncome(amount: Int, memo: String, category: Category) {
        viewModelScope.launch {
            repository.addTransaction(
                type = TransactionType.INCOME,
                amount = amount,
                memo = memo,
                category = category
            )
            _uiState.update { it.copy(showIncomeInput = false) }
        }
    }

    fun onDeleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun showExpenseInput() {
        _uiState.update { it.copy(showExpenseInput = true) }
    }

    fun hideExpenseInput() {
        _uiState.update { it.copy(showExpenseInput = false) }
    }

    fun showIncomeInput() {
        _uiState.update { it.copy(showIncomeInput = true) }
    }

    fun hideIncomeInput() {
        _uiState.update { it.copy(showIncomeInput = false) }
    }

    fun showInitialSetup() {
        _uiState.update { it.copy(showInitialSetup = true) }
    }

    fun hideInitialSetup() {
        _uiState.update { it.copy(showInitialSetup = false) }
    }
}

data class MainUiState(
    val availableBudget: Int = 0,
    val transactions: List<Transaction> = emptyList(),
    val isInitialized: Boolean = false,
    val isLoading: Boolean = true,
    val showExpenseInput: Boolean = false,
    val showIncomeInput: Boolean = false,
    val showInitialSetup: Boolean = false,
    val currentDate: LocalDate = LocalDate.now()
)
