package com.example.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.example.app.core.di.AppContainer
import com.example.app.navigation.AppNavGraph

class MainActivity : ComponentActivity() {
    private lateinit var appContainer: AppContainer
    private var pendingImportDocumentUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer = AppContainer(applicationContext)
        setContent {
            MaterialTheme {
                AppNavGraph(
                    recordsViewModelFactory = appContainer.recordsViewModelFactory,
                    exportViewModelFactory = appContainer.exportViewModelFactory,
                    createIngestionViewModelFactory = appContainer.createIngestionViewModelFactory,
                    createExposureViewModelFactory = appContainer.createExposureViewModelFactory,
                    createOutbreakViewModelFactory = appContainer.createOutbreakViewModelFactory,
                    createIngredientViewModelFactory = appContainer.createIngredientViewModelFactory,
                    createCompoundViewModelFactory = appContainer.createCompoundViewModelFactory,
                    editIngredientViewModelFactoryProvider = appContainer::createEditIngredientViewModelFactory,
                    editCompoundViewModelFactoryProvider = appContainer::createEditCompoundViewModelFactory,
                    editReusableEventCatalogViewModelFactoryProvider = appContainer::createEditReusableEventCatalogViewModelFactory,
                    recordDetailViewModelFactoryProvider = appContainer::createRecordDetailViewModelFactory,
                    editIngestionViewModelFactoryProvider = appContainer::createEditIngestionViewModelFactory,
                    editExposureViewModelFactoryProvider = appContainer::createEditExposureViewModelFactory,
                    editOutbreakViewModelFactoryProvider = appContainer::createEditOutbreakViewModelFactory
                )
            }
        }
    }

    fun resetAppStateForTesting() {
        appContainer.clearAllDataForTesting()
    }

    fun enqueueImportDocumentForTesting(fileName: String, contents: String) {
        val file = cacheDir.resolve(fileName)
        file.writeText(contents)
        pendingImportDocumentUri = Uri.fromFile(file).toString()
    }

    fun consumeImportDocumentForTesting(): String? {
        val documentUri = pendingImportDocumentUri
        pendingImportDocumentUri = null
        return documentUri
    }
}
