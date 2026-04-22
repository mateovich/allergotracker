package io.github.mateovich.allergotracker.features.records.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import io.github.mateovich.allergotracker.features.records.domain.ObserveRecordCountUseCase
import io.github.mateovich.allergotracker.features.records.domain.ObserveRecordsUseCase

class RecordsViewModelFactory(
    private val currentTimeProvider: CurrentTimeProvider,
    private val observeRecordCountUseCase: ObserveRecordCountUseCase,
    private val observeRecordsUseCase: ObserveRecordsUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecordsViewModel::class.java)) {
            return RecordsViewModel(
                currentTimeProvider = currentTimeProvider,
                observeRecordCountUseCase = observeRecordCountUseCase,
                observeRecordsUseCase = observeRecordsUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(RecordsViewModel::class.java)) {
            return RecordsViewModel(
                currentTimeProvider = currentTimeProvider,
                observeRecordCountUseCase = observeRecordCountUseCase,
                observeRecordsUseCase = observeRecordsUseCase,
                savedStateHandle = extras.createSavedStateHandle()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
