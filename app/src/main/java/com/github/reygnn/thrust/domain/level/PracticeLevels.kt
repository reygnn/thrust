package com.github.reygnn.thrust.domain.level

import com.github.reygnn.thrust.domain.model.LandingPad
import com.github.reygnn.thrust.domain.model.LevelConfig
import com.github.reygnn.thrust.domain.model.TerrainSegment
import com.github.reygnn.thrust.domain.model.TurretConfig
import com.github.reygnn.thrust.domain.model.Vector2
import kotlin.random.Random

/**
 * Hand-gebaute Level-Layouts für die vier Practice-Modi. Die Configs sind
 * statisch — für Practice braucht's keine prozedurale Variation.
 *
 * Konvention: Pad und Pod werden in einigen Modi nicht verwendet. Sie liegen
 * dann an unzugänglichen Eckpositionen (innerhalb der Außenwand), damit weder
 * Landing noch Pickup zufällig getriggert wird.
 */
object PracticeLevels {

    /** Sentinel-ID — kein Bezug zu Story- oder Endless-Levels. */
    const val PRACTICE_LEVEL_ID: Int = -1

    fun configFor(kind: PracticeKind, rng: Random = Random.Default): LevelConfig = when (kind) {
        PracticeKind.TUBE    -> tube(rng)
        PracticeKind.DOCKING -> docking()
        PracticeKind.LANDING -> landing()
        PracticeKind.TURRETS -> turrets()
    }

    // ── Tube ──────────────────────────────────────────────────────────────────

    private fun tube(rng: Random): LevelConfig {
        val w = 10000f; val h = 1500f
        // Decke/Boden-Mittellinien — stark wellig, Amplitude bis ±100 vom
        // Basisniveau. Ergibt einen lebendigen Schlauch (Decke 200..400,
        // Boden 1100..1300).
        val ceilingBase = 300f
        val floorBase   = 1200f
        val ampl        = 100f
        val segCount    = 40
        val ceilingYs   = randomWalk(rng, segCount + 1, ceilingBase, ampl)
        val floorYs     = randomWalk(rng, segCount + 1, floorBase, ampl)

        return LevelConfig(
            id              = PRACTICE_LEVEL_ID,
            name            = PracticeKind.TUBE.displayName,
            worldWidth      = w,
            worldHeight     = h,
            gravity         = 0.030f,
            shipStart       = Vector2(400f, 750f),
            fuelPodPosition = UNUSED_POSITION,
            landingPad      = UNUSED_PAD,
            terrain         = buildList {
                addAll(outerWalls(w, h))
                // Wellige Decke
                for (i in 0 until segCount) {
                    val xa = i * w / segCount
                    val xb = (i + 1) * w / segCount
                    add(seg(xa, ceilingYs[i], xb, ceilingYs[i + 1]))
                }
                // Welliger Boden
                for (i in 0 until segCount) {
                    val xa = i * w / segCount
                    val xb = (i + 1) * w / segCount
                    add(seg(xa, floorYs[i], xb, floorYs[i + 1]))
                }
                // Hindernisse — Stalaktiten und Stalagmiten dicht gestreut.
                addAll(tubeObstacles(
                    rng         = rng,
                    w           = w,
                    ceilingMaxY = ceilingBase + ampl,
                    floorMinY   = floorBase   - ampl,
                    shipStartX  = 400f,
                ))
            },
        )
    }

    /**
     * Random-Walk innerhalb [base ± ampl]. Schritte sind klein (max ±25% Ampl)
     * damit die Linie nicht zickzackt sondern organisch verläuft.
     */
    private fun randomWalk(rng: Random, count: Int, base: Float, ampl: Float): List<Float> {
        val out = mutableListOf<Float>()
        var y = base
        for (i in 0 until count) {
            out.add(y.coerceIn(base - ampl, base + ampl))
            y += (rng.nextFloat() - 0.5f) * ampl * 0.5f
            // Sanftes Pull-zur-Mitte gegen Drift
            y += (base - y) * 0.05f
        }
        return out
    }

