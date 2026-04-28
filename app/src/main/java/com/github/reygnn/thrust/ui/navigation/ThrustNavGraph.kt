package com.github.reygnn.thrust.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.reygnn.thrust.ui.endless.DifficultyPickerScreen
import com.github.reygnn.thrust.ui.endless.FavoritesScreen
import com.github.reygnn.thrust.ui.game.GameScreen
import com.github.reygnn.thrust.ui.game.GameViewModel
import com.github.reygnn.thrust.ui.highscore.HighScoreScreen
import com.github.reygnn.thrust.ui.menu.MenuScreen
import com.github.reygnn.thrust.ui.options.OptionsScreen
import com.github.reygnn.thrust.ui.practice.PracticePickerScreen

private const val ROUTE_MENU              = "menu"
private const val ROUTE_GAME              = "game"
private const val ROUTE_HIGHSCORE         = "highscore"
private const val ROUTE_OPTIONS           = "options"
private const val ROUTE_ENDLESS_PICKER    = "endless"
private const val ROUTE_ENDLESS_FAVORITES = "endless/favorites"
// Endless-Route trägt die Difficulty als Pfad-Argument; das VM liest sie
// aus dem SavedStateHandle und startet dann direkt im richtigen Modus.
private const val ROUTE_GAME_ENDLESS      = "game_endless/{difficulty}"
// Favorite-Variante: zusätzlich der Seed im Pfad — das VM erkennt daran
// EndlessFavorite-Mode (Streak wird nicht gezählt).
private const val ROUTE_GAME_ENDLESS_FAV  = "game_endless_fav/{difficulty}/{seed}"
// Practice-Routes: Picker und Game (mit kind im Pfad)
private const val ROUTE_PRACTICE_PICKER   = "practice"
private const val ROUTE_GAME_PRACTICE     = "game_practice/{kind}"

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
                onStartGame     = { navController.navigate(ROUTE_GAME) },
                onStartEndless  = { navController.navigate(ROUTE_ENDLESS_PICKER) },
                onStartPractice = { navController.navigate(ROUTE_PRACTICE_PICKER) },
                onHighScores    = { navController.navigate(ROUTE_HIGHSCORE) },
                onOptions       = { navController.navigate(ROUTE_OPTIONS) },
            )
        }

        composable(ROUTE_PRACTICE_PICKER) {
            PracticePickerScreen(
                onPick = { kind -> navController.navigate("game_practice/${kind.name}") },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route     = ROUTE_GAME_PRACTICE,
            arguments = listOf(navArgument(GameViewModel.NAV_ARG_PRACTICE_KIND) { type = NavType.StringType }),
        ) { backStackEntry ->
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

        composable(ROUTE_ENDLESS_PICKER) {
            DifficultyPickerScreen(
                onPick           = { difficulty ->
                    navController.navigate("game_endless/${difficulty.name}")
                },
                onShowFavorites  = { navController.navigate(ROUTE_ENDLESS_FAVORITES) },
                onBack           = { navController.popBackStack() },
            )
        }

        composable(ROUTE_ENDLESS_FAVORITES) {
            FavoritesScreen(
                onPlay = { fav ->
                    navController.navigate("game_endless_fav/${fav.difficulty.name}/${fav.seed}")
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route     = ROUTE_GAME_ENDLESS,
            arguments = listOf(navArgument(GameViewModel.NAV_ARG_DIFFICULTY) { type = NavType.StringType }),
        ) { backStackEntry ->
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

        composable(
            route     = ROUTE_GAME_ENDLESS_FAV,
            arguments = listOf(
                navArgument(GameViewModel.NAV_ARG_DIFFICULTY) { type = NavType.StringType },
                navArgument(GameViewModel.NAV_ARG_SEED)       { type = NavType.LongType },
            ),
        ) { backStackEntry ->
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
