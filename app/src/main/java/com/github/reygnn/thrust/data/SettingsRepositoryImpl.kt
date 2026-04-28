package com.github.reygnn.thrust.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.thrustSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "thrust_settings")

class SettingsRepositoryImpl(
    private val context: Context,
) : SettingsRepository {

    private val playerGunKey       = booleanPreferencesKey("player_gun_enabled")
    private val controlModeKey     = stringPreferencesKey("control_mode")
    private val thrustSideKey      = stringPreferencesKey("thrust_side")
    private val wheelSizeKey       = stringPreferencesKey("wheel_size")
    private val thrustButtonSizeKey = stringPreferencesKey("thrust_button_size")

    override val playerGunEnabled: Flow<Boolean> =
        context.thrustSettingsDataStore.data.map { it[playerGunKey] ?: false }

    override suspend fun setPlayerGunEnabled(enabled: Boolean) {
        context.thrustSettingsDataStore.edit { it[playerGunKey] = enabled }
    }

    override val controlMode: Flow<ControlMode> =
        context.thrustSettingsDataStore.data.map { prefs ->
            when (prefs[controlModeKey]) {
                ControlMode.WHEEL.name -> ControlMode.WHEEL
                else                   -> ControlMode.BUTTONS
            }
        }

    override suspend fun setControlMode(mode: ControlMode) {
        context.thrustSettingsDataStore.edit { it[controlModeKey] = mode.name }
    }

    override val thrustSide: Flow<ThrustSide> =
        context.thrustSettingsDataStore.data.map { prefs ->
            when (prefs[thrustSideKey]) {
                ThrustSide.LEFT.name -> ThrustSide.LEFT
                else                 -> ThrustSide.RIGHT
            }
        }

    override suspend fun setThrustSide(side: ThrustSide) {
        context.thrustSettingsDataStore.edit { it[thrustSideKey] = side.name }
    }

    override val wheelSize: Flow<WheelSize> =
        context.thrustSettingsDataStore.data.map { prefs ->
            // Defensive parsing: unknown stored values fall back to MEDIUM.
            // This is robust against typos AND against future enum-value removal.
            val stored = prefs[wheelSizeKey]
            WheelSize.entries.firstOrNull { it.name == stored } ?: WheelSize.MEDIUM
        }

    override suspend fun setWheelSize(size: WheelSize) {
        context.thrustSettingsDataStore.edit { it[wheelSizeKey] = size.name }
    }

    override val thrustButtonSize: Flow<ThrustButtonSize> =
        context.thrustSettingsDataStore.data.map { prefs ->
            val stored = prefs[thrustButtonSizeKey]
            ThrustButtonSize.entries.firstOrNull { it.name == stored } ?: ThrustButtonSize.MEDIUM
        }

    override suspend fun setThrustButtonSize(size: ThrustButtonSize) {
        context.thrustSettingsDataStore.edit { it[thrustButtonSizeKey] = size.name }
    }
}