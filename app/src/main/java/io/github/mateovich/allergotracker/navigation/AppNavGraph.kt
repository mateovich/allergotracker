package io.github.mateovich.allergotracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.ViewModelProvider
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import io.github.mateovich.allergotracker.core.designsystem.AppTopLevelDestination
import io.github.mateovich.allergotracker.core.designsystem.AppTopLevelNavigationBar
import io.github.mateovich.allergotracker.features.compounds.ui.CreateCompoundScreen
import io.github.mateovich.allergotracker.features.compounds.ui.CreateCompoundViewModelFactory
import io.github.mateovich.allergotracker.features.compounds.ui.EditCompoundScreen
import io.github.mateovich.allergotracker.features.compounds.ui.EditCompoundViewModelFactory
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogKind
import io.github.mateovich.allergotracker.features.eventcatalog.ui.EditReusableEventCatalogScreen
import io.github.mateovich.allergotracker.features.eventcatalog.ui.EditReusableEventCatalogViewModelFactory
import io.github.mateovich.allergotracker.features.eventcatalog.ui.EditedEventCatalogSelectionResult
import io.github.mateovich.allergotracker.features.export.ui.ExportScreen
import io.github.mateovich.allergotracker.features.export.ui.ExportViewModelFactory
import io.github.mateovich.allergotracker.features.exposure.ui.CreateExposureScreen
import io.github.mateovich.allergotracker.features.exposure.ui.CreateExposureViewModelFactory
import io.github.mateovich.allergotracker.features.exposure.ui.EditExposureScreen
import io.github.mateovich.allergotracker.features.exposure.ui.EditExposureViewModelFactory
import io.github.mateovich.allergotracker.features.ingredients.ui.CreateIngredientScreen
import io.github.mateovich.allergotracker.features.ingredients.ui.CreateIngredientViewModelFactory
import io.github.mateovich.allergotracker.features.ingredients.ui.EditIngredientScreen
import io.github.mateovich.allergotracker.features.ingredients.ui.ProvisionalIngredientsScreen
import io.github.mateovich.allergotracker.features.ingredients.ui.EditIngredientViewModelFactory
import io.github.mateovich.allergotracker.features.ingestion.ui.CreateIngestionScreen
import io.github.mateovich.allergotracker.features.ingestion.ui.IngestionReusableSelectionResult
import io.github.mateovich.allergotracker.features.ingestion.ui.CreateIngestionViewModelFactory
import io.github.mateovich.allergotracker.features.ingestion.ui.EditIngestionScreen
import io.github.mateovich.allergotracker.features.ingestion.ui.EditIngestionViewModelFactory
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import io.github.mateovich.allergotracker.features.outbreak.ui.CreateOutbreakScreen
import io.github.mateovich.allergotracker.features.outbreak.ui.CreateOutbreakViewModelFactory
import io.github.mateovich.allergotracker.features.outbreak.ui.EditOutbreakScreen
import io.github.mateovich.allergotracker.features.outbreak.ui.EditOutbreakViewModelFactory
import io.github.mateovich.allergotracker.features.records.domain.RecordEventType
import io.github.mateovich.allergotracker.features.records.ui.RecordDetailScreen
import io.github.mateovich.allergotracker.features.records.ui.RecordDetailViewModelFactory
import io.github.mateovich.allergotracker.features.records.ui.RecordsScreen

