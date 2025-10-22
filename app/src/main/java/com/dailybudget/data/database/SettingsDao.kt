package com.dailybudget.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun getSettings(): Flow<Settings?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun getSettingsSync(): Settings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: Settings)

    @Query("UPDATE settings SET dailyBudget = :dailyBudget WHERE id = 1")
    suspend fun updateDailyBudget(dailyBudget: Int)
}
