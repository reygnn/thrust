package com.github.reygnn.thrust.ui.game

import com.github.reygnn.thrust.domain.level.Difficulty

/**
 * Aktiver Spielmodus des [GameViewModel].
 *
 * - [Story] folgt der Level-Liste in [com.github.reygnn.thrust.domain.level.Levels]
 *   und persistiert HighScores pro Level-ID.
 * - [Endless] erzeugt prozedurale Level einer festen [Difficulty]; Tod mit
 *   Restleben bedeutet "neues Game mit selbem Level" (volles Reset, gleicher Seed).
 */
sealed interface GameMode {
    data object Story : GameMode
    data class Endless(val difficulty: Difficulty) : GameMode
}
