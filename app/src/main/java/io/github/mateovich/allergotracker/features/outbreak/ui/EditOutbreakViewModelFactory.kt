package io.github.mateovich.allergotracker.features.outbreak.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ObserveReusableEventCatalogUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import io.github.mateovich.allergotracker.features.outbreak.domain.DeleteOutbreakEventUseCase
import io.github.mateovich.allergotracker.features.outbreak.domain.GetOutbreakEventUseCase
import io.github.mateovich.allergotracker.features.outbreak.domain.UpdateOutbreakEventUseCase

class EditOutbreakViewModelFactory(
    private val recordId: Long,
    private val getOutbreakEventUseCase: GetOutbreakEventUseCase,
    private val deleteOutbreakEventUseCase: DeleteOutbreakEventUseCase,
    private val updateOutbreakEventUseCase: UpdateOutbreakEventUseCase,
    private val currentTimeProvider: CurrentTimeProvider,
    private val observeReusableEventCatalogUseCase: ObserveReusableEventCatalogUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditOutbreakViewModel::class.java)) {
            return EditOutbreakViewModel(
                recordId,
                getOutbreakEventUseCase,
                deleteOutbreakEventUseCase,
                updateOutbreakEventUseCase,
                currentTimeProvider,
                observeReusableEventCatalogUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
