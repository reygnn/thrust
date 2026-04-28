package com.github.reygnn.thrust.domain.level

import com.github.reygnn.thrust.domain.engine.CollisionDetector
import com.github.reygnn.thrust.domain.engine.PhysicsConstants
import com.github.reygnn.thrust.domain.model.LevelConfig
import com.github.reygnn.thrust.domain.model.Vector2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Reine geometrische Erreichbarkeits-Prüfung für ein generiertes [LevelConfig].
 *
 * Das Spielfeld wird in [CELL] große Quadrate gerastert. Eine Zelle gilt als
 * blockiert wenn ihr Mittelpunkt näher als der Schiffsradius an irgendeinem
 * Terrain-Segment liegt. Aus der Schiff-Spawn-Zelle wird per BFS geflutet.
 *
 * Wird vom [LevelGenerator] genutzt um in Pure Chaos pro Seed zu prüfen, dass
 * der Pod nie hinter einer geschlossenen Barriere endet — und vom
 * `LevelPlayabilityTest` für die anderen Difficulties.
 */
object LevelPlayability {

    private const val CELL = 20f
    private val DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))

    fun isPodReachableFromShip(cfg: LevelConfig): Boolean {
        val visited = floodFromShip(cfg) ?: return false
        val (rows, cols) = visited.size to visited[0].size
        val (podR, podC) = cellOf(cfg.fuelPodPosition, rows, cols)
        return visited[podR][podC]
    }

    fun isPadApproachReachableFromShip(cfg: LevelConfig): Boolean {
        val visited = floodFromShip(cfg) ?: return false
        val (rows, cols) = visited.size to visited[0].size
        val approach = Vector2(cfg.landingPad.center.x, cfg.landingPad.center.y - PhysicsConstants.SHIP_RADIUS - 10f)
        val (r, c) = cellOf(approach, rows, cols)
        return visited[r][c]
    }

    /**
     * Liefert das `visited[r][c]`-Array nach dem BFS oder `null` wenn das
     * Schiff selbst auf einer blockierten Zelle landet (in dem Fall wäre
     * gar nichts erreichbar).
     */
    private fun floodFromShip(cfg: LevelConfig): Array<BooleanArray>? {
        val collider = CollisionDetector()
        val cols = ceil(cfg.worldWidth  / CELL).toInt() + 1
        val rows = ceil(cfg.worldHeight / CELL).toInt() + 1
        val blocked = Array(rows) { BooleanArray(cols) }

        // Pro Segment nur die betroffenen Zellen besuchen — deutlich schneller
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

        val (sR, sC) = cellOf(cfg.shipStart, rows, cols)
        if (blocked[sR][sC]) return null

        val visited = Array(rows) { BooleanArray(cols) }
        val queue   = ArrayDeque<IntArray>()
        queue.addLast(intArrayOf(sR, sC))
        visited[sR][sC] = true
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
        return visited
    }

    private fun cellOf(p: Vector2, rows: Int, cols: Int): IntArray {
        val r = (p.y / CELL).toInt().coerceIn(0, rows - 1)
        val c = (p.x / CELL).toInt().coerceIn(0, cols - 1)
        return intArrayOf(r, c)
    }

    private operator fun IntArray.component1(): Int = this[0]
    private operator fun IntArray.component2(): Int = this[1]
}
