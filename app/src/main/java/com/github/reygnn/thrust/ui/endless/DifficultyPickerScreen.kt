package com.github.reygnn.thrust.ui.endless

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.thrust.R
import com.github.reygnn.thrust.domain.level.Difficulty
import com.github.reygnn.thrust.ui.theme.ThrustCyan
import com.github.reygnn.thrust.ui.theme.ThrustDark
import com.github.reygnn.thrust.ui.theme.ThrustGold
import com.github.reygnn.thrust.ui.theme.ThrustGreen
import com.github.reygnn.thrust.ui.theme.ThrustNavy
import com.github.reygnn.thrust.ui.theme.ThrustRed

@Composable
fun DifficultyPickerScreen(
    onPick: (Difficulty) -> Unit,
    onBack: () -> Unit,
    vm:     EndlessPickerViewModel = viewModel(factory = EndlessPickerViewModel.Factory),
) {
    val streaks by vm.streaks.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ThrustDark, ThrustNavy, ThrustDark))),
    ) {
        Column(
            modifier            = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text  = stringResource(R.string.endless_title),
                style = MaterialTheme.typography.displaySmall,
                color = ThrustCyan,
            )
            Text(
                text      = stringResource(R.string.endless_subtitle),
                style     = MaterialTheme.typography.bodyMedium,
                color     = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Difficulty.values().forEach { d ->
                    DifficultyCard(
                        difficulty = d,
                        bestStreak = streaks[d] ?: 0,
                        onClick    = { onPick(d) },
                    )
                }
            }
        }

        OutlinedButton(
            onClick  = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        ) {
            Text(stringResource(R.string.endless_back))
        }
    }
}

@Composable
private fun DifficultyCard(difficulty: Difficulty, bestStreak: Int, onClick: () -> Unit) {
    val accent = difficulty.accent()
    Box(
        modifier = Modifier
            .width(150.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.5.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start,
            modifier            = Modifier.fillMaxSize(),
        ) {
            Text(
                text  = difficulty.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
            )
            Text(
                text  = stringResource(R.string.endless_stat_turrets, difficulty.turretCount.first, difficulty.turretCount.last),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
            Text(
                text  = stringResource(R.string.endless_stat_gravity, stringResource(gravityLabelRes(difficulty.gravity))),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
            Text(
                text  = stringResource(R.string.endless_stat_barriers, difficulty.barrierCount.first, difficulty.barrierCount.last),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
            if (bestStreak > 0) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text  = stringResource(R.string.endless_best_streak, bestStreak),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
            }
        }
    }
}

private fun Difficulty.accent(): Color = when (this) {
    Difficulty.ROOKIE      -> ThrustGreen
    Difficulty.MEDIUM      -> ThrustCyan
    Difficulty.IMPOSSIBLE  -> ThrustGold
    Difficulty.INSTA_DEATH -> ThrustRed
    Difficulty.PURE_CHAOS  -> Color(0xFFFF38C8)
}

private fun gravityLabelRes(g: Float): Int = when {
    g < 0.030f -> R.string.endless_grav_low
    g < 0.050f -> R.string.endless_grav_med
    g < 0.070f -> R.string.endless_grav_high
    g < 0.090f -> R.string.endless_grav_very_high
    else       -> R.string.endless_grav_brutal
}
