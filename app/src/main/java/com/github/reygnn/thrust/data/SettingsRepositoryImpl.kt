package com.github.reygnn.thrust.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "thrust_settings")

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    private val KEY_PLAYER_GUN = booleanPreferencesKey("player_gun_enabled")

    override val playerGunEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs -> prefs[KEY_PLAYER_GUN] ?: false }

    override suspend fun setPlayerGunEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_PLAYER_GUN] = enabled }
    }
}
