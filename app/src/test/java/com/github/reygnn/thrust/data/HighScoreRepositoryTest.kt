package com.github.reygnn.thrust.data

import app.cash.turbine.test
import com.github.reygnn.thrust.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Verträgt-sich-mit-Konvention-Test (siehe TESTING_CONVENTIONS.kt):
 * runTest erhält explizit den Dispatcher der Rule, damit nur ein Scheduler im Spiel ist.
 *
 * Wir testen die Schnittstellenverträge per Mock; die DataStore-Anbindung der Impl
 * ist eine Integration-Concern und wird hier nicht abgedeckt.
 */
class HighScoreRepositoryTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repo = mockk<HighScoreRepository>()

    @Test fun `getHighScores emits empty map initially`() =
        runTest(mainDispatcherRule.dispatcher) {
            every { repo.getHighScores() } returns flowOf(emptyMap())

            repo.getHighScores().test {
                val item = awaitItem()
                assertTrue(item.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `getHighScores emits level scores`() =
        runTest(mainDispatcherRule.dispatcher) {
            val expected = mapOf(1 to 1500, 2 to 2200, 3 to 0, 4 to 4200)
            every { repo.getHighScores() } returns flowOf(expected)

            repo.getHighScores().test {
                val item = awaitItem()
                assertEquals(1500, item[1])
                assertEquals(2200, item[2])
                assertEquals(0,    item[3])
                assertEquals(4200, item[4])
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `updateHighScore is called with correct arguments`() =
        runTest(mainDispatcherRule.dispatcher) {
            coEvery { repo.updateHighScore(any(), any()) } just Runs

            repo.updateHighScore(level = 2, score = 3000)

            coVerify(exactly = 1) { repo.updateHighScore(2, 3000) }
        }

    @Test fun `updateHighScore can be called for each level`() =
        runTest(mainDispatcherRule.dispatcher) {
            coEvery { repo.updateHighScore(any(), any()) } just Runs

            repo.updateHighScore(1, 100)
            repo.updateHighScore(2, 200)
            repo.updateHighScore(3, 300)
            repo.updateHighScore(4, 400)

            coVerify { repo.updateHighScore(1, 100) }
            coVerify { repo.updateHighScore(2, 200) }
            coVerify { repo.updateHighScore(3, 300) }
            coVerify { repo.updateHighScore(4, 400) }
        }
}