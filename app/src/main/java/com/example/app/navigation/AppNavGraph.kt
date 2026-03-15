package com.example.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.app.features.exposure.ui.CreateExposureScreen
import com.example.app.features.exposure.ui.CreateExposureViewModelFactory
import com.example.app.features.exposure.ui.EditExposureScreen
import com.example.app.features.exposure.ui.EditExposureViewModelFactory
import com.example.app.features.ingestion.ui.CreateIngestionScreen
import com.example.app.features.ingestion.ui.CreateIngestionViewModelFactory
import com.example.app.features.ingestion.ui.EditIngestionScreen
import com.example.app.features.ingestion.ui.EditIngestionViewModelFactory
import com.example.app.features.outbreak.ui.CreateOutbreakScreen
import com.example.app.features.outbreak.ui.CreateOutbreakViewModelFactory
import com.example.app.features.outbreak.ui.EditOutbreakScreen
import com.example.app.features.outbreak.ui.EditOutbreakViewModelFactory
import com.example.app.features.records.ui.RecordsScreen

@Composable
fun AppNavGraph(
    recordsViewModelFactory: ViewModelProvider.Factory,
    createIngestionViewModelFactory: CreateIngestionViewModelFactory,
    createExposureViewModelFactory: CreateExposureViewModelFactory,
    createOutbreakViewModelFactory: CreateOutbreakViewModelFactory,
    editIngestionViewModelFactoryProvider: (Long) -> EditIngestionViewModelFactory,
    editExposureViewModelFactoryProvider: (Long) -> EditExposureViewModelFactory,
    editOutbreakViewModelFactoryProvider: (Long) -> EditOutbreakViewModelFactory
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = AppRoutes.RECORDS) {
        composable(route = AppRoutes.RECORDS) {
            RecordsScreen(
                onNavigateToRoute = navController::navigate,
                viewModelFactory = recordsViewModelFactory
            )
        }
        composable(route = AppRoutes.CREATE_INGESTION) {
            CreateIngestionScreen(
                viewModelFactory = createIngestionViewModelFactory,
                onNavigateToRoute = { route ->
                    navController.popBackStack(route, inclusive = false)
                }
            )
        }
        composable(route = AppRoutes.CREATE_EXPOSURE) {
            CreateExposureScreen(
                viewModelFactory = createExposureViewModelFactory,
                onNavigateToRoute = { route ->
                    navController.popBackStack(route, inclusive = false)
                }
            )
        }
        composable(route = AppRoutes.CREATE_OUTBREAK) {
            CreateOutbreakScreen(
                viewModelFactory = createOutbreakViewModelFactory,
                onNavigateToRoute = { route ->
                    navController.popBackStack(route, inclusive = false)
                }
            )
        }
        composable(
            route = AppRoutes.EDIT_INGESTION,
            arguments = listOf(navArgument(AppRoutes.RECORD_ID_ARG) { type = NavType.LongType })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong(AppRoutes.RECORD_ID_ARG) ?: return@composable
            EditIngestionScreen(
                viewModelFactory = editIngestionViewModelFactoryProvider(recordId),
                onNavigateToRoute = { route -> navController.popBackStack(route, inclusive = false) }
            )
        }
        composable(
            route = AppRoutes.EDIT_EXPOSURE,
            arguments = listOf(navArgument(AppRoutes.RECORD_ID_ARG) { type = NavType.LongType })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong(AppRoutes.RECORD_ID_ARG) ?: return@composable
            EditExposureScreen(
                viewModelFactory = editExposureViewModelFactoryProvider(recordId),
                onNavigateToRoute = { route -> navController.popBackStack(route, inclusive = false) }
            )
        }
        composable(
            route = AppRoutes.EDIT_OUTBREAK,
            arguments = listOf(navArgument(AppRoutes.RECORD_ID_ARG) { type = NavType.LongType })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong(AppRoutes.RECORD_ID_ARG) ?: return@composable
            EditOutbreakScreen(
                viewModelFactory = editOutbreakViewModelFactoryProvider(recordId),
                onNavigateToRoute = { route -> navController.popBackStack(route, inclusive = false) }
            )
        }
    }
}
