package io.github.mateovich.allergotracker.features.records.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateovich.allergotracker.core.common.duration.formatDurationMinutes
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.records.domain.ObserveRecordDetailUseCase
import io.github.mateovich.allergotracker.features.records.domain.RecordDetail
import io.github.mateovich.allergotracker.features.records.domain.RecordEventType
import io.github.mateovich.allergotracker.navigation.AppRoutes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

class RecordDetailViewModel(
    private val recordId: Long,
    private val eventType: RecordEventType,
    private val observeRecordDetailUseCase: ObserveRecordDetailUseCase
) : ViewModel() {
    private val zoneId = ZoneId.systemDefault()
    private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(zoneId)
    private val mutableState = MutableStateFlow(RecordDetailUiState())
    val state: StateFlow<RecordDetailUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<RecordDetailUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var hasStarted = false

    fun onIntent(intent: RecordDetailUiIntent) {
        when (intent) {
            RecordDetailUiIntent.Load -> loadIfNeeded()
            RecordDetailUiIntent.NavigateBack -> effectChannel.trySend(RecordDetailUiEffect.NavigateToRecords)
            RecordDetailUiIntent.OpenEdit -> navigateToEditRoute()
            RecordDetailUiIntent.OpenDelete -> navigateToDeleteRoute()
            RecordDetailUiIntent.AcknowledgeUnavailable -> effectChannel.trySend(RecordDetailUiEffect.NavigateToRecords)
        }
    }

    private fun loadIfNeeded() {
        if (hasStarted) return
        hasStarted = true
        observeRecordDetailUseCase(recordId, eventType)
            .onEach { result ->
                mutableState.value = when (result) {
                    is AppResult.Success -> result.value.toUiState()
                    is AppResult.Error -> RecordDetailUiState(
                        isLoading = false,
                        unavailableMessage = result.error.message.ifBlank { "This record is no longer available." },
                        isUnavailableDialogVisible = true
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun navigateToEditRoute() {
        val route = mutableState.value.editRoute
        if (route.isNotBlank()) {
            effectChannel.trySend(RecordDetailUiEffect.NavigateToRoute(route))
        }
    }

    private fun navigateToDeleteRoute() {
        val route = when (eventType) {
            RecordEventType.INGESTION -> AppRoutes.editIngestion(recordId, openDelete = true)
            RecordEventType.EXPOSURE -> AppRoutes.editExposure(recordId, openDelete = true)
            RecordEventType.OUTBREAK -> AppRoutes.editOutbreak(recordId, openDelete = true)
        }
        effectChannel.trySend(RecordDetailUiEffect.NavigateToRoute(route))
    }

    private fun RecordDetail.toUiState(): RecordDetailUiState {
        return RecordDetailUiState(
            typeLabel = typeLabel,
            title = title,
            occurredAtLabel = dateTimeFormatter.format(Instant.ofEpochMilli(occurredAtEpochMs)),
            fields = toFields(),
            editRoute = when (eventType) {
                RecordEventType.INGESTION -> AppRoutes.editIngestion(recordId)
                RecordEventType.EXPOSURE -> AppRoutes.editExposure(recordId)
                RecordEventType.OUTBREAK -> AppRoutes.editOutbreak(recordId)
            },
            isLoading = false
        )
    }

    private fun RecordDetail.toFields(): List<RecordDetailFieldUiModel> {
        return when (this) {
            is RecordDetail.Ingestion -> listOf(
                RecordDetailFieldUiModel(label = "Item", value = itemName),
                RecordDetailFieldUiModel(label = "Quantity", value = quantityLabel),
                RecordDetailFieldUiModel(
                    label = "Reusable source",
                    value = reusableEntityKind?.label ?: "Manual entry"
                ),
                RecordDetailFieldUiModel(label = "Notes", value = notes.orEmpty().ifBlank { "None" })
            )

            is RecordDetail.Exposure -> listOf(
                RecordDetailFieldUiModel(label = "Exposure", value = exposureType),
                RecordDetailFieldUiModel(label = "Place", value = place.orEmpty().ifBlank { "None" }),
                RecordDetailFieldUiModel(label = "Duration", value = formatDurationMinutes(durationMinutes)),
                RecordDetailFieldUiModel(label = "Intensity", value = intensityLabel),
                RecordDetailFieldUiModel(label = "Notes", value = notes.orEmpty().ifBlank { "None" })
            )

            is RecordDetail.Outbreak -> listOf(
                RecordDetailFieldUiModel(label = "Descriptor", value = outbreakDescriptor),
                RecordDetailFieldUiModel(label = "Body location", value = bodyLocation),
                RecordDetailFieldUiModel(label = "Duration", value = formatDurationMinutes(durationMinutes)),
                RecordDetailFieldUiModel(label = "Intensity", value = intensityLabel),
                RecordDetailFieldUiModel(label = "Notes", value = notes.orEmpty().ifBlank { "None" })
            )
        }
    }
}
