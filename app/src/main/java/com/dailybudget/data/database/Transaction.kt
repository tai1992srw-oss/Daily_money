package com.dailybudget.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dailybudget.data.model.Category
import com.dailybudget.data.model.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: LocalDate,
    val type: TransactionType,
    val amount: Int,
    val memo: String = "",
    val category: Category = Category.OTHER,
    val timestamp: LocalDateTime = LocalDateTime.now()
)