    /**
     * Streut Stalaktiten und Stalagmiten alle 300..650 Einheiten entlang des
     * Schlauchs. Erstes Hindernis erst rechts vom Schiff-Spawn damit der
     * Spieler nicht direkt am Start gegen eine Wand fliegt.
     */
    private fun tubeObstacles(
        rng: Random,
        w: Float,
        ceilingMaxY: Float,
        floorMinY: Float,
        shipStartX: Float,
    ): List<TerrainSegment> {
        val out = mutableListOf<TerrainSegment>()
        var x = shipStartX + 600f
        while (x < w - 400f) {
            val width = 50f + rng.nextFloat() * 100f          // 50..150
            val xL    = x
            val xR    = x + width
            val depth = 120f + rng.nextFloat() * 280f         // 120..400
            if (rng.nextBoolean()) {
                // Stalaktit von der Decke (Tip-y wächst nach unten)
                val tipY = ceilingMaxY + depth
                out += seg(xL, ceilingMaxY, xL, tipY)
                out += seg(xL, tipY,        xR, tipY)
                out += seg(xR, tipY,        xR, ceilingMaxY)
            } else {
                // Stalagmit vom Boden
                val tipY = floorMinY - depth
                out += seg(xL, floorMinY,   xL, tipY)
                out += seg(xL, tipY,        xR, tipY)
                out += seg(xR, tipY,        xR, floorMinY)
            }
            x += xR - xL + 250f + rng.nextFloat() * 400f      // nächstes 250..650 später
        }
        return out
    }

    // ── Docking ───────────────────────────────────────────────────────────────

    private fun docking(): LevelConfig {
        val w = 3000f; val h = 2000f
        return LevelConfig(
            id              = PRACTICE_LEVEL_ID,
            name            = PracticeKind.DOCKING.displayName,
            worldWidth      = w,
            worldHeight     = h,
            gravity         = 0.030f,
            shipStart       = Vector2(500f, 500f),
            fuelPodPosition = Vector2(2200f, 1400f),
            landingPad      = UNUSED_PAD,
            terrain         = buildList {
                addAll(outerWalls(w, h))
                // Sanfte Decke und Boden — nur Außenkanten, ansonsten offene Arena
                add(seg(0f, 250f, w, 280f))
                add(seg(0f, 1750f, w, 1730f))
            },
            turrets         = emptyList(),
        )
    }

    // ── Landing ───────────────────────────────────────────────────────────────

    private fun landing(): LevelConfig {
        val w = 2500f; val h = 2000f
        val padCenterX = w / 2f
        val padHalfWidth = 220f
        val floorY = 1700f
        return LevelConfig(
            id              = PRACTICE_LEVEL_ID,
            name            = PracticeKind.LANDING.displayName,
            worldWidth      = w,
            worldHeight     = h,
            gravity         = 0.045f,
            shipStart       = Vector2(padCenterX, 350f),
            fuelPodPosition = UNUSED_POSITION,
            landingPad      = LandingPad(Vector2(padCenterX, floorY), padHalfWidth),
            terrain         = buildList {
                addAll(outerWalls(w, h))
                // Decke
                add(seg(0f, 250f, w, 270f))
                // Boden mit Pad-Lücke
                add(seg(0f, floorY + 5f, padCenterX - padHalfWidth, floorY))
                add(seg(padCenterX + padHalfWidth, floorY, w, floorY + 5f))
            },
            turrets         = emptyList(),
        )
    }

    // ── Turrets ───────────────────────────────────────────────────────────────

    private fun turrets(): LevelConfig {
        val w = 3000f; val h = 2000f
        return LevelConfig(
            id              = PRACTICE_LEVEL_ID,
            name            = PracticeKind.TURRETS.displayName,
            worldWidth      = w,
            worldHeight     = h,
            gravity         = 0.030f,
            shipStart       = Vector2(500f, 500f),
            fuelPodPosition = UNUSED_POSITION,
            landingPad      = UNUSED_PAD,
            terrain         = buildList {
                addAll(outerWalls(w, h))
                add(seg(0f, 250f, w, 280f))
                add(seg(0f, 1750f, w, 1730f))
            },
            turrets         = listOf(
                TurretConfig(Vector2(2200f, 1300f), firePeriodFrames = 110, bulletSpeed = 4.5f),
            ),
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun outerWalls(w: Float, h: Float): List<TerrainSegment> = listOf(
        seg(0f, 0f, w, 0f),
        seg(0f, 0f, 0f, h),
        seg(0f, h, w, h),
        seg(w, 0f, w, h),
    )

    private fun seg(x1: Float, y1: Float, x2: Float, y2: Float) =
        TerrainSegment(Vector2(x1, y1), Vector2(x2, y2))

    /** Off-path Position für ungenutzte Pods/Pads — innerhalb der Außenwand. */
    private val UNUSED_POSITION = Vector2(20f, 20f)
    private val UNUSED_PAD      = LandingPad(Vector2(20f, 20f), halfWidth = 30f)
}
