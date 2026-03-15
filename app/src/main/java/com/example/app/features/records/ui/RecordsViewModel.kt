package com.example.app.features.records.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.records.domain.ObserveRecordCountUseCase
import com.example.app.features.records.domain.ObserveRecordsUseCase
import com.example.app.features.records.domain.RecordEventType
import com.example.app.navigation.AppRoutes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.FormatStyle
import java.time.format.DateTimeFormatter

class RecordsViewModel(
    private val currentTimeProvider: CurrentTimeProvider,
    observeRecordCountUseCase: ObserveRecordCountUseCase,
    observeRecordsUseCase: ObserveRecordsUseCase
) : ViewModel() {
    private val zoneId = ZoneId.systemDefault()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId)
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(zoneId)
    private val mutableState = MutableStateFlow(RecordsUiState())
    val state: StateFlow<RecordsUiState> = mutableState.asStateFlow()
    private val effectChannel = Channel<RecordsUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        combine(observeRecordCountUseCase(), observeRecordsUseCase()) { count, records ->
            count to records
        }
            .onEach { (count, records) ->
                mutableState.value = mutableState.value.copy(
                    hasRecords = count > 0,
                    records = records.map { record ->
                        RecordSummaryUiModel(
                            id = record.id,
                            typeLabel = record.typeLabel,
                            title = record.title,
                            detailLabel = record.detailLabel,
                            occurredAtLabel = formatOccurredAt(record.occurredAtEpochMs),
                            editRoute = when (record.eventType) {
                                RecordEventType.INGESTION -> AppRoutes.editIngestion(record.sourceId)
                                RecordEventType.EXPOSURE -> AppRoutes.editExposure(record.sourceId)
                                RecordEventType.OUTBREAK -> AppRoutes.editOutbreak(record.sourceId)
                            }
                        )
                    }
                )
            }
            .launchIn(viewModelScope)
    }

    fun onIntent(intent: RecordsUiIntent) {
        when (intent) {
            RecordsUiIntent.Load -> Unit
            RecordsUiIntent.OpenAddEntryPoint -> {
                mutableState.value = mutableState.value.copy(isQuickPickerVisible = true)
            }

            RecordsUiIntent.DismissAddEntryPoint -> {
                mutableState.value = mutableState.value.copy(isQuickPickerVisible = false)
            }

            is RecordsUiIntent.SelectEventType -> {
                mutableState.value = mutableState.value.copy(isQuickPickerVisible = false)
                effectChannel.trySend(
                    RecordsUiEffect.NavigateToCreateForm(intent.eventType.route)
                )
            }

            is RecordsUiIntent.OpenRecord -> {
                val route = mutableState.value.records
                    .firstOrNull { it.id == intent.recordId }
                    ?.editRoute
                    .orEmpty()
                if (route.isNotBlank()) {
                    effectChannel.trySend(RecordsUiEffect.NavigateToEditForm(route))
                }
            }
        }
    }

    private fun formatOccurredAt(occurredAtEpochMs: Long): String {
        val occurredAt = Instant.ofEpochMilli(occurredAtEpochMs).atZone(zoneId)
        val today = Instant.ofEpochMilli(currentTimeProvider.nowEpochMillis()).atZone(zoneId).toLocalDate()
        val timeLabel = timeFormatter.format(occurredAt)

        return if (occurredAt.toLocalDate().isEqual(today)) {
            timeLabel
        } else {
            "${dateFormatter.format(occurredAt)} $timeLabel"
        }
    }
}
