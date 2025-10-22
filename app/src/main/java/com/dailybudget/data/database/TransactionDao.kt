package com.dailybudget.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE date = :date ORDER BY timestamp DESC")
    fun getTransactionsByDate(date: LocalDate): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date = :date")
    suspend fun getTransactionsByDateSync(date: LocalDate): List<Transaction>

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE date = :date")
    suspend fun deleteByDate(date: LocalDate)

    @Query("SELECT SUM(amount) FROM transactions WHERE date = :date AND type = 'EXPENSE'")
    suspend fun getTotalExpenseByDate(date: LocalDate): Int?

    @Query("SELECT SUM(amount) FROM transactions WHERE date = :date AND type = 'INCOME'")
    suspend fun getTotalIncomeByDate(date: LocalDate): Int?
}
