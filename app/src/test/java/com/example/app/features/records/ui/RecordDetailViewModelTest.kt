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
}
