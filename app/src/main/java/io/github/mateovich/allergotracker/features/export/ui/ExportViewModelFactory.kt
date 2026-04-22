package io.github.mateovich.allergotracker.features.export.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mateovich.allergotracker.core.distribution.AppDistribution
import io.github.mateovich.allergotracker.features.export.domain.DeliverStagedExportUseCase
import io.github.mateovich.allergotracker.features.export.domain.GenerateVersionedJsonExportUseCase
import io.github.mateovich.allergotracker.features.export.domain.ValidateExportConfigurationUseCase
import io.github.mateovich.allergotracker.features.import.domain.ApplyStructuredImportUseCase
import io.github.mateovich.allergotracker.features.import.domain.ReviewStructuredImportUseCase
import io.github.mateovich.allergotracker.features.records.domain.ObserveRecordCountUseCase

class ExportViewModelFactory(
    private val appDistribution: AppDistribution,
    private val observeRecordCountUseCase: ObserveRecordCountUseCase,
    private val validateExportConfigurationUseCase: ValidateExportConfigurationUseCase,
    private val generateVersionedJsonExportUseCase: GenerateVersionedJsonExportUseCase,
    private val deliverStagedExportUseCase: DeliverStagedExportUseCase,
    private val reviewStructuredImportUseCase: ReviewStructuredImportUseCase,
    private val applyStructuredImportUseCase: ApplyStructuredImportUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExportViewModel::class.java)) {
            return ExportViewModel(
                appDistribution = appDistribution,
                observeRecordCountUseCase = observeRecordCountUseCase,
                validateExportConfigurationUseCase = validateExportConfigurationUseCase,
                generateVersionedJsonExportUseCase = generateVersionedJsonExportUseCase,
                deliverStagedExportUseCase = deliverStagedExportUseCase,
                reviewStructuredImportUseCase = reviewStructuredImportUseCase,
                applyStructuredImportUseCase = applyStructuredImportUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
