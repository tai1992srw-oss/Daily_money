package com.dailybudget.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface DailyBalanceDao {
    @Query("SELECT * FROM daily_balance WHERE date = :date")
    fun getBalanceByDate(date: LocalDate): Flow<DailyBalance?>

    @Query("SELECT * FROM daily_balance WHERE date = :date")
    suspend fun getBalanceByDateSync(date: LocalDate): DailyBalance?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(dailyBalance: DailyBalance)

    @Query("DELETE FROM daily_balance WHERE date < :date")
    suspend fun deleteOldBalances(date: LocalDate)
}
