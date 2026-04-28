package com.github.reygnn.thrust.domain.level

import com.github.reygnn.thrust.domain.engine.CollisionDetector
import com.github.reygnn.thrust.domain.engine.PhysicsConstants
import com.github.reygnn.thrust.domain.model.LevelConfig
import com.github.reygnn.thrust.domain.model.Vector2
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Validiert dass der LevelGenerator für alle Difficulties **außer Pure Chaos**
 * spielbare Layouts erzeugt — d.h. Schiff kann Pod erreichen und Pad anfliegen.
 *
 * Vorgehensweise: das Spielfeld wird in [CELL] große Quadrate gerastert. Eine
 * Zelle gilt als blockiert wenn ihr Mittelpunkt näher als der Schiffsradius an
 * irgendeinem Terrain-Segment liegt. Anschließend BFS von der Schiff-Spawn-
 * Zelle aus; sowohl die Pod-Zelle als auch eine Zelle direkt über dem Pad-
 * Mittelpunkt müssen erreichbar sein.
 *
 * Pure Chaos ist absichtlich vom Test ausgeschlossen — die Difficulty hat
 * einen In-Game-Disclaimer und darf weiterhin gelegentlich unspielbare Seeds
 * produzieren.
 */
class LevelPlayabilityTest {

    @Test
    fun `every non-Pure-Chaos seed produces a playable level`() {
        val playableDifficulties = Difficulty.values().filter { it != Difficulty.PURE_CHAOS }
        playableDifficulties.forEach { d ->
            (0L..49L).forEach { seed ->
                val cfg = LevelGenerator.generate(d, seed)
                val result = checkPlayable(cfg)
                assertTrue(
                    "Level nicht spielbar: difficulty=$d seed=$seed reason=${result.reason}",
                    result.ok,
                )
            }
        }
    }

    // ── Implementation ─────────────────────────────────────────────────────────

    private data class Result(val ok: Boolean, val reason: String = "")

    private fun checkPlayable(cfg: LevelConfig): Result {
        val collider = CollisionDetector()
        val cols = ceil(cfg.worldWidth  / CELL).toInt() + 1
        val rows = ceil(cfg.worldHeight / CELL).toInt() + 1
        val blocked = Array(rows) { BooleanArray(cols) }

        // Pro Segment nur die betroffenen Zellen besuchen — das ist deutlich schneller
        // als für jede Zelle alle Segmente zu prüfen.
        for (seg in cfg.terrain) {
            val minX = min(seg.start.x, seg.end.x) - PhysicsConstants.SHIP_RADIUS - CELL
            val maxX = max(seg.start.x, seg.end.x) + PhysicsConstants.SHIP_RADIUS + CELL
            val minY = min(seg.start.y, seg.end.y) - PhysicsConstants.SHIP_RADIUS - CELL
            val maxY = max(seg.start.y, seg.end.y) + PhysicsConstants.SHIP_RADIUS + CELL

            val cMin = (minX / CELL).toInt().coerceAtLeast(0)
            val cMax = (maxX / CELL).toInt().coerceAtMost(cols - 1)
            val rMin = (minY / CELL).toInt().coerceAtLeast(0)
            val rMax = (maxY / CELL).toInt().coerceAtMost(rows - 1)

            for (r in rMin..rMax) {
                for (c in cMin..cMax) {
                    if (blocked[r][c]) continue
                    val center = Vector2((c + 0.5f) * CELL, (r + 0.5f) * CELL)
                    if (collider.circleIntersectsSegment(center, PhysicsConstants.SHIP_RADIUS, seg.start, seg.end)) {
                        blocked[r][c] = true
                    }
                }
            }
        }

        val (shipR, shipC) = cellOf(cfg.shipStart, rows, cols)
        if (blocked[shipR][shipC]) return Result(false, "ship spawn cell blocked")

        val (podR, podC) = cellOf(cfg.fuelPodPosition, rows, cols)
        if (blocked[podR][podC]) return Result(false, "pod cell blocked at (${cfg.fuelPodPosition.x}, ${cfg.fuelPodPosition.y})")

        // Anflug-Zelle direkt über dem Pad — Pad selbst sitzt auf der Floor-Linie,
        // dessen Zelle ist je nach Cell-Grid teilweise vom Floor blockiert.
        val padApproach = Vector2(cfg.landingPad.center.x, cfg.landingPad.center.y - PhysicsConstants.SHIP_RADIUS - 10f)
        val (padR, padC) = cellOf(padApproach, rows, cols)
        if (blocked[padR][padC]) return Result(false, "pad approach cell blocked at $padApproach")

        // BFS aus der Schiff-Zelle.
        val visited = Array(rows) { BooleanArray(cols) }
        val queue   = ArrayDeque<IntArray>()
        queue.addLast(intArrayOf(shipR, shipC))
        visited[shipR][shipC] = true
        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()
            for (d in DIRS) {
                val nr = r + d[0]; val nc = c + d[1]
                if (nr in 0 until rows && nc in 0 until cols && !visited[nr][nc] && !blocked[nr][nc]) {
                    visited[nr][nc] = true
                    queue.addLast(intArrayOf(nr, nc))
                }
            }
        }

        if (!visited[podR][podC]) return Result(false, "pod unreachable from ship")
        if (!visited[padR][padC]) return Result(false, "pad approach unreachable from ship")
        return Result(true)
    }

    private fun cellOf(p: Vector2, rows: Int, cols: Int): IntArray {
        val r = (p.y / CELL).toInt().coerceIn(0, rows - 1)
        val c = (p.x / CELL).toInt().coerceIn(0, cols - 1)
        return intArrayOf(r, c)
    }

    private operator fun IntArray.component1(): Int = this[0]
    private operator fun IntArray.component2(): Int = this[1]

    companion object {
        // Fein genug, damit eine Spalte zwischen zwei Wänden mit genau 2*SHIP_RADIUS
        // Abstand mindestens eine begehbare Zelle in der Mitte hat. Größer würde zu
        // Falsch-Negativ-Treffern führen.
        private const val CELL = 20f
        private val DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
    }
}
