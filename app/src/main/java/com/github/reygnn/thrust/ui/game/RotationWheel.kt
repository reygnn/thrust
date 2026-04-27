package com.github.reygnn.thrust.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2

/**
 * 144dp rotary wheel for ship steering.
 *
 * Behavior:
 * - Touch the wheel to start a drag. The angle from the wheel's center to the
 *   touch point is the reference; subsequent rotation around that center
 *   updates the target angle delta.
 * - Drag input is RELATIVE: starting position doesn't change the angle —
 *   only rotation around the wheel's center does.
 * - Releasing leaves the angle as-is (no spring-back).
 * - Double-tap fires (only when the previous touch has lifted; not detectable
 *   while a drag is in progress).
 *
 * The visible rocket symbol always points in the current target angle direction.
 * 0° = up, positive = clockwise (matches the engine's coordinate system).
 */
@Composable
fun RotationWheel(
    angleDegrees: Float,
    onAngleChange: (Float) -> Unit,
    onFire: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Track tap timing for double-tap detection.
    val doubleTapWindowMs = 350L
    val doubleTapMaxDistDp = 30f
    var lastTapTimeMs by remember { mutableStateOf(0L) }
    var lastTapPos by remember { mutableStateOf<Offset?>(null) }
    var dragInProgress by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(144.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.5.dp, Color.White.copy(alpha = 0.30f), CircleShape)
            .pointerInput(Unit) {
                val centerXY = Offset(size.width / 2f, size.height / 2f)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    val downTimeMs = System.currentTimeMillis()
                    var totalMovement = 0f

                    // Track the previous angle from the wheel's center so we can
                    // compute the delta as the finger moves around.
                    var prevAngle = atan2(
                        (downPos.y - centerXY.y).toDouble(),
                        (downPos.x - centerXY.x).toDouble(),
                    )

                    dragInProgress = false

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            // Pointer up.
                            val upTimeMs = System.currentTimeMillis()
                            val gestureWasDrag = dragInProgress
                            dragInProgress = false
                            change.consume()

                            // If the gesture was a tap (not a drag), evaluate double-tap.
                            if (!gestureWasDrag && totalMovement < doubleTapMaxDistDp * density) {
                                val isDouble =
                                    upTimeMs - lastTapTimeMs <= doubleTapWindowMs &&
                                            lastTapPos != null &&
                                            (downPos - lastTapPos!!).getDistance() <= doubleTapMaxDistDp * density
                                if (isDouble) {
                                    onFire()
                                    lastTapTimeMs = 0L  // reset so a triple-tap doesn't fire twice
                                    lastTapPos = null
                                } else {
                                    lastTapTimeMs = upTimeMs
                                    lastTapPos = downPos
                                }
                            }
                            break
                        }

                        val moved = change.positionChange()
                        totalMovement += moved.getDistance()

                        // Once movement exceeds the tap-vs-drag threshold, consider it a drag.
                        if (!dragInProgress && totalMovement > doubleTapMaxDistDp * density) {
                            dragInProgress = true
                        }

                        if (dragInProgress) {
                            val pos = change.position
                            val curAngle = atan2(
                                (pos.y - centerXY.y).toDouble(),
                                (pos.x - centerXY.x).toDouble(),
                            )
                            var delta = Math.toDegrees(curAngle - prevAngle).toFloat()
                            // Normalize delta to [-180, 180]
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f

                            // Update target angle. Engine will normalize.
                            var newAngle = angleDegrees + delta
                            if (newAngle > 180f) newAngle -= 360f
                            if (newAngle < -180f) newAngle += 360f
                            onAngleChange(newAngle)

                            prevAngle = curAngle
                            change.consume()
                        }
                    }
                }
            },
    ) {
        // Rocket symbol pointing in the current angle direction.
        Canvas(modifier = Modifier.size(144.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            // Rotate canvas to match angle. 0° = up means a -90° canvas rotation
            // because canvas 0° points right. So rotation in canvas = angleDegrees - 90.
            rotate(degrees = angleDegrees - 90f, pivot = Offset(cx, cy)) {
                // Simple triangle-style rocket pointing in +X direction (after the rotation, this maps to angleDegrees).
                val rocketLen = size.width * 0.30f
                val rocketWid = size.width * 0.10f
                val path = Path().apply {
                    moveTo(cx + rocketLen, cy)              // tip
                    lineTo(cx - rocketLen * 0.5f, cy - rocketWid)
                    lineTo(cx - rocketLen * 0.3f, cy)
                    lineTo(cx - rocketLen * 0.5f, cy + rocketWid)
                    close()
                }
                drawPath(path = path, color = Color.White)
            }
        }
    }
}