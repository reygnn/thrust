package com.github.reygnn.thrust.domain

import com.github.reygnn.thrust.domain.engine.CollisionDetector
import com.github.reygnn.thrust.domain.engine.PhysicsConstants
import com.github.reygnn.thrust.domain.engine.PhysicsEngine
import com.github.reygnn.thrust.domain.level.LevelRepositoryImpl
import com.github.reygnn.thrust.domain.model.*
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PhysicsEngineTest {

    private lateinit var sut: PhysicsEngine
    private val level1 = LevelRepositoryImpl().getLevel(1)

    private fun baseState(
        ship: Ship = Ship(position = Vector2(400f, 500f)),
        phase: GamePhase = GamePhase.Playing,
    ) = GameState.initial(level1).copy(ship = ship, phase = phase)

    @Before fun setUp() { sut = PhysicsEngine() }

    // ── Non-playing states are left unchanged ─────────────────────────────────

    @Test fun `update returns unchanged state when paused`() {
        val s = baseState(phase = GamePhase.Paused)
        assertSame(s, sut.update(s, InputState()))
    }

    @Test fun `update returns unchanged state when game over`() {
        val s = baseState(phase = GamePhase.GameOver)
        assertSame(s, sut.update(s, InputState()))
    }

    // ── Gravity ───────────────────────────────────────────────────────────────

    @Test fun `gravity increases vy each frame`() {
        val s     = baseState()
        val after = sut.update(s, InputState(thrust = false))
        assertTrue("vy must increase", after.ship.velocity.y > s.ship.velocity.y)
    }

    @Test fun `gravity accelerates ship downward without thrust`() {
        var s = baseState()
        repeat(10) { s = sut.update(s, InputState()) }
        assertTrue("ship moves down after 10 frames", s.ship.position.y > 500f)
    }

    // ── Thrust ────────────────────────────────────────────────────────────────

    @Test fun `thrust at angle 0 decreases vy (moves ship upward)`() {
        val s = baseState(Ship(position = Vector2(400f, 600f), angle = 0f, fuel = 500f))
        val after = sut.update(s, InputState(thrust = true))
        // Net: vy += GRAVITY, vy -= THRUST_POWER → if THRUST > GRAVITY net vy < 0
        val netEffect = PhysicsConstants.THRUST_POWER - PhysicsConstants.GRAVITY
        assertTrue("upward net force", after.ship.velocity.y < netEffect + 0.001f)
    }

    @Test fun `thrust at angle 90 increases vx (moves ship rightward)`() {
        val s = baseState(Ship(position = Vector2(400f, 600f), angle = 90f, fuel = 500f))
        val after = sut.update(s, InputState(thrust = true))
        assertTrue("rightward thrust at 90°", after.ship.velocity.x > 0f)
    }

    @Test fun `thrust consumes fuel`() {
        val s     = baseState(Ship(position = Vector2(400f, 600f), fuel = 500f))
        val after = sut.update(s, InputState(thrust = true))
        assertTrue(after.ship.fuel < 500f)
    }

    @Test fun `thrust does nothing when fuel is empty`() {
        val s     = baseState(Ship(position = Vector2(400f, 600f), fuel = 0f))
        val after = sut.update(s, InputState(thrust = true))
        // vy should only increase by level gravity, not decrease from thrust
        assertEquals(s.levelConfig.gravity, after.ship.velocity.y, 0.001f)
    }

    // ── Rotation ──────────────────────────────────────────────────────────────

    @Test fun `rotate-left decreases ship angle`() {
        val s     = baseState(Ship(position = Vector2(400f, 400f)))
        val after = sut.update(s, InputState(rotateLeft = true))
        assertTrue(after.ship.angle < 0f)
    }

    @Test fun `rotate-right increases ship angle`() {
        val s     = baseState(Ship(position = Vector2(400f, 400f)))
        val after = sut.update(s, InputState(rotateRight = true))
        assertTrue(after.ship.angle > 0f)
    }

    // ── Speed cap ─────────────────────────────────────────────────────────────

    @Test fun `speed is capped at MAX_SPEED`() {
        var s = baseState(Ship(position = Vector2(400f, 400f), fuel = 5000f))
        repeat(200) { s = sut.update(s, InputState(thrust = true)) }
        val speed = s.ship.velocity.length()
        assertTrue("speed must not exceed cap: $speed", speed <= PhysicsConstants.MAX_SPEED + 0.01f)
    }

    // ── Respawn ───────────────────────────────────────────────────────────────

    @Test fun `dead ship respawn timer decrements each frame`() {
        val s = baseState(Ship(
            position = Vector2(400f, 400f),
            isAlive  = false,
            respawnTimer = PhysicsConstants.RESPAWN_FRAMES,
        ))
        val after = sut.update(s, InputState())
        assertEquals(PhysicsConstants.RESPAWN_FRAMES - 1, after.ship.respawnTimer)
    }

    @Test fun `ship respawns at config start when timer reaches zero`() {
        val s = baseState(Ship(
            position = Vector2(9999f, 9999f),
            isAlive  = false,
            respawnTimer = 1,
        ))
        val after = sut.update(s, InputState())
        assertTrue(after.ship.isAlive)
        assertEquals(level1.shipStart.x, after.ship.position.x, 1f)
        assertEquals(level1.shipStart.y, after.ship.position.y, 1f)
    }

    // ── Collision leads to GameOver when no lives left ────────────────────────

    @Test fun `terrain collision with zero lives sets GameOver`() {
        // Use a mock CollisionDetector to force a hit
        val detector = mockk<CollisionDetector>()
        every { detector.checkShipTerrain(any(), any()) } returns true
        every { detector.checkBulletShip(any(), any()) }  returns false
        every { detector.checkLanding(any(), any()) }     returns CollisionDetector.LandingResult.None
        every { detector.checkPodPickup(any(), any()) }   returns false
        every { detector.checkPodDelivery(any(), any()) } returns false

        val engine = PhysicsEngine(collisionDetector = detector)
        val s      = baseState().copy(lives = 1)
        val after  = engine.update(s, InputState())
        assertEquals(GamePhase.GameOver, after.phase)
    }

    @Test fun `terrain collision with lives remaining keeps Playing phase`() {
        val detector = mockk<CollisionDetector>()
        every { detector.checkShipTerrain(any(), any()) } returns true
        every { detector.checkBulletShip(any(), any()) }  returns false
        every { detector.checkLanding(any(), any()) }     returns CollisionDetector.LandingResult.None
        every { detector.checkPodPickup(any(), any()) }   returns false
        every { detector.checkPodDelivery(any(), any()) } returns false

        val engine = PhysicsEngine(collisionDetector = detector)
        val s      = baseState().copy(lives = 3)
        val after  = engine.update(s, InputState())
        assertEquals(GamePhase.Playing, after.phase)
        assertFalse(after.ship.isAlive)
    }

    // ── Pod delivery + level complete ─────────────────────────────────────────

    @Test fun `landing with delivered pod triggers LevelComplete`() {
        val detector = mockk<CollisionDetector>()
        every { detector.checkShipTerrain(any(), any()) } returns false
        every { detector.checkBulletShip(any(), any()) }  returns false
        every { detector.checkLanding(any(), any()) }     returns CollisionDetector.LandingResult.Success
        every { detector.checkPodPickup(any(), any()) }   returns false
        every { detector.checkPodDelivery(any(), any()) } returns false

        val engine = PhysicsEngine(collisionDetector = detector)
        val s      = baseState().copy(fuelPod = FuelPod(
            position    = level1.fuelPodPosition,
            isPickedUp  = false,
            isDelivered = true,
        ))
        val after = engine.update(s, InputState())
        assertTrue("phase should be LevelComplete", after.phase is GamePhase.LevelComplete)
    }

    // ── Frame counter ─────────────────────────────────────────────────────────

    @Test fun `frame count increments each playing frame`() {
        val s     = baseState()
        val after = sut.update(s, InputState())
        assertEquals(1L, after.frameCount)
    }
}
