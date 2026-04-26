package com.github.reygnn.thrust.ui.highscore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.reygnn.thrust.ThrustApplication
import com.github.reygnn.thrust.data.HighScoreRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HighScoreViewModel(
    highScoreRepository: HighScoreRepository,
) : ViewModel() {

    val highScores: StateFlow<Map<Int, Int>> = highScoreRepository.getHighScores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ThrustApplication
                HighScoreViewModel(app.highScoreRepository)
            }
        }
    }
}
