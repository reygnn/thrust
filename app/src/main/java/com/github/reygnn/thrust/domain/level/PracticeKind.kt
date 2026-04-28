package com.github.reygnn.thrust.domain.level

/**
 * Übungsmodus — kein Score, kein Highscore, keine Lives. Spielt unbegrenzt
 * weiter und resettet automatisch nach Tod oder geschafftem Move.
 */
enum class PracticeKind(val displayName: String) {
    /** Endloser Schlauch mit Hindernissen und Engstellen — Navigation üben. */
    TUBE("Tube"),
    /** Pod holen, zum Pad bringen, sanft landen — den vollen Zyklus üben. */
    DELIVERY("Delivery"),
    /** Einzelner Turret, materialisiert nach Abschuss an neuer Position. */
    TURRETS("Turrets"),
}
