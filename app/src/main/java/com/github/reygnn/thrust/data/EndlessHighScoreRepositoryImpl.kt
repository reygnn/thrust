package com.github.reygnn.thrust.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.reygnn.thrust.domain.level.Difficulty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.endlessDataStore: DataStore<Preferences> by preferencesDataStore(name = "thrust_endless_highscores")

/**
 * DataStore-backed Endless-HighScore-Repository.
 *
 * Schlüssel werden dynamisch über [Difficulty.values] erzeugt — pro Difficulty
 * genau ein Eintrag (`endless_streak_<DIFFICULTY_NAME>`). Damit skaliert die
 * Persistenz automatisch, falls weitere Schwierigkeitsgrade hinzukommen.
 */
class EndlessHighScoreRepositoryImpl(
    private val context: Context,
) : EndlessHighScoreRepository {

    private val keys: Map<Difficulty, Preferences.Key<Int>> =
        Difficulty.values().associateWith { intPreferencesKey("endless_streak_${it.name}") }

    override fun getStreaks(): Flow<Map<Difficulty, Int>> =
        context.endlessDataStore.data.map { prefs ->
            keys.mapValues { (_, key) -> prefs[key] ?: 0 }
        }

    override suspend fun updateStreak(difficulty: Difficulty, streak: Int) {
        val key = keys.getValue(difficulty)
        context.endlessDataStore.edit { prefs ->
            val current = prefs[key] ?: 0
            if (streak > current) prefs[key] = streak
        }
    }
}
