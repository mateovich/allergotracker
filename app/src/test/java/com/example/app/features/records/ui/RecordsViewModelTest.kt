package com.example.app.features.records.ui

import androidx.lifecycle.SavedStateHandle
import com.example.app.MainDispatcherRule
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.IngestionQuantityOption
import com.example.app.features.records.domain.ObserveRecordCountUseCase
import com.example.app.features.records.domain.ObserveRecordsUseCase
import com.example.app.features.records.domain.RecordEventType
import com.example.app.features.records.domain.RecordSummary
import com.example.app.features.records.domain.RecordsFilter
import com.example.app.features.records.domain.RecordsRepository
import com.example.app.navigation.AppRoutes
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecordsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedNow = 1_700_000_000_000L

    private val emptyRepository = object : RecordsRepository {
        override fun observeRecordCount() = flowOf(0)

        override fun observeRecords(filters: Flow<RecordsFilter>) = flowOf(emptyList<RecordSummary>())
    }

    private val observeRecordCountUseCase = ObserveRecordCountUseCase(emptyRepository)
    private val observeRecordsUseCase = ObserveRecordsUseCase(emptyRepository)

    @Test
    fun opening_add_entry_point_expands_picker() {
        val viewModel = RecordsViewModel(currentTimeProvider(), observeRecordCountUseCase, observeRecordsUseCase)

        viewModel.onIntent(RecordsUiIntent.OpenAddEntryPoint)

        assertTrue(viewModel.state.value.isQuickPickerVisible)
    }

    @Test
    fun dismissing_add_entry_point_collapses_picker() {
        val viewModel = RecordsViewModel(currentTimeProvider(), observeRecordCountUseCase, observeRecordsUseCase)

        viewModel.onIntent(RecordsUiIntent.OpenAddEntryPoint)
        viewModel.onIntent(RecordsUiIntent.DismissAddEntryPoint)

        assertFalse(viewModel.state.value.isQuickPickerVisible)
    }

    @Test
    fun selecting_event_type_emits_navigation_effect_for_matching_form() = runBlocking {
        val viewModel = RecordsViewModel(currentTimeProvider(), observeRecordCountUseCase, observeRecordsUseCase)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(RecordsUiIntent.SelectEventType(EventType.EXPOSURE))

        assertEquals(
            RecordsUiEffect.NavigateToCreateForm(AppRoutes.CREATE_EXPOSURE),
            effect.await()
        )
        assertFalse(viewModel.state.value.isQuickPickerVisible)
    }

    @Test
    fun selecting_record_emits_navigation_effect_for_matching_edit_form() = runBlocking {
        val repository = repositoryWithRecords(
            records = listOf(
                record(
                    id = "ingestion-7",
                    typeLabel = "Ingestion",
                    title = "Tea",
                    detailLabel = "2 Light snack",
                    sourceId = 7L,
                    eventType = RecordEventType.INGESTION,
                    ingestionQuantityLevel = 2
                )
            )
        )
        val viewModel = RecordsViewModel(
            currentTimeProvider(),
            ObserveRecordCountUseCase(repository),
            ObserveRecordsUseCase(repository)
        )
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(RecordsUiIntent.OpenRecord("ingestion-7"))

        assertEquals(
            RecordsUiEffect.NavigateToEditForm(AppRoutes.editIngestion(7L)),
            effect.await()
        )
    }

    @Test
    fun init_loads_empty_state_from_repository() {
        val viewModel = RecordsViewModel(currentTimeProvider(), observeRecordCountUseCase, observeRecordsUseCase)

        assertFalse(viewModel.state.value.hasRecords)
    }

    @Test
    fun records_from_previous_days_include_date_in_summary_label() {
        val zoneId = ZoneId.systemDefault()
        val previousDayEpochMs = Instant.ofEpochMilli(fixedNow)
            .atZone(zoneId)
            .minusDays(1)
            .withHour(8)
            .withMinute(15)
            .withSecond(0)
            .withNano(0)
            .toInstant()
            .toEpochMilli()
        val repository = repositoryWithRecords(
            records = listOf(
                record(
                    id = "ingestion-7",
                    typeLabel = "Ingestion",
                    title = "Tea",
                    detailLabel = "2 Light snack",
                    occurredAtEpochMs = previousDayEpochMs,
                    sourceId = 7L,
                    eventType = RecordEventType.INGESTION,
                    ingestionQuantityLevel = 2
                )
            )
        )
        val viewModel = RecordsViewModel(
            currentTimeProvider(),
            ObserveRecordCountUseCase(repository),
            ObserveRecordsUseCase(repository)
        )
        val expectedDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withZone(zoneId)
            .format(Instant.ofEpochMilli(previousDayEpochMs))
        val expectedTime = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(zoneId)
            .format(Instant.ofEpochMilli(previousDayEpochMs))

        assertEquals(1, viewModel.state.value.records.size)
        assertEquals(
            "$expectedDate $expectedTime",
            viewModel.state.value.records.single().occurredAtLabel
        )
    }

    @Test
    fun records_from_today_show_compact_time_only_label() {
        val todayEpochMs = Instant.ofEpochMilli(fixedNow)
            .atZone(ZoneId.systemDefault())
            .withHour(9)
            .withMinute(45)
            .withSecond(0)
            .withNano(0)
            .toInstant()
            .toEpochMilli()
        val repository = repositoryWithRecords(
            records = listOf(
                record(
                    id = "exposure-2",
                    typeLabel = "Exposure",
                    title = "Pollen",
                    detailLabel = "Exposure recorded",
                    occurredAtEpochMs = todayEpochMs,
                    sourceId = 2L,
                    eventType = RecordEventType.EXPOSURE
                )
            )
        )
        val viewModel = RecordsViewModel(
            currentTimeProvider(),
            ObserveRecordCountUseCase(repository),
            ObserveRecordsUseCase(repository)
        )
        val expectedTime = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(todayEpochMs))

        assertEquals(expectedTime, viewModel.state.value.records.single().occurredAtLabel)
    }

    @Test
    fun empty_state_keeps_creation_guidance_visible() {
        val viewModel = RecordsViewModel(currentTimeProvider(), observeRecordCountUseCase, observeRecordsUseCase)

        assertEquals("No records yet", viewModel.state.value.emptyStateTitle)
        assertEquals("Use + to create your first record.", viewModel.state.value.emptyStateMessage)
    }

    @Test
    fun has_records_tracks_materialized_list_when_count_flow_is_stale() {
        val recordsFlow = MutableStateFlow(emptyList<RecordSummary>())
        val repository = object : RecordsRepository {
            override fun observeRecordCount() = flowOf(1)

            override fun observeRecords(filters: Flow<RecordsFilter>) = recordsFlow
        }
        val viewModel = RecordsViewModel(
            currentTimeProvider(),
            ObserveRecordCountUseCase(repository),
            ObserveRecordsUseCase(repository)
        )

        assertFalse(viewModel.state.value.hasRecords)
    }

    @Test
    fun applying_filters_updates_active_labels_and_filtered_empty_state() {
        val repository = repositoryWithRecords(
            records = listOf(
                record(
                    id = "exposure-2",
                    typeLabel = "Exposure",
                    title = "Pollen",
                    detailLabel = "Exposure recorded",
                    sourceId = 2L,
                    eventType = RecordEventType.EXPOSURE
                )
            )
        )
        val viewModel = RecordsViewModel(
            currentTimeProvider(),
            ObserveRecordCountUseCase(repository),
            ObserveRecordsUseCase(repository)
        )

        viewModel.onIntent(RecordsUiIntent.OpenFilterDialog)
        viewModel.onIntent(RecordsUiIntent.ToggleFilterEventType(RecordEventType.INGESTION))
        viewModel.onIntent(RecordsUiIntent.ToggleFilterIntensity(IngestionQuantityOption.STANDARD))
        viewModel.onIntent(RecordsUiIntent.ApplyFilters)

        assertTrue(viewModel.state.value.activeFilters.hasActiveFilters)
        assertEquals("No records match these filters", viewModel.state.value.emptyStateTitle)
        assertEquals("Clear filters", viewModel.state.value.emptyStateActionLabel)
        assertEquals(0, viewModel.state.value.records.size)
    }

    @Test
    fun applying_date_range_filters_limits_records_in_view_model_state() {
        val zoneId = ZoneId.systemDefault()
        val includedDate = LocalDate.of(2026, 3, 20)
        val repository = repositoryWithRecords(
            records = listOf(
                record(
                    id = "ingestion-7",
                    typeLabel = "Ingestion",
                    title = "Tea",
                    detailLabel = "2 Light snack",
                    occurredAtEpochMs = includedDate.atStartOfDay(zoneId).plusHours(9).toInstant().toEpochMilli(),
                    sourceId = 7L,
                    eventType = RecordEventType.INGESTION,
                    ingestionQuantityLevel = 2
                ),
                record(
                    id = "exposure-5",
                    typeLabel = "Exposure",
                    title = "Pollen",
                    detailLabel = "Exposure recorded",
                    occurredAtEpochMs = includedDate.minusDays(1).atStartOfDay(zoneId).plusHours(8).toInstant().toEpochMilli(),
                    sourceId = 5L,
                    eventType = RecordEventType.EXPOSURE
                )
            )
        )
        val viewModel = RecordsViewModel(
            currentTimeProvider(),
            ObserveRecordCountUseCase(repository),
            ObserveRecordsUseCase(repository)
        )

        viewModel.onIntent(RecordsUiIntent.OpenFilterDialog)
        viewModel.onIntent(RecordsUiIntent.UpdateFilterStartDate(includedDate.toString()))
        viewModel.onIntent(RecordsUiIntent.UpdateFilterEndDate(includedDate.toString()))
        viewModel.onIntent(RecordsUiIntent.ApplyFilters)

        assertEquals(listOf("ingestion-7"), viewModel.state.value.records.map { it.id })
        assertEquals(listOf("$includedDate to $includedDate"), viewModel.state.value.activeFilters.summaryLabels)
    }

    @Test
    fun clearing_filters_restores_full_list_and_removes_active_indicators() {
        val repository = repositoryWithRecords(
            records = listOf(
                record(
                    id = "ingestion-7",
                    typeLabel = "Ingestion",
                    title = "Tea",
                    detailLabel = "2 Light snack",
                    sourceId = 7L,
                    eventType = RecordEventType.INGESTION,
                    ingestionQuantityLevel = 2
                )
            )
        )
        val viewModel = RecordsViewModel(
            currentTimeProvider(),
            ObserveRecordCountUseCase(repository),
            ObserveRecordsUseCase(repository)
        )

        viewModel.onIntent(RecordsUiIntent.OpenFilterDialog)
        viewModel.onIntent(RecordsUiIntent.ToggleFilterEventType(RecordEventType.EXPOSURE))
        viewModel.onIntent(RecordsUiIntent.ApplyFilters)
        viewModel.onIntent(RecordsUiIntent.ClearFilters)

        assertEquals(1, viewModel.state.value.records.size)
        assertFalse(viewModel.state.value.activeFilters.hasActiveFilters)
        assertEquals("No records yet", viewModel.state.value.emptyStateTitle)
    }

    @Test
    fun restores_active_filters_from_saved_state_handle() {
        val includedDate = LocalDate.of(2026, 3, 20)
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "records.filter.start_date" to includedDate.toString(),
                "records.filter.end_date" to includedDate.toString(),
                "records.filter.event_types" to arrayListOf(RecordEventType.INGESTION.name),
                "records.filter.intensity_levels" to intArrayOf(IngestionQuantityOption.LIGHT_SNACK.level)
            )
        )
        val repository = repositoryWithRecords(
            records = listOf(
                record(
                    id = "ingestion-7",
                    typeLabel = "Ingestion",
                    title = "Tea",
                    detailLabel = "2 Light snack",
                    occurredAtEpochMs = includedDate.atStartOfDay(ZoneId.systemDefault()).plusHours(9).toInstant().toEpochMilli(),
                    sourceId = 7L,
                    eventType = RecordEventType.INGESTION,
                    ingestionQuantityLevel = 2
                ),
                record(
                    id = "exposure-5",
                    typeLabel = "Exposure",
                    title = "Pollen",
                    detailLabel = "Exposure recorded",
                    occurredAtEpochMs = includedDate.atStartOfDay(ZoneId.systemDefault()).plusHours(10).toInstant().toEpochMilli(),
                    sourceId = 5L,
                    eventType = RecordEventType.EXPOSURE
                )
            )
        )

        val viewModel = RecordsViewModel(
            currentTimeProvider(),
            ObserveRecordCountUseCase(repository),
            ObserveRecordsUseCase(repository),
            savedStateHandle
        )

        assertEquals(listOf("ingestion-7"), viewModel.state.value.records.map { it.id })
        assertEquals(
            listOf("2026-03-20 to 2026-03-20", "Types: Ingestion", "Intensity: 2 Light snack"),
            viewModel.state.value.activeFilters.summaryLabels
        )
    }

    private fun repositoryWithRecords(records: List<RecordSummary>): RecordsRepository {
        return object : RecordsRepository {
            override fun observeRecordCount() = flowOf(records.size)

            override fun observeRecords(filters: Flow<RecordsFilter>) = combine(flowOf(records), filters) { items, activeFilters ->
                items.filter { record ->
                    (activeFilters.eventTypes.isEmpty() || record.eventType in activeFilters.eventTypes) &&
                        (activeFilters.startDate == null || !Instant.ofEpochMilli(record.occurredAtEpochMs).atZone(ZoneId.systemDefault()).toLocalDate().isBefore(activeFilters.startDate)) &&
                        (activeFilters.endDate == null || !Instant.ofEpochMilli(record.occurredAtEpochMs).atZone(ZoneId.systemDefault()).toLocalDate().isAfter(activeFilters.endDate)) &&
                        (activeFilters.intensityLevels.isEmpty() || activeFilters.intensityLevels.any { it.level == record.ingestionQuantityLevel })
                }
            }
        }
    }

    private fun record(
        id: String,
        typeLabel: String,
        title: String,
        detailLabel: String,
        occurredAtEpochMs: Long = fixedNow,
        sourceId: Long,
        eventType: RecordEventType,
        ingestionQuantityLevel: Int? = null
    ): RecordSummary {
        return RecordSummary(
            id = id,
            sortKey = sourceId,
            typeLabel = typeLabel,
            title = title,
            detailLabel = detailLabel,
            occurredAtEpochMs = occurredAtEpochMs,
            createdAtEpochMs = fixedNow,
            sourceId = sourceId,
            eventType = eventType,
            ingestionQuantityLevel = ingestionQuantityLevel
        )
    }

    private fun currentTimeProvider(): CurrentTimeProvider {
        return CurrentTimeProvider { fixedNow }
    }
}
