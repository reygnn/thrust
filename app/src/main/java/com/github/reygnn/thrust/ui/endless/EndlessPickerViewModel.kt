package com.github.reygnn.thrust.ui.endless

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.reygnn.thrust.ThrustApplication
import com.github.reygnn.thrust.data.EndlessHighScoreRepository
import com.github.reygnn.thrust.domain.level.Difficulty
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class EndlessPickerViewModel(
    repository: EndlessHighScoreRepository,
) : ViewModel() {

    val streaks: StateFlow<Map<Difficulty, Int>> = repository.getStreaks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ThrustApplication
                EndlessPickerViewModel(app.endlessHighScoreRepository)
            }
        }
    }
}
