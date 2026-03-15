package com.example.app.features.outbreak.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.outbreak.domain.GetOutbreakEventUseCase
import com.example.app.features.outbreak.domain.UpdateOutbreakEventUseCase

class EditOutbreakViewModelFactory(
    private val recordId: Long,
    private val getOutbreakEventUseCase: GetOutbreakEventUseCase,
    private val updateOutbreakEventUseCase: UpdateOutbreakEventUseCase,
    private val currentTimeProvider: CurrentTimeProvider
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditOutbreakViewModel::class.java)) {
            return EditOutbreakViewModel(recordId, getOutbreakEventUseCase, updateOutbreakEventUseCase, currentTimeProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
