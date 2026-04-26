package com.github.reygnn.thrust

import android.app.Application
import com.github.reygnn.thrust.data.HighScoreRepository
import com.github.reygnn.thrust.data.HighScoreRepositoryImpl
import com.github.reygnn.thrust.data.SettingsRepository
import com.github.reygnn.thrust.data.SettingsRepositoryImpl

class ThrustApplication : Application() {
    val highScoreRepository: HighScoreRepository by lazy {
        HighScoreRepositoryImpl(this)
    }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(this)
    }
}
