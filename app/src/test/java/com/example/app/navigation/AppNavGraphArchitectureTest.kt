package com.example.app.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavGraphArchitectureTest {

    @Test
    fun nav_graph_depends_on_factories_and_provider_functions_not_data_or_domain_layers() {
        val navGraphFile = File(
            System.getProperty("user.dir"),
            "src/main/java/com/example/app/navigation/AppNavGraph.kt"
        )

        assertTrue(navGraphFile.exists())
        val source = navGraphFile.readText()
        val importLines = source.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("import ") }
            .toList()

        assertTrue(source.contains("recordsViewModelFactory: ViewModelProvider.Factory"))
        assertTrue(source.contains("createIngestionViewModelFactory: CreateIngestionViewModelFactory"))
        assertTrue(source.contains("createExposureViewModelFactory: CreateExposureViewModelFactory"))
        assertTrue(source.contains("createOutbreakViewModelFactory: CreateOutbreakViewModelFactory"))
        assertTrue(source.contains("createIngredientViewModelFactory: CreateIngredientViewModelFactory"))
        assertTrue(source.contains("createCompoundViewModelFactory: CreateCompoundViewModelFactory"))
        assertTrue(source.contains("recordDetailViewModelFactoryProvider: (Long, RecordEventType) -> RecordDetailViewModelFactory"))
        assertTrue(source.contains("editIngestionViewModelFactoryProvider: (Long) -> EditIngestionViewModelFactory"))
        assertTrue(source.contains("editExposureViewModelFactoryProvider: (Long) -> EditExposureViewModelFactory"))
        assertTrue(source.contains("editOutbreakViewModelFactoryProvider: (Long) -> EditOutbreakViewModelFactory"))
        assertFalse(importLines.any { line -> line.contains("Repository") })
        assertFalse(importLines.any { line -> line.contains("UseCase") })
        assertFalse(importLines.any { line -> line.contains("Dao") })
        assertFalse(importLines.any { line -> line.contains(".data.") })
    }

    @Test
    fun nav_graph_uses_shared_restore_helpers_and_forward_navigation_for_reusable_edit_flows() {
        val navGraphFile = File(
            System.getProperty("user.dir"),
            "src/main/java/com/example/app/navigation/AppNavGraph.kt"
        )

        assertTrue(navGraphFile.exists())
        val source = navGraphFile.readText()

        assertTrue(source.contains("selectedReusableEntity = savedStateHandle.toIngestionReusableSelectionResult()"))
        assertTrue(source.contains("editedCatalogSelection = savedStateHandle.toEditedEventCatalogSelectionResult()"))
        assertTrue(source.contains("navigateToRouteOrReturnToRecords(navController, route)"))
        assertTrue(source.contains("navController.navigate(route)"))
    }
}
