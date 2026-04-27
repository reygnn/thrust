package com.github.reygnn.thrust.data

import kotlinx.coroutines.flow.Flow

/** Steuerungsmodus: klassische Buttons oder Drehrad-basiert. */
enum class ControlMode { BUTTONS, WHEEL }

/** Position des Schub-Buttons im Drehrad-Modus. */
enum class ThrustSide { LEFT, RIGHT }

interface SettingsRepository {
    val playerGunEnabled: Flow<Boolean>
    suspend fun setPlayerGunEnabled(enabled: Boolean)

    val controlMode: Flow<ControlMode>
    suspend fun setControlMode(mode: ControlMode)

    /** Nur relevant im Drehrad-Modus. */
    val thrustSide: Flow<ThrustSide>
    suspend fun setThrustSide(side: ThrustSide)
}