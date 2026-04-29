package com.github.reygnn.thrust.ui.game

import app.cash.turbine.test
import com.github.reygnn.thrust.MainDispatcherRule
import com.github.reygnn.thrust.data.ControlMode
import com.github.reygnn.thrust.data.HighScoreRepository
import com.github.reygnn.thrust.data.SettingsRepository
import com.github.reygnn.thrust.data.ThrustSide
import com.github.reygnn.thrust.data.EndlessFavorite
import com.github.reygnn.thrust.data.EndlessFavoritesRepository
import com.github.reygnn.thrust.data.EndlessHighScoreRepository
import com.github.reygnn.thrust.data.ThrustButtonSize
import com.github.reygnn.thrust.data.WheelSize
import com.github.reygnn.thrust.domain.engine.PhysicsConstants
import com.github.reygnn.thrust.domain.engine.PhysicsEngine
import com.github.reygnn.thrust.domain.level.Difficulty
import com.github.reygnn.thrust.domain.level.LevelGenerator
import com.github.reygnn.thrust.domain.level.LevelRepository
import com.github.reygnn.thrust.domain.level.LevelRepositoryImpl
import com.github.reygnn.thrust.domain.level.PracticeKind
import com.github.reygnn.thrust.domain.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val levelRepo: LevelRepository = LevelRepositoryImpl()
    private val highScoreRepo              = mockk<HighScoreRepository>()
    private val endlessHighScoreRepo       = mockk<EndlessHighScoreRepository>()
    private val endlessFavoritesRepo       = mockk<EndlessFavoritesRepository>()
    private val settingsRepo               = mockk<SettingsRepository>()
    private val physicsEngine              = PhysicsEngine()

    private fun stubRepo() {
        every { highScoreRepo.getHighScores() } returns flowOf(emptyMap())
        coEvery { highScoreRepo.updateHighScore(any(), any()) } just Runs
        every { endlessHighScoreRepo.getStreaks() } returns flowOf(emptyMap())
        coEvery { endlessHighScoreRepo.updateStreak(any(), any()) } just Runs
        every { endlessFavoritesRepo.getFavorites() } returns flowOf(emptyList())
        coEvery { endlessFavoritesRepo.addFavorite(any()) } just Runs
        coEvery { endlessFavoritesRepo.removeFavorite(any()) } just Runs
        every { settingsRepo.playerGunEnabled } returns flowOf(false)
        // SettingsRepository hat mehrere Felder, die im VM-Konstruktor sofort
        // via stateIn() konsumiert werden. Alle müssen gestubbt sein, sonst
        // wirft MockK beim Anlegen des ViewModels und ALLE Tests reißen
        // gleichzeitig durch (haben wir schon einmal erlebt).
        every { settingsRepo.controlMode }      returns flowOf(ControlMode.BUTTONS)
        every { settingsRepo.thrustSide }       returns flowOf(ThrustSide.RIGHT)
        every { settingsRepo.wheelSize }        returns flowOf(WheelSize.MEDIUM)
        every { settingsRepo.thrustButtonSize } returns flowOf(ThrustButtonSize.MEDIUM)
    }

    private fun buildVm(
        engine: PhysicsEngine = physicsEngine,
        seedSource: () -> Long = { 0L },
        clock: () -> Long = { 0L },
        savedStateHandle: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle(),
    ) = GameViewModel(
        physicsEngine              = engine,
        levelRepository            = levelRepo,
        highScoreRepository        = highScoreRepo,
        endlessHighScoreRepository = endlessHighScoreRepo,
        endlessFavoritesRepository = endlessFavoritesRepo,
        settingsRepository         = settingsRepo,
        seedSource                 = seedSource,
        clock                      = clock,
        savedStateHandle           = savedStateHandle,
    )

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test fun `initial state is level 1 Playing`() = runTest(mainDispatcherRule.dispatcher) {
        stubRepo()
        val vm = buildVm()

        vm.state.test {
            val s = awaitItem()
            assertEquals(1, s.currentLevel)
            assertEquals(GamePhase.Playing, s.phase)
            assertEquals(3, s.lives)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `initial ship is at level 1 start position`() = runTest(mainDispatcherRule.dispatcher) {
        stubRepo()
        val vm = buildVm()
        val config = levelRepo.getLevel(1)

        vm.state.test {
            val s = awaitItem()
            assertEquals(config.shipStart.x, s.ship.position.x, 1f)
            assertEquals(config.shipStart.y, s.ship.position.y, 1f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Game loop advances state ──────────────────────────────────────────────

    @Test fun `one frame advances frameCount to 1`() = runTest(mainDispatcherRule.dispatcher) {
        stubRepo()
        val vm = buildVm()

        advanceTimeBy(PhysicsConstants.FRAME_DELAY_MS + 1)

        assertTrue("frameCount must be >= 1", vm.state.value.frameCount >= 1L)
    }

    @Test fun `ten frames advance ship position downward (gravity)`() = runTest(mainDispatcherRule.dispatcher) {
        stubRepo()
        val vm = buildVm()
        val startY = vm.state.value.ship.position.y

        advanceTimeBy((PhysicsConstants.FRAME_DELAY_MS + 1) * 10)

        assertTrue("ship falls due to gravity", vm.state.value.ship.position.y > startY)
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Test fun `thrust input moves ship upward when angle is 0`() = runTest(mainDispatcherRule.dispatcher) {
        stubRepo()
        val vm = buildVm()
        val startY = vm.state.value.ship.position.y

        vm.onThrust(true)
        advanceTimeBy((PhysicsConstants.FRAME_DELAY_MS + 1) * 20)
        vm.onThrust(false)

        assertTrue("thrust lifts ship", vm.state.value.ship.position.y < startY)
    }

    @Test fun `rotate-right changes ship angle positively`() = runTest(mainDispatcherRule.dispatcher) {
        stubRepo()
        val vm = buildVm()

        vm.onRotateRight(true)
        advanceTimeBy(PhysicsConstants.FRAME_DELAY_MS + 1)
        vm.onRotateRight(false)

        assertTrue("angle > 0 after right-rotation", vm.state.value.ship.angle > 0f)
    }

    // ── Pause / Resume ───────────────────────────────────────────────────────

    @Test fun `pauseGame sets phase to Paused`() = runTest(mainDispatcherRule.dispatcher) {
        stubRepo()
        val vm = buildVm()

        vm.pauseGame()

        assertEquals(GamePhase.Paused, vm.state.value.phase)
    }

    @Test fun `resumeGame sets phase back to Playing`() = runTest(mainDispatcherRule.dispatcher) {
        stubRepo()
        val vm = buildVm()

        vm.pauseGame()
        vm.resumeGame()

        assertEquals(GamePhase.Playing, vm.state.value.phase)
    }

    @Test fun `game loop does NOT advance frames while paused`() = runTest(mainDispatcherRule.dispatcher) {
        stubRepo()
        val vm = buildVm()
        advanceTimeBy(PhysicsConstants.FRAME_DELAY_MS + 1)
        val framesBefore = vm.state.value.frameCount

        vm.pauseGame()
        advanceTimeBy((PhysicsConstants.FRAME_DELAY_MS + 1) * 10)

        assertEquals("no new frames while paused", framesBefore, vm.state.value.frameCount)
    }

    // ── startNewGame resets state ─────────────────────────────────────────────

    @Test fun `startNewGame resets score and lives`() = runTest(mainDispatcherRule.dispatcher) {
        stubRepo()
        val vm = buildVm()

        advanceTimeBy((PhysicsConstants.FRAME_DELAY_MS + 1) * 5)
        vm.startNewGame()

        assertEquals(0, vm.state.value.score)
        assertEquals(3, vm.state.value.lives)
        assertEquals(1, vm.state.value.currentLevel)
        assertEquals(GamePhase.Playing, vm.state.value.phase)
    }

    // ── NavEvents ─────────────────────────────────────────────────────────────

    @Test fun `onGameOverConfirm emits BackToMenu nav event`() = runTest(mainDispatcherRule.dispatcher) {
        stubRepo()
        val vm = buildVm()

        vm.navEvents.test {
            vm.onGameOverConfirm()
            assertEquals(NavEvent.BackToMenu, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `onLevelCompleteConfirm on last level emits BackToMenu`() = runTest(mainDispatcherRule.dispatcher) {
        stubRepo()
        val level4Config = levelRepo.getLevel(4)
        val vm4 = GameViewModel(
            physicsEngine              = physicsEngine,
            levelRepository            = object : LevelRepository {
                override fun getLevel(id: Int) = level4Config
                override val totalLevels: Int  = 4
            },
            highScoreRepository        = highScoreRepo,
            endlessHighScoreRepository = endlessHighScoreRepo,
            endlessFavoritesRepository = endlessFavoritesRepo,
            settingsRepository         = settingsRepo,
        )

        vm4.navEvents.test {
            vm4.onLevelCompleteConfirm()
            assertEquals(NavEvent.BackToMenu, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Highscore-Persistierung ──────────────────────────────────────────────

    @Test fun `onLevelCompleteConfirm persists high score for current level`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            val vm = buildVm()

            vm.onLevelCompleteConfirm()
            advanceTimeBy(100)

            coVerify { highScoreRepo.updateHighScore(1, any()) }
        }

    @Test fun `onGameOverConfirm does NOT save high score (already saved in loop)`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            val vm = buildVm()

            vm.onGameOverConfirm()
            advanceTimeBy(100)

            coVerify(exactly = 0) { highScoreRepo.updateHighScore(any(), any()) }
        }

    @Test fun `loop persists high score on GameOver in Story mode`() =
        // CLAUDE.md verlangt: GameOver schreibt genau einmal aus dem Loop.
        // Bisher nur indirekt durch das Negativ-Pendant `onGameOverConfirm
        // does NOT save` getestet — hier explizit die positive Seite.
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            val mockEngine = mockk<PhysicsEngine>()
            every { mockEngine.update(any(), any(), any()) } answers {
                val s = firstArg<GameState>()
                s.copy(phase = GamePhase.GameOver, score = 1234)
            }
            val vm = buildVm(engine = mockEngine)

            advanceTimeBy(PhysicsConstants.FRAME_DELAY_MS + 1)

            assertEquals(GamePhase.GameOver, vm.state.value.phase)
            coVerify(exactly = 1) { highScoreRepo.updateHighScore(1, 1234) }
        }

    @Test fun `loop does NOT persist Story high score on GameOver in Endless mode`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            val mockEngine = mockk<PhysicsEngine>()
            every { mockEngine.update(any(), any(), any()) } answers {
                val s = firstArg<GameState>()
                s.copy(phase = GamePhase.GameOver, score = 1234)
            }
            val vm = buildVm(engine = mockEngine, seedSource = { 99L })
            vm.startEndlessGame(Difficulty.MEDIUM)

            advanceTimeBy(PhysicsConstants.FRAME_DELAY_MS + 1)

            coVerify(exactly = 0) { highScoreRepo.updateHighScore(any(), any()) }
        }

    // ── Endless mode ──────────────────────────────────────────────────────────

    @Test fun `startEndlessGame switches mode and loads procedural level`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            val vm = buildVm(seedSource = { 1234L })

            vm.startEndlessGame(Difficulty.MEDIUM)

            assertEquals(GameMode.Endless(Difficulty.MEDIUM), vm.mode.value)
            assertEquals(LevelGenerator.ENDLESS_LEVEL_ID, vm.state.value.currentLevel)
            assertEquals(Difficulty.MEDIUM.gravity, vm.state.value.levelConfig.gravity, 0.0001f)
            assertEquals(0, vm.endlessStreak.value)
        }

    @Test fun `retryEndlessLevel reuses same seed (identical level layout)`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            val vm = buildVm(seedSource = { 4242L })
            vm.startEndlessGame(Difficulty.ROOKIE)
            val firstTerrain = vm.state.value.levelConfig.terrain

            vm.retryEndlessLevel()

            assertEquals(firstTerrain, vm.state.value.levelConfig.terrain)
        }

    @Test fun `nextEndlessLevel uses a fresh seed (different layout)`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            // Sequenz von Seeds, damit zwei Aufrufe wirklich unterschiedlich sind.
            val seeds = ArrayDeque(listOf(11L, 22L))
            val vm = buildVm(seedSource = { seeds.removeFirst() })
            vm.startEndlessGame(Difficulty.MEDIUM)
            val firstTerrain = vm.state.value.levelConfig.terrain

            vm.nextEndlessLevel()

            assertNotEquals(firstTerrain, vm.state.value.levelConfig.terrain)
        }

    @Test fun `advanceToNextLevel in endless increments streak and resets lives`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            val seeds = ArrayDeque(listOf(1L, 2L, 3L))
            val vm = buildVm(seedSource = { seeds.removeFirst() })
            vm.startEndlessGame(Difficulty.ROOKIE)

            vm.onLevelCompleteConfirm()

            assertEquals(1, vm.endlessStreak.value)
            assertEquals(3, vm.state.value.lives)
            assertEquals(LevelGenerator.ENDLESS_LEVEL_ID, vm.state.value.currentLevel)
        }

    @Test fun `advanceToNextLevel in endless does NOT persist a level highscore`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            val seeds = ArrayDeque(listOf(1L, 2L))
            val vm = buildVm(seedSource = { seeds.removeFirst() })
            vm.startEndlessGame(Difficulty.ROOKIE)

            vm.onLevelCompleteConfirm()
            advanceTimeBy(100)

            coVerify(exactly = 0) { highScoreRepo.updateHighScore(any(), any()) }
        }

    @Test fun `advanceToNextLevel in endless persists streak per difficulty`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            val seeds = ArrayDeque(listOf(1L, 2L, 3L))
            val vm = buildVm(seedSource = { seeds.removeFirst() })
            vm.startEndlessGame(Difficulty.MEDIUM)

            vm.onLevelCompleteConfirm()
            advanceTimeBy(100)

            coVerify { endlessHighScoreRepo.updateStreak(Difficulty.MEDIUM, 1) }
        }

    @Test fun `saveCurrentAsFavorite stores difficulty and current seed`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            val vm = buildVm(seedSource = { 999L }, clock = { 1_700_000_000_000L })
            vm.startEndlessGame(Difficulty.IMPOSSIBLE)

            vm.saveCurrentAsFavorite()
            advanceTimeBy(50)

            coVerify { endlessFavoritesRepo.addFavorite(EndlessFavorite(Difficulty.IMPOSSIBLE, 999L, 1_700_000_000_000L)) }
        }

    @Test fun `saveCurrentAsFavorite is no-op in story mode`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            val vm = buildVm()

            vm.saveCurrentAsFavorite()
            advanceTimeBy(50)

            coVerify(exactly = 0) { endlessFavoritesRepo.addFavorite(any()) }
        }

    @Test fun `EndlessFavorite mode is restored from savedStateHandle args`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            val handle = androidx.lifecycle.SavedStateHandle().apply {
                set(GameViewModel.NAV_ARG_DIFFICULTY, Difficulty.MEDIUM.name)
                set(GameViewModel.NAV_ARG_SEED, 12345L)
            }
            val vm = buildVm(savedStateHandle = handle)

            assertEquals(GameMode.EndlessFavorite(Difficulty.MEDIUM, 12345L), vm.mode.value)
        }

    @Test fun `advanceToNextLevel in EndlessFavorite emits BackToMenu and skips streak`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            val handle = androidx.lifecycle.SavedStateHandle().apply {
                set(GameViewModel.NAV_ARG_DIFFICULTY, Difficulty.ROOKIE.name)
                set(GameViewModel.NAV_ARG_SEED, 7L)
            }
            val vm = buildVm(savedStateHandle = handle)

            vm.navEvents.test {
                vm.onLevelCompleteConfirm()
                assertEquals(NavEvent.BackToMenu, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 0) { endlessHighScoreRepo.updateStreak(any(), any()) }
        }

    @Test fun `retryEndlessLevel works in EndlessFavorite (same seed)`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            val handle = androidx.lifecycle.SavedStateHandle().apply {
                set(GameViewModel.NAV_ARG_DIFFICULTY, Difficulty.MEDIUM.name)
                set(GameViewModel.NAV_ARG_SEED, 4242L)
            }
            val vm = buildVm(savedStateHandle = handle)
            val firstTerrain = vm.state.value.levelConfig.terrain

            vm.retryEndlessLevel()

            assertEquals(firstTerrain, vm.state.value.levelConfig.terrain)
        }

    // ── Practice DELIVERY: Pod-Platzierung ───────────────────────────────────

    private fun deliveryHandle() = androidx.lifecycle.SavedStateHandle().apply {
        set(GameViewModel.NAV_ARG_PRACTICE_KIND, PracticeKind.DELIVERY.name)
    }

    /**
     * Baut ein DELIVERY-VM und pausiert sofort die Game-Loop. Wichtig: der
     * Practice-Mode hat lives=999_999 und kein GameOver — ohne Pause würde
     * runTest beim Test-Ende ewig versuchen, die Endlosschleife zu drainen.
     */
    private fun buildDeliveryVmPaused(): GameViewModel {
        val vm = buildVm(savedStateHandle = deliveryHandle())
        vm.pauseGame()
        return vm
    }

    @Test fun `DELIVERY via savedStateHandle places pod off the origin`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Regression: pickPodTarget wurde im Init nicht gerufen → Pod blieb
            // bei (0, 0) in der Außenwand stecken.
            stubRepo()
            val vm = buildDeliveryVmPaused()
            val pod = vm.state.value.fuelPod
            assertTrue("pod x must be > 100, was ${pod.position.x}", pod.position.x > 100f)
            assertTrue("pod y must be > 100, was ${pod.position.y}", pod.position.y > 100f)
        }

    @Test fun `DELIVERY pod always lies inside the playable corridor`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Regression: Random-y konnte in [250, 1750] landen, was in der
            // inneren Decke (~y=260) oder unterhalb des Bodens (y>1700) endet.
            stubRepo()
            // Mehrfach instanziieren — practiceRng nutzt System.currentTimeMillis,
            // damit deckt der Lauf hinreichend viele Seeds ab.
            repeat(20) {
                val vm = buildDeliveryVmPaused()
                val cfg = vm.state.value.levelConfig
                val pod = vm.state.value.fuelPod
                assertTrue("pod x in arena bounds: ${pod.position.x}",
                    pod.position.x in 200f..(cfg.worldWidth  - 200f))
                assertTrue("pod y in playable corridor: ${pod.position.y}",
                    pod.position.y in 300f..(cfg.worldHeight - 400f))
            }
        }

    @Test fun `DELIVERY pod respects min distance to pad and ship spawn`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            repeat(20) {
                val vm = buildDeliveryVmPaused()
                val cfg = vm.state.value.levelConfig
                val pod = vm.state.value.fuelPod
                val toPad  = (pod.position - cfg.landingPad.center).length()
                val toShip = (pod.position - cfg.shipStart).length()
                assertTrue("pod must be > 1000 from pad: $toPad",  toPad  > 1000f)
                assertTrue("pod must be > 600 from ship: $toShip", toShip > 600f)
            }
        }
}