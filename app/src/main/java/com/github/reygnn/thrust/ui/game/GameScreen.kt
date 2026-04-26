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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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

    // Lifecycle-Observer: App in Hintergrund → pausieren
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) vm.pauseForBackground()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Nav-Events abhören
    LaunchedEffect(vm) {
        vm.navEvents.collect { onNavigateBack() }
    }

    val gunEnabled by vm.playerGunEnabled.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        // Spielfeld
        GameCanvas(state = state)

        // HUD
        GameHud(state = state, modifier = Modifier.align(Alignment.TopStart))

        // Steuerung
        GameControls(
            onInputChanged   = vm::setInput,
            onPauseToggle    = vm::togglePause,
            isPaused         = state.phase == GamePhase.Paused,
            playerGunEnabled = gunEnabled,
            onFire           = vm::onFire,
            modifier         = Modifier.align(Alignment.BottomCenter),
        )

        // Overlays
        when (val phase = state.phase) {
            is GamePhase.LevelComplete -> LevelCompleteOverlay(
                score     = phase.score,
                levelName = state.levelConfig.name,
                onNext    = vm::advanceToNextLevel,
            )
            GamePhase.GameOver -> GameOverOverlay(
                score  = state.score,
                onQuit = vm::onGameOverConfirmed,
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
        // Level-Name
        Text(
            text  = state.levelConfig.name.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = ThrustCyan,
        )

        // Score
        Text(
            text  = "SCORE ${state.score}",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )

        // Leben
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(state.lives.coerceAtLeast(0)) {
                Text("▲", color = ThrustGreen, style = MaterialTheme.typography.bodyLarge)
            }
        }

        // Treibstoff-Balken
        val fuelPct = (state.ship.fuel / PhysicsConstants.INITIAL_FUEL).coerceIn(0f, 1f)
        val fuelColor = when {
            fuelPct > 0.40f -> ThrustGreen
            fuelPct > 0.15f -> ThrustGold
            else            -> ThrustRed
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("FUEL", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
            LinearProgressIndicator(
                progress    = { fuelPct },
                modifier    = Modifier.width(90.dp).height(8.dp),
                color       = fuelColor,
                trackColor  = Color.White.copy(alpha = 0.15f),
            )
        }

        // Pod-Status
        val podStatus = when {
            state.fuelPod.isDelivered -> "POD ✓" to ThrustGreen
            state.fuelPod.isPickedUp  -> "POD ○" to ThrustGold
            else                      -> "POD ×" to Color.White.copy(alpha = 0.5f)
        }
        Text(podStatus.first, style = MaterialTheme.typography.labelLarge, color = podStatus.second)

        // Respawn-Countdown
        if (!state.ship.isAlive && state.ship.respawnTimer > 0) {
            val secs = "%.1f".format(state.ship.respawnTimer / 60f)
            Text("RESPAWN $secs", style = MaterialTheme.typography.labelLarge, color = ThrustRed)
        }
    }
}

// ── Steuerung ─────────────────────────────────────────────────────────────────

@Composable
private fun GameControls(
    onInputChanged:   (InputState) -> Unit,
    onPauseToggle:    () -> Unit,
    isPaused:         Boolean,
    playerGunEnabled: Boolean,
    onFire:           (Boolean) -> Unit,
    modifier:         Modifier = Modifier,
) {
    var rotL  by remember { mutableStateOf(false) }
    var rotR  by remember { mutableStateOf(false) }
    var thr   by remember { mutableStateOf(false) }

    fun pushInput() = onInputChanged(InputState(rotL, rotR, thr))

    Box(modifier = modifier.fillMaxWidth().padding(bottom = 20.dp, start = 16.dp, end = 16.dp)) {

        IconButton(
            onClick  = onPauseToggle,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Text(
                text  = if (isPaused) "▶" else "⏸",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Row(
            modifier              = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            // Links drehen
            ControlButton(label = "◄", size = 72) { pressed ->
                rotL = pressed; pushInput()
            }

            // Schub
            ControlButton(label = "▲\nSCHUB", size = 88) { pressed ->
                thr = pressed; pushInput()
            }

            // Rechts drehen
            ControlButton(label = "►", size = 72) { pressed ->
                rotR = pressed; pushInput()
            }

            // FIRE – nur sichtbar wenn Kanone aktiviert
            if (playerGunEnabled) {
                ControlButton(
                    label     = "🔥\nFIRE",
                    size      = 72,
                    tintColor = Color(0xFFFF5252),
                ) { pressed -> onFire(pressed) }
            }
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
                modifier                = Modifier.padding(32.dp),
                horizontalAlignment     = Alignment.CenterHorizontally,
                verticalArrangement     = Arrangement.spacedBy(16.dp),
            ) {
                Text("MISSION COMPLETE", style = MaterialTheme.typography.headlineMedium, color = ThrustGreen)
                Text(levelName, style = MaterialTheme.typography.titleLarge, color = ThrustCyan)
                Text("Score: $score", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Text("NÄCHSTES LEVEL")
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
                modifier                = Modifier.padding(32.dp),
                horizontalAlignment     = Alignment.CenterHorizontally,
                verticalArrangement     = Arrangement.spacedBy(16.dp),
            ) {
                Text("MISSION FAILED", style = MaterialTheme.typography.headlineMedium, color = ThrustRed)
                Text("Finaler Score: $score", style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onQuit, modifier = Modifier.weight(1f)) { Text("MENÜ") }
                    Button(onClick = onRetry, modifier = Modifier.weight(1f))        { Text("RETRY") }
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
                modifier                = Modifier.padding(32.dp),
                horizontalAlignment     = Alignment.CenterHorizontally,
                verticalArrangement     = Arrangement.spacedBy(16.dp),
            ) {
                Text("PAUSE", style = MaterialTheme.typography.headlineLarge)
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("WEITER") }
                OutlinedButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) { Text("MENÜ") }
            }
        }
    }
}
