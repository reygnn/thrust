package com.github.reygnn.thrust.ui.game

import app.cash.turbine.test
import com.github.reygnn.thrust.MainDispatcherRule
import com.github.reygnn.thrust.data.HighScoreRepository
import com.github.reygnn.thrust.data.SettingsRepository
import com.github.reygnn.thrust.domain.engine.PhysicsConstants
import com.github.reygnn.thrust.domain.engine.PhysicsEngine
import com.github.reygnn.thrust.domain.level.LevelRepository
import com.github.reygnn.thrust.domain.level.LevelRepositoryImpl
import com.github.reygnn.thrust.domain.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Hinweis zum Dispatcher:
 * Alle [runTest]-Aufrufe übergeben explizit [MainDispatcherRule.dispatcher] –
 * andernfalls würde runTest seinen eigenen StandardTestDispatcher mit eigenem
 * Scheduler erzeugen, während die ViewModel-Coroutinen via Dispatchers.Main auf
 * dem Scheduler der Rule laufen. [advanceTimeBy] würde dann den Loop nicht
 * vorspulen. Siehe TESTING_CONVENTIONS.kt.
 */
class GameViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val levelRepo: LevelRepository = LevelRepositoryImpl()
    private val highScoreRepo              = mockk<HighScoreRepository>()
    private val settingsRepo               = mockk<SettingsRepository>()
    private val physicsEngine              = PhysicsEngine()

    private fun stubRepo() {
        every { highScoreRepo.getHighScores() } returns flowOf(emptyMap())
        coEvery { highScoreRepo.updateHighScore(any(), any()) } just Runs
        every { settingsRepo.playerGunEnabled } returns flowOf(false)
    }

    private fun buildVm(engine: PhysicsEngine = physicsEngine) = GameViewModel(
        physicsEngine       = engine,
        levelRepository     = levelRepo,
        highScoreRepository = highScoreRepo,
        settingsRepository  = settingsRepo,
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
            physicsEngine       = physicsEngine,
            levelRepository     = object : LevelRepository {
                override fun getLevel(id: Int) = level4Config
                override val totalLevels: Int  = 4
            },
            highScoreRepository = highScoreRepo,
            settingsRepository  = settingsRepo,
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

            // Beim Abschluss von Level 1 (currentLevel = 1) wird der Highscore gespeichert –
            // unabhängig davon, ob es das letzte Level ist.
            coVerify { highScoreRepo.updateHighScore(1, any()) }
        }

    @Test fun `onGameOverConfirm does NOT save high score (already saved in loop)`() =
        runTest(mainDispatcherRule.dispatcher) {
            stubRepo()
            val vm = buildVm()

            // Zustand: noch keine GameOver-Phase erreicht (Loop hat nichts gespeichert).
            // onGameOverConfirm() darf jetzt KEINEN Save mehr auslösen.
            vm.onGameOverConfirm()
            advanceTimeBy(100)

            coVerify(exactly = 0) { highScoreRepo.updateHighScore(any(), any()) }
        }
}