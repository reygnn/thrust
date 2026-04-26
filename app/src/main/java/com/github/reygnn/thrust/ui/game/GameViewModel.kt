package com.github.reygnn.thrust.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.reygnn.thrust.ThrustApplication
import com.github.reygnn.thrust.data.HighScoreRepository
import com.github.reygnn.thrust.data.SettingsRepository
import com.github.reygnn.thrust.domain.engine.PhysicsConstants
import com.github.reygnn.thrust.domain.engine.PhysicsEngine
import com.github.reygnn.thrust.domain.level.LevelRepository
import com.github.reygnn.thrust.domain.level.LevelRepositoryImpl
import com.github.reygnn.thrust.domain.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ── Navigation-Events ─────────────────────────────────────────────────────────

sealed interface NavEvent {
    data object BackToMenu : NavEvent
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class GameViewModel(
    private val physicsEngine: PhysicsEngine,
    private val levelRepository: LevelRepository,
    private val highScoreRepository: HighScoreRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        GameState.initial(levelRepository.getLevel(1))
    )
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _navEvents = MutableSharedFlow<NavEvent>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<NavEvent> = _navEvents.asSharedFlow()

    private val _input = MutableStateFlow(InputState())

    private var gameLoopJob: Job? = null

    /** Gespiegelt aus den Settings – wird pro Frame ans PhysicsEngine übergeben. */
    val playerGunEnabled: StateFlow<Boolean> = settingsRepository.playerGunEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        startLoop()
    }

    // ── Öffentliche API ───────────────────────────────────────────────────────

    fun setInput(input: InputState) { _input.value = input }

    fun onRotateLeft(pressed: Boolean)  { _input.update { it.copy(rotateLeft  = pressed) } }
    fun onRotateRight(pressed: Boolean) { _input.update { it.copy(rotateRight = pressed) } }
    fun onThrust(pressed: Boolean)      { _input.update { it.copy(thrust      = pressed) } }
    /** FIRE: schießt von der Raketenspitze in Blickrichtung. */
    fun onFire(pressed: Boolean)        { _input.update { it.copy(shoot       = pressed) } }

    fun togglePause() {
        val isPlaying = _state.value.phase == GamePhase.Playing
        if (isPlaying) {
            gameLoopJob?.cancel()
            _state.update { it.copy(phase = GamePhase.Paused) }
        } else if (_state.value.phase == GamePhase.Paused) {
            _state.update { it.copy(phase = GamePhase.Playing) }
            startLoop()
        }
    }

    fun advanceToNextLevel() {
        val current = _state.value
        val nextId  = current.currentLevel + 1
        if (nextId > levelRepository.totalLevels) {
            viewModelScope.launch {
                highScoreRepository.updateHighScore(current.currentLevel, current.score)
            }
            _navEvents.tryEmit(NavEvent.BackToMenu)
        } else {
            _state.value = GameState.initial(
                config = levelRepository.getLevel(nextId),
                score  = current.score,
                lives  = current.lives,
            )
            startLoop()
        }
    }

    fun onGameOverConfirmed() {
        val s = _state.value
        viewModelScope.launch { highScoreRepository.updateHighScore(s.currentLevel, s.score) }
        _navEvents.tryEmit(NavEvent.BackToMenu)
    }

    fun restartLevel() {
        _state.value = GameState.initial(_state.value.levelConfig)
        startLoop()
    }

    fun startNewGame() {
        gameLoopJob?.cancel()
        _state.value = GameState.initial(levelRepository.getLevel(1))
        startLoop()
    }

    fun pauseForBackground() {
        if (_state.value.phase == GamePhase.Playing) {
            _state.update { it.copy(phase = GamePhase.Paused) }
            gameLoopJob?.cancel()
        }
    }

    fun pauseGame()              = pauseForBackground()
    fun resumeGame() {
        if (_state.value.phase == GamePhase.Paused) {
            _state.update { it.copy(phase = GamePhase.Playing) }
            startLoop()
        }
    }
    fun onLevelCompleteConfirm() = advanceToNextLevel()
    fun onGameOverConfirm()      = onGameOverConfirmed()

    // ── Game Loop ─────────────────────────────────────────────────────────────

    private fun startLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            while (isActive) {
                delay(PhysicsConstants.FRAME_DELAY_MS)
                val current = _state.value
                if (current.phase != GamePhase.Playing) break

                val next = physicsEngine.update(
                    state            = current,
                    input            = _input.value,
                    playerGunEnabled = playerGunEnabled.value,
                )
                _state.value = next

                if (next.phase != GamePhase.Playing) {
                    if (next.phase == GamePhase.GameOver) {
                        highScoreRepository.updateHighScore(next.currentLevel, next.score)
                    }
                    break
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ThrustApplication
                GameViewModel(
                    physicsEngine       = PhysicsEngine(),
                    levelRepository     = LevelRepositoryImpl(),
                    highScoreRepository = app.highScoreRepository,
                    settingsRepository  = app.settingsRepository,
                )
            }
        }
    }
}
