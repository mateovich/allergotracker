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
import com.example.app.features.exposure.domain.CreateExposureDefaultsUseCase
import com.example.app.features.exposure.domain.CreateExposureEventCommand
import com.example.app.features.exposure.domain.CreateExposureEventUseCase
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

class CreateExposureViewModel(
    private val createExposureDefaultsUseCase: CreateExposureDefaultsUseCase,
    private val createExposureEventUseCase: CreateExposureEventUseCase,
    private val observeReusableEventCatalogUseCase: ObserveReusableEventCatalogUseCase = emptyObserveReusableEventCatalogUseCase()
) : ViewModel() {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val mutableState = MutableStateFlow(buildInitialState())
    val state: StateFlow<CreateExposureUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<CreateExposureUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeReusableEventCatalogUseCase(ReusableEventCatalogKind.EXPOSURE_TYPE).collect { entries ->
                updateState {
                    it.copy(
                        availableExposureTypes = entries.map { entry ->
                            ReusableEventCatalogOptionUiModel(
                                id = entry.id,
                                name = entry.name,
                                kind = entry.kind,
                                usageCount = entry.usageCount,
                                lastUsedAtEpochMs = entry.lastUsedAtEpochMs,
                                updatedAtEpochMs = entry.updatedAtEpochMs
                            )
                        }
                    ).withFilteredExposureTypes()
                }
            }
        }
    }

    fun onIntent(intent: CreateExposureUiIntent) {
        when (intent) {
            is CreateExposureUiIntent.UpdateExposureType -> updateState {
                it.copy(
                    exposureType = intent.value,
                    selectedExposureTypeCatalogId = null,
                    selectedExposureTypeSummary = null,
                    exposureTypeError = null,
                    formMessage = "",
                    canSave = intent.value.isNotBlank()
                ).withFilteredExposureTypes()
            }

            is CreateExposureUiIntent.SelectExposureCatalogOption -> updateState {
                it.copy(
                    exposureType = intent.name,
                    selectedExposureTypeCatalogId = intent.id,
                    selectedExposureTypeSummary = "Saved exposure type selected: ${intent.name}",
                    exposureTypeError = null,
                    formMessage = "",
                    canSave = intent.name.isNotBlank()
                ).withFilteredExposureTypes()
            }

            CreateExposureUiIntent.UseTypedExposureType -> updateState {
                it.copy(
                    selectedExposureTypeCatalogId = null,
                    selectedExposureTypeSummary = "New exposure type will be saved locally.",
                    exposureTypeError = null,
                    formMessage = "",
                    canSave = it.exposureType.isNotBlank()
                ).withFilteredExposureTypes()
            }

            CreateExposureUiIntent.ClearExposureCatalogSelection -> updateState {
                it.copy(
                    selectedExposureTypeCatalogId = null,
                    selectedExposureTypeSummary = null,
                    exposureTypeError = null,
                    formMessage = "",
                    canSave = it.exposureType.isNotBlank()
                ).withFilteredExposureTypes()
            }

            is CreateExposureUiIntent.UpdateNotes -> updateState {
                it.copy(notes = intent.value)
            }

            is CreateExposureUiIntent.UpdateOccurredAtInput -> updateState {
                val parsedOccurredAt = parseOccurredAtInput(intent.value)
                it.copy(
                    occurredAtInput = intent.value,
                    occurredAtEpochMs = parsedOccurredAt ?: it.occurredAtEpochMs,
                    occurredAtLabel = parsedOccurredAt?.let(::formatTime).orEmpty(),
                    isOccurredAtManuallyEdited = true,
                    occurredAtError = null,
                    formMessage = ""
                )
            }

            is CreateExposureUiIntent.SelectTimePreset -> {
                val nextOccurredAt = createExposureDefaultsUseCase().occurredAtEpochMs - intent.preset.offsetMillis
                updateState {
                    it.copy(
                        selectedTimePreset = intent.preset,
                        occurredAtEpochMs = nextOccurredAt,
                        occurredAtInput = formatDateTimeInput(nextOccurredAt),
                        occurredAtLabel = formatTime(nextOccurredAt),
                        isOccurredAtManuallyEdited = false,
                        occurredAtError = null,
                        formMessage = ""
                    )
                }
            }

            CreateExposureUiIntent.Save -> save()
        }
    }

    private fun save() {
        val currentState = mutableState.value
        if (currentState.isSaving) return
        val occurredAtEpochMs = if (currentState.isOccurredAtManuallyEdited) {
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
            parsedOccurredAt
        } else {
            currentState.occurredAtEpochMs
        }

        updateState { it.copy(isSaving = true, formMessage = "") }
        viewModelScope.launch {
            when (
                val result = createExposureEventUseCase(
                    CreateExposureEventCommand(
                        exposureType = currentState.exposureType,
                        occurredAtEpochMs = occurredAtEpochMs,
                        notes = currentState.notes,
                        exposureTypeCatalogId = currentState.selectedExposureTypeCatalogId
                    )
                )
            ) {
                is AppResult.Success -> {
                    updateState { it.copy(isSaving = false) }
                    effectChannel.trySend(CreateExposureUiEffect.NavigateToRecords)
                }

                is AppResult.Error -> {
                    val error = result.error
                    updateState {
                        it.copy(
                            isSaving = false,
                            exposureTypeError = error.fieldError("exposureType"),
                            occurredAtError = error.fieldError("occurredAtEpochMs"),
                            formMessage = error.message,
                            canSave = it.exposureType.isNotBlank()
                        )
                    }
                }
            }
        }
    }

    private fun buildInitialState(): CreateExposureUiState {
        val defaults = createExposureDefaultsUseCase()
        return CreateExposureUiState(
            occurredAtEpochMs = defaults.occurredAtEpochMs,
            occurredAtInput = formatDateTimeInput(defaults.occurredAtEpochMs),
            occurredAtLabel = formatTime(defaults.occurredAtEpochMs),
            isOccurredAtManuallyEdited = false,
            timeOptions = ExposureTimePreset.entries.map { preset ->
                ExposureTimeOptionUiModel(
                    key = preset.name,
                    label = preset.label,
                    offsetMillis = preset.offsetMillis
                )
            }
        ).withFilteredExposureTypes()
    }

    private fun formatTime(epochMillis: Long): String {
        return timeFormatter.format(Instant.ofEpochMilli(epochMillis))
    }

    private fun formatDateTimeInput(epochMillis: Long): String {
        return dateTimeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime())
    }

    private fun parseOccurredAtInput(value: String): Long? {
        return try {
            val zoneId = ZoneId.systemDefault()
            LocalDateTime.parse(value.trim(), dateTimeFormatter)
                .withSecond(0)
                .withNano(0)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun updateState(transform: (CreateExposureUiState) -> CreateExposureUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun DomainError.fieldError(key: String): String? {
        return when (this) {
            is DomainError.ValidationError -> fieldErrors[key]
            else -> null
        }
    }

    private fun CreateExposureUiState.withFilteredExposureTypes(): CreateExposureUiState {
        val filteredOptions = availableExposureTypes.filterForQuery(exposureType)
        return copy(
            filteredExposureTypes = filteredOptions,
            showCreateExposureTypeOption = exposureType.trim().isNotBlank() && !availableExposureTypes.hasExactName(exposureType)
        )
    }
}
