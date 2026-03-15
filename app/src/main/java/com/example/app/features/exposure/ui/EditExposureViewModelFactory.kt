package com.example.app.features.exposure.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.exposure.domain.GetExposureEventUseCase
import com.example.app.features.exposure.domain.UpdateExposureEventUseCase
import com.example.app.features.ingestion.domain.CurrentTimeProvider

class EditExposureViewModelFactory(
    private val recordId: Long,
    private val getExposureEventUseCase: GetExposureEventUseCase,
    private val updateExposureEventUseCase: UpdateExposureEventUseCase,
    private val currentTimeProvider: CurrentTimeProvider
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditExposureViewModel::class.java)) {
            return EditExposureViewModel(recordId, getExposureEventUseCase, updateExposureEventUseCase, currentTimeProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
