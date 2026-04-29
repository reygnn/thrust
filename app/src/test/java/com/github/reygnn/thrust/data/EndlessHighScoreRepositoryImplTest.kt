package com.github.reygnn.thrust.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.github.reygnn.thrust.MainDispatcherRule
import com.github.reygnn.thrust.domain.level.Difficulty
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EndlessHighScoreRepositoryImplTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private lateinit var tempDir: File

    @Before fun setUp() {
        tempDir = Files.createTempDirectory("thrust-endless-hs-test").toFile()
    }

    @After fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun newDataStore(name: String = "endless.preferences_pb"): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { File(tempDir, name) })

    @Test fun `initial streaks are zero for every difficulty`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = EndlessHighScoreRepositoryImpl(newDataStore())

            repo.getStreaks().test {
                val item = awaitItem()
                assertEquals(Difficulty.values().toSet(), item.keys)
                assertTrue("all initial streaks are 0", item.values.all { it == 0 })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `updateStreak stores a higher streak`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = EndlessHighScoreRepositoryImpl(newDataStore())

            repo.updateStreak(Difficulty.MEDIUM, 7)

            repo.getStreaks().test {
                assertEquals(7, awaitItem()[Difficulty.MEDIUM])
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `updateStreak does not overwrite a higher existing streak`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = EndlessHighScoreRepositoryImpl(newDataStore())
            repo.updateStreak(Difficulty.ROOKIE, 10)
            repo.updateStreak(Difficulty.ROOKIE, 3)

            repo.getStreaks().test {
                assertEquals(10, awaitItem()[Difficulty.ROOKIE])
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `streaks for different difficulties are stored independently`() =
        // Gleiche Klasse wie der Story-HighScore-Bug v0.2, hier präventiv:
        // jeder Difficulty muss seinen eigenen Slot haben.
        runTest(mainDispatcherRule.dispatcher) {
            val repo = EndlessHighScoreRepositoryImpl(newDataStore())
            Difficulty.values().forEachIndexed { idx, d ->
                repo.updateStreak(d, idx + 1)  // 1, 2, 3, …
            }

            repo.getStreaks().test {
                val item = awaitItem()
                Difficulty.values().forEachIndexed { idx, d ->
                    assertEquals("streak for $d", idx + 1, item[d])
                }
                cancelAndIgnoreRemainingEvents()
            }
        }
}
