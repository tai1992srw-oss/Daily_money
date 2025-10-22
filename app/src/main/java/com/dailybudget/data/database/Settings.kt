package com.dailybudget.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey
    val id: Int = 1,
    val dailyBudget: Int,
    val lastUpdateDate: LocalDate
)
