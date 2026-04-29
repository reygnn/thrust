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

        // Frame-Start-Snapshot: gebraucht für die Death-Branches unten. Wenn der
        // Pod im selben Frame hart abreißt (isPickedUp wechselt auf false) und
        // das Schiff danach stirbt, fragen die Death-Pfade `pod.isPickedUp` zu
        // spät ab und lassen den Pod mitten im Fall stehen statt ihn auf
        // Startposition zurückzusetzen.
        val podWasAttached = state.fuelPod.isPickedUp

        var ship = applyShipPhysics(state.ship, input, state.levelConfig.gravity)

        var pod = when {
            state.fuelPod.isDelivered  -> state.fuelPod
            state.fuelPod.isPickedUp   -> {
                // Pod hängt am Seil. Pendel-Update, dann auf Terrain prüfen.
                // Leichte Berührung → Pod prallt ab und bleibt am Seil. Erst
                // bei harter Kollision (impactSpeed > Schwelle) reißt das Seil.
                val swung = updateRope(state.fuelPod, ship.position, state.levelConfig.gravity)
                val hit = collisionDetector.firstCollidingSegment(swung.position, PhysicsConstants.POD_RADIUS, state.levelConfig.terrain)
                if (hit != null) {
                    val info = collisionInfo(swung.position, hit)
                    val impact = -swung.velocity.dot(info.normal)
                    val bounced = bouncePod(swung, info)
                    if (impact > HARD_HIT_THRESHOLD) {
                        ship = ship.copy(hasPod = false)
                        bounced.copy(isPickedUp = false, isFalling = true)
                    } else {
                        // Liegt die Wand zwischen Schiff und Pod, schiebt der
                        // Bounce den Pod über die Seillänge hinaus. Ohne erneute
                        // Constraint snappt das Seil im nächsten Frame zurück in
                        // dieselbe Wand → Mikro-Oszillation. Hier gleich klemmen.
                        val (clampedPos, clampedVel) =
                            applyRopeConstraint(bounced.position, bounced.velocity, ship.position)
                        bounced.copy(position = clampedPos, velocity = clampedVel)
                    }
                } else {
                    swung
                }
            }
            state.fuelPod.isFalling -> {
                // Frei fallender Pod unter Schwerkraft. Bei Terrain-Kontakt
                // wird abgeprallt; sinkt die Energie unter die Settle-Schwelle,
                // bleibt der Pod liegen. Höhere Fallhöhe → mehr Bounces (Damping
                // 0.5 gibt typischerweise 2-3 Sprünge bei mittleren Fällen).
                val newVel = state.fuelPod.velocity + Vector2(0f, state.levelConfig.gravity * 0.6f)
                val newPos = state.fuelPod.position + newVel
                val movedPod = state.fuelPod.copy(position = newPos, velocity = newVel)
                val hit = collisionDetector.firstCollidingSegment(movedPod.position, PhysicsConstants.POD_RADIUS, state.levelConfig.terrain)
                if (hit != null) {
                    val info = collisionInfo(movedPod.position, hit)
                    val bounced = bouncePod(movedPod, info)
                    if (bounced.velocity.length() < POD_SETTLE_THRESHOLD) {
                        bounced.copy(velocity = Vector2.Zero, isFalling = false)
                    } else {
                        bounced
                    }
                } else {
                    movedPod
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
                fuelPod = if (podWasAttached && !pod.isDelivered) FuelPod(position = state.levelConfig.fuelPodPosition) else pod,
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
                    fuelPod = if (podWasAttached && !pod.isDelivered) FuelPod(position = state.levelConfig.fuelPodPosition) else pod,
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
        val velAfterGravity = pod.velocity + Vector2(0f, gravity * 0.6f)
        val integratedPos   = pod.position + velAfterGravity
        val (pos, vel) = applyRopeConstraint(integratedPos, velAfterGravity, shipPos)
        return pod.copy(position = pos, velocity = vel)
    }

    /**
     * Klemmt eine Position auf maximal [PhysicsConstants.ROPE_LENGTH] vom
     * Schiff weg und dämpft den nach außen zeigenden Geschwindigkeitsanteil
     * um 15 %. Wird sowohl vom regulären Rope-Update als auch nach einem
     * Bounce gegen Terrain aufgerufen — beim Bounce, weil `bouncePod` den
     * Pod blind aus der Wand drückt und dabei die Seillänge sprengen kann.
     */
    private fun applyRopeConstraint(
        pos: Vector2,
        vel: Vector2,
        shipPos: Vector2,
    ): Pair<Vector2, Vector2> {
        val delta = pos - shipPos
        val dist  = delta.length()
        if (dist <= PhysicsConstants.ROPE_LENGTH) return pos to vel
        val dir       = delta.normalized()
        val clampedPos = shipPos + dir * PhysicsConstants.ROPE_LENGTH
        val radial    = dir.dot(vel)
        val clampedVel = if (radial > 0f) vel - dir * (radial * 0.85f) else vel
        return clampedPos to clampedVel
    }

    private data class CollisionInfo(val normal: Vector2, val closest: Vector2)

    /**
     * Berechnet die Kollisions-Normale (Vektor von der Wandoberfläche zum
     * Kreis-Mittelpunkt) und den nächsten Punkt auf dem Segment.
     */
    private fun collisionInfo(pos: Vector2, hit: TerrainSegment): CollisionInfo {
        val ab   = hit.end - hit.start
        val ap   = pos - hit.start
        val len2 = ab.dot(ab)
        val t    = if (len2 == 0f) 0f else (ap.dot(ab) / len2).coerceIn(0f, 1f)
        val closest = hit.start + ab * t
        val toPod = pos - closest
        val toLen = toPod.length()
        // Fallback-Normale falls Pod-Position exakt auf dem Segment liegt.
        val n = if (toLen > 0.001f) toPod * (1f / toLen) else Vector2(0f, -1f)
        return CollisionInfo(n, closest)
    }

    /**
     * Wendet eine elastische, gedämpfte Reflexion an einem Terrain-Segment an
     * und schiebt den Pod aus der Wand heraus, damit das Frame nicht im selben
     * Segment endet.
     */
    private fun bouncePod(pod: FuelPod, info: CollisionInfo): FuelPod {
        val vDotN = pod.velocity.dot(info.normal)
        val reflected = if (vDotN < 0f) pod.velocity - info.normal * (2f * vDotN) else pod.velocity
        val damped = reflected * BOUNCE_DAMPING
        val pushedOut = info.closest + info.normal * (PhysicsConstants.POD_RADIUS + 0.5f)
        return pod.copy(position = pushedOut, velocity = damped)
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

    companion object {
        /**
         * Geschwindigkeitsanteil entlang der Wandnormale, ab dem das Seil
         * reißt. Darunter prallt der Pod nur ab und bleibt am Schiff. Bezug
         * sind Schiff-typische Speeds (MAX_SPEED = 7); Pods am Seil haben
         * meist deutlich darunter, harte Schwingen aber knacken die Schwelle.
         */
        private const val HARD_HIT_THRESHOLD = 3.5f
        /** Energie-Verlust pro Bounce (50% behalten). */
        private const val BOUNCE_DAMPING = 0.5f
        /** |v| unter dem ein gefallener Pod als ruhend gilt. */
        private const val POD_SETTLE_THRESHOLD = 0.8f
    }
}