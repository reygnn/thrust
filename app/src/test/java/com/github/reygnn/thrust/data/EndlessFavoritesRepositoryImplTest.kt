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

class EndlessFavoritesRepositoryImplTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private lateinit var tempDir: File

    @Before fun setUp() {
        tempDir = Files.createTempDirectory("thrust-fav-test").toFile()
    }

    @After fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun newDataStore(name: String = "favs.preferences_pb"): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { File(tempDir, name) })

    // ── Pure encode / decode helpers ──────────────────────────────────────────

    @Test fun `encode then decode round-trips a single entry`() {
        val original = listOf(EndlessFavorite(Difficulty.MEDIUM, 12345L, 1_700_000_000L))
        val encoded  = EndlessFavoritesRepositoryImpl.encode(original)
        val decoded  = EndlessFavoritesRepositoryImpl.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test fun `encode then decode round-trips multiple entries in order`() {
        val original = listOf(
            EndlessFavorite(Difficulty.ROOKIE,    1L, 100L),
            EndlessFavorite(Difficulty.MEDIUM,    2L, 200L),
            EndlessFavorite(Difficulty.IMPOSSIBLE, 3L, 300L),
        )
        val decoded = EndlessFavoritesRepositoryImpl.decode(
            EndlessFavoritesRepositoryImpl.encode(original)
        )
        assertEquals(original, decoded)
    }

    @Test fun `decode of empty string returns empty list`() {
        assertTrue(EndlessFavoritesRepositoryImpl.decode("").isEmpty())
        assertTrue(EndlessFavoritesRepositoryImpl.decode("   ").isEmpty())
    }

    @Test fun `decode skips lines with wrong number of pipes`() {
        // 2 statt 3 Felder, 4 statt 3 Felder, leere Zeile — alle müssen
        // verworfen werden, ohne den Rest umzunieten.
        val raw = buildString {
            append("MEDIUM|1\n")
            append("MEDIUM|2|200\n")
            append("MEDIUM|3|300|extra\n")
            append("\n")
            append("ROOKIE|4|400")
        }
        val decoded = EndlessFavoritesRepositoryImpl.decode(raw)
        assertEquals(
            listOf(
                EndlessFavorite(Difficulty.MEDIUM, 2L, 200L),
                EndlessFavorite(Difficulty.ROOKIE, 4L, 400L),
            ),
            decoded,
        )
    }

    @Test fun `decode skips lines with unknown Difficulty`() {
        // Falls jemand das Enum umbenennt oder alte Daten vorliegen, dürfen
        // gültige Zeilen davon nicht betroffen sein.
        val raw = "BANANA|1|100\nMEDIUM|2|200"
        assertEquals(
            listOf(EndlessFavorite(Difficulty.MEDIUM, 2L, 200L)),
            EndlessFavoritesRepositoryImpl.decode(raw),
        )
    }

    @Test fun `decode skips lines with non-numeric seed or savedAt`() {
        val raw = "MEDIUM|abc|100\nMEDIUM|2|xyz\nMEDIUM|3|300"
        assertEquals(
            listOf(EndlessFavorite(Difficulty.MEDIUM, 3L, 300L)),
            EndlessFavoritesRepositoryImpl.decode(raw),
        )
    }

    // ── DataStore-backed integration ──────────────────────────────────────────

    @Test fun `addFavorite makes it observable via getFavorites`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = EndlessFavoritesRepositoryImpl(newDataStore())
            val fav  = EndlessFavorite(Difficulty.MEDIUM, 42L, 1L)

            repo.addFavorite(fav)

            repo.getFavorites().test {
                val items = awaitItem()
                assertEquals(listOf(fav), items)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `addFavorite is idempotent for same difficulty and seed`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = EndlessFavoritesRepositoryImpl(newDataStore())
            val fav  = EndlessFavorite(Difficulty.MEDIUM, 42L, savedAt = 100L)

            repo.addFavorite(fav)
            repo.addFavorite(fav.copy(savedAt = 200L))

            repo.getFavorites().test {
                val items = awaitItem()
                assertEquals("only the first save survives", 1, items.size)
                assertEquals(100L, items.single().savedAt)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `addFavorite enforces FIFO at maxEntries (oldest dropped)`() =
        runTest(mainDispatcherRule.dispatcher) {
            // maxEntries=3, vier verschiedene Einträge → der älteste (zuerst
            // hinzugefügt) muss raus, neueste an Position 0.
            val repo = EndlessFavoritesRepositoryImpl(newDataStore(), maxEntries = 3)
            repo.addFavorite(EndlessFavorite(Difficulty.ROOKIE, 1L, 1L))
            repo.addFavorite(EndlessFavorite(Difficulty.ROOKIE, 2L, 2L))
            repo.addFavorite(EndlessFavorite(Difficulty.ROOKIE, 3L, 3L))
            repo.addFavorite(EndlessFavorite(Difficulty.ROOKIE, 4L, 4L))

            repo.getFavorites().test {
                val items = awaitItem()
                assertEquals(3, items.size)
                assertEquals(4L, items[0].seed)  // newest first
                assertEquals(3L, items[1].seed)
                assertEquals(2L, items[2].seed)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `removeFavorite drops only the matching entry`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = EndlessFavoritesRepositoryImpl(newDataStore())
            val a = EndlessFavorite(Difficulty.ROOKIE, 1L, 1L)
            val b = EndlessFavorite(Difficulty.MEDIUM, 1L, 2L)
            val c = EndlessFavorite(Difficulty.ROOKIE, 2L, 3L)
            repo.addFavorite(a); repo.addFavorite(b); repo.addFavorite(c)

            repo.removeFavorite(b)

            repo.getFavorites().test {
                val items = awaitItem()
                assertEquals(setOf(a, c), items.toSet())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `removeFavorite is a no-op for missing entries`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repo = EndlessFavoritesRepositoryImpl(newDataStore())
            val present = EndlessFavorite(Difficulty.MEDIUM, 1L, 1L)
            repo.addFavorite(present)

            repo.removeFavorite(EndlessFavorite(Difficulty.IMPOSSIBLE, 99L, 99L))

            repo.getFavorites().test {
                val items = awaitItem()
                assertEquals(listOf(present), items)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `constructor rejects non-positive maxEntries`() {
        assertThrows(IllegalArgumentException::class.java) {
            EndlessFavoritesRepositoryImpl(newDataStore(), maxEntries = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EndlessFavoritesRepositoryImpl(newDataStore(), maxEntries = -1)
        }
    }
}
