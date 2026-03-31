package com.example.app.features.records.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.records.domain.ObserveRecordDetailUseCase
import com.example.app.features.records.domain.RecordEventType

class RecordDetailViewModelFactory(
    private val recordId: Long,
    private val eventType: RecordEventType,
    private val observeRecordDetailUseCase: ObserveRecordDetailUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecordDetailViewModel::class.java)) {
            return RecordDetailViewModel(
                recordId = recordId,
                eventType = eventType,
                observeRecordDetailUseCase = observeRecordDetailUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
