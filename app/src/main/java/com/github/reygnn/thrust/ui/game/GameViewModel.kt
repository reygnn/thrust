package com.github.reygnn.thrust.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.reygnn.thrust.ThrustApplication
import com.github.reygnn.thrust.data.ControlMode
import com.github.reygnn.thrust.data.HighScoreRepository
import com.github.reygnn.thrust.data.SettingsRepository
import com.github.reygnn.thrust.data.ThrustSide
import com.github.reygnn.thrust.data.WheelSize
import com.github.reygnn.thrust.domain.engine.PhysicsConstants
import com.github.reygnn.thrust.domain.engine.PhysicsEngine
import com.github.reygnn.thrust.domain.level.Difficulty
import com.github.reygnn.thrust.domain.level.LevelGenerator
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
import kotlin.random.Random

sealed interface NavEvent {
    data object BackToMenu : NavEvent
}

class GameViewModel(
    private val physicsEngine: PhysicsEngine,
    private val levelRepository: LevelRepository,
    private val highScoreRepository: HighScoreRepository,
    private val settingsRepository: SettingsRepository,
    private val seedSource: () -> Long = { Random.Default.nextLong() },
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _mode = MutableStateFlow<GameMode>(GameMode.Story)
    val mode: StateFlow<GameMode> = _mode.asStateFlow()

    private val _state = MutableStateFlow(GameState.initial(levelRepository.getLevel(1)))
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _navEvents = MutableSharedFlow<NavEvent>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<NavEvent> = _navEvents.asSharedFlow()

    private val _input = MutableStateFlow(InputState())

    private val _endlessStreak = MutableStateFlow(0)
    val endlessStreak: StateFlow<Int> = _endlessStreak.asStateFlow()

    private var gameLoopJob: Job? = null

    /** Aktueller Seed des laufenden Endless-Levels — Retry verwendet ihn wieder. */
    private var currentEndlessSeed: Long = 0L

    val playerGunEnabled: StateFlow<Boolean> = settingsRepository.playerGunEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val controlMode: StateFlow<ControlMode> = settingsRepository.controlMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ControlMode.BUTTONS)

    val thrustSide: StateFlow<ThrustSide> = settingsRepository.thrustSide
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThrustSide.RIGHT)

    val wheelSize: StateFlow<WheelSize> = settingsRepository.wheelSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WheelSize.MEDIUM)

    init {
        // Wenn die Navigation eine Difficulty mitgegeben hat, starten wir direkt in
        // Endless. Sonst (Default) Story-Mode mit Level 1.
        val difficultyArg = savedStateHandle.get<String>(NAV_ARG_DIFFICULTY)
        val difficulty = difficultyArg?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() }
        if (difficulty != null) {
            currentEndlessSeed = seedSource()
            _mode.value  = GameMode.Endless(difficulty)
            _state.value = GameState.initial(LevelGenerator.generate(difficulty, currentEndlessSeed))
        }
        startLoop()
    }

    fun onRotateLeft(pressed: Boolean)  { _input.update { it.copy(rotateLeft  = pressed) } }
    fun onRotateRight(pressed: Boolean) { _input.update { it.copy(rotateRight = pressed) } }
    fun onThrust(pressed: Boolean)      { _input.update { it.copy(thrust      = pressed) } }
    fun onFire(pressed: Boolean)        { _input.update { it.copy(shoot       = pressed) } }

    fun onTargetAngleChange(angle: Float?) { _input.update { it.copy(targetAngle = angle) } }

    fun onFireTriggered() {
        _input.update { it.copy(shoot = true) }
        viewModelScope.launch {
            delay(PhysicsConstants.FRAME_DELAY_MS + 1)
            _input.update { it.copy(shoot = false) }
        }
    }

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
        when (val mode = _mode.value) {
            GameMode.Story -> {
                viewModelScope.launch {
                    highScoreRepository.updateHighScore(current.currentLevel, current.score)
                }
                val nextId = current.currentLevel + 1
                if (nextId > levelRepository.totalLevels) {
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
            is GameMode.Endless -> {
                _endlessStreak.update { it + 1 }
                currentEndlessSeed = seedSource()
                val nextConfig = LevelGenerator.generate(mode.difficulty, currentEndlessSeed)
                _state.value = GameState.initial(
                    config = nextConfig,
                    score  = current.score,
                    lives  = 3,
                )
                startLoop()
            }
        }
    }

    fun onGameOverConfirmed() { _navEvents.tryEmit(NavEvent.BackToMenu) }

    fun restartLevel() {
        val mode = _mode.value
        if (mode is GameMode.Endless) {
            _state.value = GameState.initial(LevelGenerator.generate(mode.difficulty, currentEndlessSeed))
        } else {
            _state.value = GameState.initial(_state.value.levelConfig)
        }
        startLoop()
    }

    fun startNewGame() {
        gameLoopJob?.cancel()
        _mode.value = GameMode.Story
        _endlessStreak.value = 0
        _state.value = GameState.initial(levelRepository.getLevel(1))
        startLoop()
    }

    fun startEndlessGame(difficulty: Difficulty) {
        gameLoopJob?.cancel()
        _mode.value = GameMode.Endless(difficulty)
        _endlessStreak.value = 0
        currentEndlessSeed = seedSource()
        _state.value = GameState.initial(LevelGenerator.generate(difficulty, currentEndlessSeed))
        startLoop()
    }

    /** Game Over → "Den Level nochmal spielen": gleicher Seed, frischer Run. */
    fun retryEndlessLevel() {
        val mode = _mode.value
        require(mode is GameMode.Endless) { "retryEndlessLevel called outside Endless mode" }
        gameLoopJob?.cancel()
        _endlessStreak.value = 0
        _state.value = GameState.initial(LevelGenerator.generate(mode.difficulty, currentEndlessSeed))
        startLoop()
    }

    /** Game Over → "Nächstes random Level": neuer Seed, frischer Run, gleiche Difficulty. */
    fun nextEndlessLevel() {
        val mode = _mode.value
        require(mode is GameMode.Endless) { "nextEndlessLevel called outside Endless mode" }
        gameLoopJob?.cancel()
        _endlessStreak.value = 0
        currentEndlessSeed = seedSource()
        _state.value = GameState.initial(LevelGenerator.generate(mode.difficulty, currentEndlessSeed))
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

    private fun startLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            while (isActive) {
                delay(PhysicsConstants.FRAME_DELAY_MS)
                val current = _state.value
                if (current.phase != GamePhase.Playing) break

                val nextRaw = physicsEngine.update(
                    state            = current,
                    input            = _input.value,
                    playerGunEnabled = playerGunEnabled.value,
                )

                // Endless-Spezialfall: in dem Frame, in dem das Schiff wiederbelebt wird,
                // verwerfen wir den engine-eigenen In-Place-Respawn und spielen das Level
                // komplett frisch ein (Pod, Türme, Geschosse alles zurückgesetzt). So ist
                // ein Tod in Endless ein echtes "neues Game mit selbem Level".
                val justRevived = !current.ship.isAlive && nextRaw.ship.isAlive
                val next = if (_mode.value is GameMode.Endless && justRevived && nextRaw.phase == GamePhase.Playing) {
                    GameState.initial(
                        config = current.levelConfig,
                        score  = nextRaw.score,
                        lives  = nextRaw.lives,
                    )
                } else {
                    nextRaw
                }
                _state.value = next

                if (next.phase != GamePhase.Playing) {
                    if (next.phase == GamePhase.GameOver && _mode.value == GameMode.Story) {
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

    companion object {
        const val NAV_ARG_DIFFICULTY = "difficulty"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ThrustApplication
                GameViewModel(
                    physicsEngine       = PhysicsEngine(),
                    levelRepository     = LevelRepositoryImpl(),
                    highScoreRepository = app.highScoreRepository,
                    settingsRepository  = app.settingsRepository,
                    savedStateHandle    = createSavedStateHandle(),
                )
            }
        }
    }
}
