package com.example.app.features.exposure.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.eventcatalog.domain.ObserveReusableEventCatalogUseCase
import com.example.app.features.exposure.domain.DeleteExposureEventUseCase
import com.example.app.features.exposure.domain.GetExposureEventUseCase
import com.example.app.features.exposure.domain.UpdateExposureEventUseCase
import com.example.app.features.ingestion.domain.CurrentTimeProvider

class EditExposureViewModelFactory(
    private val recordId: Long,
    private val getExposureEventUseCase: GetExposureEventUseCase,
    private val deleteExposureEventUseCase: DeleteExposureEventUseCase,
    private val updateExposureEventUseCase: UpdateExposureEventUseCase,
    private val currentTimeProvider: CurrentTimeProvider,
    private val observeReusableEventCatalogUseCase: ObserveReusableEventCatalogUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditExposureViewModel::class.java)) {
            return EditExposureViewModel(
                recordId,
                getExposureEventUseCase,
                deleteExposureEventUseCase,
                updateExposureEventUseCase,
                currentTimeProvider,
                observeReusableEventCatalogUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
