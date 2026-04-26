package io.github.mateovich.allergotracker.features.exposure.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ObserveReusableEventCatalogUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogKind
import io.github.mateovich.allergotracker.features.eventcatalog.domain.emptyObserveReusableEventCatalogUseCase
import io.github.mateovich.allergotracker.features.eventcatalog.ui.ReusableEventCatalogOptionUiModel
import io.github.mateovich.allergotracker.features.eventcatalog.ui.filterForQuery
import io.github.mateovich.allergotracker.features.eventcatalog.ui.hasExactName
import io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureDefaultsUseCase
import io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureEventCommand
import io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureEventUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    private val zoneId = ZoneId.systemDefault()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
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

            CreateExposureUiIntent.ClearRetiredExposureCatalogSelection -> updateState {
                it.copy(
                    selectedExposureTypeCatalogId = null,
                    selectedExposureTypeSummary = null,
                    formMessage = "Saved exposure type was retired. Review the text value before saving.",
                    canSave = it.exposureType.isNotBlank()
                ).withFilteredExposureTypes()
            }

            CreateExposureUiIntent.EditSelectedExposureCatalog -> {
                val selectedId = mutableState.value.selectedExposureTypeCatalogId ?: return
                effectChannel.trySend(
                    CreateExposureUiEffect.NavigateToEditEventCatalog(
                        ReusableEventCatalogKind.EXPOSURE_TYPE.storageValue,
                        selectedId
                    )
                )
            }

            is CreateExposureUiIntent.UpdateNotes -> updateState {
                it.copy(notes = intent.value)
            }

            is CreateExposureUiIntent.SelectIntensityLevel -> updateState {
                it.copy(
                    selectedIntensityLevel = intent.level,
                    intensityLevelError = null,
                    formMessage = ""
                )
            }

            is CreateExposureUiIntent.SelectOccurredOn -> updateState {
                it.withOccurredAt(composeOccurredAt(intent.value, it.selectedOccurredTime))
            }

            is CreateExposureUiIntent.SelectOccurredTime -> updateState {
                it.withOccurredAt(composeOccurredAt(it.selectedOccurredOn, intent.value))
            }

            is CreateExposureUiIntent.SelectTimePreset -> {
                val nextOccurredAt = createExposureDefaultsUseCase().occurredAtEpochMs - intent.preset.offsetMillis
                updateState {
                    it.withOccurredAt(nextOccurredAt, intent.preset)
                }
            }

            CreateExposureUiIntent.Save -> save()
        }
    }

    private fun save() {
        val currentState = mutableState.value
        if (currentState.isSaving) return

        updateState { it.copy(isSaving = true, formMessage = "") }
        viewModelScope.launch {
            when (
                val result = createExposureEventUseCase(
                    CreateExposureEventCommand(
                        exposureType = currentState.exposureType,
                        occurredAtEpochMs = currentState.occurredAtEpochMs,
                        notes = currentState.notes,
                        intensityLevel = currentState.selectedIntensityLevel,
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

    private fun buildInitialState(): CreateExposureUiState {
        val defaults = createExposureDefaultsUseCase()
        return CreateExposureUiState(
            selectedIntensityLevel = defaults.intensityLevel,
            timeOptions = ExposureTimePreset.entries.map { preset ->
                ExposureTimeOptionUiModel(
                    key = preset.name,
                    label = preset.label,
                    offsetMillis = preset.offsetMillis
                )
            },
            intensityOptions = exposureIntensityOptions()
        ).withOccurredAt(defaults.occurredAtEpochMs, ExposureTimePreset.NOW)
            .withFilteredExposureTypes()
    }

    private fun composeOccurredAt(date: LocalDate, time: LocalTime): Long {
        return LocalDateTime.of(date, time.withSecond(0).withNano(0))
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    private fun normalizeOccurredAt(epochMillis: Long): Long {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .withSecond(0)
            .withNano(0)
            .toInstant()
            .toEpochMilli()
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

    private fun CreateExposureUiState.withOccurredAt(
        epochMillis: Long,
        selectedPreset: ExposureTimePreset? = null
    ): CreateExposureUiState {
        val normalizedEpochMs = normalizeOccurredAt(epochMillis)
        val occurredAt = Instant.ofEpochMilli(normalizedEpochMs).atZone(zoneId)
        return copy(
            occurredAtEpochMs = normalizedEpochMs,
            occurredAtInput = dateTimeFormatter.format(occurredAt.toLocalDateTime()),
            occurredAtLabel = timeFormatter.format(occurredAt),
            selectedOccurredOn = occurredAt.toLocalDate(),
            selectedOccurredTime = occurredAt.toLocalTime().withSecond(0).withNano(0),
            selectedTimePreset = selectedPreset,
            occurredAtError = null,
            formMessage = ""
        )
    }

    fun formatOccurredOnLabel(value: LocalDate): String = dateFormatter.format(value)

    fun formatOccurredTimeLabel(value: LocalTime): String = value.withSecond(0).withNano(0).format(timeFormatter)

    private fun exposureIntensityOptions(): List<ExposureIntensityOptionUiModel> {
        return (1..5).map { level ->
            ExposureIntensityOptionUiModel(level = level, label = level.toString())
        }
    }
}
