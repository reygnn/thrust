package com.github.reygnn.thrust.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.reygnn.thrust.domain.level.Difficulty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "thrust_endless_favorites")

/**
 * DataStore-backed Favorites-Repository.
 *
 * Speichert die gesamte Liste serialisiert in einem einzigen String-Preference.
 * Format pro Eintrag: `<DIFFICULTY_NAME>|<seed>|<savedAt>`, Einträge getrennt
 * durch `\n`. Unbekannte Difficulty-Namen oder defekte Zeilen werden beim
 * Lesen still überspringt — so bleibt die App auch nach einem Enum-Rename
 * lauffähig.
 *
 * Größenbegrenzung: [maxEntries] (default 20). Beim Hinzufügen verdrängen
 * neue Einträge die ältesten (FIFO).
 */
class EndlessFavoritesRepositoryImpl internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : EndlessFavoritesRepository {

    constructor(context: Context, maxEntries: Int = DEFAULT_MAX_ENTRIES) :
        this(context.favoritesDataStore, maxEntries)

    init {
        require(maxEntries > 0) { "maxEntries must be > 0, was $maxEntries" }
    }

    override fun getFavorites(): Flow<List<EndlessFavorite>> =
        dataStore.data.map { prefs ->
            decode(prefs[KEY] ?: "")
        }

    override suspend fun addFavorite(favorite: EndlessFavorite) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY] ?: "")
            // Idempotent: gleicher (difficulty, seed) wird nicht doppelt gespeichert.
            if (current.any { it.difficulty == favorite.difficulty && it.seed == favorite.seed }) return@edit
            val merged = (listOf(favorite) + current).take(maxEntries)
            prefs[KEY] = encode(merged)
        }
    }

    override suspend fun removeFavorite(favorite: EndlessFavorite) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY] ?: "")
            val filtered = current.filterNot { it.difficulty == favorite.difficulty && it.seed == favorite.seed }
            if (filtered.size != current.size) prefs[KEY] = encode(filtered)
        }
    }

    companion object {
        private val KEY = stringPreferencesKey("endless_favorites")
        const val DEFAULT_MAX_ENTRIES = 20

        internal fun encode(list: List<EndlessFavorite>): String =
            list.joinToString("\n") { "${it.difficulty.name}|${it.seed}|${it.savedAt}" }

        internal fun decode(raw: String): List<EndlessFavorite> {
            if (raw.isBlank()) return emptyList()
            return raw.split("\n").mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size != 3) return@mapNotNull null
                val d = runCatching { Difficulty.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
                val s = parts[1].toLongOrNull() ?: return@mapNotNull null
                val a = parts[2].toLongOrNull() ?: return@mapNotNull null
                EndlessFavorite(d, s, a)
            }
        }
    }
}
