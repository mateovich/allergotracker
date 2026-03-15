package com.example.app.features.exposure.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.exposure.domain.CreateExposureDefaultsUseCase
import com.example.app.features.exposure.domain.CreateExposureEventUseCase

class CreateExposureViewModelFactory(
    private val createExposureDefaultsUseCase: CreateExposureDefaultsUseCase,
    private val createExposureEventUseCase: CreateExposureEventUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateExposureViewModel::class.java)) {
            return CreateExposureViewModel(
                createExposureDefaultsUseCase = createExposureDefaultsUseCase,
                createExposureEventUseCase = createExposureEventUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
