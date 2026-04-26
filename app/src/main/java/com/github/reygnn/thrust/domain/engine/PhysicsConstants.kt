package com.github.reygnn.thrust.domain.engine

object PhysicsConstants {
    const val GRAVITY              = 0.04f
    const val ROTATION_SPEED       = 4f
    const val THRUST_POWER         = 0.12f
    const val FUEL_CONSUMPTION     = 0.3f
    const val MAX_SPEED            = 7f
    const val INITIAL_FUEL         = 1000f
    const val SHIP_RADIUS          = 18f
    const val POD_RADIUS           = 10f
    const val ROPE_LENGTH          = 100f
    const val BULLET_LIFETIME      = 150
    const val MAX_LANDING_SPEED_Y  = 2.5f
    const val MAX_LANDING_ANGLE    = 20f
    const val RESPAWN_FRAMES       = 90
    const val POD_PICKUP_RADIUS    = 40f
    const val FRAME_DELAY_MS           = 16L
    const val PLAYER_BULLET_SPEED      = 9f
    const val FIRE_COOLDOWN_FRAMES     = 18   // ~290 ms zwischen Schüssen
    const val PLAYER_BULLET_LIFETIME   = 120
}
