package com.example.app.features.ingestion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.ingestion.domain.DeleteIngestionEventUseCase
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.GetIngestionEventUseCase
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.ingestion.domain.UpdateIngestionEventUseCase

class EditIngestionViewModelFactory(
    private val recordId: Long,
    private val getIngestionEventUseCase: GetIngestionEventUseCase,
    private val deleteIngestionEventUseCase: DeleteIngestionEventUseCase,
    private val updateIngestionEventUseCase: UpdateIngestionEventUseCase,
    private val currentTimeProvider: CurrentTimeProvider,
    private val reusableEntityExists: suspend (ReusableEntityKind, Long) -> Boolean
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditIngestionViewModel::class.java)) {
            return EditIngestionViewModel(
                recordId,
                getIngestionEventUseCase,
                deleteIngestionEventUseCase,
                updateIngestionEventUseCase,
                currentTimeProvider,
                reusableEntityExists
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
