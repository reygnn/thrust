package com.github.reygnn.thrust.domain.model

import com.github.reygnn.thrust.domain.engine.PhysicsConstants

// ── Ship ────────────────────────────────────────────────────────────────────
data class Ship(
    val position: Vector2,
    val velocity: Vector2 = Vector2.Zero,
    val angle: Float = 0f,          // degrees; 0 = nose up, CW positive
    val fuel: Float = PhysicsConstants.INITIAL_FUEL,
    val isAlive: Boolean = true,
    val respawnTimer: Int = 0,
    val hasPod: Boolean = false,    // rope attached
)

// ── FuelPod ──────────────────────────────────────────────────────────────────
data class FuelPod(
    val position: Vector2,
    val velocity: Vector2 = Vector2.Zero,
    val isPickedUp: Boolean = false,
    val isDelivered: Boolean = false,
)

// ── Bullet ───────────────────────────────────────────────────────────────────
data class Bullet(
    val position: Vector2,
    val velocity: Vector2,
    val isEnemy: Boolean,
    val lifeFrames: Int = PhysicsConstants.BULLET_LIFETIME,
)

// ── Turret ───────────────────────────────────────────────────────────────────
data class TurretConfig(
    val position: Vector2,
    val firePeriodFrames: Int,
    val bulletSpeed: Float = 4.5f,
)

/**
 * Runtime state of a turret. The fire timing is derived directly from
 * [GameState.frameCount] modulo [TurretConfig.firePeriodFrames] in
 * [com.github.reygnn.thrust.domain.engine.PhysicsEngine.fireTurrets] – we
 * don't keep a per-turret countdown. If you ever need staggered firing
 * (multiple turrets with the same period firing on different frames),
 * reintroduce a per-turret offset or cooldown counter here.
 */
data class Turret(
    val config: TurretConfig,
    val isDestroyed: Boolean = false,
)

// ── Terrain / Pad ─────────────────────────────────────────────────────────────
data class TerrainSegment(val start: Vector2, val end: Vector2)

data class LandingPad(
    val center: Vector2,
    val halfWidth: Float,
) {
    val left: Float  get() = center.x - halfWidth
    val right: Float get() = center.x + halfWidth
    val y: Float     get() = center.y
}

// ── Level ─────────────────────────────────────────────────────────────────────
data class LevelConfig(
    val id: Int,
    val name: String,
    val worldWidth: Float,
    val worldHeight: Float,
    val terrain: List<TerrainSegment>,
    val landingPad: LandingPad,
    val shipStart: Vector2,
    val shipStartAngle: Float = 0f,
    val fuelPodPosition: Vector2,
    val turrets: List<TurretConfig> = emptyList(),
    val gravity: Float = PhysicsConstants.GRAVITY,
)

// ── Game phase ────────────────────────────────────────────────────────────────
sealed interface GamePhase {
    data object Playing      : GamePhase
    data object Paused       : GamePhase
    data class  LevelComplete(val score: Int) : GamePhase
    data object GameOver     : GamePhase
    data object Victory      : GamePhase
}

// ── Input ─────────────────────────────────────────────────────────────────────
data class InputState(
    val rotateLeft: Boolean  = false,
    val rotateRight: Boolean = false,
    val thrust: Boolean      = false,
    val shoot: Boolean       = false,
)

// ── Full game state ───────────────────────────────────────────────────────────
data class GameState(
    val ship: Ship,
    val fuelPod: FuelPod,
    val bullets: List<Bullet>      = emptyList(),
    val turrets: List<Turret>      = emptyList(),
    val phase: GamePhase           = GamePhase.Playing,
    val score: Int                 = 0,
    val lives: Int                 = 3,
    val currentLevel: Int          = 1,
    val levelConfig: LevelConfig,
    val frameCount: Long           = 0L,
    val isThrusting: Boolean       = false,
    val playerFireCooldown: Int    = 0,   // frames remaining until next shot allowed
) {
    companion object {
        fun initial(config: LevelConfig, score: Int = 0, lives: Int = 3) = GameState(
            ship        = Ship(position = config.shipStart, angle = config.shipStartAngle),
            fuelPod     = FuelPod(position = config.fuelPodPosition),
            turrets     = config.turrets.map { Turret(config = it) },
            score       = score,
            lives       = lives,
            currentLevel = config.id,
            levelConfig = config,
        )
    }
}