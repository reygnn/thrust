package com.github.reygnn.thrust.ui.game

import com.github.reygnn.thrust.domain.level.Difficulty
import com.github.reygnn.thrust.domain.level.PracticeKind

/**
 * Aktiver Spielmodus des [GameViewModel].
 *
 * - [Story] folgt der Level-Liste in [com.github.reygnn.thrust.domain.level.Levels]
 *   und persistiert HighScores pro Level-ID.
 * - [Endless] erzeugt prozedurale Level einer festen [Difficulty]; Tod mit
 *   Restleben bedeutet "neues Game mit selbem Level" (volles Reset, gleicher Seed).
 * - [EndlessFavorite] spielt einen vom Spieler gespeicherten Seed nach. Streak
 *   wird **nicht** gezählt (kein Level-Hopping zur Bestenliste); LevelComplete
 *   führt direkt zurück ans Menü, GameOver-Overlay zeigt nur Menu/Retry.
 */
sealed interface GameMode {
    data object Story : GameMode
    data class Endless(val difficulty: Difficulty) : GameMode
    data class EndlessFavorite(val difficulty: Difficulty, val seed: Long) : GameMode

    /**
     * Übungsmodus — kein Score, keine Lives, kein HighScore. Schiff respawnt
     * automatisch nach Tod oder geschafftem Move; das jeweilige Element
     * (Pod, Turret) materialisiert nach 2 s an neuer Position.
     */
    data class Practice(val kind: PracticeKind) : GameMode
}
