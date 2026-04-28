package com.github.reygnn.thrust.ui.options

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.reygnn.thrust.ThrustApplication
import com.github.reygnn.thrust.data.ControlMode
import com.github.reygnn.thrust.data.SettingsRepository
import com.github.reygnn.thrust.data.ThrustButtonSize
import com.github.reygnn.thrust.data.ThrustSide
import com.github.reygnn.thrust.data.WheelSize
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OptionsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val playerGunEnabled: StateFlow<Boolean> = settingsRepository.playerGunEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val controlMode: StateFlow<ControlMode> = settingsRepository.controlMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ControlMode.BUTTONS)

    val thrustSide: StateFlow<ThrustSide> = settingsRepository.thrustSide
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThrustSide.RIGHT)

    val wheelSize: StateFlow<WheelSize> = settingsRepository.wheelSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WheelSize.MEDIUM)

    val thrustButtonSize: StateFlow<ThrustButtonSize> = settingsRepository.thrustButtonSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThrustButtonSize.MEDIUM)

    fun togglePlayerGun(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPlayerGunEnabled(enabled) }
    }

    fun setControlMode(mode: ControlMode) {
        viewModelScope.launch { settingsRepository.setControlMode(mode) }
    }

    fun setThrustSide(side: ThrustSide) {
        viewModelScope.launch { settingsRepository.setThrustSide(side) }
    }

    fun setWheelSize(size: WheelSize) {
        viewModelScope.launch { settingsRepository.setWheelSize(size) }
    }

    fun setThrustButtonSize(size: ThrustButtonSize) {
        viewModelScope.launch { settingsRepository.setThrustButtonSize(size) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ThrustApplication
                OptionsViewModel(settingsRepository = app.settingsRepository)
            }
        }
    }
}