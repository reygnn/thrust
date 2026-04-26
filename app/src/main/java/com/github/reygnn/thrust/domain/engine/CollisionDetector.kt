package com.github.reygnn.thrust.domain.engine

import com.github.reygnn.thrust.domain.model.*
import kotlin.math.abs

class CollisionDetector {

    /** Closest-point circle-vs-segment test. */
    fun circleIntersectsSegment(
        center: Vector2,
        radius: Float,
        a: Vector2,
        b: Vector2,
    ): Boolean {
        val ab   = b - a
        val ac   = center - a
        val len2 = ab.dot(ab)
        val t    = if (len2 == 0f) 0f else (ac.dot(ab) / len2).coerceIn(0f, 1f)
        val closest = a + ab * t
        return (center - closest).length() < radius
    }

    /** True if ship circle overlaps any terrain segment. */
    fun checkShipTerrain(ship: Ship, terrain: List<TerrainSegment>): Boolean =
        terrain.any { circleIntersectsSegment(ship.position, PhysicsConstants.SHIP_RADIUS, it.start, it.end) }

    // ── Landing ──────────────────────────────────────────────────────────────
    sealed interface LandingResult {
        data object None    : LandingResult
        data object Success : LandingResult
        data object Crash   : LandingResult
    }

    fun checkLanding(ship: Ship, pad: LandingPad): LandingResult {
        val shipBottom = ship.position.y + PhysicsConstants.SHIP_RADIUS
        if (ship.position.x < pad.left || ship.position.x > pad.right) return LandingResult.None
        if (abs(shipBottom - pad.y) > 10f) return LandingResult.None

        val speedOk = ship.velocity.y in 0f..PhysicsConstants.MAX_LANDING_SPEED_Y
        val norm    = ship.angle % 360f
        val adjusted = if (norm < 0) norm + 360f else norm
        val angleOk = adjusted < PhysicsConstants.MAX_LANDING_ANGLE ||
                      adjusted > 360f - PhysicsConstants.MAX_LANDING_ANGLE
        return if (speedOk && angleOk) LandingResult.Success else LandingResult.Crash
    }

    // ── Other checks ─────────────────────────────────────────────────────────
    fun checkBulletShip(bullet: Bullet, ship: Ship): Boolean =
        (bullet.position - ship.position).length() < PhysicsConstants.SHIP_RADIUS + 5f

    fun checkPodPickup(ship: Ship, pod: FuelPod): Boolean =
        (ship.position - pod.position).length() < PhysicsConstants.POD_PICKUP_RADIUS

    fun checkPodDelivery(pod: FuelPod, pad: LandingPad): Boolean =
        pod.position.x in pad.left..pad.right && abs(pod.position.y - pad.y) < 35f
}
