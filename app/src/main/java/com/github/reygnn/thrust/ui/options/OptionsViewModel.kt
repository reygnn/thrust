package com.github.reygnn.thrust.ui.options

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.github.reygnn.thrust.ThrustApplication
import com.github.reygnn.thrust.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OptionsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val playerGunEnabled: StateFlow<Boolean> = settingsRepository.playerGunEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun togglePlayerGun(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPlayerGunEnabled(enabled) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ThrustApplication
                return OptionsViewModel(app.settingsRepository) as T
            }
        }
    }
}
