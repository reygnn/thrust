package com.github.reygnn.thrust.data

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow

/** Steuerungsmodus: klassische Buttons oder Drehrad-basiert. */
enum class ControlMode { BUTTONS, WHEEL }

/** Position des Schub-Buttons im Drehrad-Modus. */
enum class ThrustSide { LEFT, RIGHT }

/**
 * Drehrad-Größe (Durchmesser).
 *
 * Größere Räder sind angenehmer zu bedienen, verdecken aber mehr vom Spielfeld.
 * Default ist [MEDIUM] (entspricht dem ursprünglichen festen Wert von 144dp).
 */
enum class WheelSize(val diameter: Dp) {
    SMALL(120.dp),
    MEDIUM(144.dp),
    LARGE(180.dp),
    XL(220.dp),
}

interface SettingsRepository {
    val playerGunEnabled: Flow<Boolean>
    suspend fun setPlayerGunEnabled(enabled: Boolean)

    val controlMode: Flow<ControlMode>
    suspend fun setControlMode(mode: ControlMode)

    /** Nur relevant im Drehrad-Modus. */
    val thrustSide: Flow<ThrustSide>
    suspend fun setThrustSide(side: ThrustSide)

    /** Nur relevant im Drehrad-Modus. */
    val wheelSize: Flow<WheelSize>
    suspend fun setWheelSize(size: WheelSize)
}