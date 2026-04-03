package com.example.app.features.records.data

import com.example.app.features.ingestion.domain.IngestionQuantityOption
import com.example.app.features.records.domain.RecordEventType
import com.example.app.features.records.domain.RecordSummary
import com.example.app.features.records.domain.RecordsFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryRecordsRepositoryTest {

    @Test
    fun starts_with_local_empty_record_set() = runBlocking {
        val repository = InMemoryRecordsRepository()

        assertEquals(0, repository.observeRecordCount().first())
    }

    @Test
    fun observe_records_applies_date_and_type_filters_with_and_semantics() = runBlocking {
        val zoneId = ZoneId.systemDefault()
        val includedDate = LocalDate.of(2026, 3, 20)
        val repository = InMemoryRecordsRepository(
            initialRecords = listOf(
                record(
                    id = "ingestion-7",
                    occurredAtEpochMs = includedDate.atStartOfDay(zoneId).plusHours(9).toInstant().toEpochMilli(),
                    eventType = RecordEventType.INGESTION,
                    intensityLevel = 2
                ),
                record(
                    id = "exposure-5",
                    occurredAtEpochMs = includedDate.atStartOfDay(zoneId).plusHours(10).toInstant().toEpochMilli(),
                    eventType = RecordEventType.EXPOSURE
                ),
                record(
                    id = "ingestion-9",
                    occurredAtEpochMs = includedDate.minusDays(1).atStartOfDay(zoneId).plusHours(8).toInstant().toEpochMilli(),
                    eventType = RecordEventType.INGESTION,
                    intensityLevel = 2
                )
            )
        )

        val visible = repository.observeRecords(
            flowOf(
                RecordsFilter(
                    startDate = includedDate,
                    endDate = includedDate,
                    eventTypes = setOf(RecordEventType.INGESTION)
                )
            )
        ).first()

        assertEquals(listOf("ingestion-7"), visible.map { it.id })
    }

    @Test
    fun observe_records_applies_shared_intensity_filter() = runBlocking {
        val repository = InMemoryRecordsRepository(
            initialRecords = listOf(
                record(
                    id = "ingestion-7",
                    eventType = RecordEventType.INGESTION,
                    intensityLevel = 2
                ),
                record(
                    id = "ingestion-8",
                    eventType = RecordEventType.INGESTION,
                    intensityLevel = 4
                ),
                record(
                    id = "outbreak-9",
                    eventType = RecordEventType.OUTBREAK,
                    intensityLevel = 2
                )
            )
        )

        val visible = repository.observeRecords(
            flowOf(
                RecordsFilter(intensityLevels = setOf(IngestionQuantityOption.LIGHT_SNACK))
            )
        ).first()

        assertEquals(listOf("ingestion-7", "outbreak-9"), visible.map { it.id })
    }

    private fun record(
        id: String,
        occurredAtEpochMs: Long = Instant.parse("2026-03-20T09:00:00Z").toEpochMilli(),
        eventType: RecordEventType,
        intensityLevel: Int? = null
    ): RecordSummary {
        return RecordSummary(
            id = id,
            sortKey = id.substringAfter('-').toLong(),
            typeLabel = eventType.name,
            title = id,
            detailLabel = id,
            occurredAtEpochMs = occurredAtEpochMs,
            createdAtEpochMs = occurredAtEpochMs,
            sourceId = id.substringAfter('-').toLong(),
            eventType = eventType,
            intensityLevel = intensityLevel
        )
    }
}
