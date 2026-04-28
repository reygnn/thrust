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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.thrust.R
import com.github.reygnn.thrust.domain.level.Levels
import com.github.reygnn.thrust.ui.theme.ThrustCyan
import com.github.reygnn.thrust.ui.theme.ThrustDark
import com.github.reygnn.thrust.ui.theme.ThrustNavy

@Composable
fun MenuScreen(
    onStartGame:     () -> Unit,
    onStartEndless:  () -> Unit,
    onStartPractice: () -> Unit,
    onHighScores:    () -> Unit,
    onOptions:       () -> Unit,
    vm:              MenuViewModel = viewModel(factory = MenuViewModel.Factory),
) {
    val highScores by vm.highScores.collectAsStateWithLifecycle()
    val totalLevels = Levels.totalLevels

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(ThrustDark, ThrustNavy, ThrustDark))
            ),
    ) {
        // ── Header (Titel + Untertitel) oben ──────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 24.dp, end = 24.dp),
        ) {
            Text(
                text      = "THRUST", // Eigenname, nicht lokalisiert
                style     = MaterialTheme.typography.displayMedium,
                color     = ThrustCyan,
                textAlign = TextAlign.Center,
            )
            Text(
                text      = stringResource(R.string.menu_subtitle),
                style     = MaterialTheme.typography.titleMedium,
                color     = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }

        // ── Hauptbereich (Highscores + Buttons nebeneinander) ─────────────────
        Row(
            modifier              = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            // Linke Spalte: Highscores
            // Iteriert dynamisch über alle Level (skaliert mit Levels.totalLevels).
            // Wird nur angezeigt, wenn mindestens ein Highscore vorhanden ist.
            if (highScores.values.any { it > 0 }) {
                Card(
                    colors   = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text  = stringResource(R.string.menu_best_scores),
                            style = MaterialTheme.typography.labelLarge,
                            color = ThrustCyan,
                        )
                        (1..totalLevels).forEach { lvl ->
                            val hs = highScores[lvl] ?: 0
                            if (hs > 0) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text(
                                        text  = stringResource(R.string.menu_level_n, lvl),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White,
                                    )
                                    Text(
                                        text  = "$hs",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = ThrustCyan,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Wenn keine Highscores existieren, lassen wir links Platz frei,
                // damit die Buttons rechts nicht in die Bildschirmmitte rutschen.
                Spacer(modifier = Modifier.weight(1f))
            }

            // Rechte Spalte: Buttons in 2x2-Grid
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier            = Modifier.weight(1f),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick  = onStartGame,
                        modifier = Modifier.weight(1f).height(54.dp),
                    ) {
                        Text(
                            text  = stringResource(R.string.menu_mission_start),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Button(
                        onClick  = onStartEndless,
                        modifier = Modifier.weight(1f).height(54.dp),
                    ) {
                        Text(
                            text  = stringResource(R.string.menu_endless),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                OutlinedButton(
                    onClick  = onStartPractice,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(stringResource(R.string.menu_practice))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick  = onHighScores,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(stringResource(R.string.menu_high_scores))
                    }
                    OutlinedButton(
                        onClick  = onOptions,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(stringResource(R.string.menu_options))
                    }
                }
            }
        }
    }
}