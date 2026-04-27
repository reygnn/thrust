package com.github.reygnn.thrust.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.github.reygnn.thrust.domain.engine.PhysicsConstants
import com.github.reygnn.thrust.domain.model.*

// ── Größen-Konstanten ─────────────────────────────────────────────────────────

private const val SHIP_W      = 22f
private const val SHIP_H      = 28f
private const val FLAME_BASE  = 22f
private const val FLAME_EXTRA = 14f
private const val POD_HALF    = 11f

/**
 * Vertikaler Camera-Offset als Anteil der Bildschirmhöhe.
 *
 * Die Rakete wird um diesen Anteil nach OBEN von der Bildschirmmitte verschoben,
 * damit sie nicht von der Steuerleiste am unteren Bildschirmrand verdeckt wird.
 *
 * Hinweis: gilt sowohl im Buttons- als auch im Wheel-Modus, weil in beiden
 * die Hand am unteren Bildschirmrand sitzt und Sichtbereich verdeckt.
 */
private const val CAMERA_VERTICAL_OFFSET = 0.18f

// ── Farben ────────────────────────────────────────────────────────────────────

private val BgColor          = Color(0xFF080C1E)
private val TerrainColor     = Color(0xFF3D6B4A)
private val TerrainGlow      = Color(0xFF2E7D32)
private val PadColor         = Color(0xFF00E5FF)
private val PadDelivered     = Color(0xFF00FF88)
private val ShipColor        = Color(0xFFEEEEEE)
private val CockpitColor     = Color(0xFF00E5FF)
private val FlameOuter       = Color(0xFFFF6D00)
private val FlameInner       = Color(0xFFFFEA00)
private val RopeColor        = Color(0xFFFFD700)
private val PodColor         = Color(0xFFFFD700)
private val PodBorder        = Color(0xFFFFA000)
private val TurretColor      = Color(0xFFFF1744)
private val TurretCore       = Color(0xFFFFFFFF)
private val EnemyBulletColor = Color(0xFFFF5252)
private val FriendlyBullet   = Color(0xFF69FF47)

// ── Öffentliches Composable ───────────────────────────────────────────────────

@Composable
fun GameCanvas(
    state: GameState,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Canvas(modifier = modifier) {
        val targetScreenY = size.height * (0.5f - CAMERA_VERTICAL_OFFSET)

        val camX = (state.ship.position.x - size.width  / 2f)
            .coerceIn(0f, (state.levelConfig.worldWidth  - size.width ).coerceAtLeast(0f))
        val camY = (state.ship.position.y - targetScreenY)
            .coerceIn(0f, (state.levelConfig.worldHeight - size.height).coerceAtLeast(0f))

        drawRect(BgColor)

        translate(-camX, -camY) {

            drawTerrain(state.levelConfig.terrain)
            drawLandingPad(state.levelConfig.landingPad, state.fuelPod.isDelivered)

            if (!state.fuelPod.isDelivered) {
                drawFuelPod(state.fuelPod, state.ship)
            }

            state.turrets.filterNot { it.isDestroyed }.forEach { drawTurret(it) }
            state.bullets.forEach { drawBullet(it) }

            if (state.ship.isAlive) {
                drawShip(state.ship, state.isThrusting, state.frameCount)
            } else if (state.ship.respawnTimer > 0) {
                drawExplosionMarker(state.ship.position)
            }
        }
    }
}

// ── Terrain ───────────────────────────────────────────────────────────────────

private fun DrawScope.drawTerrain(terrain: List<TerrainSegment>) {
    terrain.forEach { seg ->
        drawLine(
            color       = TerrainColor,
            start       = Offset(seg.start.x, seg.start.y),
            end         = Offset(seg.end.x,   seg.end.y),
            strokeWidth = 4f,
            cap         = StrokeCap.Round,
        )
        drawLine(
            color       = TerrainGlow.copy(alpha = 0.35f),
            start       = Offset(seg.start.x, seg.start.y),
            end         = Offset(seg.end.x,   seg.end.y),
            strokeWidth = 1.5f,
            cap         = StrokeCap.Round,
        )
    }
}

// ── Landeplatte ───────────────────────────────────────────────────────────────

