package io.github.mateovich.allergotracker.features.exposure.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ObserveReusableEventCatalogUseCase
import io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureDefaultsUseCase
import io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureEventUseCase

class CreateExposureViewModelFactory(
    private val createExposureDefaultsUseCase: CreateExposureDefaultsUseCase,
    private val createExposureEventUseCase: CreateExposureEventUseCase,
    private val observeReusableEventCatalogUseCase: ObserveReusableEventCatalogUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateExposureViewModel::class.java)) {
            return CreateExposureViewModel(
                createExposureDefaultsUseCase = createExposureDefaultsUseCase,
                createExposureEventUseCase = createExposureEventUseCase,
                observeReusableEventCatalogUseCase = observeReusableEventCatalogUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
