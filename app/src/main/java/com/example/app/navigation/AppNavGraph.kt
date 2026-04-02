package com.example.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.rememberNavController
import com.example.app.features.compounds.ui.CreateCompoundScreen
import com.example.app.features.compounds.ui.CreateCompoundViewModelFactory
import com.example.app.features.compounds.ui.EditCompoundScreen
import com.example.app.features.compounds.ui.EditCompoundViewModelFactory
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.ui.EditReusableEventCatalogScreen
import com.example.app.features.eventcatalog.ui.EditReusableEventCatalogViewModelFactory
import com.example.app.features.eventcatalog.ui.EditedEventCatalogSelectionResult
import com.example.app.features.exposure.ui.CreateExposureScreen
import com.example.app.features.exposure.ui.CreateExposureViewModelFactory
import com.example.app.features.exposure.ui.EditExposureScreen
import com.example.app.features.exposure.ui.EditExposureViewModelFactory
import com.example.app.features.ingredients.ui.CreateIngredientScreen
import com.example.app.features.ingredients.ui.CreateIngredientViewModelFactory
import com.example.app.features.ingredients.ui.EditIngredientScreen
import com.example.app.features.ingredients.ui.EditIngredientViewModelFactory
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
    editIngredientViewModelFactoryProvider: (Long) -> EditIngredientViewModelFactory,
    editCompoundViewModelFactoryProvider: (Long) -> EditCompoundViewModelFactory,
    editReusableEventCatalogViewModelFactoryProvider: (ReusableEventCatalogKind, Long) -> EditReusableEventCatalogViewModelFactory,
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
                onNavigateToRoute = { route -> navigateToRouteOrReturnToRecords(navController, route) },
                selectedReusableEntity = savedStateHandle.toIngestionReusableSelectionResult(),
                retiredReusableSelection = savedStateHandle.consumeRetiredReusableSelection(),
                onReusableEntityConsumed = {
                    savedStateHandle.remove<Long>(AppRoutes.REUSABLE_ENTITY_ID_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.REUSABLE_ENTITY_NAME_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.REUSABLE_ENTITY_KIND_RESULT)
                    savedStateHandle.remove<Boolean>(AppRoutes.REUSABLE_ENTITY_RETIRED_RESULT)
                }
            )
        }
        composable(route = AppRoutes.CREATE_EXPOSURE) {
            val savedStateHandle = it.savedStateHandle
            CreateExposureScreen(
                viewModelFactory = createExposureViewModelFactory,
                editedCatalogSelection = savedStateHandle.toEditedEventCatalogSelectionResult(),
                retiredCatalogKind = savedStateHandle.consumeRetiredEventCatalogKind(),
                onEditedCatalogConsumed = {
                    savedStateHandle.remove<Long>(AppRoutes.EVENT_CATALOG_ID_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.EVENT_CATALOG_NAME_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.EVENT_CATALOG_KIND_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.EVENT_CATALOG_RETIRED_KIND_RESULT)
                },
                onNavigateToRoute = { route -> navigateToRouteOrReturnToRecords(navController, route) }
            )
        }
        composable(route = AppRoutes.CREATE_OUTBREAK) {
            val savedStateHandle = it.savedStateHandle
            CreateOutbreakScreen(
                viewModelFactory = createOutbreakViewModelFactory,
                editedCatalogSelection = savedStateHandle.toEditedEventCatalogSelectionResult(),
                retiredCatalogKind = savedStateHandle.consumeRetiredEventCatalogKind(),
                onEditedCatalogConsumed = {
                    savedStateHandle.remove<Long>(AppRoutes.EVENT_CATALOG_ID_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.EVENT_CATALOG_NAME_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.EVENT_CATALOG_KIND_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.EVENT_CATALOG_RETIRED_KIND_RESULT)
                },
                onNavigateToRoute = { route -> navigateToRouteOrReturnToRecords(navController, route) }
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
            route = AppRoutes.EDIT_INGREDIENT,
            arguments = listOf(navArgument(AppRoutes.REUSABLE_ID_ARG) { type = NavType.LongType })
        ) { backStackEntry ->
            val reusableId = backStackEntry.arguments?.getLong(AppRoutes.REUSABLE_ID_ARG) ?: return@composable
            EditIngredientScreen(
                viewModelFactory = editIngredientViewModelFactoryProvider(reusableId),
                onNavigateBackWithSelection = { result ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_ID_RESULT, result.id)
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_NAME_RESULT, result.name)
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_KIND_RESULT, result.kind.storageValue)
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.previousBackStackEntry?.savedStateHandle?.remove<Long>(AppRoutes.REUSABLE_ENTITY_ID_RESULT)
                    navController.previousBackStackEntry?.savedStateHandle?.remove<String>(AppRoutes.REUSABLE_ENTITY_NAME_RESULT)
                    navController.previousBackStackEntry?.savedStateHandle?.remove<String>(AppRoutes.REUSABLE_ENTITY_KIND_RESULT)
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_RETIRED_RESULT, true)
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = AppRoutes.EDIT_COMPOUND,
            arguments = listOf(navArgument(AppRoutes.REUSABLE_ID_ARG) { type = NavType.LongType })
        ) { backStackEntry ->
            val reusableId = backStackEntry.arguments?.getLong(AppRoutes.REUSABLE_ID_ARG) ?: return@composable
            EditCompoundScreen(
                viewModelFactory = editCompoundViewModelFactoryProvider(reusableId),
                onNavigateBackWithSelection = { result ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_ID_RESULT, result.id)
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_NAME_RESULT, result.name)
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_KIND_RESULT, result.kind.storageValue)
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.previousBackStackEntry?.savedStateHandle?.remove<Long>(AppRoutes.REUSABLE_ENTITY_ID_RESULT)
                    navController.previousBackStackEntry?.savedStateHandle?.remove<String>(AppRoutes.REUSABLE_ENTITY_NAME_RESULT)
                    navController.previousBackStackEntry?.savedStateHandle?.remove<String>(AppRoutes.REUSABLE_ENTITY_KIND_RESULT)
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_RETIRED_RESULT, true)
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = AppRoutes.EDIT_EVENT_CATALOG,
            arguments = listOf(
                navArgument(AppRoutes.CATALOG_KIND_ARG) { type = NavType.StringType },
                navArgument(AppRoutes.REUSABLE_ID_ARG) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val reusableId = backStackEntry.arguments?.getLong(AppRoutes.REUSABLE_ID_ARG) ?: return@composable
            val kind = backStackEntry.arguments?.getString(AppRoutes.CATALOG_KIND_ARG)
                ?.let(ReusableEventCatalogKind::fromStorageValue)
                ?: return@composable
            EditReusableEventCatalogScreen(
                viewModelFactory = editReusableEventCatalogViewModelFactoryProvider(kind, reusableId),
                onNavigateBackWithSelection = { result ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.EVENT_CATALOG_ID_RESULT, result.id)
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.EVENT_CATALOG_NAME_RESULT, result.name)
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.EVENT_CATALOG_KIND_RESULT, result.kind.storageValue)
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.previousBackStackEntry?.savedStateHandle?.remove<Long>(AppRoutes.EVENT_CATALOG_ID_RESULT)
                    navController.previousBackStackEntry?.savedStateHandle?.remove<String>(AppRoutes.EVENT_CATALOG_NAME_RESULT)
                    navController.previousBackStackEntry?.savedStateHandle?.remove<String>(AppRoutes.EVENT_CATALOG_KIND_RESULT)
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        AppRoutes.EVENT_CATALOG_RETIRED_KIND_RESULT,
                        kind.storageValue
                    )
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = AppRoutes.EDIT_INGESTION,
            arguments = listOf(navArgument(AppRoutes.RECORD_ID_ARG) { type = NavType.LongType })
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong(AppRoutes.RECORD_ID_ARG) ?: return@composable
            val savedStateHandle = backStackEntry.savedStateHandle
            EditIngestionScreen(
                viewModelFactory = editIngestionViewModelFactoryProvider(recordId),
                selectedReusableEntity = savedStateHandle.toIngestionReusableSelectionResult(),
                retiredReusableSelection = savedStateHandle.consumeRetiredReusableSelection(),
                onReusableEntityConsumed = {
                    savedStateHandle.remove<Long>(AppRoutes.REUSABLE_ENTITY_ID_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.REUSABLE_ENTITY_NAME_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.REUSABLE_ENTITY_KIND_RESULT)
                    savedStateHandle.remove<Boolean>(AppRoutes.REUSABLE_ENTITY_RETIRED_RESULT)
                },
                onNavigateToRoute = { route -> navigateToRouteOrReturnToRecords(navController, route) }
            )
        }
        composable(
            route = AppRoutes.EDIT_EXPOSURE,
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
            val savedStateHandle = backStackEntry.savedStateHandle
            EditExposureScreen(
                viewModelFactory = editExposureViewModelFactoryProvider(recordId),
                openDeleteOnStart = openDeleteOnStart,
                editedCatalogSelection = savedStateHandle.toEditedEventCatalogSelectionResult(),
                retiredCatalogKind = savedStateHandle.consumeRetiredEventCatalogKind(),
                onEditedCatalogConsumed = {
                    savedStateHandle.remove<Long>(AppRoutes.EVENT_CATALOG_ID_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.EVENT_CATALOG_NAME_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.EVENT_CATALOG_KIND_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.EVENT_CATALOG_RETIRED_KIND_RESULT)
                },
                onNavigateToRoute = { route -> navigateToRouteOrReturnToRecords(navController, route) }
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
            val savedStateHandle = backStackEntry.savedStateHandle
            EditOutbreakScreen(
                viewModelFactory = editOutbreakViewModelFactoryProvider(recordId),
                openDeleteOnStart = openDeleteOnStart,
                editedCatalogSelection = savedStateHandle.toEditedEventCatalogSelectionResult(),
                retiredCatalogKind = savedStateHandle.consumeRetiredEventCatalogKind(),
                onEditedCatalogConsumed = {
                    savedStateHandle.remove<Long>(AppRoutes.EVENT_CATALOG_ID_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.EVENT_CATALOG_NAME_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.EVENT_CATALOG_KIND_RESULT)
                    savedStateHandle.remove<String>(AppRoutes.EVENT_CATALOG_RETIRED_KIND_RESULT)
                },
                onNavigateToRoute = { route -> navigateToRouteOrReturnToRecords(navController, route) }
            )
        }
    }
}

