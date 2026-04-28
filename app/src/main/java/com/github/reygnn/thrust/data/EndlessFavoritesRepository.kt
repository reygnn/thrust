package com.github.reygnn.thrust.data

import kotlinx.coroutines.flow.Flow

/**
 * Persistiert die Lieblings-Endless-Level des Spielers.
 *
 * Reihenfolge der Liste: zuletzt gespeichert zuerst (newest first). Die
 * Implementation ist verantwortlich für Größenbegrenzung (FIFO bei Limit).
 */
interface EndlessFavoritesRepository {
    fun getFavorites(): Flow<List<EndlessFavorite>>
    suspend fun addFavorite(favorite: EndlessFavorite)
    suspend fun removeFavorite(favorite: EndlessFavorite)
}
