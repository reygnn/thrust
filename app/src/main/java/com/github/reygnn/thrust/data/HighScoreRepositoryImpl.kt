package com.github.reygnn.thrust.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.thrustDataStore: DataStore<Preferences> by preferencesDataStore(name = "thrust_highscores")

class HighScoreRepositoryImpl(private val context: Context) : HighScoreRepository {

    private val KEY_1 = intPreferencesKey("hs_level_1")
    private val KEY_2 = intPreferencesKey("hs_level_2")
    private val KEY_3 = intPreferencesKey("hs_level_3")

    override fun getHighScores(): Flow<Map<Int, Int>> =
        context.thrustDataStore.data.map { prefs ->
            mapOf(
                1 to (prefs[KEY_1] ?: 0),
                2 to (prefs[KEY_2] ?: 0),
                3 to (prefs[KEY_3] ?: 0),
            )
        }

    override suspend fun updateHighScore(level: Int, score: Int) {
        context.thrustDataStore.edit { prefs ->
            val key     = keyFor(level)
            val current = prefs[key] ?: 0
            if (score > current) prefs[key] = score
        }
    }

    private fun keyFor(level: Int) = when (level) {
        1    -> KEY_1
        2    -> KEY_2
        else -> KEY_3
    }
}
