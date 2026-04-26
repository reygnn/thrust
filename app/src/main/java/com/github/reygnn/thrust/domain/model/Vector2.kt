package com.github.reygnn.thrust.domain.model

import kotlin.math.sqrt

data class Vector2(val x: Float, val y: Float) {
    operator fun plus(other: Vector2) = Vector2(x + other.x, y + other.y)
    operator fun minus(other: Vector2) = Vector2(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vector2(x * scalar, y * scalar)
    operator fun unaryMinus() = Vector2(-x, -y)

    fun length(): Float = sqrt(x * x + y * y)

    fun normalized(): Vector2 {
        val len = length()
        return if (len < 0.0001f) Zero else Vector2(x / len, y / len)
    }

    fun dot(other: Vector2): Float = x * other.x + y * other.y

    companion object {
        val Zero = Vector2(0f, 0f)
    }
}
