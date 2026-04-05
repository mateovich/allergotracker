package com.example.app.features.records.ui

import com.example.app.MainDispatcherRule
import com.example.app.core.common.result.AppResult
import com.example.app.features.records.domain.ObserveRecordDetailUseCase
import com.example.app.features.records.domain.RecordDetail
import com.example.app.features.records.domain.RecordEventType
import com.example.app.features.records.domain.RecordSummary
import com.example.app.features.records.domain.RecordsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecordDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun exposure_detail_includes_intensity_field() {
        val viewModel = RecordDetailViewModel(
            recordId = 5L,
            eventType = RecordEventType.EXPOSURE,
            observeRecordDetailUseCase = ObserveRecordDetailUseCase(
                detailRepository(
                    AppResult.Success(
                        RecordDetail.Exposure(
                            recordId = 5L,
                            occurredAtEpochMs = 1_700_000_000_000L,
                            exposureType = "Pollen",
                            intensityLabel = "2 Light snack",
                            notes = "outside"
                        )
                    )
                )
            )
        )

        viewModel.onIntent(RecordDetailUiIntent.Load)

        assertEquals(
            listOf("Exposure", "Intensity", "Notes"),
            viewModel.state.value.fields.map { it.label }
        )
        assertEquals("2 Light snack", viewModel.state.value.fields[1].value)
    }

    @Test
    fun outbreak_detail_includes_intensity_field() {
        val viewModel = RecordDetailViewModel(
            recordId = 9L,
            eventType = RecordEventType.OUTBREAK,
            observeRecordDetailUseCase = ObserveRecordDetailUseCase(
                detailRepository(
                    AppResult.Success(
                        RecordDetail.Outbreak(
                            recordId = 9L,
                            occurredAtEpochMs = 1_700_000_000_000L,
                            outbreakDescriptor = "Skin flare",
                            bodyLocation = "Left arm",
                            intensityLabel = "4 Large",
                            notes = "after workout"
                        )
                    )
                )
            )
        )

        viewModel.onIntent(RecordDetailUiIntent.Load)

        assertEquals(
            listOf("Descriptor", "Body location", "Intensity", "Notes"),
            viewModel.state.value.fields.map { it.label }
        )
        assertEquals("4 Large", viewModel.state.value.fields[2].value)
    }

    @Test
    fun ingestion_detail_shows_compound_reusable_source_when_linked() {
        val viewModel = RecordDetailViewModel(
            recordId = 10L,
            eventType = RecordEventType.INGESTION,
            observeRecordDetailUseCase = ObserveRecordDetailUseCase(
                detailRepository(
                    AppResult.Success(
                        RecordDetail.Ingestion(
                            recordId = 10L,
                            occurredAtEpochMs = 1_700_000_000_000L,
                            itemName = "Breakfast mix shake",
                            quantityLabel = "3 Standard",
                            reusableEntityKind = com.example.app.features.ingestion.domain.ReusableEntityKind.COMPOUND,
                            reusableEntityId = 42L,
                            notes = "morning routine"
                        )
                    )
                )
            )
        )

        viewModel.onIntent(RecordDetailUiIntent.Load)

        val fields = viewModel.state.value.fields
        assertEquals(4, fields.size)
        assertEquals("Item", fields[0].label)
        assertEquals("Breakfast mix shake", fields[0].value)
        assertEquals("Reusable source", fields[2].label)
        assertEquals("Reusable compound", fields[2].value)
    }

    @Test
    fun ingestion_detail_shows_manual_entry_when_no_reusable_link() {
        val viewModel = RecordDetailViewModel(
            recordId = 11L,
            eventType = RecordEventType.INGESTION,
            observeRecordDetailUseCase = ObserveRecordDetailUseCase(
                detailRepository(
                    AppResult.Success(
                        RecordDetail.Ingestion(
                            recordId = 11L,
                            occurredAtEpochMs = 1_700_000_000_000L,
                            itemName = "Random snack",
                            quantityLabel = "2 Light snack",
                            reusableEntityKind = null,
                            reusableEntityId = null,
                            notes = null
                        )
                    )
                )
            )
        )

        viewModel.onIntent(RecordDetailUiIntent.Load)

        val reusableField = viewModel.state.value.fields.find { it.label == "Reusable source" }
        assertEquals("Manual entry", reusableField?.value)
    }

    @Test
    fun ingestion_detail_shows_ingredient_reusable_source_when_linked() {
        val viewModel = RecordDetailViewModel(
            recordId = 12L,
            eventType = RecordEventType.INGESTION,
            observeRecordDetailUseCase = ObserveRecordDetailUseCase(
                detailRepository(
                    AppResult.Success(
                        RecordDetail.Ingestion(
                            recordId = 12L,
                            occurredAtEpochMs = 1_700_000_000_000L,
                            itemName = "Oat milk glass",
                            quantityLabel = "1 Small bite",
                            reusableEntityKind = com.example.app.features.ingestion.domain.ReusableEntityKind.INGREDIENT,
                            reusableEntityId = 5L,
                            notes = null
                        )
                    )
                )
            )
        )

        viewModel.onIntent(RecordDetailUiIntent.Load)

        val reusableField = viewModel.state.value.fields.find { it.label == "Reusable source" }
        assertEquals("Reusable ingredient", reusableField?.value)
    }

    @Test
    fun ingestion_detail_handles_retired_compound_gracefully() {
        val viewModel = RecordDetailViewModel(
            recordId = 13L,
            eventType = RecordEventType.INGESTION,
            observeRecordDetailUseCase = ObserveRecordDetailUseCase(
                detailRepository(
                    AppResult.Success(
                        RecordDetail.Ingestion(
                            recordId = 13L,
                            occurredAtEpochMs = 1_700_000_000_000L,
                            itemName = "Old compound recipe",
                            quantityLabel = "3 Standard",
                            reusableEntityKind = null,
                            reusableEntityId = null,
                            notes = "compound was retired, text preserved"
                        )
                    )
                )
            )
        )

        viewModel.onIntent(RecordDetailUiIntent.Load)

        val state = viewModel.state.value
        assertEquals("Old compound recipe", state.title)
        val reusableField = state.fields.find { it.label == "Reusable source" }
        assertEquals("Manual entry", reusableField?.value)
    }

    @Test
    fun ingestion_detail_fields_remain_stable_after_compound_name_sync() {
        val viewModel = RecordDetailViewModel(
            recordId = 14L,
            eventType = RecordEventType.INGESTION,
            observeRecordDetailUseCase = ObserveRecordDetailUseCase(
                detailRepository(
                    AppResult.Success(
                        RecordDetail.Ingestion(
                            recordId = 14L,
                            occurredAtEpochMs = 1_700_000_000_000L,
                            itemName = "Updated compound name",
                            quantityLabel = "3 Standard",
                            reusableEntityKind = com.example.app.features.ingestion.domain.ReusableEntityKind.COMPOUND,
                            reusableEntityId = 42L,
                            notes = "synced name"
                        )
                    )
                )
            )
        )

        viewModel.onIntent(RecordDetailUiIntent.Load)

        val state = viewModel.state.value
        assertEquals(4, state.fields.size)
        assertEquals("Updated compound name", state.title)
        assertEquals("Updated compound name", state.fields[0].value)
        assertEquals("Reusable compound", state.fields[2].value)
    }

    @Test
    fun ingestion_detail_shows_unavailable_dialog_when_record_deleted() {
        val viewModel = RecordDetailViewModel(
            recordId = 99L,
            eventType = RecordEventType.INGESTION,
            observeRecordDetailUseCase = ObserveRecordDetailUseCase(
                detailRepositoryError(
                    com.example.app.core.common.error.DomainError.IntegrityError("Ingestion record no longer exists.")
                )
            )
        )

        viewModel.onIntent(RecordDetailUiIntent.Load)

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.isUnavailableDialogVisible)
        assertEquals("Ingestion record no longer exists.", state.unavailableMessage)
    }

    private fun detailRepository(result: AppResult<RecordDetail>): RecordsRepository {
        return object : RecordsRepository {
            override fun observeRecordCount(): Flow<Int> = flowOf(1)

            override fun observeRecords(filters: Flow<com.example.app.features.records.domain.RecordsFilter>): Flow<List<RecordSummary>> {
                return flowOf(emptyList())
            }

            override fun observeRecordDetail(recordId: Long, eventType: RecordEventType): Flow<AppResult<RecordDetail>> {
                return flowOf(result)
            }
        }
    }

    private fun detailRepositoryError(error: com.example.app.core.common.error.DomainError): RecordsRepository {
        return object : RecordsRepository {
            override fun observeRecordCount(): Flow<Int> = flowOf(1)

            override fun observeRecords(filters: Flow<com.example.app.features.records.domain.RecordsFilter>): Flow<List<RecordSummary>> {
                return flowOf(emptyList())
            }

            override fun observeRecordDetail(recordId: Long, eventType: RecordEventType): Flow<AppResult<RecordDetail>> {
                return flowOf(AppResult.Error(error))
            }
        }
    }
}
