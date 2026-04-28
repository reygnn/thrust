package com.github.reygnn.thrust.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.reygnn.thrust.R
import com.github.reygnn.thrust.domain.level.PracticeKind
import com.github.reygnn.thrust.ui.theme.ThrustCyan
import com.github.reygnn.thrust.ui.theme.ThrustDark
import com.github.reygnn.thrust.ui.theme.ThrustGold
import com.github.reygnn.thrust.ui.theme.ThrustGreen
import com.github.reygnn.thrust.ui.theme.ThrustNavy
import com.github.reygnn.thrust.ui.theme.ThrustRed

@Composable
fun PracticePickerScreen(
    onPick: (PracticeKind) -> Unit,
    onBack: () -> Unit,
) {
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
                text  = stringResource(R.string.practice_title),
                style = MaterialTheme.typography.displaySmall,
                color = ThrustCyan,
            )
            Text(
                text      = stringResource(R.string.practice_subtitle),
                style     = MaterialTheme.typography.bodyMedium,
                color     = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                PracticeKind.values().forEach { kind ->
                    PracticeCard(kind = kind, onClick = { onPick(kind) })
                }
            }
        }

        OutlinedButton(
            onClick  = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        ) {
            Text(stringResource(R.string.practice_back))
        }
    }
}

@Composable
private fun PracticeCard(kind: PracticeKind, onClick: () -> Unit) {
    val (titleRes, descRes) = when (kind) {
        PracticeKind.TUBE     -> R.string.practice_tube_title     to R.string.practice_tube_desc
        PracticeKind.DELIVERY -> R.string.practice_delivery_title to R.string.practice_delivery_desc
        PracticeKind.TURRETS  -> R.string.practice_turrets_title  to R.string.practice_turrets_desc
    }
    val accent = kind.accent()
    Box(
        modifier = Modifier
            .width(170.dp)
            .height(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.5.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start,
            modifier            = Modifier.fillMaxSize(),
        ) {
            Text(
                text  = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
            )
            Text(
                text  = stringResource(descRes),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

private fun PracticeKind.accent(): Color = when (this) {
    PracticeKind.TUBE     -> ThrustCyan
    PracticeKind.DELIVERY -> ThrustGreen
    PracticeKind.TURRETS  -> ThrustRed
}
