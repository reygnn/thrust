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
 * The visible rocket symbol points in the current target angle direction.
 * 0° = up, positive = clockwise (matches the engine's coordinate system).
 *
 * Implementation notes:
 *
 * 1. The current angle is held as **internal state**, not derived from a parameter.
 *    Earlier versions read the angle from a parameter inside the pointerInput
 *    block, which captured a stale value due to closure semantics — each drag
 *    delta was added to the same starting angle, causing the rotation to "stutter"
 *    around the initial value instead of accumulating. State must live where the
 *    pointer handler reads it.
 *
 * 2. [initialAngle] seeds the internal state on first composition only. Later
 *    changes to the parameter are intentionally NOT reflected, because the wheel
 *    is the source of truth while the user is interacting with it.
 *
 * 3. The double-tap detection uses dp→px conversion via [Density.toPx]; the
 *    PointerInputScope provides a Density implementation, but you must call
 *    `30.dp.toPx()` inside that scope, not access a `density` property directly.
 */
@Composable
fun RotationWheel(
    initialAngle: Float,
    onAngleChange: (Float) -> Unit,
    onFire: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val doubleTapWindowMs  = 350L
    val doubleTapMaxDistDp = 30.dp

    // The wheel's current target angle. Held internally so the pointer handler
    // always reads the up-to-date value (see implementation note 1 above).
    var currentAngle by remember { mutableFloatStateOf(initialAngle) }

    Box(
        modifier = modifier
            .size(144.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.5.dp, Color.White.copy(alpha = 0.30f), CircleShape)
            .pointerInput(Unit) {
                val centerXY = Offset(size.width / 2f, size.height / 2f)
                val tapMaxDistPx = doubleTapMaxDistDp.toPx()

                var lastTapTimeMs = 0L
                var lastTapPos: Offset? = null

                awaitEachGesture {
                    val down       = awaitFirstDown(requireUnconsumed = false)
                    val downPos    = down.position
                    var totalMovement = 0f
                    var dragInProgress = false

                    var prevAngle = atan2(
                        (downPos.y - centerXY.y).toDouble(),
                        (downPos.x - centerXY.x).toDouble(),
                    )

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!change.pressed) {
                            // Pointer up — evaluate tap vs. double-tap if it wasn't a drag.
                            val upTimeMs = System.currentTimeMillis()
                            change.consume()

                            if (!dragInProgress && totalMovement < tapMaxDistPx) {
                                val isDouble =
                                    upTimeMs - lastTapTimeMs <= doubleTapWindowMs &&
                                            lastTapPos != null &&
                                            (downPos - lastTapPos!!).getDistance() <= tapMaxDistPx
                                if (isDouble) {
                                    onFire()
                                    lastTapTimeMs = 0L
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

                        if (!dragInProgress && totalMovement > tapMaxDistPx) {
                            dragInProgress = true
                        }

                        if (dragInProgress) {
                            val pos = change.position
                            val curAngle = atan2(
                                (pos.y - centerXY.y).toDouble(),
                                (pos.x - centerXY.x).toDouble(),
                            )
                            var delta = Math.toDegrees(curAngle - prevAngle).toFloat()
                            if (delta >  180f) delta -= 360f
                            if (delta < -180f) delta += 360f

                            // Accumulate onto the up-to-date internal state, not the parameter.
                            var next = currentAngle + delta
                            if (next >  180f) next -= 360f
                            if (next < -180f) next += 360f

                            currentAngle = next
                            onAngleChange(next)

                            prevAngle = curAngle
                            change.consume()
                        }
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.size(144.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            // Canvas 0° points right; engine 0° points up. So rotate by angle - 90.
            rotate(degrees = currentAngle - 90f, pivot = Offset(cx, cy)) {
                val rocketLen = size.width * 0.30f
                val rocketWid = size.width * 0.10f
                val path = Path().apply {
                    moveTo(cx + rocketLen, cy)                              // tip
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