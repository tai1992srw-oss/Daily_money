package com.dailybudget.data.diet

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dietDataStore by preferencesDataStore(name = "diet_settings")

@Singleton
class DietSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val API_URL = stringPreferencesKey("api_url")
        val TOKEN = stringPreferencesKey("token")
        val TARGET_KCAL = intPreferencesKey("target_kcal")
    }

    val settings: Flow<DietSettings> = context.dietDataStore.data.map { prefs ->
        DietSettings(
            apiUrl = prefs[Keys.API_URL] ?: "",
            token = prefs[Keys.TOKEN] ?: "",
            targetKcal = prefs[Keys.TARGET_KCAL] ?: 1800
        )
    }

    suspend fun save(settings: DietSettings) {
        context.dietDataStore.edit { prefs ->
            prefs[Keys.API_URL] = settings.apiUrl.trim()
            prefs[Keys.TOKEN] = settings.token.trim()
            prefs[Keys.TARGET_KCAL] = settings.targetKcal
        }
    }
}
