package com.github.reygnn.thrust.domain

import com.github.reygnn.thrust.domain.engine.CollisionDetector
import com.github.reygnn.thrust.domain.engine.CollisionDetector.LandingResult
import com.github.reygnn.thrust.domain.engine.PhysicsConstants
import com.github.reygnn.thrust.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CollisionDetectorTest {

    private lateinit var sut: CollisionDetector

    @Before fun setUp() { sut = CollisionDetector() }

    // ── circleIntersectsSegment ───────────────────────────────────────────────

    @Test fun `circle far from segment does not intersect`() {
        assertFalse(
            sut.circleIntersectsSegment(
                center = Vector2(0f, 0f),
                radius = 10f,
                a      = Vector2(100f, 0f),
                b      = Vector2(200f, 0f),
            )
        )
    }

    @Test fun `circle touching segment returns true`() {
        // Circle centered at (0,5), radius=6 → closest point on segment (0,0)-(10,0) is (0,0) → dist=5 < 6
        assertTrue(
            sut.circleIntersectsSegment(
                center = Vector2(0f, 5f),
                radius = 6f,
                a      = Vector2(0f, 0f),
                b      = Vector2(10f, 0f),
            )
        )
    }

    @Test fun `circle exactly at segment endpoint intersects`() {
        assertTrue(
            sut.circleIntersectsSegment(
                center = Vector2(5f, 0f),
                radius = 5.1f,
                a      = Vector2(5f, 0f),
                b      = Vector2(20f, 0f),
            )
        )
    }

    @Test fun `zero-length segment treated as point`() {
        assertTrue(
            sut.circleIntersectsSegment(
                center = Vector2(0f, 0f),
                radius = 1f,
                a      = Vector2(0.5f, 0f),
                b      = Vector2(0.5f, 0f),
            )
        )
    }

    // ── checkShipTerrain ──────────────────────────────────────────────────────

    @Test fun `ship not near any segment returns false`() {
        val ship    = Ship(position = Vector2(500f, 500f))
        val terrain = listOf(TerrainSegment(Vector2(0f, 0f), Vector2(100f, 0f)))
        assertFalse(sut.checkShipTerrain(ship, terrain))
    }

    @Test fun `ship overlapping terrain segment returns true`() {
        val ship    = Ship(position = Vector2(50f, PhysicsConstants.SHIP_RADIUS - 2f))
        val terrain = listOf(TerrainSegment(Vector2(0f, 0f), Vector2(100f, 0f)))
        assertTrue(sut.checkShipTerrain(ship, terrain))
    }

    // ── checkLanding ──────────────────────────────────────────────────────────

    private fun pad() = LandingPad(center = Vector2(500f, 400f), halfWidth = 100f)

    @Test fun `ship outside pad x-range returns None`() {
        val ship = Ship(position = Vector2(300f, 400f - PhysicsConstants.SHIP_RADIUS),
                        velocity = Vector2(0f, 1f))
        assertEquals(LandingResult.None, sut.checkLanding(ship, pad()))
    }

    @Test fun `slow upright ship returns Success`() {
        val ship = Ship(
            position = Vector2(500f, 400f - PhysicsConstants.SHIP_RADIUS + 2f),
            velocity = Vector2(0f, 1.5f),
            angle    = 0f,
        )
        assertEquals(LandingResult.Success, sut.checkLanding(ship, pad()))
    }

    @Test fun `too-fast ship returns Crash`() {
        val ship = Ship(
            position = Vector2(500f, 400f - PhysicsConstants.SHIP_RADIUS + 2f),
            velocity = Vector2(0f, PhysicsConstants.MAX_LANDING_SPEED_Y + 1f),
            angle    = 0f,
        )
        assertEquals(LandingResult.Crash, sut.checkLanding(ship, pad()))
    }

    @Test fun `tilted ship returns Crash`() {
        val ship = Ship(
            position = Vector2(500f, 400f - PhysicsConstants.SHIP_RADIUS + 2f),
            velocity = Vector2(0f, 1f),
            angle    = 45f,
        )
        assertEquals(LandingResult.Crash, sut.checkLanding(ship, pad()))
    }

    // ── checkPodPickup ────────────────────────────────────────────────────────

    @Test fun `ship within pickup radius returns true`() {
        val ship = Ship(position = Vector2(0f, 0f))
        val pod  = FuelPod(position = Vector2(PhysicsConstants.POD_PICKUP_RADIUS - 1f, 0f))
        assertTrue(sut.checkPodPickup(ship, pod))
    }

    @Test fun `ship outside pickup radius returns false`() {
        val ship = Ship(position = Vector2(0f, 0f))
        val pod  = FuelPod(position = Vector2(PhysicsConstants.POD_PICKUP_RADIUS + 1f, 0f))
        assertFalse(sut.checkPodPickup(ship, pod))
    }

    // ── checkBulletShip ──────────────────────────────────────────────────────

    @Test fun `bullet on ship returns true`() {
        val ship   = Ship(position = Vector2(100f, 100f))
        val bullet = Bullet(position = Vector2(100f, 100f), velocity = Vector2.Zero, isEnemy = true)
        assertTrue(sut.checkBulletShip(bullet, ship))
    }

    @Test fun `bullet far from ship returns false`() {
        val ship   = Ship(position = Vector2(100f, 100f))
        val bullet = Bullet(position = Vector2(200f, 200f), velocity = Vector2.Zero, isEnemy = true)
        assertFalse(sut.checkBulletShip(bullet, ship))
    }

    // ── checkPodDelivery ─────────────────────────────────────────────────────

    @Test fun `pod over pad returns true`() {
        val pod = FuelPod(position = Vector2(500f, 400f), isPickedUp = true)
        assertTrue(sut.checkPodDelivery(pod, pad()))
    }

    @Test fun `pod outside pad x returns false`() {
        val pod = FuelPod(position = Vector2(700f, 400f), isPickedUp = true)
        assertFalse(sut.checkPodDelivery(pod, pad()))
    }
}
