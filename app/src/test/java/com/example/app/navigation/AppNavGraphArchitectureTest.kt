package com.example.app.navigation

import java.io.File
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
        assertTrue(!source.contains("Repository"))
        assertTrue(!source.contains("UseCase"))
        assertTrue(!source.contains("Dao"))
    }
}
