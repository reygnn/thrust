package com.github.reygnn.thrust.ui.highscore

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.thrust.R
import com.github.reygnn.thrust.domain.level.Levels
import com.github.reygnn.thrust.ui.theme.ThrustCyan
import com.github.reygnn.thrust.ui.theme.ThrustDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighScoreScreen(
    onBack: () -> Unit,
    vm:     HighScoreViewModel = viewModel(factory = HighScoreViewModel.Factory),
) {
    val scores by vm.highScores.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = stringResource(R.string.highscore_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(
                            text  = "←",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = ThrustDark,
                    titleContentColor = ThrustCyan,
                ),
            )
        },
        containerColor = ThrustDark,
    ) { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Levels.all.forEach { level ->
                val score = scores[level.id] ?: 0
                Card(
                    modifier = Modifier.fillMaxWidth(0.7f),
                    colors   = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text  = stringResource(R.string.menu_level_n, level.id),
                                style = MaterialTheme.typography.labelLarge,
                                color = ThrustCyan,
                            )
                            // Level-Name = Eigenname, nicht lokalisiert.
                            Text(level.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                        }
                        Text(
                            text  = if (score > 0) "$score" else stringResource(R.string.highscore_empty),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (score > 0) ThrustCyan else Color.White.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        }
    }
}
