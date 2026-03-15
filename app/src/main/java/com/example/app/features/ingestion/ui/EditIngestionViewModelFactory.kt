package com.example.app.features.ingestion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.GetIngestionEventUseCase
import com.example.app.features.ingestion.domain.UpdateIngestionEventUseCase

class EditIngestionViewModelFactory(
    private val recordId: Long,
    private val getIngestionEventUseCase: GetIngestionEventUseCase,
    private val updateIngestionEventUseCase: UpdateIngestionEventUseCase,
    private val currentTimeProvider: CurrentTimeProvider
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditIngestionViewModel::class.java)) {
            return EditIngestionViewModel(recordId, getIngestionEventUseCase, updateIngestionEventUseCase, currentTimeProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
