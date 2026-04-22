package io.github.mateovich.allergotracker.features.records.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionQuantityOption
import io.github.mateovich.allergotracker.features.records.domain.ObserveRecordCountUseCase
import io.github.mateovich.allergotracker.features.records.domain.ObserveRecordsUseCase
import io.github.mateovich.allergotracker.features.records.domain.RecordEventType
import io.github.mateovich.allergotracker.features.records.domain.RecordsFilter
import io.github.mateovich.allergotracker.navigation.AppRoutes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

class RecordsViewModel(
    private val currentTimeProvider: CurrentTimeProvider,
    observeRecordCountUseCase: ObserveRecordCountUseCase,
    observeRecordsUseCase: ObserveRecordsUseCase,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val zoneId = ZoneId.systemDefault()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId)
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(zoneId)
    private val mutableState = MutableStateFlow(RecordsUiState())
    private val activeFilters = MutableStateFlow(savedStateHandle.restoreRecordsFilter())
    val state: StateFlow<RecordsUiState> = mutableState.asStateFlow()
    private val effectChannel = Channel<RecordsUiEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        observeRecordCountUseCase()
            .onEach { count ->
                mutableState.value = mutableState.value.copy(hasStoredRecords = count > 0)
                syncEmptyState()
            }
            .launchIn(viewModelScope)

        activeFilters
            .onEach { filters ->
                persistActiveFilters(savedStateHandle, filters)
                mutableState.value = mutableState.value.copy(
                    activeFilters = filters.toActiveUiModel(),
                    filterDraft = if (mutableState.value.isFilterDialogVisible) {
                        filters.toDraftUiState()
                    } else {
                        mutableState.value.filterDraft
                    }
                )
                syncEmptyState()
            }
            .launchIn(viewModelScope)

        observeRecordsUseCase(activeFilters.asStateFlow())
            .onEach { records ->
                mutableState.value = mutableState.value.copy(
                    hasRecords = records.isNotEmpty(),
                    records = records.map { record ->
                        RecordSummaryUiModel(
                            id = record.id,
                            typeLabel = record.typeLabel,
                            title = record.title,
                            detailLabel = record.detailLabel,
                            occurredAtLabel = formatOccurredAt(record.occurredAtEpochMs),
                            detailRoute = AppRoutes.recordDetail(record.eventType, record.sourceId)
                        )
                    }
                )
                syncEmptyState()
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

            RecordsUiIntent.OpenFilterDialog -> {
                mutableState.value = mutableState.value.copy(
                    isFilterDialogVisible = true,
                    filterDraft = activeFilters.value.toDraftUiState()
                )
            }

            RecordsUiIntent.DismissFilterDialog -> {
                mutableState.value = mutableState.value.copy(
                    isFilterDialogVisible = false,
                    filterDraft = activeFilters.value.toDraftUiState()
                )
            }

            RecordsUiIntent.ApplyFilters -> applyFilters()
            RecordsUiIntent.ClearFilters -> clearFilters()
            is RecordsUiIntent.UpdateFilterStartDate -> updateFilterDraft(startDateInput = intent.value)
            is RecordsUiIntent.UpdateFilterEndDate -> updateFilterDraft(endDateInput = intent.value)
            is RecordsUiIntent.ToggleFilterEventType -> {
                val selected = mutableState.value.filterDraft.selectedEventTypes.toMutableSet()
                if (!selected.add(intent.eventType)) {
                    selected.remove(intent.eventType)
                }
                updateFilterDraft(selectedEventTypes = selected)
            }

            is RecordsUiIntent.ToggleFilterIntensity -> {
                val selected = mutableState.value.filterDraft.selectedIntensityLevels.toMutableSet()
                if (!selected.add(intent.option)) {
                    selected.remove(intent.option)
                }
                updateFilterDraft(selectedIntensityLevels = selected)
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
                    ?.detailRoute
                    .orEmpty()
                if (route.isNotBlank()) {
                    effectChannel.trySend(RecordsUiEffect.NavigateToRecordDetail(route))
                }
            }
        }
    }

    private fun clearFilters() {
        activeFilters.value = RecordsFilter()
        mutableState.value = mutableState.value.copy(
            isFilterDialogVisible = false,
            filterDraft = RecordsFilter().toDraftUiState()
        )
        syncEmptyState()
    }

    private fun applyFilters() {
        val draft = mutableState.value.filterDraft
        val startDateResult = parseDateOrShowError(draft.startDateInput, "Start date must use YYYY-MM-DD.")
        if (!startDateResult.isValid) return
        val endDateResult = parseDateOrShowError(draft.endDateInput, "End date must use YYYY-MM-DD.")
        if (!endDateResult.isValid) return
        val startDate = startDateResult.value
        val endDate = endDateResult.value
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            mutableState.value = mutableState.value.copy(
                filterDraft = draft.copy(errorMessage = "End date must be on or after the start date.")
            )
            return
        }

        activeFilters.value = RecordsFilter(
            startDate = startDate,
            endDate = endDate,
            eventTypes = draft.selectedEventTypes,
            intensityLevels = draft.selectedIntensityLevels
        )
        mutableState.value = mutableState.value.copy(
            isFilterDialogVisible = false,
            filterDraft = draft.copy(errorMessage = null)
        )
        syncEmptyState()
    }

    private fun parseDateOrShowError(value: String, errorMessage: String): ParsedFilterDate {
        if (value.isBlank()) return ParsedFilterDate(isValid = true, value = null)
        return try {
            ParsedFilterDate(isValid = true, value = parseFilterDate(value))
        } catch (_: DateTimeParseException) {
            mutableState.value = mutableState.value.copy(
                filterDraft = mutableState.value.filterDraft.copy(errorMessage = errorMessage)
            )
            ParsedFilterDate(isValid = false, value = null)
        }
    }

    private fun updateFilterDraft(
        startDateInput: String = mutableState.value.filterDraft.startDateInput,
        endDateInput: String = mutableState.value.filterDraft.endDateInput,
        selectedEventTypes: Set<RecordEventType> = mutableState.value.filterDraft.selectedEventTypes,
        selectedIntensityLevels: Set<IngestionQuantityOption> = mutableState.value.filterDraft.selectedIntensityLevels
    ) {
        mutableState.value = mutableState.value.copy(
            filterDraft = mutableState.value.filterDraft.copy(
                startDateInput = startDateInput,
                endDateInput = endDateInput,
                selectedEventTypes = selectedEventTypes,
                selectedIntensityLevels = selectedIntensityLevels,
                errorMessage = null
            )
        )
    }

    private fun syncEmptyState() {
        val stateValue = mutableState.value
        val hasFilteredEmptyState = stateValue.hasStoredRecords &&
            !stateValue.hasRecords &&
            stateValue.activeFilters.hasActiveFilters
        mutableState.value = stateValue.copy(
            emptyStateTitle = if (hasFilteredEmptyState) {
                "No records match these filters"
            } else {
                "No records yet"
            },
            emptyStateMessage = if (hasFilteredEmptyState) {
                "Records exist, but the current filters hide every result. Clear or adjust filters to continue."
            } else {
                "Use + to create your first record."
            },
            emptyStateActionLabel = if (hasFilteredEmptyState) {
                stateValue.activeFilters.resetLabel
            } else {
                null
            }
        )
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

private const val FILTER_START_DATE_KEY = "records.filter.start_date"
private const val FILTER_END_DATE_KEY = "records.filter.end_date"
private const val FILTER_EVENT_TYPES_KEY = "records.filter.event_types"
private const val FILTER_INTENSITY_LEVELS_KEY = "records.filter.intensity_levels"

private data class ParsedFilterDate(
    val isValid: Boolean,
    val value: LocalDate?
)

private fun persistActiveFilters(savedStateHandle: SavedStateHandle, filters: RecordsFilter) {
    savedStateHandle[FILTER_START_DATE_KEY] = filters.startDate?.toString()
    savedStateHandle[FILTER_END_DATE_KEY] = filters.endDate?.toString()
    savedStateHandle[FILTER_EVENT_TYPES_KEY] = ArrayList(filters.eventTypes.map { it.name })
    savedStateHandle[FILTER_INTENSITY_LEVELS_KEY] = filters.intensityLevels.map { it.level }.toIntArray()
}

private fun SavedStateHandle.restoreRecordsFilter(): RecordsFilter {
    val startDate = get<String>(FILTER_START_DATE_KEY)?.let(LocalDate::parse)
    val endDate = get<String>(FILTER_END_DATE_KEY)?.let(LocalDate::parse)
    val eventTypes = get<ArrayList<String>>(FILTER_EVENT_TYPES_KEY)
        .orEmpty()
        .mapNotNull { name -> RecordEventType.entries.firstOrNull { it.name == name } }
        .toSet()
    val intensityLevels = get<IntArray>(FILTER_INTENSITY_LEVELS_KEY)
        ?.toList()
        ?.mapNotNull { level -> IngestionQuantityOption.fromLevel(level) }
        ?.toSet()
        .orEmpty()

    return RecordsFilter(
        startDate = startDate,
        endDate = endDate,
        eventTypes = eventTypes,
        intensityLevels = intensityLevels
    )
}
