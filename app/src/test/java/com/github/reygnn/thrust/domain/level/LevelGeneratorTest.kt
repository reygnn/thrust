package com.github.reygnn.thrust.domain.level

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelGeneratorTest {

    @Test
    fun `same seed produces identical level`() {
        Difficulty.values().forEach { d ->
            val a = LevelGenerator.generate(d, seed = 42L)
            val b = LevelGenerator.generate(d, seed = 42L)
            assertEquals("Determinism failed for $d", a, b)
        }
    }

    @Test
    fun `different seeds produce different levels`() {
        // For levels with any randomness (all but possibly trivial cases), seed change should matter.
        val a = LevelGenerator.generate(Difficulty.MEDIUM, seed = 1L)
        val b = LevelGenerator.generate(Difficulty.MEDIUM, seed = 2L)
        assertNotEquals(a, b)
    }

    @Test
    fun `level uses endless sentinel id`() {
        val cfg = LevelGenerator.generate(Difficulty.ROOKIE, seed = 0L)
        assertEquals(LevelGenerator.ENDLESS_LEVEL_ID, cfg.id)
    }

    @Test
    fun `world dimensions and gravity follow difficulty`() {
        Difficulty.values().forEach { d ->
            val cfg = LevelGenerator.generate(d, seed = 7L)
            assertEquals(d.worldWidth, cfg.worldWidth, 0.001f)
            assertEquals(d.worldHeight, cfg.worldHeight, 0.001f)
            assertEquals(d.gravity, cfg.gravity, 0.0001f)
            assertEquals(d.padHalfWidth, cfg.landingPad.halfWidth, 0.001f)
            assertEquals(d.displayName, cfg.name)
        }
    }

    @Test
    fun `turret count is within difficulty range across many seeds`() {
        Difficulty.values().forEach { d ->
            (0L..30L).forEach { seed ->
                val cfg = LevelGenerator.generate(d, seed)
                assertTrue(
                    "Turret count ${cfg.turrets.size} out of range ${d.turretCount} for $d seed=$seed",
                    cfg.turrets.size in d.turretCount,
                )
            }
        }
    }

    @Test
    fun `ship pod and pad are inside world bounds`() {
        Difficulty.values().forEach { d ->
            (0L..10L).forEach { seed ->
                val cfg = LevelGenerator.generate(d, seed)
                assertInBounds("ship", cfg.shipStart.x, cfg.shipStart.y, cfg.worldWidth, cfg.worldHeight)
                assertInBounds("pod", cfg.fuelPodPosition.x, cfg.fuelPodPosition.y, cfg.worldWidth, cfg.worldHeight)
                assertInBounds("pad", cfg.landingPad.center.x, cfg.landingPad.center.y, cfg.worldWidth, cfg.worldHeight)
            }
        }
    }

    @Test
    fun `floor leaves a clear gap centered on the landing pad`() {
        // Kein Floor-Segment darf horizontal in den Pad-Spalt ragen.
        Difficulty.values().forEach { d ->
            val cfg = LevelGenerator.generate(d, seed = 13L)
            val padLeft  = cfg.landingPad.left
            val padRight = cfg.landingPad.right
            val padY     = cfg.landingPad.y
            cfg.terrain.forEach { s ->
                // Floor-Segmente liegen nahe pad y; nur diese prüfen wir auf Lücke.
                val midY = (s.start.y + s.end.y) / 2f
                if (kotlin.math.abs(midY - padY) < 30f) {
                    val xs = listOf(s.start.x, s.end.x).sorted()
                    assertTrue(
                        "Segment ${s.start} -> ${s.end} überlappt Pad-Lücke ($padLeft..$padRight) bei $d",
                        xs[1] <= padLeft + 1f || xs[0] >= padRight - 1f,
                    )
                }
            }
        }
    }

    @Test
    fun `turret periods and speeds respect difficulty ranges`() {
        Difficulty.values().forEach { d ->
            (0L..20L).forEach { seed ->
                val cfg = LevelGenerator.generate(d, seed)
                cfg.turrets.forEach { t ->
                    assertTrue(
                        "Period ${t.firePeriodFrames} out of ${d.turretFirePeriod} for $d",
                        t.firePeriodFrames in d.turretFirePeriod,
                    )
                    assertTrue(
                        "Speed ${t.bulletSpeed} out of ${d.turretBulletSpeed} for $d",
                        t.bulletSpeed >= d.turretBulletSpeed.start - 0.001f &&
                                t.bulletSpeed <= d.turretBulletSpeed.endInclusive + 0.001f,
                    )
                }
            }
        }
    }

    private fun assertInBounds(label: String, x: Float, y: Float, w: Float, h: Float) {
        assertTrue("$label x=$x out of [0..$w]", x in 0f..w)
        assertTrue("$label y=$y out of [0..$h]", y in 0f..h)
    }
}
