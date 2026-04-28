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
import com.github.reygnn.thrust.data.EndlessFavorite
import com.github.reygnn.thrust.data.EndlessFavoritesRepository
import com.github.reygnn.thrust.data.EndlessHighScoreRepository
import com.github.reygnn.thrust.data.HighScoreRepository
import com.github.reygnn.thrust.data.SettingsRepository
import com.github.reygnn.thrust.data.ThrustButtonSize
import com.github.reygnn.thrust.data.ThrustSide
import com.github.reygnn.thrust.data.WheelSize
import com.github.reygnn.thrust.domain.engine.PhysicsConstants
import com.github.reygnn.thrust.domain.engine.PhysicsEngine
import com.github.reygnn.thrust.domain.level.Difficulty
import com.github.reygnn.thrust.domain.level.LevelGenerator
import com.github.reygnn.thrust.domain.level.LevelRepository
import com.github.reygnn.thrust.domain.level.LevelRepositoryImpl
import com.github.reygnn.thrust.domain.level.PracticeKind
import com.github.reygnn.thrust.domain.level.PracticeLevels
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
import kotlinx.coroutines.flow.firstOrNull
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
    private val endlessHighScoreRepository: EndlessHighScoreRepository,
    private val endlessFavoritesRepository: EndlessFavoritesRepository,
    private val settingsRepository: SettingsRepository,
    private val seedSource: () -> Long = { Random.Default.nextLong() },
    private val clock: () -> Long = { System.currentTimeMillis() },
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

    /**
     * Wahr wenn der aktuell laufende Endless-Seed bereits in den Favoriten liegt.
     * Wird vom Save-Button im Pause-Overlay als "SAVED"-Anzeige genutzt; nach dem
     * Wechsel auf einen neuen Seed (advance/retry/next) zurückgesetzt.
     */
    private val _currentSeedSaved = MutableStateFlow(false)
    val currentSeedSaved: StateFlow<Boolean> = _currentSeedSaved.asStateFlow()

    private var gameLoopJob: Job? = null

    /** Aktueller Seed des laufenden Endless-Levels — Retry verwendet ihn wieder. */
    private var currentEndlessSeed: Long = 0L

    // ── Practice-Mode interne State ───────────────────────────────────────────
    /** Frame in dem der Turret im TURRETS-Mode zerstört wurde (-1 = noch nicht). */
    private var practiceTurretDestroyedFrame: Long = -1L
    private val practiceRng = Random(System.currentTimeMillis())
    /**
     * Pro Practice-Session einmal generierte LevelConfig. Wichtig für TUBE,
     * dessen Layout per Random gewürfelt wird — bleibt nach Tod gleich, sonst
     * würde der Spieler bei jedem Reset einen neuen Schlauch lernen müssen.
     */
    private var practiceConfig: LevelConfig? = null
    /**
     * Aktuelle Pod-Position im DELIVERY-Mode. Bleibt nach einem Tod gleich
     * (Spieler retried denselben Run); wechselt erst nach erfolgreicher
     * Auslieferung & Landung.
     */
    private var practicePodTarget: Vector2 = Vector2.Zero

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

    init {
        // Bootstrapping aus dem SavedStateHandle:
        // - kind  → Practice-Mode mit dem entsprechenden Drill
        // - difficulty (+ optional seed) → Endless oder EndlessFavorite
        // - sonst → Story (Level 1, Default)
        val practiceArg   = savedStateHandle.get<String>(NAV_ARG_PRACTICE_KIND)
        val practice      = practiceArg?.let { runCatching { PracticeKind.valueOf(it) }.getOrNull() }
        val difficultyArg = savedStateHandle.get<String>(NAV_ARG_DIFFICULTY)
        val seedArg       = savedStateHandle.get<Long>(NAV_ARG_SEED)
        val difficulty    = difficultyArg?.let { runCatching { Difficulty.valueOf(it) }.getOrNull() }

        when {
            practice != null -> {
                _mode.value  = GameMode.Practice(practice)
                _state.value = practiceInitialState(practice)
            }
            difficulty != null -> {
                if (seedArg != null) {
                    currentEndlessSeed = seedArg
                    _mode.value = GameMode.EndlessFavorite(difficulty, seedArg)
                } else {
                    currentEndlessSeed = seedSource()
                    _mode.value = GameMode.Endless(difficulty)
                }
                _state.value = GameState.initial(LevelGenerator.generate(difficulty, currentEndlessSeed))
                refreshSavedFlag()
            }
            // sonst: Default Story-State aus den Property-Initializern
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
                val newStreak = _endlessStreak.value + 1
                _endlessStreak.value = newStreak
                viewModelScope.launch {
                    endlessHighScoreRepository.updateStreak(mode.difficulty, newStreak)
                }
                currentEndlessSeed = seedSource()
                val nextConfig = LevelGenerator.generate(mode.difficulty, currentEndlessSeed)
                _state.value = GameState.initial(
                    config = nextConfig,
                    score  = current.score,
                    lives  = 3,
                )
                refreshSavedFlag()
                startLoop()
            }
            is GameMode.EndlessFavorite -> {
                // Favorite ist ein One-Shot-Level — kein "next", einfach zurück ans Menü.
                _navEvents.tryEmit(NavEvent.BackToMenu)
            }
            is GameMode.Practice -> {
                // In Practice gibt's kein advance — der Practice-Frame-Handler resettet
                // bei LevelComplete eigenständig. Falls dieser Pfad doch erreicht wird
                // (z.B. UI-Race), neu initialisieren statt zum Menü zu springen.
                _state.value = practiceInitialState(mode.kind)
                startLoop()
            }
        }
    }

    fun onGameOverConfirmed() { _navEvents.tryEmit(NavEvent.BackToMenu) }

    fun restartLevel() {
        val mode = _mode.value
        val difficulty = endlessDifficultyOrNull(mode)
        _state.value = if (difficulty != null) {
            GameState.initial(LevelGenerator.generate(difficulty, currentEndlessSeed))
        } else {
            GameState.initial(_state.value.levelConfig)
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

    fun startPractice(kind: PracticeKind) {
        gameLoopJob?.cancel()
        _mode.value = GameMode.Practice(kind)
        _endlessStreak.value = 0
        practiceTurretDestroyedFrame = -1L
        // Layout neu würfeln — bleibt für die Dauer der Session gleich.
        practiceConfig = PracticeLevels.configFor(kind, practiceRng)
        if (kind == PracticeKind.DELIVERY) practicePodTarget = pickPodTarget()
        _state.value = practiceInitialState(kind)
        startLoop()
    }

    private fun practiceInitialState(kind: PracticeKind): GameState {
        val cfg = practiceConfig ?: PracticeLevels.configFor(kind, practiceRng).also { practiceConfig = it }
        // Sehr hohe Lives sodass die Engine nie GameOver triggert; bei jedem
        // Tod wird die State im Practice-Frame-Handler ohnehin frisch ersetzt.
        val state = GameState.initial(cfg, lives = PRACTICE_LIVES)
        return when (kind) {
            // TUBE: Schiff hat den Pod von Anfang an am Seil — die Pendel-Physik
            // macht das Navigieren durch den Schlangenkorridor deutlich härter.
            PracticeKind.TUBE -> state.copy(
                ship    = state.ship.copy(hasPod = true),
                fuelPod = state.fuelPod.copy(
                    position    = state.ship.position + Vector2(0f, 50f),
                    isPickedUp  = true,
                    isDelivered = false,
                ),
            )
            // DELIVERY: Schiff steht auf dem Pad, Pod liegt frei an einer Random-
            // Position weiter weg. Spieler hebt ab, holt den Pod, bringt ihn zurück
            // und landet. Engine triggert LevelComplete sobald isDelivered=true und
            // sanft gelandet — dann wird im Frame-Handler ein neuer Cycle gestartet.
            PracticeKind.DELIVERY -> state.copy(
                fuelPod = state.fuelPod.copy(
                    position    = practicePodTarget,
                    isPickedUp  = false,
                    isDelivered = false,
                ),
            )
            else -> state
        }
    }

    /**
     * Random-Position für den Pod im DELIVERY-Mode. Der Pod muss
     *  - innerhalb des begehbaren Korridors liegen (nicht in/an Decke oder
     *    Boden — sonst spawn auf einer Wand oder unterhalb des Bodens, was
     *    nicht erreichbar ist),
     *  - mindestens 1000 Einheiten vom Pad und 600 vom Schiff entfernt.
     *
     * Die DELIVERY-Arena hat innen Decke um y≈260 und Boden um y≈1700; mit
     * Buffer bleibt der Pod sicher in y∈[350, 1500].
     */
    private fun pickPodTarget(): Vector2 {
        val cfg = practiceConfig ?: return Vector2.Zero
        val padCenter = cfg.landingPad.center
        val shipSpawn = cfg.shipStart
        val xMargin = 350f
        val yMin    = 350f
        val yMax    = cfg.worldHeight - 500f
        repeat(20) {
            val x = xMargin + practiceRng.nextFloat() * (cfg.worldWidth - 2f * xMargin)
            val y = yMin    + practiceRng.nextFloat() * (yMax - yMin)
            val pos = Vector2(x, y)
            if ((pos - padCenter).length() > 1000f && (pos - shipSpawn).length() > 600f) return pos
        }
        return Vector2(cfg.worldWidth / 2f, (yMin + yMax) / 2f)
    }

    fun startEndlessGame(difficulty: Difficulty) {
        gameLoopJob?.cancel()
        _mode.value = GameMode.Endless(difficulty)
        _endlessStreak.value = 0
        currentEndlessSeed = seedSource()
        _state.value = GameState.initial(LevelGenerator.generate(difficulty, currentEndlessSeed))
        refreshSavedFlag()
        startLoop()
    }

    /**
     * Game Over → "Den Level nochmal spielen": gleicher Seed, frischer Run.
     * Funktioniert sowohl in Endless als auch in EndlessFavorite — beide spielen
     * den aktuellen Seed neu, Streak wird (sofern getrackt) zurückgesetzt.
     */
    fun retryEndlessLevel() {
        val difficulty = endlessDifficultyOrNull(_mode.value)
            ?: error("retryEndlessLevel called outside any Endless mode")
        gameLoopJob?.cancel()
        _endlessStreak.value = 0
        _state.value = GameState.initial(LevelGenerator.generate(difficulty, currentEndlessSeed))
        startLoop()
    }

    /** Game Over → "Nächstes random Level": neuer Seed, frischer Run, gleiche Difficulty. */
    fun nextEndlessLevel() {
        val mode = _mode.value
        require(mode is GameMode.Endless) { "nextEndlessLevel called outside Endless (regular) mode" }
        gameLoopJob?.cancel()
        _endlessStreak.value = 0
        currentEndlessSeed = seedSource()
        _state.value = GameState.initial(LevelGenerator.generate(mode.difficulty, currentEndlessSeed))
        refreshSavedFlag()
        startLoop()
    }

    /**
     * Speichert den aktuell laufenden Endless-Seed in den Favoriten. No-op wenn
     * nicht im Endless-Modus oder bereits gespeichert (Repository-seitig idempotent).
     */
    fun saveCurrentAsFavorite() {
        val difficulty = endlessDifficultyOrNull(_mode.value) ?: return
        val seed = currentEndlessSeed
        val savedAt = clock()
        viewModelScope.launch {
            endlessFavoritesRepository.addFavorite(EndlessFavorite(difficulty, seed, savedAt))
            _currentSeedSaved.value = true
        }
    }

    private fun endlessDifficultyOrNull(mode: GameMode): Difficulty? = when (mode) {
        is GameMode.Endless         -> mode.difficulty
        is GameMode.EndlessFavorite -> mode.difficulty
        GameMode.Story              -> null
        is GameMode.Practice        -> null
    }

    private fun refreshSavedFlag() {
        val difficulty = endlessDifficultyOrNull(_mode.value) ?: run {
            _currentSeedSaved.value = false
            return
        }
        val seed = currentEndlessSeed
        viewModelScope.launch {
            val favorites = endlessFavoritesRepository.getFavorites().firstOrNull() ?: emptyList()
            _currentSeedSaved.value = favorites.any { it.difficulty == difficulty && it.seed == seed }
        }
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

                val mode = _mode.value
                val effectiveGun = effectivePlayerGun(mode)
                val nextRaw = physicsEngine.update(
                    state            = current,
                    input            = _input.value,
                    playerGunEnabled = effectiveGun,
                )

                val next = when {
                    // Endless-Spezialfall: in dem Frame, in dem das Schiff wiederbelebt wird,
                    // verwerfen wir den engine-eigenen In-Place-Respawn und spielen das Level
                    // komplett frisch ein (Pod, Türme, Geschosse alles zurückgesetzt).
                    mode is GameMode.Endless &&
                            !current.ship.isAlive && nextRaw.ship.isAlive &&
                            nextRaw.phase == GamePhase.Playing ->
                        GameState.initial(current.levelConfig, score = nextRaw.score, lives = nextRaw.lives)

                    mode is GameMode.Practice ->
                        handlePracticeFrame(mode.kind, current, nextRaw)

                    else -> nextRaw
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

    private fun effectivePlayerGun(mode: GameMode): Boolean =
        if (mode is GameMode.Practice && mode.kind == PracticeKind.TURRETS) true
        else playerGunEnabled.value

    /**
     * Practice-spezifische Nachbearbeitung pro Frame:
     * - Tod → Level mit gleichem Cycle-State frisch initialisieren
     * - LevelComplete in DELIVERY → neuer Pod-Target, frischer Cycle
     * - TUBE: bei Annäherung an die rechte Wand zurück an den Start teleportieren
     * - TURRETS: 2 s nach Abschuss neuen Turret an neuer Position einsetzen
     */
    private fun handlePracticeFrame(kind: PracticeKind, current: GameState, next: GameState): GameState {
        // Tod → frischer Level-Reset (Cycle-State bleibt: gleicher Pod-Target etc.)
        val justDied = current.ship.isAlive && !next.ship.isAlive
        if (justDied) {
            practiceTurretDestroyedFrame = -1L
            return practiceInitialState(kind)
        }
        // Erfolgreiche Landung mit Pod (DELIVERY) → neuer Pod-Target, neuer Cycle
        if (next.phase is GamePhase.LevelComplete) {
            practiceTurretDestroyedFrame = -1L
            if (kind == PracticeKind.DELIVERY) practicePodTarget = pickPodTarget()
            return practiceInitialState(kind)
        }

        var working = next
        when (kind) {
            PracticeKind.TUBE -> {
                val w = working.levelConfig.worldWidth
                if (working.ship.position.x > w - 200f && working.ship.velocity.x > 0f) {
                    // Voller Reset, damit der Pod am Seil mitgenommen wird (sonst
                    // bleibt er am rechten Wand-Rand zurück und das Seil wird
                    // physikalisch unsinnig gestreckt).
                    return practiceInitialState(kind)
                }
            }
            PracticeKind.DELIVERY -> { /* nur Tod/LevelComplete-Reset oben */ }
            PracticeKind.TURRETS -> {
                val justDestroyed = current.turrets.zip(working.turrets)
                    .any { (c, w) -> !c.isDestroyed && w.isDestroyed }
                if (justDestroyed) {
                    practiceTurretDestroyedFrame = working.frameCount
                }
                if (practiceTurretDestroyedFrame >= 0 &&
                    working.frameCount - practiceTurretDestroyedFrame >= PRACTICE_RESPAWN_FRAMES &&
                    working.turrets.isNotEmpty()
                ) {
                    val originalCfg = working.turrets.first().config
                    val newPos = randomArenaPosition(working.levelConfig)
                    val newTurret = Turret(
                        config         = originalCfg.copy(position = newPos),
                        cooldownFrames = originalCfg.firePeriodFrames,
                        isDestroyed    = false,
                    )
                    working = working.copy(turrets = listOf(newTurret))
                    practiceTurretDestroyedFrame = -1L
                }
            }
        }
        return working
    }

    /**
     * Zufällige Position innerhalb der Arena mit Sicherheitsabstand zu den
     * Wänden. Funktioniert für Practice-Levels die ein offenes Innenraum-
     * Layout haben (DOCKING, TURRETS).
     */
    private fun randomArenaPosition(cfg: LevelConfig): Vector2 {
        val margin = 250f
        val x = margin + practiceRng.nextFloat() * (cfg.worldWidth  - 2f * margin)
        val y = margin + practiceRng.nextFloat() * (cfg.worldHeight - 2f * margin)
        return Vector2(x, y)
    }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
    }

    companion object {
        const val NAV_ARG_DIFFICULTY    = "difficulty"
        const val NAV_ARG_SEED          = "seed"
        const val NAV_ARG_PRACTICE_KIND = "kind"

        /** Arbiträr hoch — verhindert dass der Engine in Practice GameOver triggert. */
        private const val PRACTICE_LIVES = 999_999

        /** 2 Sekunden bei 60 fps — Pod- bzw. Turret-Respawn-Delay in Practice. */
        private const val PRACTICE_RESPAWN_FRAMES = 120L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ThrustApplication
                GameViewModel(
                    physicsEngine              = PhysicsEngine(),
                    levelRepository            = LevelRepositoryImpl(),
                    highScoreRepository        = app.highScoreRepository,
                    endlessHighScoreRepository = app.endlessHighScoreRepository,
                    endlessFavoritesRepository = app.endlessFavoritesRepository,
                    settingsRepository         = app.settingsRepository,
                    savedStateHandle           = createSavedStateHandle(),
                )
            }
        }
    }
}
