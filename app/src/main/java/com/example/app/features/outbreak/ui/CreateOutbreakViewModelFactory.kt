package com.example.app.features.outbreak.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.outbreak.domain.CreateOutbreakDefaultsUseCase
import com.example.app.features.outbreak.domain.CreateOutbreakEventUseCase

class CreateOutbreakViewModelFactory(
    private val createOutbreakDefaultsUseCase: CreateOutbreakDefaultsUseCase,
    private val createOutbreakEventUseCase: CreateOutbreakEventUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateOutbreakViewModel::class.java)) {
            return CreateOutbreakViewModel(
                createOutbreakDefaultsUseCase = createOutbreakDefaultsUseCase,
                createOutbreakEventUseCase = createOutbreakEventUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
