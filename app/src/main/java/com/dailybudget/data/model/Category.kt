package com.dailybudget.data.model

enum class Category(val displayName: String, val emoji: String) {
    FOOD("食費", "🍔"),
    TRANSPORT("交通費", "🚃"),
    DAILY_GOODS("日用品", "🛒"),
    ENTERTAINMENT("娯楽", "🎮"),
    OTHER("その他", "📌");

    companion object {
        fun fromDisplayName(name: String): Category {
            return values().find { it.displayName == name } ?: OTHER
        }
    }
}
