package com.github.reygnn.thrust.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.reygnn.thrust.domain.level.Levels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.thrustDataStore: DataStore<Preferences> by preferencesDataStore(name = "thrust_highscores")

/**
 * DataStore-backed Highscore-Repository.
 *
 * Keys werden dynamisch auf Basis von [totalLevels] erzeugt – pro Level genau ein Eintrag
 * (`hs_level_$level`). Damit skaliert die Persistenz automatisch, wenn neue Level
 * hinzukommen, ohne dass dieser Code angepasst werden muss.
 */
class HighScoreRepositoryImpl(
    private val context: Context,
    private val totalLevels: Int = Levels.totalLevels,
) : HighScoreRepository {

    init {
        require(totalLevels > 0) { "totalLevels must be > 0, was $totalLevels" }
    }

    private val keys: Map<Int, Preferences.Key<Int>> =
        (1..totalLevels).associateWith { intPreferencesKey("hs_level_$it") }

    override fun getHighScores(): Flow<Map<Int, Int>> =
        context.thrustDataStore.data.map { prefs ->
            keys.mapValues { (_, key) -> prefs[key] ?: 0 }
        }

    override suspend fun updateHighScore(level: Int, score: Int) {
        val key = keys[level]
            ?: throw IllegalArgumentException(
                "Unknown level: $level (valid range: 1..$totalLevels)"
            )
        context.thrustDataStore.edit { prefs ->
            val current = prefs[key] ?: 0
            if (score > current) prefs[key] = score
        }
    }
}