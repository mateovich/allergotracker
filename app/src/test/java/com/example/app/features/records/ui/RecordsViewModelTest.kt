package com.example.app.features.records.ui

import com.example.app.MainDispatcherRule
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.records.domain.ObserveRecordCountUseCase
import com.example.app.features.records.domain.ObserveRecordsUseCase
import com.example.app.features.records.domain.RecordEventType
import com.example.app.features.records.domain.RecordSummary
import com.example.app.features.records.domain.RecordsRepository
import com.example.app.navigation.AppRoutes
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import java.time.Instant
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

    private val observeRecordCountUseCase = ObserveRecordCountUseCase(
        repository = object : RecordsRepository {
            override fun observeRecordCount() = flowOf(0)

            override fun observeRecords() = flowOf(emptyList<RecordSummary>())
        }
    )
    private val observeRecordsUseCase = ObserveRecordsUseCase(
        repository = object : RecordsRepository {
            override fun observeRecordCount() = flowOf(0)

            override fun observeRecords() = flowOf(emptyList<RecordSummary>())
        }
    )

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
        val repository = object : RecordsRepository {
            override fun observeRecordCount() = flowOf(1)

            override fun observeRecords() = flowOf(
                listOf(
                    RecordSummary(
                        id = "ingestion-7",
                        sortKey = 7L,
                        typeLabel = "Ingestion",
                        title = "Tea",
                        detailLabel = "2 Light snack",
                        occurredAtEpochMs = fixedNow,
                        sourceId = 7L,
                        eventType = RecordEventType.INGESTION
                    )
                )
            )
        }
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
        val repository = object : RecordsRepository {
            override fun observeRecordCount() = flowOf(1)

            override fun observeRecords() = flowOf(
                listOf(
                    RecordSummary(
                        id = "ingestion-7",
                        sortKey = 7L,
                        typeLabel = "Ingestion",
                        title = "Tea",
                        detailLabel = "2 Light snack",
                        occurredAtEpochMs = previousDayEpochMs,
                        sourceId = 7L,
                        eventType = RecordEventType.INGESTION
                    )
                )
            )
        }
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

    private fun currentTimeProvider(): CurrentTimeProvider {
        return CurrentTimeProvider { fixedNow }
    }
}
