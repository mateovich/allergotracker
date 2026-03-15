package com.example.app.features.ingestion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.ingestion.domain.CreateIngestionDefaultsUseCase
import com.example.app.features.ingestion.domain.CreateIngestionEventUseCase

class CreateIngestionViewModelFactory(
    private val createIngestionDefaultsUseCase: CreateIngestionDefaultsUseCase,
    private val createIngestionEventUseCase: CreateIngestionEventUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateIngestionViewModel::class.java)) {
            return CreateIngestionViewModel(
                createIngestionDefaultsUseCase = createIngestionDefaultsUseCase,
                createIngestionEventUseCase = createIngestionEventUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
