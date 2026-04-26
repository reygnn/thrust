package com.github.reygnn.thrust.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.reygnn.thrust.ui.game.GameScreen
import com.github.reygnn.thrust.ui.game.GameViewModel
import com.github.reygnn.thrust.ui.highscore.HighScoreScreen
import com.github.reygnn.thrust.ui.menu.MenuScreen
import com.github.reygnn.thrust.ui.options.OptionsScreen

private const val ROUTE_MENU      = "menu"
private const val ROUTE_GAME      = "game"
private const val ROUTE_HIGHSCORE = "highscore"
private const val ROUTE_OPTIONS   = "options"

@Composable
fun ThrustNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController    = navController,
        startDestination = ROUTE_MENU,
        modifier         = modifier,
    ) {
        composable(ROUTE_MENU) {
            MenuScreen(
                onStartGame  = { navController.navigate(ROUTE_GAME) },
                onHighScores = { navController.navigate(ROUTE_HIGHSCORE) },
                onOptions    = { navController.navigate(ROUTE_OPTIONS) },
            )
        }

        composable(ROUTE_GAME) { backStackEntry ->
            val vm = androidx.lifecycle.viewmodel.compose.viewModel<GameViewModel>(
                viewModelStoreOwner = backStackEntry,
                factory             = GameViewModel.Factory,
            )
            GameScreen(
                onNavigateBack = {
                    navController.popBackStack(ROUTE_MENU, inclusive = false)
                },
                vm = vm,
            )
        }

        composable(ROUTE_HIGHSCORE) {
            HighScoreScreen(onBack = { navController.popBackStack() })
        }

        composable(ROUTE_OPTIONS) {
            OptionsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
