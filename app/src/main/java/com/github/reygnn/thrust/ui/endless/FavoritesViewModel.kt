package com.github.reygnn.thrust.ui.endless

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.reygnn.thrust.ThrustApplication
import com.github.reygnn.thrust.data.EndlessFavorite
import com.github.reygnn.thrust.data.EndlessFavoritesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritesViewModel(
    private val repository: EndlessFavoritesRepository,
) : ViewModel() {

    val favorites: StateFlow<List<EndlessFavorite>> = repository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(favorite: EndlessFavorite) {
        viewModelScope.launch { repository.removeFavorite(favorite) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ThrustApplication
                FavoritesViewModel(app.endlessFavoritesRepository)
            }
        }
    }
}
