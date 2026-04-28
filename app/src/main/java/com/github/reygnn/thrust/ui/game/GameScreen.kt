package com.github.reygnn.thrust.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.thrust.R
import com.github.reygnn.thrust.data.ControlMode
import com.github.reygnn.thrust.data.ThrustSide
import com.github.reygnn.thrust.data.WheelSize
import com.github.reygnn.thrust.domain.engine.PhysicsConstants
import com.github.reygnn.thrust.domain.model.*
import com.github.reygnn.thrust.ui.theme.ThrustCyan
import com.github.reygnn.thrust.ui.theme.ThrustGold
import com.github.reygnn.thrust.ui.theme.ThrustGreen
import com.github.reygnn.thrust.ui.theme.ThrustRed

@Composable
fun GameScreen(
    onNavigateBack: () -> Unit,
    vm: GameViewModel = viewModel(factory = GameViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) vm.pauseForBackground()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(vm) {
        vm.navEvents.collect { onNavigateBack() }
    }

    val gunEnabled  by vm.playerGunEnabled.collectAsStateWithLifecycle()
    val controlMode by vm.controlMode.collectAsStateWithLifecycle()
    val thrustSide  by vm.thrustSide.collectAsStateWithLifecycle()
    val wheelSize   by vm.wheelSize.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        GameCanvas(state = state)
        GameHud(state = state, modifier = Modifier.align(Alignment.TopStart))

        IconButton(
            onClick  = vm::togglePause,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            Text(
                text  = if (state.phase == GamePhase.Paused) "▶" else "⏸",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        when (controlMode) {
            ControlMode.BUTTONS -> GameControls(
                onRotateLeft     = vm::onRotateLeft,
                onRotateRight    = vm::onRotateRight,
                onThrust         = vm::onThrust,
                onFire           = vm::onFire,
                playerGunEnabled = gunEnabled,
                modifier         = Modifier.align(Alignment.BottomCenter),
            )
            ControlMode.WHEEL -> WheelControls(
                initialAngle     = state.ship.angle,
                onTargetAngle    = vm::onTargetAngleChange,
                onThrust         = vm::onThrust,
                onFire           = vm::onFireTriggered,
                playerGunEnabled = gunEnabled,
                thrustSide       = thrustSide,
                wheelSize        = wheelSize,
                modifier         = Modifier.align(Alignment.BottomCenter),
            )
        }

        when (val phase = state.phase) {
            is GamePhase.LevelComplete -> LevelCompleteOverlay(
                score     = phase.score,
                levelName = state.levelConfig.name,
                onNext    = vm::advanceToNextLevel,
            )
            GamePhase.GameOver -> GameOverOverlay(
                score   = state.score,
                onQuit  = vm::onGameOverConfirmed,
                onRetry = vm::restartLevel,
            )
            GamePhase.Paused -> PausedOverlay(
                onResume = vm::togglePause,
                onQuit   = vm::onGameOverConfirmed,
            )
            else -> Unit
        }
    }
}

// ── HUD ───────────────────────────────────────────────────────────────────────

@Composable
private fun GameHud(state: GameState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(12.dp)
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text  = state.levelConfig.name.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = ThrustCyan,
        )
        Text(
            text  = stringResource(R.string.hud_score, state.score),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(state.lives.coerceAtLeast(0)) {
                Text("▲", color = ThrustGreen, style = MaterialTheme.typography.bodyLarge)
            }
        }
        val fuelPct = (state.ship.fuel / PhysicsConstants.INITIAL_FUEL).coerceIn(0f, 1f)
        val fuelColor = when {
            fuelPct > 0.40f -> ThrustGreen
            fuelPct > 0.15f -> ThrustGold
            else            -> ThrustRed
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.hud_fuel),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f))
            LinearProgressIndicator(
                progress    = { fuelPct },
                modifier    = Modifier.width(90.dp).height(8.dp),
                color       = fuelColor,
                trackColor  = Color.White.copy(alpha = 0.15f),
            )
        }
        val (podText, podColor) = when {
            state.fuelPod.isDelivered -> stringResource(R.string.hud_pod_delivered) to ThrustGreen
            state.fuelPod.isPickedUp  -> stringResource(R.string.hud_pod_picked)    to ThrustGold
            else                      -> stringResource(R.string.hud_pod_idle)      to Color.White.copy(alpha = 0.5f)
        }
        Text(podText, style = MaterialTheme.typography.labelLarge, color = podColor)
        if (!state.ship.isAlive && state.ship.respawnTimer > 0) {
            val secs = "%.1f".format(state.ship.respawnTimer / 60f)
            Text(
                text  = stringResource(R.string.hud_respawn, secs),
                style = MaterialTheme.typography.labelLarge,
                color = ThrustRed,
            )
        }
    }
}

