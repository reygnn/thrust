package com.github.reygnn.thrust.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.github.reygnn.thrust.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HighScoreRepositoryImplTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private lateinit var tempDir: File

    @Before fun setUp() {
        tempDir = Files.createTempDirectory("thrust-hs-test").toFile()
    }

    @After fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun newDataStore(name: String = "hs.preferences_pb"): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { File(tempDir, name) })

    private fun newRepo(totalLevels: Int = 4) =
        HighScoreRepositoryImpl(newDataStore(), totalLevels = totalLevels)

    @Test fun `initial scores are zero for every level`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = newRepo(totalLevels = 4)

            repo.getHighScores().test {
                val item = awaitItem()
                assertEquals(setOf(1, 2, 3, 4), item.keys)
                assertTrue("all initial scores are 0", item.values.all { it == 0 })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `updateHighScore stores a higher score`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = newRepo()

            repo.updateHighScore(level = 2, score = 1500)

            repo.getHighScores().test {
                assertEquals(1500, awaitItem()[2])
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `updateHighScore does not overwrite a higher existing score`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = newRepo()
            repo.updateHighScore(level = 1, score = 5000)
            repo.updateHighScore(level = 1, score = 100)

            repo.getHighScores().test {
                assertEquals(5000, awaitItem()[1])
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `scores for different levels are stored independently`() =
        // Regression v0.2: Hardcoded Key-Range hatte Level 4 auf Level 3
        // gemappt — Level-4-Scores haben Level-3-Scores still überschrieben.
        // Hier sicherstellen, dass jedes Level seinen eigenen Slot hat.
        runTest(mainDispatcherRule.dispatcher) {
            val repo = newRepo(totalLevels = 4)
            repo.updateHighScore(1, 100)
            repo.updateHighScore(2, 200)
            repo.updateHighScore(3, 300)
            repo.updateHighScore(4, 400)

            repo.getHighScores().test {
                val item = awaitItem()
                assertEquals(100, item[1])
                assertEquals(200, item[2])
                assertEquals(300, item[3])
                assertEquals(400, item[4])
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `updateHighScore throws for level outside totalLevels range`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = newRepo(totalLevels = 4)
            assertThrows(IllegalArgumentException::class.java) {
                runTest(mainDispatcherRule.dispatcher) { repo.updateHighScore(0, 100) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runTest(mainDispatcherRule.dispatcher) { repo.updateHighScore(5, 100) }
            }
        }

    @Test fun `constructor rejects non-positive totalLevels`() {
        assertThrows(IllegalArgumentException::class.java) {
            HighScoreRepositoryImpl(newDataStore(), totalLevels = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HighScoreRepositoryImpl(newDataStore(), totalLevels = -1)
        }
    }
}
