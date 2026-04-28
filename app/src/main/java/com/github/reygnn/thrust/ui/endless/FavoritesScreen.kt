package com.github.reygnn.thrust.ui.endless

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.github.reygnn.thrust.data.EndlessFavorite
import com.github.reygnn.thrust.domain.level.Difficulty
import com.github.reygnn.thrust.ui.theme.ThrustCyan
import com.github.reygnn.thrust.ui.theme.ThrustDark
import com.github.reygnn.thrust.ui.theme.ThrustGold
import com.github.reygnn.thrust.ui.theme.ThrustGreen
import com.github.reygnn.thrust.ui.theme.ThrustNavy
import com.github.reygnn.thrust.ui.theme.ThrustRed

@Composable
fun FavoritesScreen(
    onPlay: (EndlessFavorite) -> Unit,
    onBack: () -> Unit,
    vm:     FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory),
) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ThrustDark, ThrustNavy, ThrustDark))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text  = stringResource(R.string.endless_favorites_title),
                style = MaterialTheme.typography.headlineMedium,
                color = ThrustCyan,
            )

            if (favorites.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text      = stringResource(R.string.endless_favorites_empty),
                        style     = MaterialTheme.typography.bodyLarge,
                        color     = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items = favorites, key = { "${it.difficulty.name}|${it.seed}" }) { fav ->
                        FavoriteRow(
                            favorite = fav,
                            onPlay   = { onPlay(fav) },
                            onDelete = { vm.remove(fav) },
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick  = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        ) {
            Text(stringResource(R.string.endless_favorites_back))
        }
    }
}

@Composable
private fun FavoriteRow(
    favorite: EndlessFavorite,
    onPlay:   () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = favorite.difficulty.accent()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text  = favorite.difficulty.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
            )
            Text(
                text  = stringResource(R.string.endless_favorite_seed_label, favorite.seed.shortHex()),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        TextButton(onClick = onPlay) {
            Text(stringResource(R.string.endless_favorites_play), color = accent)
        }
        TextButton(onClick = onDelete) {
            Text(stringResource(R.string.endless_favorites_delete), color = Color.White.copy(alpha = 0.55f))
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

private fun Long.shortHex(): String =
    java.lang.Long.toHexString(this).takeLast(6).uppercase()
