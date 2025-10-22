package com.dailybudget.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "daily_balance")
data class DailyBalance(
    @PrimaryKey
    val date: LocalDate,
    val balance: Int,
    val carryOver: Int
)
