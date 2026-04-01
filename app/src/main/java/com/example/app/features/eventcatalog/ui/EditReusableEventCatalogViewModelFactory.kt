package com.example.app.features.eventcatalog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.eventcatalog.domain.GetReusableEventCatalogEntryUseCase
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.domain.UpdateReusableEventCatalogUseCase

class EditReusableEventCatalogViewModelFactory(
    private val catalogId: Long,
    private val kind: ReusableEventCatalogKind,
    private val getReusableEventCatalogEntryUseCase: GetReusableEventCatalogEntryUseCase,
    private val updateReusableEventCatalogUseCase: UpdateReusableEventCatalogUseCase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditReusableEventCatalogViewModel::class.java)) {
            return EditReusableEventCatalogViewModel(
                catalogId = catalogId,
                kind = kind,
                getReusableEventCatalogEntryUseCase = getReusableEventCatalogEntryUseCase,
                updateReusableEventCatalogUseCase = updateReusableEventCatalogUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
