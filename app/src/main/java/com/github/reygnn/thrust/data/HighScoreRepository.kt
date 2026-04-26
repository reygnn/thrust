package com.github.reygnn.thrust.data

import kotlinx.coroutines.flow.Flow

interface HighScoreRepository {
    /** Liefert Map<levelId, highScore>. */
    fun getHighScores(): Flow<Map<Int, Int>>
    /** Speichert nur wenn score > bisheriger Rekord. */
    suspend fun updateHighScore(level: Int, score: Int)
}
