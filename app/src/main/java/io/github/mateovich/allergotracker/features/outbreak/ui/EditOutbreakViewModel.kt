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
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionQuantityOption
import io.github.mateovich.allergotracker.features.outbreak.domain.DeleteOutbreakEventUseCase
import io.github.mateovich.allergotracker.features.outbreak.domain.GetOutbreakEventUseCase
import io.github.mateovich.allergotracker.features.outbreak.domain.UpdateOutbreakEventCommand
import io.github.mateovich.allergotracker.features.outbreak.domain.UpdateOutbreakEventUseCase
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

class EditOutbreakViewModel(
    private val recordId: Long,
    private val getOutbreakEventUseCase: GetOutbreakEventUseCase,
    private val deleteOutbreakEventUseCase: DeleteOutbreakEventUseCase,
    private val updateOutbreakEventUseCase: UpdateOutbreakEventUseCase,
    private val currentTimeProvider: CurrentTimeProvider,
    private val observeReusableEventCatalogUseCase: ObserveReusableEventCatalogUseCase = emptyObserveReusableEventCatalogUseCase()
) : ViewModel() {
    private val zoneId = ZoneId.systemDefault()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val mutableState = MutableStateFlow(
        EditOutbreakUiState(
            intensityOptions = IngestionQuantityOption.entries.map { OutbreakIntensityOptionUiModel(it.level, it.label) },
            timeOptions = OutbreakTimePreset.entries.map { OutbreakTimeOptionUiModel(it.name, it.label, it.offsetMillis) }
        )
    )
    val state: StateFlow<EditOutbreakUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<EditOutbreakUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeReusableEventCatalogUseCase(ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR).collect { entries ->
                updateState {
                    val availableOutbreakDescriptors = entries.map { entry ->
                        ReusableEventCatalogOptionUiModel(
                            id = entry.id,
                            name = entry.name,
                            kind = entry.kind,
                            usageCount = entry.usageCount,
                            lastUsedAtEpochMs = entry.lastUsedAtEpochMs,
                            updatedAtEpochMs = entry.updatedAtEpochMs
                        )
                    }
                    val selectedOutbreakDescriptorSummary = it.selectedOutbreakDescriptorCatalogId
                        ?.takeIf { selectedId -> availableOutbreakDescriptors.any { option -> option.id == selectedId } }
                        ?.let { _ -> "Saved outbreak descriptor selected: ${it.outbreakDescriptor}" }
                    it.copy(
                        availableOutbreakDescriptors = availableOutbreakDescriptors,
                        selectedOutbreakDescriptorSummary = selectedOutbreakDescriptorSummary
                    ).withFilteredCatalogs()
                }
            }
        }
        viewModelScope.launch {
            observeReusableEventCatalogUseCase(ReusableEventCatalogKind.BODY_LOCATION).collect { entries ->
                updateState {
                    val availableBodyLocations = entries.map { entry ->
                        ReusableEventCatalogOptionUiModel(
                            id = entry.id,
                            name = entry.name,
                            kind = entry.kind,
                            usageCount = entry.usageCount,
                            lastUsedAtEpochMs = entry.lastUsedAtEpochMs,
                            updatedAtEpochMs = entry.updatedAtEpochMs
                        )
                    }
                    val selectedBodyLocationSummary = it.selectedBodyLocationCatalogId
                        ?.takeIf { selectedId -> availableBodyLocations.any { option -> option.id == selectedId } }
                        ?.let { _ -> "Saved body location selected: ${it.bodyLocation}" }
                    it.copy(
                        availableBodyLocations = availableBodyLocations,
                        selectedBodyLocationSummary = selectedBodyLocationSummary
                    ).withFilteredCatalogs()
                }
            }
        }
    }

    fun onIntent(intent: EditOutbreakUiIntent) {
        when (intent) {
            EditOutbreakUiIntent.Load -> loadIfNeeded()
            is EditOutbreakUiIntent.UpdateOutbreakDescriptor -> updateState {
                it.copy(
                    outbreakDescriptor = intent.value,
                    selectedOutbreakDescriptorCatalogId = null,
                    selectedOutbreakDescriptorSummary = null,
                    outbreakDescriptorError = null,
                    formMessage = "",
                    canSave = canSave(intent.value, it.bodyLocation, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }
            is EditOutbreakUiIntent.SelectOutbreakDescriptorCatalogOption -> updateState {
                it.copy(
                    outbreakDescriptor = intent.name,
                    selectedOutbreakDescriptorCatalogId = intent.id,
                    selectedOutbreakDescriptorSummary = "Saved outbreak descriptor selected: ${intent.name}",
                    outbreakDescriptorError = null,
                    formMessage = "",
                    canSave = canSave(intent.name, it.bodyLocation, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }
            EditOutbreakUiIntent.UseTypedOutbreakDescriptor -> updateState {
                it.copy(
                    selectedOutbreakDescriptorCatalogId = null,
                    selectedOutbreakDescriptorSummary = "New outbreak descriptor will be saved locally.",
                    outbreakDescriptorError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }
            EditOutbreakUiIntent.ClearOutbreakDescriptorCatalogSelection -> updateState {
                it.copy(
                    selectedOutbreakDescriptorCatalogId = null,
                    selectedOutbreakDescriptorSummary = null,
                    outbreakDescriptorError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }
            EditOutbreakUiIntent.ClearRetiredOutbreakDescriptorCatalogSelection -> updateState {
                it.copy(
                    selectedOutbreakDescriptorCatalogId = null,
                    selectedOutbreakDescriptorSummary = null,
                    formMessage = "Saved outbreak descriptor was retired. Review the text value before saving.",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }
            EditOutbreakUiIntent.EditSelectedOutbreakDescriptorCatalog -> {
                val selectedId = mutableState.value.selectedOutbreakDescriptorCatalogId ?: return
                effectChannel.trySend(
                    EditOutbreakUiEffect.NavigateToEditEventCatalog(
                        ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR.storageValue,
                        selectedId
                    )
                )
            }
            is EditOutbreakUiIntent.UpdateBodyLocation -> updateState {
                it.copy(
                    bodyLocation = intent.value,
                    selectedBodyLocationCatalogId = null,
                    selectedBodyLocationSummary = null,
                    bodyLocationError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, intent.value, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }
            is EditOutbreakUiIntent.SelectBodyLocationCatalogOption -> updateState {
                it.copy(
                    bodyLocation = intent.name,
                    selectedBodyLocationCatalogId = intent.id,
                    selectedBodyLocationSummary = "Saved body location selected: ${intent.name}",
                    bodyLocationError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, intent.name, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }
            EditOutbreakUiIntent.UseTypedBodyLocation -> updateState {
                it.copy(
                    selectedBodyLocationCatalogId = null,
                    selectedBodyLocationSummary = "New body location will be saved locally.",
                    bodyLocationError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }
            EditOutbreakUiIntent.ClearBodyLocationCatalogSelection -> updateState {
                it.copy(
                    selectedBodyLocationCatalogId = null,
                    selectedBodyLocationSummary = null,
                    bodyLocationError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }
            EditOutbreakUiIntent.ClearRetiredBodyLocationCatalogSelection -> updateState {
                it.copy(
                    selectedBodyLocationCatalogId = null,
                    selectedBodyLocationSummary = null,
                    formMessage = "Saved body location was retired. Review the text value before saving.",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation, it.selectedIntensityLevel)
                ).withFilteredCatalogs()
            }
            EditOutbreakUiIntent.EditSelectedBodyLocationCatalog -> {
                val selectedId = mutableState.value.selectedBodyLocationCatalogId ?: return
                effectChannel.trySend(
                    EditOutbreakUiEffect.NavigateToEditEventCatalog(
                        ReusableEventCatalogKind.BODY_LOCATION.storageValue,
                        selectedId
                    )
                )
            }
            is EditOutbreakUiIntent.SelectIntensityLevel -> updateState {
                it.copy(
                    selectedIntensityLevel = intent.level,
                    intensityError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation, intent.level)
                )
            }
            is EditOutbreakUiIntent.UpdateNotes -> updateState { it.copy(notes = intent.value) }
            is EditOutbreakUiIntent.SelectOccurredOn -> updateState {
                it.withOccurredAt(composeOccurredAt(intent.value, it.selectedOccurredTime))
            }
            is EditOutbreakUiIntent.SelectOccurredTime -> updateState {
                it.withOccurredAt(composeOccurredAt(it.selectedOccurredOn, intent.value))
            }
            is EditOutbreakUiIntent.SelectTimePreset -> {
                val nextOccurredAt = currentTimeProvider.nowEpochMillis() - intent.preset.offsetMillis
                updateState {
                    it.withOccurredAt(nextOccurredAt, intent.preset)
                }
            }
            EditOutbreakUiIntent.ShowDeleteConfirmation -> showDeleteConfirmation()
            EditOutbreakUiIntent.DismissDeleteConfirmation -> updateState { it.copy(isDeleteConfirmationVisible = false) }
            EditOutbreakUiIntent.ConfirmDelete -> delete()
            EditOutbreakUiIntent.Save -> save()
        }
    }

    private fun loadIfNeeded() {
        val currentState = mutableState.value
        if (currentState.outbreakDescriptor.isNotBlank()) return
        updateState { it.copy(isLoading = true, formMessage = "") }
        viewModelScope.launch {
            when (val result = getOutbreakEventUseCase(recordId)) {
                is AppResult.Success -> {
                    val event = result.value
                    updateState {
                        it.copy(
                            outbreakDescriptor = event.outbreakDescriptor,
                            bodyLocation = event.bodyLocation,
                            deleteRecordLabel = deleteRecordLabel(event.outbreakDescriptor, event.bodyLocation),
                            notes = event.notes.orEmpty(),
                            selectedIntensityLevel = event.intensityLevel,
                            selectedOutbreakDescriptorCatalogId = event.outbreakDescriptorCatalogId,
                            selectedOutbreakDescriptorSummary = event.outbreakDescriptorCatalogId
                                ?.takeIf { selectedId -> it.availableOutbreakDescriptors.any { option -> option.id == selectedId } }
                                ?.let { _ ->
                                    "Saved outbreak descriptor selected: ${event.outbreakDescriptor}"
                                },
                            selectedBodyLocationCatalogId = event.bodyLocationCatalogId,
                            selectedBodyLocationSummary = event.bodyLocationCatalogId
                                ?.takeIf { selectedId -> it.availableBodyLocations.any { option -> option.id == selectedId } }
                                ?.let { _ ->
                                    "Saved body location selected: ${event.bodyLocation}"
                                },
                            isLoading = false,
                            canSave = canSave(event.outbreakDescriptor, event.bodyLocation, event.intensityLevel),
                            canDelete = true
                        ).withOccurredAt(event.occurredAtEpochMs).withFilteredCatalogs()
                    }
                }
                is AppResult.Error -> updateState { it.copy(isLoading = false, formMessage = result.error.message) }
            }
        }
    }

    private fun save() {
        val currentState = mutableState.value
        if (!currentState.canSave || currentState.isSaving || currentState.isDeleting || currentState.isLoading) return
        updateState { it.copy(isSaving = true, formMessage = "") }
        viewModelScope.launch {
            when (
                val result = updateOutbreakEventUseCase(
                    UpdateOutbreakEventCommand(
                        id = recordId,
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
                    effectChannel.trySend(EditOutbreakUiEffect.NavigateToRecords)
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
            when (val result = deleteOutbreakEventUseCase(recordId)) {
                is AppResult.Success -> {
                    updateState { it.copy(isDeleting = false) }
                    effectChannel.trySend(EditOutbreakUiEffect.NavigateToRecords)
                }
                is AppResult.Error -> {
                    updateState {
                        it.copy(
                            isDeleting = false,
                            formMessage = result.error.message,
                            canSave = canSave(it.outbreakDescriptor, it.bodyLocation, it.selectedIntensityLevel)
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

    private fun deleteRecordLabel(outbreakDescriptor: String, bodyLocation: String): String {
        if (outbreakDescriptor.isBlank() && bodyLocation.isBlank()) return ""
        if (bodyLocation.isBlank()) return outbreakDescriptor
        return "$outbreakDescriptor at $bodyLocation"
    }

    private fun canSave(outbreakDescriptor: String, bodyLocation: String, intensityLevel: Int): Boolean {
        return outbreakDescriptor.isNotBlank() &&
            bodyLocation.isNotBlank() &&
            IngestionQuantityOption.fromLevel(intensityLevel) != null
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

    private fun updateState(transform: (EditOutbreakUiState) -> EditOutbreakUiState) {
        mutableState.value = transform(mutableState.value)
    }

    private fun DomainError.fieldError(key: String): String? {
        return when (this) {
            is DomainError.ValidationError -> fieldErrors[key]
            else -> null
        }
    }

    private fun EditOutbreakUiState.withFilteredCatalogs(): EditOutbreakUiState {
        return copy(
            filteredOutbreakDescriptors = availableOutbreakDescriptors.filterForQuery(outbreakDescriptor),
            showCreateOutbreakDescriptorOption = outbreakDescriptor.trim().isNotBlank() && !availableOutbreakDescriptors.hasExactName(outbreakDescriptor),
            filteredBodyLocations = availableBodyLocations.filterForQuery(bodyLocation),
            showCreateBodyLocationOption = bodyLocation.trim().isNotBlank() && !availableBodyLocations.hasExactName(bodyLocation)
        )
    }

    private fun EditOutbreakUiState.withOccurredAt(
        epochMillis: Long,
        selectedPreset: OutbreakTimePreset? = null
    ): EditOutbreakUiState {
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
