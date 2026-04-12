package com.example.app.features.export.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.export.domain.ExportConfiguration
import com.example.app.features.export.domain.ExportFormat
import com.example.app.features.export.domain.ExportScope
import com.example.app.features.export.domain.ValidateExportConfigurationUseCase
import com.example.app.features.records.domain.ObserveRecordCountUseCase
import com.example.app.features.records.domain.RecordEventType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

class ExportViewModel(
    observeRecordCountUseCase: ObserveRecordCountUseCase,
    private val validateExportConfigurationUseCase: ValidateExportConfigurationUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(createState())
    val state: StateFlow<ExportUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<ExportUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        observeRecordCountUseCase()
            .onEach { count ->
                mutableState.value = createState(
                    selectedFormat = mutableState.value.selectedFormat,
                    selectedScope = mutableState.value.selectedScope,
                    formatValidationMessage = mutableState.value.formatValidationMessage,
                    scopeValidationMessage = mutableState.value.scopeValidationMessage,
                    generalValidationMessage = mutableState.value.generalValidationMessage,
                    requestMessage = mutableState.value.requestMessage,
                    isConfirming = mutableState.value.isConfirming,
                    totalRecordCount = count
                )
            }
            .launchIn(viewModelScope)
    }

    fun onIntent(intent: ExportUiIntent) {
        when (intent) {
            is ExportUiIntent.SelectFormat -> {
                mutableState.value = createState(
                    selectedFormat = intent.format,
                    selectedScope = mutableState.value.selectedScope,
                    totalRecordCount = mutableState.value.totalRecordCount
                )
            }

            is ExportUiIntent.SelectScope -> {
                mutableState.value = createState(
                    selectedFormat = mutableState.value.selectedFormat,
                    selectedScope = intent.scope,
                    totalRecordCount = mutableState.value.totalRecordCount
                )
            }

            ExportUiIntent.ConfirmExportStart -> confirmExportStart()
        }
    }

    private fun confirmExportStart() {
        mutableState.value = mutableState.value.copy(isConfirming = true)
        when (val result = validateExportConfigurationUseCase(mutableState.value.selectedFormat, mutableState.value.selectedScope)) {
            is AppResult.Success -> handleValidConfiguration(result.value)
            is AppResult.Error -> handleValidationError(result.error)
        }
    }

    private fun handleValidConfiguration(configuration: ExportConfiguration) {
        mutableState.value = createState(
            selectedFormat = configuration.format,
            selectedScope = configuration.scope,
            requestMessage = "Export request ready. Choose where to save it in the next step.",
            totalRecordCount = mutableState.value.totalRecordCount
        )
        effectChannel.trySend(ExportUiEffect.ExportStartRequested(configuration))
    }

    private fun handleValidationError(error: DomainError) {
        val validationError = error as? DomainError.ValidationError
        mutableState.value = createState(
            selectedFormat = mutableState.value.selectedFormat,
            selectedScope = mutableState.value.selectedScope,
            formatValidationMessage = validationError?.fieldErrors?.get(ValidateExportConfigurationUseCase.FORMAT_FIELD),
            scopeValidationMessage = validationError?.fieldErrors?.get(ValidateExportConfigurationUseCase.SCOPE_FIELD),
            generalValidationMessage = validationError?.message ?: error.message,
            totalRecordCount = mutableState.value.totalRecordCount
        )
    }

    private fun createState(
        selectedFormat: ExportFormat? = null,
        selectedScope: ExportScope? = null,
        formatValidationMessage: String? = null,
        scopeValidationMessage: String? = null,
        generalValidationMessage: String? = null,
        requestMessage: String? = null,
        isConfirming: Boolean = false,
        totalRecordCount: Int = 0
    ): ExportUiState {
        return ExportUiState(
            formatOptions = ExportFormat.entries.map { format ->
                ExportFormatOptionUiModel(
                    format = format,
                    label = format.name,
                    description = "Stable machine-readable export contract.",
                    isSelected = format == selectedFormat
                )
            },
            scopeOptions = defaultExportScopeOptions.map { scope ->
                ExportScopeOptionUiModel(
                    scope = scope,
                    label = scope.label,
                    description = scope.description,
                    isSelected = scope == selectedScope,
                    testTag = scope.testTag
                )
            },
            selectedFormat = selectedFormat,
            selectedScope = selectedScope,
            summaryText = buildSummary(selectedFormat, selectedScope, totalRecordCount),
            formatValidationMessage = formatValidationMessage,
            scopeValidationMessage = scopeValidationMessage,
            generalValidationMessage = generalValidationMessage,
            requestMessage = requestMessage,
            isConfirming = isConfirming,
            totalRecordCount = totalRecordCount
        )
    }

    private fun buildSummary(
        selectedFormat: ExportFormat?,
        selectedScope: ExportScope?,
        totalRecordCount: Int
    ): String {
        if (selectedFormat == null && selectedScope == null) {
            return "Choose an export format and scope to review what will be included."
        }
        if (selectedFormat == null) {
            return "${selectedScope?.label ?: "Selected records"} are ready. Choose a format to continue."
        }
        if (selectedScope == null) {
            return "${selectedFormat.name} is selected. Choose which records to include."
        }
        return when (selectedScope) {
            ExportScope.AllRecords -> {
                val countSummary = when (totalRecordCount) {
                    0 -> "all saved records currently on this device"
                    1 -> "the 1 saved record on this device"
                    else -> "all $totalRecordCount saved records on this device"
                }
                "${selectedFormat.name} export will include $countSummary."
            }

            is ExportScope.EventTypeRecords -> {
                "${selectedFormat.name} export will include only ${selectedScope.eventType.label.lowercase()} records saved on this device."
            }
        }
    }
}

private val ExportScope.label: String
    get() = when (this) {
        ExportScope.AllRecords -> "All records"
        is ExportScope.EventTypeRecords -> when (eventType) {
            RecordEventType.INGESTION -> "Ingestion records"
            RecordEventType.EXPOSURE -> "Exposure records"
            RecordEventType.OUTBREAK -> "Outbreak records"
        }
    }

private val ExportScope.description: String
    get() = when (this) {
        ExportScope.AllRecords -> "Include every saved record in the export draft."
        is ExportScope.EventTypeRecords -> when (eventType) {
            RecordEventType.INGESTION -> "Include only ingestion records."
            RecordEventType.EXPOSURE -> "Include only exposure records."
            RecordEventType.OUTBREAK -> "Include only outbreak records."
        }
    }

private val ExportScope.testTag: String
    get() = when (this) {
        ExportScope.AllRecords -> "export-scope-all-records"
        is ExportScope.EventTypeRecords -> "export-scope-${eventType.name.lowercase()}"
    }

private val RecordEventType.label: String
    get() = when (this) {
        RecordEventType.INGESTION -> "Ingestion"
        RecordEventType.EXPOSURE -> "Exposure"
        RecordEventType.OUTBREAK -> "Outbreak"
    }
