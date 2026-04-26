package com.github.reygnn.thrust.data

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val playerGunEnabled: Flow<Boolean>
    suspend fun setPlayerGunEnabled(enabled: Boolean)
}
