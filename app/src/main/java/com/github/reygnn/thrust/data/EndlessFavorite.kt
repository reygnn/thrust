package com.github.reygnn.thrust.data

import com.github.reygnn.thrust.domain.level.Difficulty

/**
 * Vom Spieler gespeichertes Endless-Level — reproduzierbar über (Difficulty, Seed).
 *
 * [savedAt] ist die Wall-Clock-Zeit (Epoch ms) des Speicherns. Sie dient nur der
 * Sortierung in der UI; die Logik selbst hängt nicht davon ab.
 */
data class EndlessFavorite(
    val difficulty: Difficulty,
    val seed: Long,
    val savedAt: Long,
)
