package com.github.reygnn.thrust.domain.engine

import com.github.reygnn.thrust.domain.model.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class PhysicsEngine(
    private val collisionDetector: CollisionDetector = CollisionDetector(),
) {

    fun update(state: GameState, input: InputState, playerGunEnabled: Boolean = false): GameState {
        if (state.phase != GamePhase.Playing) return state

        if (!state.ship.isAlive) {
            val remaining = state.ship.respawnTimer - 1
            return if (remaining <= 0) {
                state.copy(
                    ship    = Ship(position = state.levelConfig.shipStart, angle = state.levelConfig.shipStartAngle),
                    fuelPod = if (state.ship.hasPod)
                        FuelPod(position = state.levelConfig.fuelPodPosition)
                    else state.fuelPod,
                    frameCount = state.frameCount + 1,
                )
            } else {
                state.copy(ship = state.ship.copy(respawnTimer = remaining), frameCount = state.frameCount + 1)
            }
        }

        var ship = applyShipPhysics(state.ship, input, state.levelConfig.gravity)

        var pod = when {
            state.fuelPod.isDelivered  -> state.fuelPod
            state.fuelPod.isPickedUp   -> {
                // Pod hängt am Seil. Erst Pendel-Update, dann auf Terrain prüfen —
                // streift der Pod eine Wand, reißt das Seil und der Pod fällt.
                val swung = updateRope(state.fuelPod, ship.position, state.levelConfig.gravity)
                if (collisionDetector.checkPodTerrain(swung, state.levelConfig.terrain)) {
                    ship = ship.copy(hasPod = false)
                    swung.copy(isPickedUp = false, isFalling = true)
                } else {
                    swung
                }
            }
            state.fuelPod.isFalling -> {
                // Frei fallender Pod: Schwerkraft anwenden, bei Terrain-Kontakt
                // an Ort und Stelle liegen bleiben (kein Bouncing für jetzt).
                val newVel = state.fuelPod.velocity + Vector2(0f, state.levelConfig.gravity * 0.6f)
                val newPos = state.fuelPod.position + newVel
                val testPod = state.fuelPod.copy(position = newPos)
                if (collisionDetector.checkPodTerrain(testPod, state.levelConfig.terrain)) {
                    state.fuelPod.copy(velocity = Vector2.Zero, isFalling = false)
                } else {
                    state.fuelPod.copy(position = newPos, velocity = newVel)
                }
            }
            else -> state.fuelPod
        }

        val (turrets, newEnemyBullets) = fireTurrets(state.turrets, state.ship.position)

        var fireCooldown = (state.playerFireCooldown - 1).coerceAtLeast(0)
        val newPlayerBullets = mutableListOf<Bullet>()
        if (playerGunEnabled && input.shoot && fireCooldown == 0) {
            val rad = Math.toRadians(ship.angle.toDouble()).toFloat()
            val tipDir = Vector2(sin(rad), -cos(rad))
            val tipPos = ship.position + tipDir * (PhysicsConstants.SHIP_RADIUS + 4f)
            newPlayerBullets += Bullet(
                position   = tipPos,
                velocity   = tipDir * PhysicsConstants.PLAYER_BULLET_SPEED + ship.velocity * 0.5f,
                isEnemy    = false,
                lifeFrames = PhysicsConstants.PLAYER_BULLET_LIFETIME,
            )
            fireCooldown = PhysicsConstants.FIRE_COOLDOWN_FRAMES
        }

        val allBullets = (state.bullets + newEnemyBullets + newPlayerBullets)
            .map { b -> b.copy(position = b.position + b.velocity, lifeFrames = b.lifeFrames - 1) }
            .filter { b ->
                b.lifeFrames > 0 &&
                        b.position.x in 0f..state.levelConfig.worldWidth &&
                        b.position.y in 0f..state.levelConfig.worldHeight
            }

        val (updatedTurrets, remainingBullets) = resolvePlayerBulletsVsTurrets(allBullets, turrets)

        val terrainHit = collisionDetector.checkShipTerrain(ship, state.levelConfig.terrain)
        val bulletHit  = remainingBullets.filter { it.isEnemy }.any { collisionDetector.checkBulletShip(it, ship) }
        if (terrainHit || bulletHit) {
            val newLives = state.lives - 1
            return state.copy(
                ship    = ship.copy(isAlive = false, hasPod = false, respawnTimer = PhysicsConstants.RESPAWN_FRAMES),
                fuelPod = if (pod.isPickedUp && !pod.isDelivered) FuelPod(position = state.levelConfig.fuelPodPosition) else pod,
                bullets = remainingBullets.filterNot { it.isEnemy },
                turrets = updatedTurrets,
                lives   = newLives,
                phase   = if (newLives <= 0) GamePhase.GameOver else state.phase,
                frameCount = state.frameCount + 1,
                isThrusting = false,
                playerFireCooldown = fireCooldown,
            )
        }

        // Aufnahme nur wenn der Pod ruht — ein noch fallender Pod wird nicht direkt
        // wieder ans Seil gehängt, sonst ist die "Wand-streifen → fällt"-Mechanik
        // wirkungslos (das Schiff wäre meist unmittelbar nach dem Detach noch in
        // Pickup-Reichweite).
        if (!pod.isPickedUp && !pod.isDelivered && !pod.isFalling &&
            collisionDetector.checkPodPickup(ship, pod)
        ) {
            pod  = pod.copy(isPickedUp = true)
            ship = ship.copy(hasPod = true)
        }

        var score = state.score
        if (pod.isPickedUp && !pod.isDelivered && collisionDetector.checkPodDelivery(pod, state.levelConfig.landingPad)) {
            pod   = pod.copy(isPickedUp = false, isDelivered = true)
            ship  = ship.copy(hasPod = false)
            score += 500
        }

        return when (collisionDetector.checkLanding(ship, state.levelConfig.landingPad)) {
            CollisionDetector.LandingResult.Success -> if (pod.isDelivered) {
                state.copy(
                    ship = ship, fuelPod = pod, bullets = remainingBullets, turrets = updatedTurrets,
                    score = score + 1000, phase = GamePhase.LevelComplete(score + 1000),
                    frameCount = state.frameCount + 1, isThrusting = false,
                    playerFireCooldown = fireCooldown,
                )
            } else defaultCopy(state, ship, pod, remainingBullets, updatedTurrets, score, input, fireCooldown)

            CollisionDetector.LandingResult.Crash -> {
                val newLives = state.lives - 1
                state.copy(
                    ship    = ship.copy(isAlive = false, hasPod = false, respawnTimer = PhysicsConstants.RESPAWN_FRAMES),
                    fuelPod = if (pod.isPickedUp) FuelPod(position = state.levelConfig.fuelPodPosition) else pod,
                    bullets = remainingBullets, turrets = updatedTurrets,
                    lives   = newLives,
                    phase   = if (newLives <= 0) GamePhase.GameOver else state.phase,
                    frameCount = state.frameCount + 1, isThrusting = false,
                    playerFireCooldown = fireCooldown,
                )
            }

            CollisionDetector.LandingResult.None ->
                defaultCopy(state, ship, pod, remainingBullets, updatedTurrets, score, input, fireCooldown)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun defaultCopy(
        state: GameState, ship: Ship, pod: FuelPod,
        bullets: List<Bullet>, turrets: List<Turret>,
        score: Int, input: InputState, fireCooldown: Int,
    ) = state.copy(
        ship = ship, fuelPod = pod, bullets = bullets, turrets = turrets,
        score = score, frameCount = state.frameCount + 1,
        isThrusting = input.thrust && ship.fuel > 0f,
        playerFireCooldown = fireCooldown,
    )

    private fun resolvePlayerBulletsVsTurrets(
        bullets: List<Bullet>,
        turrets: List<Turret>,
    ): Pair<List<Turret>, List<Bullet>> {
        val destroyedIndices      = mutableSetOf<Int>()
        val consumedBulletIndices = mutableSetOf<Int>()

        bullets.forEachIndexed { bi, bullet ->
            if (bullet.isEnemy) return@forEachIndexed
            turrets.forEachIndexed { ti, turret ->
                if (turret.isDestroyed || ti in destroyedIndices) return@forEachIndexed
                if ((bullet.position - turret.config.position).length() < 14f) {
                    destroyedIndices      += ti
                    consumedBulletIndices += bi
                }
            }
        }

        val updatedTurrets   = turrets.mapIndexed { i, t -> if (i in destroyedIndices) t.copy(isDestroyed = true) else t }
        val remainingBullets = bullets.filterIndexed { i, _ -> i !in consumedBulletIndices }
        return updatedTurrets to remainingBullets
    }

    /**
     * Applies rotation, thrust and gravity for one frame.
     *
     * Rotation has two paths:
     * - Button mode ([InputState.targetAngle] is null): adds [PhysicsConstants.ROTATION_SPEED]
     *   per pressed direction, exactly as the original engine.
     * - Slider/wheel mode (targetAngle is non-null): rotates toward the target by at most
     *   [PhysicsConstants.ROTATION_SPEED] degrees per frame, using the shortest path around
     *   the 360° circle. This preserves the original rotation-rate skill element — the slider
     *   can request a target angle but the ship still has rotational inertia.
     */
    private fun applyShipPhysics(ship: Ship, input: InputState, gravity: Float): Ship {
        var angle = ship.angle

        if (input.targetAngle != null) {
            val diff = shortestAngleDiff(input.targetAngle, angle)
            val step = diff.coerceIn(-PhysicsConstants.ROTATION_SPEED, +PhysicsConstants.ROTATION_SPEED)
            angle += step
        } else {
            if (input.rotateLeft)  angle -= PhysicsConstants.ROTATION_SPEED
            if (input.rotateRight) angle += PhysicsConstants.ROTATION_SPEED
        }
        angle = ((angle + 180f) % 360f + 360f) % 360f - 180f

        val rad  = Math.toRadians(angle.toDouble()).toFloat()
        var vx   = ship.velocity.x
        var vy   = ship.velocity.y
        var fuel = ship.fuel

        if (input.thrust && fuel > 0f) {
            vx  += sin(rad) * PhysicsConstants.THRUST_POWER
            vy  -= cos(rad) * PhysicsConstants.THRUST_POWER
            fuel = (fuel - PhysicsConstants.FUEL_CONSUMPTION).coerceAtLeast(0f)
        }

        vy += gravity

        val speed = sqrt(vx * vx + vy * vy)
        if (speed > PhysicsConstants.MAX_SPEED) {
            val s = PhysicsConstants.MAX_SPEED / speed
            vx *= s; vy *= s
        }

        return ship.copy(
            position = Vector2(ship.position.x + vx, ship.position.y + vy),
            velocity = Vector2(vx, vy),
            angle    = angle,
            fuel     = fuel,
        )
    }

    /**
     * Shortest signed angle difference from [from] to [to], in degrees.
     * Result is in [-180, 180]. Going from 170° to -170° returns +20°, not -340°.
     */
    private fun shortestAngleDiff(to: Float, from: Float): Float {
        var diff = (to - from) % 360f
        if (diff >  180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return diff
    }

    private fun updateRope(pod: FuelPod, shipPos: Vector2, gravity: Float): FuelPod {
        var vel = pod.velocity + Vector2(0f, gravity * 0.6f)
        var pos = pod.position + vel
        val delta = pos - shipPos
        val dist  = delta.length()
        if (dist > PhysicsConstants.ROPE_LENGTH) {
            val dir    = delta.normalized()
            pos        = shipPos + dir * PhysicsConstants.ROPE_LENGTH
            val radial = dir.dot(vel)
            if (radial > 0f) vel = vel - dir * (radial * 0.85f)
        }
        return pod.copy(position = pos, velocity = vel)
    }

    private fun fireTurrets(
        turrets: List<Turret>,
        shipPos: Vector2,
    ): Pair<List<Turret>, List<Bullet>> {
        val newBullets = mutableListOf<Bullet>()
        val updated = turrets.map { t ->
            if (t.isDestroyed) return@map t
            val ticked = t.cooldownFrames - 1
            if (ticked <= 0) {
                val dir = (shipPos - t.config.position).normalized()
                newBullets += Bullet(
                    position = t.config.position + dir * 16f,
                    velocity = dir * t.config.bulletSpeed,
                    isEnemy  = true,
                )
                t.copy(cooldownFrames = t.config.firePeriodFrames)
            } else {
                t.copy(cooldownFrames = ticked)
            }
        }
        return updated to newBullets
    }
}