// ── Buttons-Steuerung ────────────────────────────────────────────────────────

@Composable
private fun GameControls(
    onRotateLeft:     (Boolean) -> Unit,
    onRotateRight:    (Boolean) -> Unit,
    onThrust:         (Boolean) -> Unit,
    onFire:           (Boolean) -> Unit,
    playerGunEnabled: Boolean,
    modifier:         Modifier = Modifier,
) {
    Row(
        modifier              = modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        ControlButton(label = "◄", size = 72, onPress = onRotateLeft)
        ControlButton(label = stringResource(R.string.control_thrust_label), size = 88, onPress = onThrust)
        ControlButton(label = "►", size = 72, onPress = onRotateRight)
        if (playerGunEnabled) {
            ControlButton(
                label     = stringResource(R.string.control_fire_label),
                size      = 72,
                tintColor = Color(0xFFFF5252),
                onPress   = onFire,
            )
        }
    }
}

// ── Drehrad-Steuerung ────────────────────────────────────────────────────────

@Composable
private fun WheelControls(
    initialAngle:     Float,
    onTargetAngle:    (Float?) -> Unit,
    onThrust:         (Boolean) -> Unit,
    onFire:           () -> Unit,
    playerGunEnabled: Boolean,
    thrustSide:       ThrustSide,
    wheelSize:        WheelSize,
    modifier:         Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onTargetAngle(initialAngle) }
    DisposableEffect(Unit) { onDispose { onTargetAngle(null) } }

    Row(
        modifier              = modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Bottom,
    ) {
        if (thrustSide == ThrustSide.LEFT) {
            ControlButton(
                label   = stringResource(R.string.control_thrust_label),
                size    = 88,
                onPress = onThrust,
            )
            RotationWheel(
                initialAngle  = initialAngle,
                diameter      = wheelSize.diameter,
                onAngleChange = onTargetAngle,
                onFire        = { if (playerGunEnabled) onFire() },
            )
        } else {
            RotationWheel(
                initialAngle  = initialAngle,
                diameter      = wheelSize.diameter,
                onAngleChange = onTargetAngle,
                onFire        = { if (playerGunEnabled) onFire() },
            )
            ControlButton(
                label   = stringResource(R.string.control_thrust_label),
                size    = 88,
                onPress = onThrust,
            )
        }
    }
}

@Composable
private fun ControlButton(
    label:     String,
    size:      Int,
    tintColor: Color = Color.White,
    onPress:   (Boolean) -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(tintColor.copy(alpha = 0.10f))
            .border(1.5.dp, tintColor.copy(alpha = 0.35f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress(true)
                        tryAwaitRelease()
                        onPress(false)
                    },
                )
            },
    ) {
        Text(
            text      = label,
            color     = tintColor,
            style     = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Overlays ─────────────────────────────────────────────────────────────────

@Composable
private fun LevelCompleteOverlay(score: Int, levelName: String, onNext: () -> Unit) {
    Box(
        modifier         = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier            = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.level_complete_title), style = MaterialTheme.typography.headlineMedium, color = ThrustGreen)
                Text(levelName, style = MaterialTheme.typography.titleLarge, color = ThrustCyan)
                Text(stringResource(R.string.level_complete_score, score), style = MaterialTheme.typography.headlineMedium)
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.level_complete_next))
                }
            }
        }
    }
}

@Composable
private fun GameOverOverlay(score: Int, onQuit: () -> Unit, onRetry: () -> Unit) {
    Box(
        modifier         = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier            = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.game_over_title), style = MaterialTheme.typography.headlineMedium, color = ThrustRed)
                Text(stringResource(R.string.game_over_final_score, score), style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onQuit, modifier = Modifier.weight(1f))  { Text(stringResource(R.string.game_over_menu)) }
                    Button(onClick = onRetry, modifier = Modifier.weight(1f))         { Text(stringResource(R.string.game_over_retry)) }
                }
            }
        }
    }
}

@Composable
private fun PausedOverlay(onResume: () -> Unit, onQuit: () -> Unit) {
    Box(
        modifier         = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.padding(32.dp)) {
            Column(
                modifier            = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.pause_title), style = MaterialTheme.typography.headlineLarge)
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth())   { Text(stringResource(R.string.pause_resume)) }
                OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pause_quit_to_menu)) }
            }
        }
    }
}