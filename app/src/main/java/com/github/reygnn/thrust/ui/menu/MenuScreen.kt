package com.github.reygnn.thrust.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.thrust.ui.theme.ThrustCyan
import com.github.reygnn.thrust.ui.theme.ThrustDark
import com.github.reygnn.thrust.ui.theme.ThrustNavy

@Composable
fun MenuScreen(
    onStartGame:   () -> Unit,
    onHighScores:  () -> Unit,
    onOptions:     () -> Unit,
    vm:            MenuViewModel = viewModel(factory = MenuViewModel.Factory),
) {
    val highScores by vm.highScores.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(ThrustDark, ThrustNavy, ThrustDark))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier            = Modifier.padding(48.dp),
        ) {
            // Titel
            Text(
                text      = "THRUST",
                style     = MaterialTheme.typography.displayLarge,
                color     = ThrustCyan,
                textAlign = TextAlign.Center,
            )
            Text(
                text      = "Fuel Pod Rescue Mission",
                style     = MaterialTheme.typography.titleLarge,
                color     = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(8.dp))

            // Highscores-Vorschau
            if (highScores.values.any { it > 0 }) {
                Card(
                    colors   = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.fillMaxWidth(0.5f),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("BEST SCORES", style = MaterialTheme.typography.labelLarge, color = ThrustCyan)
                        (1..3).forEach { lvl ->
                            val hs = highScores[lvl] ?: 0
                            if (hs > 0) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("Level $lvl", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                                    Text("$hs",        style = MaterialTheme.typography.bodyLarge, color = ThrustCyan)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Buttons
            Button(
                onClick  = onStartGame,
                modifier = Modifier.fillMaxWidth(0.45f).height(54.dp),
            ) {
                Text("MISSION START", style = MaterialTheme.typography.titleMedium)
            }

            OutlinedButton(
                onClick  = onHighScores,
                modifier = Modifier.fillMaxWidth(0.45f).height(48.dp),
            ) {
                Text("HIGH SCORES")
            }

            OutlinedButton(
                onClick  = onOptions,
                modifier = Modifier.fillMaxWidth(0.45f).height(48.dp),
            ) {
                Text("OPTIONS")
            }
        }
    }
}
