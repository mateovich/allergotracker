package io.github.mateovich.allergotracker.features.records.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveRecordCountUseCaseTest {

    @Test
    fun returns_record_count_from_repository() = runBlocking {
        val useCase = ObserveRecordCountUseCase(
            repository = object : RecordsRepository {
                override fun observeRecordCount() = flowOf(3)

                override fun observeRecords(filters: Flow<RecordsFilter>) = flowOf(emptyList<RecordSummary>())
            }
        )

        assertEquals(3, useCase().first())
    }
}
