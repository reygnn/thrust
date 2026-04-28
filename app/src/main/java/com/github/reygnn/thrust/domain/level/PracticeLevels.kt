package com.github.reygnn.thrust.domain.level

import com.github.reygnn.thrust.domain.model.LandingPad
import com.github.reygnn.thrust.domain.model.LevelConfig
import com.github.reygnn.thrust.domain.model.TerrainSegment
import com.github.reygnn.thrust.domain.model.TurretConfig
import com.github.reygnn.thrust.domain.model.Vector2

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

    fun configFor(kind: PracticeKind): LevelConfig = when (kind) {
        PracticeKind.TUBE    -> tube()
        PracticeKind.DOCKING -> docking()
        PracticeKind.LANDING -> landing()
        PracticeKind.TURRETS -> turrets()
    }

    // ── Tube ──────────────────────────────────────────────────────────────────

    private fun tube(): LevelConfig {
        val w = 10000f; val h = 1500f
        val ceilingY = 300f
        val floorY   = 1200f
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
                // Decke leicht wellig
                add(seg(0f, ceilingY, 1500f, ceilingY - 30f))
                add(seg(1500f, ceilingY - 30f, 3000f, ceilingY + 40f))
                add(seg(3000f, ceilingY + 40f, 4500f, ceilingY - 50f))
                add(seg(4500f, ceilingY - 50f, 6000f, ceilingY + 30f))
                add(seg(6000f, ceilingY + 30f, 7500f, ceilingY - 40f))
                add(seg(7500f, ceilingY - 40f, 9000f, ceilingY + 50f))
                add(seg(9000f, ceilingY + 50f, w, ceilingY))
                // Boden leicht wellig
                add(seg(0f, floorY, 1500f, floorY + 40f))
                add(seg(1500f, floorY + 40f, 3000f, floorY - 30f))
                add(seg(3000f, floorY - 30f, 4500f, floorY + 50f))
                add(seg(4500f, floorY + 50f, 6000f, floorY - 40f))
                add(seg(6000f, floorY - 40f, 7500f, floorY + 30f))
                add(seg(7500f, floorY + 30f, 9000f, floorY - 50f))
                add(seg(9000f, floorY - 50f, w, floorY))
            },
        )
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
