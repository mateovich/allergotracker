package io.github.mateovich.allergotracker.features.exposure.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ObserveReusableEventCatalogUseCase
import io.github.mateovich.allergotracker.features.exposure.domain.DeleteExposureEventUseCase
import io.github.mateovich.allergotracker.features.exposure.domain.GetExposureEventUseCase
import io.github.mateovich.allergotracker.features.exposure.domain.UpdateExposureEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider

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
