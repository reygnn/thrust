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
    /**
     * True wenn der Pod gerade aus dem Seil gerissen wurde und unter der
     * Schwerkraft fällt. Sobald er ein Terrain-Segment berührt setzt sich
     * dieses Flag wieder auf false (Pod liegt). Frisch platzierte oder von
     * der Engine zurückgesetzte Pods sind nicht "falling" — sie warten an
     * ihrer Position.
     */
    val isFalling: Boolean = false,
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
 * Runtime state of a turret.
 *
 * [cooldownFrames] is decremented by [com.github.reygnn.thrust.domain.engine.PhysicsEngine]
 * each frame. When it reaches zero the turret fires and is reset to
 * [TurretConfig.firePeriodFrames]. Initial values are staggered in
 * [GameState.initial] so that turrets sharing the same period don't fire
 * on the same frame.
 */
data class Turret(
    val config: TurretConfig,
    val cooldownFrames: Int = 0,    // frames until next shot; counts down each frame
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
/**
 * Player input for one frame.
 *
 * Two rotation modes are supported:
 * - **Button mode** (default): [rotateLeft] / [rotateRight] are booleans.
 *   The engine applies a fixed rotation step per frame in the indicated
 *   direction, exactly as before.
 * - **Wheel/slider mode**: [targetAngle] is non-null. The engine ignores
 *   [rotateLeft] / [rotateRight] and instead rotates the ship toward
 *   [targetAngle] at the same per-frame rate. The angle limit per frame is
 *   identical between modes — wheel mode is more *precise* (you specify the
 *   exact target), not faster.
 *
 * Rule: at most one of (rotateLeft/rotateRight) or targetAngle should be
 * active at a time. The UI layer is responsible for setting the correct
 * fields based on the active control mode.
 */

data class InputState(
    val rotateLeft:  Boolean = false,
    val rotateRight: Boolean = false,
    val thrust:      Boolean = false,
    val shoot:       Boolean = false,
    /** Slider/wheel mode target angle in degrees, normalized to [-180, 180]. */
    val targetAngle: Float?  = null,
)


// ── Full game state ───────────────────────────────────────────────────────────
data class GameState(
    val ship: Ship,
    val fuelPod: FuelPod,
    val bullets: List<Bullet>   = emptyList(),
    val turrets: List<Turret>   = emptyList(),
    val phase: GamePhase        = GamePhase.Playing,
    val score: Int              = 0,
    val lives: Int              = 3,
    val currentLevel: Int       = 1,
    val levelConfig: LevelConfig,
    val frameCount: Long        = 0L,
    val isThrusting: Boolean    = false,
    val playerFireCooldown: Int = 0,
) {
    companion object {
        fun initial(config: LevelConfig, score: Int = 0, lives: Int = 3) = GameState(
            ship    = Ship(position = config.shipStart, angle = config.shipStartAngle),
            fuelPod = FuelPod(position = config.fuelPodPosition),
            // Stagger initial cooldowns by index so turrets with equal firePeriodFrames
            // don't fire on the same frame. Offset = (idx * 7) % period is deterministic
            // and keeps tests reproducible while avoiding lock-step firing.
            turrets = config.turrets.mapIndexed { idx, cfg ->
                Turret(
                    config         = cfg,
                    cooldownFrames = cfg.firePeriodFrames - (idx * 7) % cfg.firePeriodFrames,
                )
            },
            score        = score,
            lives        = lives,
            currentLevel = config.id,
            levelConfig  = config,
        )
    }
}