@Composable
fun AppNavGraph(
    recordsViewModelFactory: ViewModelProvider.Factory,
    exportViewModelFactory: ExportViewModelFactory,
    createIngestionViewModelFactory: CreateIngestionViewModelFactory,
    createExposureViewModelFactory: CreateExposureViewModelFactory,
    createOutbreakViewModelFactory: CreateOutbreakViewModelFactory,
    createIngredientViewModelFactory: CreateIngredientViewModelFactory,
    provisionalIngredientsViewModelFactory: ViewModelProvider.Factory,
    createCompoundViewModelFactoryProvider: (String, IngestionReusableSelectionResult?) -> CreateCompoundViewModelFactory,
    editIngredientViewModelFactoryProvider: (Long) -> EditIngredientViewModelFactory,
    editCompoundViewModelFactoryProvider: (Long) -> EditCompoundViewModelFactory,
    editReusableEventCatalogViewModelFactoryProvider: (ReusableEventCatalogKind, Long) -> EditReusableEventCatalogViewModelFactory,
    recordDetailViewModelFactoryProvider: (Long, RecordEventType) -> RecordDetailViewModelFactory,
    editIngestionViewModelFactoryProvider: (Long) -> EditIngestionViewModelFactory,
    editExposureViewModelFactoryProvider: (Long) -> EditExposureViewModelFactory,
    editOutbreakViewModelFactoryProvider: (Long) -> EditOutbreakViewModelFactory
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val topLevelDestinations = listOf(
        AppTopLevelDestination(route = AppRoutes.RECORDS, label = "Records", testTag = "top-level-records"),
        AppTopLevelDestination(route = AppRoutes.EXPORT, label = "Export", testTag = "top-level-export")
    )
    val topLevelRoutes = topLevelDestinations.map { it.route }.toSet()

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                AppTopLevelNavigationBar(
                    destinations = topLevelDestinations,
                    selectedRoute = currentRoute,
                    onDestinationSelected = { destination ->
                        navController.navigate(destination.route, navOptions {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        })
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.RECORDS,
            modifier = Modifier.padding(innerPadding)
        ) {
        composable(route = AppRoutes.RECORDS) {
            RecordsScreen(
                onNavigateToRoute = navController::navigate,
                viewModelFactory = recordsViewModelFactory
            )
        }
        composable(route = AppRoutes.EXPORT) {
            ExportScreen(viewModelFactory = exportViewModelFactory)
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
        composable(route = AppRoutes.PROVISIONAL) {
            ProvisionalIngredientsScreen(
                onNavigateToRoute = navController::navigate,
                viewModelFactory = provisionalIngredientsViewModelFactory
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
        composable(
            route = AppRoutes.CREATE_COMPOUND,
            arguments = listOf(
                navArgument(AppRoutes.PREFILL_NAME_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(AppRoutes.SEED_INGREDIENT_ID_ARG) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument(AppRoutes.SEED_INGREDIENT_NAME_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val prefillName = backStackEntry.arguments?.getString(AppRoutes.PREFILL_NAME_ARG).orEmpty()
            val seedIngredientId = backStackEntry.arguments?.getLong(AppRoutes.SEED_INGREDIENT_ID_ARG)?.takeIf { it > 0L }
            val seedIngredientName = backStackEntry.arguments?.getString(AppRoutes.SEED_INGREDIENT_NAME_ARG).orEmpty()
            val seedIngredient = seedIngredientId?.let { id ->
                IngestionReusableSelectionResult(
                    id = id,
                    name = seedIngredientName,
                    kind = ReusableEntityKind.INGREDIENT
                )
            }
            CreateCompoundScreen(
                viewModelFactory = createCompoundViewModelFactoryProvider(prefillName, seedIngredient),
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
                selectedReusableEntity = backStackEntry.savedStateHandle.toIngestionReusableSelectionResult(),
                onReusableEntityConsumed = {
                    backStackEntry.savedStateHandle.remove<Long>(AppRoutes.REUSABLE_ENTITY_ID_RESULT)
                    backStackEntry.savedStateHandle.remove<String>(AppRoutes.REUSABLE_ENTITY_NAME_RESULT)
                    backStackEntry.savedStateHandle.remove<String>(AppRoutes.REUSABLE_ENTITY_KIND_RESULT)
                },
                viewModelFactory = editIngredientViewModelFactoryProvider(reusableId),
                onNavigateBackWithSelection = { result ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_ID_RESULT, result.id)
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_NAME_RESULT, result.name)
                    navController.previousBackStackEntry?.savedStateHandle?.set(AppRoutes.REUSABLE_ENTITY_KIND_RESULT, result.kind.storageValue)
                    navController.popBackStack()
                },
                onNavigateToCreateCompound = navController::navigate,
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
            arguments = listOf(
                navArgument(AppRoutes.RECORD_ID_ARG) { type = NavType.LongType },
                navArgument(AppRoutes.OPEN_DELETE_ARG) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getLong(AppRoutes.RECORD_ID_ARG) ?: return@composable
            val savedStateHandle = backStackEntry.savedStateHandle
            val openDeleteOnStart = consumeOpenDeleteOnStart(
                savedStateHandle = savedStateHandle,
                defaultValue = backStackEntry.arguments?.getBoolean(AppRoutes.OPEN_DELETE_ARG) == true
            )
            EditIngestionScreen(
                viewModelFactory = editIngestionViewModelFactoryProvider(recordId),
                openDeleteOnStart = openDeleteOnStart,
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
            val savedStateHandle = backStackEntry.savedStateHandle
            val openDeleteOnStart = consumeOpenDeleteOnStart(
                savedStateHandle = savedStateHandle,
                defaultValue = backStackEntry.arguments?.getBoolean(AppRoutes.OPEN_DELETE_ARG) == true
            )
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
            val savedStateHandle = backStackEntry.savedStateHandle
            val openDeleteOnStart = consumeOpenDeleteOnStart(
                savedStateHandle = savedStateHandle,
                defaultValue = backStackEntry.arguments?.getBoolean(AppRoutes.OPEN_DELETE_ARG) == true
            )
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
}

private fun navigateToRouteOrReturnToRecords(navController: NavHostController, route: String) {
    if (route == AppRoutes.RECORDS) {
        navController.popBackStack(AppRoutes.RECORDS, inclusive = false)
    } else {
        navController.navigate(route)
    }
}

private fun consumeOpenDeleteOnStart(savedStateHandle: SavedStateHandle, defaultValue: Boolean): Boolean {
    val consumedKey = "${AppRoutes.OPEN_DELETE_ARG}_consumed"
    if (savedStateHandle.get<Boolean>(consumedKey) == true) return false

    savedStateHandle[consumedKey] = true
    savedStateHandle.remove<Boolean>(AppRoutes.OPEN_DELETE_ARG)
    return defaultValue
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
