package com.example.app.features.export.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.export.domain.ValidateExportConfigurationUseCase
import com.example.app.features.records.domain.ObserveRecordCountUseCase

class ExportViewModelFactory(
    private val observeRecordCountUseCase: ObserveRecordCountUseCase,
    private val validateExportConfigurationUseCase: ValidateExportConfigurationUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExportViewModel::class.java)) {
            return ExportViewModel(
                observeRecordCountUseCase = observeRecordCountUseCase,
                validateExportConfigurationUseCase = validateExportConfigurationUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
