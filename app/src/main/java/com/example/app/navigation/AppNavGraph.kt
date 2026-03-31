package com.example.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.app.features.compounds.ui.CreateCompoundScreen
import com.example.app.features.compounds.ui.CreateCompoundViewModelFactory
import com.example.app.features.exposure.ui.CreateExposureScreen
import com.example.app.features.exposure.ui.CreateExposureViewModelFactory
import com.example.app.features.exposure.ui.EditExposureScreen
import com.example.app.features.exposure.ui.EditExposureViewModelFactory
import com.example.app.features.ingredients.ui.CreateIngredientScreen
import com.example.app.features.ingredients.ui.CreateIngredientViewModelFactory
import com.example.app.features.ingestion.ui.CreateIngestionScreen
import com.example.app.features.ingestion.ui.IngestionReusableSelectionResult
import com.example.app.features.ingestion.ui.CreateIngestionViewModelFactory
import com.example.app.features.ingestion.ui.EditIngestionScreen
import com.example.app.features.ingestion.ui.EditIngestionViewModelFactory
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.outbreak.ui.CreateOutbreakScreen
import com.example.app.features.outbreak.ui.CreateOutbreakViewModelFactory
import com.example.app.features.outbreak.ui.EditOutbreakScreen
import com.example.app.features.outbreak.ui.EditOutbreakViewModelFactory
import com.example.app.features.records.domain.RecordEventType
import com.example.app.features.records.ui.RecordDetailScreen
import com.example.app.features.records.ui.RecordDetailViewModelFactory
import com.example.app.features.records.ui.RecordsScreen

@Composable
fun AppNavGraph(
    recordsViewModelFactory: ViewModelProvider.Factory,
    createIngestionViewModelFactory: CreateIngestionViewModelFactory,
    createExposureViewModelFactory: CreateExposureViewModelFactory,
    createOutbreakViewModelFactory: CreateOutbreakViewModelFactory,
    createIngredientViewModelFactory: CreateIngredientViewModelFactory,
    createCompoundViewModelFactory: CreateCompoundViewModelFactory,
    recordDetailViewModelFactoryProvider: (Long, RecordEventType) -> RecordDetailViewModelFactory,
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
        composable(
            route = AppRoutes.RECORD_DETAIL,
            arguments = listOf(
                navArgument(AppRoutes.RECORD_EVENT_TYPE_ARG) { type = NavType.StringType },
                navArgument(AppRoutes.RECORD_ID_ARG) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong(AppRoutes.RECORD_ID_ARG) ?: return@composable
            val eventTypeArg = backStackEntry.arguments?.getString(AppRoutes.RECORD_EVENT_TYPE_ARG).orEmpty()
            val eventType = AppRoutes.parseRecordEventType(eventTypeArg) ?: return@composable
            RecordDetailScreen(
                onNavigateToRoute = { route ->
                    if (route == AppRoutes.RECORDS) {
                        navController.popBackStack(AppRoutes.RECORDS, inclusive = false)
                    } else {
                        navController.navigate(route)
                    }
                },
                viewModelFactory = recordDetailViewModelFactoryProvider(recordId, eventType)
            )
        }
        composable(route = AppRoutes.CREATE_INGESTION) {
            val savedStateHandle = it.savedStateHandle
            CreateIngestionScreen(
                viewModelFactory = createIngestionViewModelFactory,
                onNavigateToRoute = { route ->
                    if (route == AppRoutes.RECORDS) {
                        navController.popBackStack(route, inclusive = false)
                    } else {
                        navController.navigate(route)
                    }
                },
                selectedReusableEntity = savedStateHandle.get<String>(AppRoutes.REUSABLE_ENTITY_NAME_RESULT)?.let { name ->
                    IngestionReusableSelectionResult(
                        id = savedStateHandle.get<Long>(AppRoutes.REUSABLE_ENTITY_ID_RESULT) ?: 0L,
                        name = name,
                        kind = ReusableEntityKind.fromStorageValue(
                            savedStateHandle.get<String>(AppRoutes.REUSABLE_ENTITY_KIND_RESULT)
                        ) ?: ReusableEntityKind.INGREDIENT
                    )
                },
                onReusableEntityConsumed = {
                    savedStateHandle.remove<Long>(AppRoutes.REUSABLE_ENTITY_ID_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.REUSABLE_ENTITY_NAME_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.REUSABLE_ENTITY_KIND_RESULT)
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
        composable(route = AppRoutes.CREATE_INGREDIENT) {
            CreateIngredientScreen(
                viewModelFactory = createIngredientViewModelFactory,
                onNavigateBackWithSelection = { result ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_ID_RESULT, result.id)
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_NAME_RESULT, result.name)
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_KIND_RESULT, result.kind.storageValue)
                    navController.popBackStack()
                }
            )
        }
        composable(route = AppRoutes.CREATE_COMPOUND) {
            CreateCompoundScreen(
                viewModelFactory = createCompoundViewModelFactory,
                onNavigateBackWithSelection = { result ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_ID_RESULT, result.id)
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_NAME_RESULT, result.name)
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_KIND_RESULT, result.kind.storageValue)
                    navController.popBackStack()
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
            arguments = listOf(
                navArgument(AppRoutes.RECORD_ID_ARG) { type = NavType.LongType },
                navArgument(AppRoutes.OPEN_DELETE_ARG) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong(AppRoutes.RECORD_ID_ARG) ?: return@composable
            val openDeleteOnStart = backStackEntry.arguments?.getBoolean(AppRoutes.OPEN_DELETE_ARG) == true
            EditOutbreakScreen(
                viewModelFactory = editOutbreakViewModelFactoryProvider(recordId),
                openDeleteOnStart = openDeleteOnStart,
                onNavigateToRoute = { route -> navController.popBackStack(route, inclusive = false) }
            )
        }
    }
}
