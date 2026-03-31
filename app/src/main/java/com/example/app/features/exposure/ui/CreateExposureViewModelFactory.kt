package com.example.app.features.exposure.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.eventcatalog.domain.ObserveReusableEventCatalogUseCase
import com.example.app.features.exposure.domain.CreateExposureDefaultsUseCase
import com.example.app.features.exposure.domain.CreateExposureEventUseCase

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
