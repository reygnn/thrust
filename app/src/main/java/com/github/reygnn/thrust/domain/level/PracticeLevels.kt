package com.github.reygnn.thrust.domain.level

import com.github.reygnn.thrust.domain.engine.PhysicsConstants
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
        PracticeKind.TUBE     -> tube(rng)
        PracticeKind.DELIVERY -> delivery()
        PracticeKind.TURRETS  -> turrets()
    }

    // ── Tube ──────────────────────────────────────────────────────────────────

    private fun tube(rng: Random): LevelConfig {
        val w = 10000f; val h = 1500f
        val segCount = 50
        val shipStartX = 400f
        // Centerline wandert stark (±250 vom Basis 750), Korridor-Halbhöhe
        // schwankt zwischen 200 und 300 — Korridor 400..600 breit, der Spieler
        // kann nicht einfach mit vollem Schub geradeaus düsen.
        val centerYs = randomWalk(rng, segCount + 1, base = 750f, ampl = 250f).toMutableList()
        val halfHs   = randomWalk(rng, segCount + 1, base = 250f, ampl = 50f)
        // Ersten paar Segmente um den Spawn fixieren, sonst kann das Schiff
        // direkt im Terrain landen.
        for (i in 0..2) centerYs[i] = 750f
        val ceilingYs = centerYs.zip(halfHs) { c, hh -> c - hh }
        val floorYs   = centerYs.zip(halfHs) { c, hh -> c + hh }

        return LevelConfig(
            id              = PRACTICE_LEVEL_ID,
            name            = PracticeKind.TUBE.displayName,
            worldWidth      = w,
            worldHeight     = h,
            // Gravity hochgezogen — gleiten reicht nicht mehr, der Spieler
            // muss aktiv Schub und Lage managen.
            gravity         = 0.050f,
            shipStart       = Vector2(shipStartX, 750f),
            fuelPodPosition = UNUSED_POSITION,
            landingPad      = UNUSED_PAD,
            terrain         = buildList {
                addAll(outerWalls(w, h))
                // Wellige Decke und Boden (Centerline + Halbhöhe).
                for (i in 0 until segCount) {
                    val xa = i * w / segCount
                    val xb = (i + 1) * w / segCount
                    add(seg(xa, ceilingYs[i], xb, ceilingYs[i + 1]))
                    add(seg(xa, floorYs[i],   xb, floorYs[i + 1]))
                }
                // Lokale Stalaktiten/Stalagmiten an den Wandlinien.
                addAll(tubeObstacles(rng, w, segCount, ceilingYs, floorYs, shipStartX))
                // Volle Pillar-Barrieren mit kleinem vertikalem Durchlass.
                addAll(tubeFullBarriers(rng, w, segCount, ceilingYs, floorYs, shipStartX))
                // Frei schwebende Brocken — Rechtecke und Diamanten.
                addAll(tubeFloatingObstacles(rng, w, segCount, ceilingYs, floorYs, shipStartX))
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
            y += (base - y) * 0.05f   // sanftes Pull-zur-Mitte
        }
        return out
    }

    private fun yAtX(x: Float, ys: List<Float>, segCount: Int, w: Float): Float {
        val frac = (x / w) * segCount
        val i    = frac.toInt().coerceIn(0, segCount - 1)
        val t    = frac - i
        return ys[i] * (1f - t) + ys[i + 1] * t
    }

    /**
     * Lokale Stalaktiten und Stalagmiten alle 250..500 Einheiten — abwechselnd
     * zufällig. Ankerpunkt ist die jeweilige wellige Wand, der Hindernis-Tip
     * ragt 100..300 in den Korridor hinein.
     */
    private fun tubeObstacles(
        rng: Random,
        w: Float,
        segCount: Int,
        ceilingYs: List<Float>,
        floorYs: List<Float>,
        shipStartX: Float,
    ): List<TerrainSegment> {
        val out = mutableListOf<TerrainSegment>()
        var x = shipStartX + 600f
        while (x < w - 400f) {
            val width = 50f + rng.nextFloat() * 100f      // 50..150
            val xL    = x
            val xR    = x + width
            val midX  = (xL + xR) / 2f
            val depth = 100f + rng.nextFloat() * 200f     // 100..300
            if (rng.nextBoolean()) {
                val anchor = yAtX(midX, ceilingYs, segCount, w)
                val tipY   = anchor + depth
                out += seg(xL, anchor, xL, tipY)
                out += seg(xL, tipY,   xR, tipY)
                out += seg(xR, tipY,   xR, anchor)
            } else {
                val anchor = yAtX(midX, floorYs, segCount, w)
                val tipY   = anchor - depth
                out += seg(xL, anchor, xL, tipY)
                out += seg(xL, tipY,   xR, tipY)
                out += seg(xR, tipY,   xR, anchor)
            }
            x += width + 200f + rng.nextFloat() * 300f    // nächstes 200..500 später
        }
        return out
    }

    /**
     * Volle Pillar-Barrieren von Decke zu Boden mit einem vertikalen Durchlass —
     * der Schlauch wird stellenweise zum Schlüsselloch. Alle 1500..2300 Einheiten,
     * Durchlass-Höhe 180..280.
     */
    private fun tubeFullBarriers(
        rng: Random,
        w: Float,
        segCount: Int,
        ceilingYs: List<Float>,
        floorYs: List<Float>,
        shipStartX: Float,
    ): List<TerrainSegment> {
        val out = mutableListOf<TerrainSegment>()
        var x = shipStartX + 1800f
        while (x < w - 1000f) {
            val width = 60f + rng.nextFloat() * 60f       // 60..120
            val xL    = x
            val xR    = x + width
            val midX  = (xL + xR) / 2f
            val cy    = yAtX(midX, ceilingYs, segCount, w)
            val fy    = yAtX(midX, floorYs,   segCount, w)
            val gapH  = 180f + rng.nextFloat() * 100f     // 180..280
            val gapMin = cy + 80f
            val gapMax = fy - 80f - gapH
            if (gapMax > gapMin) {
                val gapTop = gapMin + rng.nextFloat() * (gapMax - gapMin)
                val gapBot = gapTop + gapH
                // Stalaktit-Hälfte
                out += seg(xL, cy,     xL, gapTop)
                out += seg(xL, gapTop, xR, gapTop)
                out += seg(xR, gapTop, xR, cy)
                // Stalagmit-Hälfte
                out += seg(xL, fy,     xL, gapBot)
                out += seg(xL, gapBot, xR, gapBot)
                out += seg(xR, gapBot, xR, fy)
            }
            x += width + 1500f + rng.nextFloat() * 800f   // nächstes 1500..2300 später
        }
        return out
    }

    /**
     * Frei schwebende Hindernisse mitten im Korridor — abwechselnd Rechtecke
     * und Diamanten. Position und Form pro Eintrag zufällig, alle 800..1500
     * Einheiten verteilt. Padding 100 zu den Wandlinien stellt sicher, dass
     * ober- und unterhalb des Brockens noch genug Raum für das Schiff bleibt.
     */
    private fun tubeFloatingObstacles(
        rng: Random,
        w: Float,
        segCount: Int,
        ceilingYs: List<Float>,
        floorYs: List<Float>,
        shipStartX: Float,
    ): List<TerrainSegment> {
        val out = mutableListOf<TerrainSegment>()
        var x = shipStartX + 1200f
        while (x < w - 500f) {
            val midX = x
            val cy   = yAtX(midX, ceilingYs, segCount, w)
            val fy   = yAtX(midX, floorYs,   segCount, w)
            val padding = 100f
            // Genug lokale Korridor-Höhe damit das Hindernis nicht selber Wand wird.
            if (fy - cy > 2f * padding + 80f) {
                val ymin = cy + padding
                val ymax = fy - padding
                val cyShape = ymin + rng.nextFloat() * (ymax - ymin)
                if (rng.nextBoolean()) {
                    // Achsenparalleles Rechteck
                    val hw = 25f + rng.nextFloat() * 15f      // 25..40
                    val hh = 25f + rng.nextFloat() * 15f
                    val l  = midX - hw; val r = midX + hw
                    val t  = cyShape - hh; val b = cyShape + hh
                    out += seg(l, t, r, t)
                    out += seg(r, t, r, b)
                    out += seg(r, b, l, b)
                    out += seg(l, b, l, t)
                } else {
                    // Diamant (gedrehtes Quadrat)
                    val hd = 30f + rng.nextFloat() * 20f      // 30..50
                    val top   = Vector2(midX,      cyShape - hd)
                    val right = Vector2(midX + hd, cyShape)
                    val bot   = Vector2(midX,      cyShape + hd)
                    val left  = Vector2(midX - hd, cyShape)
                    out += TerrainSegment(top,   right)
                    out += TerrainSegment(right, bot)
                    out += TerrainSegment(bot,   left)
                    out += TerrainSegment(left,  top)
                }
            }
            x += 800f + rng.nextFloat() * 700f                 // alle 800..1500
        }
        return out
    }

    // ── Delivery (Pickup + Land) ──────────────────────────────────────────────

    private fun delivery(): LevelConfig {
        val w = 3000f; val h = 2000f
        val padCenterX = 2400f
        val padHalfWidth = 200f
        val floorY = 1700f
        // Schiff sitzt mit shipBottom (= shipY + SHIP_RADIUS) exakt auf der
        // Pad-Linie — visuell satt aufliegend, ohne den 40-Pixel-Schwebebug.
        val shipStartX = padCenterX
        val shipStartY = floorY - PhysicsConstants.SHIP_RADIUS
        return LevelConfig(
            id              = PRACTICE_LEVEL_ID,
            name            = PracticeKind.DELIVERY.displayName,
            worldWidth      = w,
            worldHeight     = h,
            gravity         = 0.045f,
            shipStart       = Vector2(shipStartX, shipStartY),
            // Initialwert; VM platziert den Pod beim Start an einer
            // Random-Position weit weg vom Pad.
            fuelPodPosition = Vector2(500f, 800f),
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
