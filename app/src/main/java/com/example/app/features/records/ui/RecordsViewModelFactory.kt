package com.example.app.features.records.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.records.domain.ObserveRecordCountUseCase
import com.example.app.features.records.domain.ObserveRecordsUseCase

class RecordsViewModelFactory(
    private val currentTimeProvider: CurrentTimeProvider,
    private val observeRecordCountUseCase: ObserveRecordCountUseCase,
    private val observeRecordsUseCase: ObserveRecordsUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecordsViewModel::class.java)) {
            return RecordsViewModel(currentTimeProvider, observeRecordCountUseCase, observeRecordsUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
