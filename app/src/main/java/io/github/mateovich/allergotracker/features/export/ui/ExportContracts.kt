package io.github.mateovich.allergotracker.features.export.ui

import io.github.mateovich.allergotracker.features.export.domain.ExportConfiguration
import io.github.mateovich.allergotracker.features.export.domain.ExportDeliveryResult
import io.github.mateovich.allergotracker.features.export.domain.ExportDestination
import io.github.mateovich.allergotracker.features.export.domain.ExportFormat
import io.github.mateovich.allergotracker.features.export.domain.ExportScope
import io.github.mateovich.allergotracker.features.export.domain.GeneratedJsonExport
import io.github.mateovich.allergotracker.features.import.domain.StructuredImportApplyResult
import io.github.mateovich.allergotracker.features.import.domain.StructuredImportReview
import io.github.mateovich.allergotracker.features.records.domain.RecordEventType

data class ExportFormatOptionUiModel(
    val format: ExportFormat,
    val label: String,
    val description: String,
    val isSelected: Boolean
)

data class ExportScopeOptionUiModel(
    val scope: ExportScope,
    val label: String,
    val description: String,
    val isSelected: Boolean,
    val testTag: String
)

data class ExportDestinationOptionUiModel(
    val destination: ExportDestination,
    val label: String,
    val description: String,
    val isSelected: Boolean,
    val testTag: String
)

data class ExportUiState(
    val title: String = "Export",
    val distributionNotice: String = "This build keeps capture, review, export, and import available without Play or store services.",
    val unavailableCapabilityMessage: String? = null,
    val formatOptions: List<ExportFormatOptionUiModel> = emptyList(),
    val scopeOptions: List<ExportScopeOptionUiModel> = emptyList(),
    val destinationOptions: List<ExportDestinationOptionUiModel> = emptyList(),
    val selectedFormat: ExportFormat? = null,
    val selectedScope: ExportScope? = null,
    val selectedDestination: ExportDestination? = null,
    val summaryText: String = "Choose an export format and scope to review what will be included.",
    val formatValidationMessage: String? = null,
    val scopeValidationMessage: String? = null,
    val generalValidationMessage: String? = null,
    val warningMessages: List<String> = emptyList(),
    val statusMessage: String? = null,
    val importValidationMessage: String? = null,
    val importStatusMessage: String? = null,
    val isBusy: Boolean = false,
    val isReviewingWarnings: Boolean = false,
    val stagedExport: GeneratedJsonExport? = null,
    val deliveryResult: ExportDeliveryResult? = null,
    val importReview: StructuredImportReview? = null,
    val importResult: StructuredImportApplyResult? = null,
    val confirmActionLabel: String = "Stage export",
    val privacyNotice: String = "Exports can contain sensitive local records. Review the summary before sharing.",
    val totalRecordCount: Int = 0
)

sealed interface ExportUiIntent {
    data class SelectFormat(val format: ExportFormat) : ExportUiIntent
    data class SelectScope(val scope: ExportScope) : ExportUiIntent
    data class SelectDestination(val destination: ExportDestination) : ExportUiIntent
    data object ConfirmExportStart : ExportUiIntent
    data object ContinueWarningReview : ExportUiIntent
    data object CancelWarningReview : ExportUiIntent
    data object ConfirmFinalDelivery : ExportUiIntent
    data object CancelFinalDelivery : ExportUiIntent
    data object RequestImportSelection : ExportUiIntent
    data class ImportDocumentSelected(val documentUri: String?) : ExportUiIntent
    data object ConfirmImportApply : ExportUiIntent
    data object CancelImportReview : ExportUiIntent
}

sealed interface ExportUiEffect {
    data class ExportDelivered(
        val configuration: ExportConfiguration,
        val result: ExportDeliveryResult
    ) : ExportUiEffect
}

internal val defaultExportScopeOptions: List<ExportScope> = listOf(
    ExportScope.AllRecords,
    ExportScope.EventTypeRecords(RecordEventType.INGESTION),
    ExportScope.EventTypeRecords(RecordEventType.EXPOSURE),
    ExportScope.EventTypeRecords(RecordEventType.OUTBREAK)
)

internal val defaultExportDestinationOptions: List<ExportDestination> = listOf(
    ExportDestination.DeviceDownloads
)
