package com.dailybudget.data.repository

import com.dailybudget.data.database.*
import com.dailybudget.data.model.Category
import com.dailybudget.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val settingsDao: SettingsDao,
    private val dailyBalanceDao: DailyBalanceDao
) {
    // Settings
    fun getSettings(): Flow<Settings?> = settingsDao.getSettings()

    suspend fun getSettingsSync(): Settings? = settingsDao.getSettingsSync()

    suspend fun saveSettings(dailyBudget: Int) {
        settingsDao.insertOrUpdate(
            Settings(
                id = 1,
                dailyBudget = dailyBudget,
                lastUpdateDate = LocalDate.now()
            )
        )
    }

    suspend fun updateDailyBudget(dailyBudget: Int) {
        settingsDao.updateDailyBudget(dailyBudget)
    }

    // Transactions
    fun getTransactionsByDate(date: LocalDate): Flow<List<Transaction>> =
        transactionDao.getTransactionsByDate(date)

    suspend fun addTransaction(
        type: TransactionType,
        amount: Int,
        memo: String,
        category: Category,
        date: LocalDate = LocalDate.now()
    ) {
        transactionDao.insert(
            Transaction(
                date = date,
                type = type,
                amount = amount,
                memo = memo,
                category = category,
                timestamp = LocalDateTime.now()
            )
        )
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.delete(transaction)
    }

    // Daily Balance
    fun getDailyBalance(date: LocalDate): Flow<DailyBalance?> =
        dailyBalanceDao.getBalanceByDate(date)

    suspend fun getDailyBalanceSync(date: LocalDate): DailyBalance? =
        dailyBalanceDao.getBalanceByDateSync(date)

    suspend fun saveDailyBalance(date: LocalDate, balance: Int, carryOver: Int) {
        dailyBalanceDao.insertOrUpdate(
            DailyBalance(
                date = date,
                balance = balance,
                carryOver = carryOver
            )
        )
    }

    // Calculate today's available budget
    suspend fun calculateTodayBudget(date: LocalDate = LocalDate.now()): Int {
        val settings = getSettingsSync() ?: return 0
        val dailyBalance = getDailyBalanceSync(date)

        val carryOver = dailyBalance?.carryOver ?: 0
        val todayIncome = transactionDao.getTotalIncomeByDate(date) ?: 0
        val todayExpense = transactionDao.getTotalExpenseByDate(date) ?: 0

        return settings.dailyBudget + carryOver + todayIncome - todayExpense
    }

    // Carry over balance to next day
    suspend fun carryOverBalance(fromDate: LocalDate, toDate: LocalDate) {
        val yesterdayBalance = calculateTodayBudget(fromDate)
        saveDailyBalance(toDate, 0, yesterdayBalance)
    }

    // Check if date changed and perform carry over
    suspend fun checkAndPerformCarryOver() {
        val settings = getSettingsSync() ?: return
        val today = LocalDate.now()

        if (settings.lastUpdateDate.isBefore(today)) {
            // Perform carry over from last update date to today
            val yesterdayBalance = calculateTodayBudget(settings.lastUpdateDate)
            saveDailyBalance(today, 0, yesterdayBalance)

            // Update last update date
            settingsDao.insertOrUpdate(settings.copy(lastUpdateDate = today))
        }
    }

    // Flow that combines all data for main screen
    fun getTodayBudgetFlow(date: LocalDate = LocalDate.now()): Flow<TodayBudgetData> {
        return combine(
            getSettings(),
            getTransactionsByDate(date),
            getDailyBalance(date)
        ) { settings, transactions, balance ->
            val carryOver = balance?.carryOver ?: 0
            val todayIncome = transactions.filter { it.type == TransactionType.INCOME }
                .sumOf { it.amount }
            val todayExpense = transactions.filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }

            val availableBudget = (settings?.dailyBudget ?: 0) + carryOver + todayIncome - todayExpense

            TodayBudgetData(
                availableBudget = availableBudget,
                dailyBudget = settings?.dailyBudget ?: 0,
                carryOver = carryOver,
                todayIncome = todayIncome,
                todayExpense = todayExpense,
                transactions = transactions,
                isInitialized = settings != null
            )
        }
    }
}

data class TodayBudgetData(
    val availableBudget: Int,
    val dailyBudget: Int,
    val carryOver: Int,
    val todayIncome: Int,
    val todayExpense: Int,
    val transactions: List<Transaction>,
    val isInitialized: Boolean
)
