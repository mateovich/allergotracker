package com.example.app.features.exposure.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.eventcatalog.domain.ObserveReusableEventCatalogUseCase
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.domain.emptyObserveReusableEventCatalogUseCase
import com.example.app.features.eventcatalog.ui.ReusableEventCatalogOptionUiModel
import com.example.app.features.eventcatalog.ui.filterForQuery
import com.example.app.features.eventcatalog.ui.hasExactName
import com.example.app.features.exposure.domain.DeleteExposureEventUseCase
import com.example.app.features.exposure.domain.GetExposureEventUseCase
import com.example.app.features.exposure.domain.UpdateExposureEventCommand
import com.example.app.features.exposure.domain.UpdateExposureEventUseCase
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class EditExposureViewModel(
    private val recordId: Long,
    private val getExposureEventUseCase: GetExposureEventUseCase,
    private val deleteExposureEventUseCase: DeleteExposureEventUseCase,
    private val updateExposureEventUseCase: UpdateExposureEventUseCase,
    private val currentTimeProvider: CurrentTimeProvider,
    private val observeReusableEventCatalogUseCase: ObserveReusableEventCatalogUseCase = emptyObserveReusableEventCatalogUseCase()
) : ViewModel() {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val mutableState = MutableStateFlow(
        EditExposureUiState(
            timeOptions = ExposureTimePreset.entries.map { ExposureTimeOptionUiModel(it.name, it.label, it.offsetMillis) },
            intensityOptions = exposureIntensityOptions()
        )
    )
    val state: StateFlow<EditExposureUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<EditExposureUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeReusableEventCatalogUseCase(ReusableEventCatalogKind.EXPOSURE_TYPE).collect { entries ->
                updateState {
                    val availableExposureTypes = entries.map { entry ->
                        ReusableEventCatalogOptionUiModel(
                            id = entry.id,
                            name = entry.name,
                            kind = entry.kind,
                            usageCount = entry.usageCount,
                            lastUsedAtEpochMs = entry.lastUsedAtEpochMs,
                            updatedAtEpochMs = entry.updatedAtEpochMs
                        )
                    }
                    val selectedExposureTypeSummary = it.selectedExposureTypeCatalogId
                        ?.takeIf { selectedId -> availableExposureTypes.any { option -> option.id == selectedId } }
                        ?.let { _ -> "Saved exposure type selected: ${it.exposureType}" }
                    it.copy(
                        availableExposureTypes = availableExposureTypes,
                        selectedExposureTypeSummary = selectedExposureTypeSummary
                    ).withFilteredExposureTypes()
                }
            }
        }
    }

    fun onIntent(intent: EditExposureUiIntent) {
        when (intent) {
            EditExposureUiIntent.Load -> loadIfNeeded()
            is EditExposureUiIntent.UpdateExposureType -> updateState {
                it.copy(
                    exposureType = intent.value,
                    selectedExposureTypeCatalogId = null,
                    selectedExposureTypeSummary = null,
                    exposureTypeError = null,
                    formMessage = "",
                    canSave = intent.value.isNotBlank()
                ).withFilteredExposureTypes()
            }
            is EditExposureUiIntent.SelectExposureCatalogOption -> updateState {
                it.copy(
                    exposureType = intent.name,
                    selectedExposureTypeCatalogId = intent.id,
                    selectedExposureTypeSummary = "Saved exposure type selected: ${intent.name}",
                    exposureTypeError = null,
                    formMessage = "",
                    canSave = intent.name.isNotBlank()
                ).withFilteredExposureTypes()
            }
            EditExposureUiIntent.UseTypedExposureType -> updateState {
                it.copy(
                    selectedExposureTypeCatalogId = null,
                    selectedExposureTypeSummary = "New exposure type will be saved locally.",
                    exposureTypeError = null,
                    formMessage = "",
                    canSave = it.exposureType.isNotBlank()
                ).withFilteredExposureTypes()
            }
            EditExposureUiIntent.ClearExposureCatalogSelection -> updateState {
                it.copy(
                    selectedExposureTypeCatalogId = null,
                    selectedExposureTypeSummary = null,
                    exposureTypeError = null,
                    formMessage = "",
                    canSave = it.exposureType.isNotBlank()
                ).withFilteredExposureTypes()
            }
            EditExposureUiIntent.ClearRetiredExposureCatalogSelection -> updateState {
                it.copy(
                    selectedExposureTypeCatalogId = null,
                    selectedExposureTypeSummary = null,
                    formMessage = "Saved exposure type was retired. Review the text value before saving.",
                    canSave = it.exposureType.isNotBlank()
                ).withFilteredExposureTypes()
            }
            EditExposureUiIntent.EditSelectedExposureCatalog -> {
                val selectedId = mutableState.value.selectedExposureTypeCatalogId ?: return
                effectChannel.trySend(
                    EditExposureUiEffect.NavigateToEditEventCatalog(
                        ReusableEventCatalogKind.EXPOSURE_TYPE.storageValue,
                        selectedId
                    )
                )
            }
            is EditExposureUiIntent.UpdateNotes -> updateState { it.copy(notes = intent.value) }
            is EditExposureUiIntent.SelectIntensityLevel -> updateState {
                it.copy(
                    selectedIntensityLevel = intent.level,
                    intensityLevelError = null,
                    formMessage = ""
                )
            }
            is EditExposureUiIntent.UpdateOccurredAtInput -> {
                val parsedOccurredAt = parseOccurredAtInput(intent.value)
                updateState {
                    it.copy(
                        occurredAtInput = intent.value,
                        occurredAtEpochMs = parsedOccurredAt ?: it.occurredAtEpochMs,
                        occurredAtLabel = parsedOccurredAt?.let(::formatTime).orEmpty(),
                        occurredAtError = null,
                        formMessage = ""
                    )
                }
            }
            is EditExposureUiIntent.SelectTimePreset -> {
                val nextOccurredAt = currentTimeProvider.nowEpochMillis() - intent.preset.offsetMillis
                updateState {
                    it.copy(
                        selectedTimePreset = intent.preset,
                        occurredAtEpochMs = nextOccurredAt,
                        occurredAtInput = formatDateTimeInput(nextOccurredAt),
                        occurredAtLabel = formatTime(nextOccurredAt),
                        occurredAtError = null,
                        formMessage = ""
                    )
                }
            }
            EditExposureUiIntent.ShowDeleteConfirmation -> showDeleteConfirmation()
            EditExposureUiIntent.DismissDeleteConfirmation -> updateState { it.copy(isDeleteConfirmationVisible = false) }
            EditExposureUiIntent.ConfirmDelete -> delete()
            EditExposureUiIntent.Save -> save()
        }
    }

    private fun loadIfNeeded() {
        val currentState = mutableState.value
        if (currentState.exposureType.isNotBlank()) return
        updateState { it.copy(isLoading = true, formMessage = "") }
        viewModelScope.launch {
            when (val result = getExposureEventUseCase(recordId)) {
                is AppResult.Success -> {
                    val event = result.value
                    updateState {
                        it.copy(
                            exposureType = event.exposureType,
                            deleteRecordLabel = event.exposureType,
                            notes = event.notes.orEmpty(),
                            selectedIntensityLevel = event.intensityLevel,
                            occurredAtEpochMs = event.occurredAtEpochMs,
                            occurredAtInput = formatDateTimeInput(event.occurredAtEpochMs),
                            occurredAtLabel = formatTime(event.occurredAtEpochMs),
                            selectedExposureTypeCatalogId = event.exposureTypeCatalogId,
                            selectedExposureTypeSummary = event.exposureTypeCatalogId
                                ?.takeIf { selectedId -> it.availableExposureTypes.any { option -> option.id == selectedId } }
                                ?.let { _ ->
                                "Saved exposure type selected: ${event.exposureType}"
                            },
                            isLoading = false,
                            canSave = event.exposureType.isNotBlank(),
                            canDelete = event.exposureType.isNotBlank()
                        ).withFilteredExposureTypes()
                    }
                }
                is AppResult.Error -> updateState { it.copy(isLoading = false, formMessage = result.error.message) }
            }
        }
    }

    private fun save() {
        val currentState = mutableState.value
        if (!currentState.canSave || currentState.isSaving || currentState.isDeleting || currentState.isLoading) return
        val parsedOccurredAt = parseOccurredAtInput(currentState.occurredAtInput)
        if (parsedOccurredAt == null) {
            updateState {
                it.copy(
                    isSaving = false,
                    occurredAtError = "Enter date and time as yyyy-MM-dd HH:mm.",
                    formMessage = "Please fix the highlighted fields."
                )
            }
            return
        }
        updateState { it.copy(isSaving = true, formMessage = "") }
        viewModelScope.launch {
            when (
                val result = updateExposureEventUseCase(
                    UpdateExposureEventCommand(
                        id = recordId,
                        exposureType = currentState.exposureType,
                        occurredAtEpochMs = parsedOccurredAt,
                        notes = currentState.notes,
                        intensityLevel = currentState.selectedIntensityLevel,
                        exposureTypeCatalogId = currentState.selectedExposureTypeCatalogId
                    )
                )
            ) {
                is AppResult.Success -> {
                    updateState { it.copy(isSaving = false) }
                    effectChannel.trySend(EditExposureUiEffect.NavigateToRecords)
                }
                is AppResult.Error -> {
                    val error = result.error
                    updateState {
                        it.copy(
                            isSaving = false,
                            exposureTypeError = error.fieldError("exposureType"),
                            intensityLevelError = error.fieldError("intensityLevel"),
                            occurredAtError = error.fieldError("occurredAtEpochMs"),
                            formMessage = error.message,
                            canSave = it.exposureType.isNotBlank()
                        )
                    }
                }
            }
        }
    }

    private fun delete() {
        val currentState = mutableState.value
        if (!currentState.isDeleteConfirmationVisible || currentState.isDeleting || currentState.isSaving || currentState.isLoading) return
        updateState {
            it.copy(
                isDeleting = true,
                isDeleteConfirmationVisible = false,
                formMessage = ""
            )
        }
        viewModelScope.launch {
            when (val result = deleteExposureEventUseCase(recordId)) {
                is AppResult.Success -> {
                    updateState { it.copy(isDeleting = false) }
                    effectChannel.trySend(EditExposureUiEffect.NavigateToRecords)
                }
                is AppResult.Error -> {
                    updateState {
                        it.copy(
                            isDeleting = false,
                            formMessage = result.error.message,
                            canSave = it.exposureType.isNotBlank()
                        )
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmation() {
        val currentState = mutableState.value
        if (currentState.isLoading || currentState.isSaving || currentState.isDeleting || !currentState.canDelete) return
        updateState { it.copy(isDeleteConfirmationVisible = true, formMessage = "") }
    }

    private fun formatTime(epochMillis: Long): String = timeFormatter.format(Instant.ofEpochMilli(epochMillis))

    private fun formatDateTimeInput(epochMillis: Long): String {
        return dateTimeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime())
    }

    private fun parseOccurredAtInput(value: String): Long? {
        return try {
            LocalDateTime.parse(value.trim(), dateTimeFormatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun updateState(transform: (EditExposureUiState) -> EditExposureUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun DomainError.fieldError(key: String): String? {
        return when (this) {
            is DomainError.ValidationError -> fieldErrors[key]
            else -> null
        }
    }

    private fun EditExposureUiState.withFilteredExposureTypes(): EditExposureUiState {
        val filteredOptions = availableExposureTypes.filterForQuery(exposureType)
        return copy(
            filteredExposureTypes = filteredOptions,
            showCreateExposureTypeOption = exposureType.trim().isNotBlank() && !availableExposureTypes.hasExactName(exposureType)
        )
    }

    private fun exposureIntensityOptions(): List<ExposureIntensityOptionUiModel> {
        return (1..5).map { level ->
            ExposureIntensityOptionUiModel(level = level, label = level.toString())
        }
    }
}
