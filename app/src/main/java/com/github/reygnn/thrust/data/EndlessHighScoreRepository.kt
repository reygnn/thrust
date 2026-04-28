package com.github.reygnn.thrust.data

import com.github.reygnn.thrust.domain.level.Difficulty
import kotlinx.coroutines.flow.Flow

/**
 * Persistiert die längste in Endless erreichte Streak (Anzahl abgeschlossener
 * Level pro Difficulty).
 */
interface EndlessHighScoreRepository {
    /** Map<Difficulty, bisher beste Streak>. */
    fun getStreaks(): Flow<Map<Difficulty, Int>>
    /** Speichert nur wenn streak > bisheriger Rekord für diese Difficulty. */
    suspend fun updateStreak(difficulty: Difficulty, streak: Int)
}