private fun navigateToRouteOrReturnToRecords(navController: NavHostController, route: String) {
    if (route == AppRoutes.RECORDS) {
        navController.popBackStack(AppRoutes.RECORDS, inclusive = false)
    } else {
        navController.navigate(route)
    }
}

internal fun SavedStateHandle.toIngestionReusableSelectionResult(): IngestionReusableSelectionResult? {
    val id = get<Long>(AppRoutes.REUSABLE_ENTITY_ID_RESULT) ?: return null
    val name = get<String>(AppRoutes.REUSABLE_ENTITY_NAME_RESULT) ?: return null
    val kindRaw = get<String>(AppRoutes.REUSABLE_ENTITY_KIND_RESULT) ?: return null
    val kind = ReusableEntityKind.fromStorageValue(kindRaw) ?: return null
    if (id <= 0L || name.isBlank()) return null
    return IngestionReusableSelectionResult(id = id, name = name, kind = kind)
}

internal fun SavedStateHandle.toEditedEventCatalogSelectionResult(): EditedEventCatalogSelectionResult? {
    val id = get<Long>(AppRoutes.EVENT_CATALOG_ID_RESULT) ?: return null
    val name = get<String>(AppRoutes.EVENT_CATALOG_NAME_RESULT) ?: return null
    val kindRaw = get<String>(AppRoutes.EVENT_CATALOG_KIND_RESULT) ?: return null
    val kind = ReusableEventCatalogKind.fromStorageValue(kindRaw) ?: return null
    if (id <= 0L || name.isBlank()) return null
    return EditedEventCatalogSelectionResult(id = id, kind = kind, name = name)
}

internal fun SavedStateHandle.consumeRetiredReusableSelection(): Boolean {
    return get<Boolean>(AppRoutes.REUSABLE_ENTITY_RETIRED_RESULT) == true
}

internal fun SavedStateHandle.consumeRetiredEventCatalogKind(): ReusableEventCatalogKind? {
    val kindRaw = get<String>(AppRoutes.EVENT_CATALOG_RETIRED_KIND_RESULT) ?: return null
    return ReusableEventCatalogKind.fromStorageValue(kindRaw)
}
