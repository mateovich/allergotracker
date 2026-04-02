package com.example.app.features.outbreak.ui
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
import com.example.app.features.outbreak.domain.CreateOutbreakDefaultsUseCase
import com.example.app.features.outbreak.domain.CreateOutbreakEventCommand
import com.example.app.features.outbreak.domain.CreateOutbreakEventUseCase
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

class CreateOutbreakViewModel(
    private val createOutbreakDefaultsUseCase: CreateOutbreakDefaultsUseCase,
    private val createOutbreakEventUseCase: CreateOutbreakEventUseCase,
    private val observeReusableEventCatalogUseCase: ObserveReusableEventCatalogUseCase = emptyObserveReusableEventCatalogUseCase()
) : ViewModel() {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
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
                    canSave = canSave(intent.value, it.bodyLocation)
                ).withFilteredCatalogs()
            }

            is CreateOutbreakUiIntent.SelectOutbreakDescriptorCatalogOption -> updateState {
                it.copy(
                    outbreakDescriptor = intent.name,
                    selectedOutbreakDescriptorCatalogId = intent.id,
                    selectedOutbreakDescriptorSummary = "Saved outbreak descriptor selected: ${intent.name}",
                    outbreakDescriptorError = null,
                    formMessage = "",
                    canSave = canSave(intent.name, it.bodyLocation)
                ).withFilteredCatalogs()
            }

            CreateOutbreakUiIntent.UseTypedOutbreakDescriptor -> updateState {
                it.copy(
                    selectedOutbreakDescriptorCatalogId = null,
                    selectedOutbreakDescriptorSummary = "New outbreak descriptor will be saved locally.",
                    outbreakDescriptorError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation)
                ).withFilteredCatalogs()
            }

            CreateOutbreakUiIntent.ClearOutbreakDescriptorCatalogSelection -> updateState {
                it.copy(
                    selectedOutbreakDescriptorCatalogId = null,
                    selectedOutbreakDescriptorSummary = null,
                    outbreakDescriptorError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation)
                ).withFilteredCatalogs()
            }

            CreateOutbreakUiIntent.ClearRetiredOutbreakDescriptorCatalogSelection -> updateState {
                it.copy(
                    selectedOutbreakDescriptorCatalogId = null,
                    selectedOutbreakDescriptorSummary = null,
                    formMessage = "Saved outbreak descriptor was retired. Review the text value before saving.",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation)
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
                    canSave = canSave(it.outbreakDescriptor, intent.value)
                ).withFilteredCatalogs()
            }

            is CreateOutbreakUiIntent.SelectBodyLocationCatalogOption -> updateState {
                it.copy(
                    bodyLocation = intent.name,
                    selectedBodyLocationCatalogId = intent.id,
                    selectedBodyLocationSummary = "Saved body location selected: ${intent.name}",
                    bodyLocationError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, intent.name)
                ).withFilteredCatalogs()
            }

            CreateOutbreakUiIntent.UseTypedBodyLocation -> updateState {
                it.copy(
                    selectedBodyLocationCatalogId = null,
                    selectedBodyLocationSummary = "New body location will be saved locally.",
                    bodyLocationError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation)
                ).withFilteredCatalogs()
            }

            CreateOutbreakUiIntent.ClearBodyLocationCatalogSelection -> updateState {
                it.copy(
                    selectedBodyLocationCatalogId = null,
                    selectedBodyLocationSummary = null,
                    bodyLocationError = null,
                    formMessage = "",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation)
                ).withFilteredCatalogs()
            }

            CreateOutbreakUiIntent.ClearRetiredBodyLocationCatalogSelection -> updateState {
                it.copy(
                    selectedBodyLocationCatalogId = null,
                    selectedBodyLocationSummary = null,
                    formMessage = "Saved body location was retired. Review the text value before saving.",
                    canSave = canSave(it.outbreakDescriptor, it.bodyLocation)
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

            is CreateOutbreakUiIntent.UpdateNotes -> updateState {
                it.copy(notes = intent.value)
            }

            is CreateOutbreakUiIntent.UpdateOccurredAtInput -> updateState {
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

            is CreateOutbreakUiIntent.SelectTimePreset -> {
                val nextOccurredAt = createOutbreakDefaultsUseCase().occurredAtEpochMs - intent.preset.offsetMillis
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

            CreateOutbreakUiIntent.Save -> save()
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
                val result = createOutbreakEventUseCase(
                    CreateOutbreakEventCommand(
                        outbreakDescriptor = currentState.outbreakDescriptor,
                        bodyLocation = currentState.bodyLocation,
                        occurredAtEpochMs = occurredAtEpochMs,
                        notes = currentState.notes,
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
                            occurredAtError = error.fieldError("occurredAtEpochMs"),
                            formMessage = error.message,
                            canSave = canSave(it.outbreakDescriptor, it.bodyLocation)
                        )
                    }
                }
            }
        }
    }

    private fun buildInitialState(): CreateOutbreakUiState {
        val defaults = createOutbreakDefaultsUseCase()
        return CreateOutbreakUiState(
            occurredAtEpochMs = defaults.occurredAtEpochMs,
            occurredAtInput = formatDateTimeInput(defaults.occurredAtEpochMs),
            occurredAtLabel = formatTime(defaults.occurredAtEpochMs),
            isOccurredAtManuallyEdited = false,
            timeOptions = OutbreakTimePreset.entries.map { preset ->
                OutbreakTimeOptionUiModel(
                    key = preset.name,
                    label = preset.label,
                    offsetMillis = preset.offsetMillis
                )
            }
        ).withFilteredCatalogs()
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

    private fun canSave(outbreakDescriptor: String, bodyLocation: String): Boolean {
        return outbreakDescriptor.isNotBlank() && bodyLocation.isNotBlank()
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
}
