package io.github.mateovich.allergotracker.features.outbreak.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ObserveReusableEventCatalogUseCase
import io.github.mateovich.allergotracker.features.outbreak.domain.CreateOutbreakDefaultsUseCase
import io.github.mateovich.allergotracker.features.outbreak.domain.CreateOutbreakEventUseCase

class CreateOutbreakViewModelFactory(
    private val createOutbreakDefaultsUseCase: CreateOutbreakDefaultsUseCase,
    private val createOutbreakEventUseCase: CreateOutbreakEventUseCase,
    private val observeReusableEventCatalogUseCase: ObserveReusableEventCatalogUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateOutbreakViewModel::class.java)) {
            return CreateOutbreakViewModel(
                createOutbreakDefaultsUseCase = createOutbreakDefaultsUseCase,
                createOutbreakEventUseCase = createOutbreakEventUseCase,
                observeReusableEventCatalogUseCase = observeReusableEventCatalogUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
