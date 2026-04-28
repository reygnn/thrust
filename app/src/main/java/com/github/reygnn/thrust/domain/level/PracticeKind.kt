package com.github.reygnn.thrust.domain.level

/**
 * Übungsmodus — kein Score, kein Highscore, keine Lives. Spielt unbegrenzt
 * weiter und resettet automatisch nach Tod oder geschafftem Move.
 */
enum class PracticeKind(val displayName: String) {
    /** Endloser horizontaler Schlauch — Navigation üben. */
    TUBE("Tube"),
    /** Pod aufnehmen, 2 s später materialisiert er an neuer Position. */
    DOCKING("Docking"),
    /** Plattform mittig, Schiff respawnt nach erfolgreicher Landung oben. */
    LANDING("Landing"),
    /** Einzelner Turret, materialisiert nach Abschuss an neuer Position. */
    TURRETS("Turrets"),
}
