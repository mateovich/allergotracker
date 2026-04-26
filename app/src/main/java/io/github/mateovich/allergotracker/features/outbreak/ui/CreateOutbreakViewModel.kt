package io.github.mateovich.allergotracker.features.outbreak.ui

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
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionQuantityOption
import io.github.mateovich.allergotracker.features.outbreak.domain.CreateOutbreakDefaultsUseCase
import io.github.mateovich.allergotracker.features.outbreak.domain.CreateOutbreakEventCommand
import io.github.mateovich.allergotracker.features.outbreak.domain.CreateOutbreakEventUseCase
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

class CreateOutbreakViewModel(
    private val createOutbreakDefaultsUseCase: CreateOutbreakDefaultsUseCase,
    private val createOutbreakEventUseCase: CreateOutbreakEventUseCase,
    private val observeReusableEventCatalogUseCase: ObserveReusableEventCatalogUseCase = emptyObserveReusableEventCatalogUseCase()
) : ViewModel() {
    private val zoneId = ZoneId.systemDefault()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val mutableState = MutableStateFlow(buildInitialState())
    val state: StateFlow<CreateOutbreakUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<CreateOutbreakUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeReusableEventCatalogUseCase(ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR).collect { entries ->
                updateState {
                    it.copy(
                        availableOutbreakDescriptors = entries.map { entry ->
                            ReusableEventCatalogOptionUiModel(
                                id = entry.id,
                                name = entry.name,
                                kind = entry.kind,
                                usageCount = entry.usageCount,
                                lastUsedAtEpochMs = entry.lastUsedAtEpochMs,
                                updatedAtEpochMs = entry.updatedAtEpochMs
                            )
                        }
                    ).withFilteredCatalogs()
                }
            }
        }
        viewModelScope.launch {
            observeReusableEventCatalogUseCase(ReusableEventCatalogKind.BODY_LOCATION).collect { entries ->
                updateState {
                    it.copy(
                        availableBodyLocations = entries.map { entry ->
                            ReusableEventCatalogOptionUiModel(
                                id = entry.id,
                                name = entry.name,
                                kind = entry.kind,
                                usageCount = entry.usageCount,
                                lastUsedAtEpochMs = entry.lastUsedAtEpochMs,
                                updatedAtEpochMs = entry.updatedAtEpochMs
                            )
                        }
                    ).withFilteredCatalogs()
                }
            }
        }
    }

    fun onIntent(intent: CreateOutbreakUiIntent) {
        when (intent) {
            is CreateOutbreakUiIntent.UpdateOutbreakDescriptor -> updateState {
                it.copy(
                    outbreakDescriptor = intent.value,
                    selectedOutbreakDescriptorCatalogId = null,
                    selectedOutbreakDescriptorSummary = null,
                    outbreakDescriptorError = null,
                    formMessage = "",
                    canSave = canSave(intent.value, it.bodyLocation, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }

            is CreateOutbreakUiIntent.SelectOutbreakDescriptorCatalogOption -> updateState {
                it.copy(
                    outbreakDescriptor = intent.name,
                    selectedOutbreakDescriptorCatalogId = intent.id,
                    selectedOutbreakDescriptorSummary = "Saved outbreak descriptor selected: ${intent.name}",
                    outbreakDescriptorError = null,
                    formMessage = "",
                    canSave = canSave(intent.name, it.bodyLocation, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }

            CreateOutbreakUiIntent.UseTypedOutbreakDescriptor -> updateState {
                it.copy(
                    selectedOutbreakDescriptorCatalogId = null,
                    selectedOutbreakDescriptorSummary = "New outbreak descriptor will be saved locally.",
                    outbreakDescriptorError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }

            CreateOutbreakUiIntent.ClearOutbreakDescriptorCatalogSelection -> updateState {
                it.copy(
                    selectedOutbreakDescriptorCatalogId = null,
                    selectedOutbreakDescriptorSummary = null,
                    outbreakDescriptorError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }

            CreateOutbreakUiIntent.ClearRetiredOutbreakDescriptorCatalogSelection -> updateState {
                it.copy(
                    selectedOutbreakDescriptorCatalogId = null,
                    selectedOutbreakDescriptorSummary = null,
                    formMessage = "Saved outbreak descriptor was retired. Review the text value before saving.",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }

            CreateOutbreakUiIntent.EditSelectedOutbreakDescriptorCatalog -> {
                val selectedId = mutableState.value.selectedOutbreakDescriptorCatalogId ?: return
                effectChannel.trySend(
                    CreateOutbreakUiEffect.NavigateToEditEventCatalog(
                        ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR.storageValue,
                        selectedId
                    )
                )
            }

            is CreateOutbreakUiIntent.UpdateBodyLocation -> updateState {
                it.copy(
                    bodyLocation = intent.value,
                    selectedBodyLocationCatalogId = null,
                    selectedBodyLocationSummary = null,
                    bodyLocationError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, intent.value, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }

            is CreateOutbreakUiIntent.SelectBodyLocationCatalogOption -> updateState {
                it.copy(
                    bodyLocation = intent.name,
                    selectedBodyLocationCatalogId = intent.id,
                    selectedBodyLocationSummary = "Saved body location selected: ${intent.name}",
                    bodyLocationError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, intent.name, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }

            CreateOutbreakUiIntent.UseTypedBodyLocation -> updateState {
                it.copy(
                    selectedBodyLocationCatalogId = null,
                    selectedBodyLocationSummary = "New body location will be saved locally.",
                    bodyLocationError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }

            CreateOutbreakUiIntent.ClearBodyLocationCatalogSelection -> updateState {
                it.copy(
                    selectedBodyLocationCatalogId = null,
                    selectedBodyLocationSummary = null,
                    bodyLocationError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }

            CreateOutbreakUiIntent.ClearRetiredBodyLocationCatalogSelection -> updateState {
                it.copy(
                    selectedBodyLocationCatalogId = null,
                    selectedBodyLocationSummary = null,
                    formMessage = "Saved body location was retired. Review the text value before saving.",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }

            CreateOutbreakUiIntent.EditSelectedBodyLocationCatalog -> {
                val selectedId = mutableState.value.selectedBodyLocationCatalogId ?: return
                effectChannel.trySend(
                    CreateOutbreakUiEffect.NavigateToEditEventCatalog(
                        ReusableEventCatalogKind.BODY_LOCATION.storageValue,
                        selectedId
                    )
                )
            }

            is CreateOutbreakUiIntent.SelectIntensityLevel -> updateState {
                it.copy(
                    selectedIntensityLevel = intent.level,
                    intensityError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation, intent.level)
                )
            }

            is CreateOutbreakUiIntent.UpdateNotes -> updateState {
                it.copy(notes = intent.value)
            }

            is CreateOutbreakUiIntent.SelectOccurredOn -> updateState {
                it.withOccurredAt(composeOccurredAt(intent.value, it.selectedOccurredTime))
            }

            is CreateOutbreakUiIntent.SelectOccurredTime -> updateState {
                it.withOccurredAt(composeOccurredAt(it.selectedOccurredOn, intent.value))
            }

            is CreateOutbreakUiIntent.SelectTimePreset -> {
                val nextOccurredAt = createOutbreakDefaultsUseCase().occurredAtEpochMs - intent.preset.offsetMillis
                updateState {
                    it.withOccurredAt(nextOccurredAt, intent.preset)
                }
            }

            CreateOutbreakUiIntent.Save -> save()
        }
    }

    private fun save() {
        val currentState = mutableState.value
        if (currentState.isSaving) return

        updateState { it.copy(isSaving = true, formMessage = "") }
        viewModelScope.launch {
            when (
                val result = createOutbreakEventUseCase(
                    CreateOutbreakEventCommand(
                        outbreakDescriptor = currentState.outbreakDescriptor,
                        bodyLocation = currentState.bodyLocation,
                        occurredAtEpochMs = currentState.occurredAtEpochMs,
                        notes = currentState.notes,
                        intensityLevel = currentState.selectedIntensityLevel,
                        outbreakDescriptorCatalogId = currentState.selectedOutbreakDescriptorCatalogId,
                        bodyLocationCatalogId = currentState.selectedBodyLocationCatalogId
                    )
                )
            ) {
                is AppResult.Success -> {
                    updateState { it.copy(isSaving = false) }
                    effectChannel.trySend(CreateOutbreakUiEffect.NavigateToRecords)
                }

                is AppResult.Error -> {
                    val error = result.error
                    updateState {
                        it.copy(
                            isSaving = false,
                            outbreakDescriptorError = error.fieldError("outbreakDescriptor"),
                            bodyLocationError = error.fieldError("bodyLocation"),
                            intensityError = error.fieldError("intensityLevel"),
                            occurredAtError = error.fieldError("occurredAtEpochMs"),
                            formMessage = error.message,
                            canSave = canSave(it.outbreakDescriptor, it.bodyLocation, it.selectedIntensityLevel)
                        )
                    }
                }
            }
        }
    }

    private fun buildInitialState(): CreateOutbreakUiState {
        val defaults = createOutbreakDefaultsUseCase()
        return CreateOutbreakUiState(
            intensityOptions = IngestionQuantityOption.entries.map { option ->
                OutbreakIntensityOptionUiModel(level = option.level, label = option.label)
            },
            selectedIntensityLevel = defaults.intensityLevel,
            timeOptions = OutbreakTimePreset.entries.map { preset ->
                OutbreakTimeOptionUiModel(
                    key = preset.name,
                    label = preset.label,
                    offsetMillis = preset.offsetMillis
                )
            }
        ).withOccurredAt(defaults.occurredAtEpochMs, OutbreakTimePreset.NOW)
            .withFilteredCatalogs()
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

    private fun canSave(outbreakDescriptor: String, bodyLocation: String, intensityLevel: Int): Boolean {
        return outbreakDescriptor.isNotBlank() &&
            bodyLocation.isNotBlank() &&
            IngestionQuantityOption.fromLevel(intensityLevel) != null
    }

    private fun updateState(transform: (CreateOutbreakUiState) -> CreateOutbreakUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun DomainError.fieldError(key: String): String? {
        return when (this) {
            is DomainError.ValidationError -> fieldErrors[key]
            else -> null
        }
    }

    private fun CreateOutbreakUiState.withFilteredCatalogs(): CreateOutbreakUiState {
        return copy(
            filteredOutbreakDescriptors = availableOutbreakDescriptors.filterForQuery(outbreakDescriptor),
            showCreateOutbreakDescriptorOption = outbreakDescriptor.trim().isNotBlank() && !availableOutbreakDescriptors.hasExactName(outbreakDescriptor),
            filteredBodyLocations = availableBodyLocations.filterForQuery(bodyLocation),
            showCreateBodyLocationOption = bodyLocation.trim().isNotBlank() && !availableBodyLocations.hasExactName(bodyLocation)
        )
    }

    private fun CreateOutbreakUiState.withOccurredAt(
        epochMillis: Long,
        selectedPreset: OutbreakTimePreset? = null
    ): CreateOutbreakUiState {
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
}