private fun DrawScope.drawLandingPad(pad: LandingPad, isDelivered: Boolean) {
    val color = if (isDelivered) PadDelivered else PadColor
    val alpha = if (isDelivered) 1f else 0.9f

    drawLine(
        color       = color.copy(alpha = alpha),
        start       = Offset(pad.left, pad.y),
        end         = Offset(pad.right, pad.y),
        strokeWidth = 6f,
    )

    val beaconCount = 5
    val spacing     = (pad.right - pad.left) / (beaconCount - 1)
    repeat(beaconCount) { i ->
        val bx = pad.left + i * spacing
        drawCircle(color.copy(alpha = 0.8f), radius = 5f, center = Offset(bx, pad.y - 4f))
    }
}

// ── Fuel Pod ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawFuelPod(pod: FuelPod, ship: Ship) {
    if (pod.isPickedUp) {
        drawLine(
            color       = RopeColor.copy(alpha = 0.7f),
            start       = Offset(ship.position.x, ship.position.y),
            end         = Offset(pod.position.x,  pod.position.y),
            strokeWidth = 1.8f,
            pathEffect  = PathEffect.dashPathEffect(floatArrayOf(9f, 5f)),
        )
    }

    val px = pod.position.x; val py = pod.position.y
    drawRect(
        color   = PodColor,
        topLeft = Offset(px - POD_HALF, py - POD_HALF),
        size    = Size(POD_HALF * 2, POD_HALF * 2),
    )
    drawRect(
        color   = PodBorder,
        topLeft = Offset(px - POD_HALF, py - POD_HALF),
        size    = Size(POD_HALF * 2, POD_HALF * 2),
        style   = Stroke(width = 2f),
    )
    drawLine(PodBorder, Offset(px - 5f, py), Offset(px + 5f, py), strokeWidth = 1.5f)
    drawLine(PodBorder, Offset(px, py - 5f), Offset(px, py + 5f), strokeWidth = 1.5f)
}

// ── Schiff ───────────────────────────────────────────────────────────────────

private fun DrawScope.drawShip(ship: Ship, isThrusting: Boolean, frame: Long) {
    withTransform({
        translate(ship.position.x, ship.position.y)
        rotate(degrees = ship.angle, pivot = Offset.Zero)
    }) {
        val path = Path().apply {
            moveTo(0f, -SHIP_H / 2f)
            lineTo(-SHIP_W / 2f,  SHIP_H / 2f)
            lineTo( SHIP_W / 2f,  SHIP_H / 2f)
            close()
        }
        drawPath(
            path  = path,
            color = ShipColor,
            style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        drawCircle(CockpitColor, radius = 4f, center = Offset(0f, -5f))

        if (isThrusting) {
            val varLen = if (frame % 2L == 0L) FLAME_BASE + FLAME_EXTRA else FLAME_BASE
            val flame  = Path().apply {
                moveTo(-SHIP_W / 3f, SHIP_H / 2f)
                lineTo(0f, SHIP_H / 2f + varLen)
                lineTo( SHIP_W / 3f, SHIP_H / 2f)
                close()
            }
            drawPath(flame, FlameOuter, style = Fill)

            val inner = Path().apply {
                moveTo(-SHIP_W / 5f, SHIP_H / 2f)
                lineTo(0f, SHIP_H / 2f + varLen * 0.55f)
                lineTo( SHIP_W / 5f, SHIP_H / 2f)
                close()
            }
            drawPath(inner, FlameInner, style = Fill)
        }
    }
}

// ── Explosions-Marker ─────────────────────────────────────────────────────────

private fun DrawScope.drawExplosionMarker(pos: Vector2) {
    drawCircle(
        color  = Color(0xFFFF6D00).copy(alpha = 0.4f),
        radius = 30f,
        center = Offset(pos.x, pos.y),
    )
}

// ── Turret ───────────────────────────────────────────────────────────────────

private fun DrawScope.drawTurret(turret: Turret) {
    val pos = turret.config.position
    drawCircle(TurretColor, radius = 13f, center = Offset(pos.x, pos.y))
    drawCircle(TurretColor, radius = 13f, center = Offset(pos.x, pos.y), style = Stroke(2.5f))
    drawCircle(TurretCore,  radius = 4f,  center = Offset(pos.x, pos.y))
    drawLine(
        color       = TurretColor,
        start       = Offset(pos.x, pos.y),
        end         = Offset(pos.x, pos.y - 18f),
        strokeWidth = 4f,
        cap         = StrokeCap.Round,
    )
}

// ── Bullet ───────────────────────────────────────────────────────────────────

private fun DrawScope.drawBullet(bullet: Bullet) {
    val color = if (bullet.isEnemy) EnemyBulletColor else FriendlyBullet
    drawCircle(color, radius = 3.5f, center = Offset(bullet.position.x, bullet.position.y))
}