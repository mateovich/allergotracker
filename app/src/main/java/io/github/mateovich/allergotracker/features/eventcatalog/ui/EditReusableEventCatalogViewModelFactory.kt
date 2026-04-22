package io.github.mateovich.allergotracker.features.eventcatalog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mateovich.allergotracker.features.eventcatalog.domain.GetReusableEventCatalogEntryUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.domain.GetReusableEventCatalogRetirementImpactUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogKind
import io.github.mateovich.allergotracker.features.eventcatalog.domain.RetireReusableEventCatalogUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.domain.UpdateReusableEventCatalogUseCase

class EditReusableEventCatalogViewModelFactory(
    private val catalogId: Long,
    private val kind: ReusableEventCatalogKind,
    private val getReusableEventCatalogEntryUseCase: GetReusableEventCatalogEntryUseCase,
    private val updateReusableEventCatalogUseCase: UpdateReusableEventCatalogUseCase,
    private val getReusableEventCatalogRetirementImpactUseCase: GetReusableEventCatalogRetirementImpactUseCase,
    private val retireReusableEventCatalogUseCase: RetireReusableEventCatalogUseCase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditReusableEventCatalogViewModel::class.java)) {
            return EditReusableEventCatalogViewModel(
                catalogId = catalogId,
                kind = kind,
                getReusableEventCatalogEntryUseCase = getReusableEventCatalogEntryUseCase,
                updateReusableEventCatalogUseCase = updateReusableEventCatalogUseCase,
                getReusableEventCatalogRetirementImpactUseCase = getReusableEventCatalogRetirementImpactUseCase,
                retireReusableEventCatalogUseCase = retireReusableEventCatalogUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
