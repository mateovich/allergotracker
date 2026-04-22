package com.example.app.features.export.ui

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.core.distribution.AppDistribution
import com.example.app.features.export.domain.DeliverStagedExportUseCase
import com.example.app.features.export.domain.ExportConfiguration
import com.example.app.features.export.domain.ExportDeliveryResult
import com.example.app.features.export.domain.ExportDestination
import com.example.app.features.export.domain.ExportFormat
import com.example.app.features.export.domain.ExportPreflightReview
import com.example.app.features.export.domain.ExportScope
import com.example.app.features.export.domain.ExportWarningDecision
import com.example.app.features.export.domain.GenerateVersionedJsonExportUseCase
import com.example.app.features.export.domain.PreparedExport
import com.example.app.features.export.domain.ValidateExportConfigurationUseCase
import com.example.app.features.import.domain.ApplyStructuredImportUseCase
import com.example.app.features.import.domain.ReviewStructuredImportUseCase
import com.example.app.features.import.domain.StructuredImportReview
import com.example.app.features.records.domain.ObserveRecordCountUseCase
import com.example.app.features.records.domain.RecordEventType
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ExportViewModel(
    private val appDistribution: AppDistribution,
    observeRecordCountUseCase: ObserveRecordCountUseCase,
    private val validateExportConfigurationUseCase: ValidateExportConfigurationUseCase,
    private val generateVersionedJsonExportUseCase: GenerateVersionedJsonExportUseCase,
    private val deliverStagedExportUseCase: DeliverStagedExportUseCase,
    private val reviewStructuredImportUseCase: ReviewStructuredImportUseCase,
    private val applyStructuredImportUseCase: ApplyStructuredImportUseCase
) : ViewModel() {
    private val mutableState = MutableStateFlow(createState())
    val state: StateFlow<ExportUiState> = mutableState.asStateFlow()

    private var pendingWarningConfiguration: ExportConfiguration? = null
    private var pendingPreparedExport: PreparedExport? = null
    private var pendingImportReview: StructuredImportReview? = null
    private var importReviewJob: Job? = null
    private var importReviewRequestToken: Long = 0L

    private val effectChannel = Channel<ExportUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        observeRecordCountUseCase()
            .onEach { count ->
                val currentState = mutableState.value
                mutableState.value = createState(
                    selectedFormat = currentState.selectedFormat,
                    selectedScope = currentState.selectedScope,
                    selectedDestination = currentState.selectedDestination,
                    formatValidationMessage = currentState.formatValidationMessage,
                    scopeValidationMessage = currentState.scopeValidationMessage,
                    generalValidationMessage = currentState.generalValidationMessage,
                    warningMessages = currentState.warningMessages,
                    statusMessage = currentState.statusMessage,
                    importValidationMessage = currentState.importValidationMessage,
                    importStatusMessage = currentState.importStatusMessage,
                    isBusy = currentState.isBusy,
                    isReviewingWarnings = currentState.isReviewingWarnings,
                    stagedExport = currentState.stagedExport,
                    deliveryResult = currentState.deliveryResult,
                    importReview = currentState.importReview,
                    importResult = currentState.importResult,
                    summaryText = currentState.summaryText.takeIf {
                        currentState.stagedExport != null || currentState.deliveryResult != null
                    },
                    totalRecordCount = count
                )
            }
            .launchIn(viewModelScope)
    }

    fun onIntent(intent: ExportUiIntent) {
        when (intent) {
            is ExportUiIntent.SelectFormat -> {
                if (mutableState.value.isBusy || mutableState.value.isReviewingWarnings) return
                mutableState.value = createState(
                    selectedFormat = intent.format,
                    selectedScope = mutableState.value.selectedScope,
                    totalRecordCount = mutableState.value.totalRecordCount
                )
            }

            is ExportUiIntent.SelectScope -> {
                if (mutableState.value.isBusy || mutableState.value.isReviewingWarnings) return
                mutableState.value = createState(
                    selectedFormat = mutableState.value.selectedFormat,
                    selectedScope = intent.scope,
                    totalRecordCount = mutableState.value.totalRecordCount
                )
            }

            is ExportUiIntent.SelectDestination -> selectDestination(intent.destination)
            ExportUiIntent.ConfirmExportStart -> confirmExportStart()
            ExportUiIntent.ContinueWarningReview -> continueWarningReview()
            ExportUiIntent.CancelWarningReview -> cancelWarningReview()
            ExportUiIntent.ConfirmFinalDelivery -> confirmFinalDelivery()
            ExportUiIntent.CancelFinalDelivery -> cancelFinalDelivery()
            ExportUiIntent.RequestImportSelection -> Unit
            is ExportUiIntent.ImportDocumentSelected -> importDocumentSelected(intent.documentUri)
            ExportUiIntent.ConfirmImportApply -> confirmImportApply()
            ExportUiIntent.CancelImportReview -> cancelImportReview()
        }
    }

    private fun importDocumentSelected(documentUri: String?) {
        if (mutableState.value.isBusy && importReviewJob == null) return
        if (documentUri.isNullOrBlank()) {
            importReviewJob?.cancel()
            importReviewJob = null
            importReviewRequestToken += 1
            pendingImportReview = null
            mutableState.value = mutableState.value.copy(
                isBusy = false,
                importReview = null,
                importResult = null,
                importValidationMessage = null,
                importStatusMessage = "Import selection canceled. Choose a file to review when ready."
            )
            return
        }
        importReviewJob?.cancel()
        importReviewRequestToken += 1
        val requestToken = importReviewRequestToken
        pendingImportReview = null
        mutableState.value = mutableState.value.copy(
            isBusy = true,
            importValidationMessage = null,
            importStatusMessage = null,
            importResult = null,
            importReview = null
        )
        importReviewJob = viewModelScope.launch {
            when (val result = reviewStructuredImportUseCase(documentUri)) {
                is AppResult.Success -> {
                    if (requestToken != importReviewRequestToken) return@launch
                    pendingImportReview = result.value
                    mutableState.value = mutableState.value.copy(
                        isBusy = false,
                        importReview = result.value,
                        importStatusMessage = "Import review ready. Confirm to apply the validated dataset locally."
                    )
                }

                is AppResult.Error -> {
                    if (requestToken != importReviewRequestToken) return@launch
                    mutableState.value = mutableState.value.copy(
                        isBusy = false,
                        importValidationMessage = result.error.message,
                        importReview = null
                    )
                }
            }
            if (requestToken == importReviewRequestToken) {
                importReviewJob = null
            }
        }
    }

    private fun cancelImportReview() {
        importReviewJob?.cancel()
        importReviewJob = null
        importReviewRequestToken += 1
        pendingImportReview = null
        mutableState.value = mutableState.value.copy(
            isBusy = false,
            importReview = null,
            importResult = null,
            importStatusMessage = "Import review canceled. Choose another file when ready.",
            importValidationMessage = null
        )
    }

    private fun confirmImportApply() {
        val review = pendingImportReview ?: return
        if (mutableState.value.isBusy) return
        mutableState.value = mutableState.value.copy(isBusy = true, importValidationMessage = null)
        viewModelScope.launch {
            when (val result = applyStructuredImportUseCase(review)) {
                is AppResult.Success -> {
                    pendingImportReview = null
                    mutableState.value = mutableState.value.copy(
                        isBusy = false,
                        importReview = null,
                        importResult = result.value,
                        importStatusMessage = "Import completed. The validated dataset was applied atomically."
                    )
                }

                is AppResult.Error -> {
                    mutableState.value = mutableState.value.copy(
                        isBusy = false,
                        importValidationMessage = result.error.message
                    )
                }
            }
        }
    }

    private fun confirmExportStart() {
        if (mutableState.value.isBusy || mutableState.value.isReviewingWarnings) return
        when (val result = validateExportConfigurationUseCase(mutableState.value.selectedFormat, mutableState.value.selectedScope)) {
            is AppResult.Success -> reviewExport(result.value)
            is AppResult.Error -> handleValidationError(result.error)
        }
    }

    private fun reviewExport(configuration: ExportConfiguration) {
        pendingWarningConfiguration = null
        pendingPreparedExport = null
        mutableState.value = createState(
            selectedFormat = configuration.format,
            selectedScope = configuration.scope,
            totalRecordCount = mutableState.value.totalRecordCount,
            isBusy = true
        )
        viewModelScope.launch {
            handlePreflightResult(
                configuration = configuration,
                review = generateVersionedJsonExportUseCase.review(configuration)
            )
        }
    }

    private fun continueWarningReview() {
        val configuration = pendingWarningConfiguration ?: return
        if (mutableState.value.isBusy || !mutableState.value.isReviewingWarnings) return
        generateExport(configuration, ExportWarningDecision.AcceptWarnings)
    }

    private fun cancelWarningReview() {
        if (!mutableState.value.isReviewingWarnings) return
        pendingWarningConfiguration = null
        pendingPreparedExport = null
        mutableState.value = createState(
            selectedFormat = mutableState.value.selectedFormat,
            selectedScope = mutableState.value.selectedScope,
            totalRecordCount = mutableState.value.totalRecordCount,
            statusMessage = "Warning review canceled. Adjust the export settings or stage the export again when ready."
        )
    }

    private fun generateExport(configuration: ExportConfiguration, warningDecision: ExportWarningDecision) {
        pendingWarningConfiguration = configuration.takeIf { warningDecision == ExportWarningDecision.AcceptWarnings }
        mutableState.value = mutableState.value.copy(isBusy = true, isReviewingWarnings = false)
        viewModelScope.launch {
            when (
                val result = generateVersionedJsonExportUseCase(
                    configuration = configuration,
                    warningDecision = warningDecision,
                    preparedExport = pendingPreparedExport
                )
            ) {
                is AppResult.Success -> handleGeneratedExport(result.value)
                is AppResult.Error -> handleGenerationError(result.error, configuration)
            }
        }
    }

    private fun handlePreflightResult(configuration: ExportConfiguration, review: ExportPreflightReview) {
        when {
            review.result.hasBlockingIssues() -> {
                pendingWarningConfiguration = null
                pendingPreparedExport = null
                mutableState.value = createState(
                    selectedFormat = configuration.format,
                    selectedScope = configuration.scope,
                    generalValidationMessage = review.result.blockingIssues.first().message,
                    totalRecordCount = mutableState.value.totalRecordCount
                )
            }

            review.result.requiresExplicitWarningAcceptance() -> {
                pendingWarningConfiguration = configuration
                pendingPreparedExport = review.preparedExport
                mutableState.value = createState(
                    selectedFormat = configuration.format,
                    selectedScope = configuration.scope,
                    warningMessages = review.result.warningIssues.map { it.message },
                    totalRecordCount = mutableState.value.totalRecordCount,
                    isReviewingWarnings = true
                )
            }

            else -> {
                pendingPreparedExport = review.preparedExport
                generateExport(configuration, ExportWarningDecision.RequireCleanPass)
            }
        }
    }

    private fun handleGeneratedExport(result: com.example.app.features.export.domain.GeneratedJsonExport) {
        pendingWarningConfiguration = null
        pendingPreparedExport = null
        mutableState.value = createState(
            selectedFormat = result.configuration.format,
            selectedScope = result.configuration.scope,
            stagedExport = result,
            statusMessage = "Staged export ready. Choose a destination and review the final confirmation before saving.",
            totalRecordCount = mutableState.value.totalRecordCount
        )
    }

    private fun handleGenerationError(error: DomainError, configuration: ExportConfiguration) {
        pendingWarningConfiguration = null
        pendingPreparedExport = null
        mutableState.value = createState(
            selectedFormat = configuration.format,
            selectedScope = configuration.scope,
            generalValidationMessage = error.message,
            totalRecordCount = mutableState.value.totalRecordCount
        )
    }

    private fun selectDestination(destination: ExportDestination) {
        val stagedExport = mutableState.value.stagedExport ?: return
        if (mutableState.value.isBusy || mutableState.value.isReviewingWarnings) return
        mutableState.value = createState(
            selectedFormat = stagedExport.configuration.format,
            selectedScope = stagedExport.configuration.scope,
            selectedDestination = destination,
            stagedExport = stagedExport,
            summaryText = mutableState.value.summaryText,
            totalRecordCount = mutableState.value.totalRecordCount,
            statusMessage = "Review the destination and scope together, then confirm the final save."
        )
    }

    private fun cancelFinalDelivery() {
        val stagedExport = mutableState.value.stagedExport ?: return
        if (mutableState.value.isBusy) return
        mutableState.value = createState(
            selectedFormat = stagedExport.configuration.format,
            selectedScope = stagedExport.configuration.scope,
            stagedExport = stagedExport,
            summaryText = mutableState.value.summaryText,
            totalRecordCount = mutableState.value.totalRecordCount,
            statusMessage = "Final confirmation canceled. The staged export remains local. Choose a destination again when ready."
        )
    }

    private fun confirmFinalDelivery() {
        if (mutableState.value.isBusy || mutableState.value.deliveryResult != null) return
        val stagedExport = mutableState.value.stagedExport ?: return
        val destination = mutableState.value.selectedDestination ?: run {
            mutableState.value = mutableState.value.copy(
                generalValidationMessage = "Choose a destination before confirming the final export."
            )
            return
        }
        mutableState.value = mutableState.value.copy(isBusy = true, generalValidationMessage = null)
        viewModelScope.launch {
            when (val result = deliverStagedExportUseCase(stagedExport = stagedExport, destination = destination)) {
                is AppResult.Success -> handleDeliveredExport(stagedExport.configuration, result.value)
                is AppResult.Error -> handleDeliveryError(result.error, stagedExport, destination)
            }
        }
    }

    private fun handleDeliveredExport(configuration: ExportConfiguration, result: ExportDeliveryResult) {
        mutableState.value = createState(
            selectedFormat = configuration.format,
            selectedScope = configuration.scope,
            deliveryResult = result,
            summaryText = mutableState.value.summaryText,
            totalRecordCount = mutableState.value.totalRecordCount,
            statusMessage = "Export saved as ${result.fileName} to ${result.locationLabel}."
        )
        effectChannel.trySend(
            ExportUiEffect.ExportDelivered(
                configuration = configuration,
                result = result
            )
        )
    }

    private fun handleDeliveryError(
        error: DomainError,
        stagedExport: com.example.app.features.export.domain.GeneratedJsonExport,
        destination: ExportDestination
    ) {
        mutableState.value = createState(
            selectedFormat = stagedExport.configuration.format,
            selectedScope = stagedExport.configuration.scope,
            selectedDestination = destination,
            stagedExport = stagedExport,
            summaryText = mutableState.value.summaryText,
            totalRecordCount = mutableState.value.totalRecordCount,
            generalValidationMessage = error.message,
            statusMessage = "Review the destination and scope together, then confirm the final save."
        )
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
        selectedDestination: ExportDestination? = null,
        formatValidationMessage: String? = null,
        scopeValidationMessage: String? = null,
        generalValidationMessage: String? = null,
        importValidationMessage: String? = null,
        importStatusMessage: String? = null,
        warningMessages: List<String> = emptyList(),
        statusMessage: String? = null,
        summaryText: String? = null,
        isBusy: Boolean = false,
        isReviewingWarnings: Boolean = false,
        stagedExport: com.example.app.features.export.domain.GeneratedJsonExport? = null,
        deliveryResult: ExportDeliveryResult? = null,
        importReview: StructuredImportReview? = null,
        importResult: com.example.app.features.import.domain.StructuredImportApplyResult? = null,
        totalRecordCount: Int = 0
    ): ExportUiState {
        return ExportUiState(
            distributionNotice = appDistribution.distributionNotice,
            unavailableCapabilityMessage = appDistribution.unavailableCapabilityMessage,
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
            destinationOptions = defaultExportDestinationOptions.map { destination ->
                ExportDestinationOptionUiModel(
                    destination = destination,
                    label = destination.label,
                    description = destination.description,
                    isSelected = destination == selectedDestination,
                    testTag = destination.testTag
                )
            },
            selectedFormat = selectedFormat,
            selectedScope = selectedScope,
            selectedDestination = selectedDestination,
            summaryText = summaryText ?: buildSummary(selectedFormat, selectedScope, totalRecordCount),
            formatValidationMessage = formatValidationMessage,
            scopeValidationMessage = scopeValidationMessage,
            generalValidationMessage = generalValidationMessage,
            importValidationMessage = importValidationMessage,
            importStatusMessage = importStatusMessage,
            warningMessages = warningMessages,
            statusMessage = statusMessage,
            isBusy = isBusy,
            isReviewingWarnings = isReviewingWarnings,
            stagedExport = stagedExport,
            deliveryResult = deliveryResult,
            importReview = importReview,
            importResult = importResult,
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

private val ExportDestination.label: String
    get() = when (this) {
        ExportDestination.DeviceDownloads -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Device Downloads"
        } else {
            "App-specific Downloads"
        }
    }

private val ExportDestination.description: String
    get() = when (this) {
        ExportDestination.DeviceDownloads -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Copy the staged export to Downloads only after final confirmation."
        } else {
            "Copy the staged export to app-specific Downloads only after final confirmation."
        }
    }

private val ExportDestination.testTag: String
    get() = when (this) {
        ExportDestination.DeviceDownloads -> "export-destination-downloads"
    }
