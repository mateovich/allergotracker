package io.github.mateovich.allergotracker.features.records.data

import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionQuantityOption
import io.github.mateovich.allergotracker.features.records.domain.RecordDetail
import io.github.mateovich.allergotracker.features.records.domain.RecordDetailSnapshot
import io.github.mateovich.allergotracker.features.records.domain.RecordEventType
import io.github.mateovich.allergotracker.features.records.domain.RecordSummary
import io.github.mateovich.allergotracker.features.records.domain.RecordsFilter
import io.github.mateovich.allergotracker.core.common.result.AppResult
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

    @Test
    fun observe_record_detail_keeps_outbreak_body_location_and_notes_when_body_location_contains_delimiter() = runBlocking {
        val repository = InMemoryRecordsRepository(
            initialRecords = listOf(
                RecordSummary(
                    id = "outbreak-9",
                    sortKey = 9L,
                    typeLabel = "Outbreak",
                    title = "Skin flare",
                    detailLabel = "Intensity 4 - Left arm - upper - after workout",
                    occurredAtEpochMs = Instant.parse("2026-03-20T09:00:00Z").toEpochMilli(),
                    createdAtEpochMs = Instant.parse("2026-03-20T09:00:00Z").toEpochMilli(),
                    sourceId = 9L,
                    eventType = RecordEventType.OUTBREAK,
                    intensityLevel = 4,
                    detailSnapshot = RecordDetailSnapshot.Outbreak(
                        bodyLocation = "Left arm - upper",
                        intensityLabel = "Intensity 4",
                        notes = "after workout"
                    )
                )
            )
        )

        val result = repository.observeRecordDetail(9L, RecordEventType.OUTBREAK).first()
        val detail = (result as AppResult.Success<RecordDetail>).value as RecordDetail.Outbreak

        assertEquals("Left arm - upper", detail.bodyLocation)
        assertEquals("Intensity 4", detail.intensityLabel)
        assertEquals("after workout", detail.notes)
    }

    @Test
    fun observe_record_detail_preserves_legacy_exposure_and_outbreak_fields_without_detail_snapshot() = runBlocking {
        val repository = InMemoryRecordsRepository(
            initialRecords = listOf(
                RecordSummary(
                    id = "exposure-5",
                    sortKey = 5L,
                    typeLabel = "Exposure",
                    title = "Pollen",
                    detailLabel = "outside",
                    occurredAtEpochMs = Instant.parse("2026-03-20T09:00:00Z").toEpochMilli(),
                    createdAtEpochMs = Instant.parse("2026-03-20T09:00:00Z").toEpochMilli(),
                    sourceId = 5L,
                    eventType = RecordEventType.EXPOSURE,
                    intensityLevel = 2
                ),
                RecordSummary(
                    id = "outbreak-9",
                    sortKey = 9L,
                    typeLabel = "Outbreak",
                    title = "Skin flare",
                    detailLabel = "Left arm - upper - after workout",
                    occurredAtEpochMs = Instant.parse("2026-03-20T09:00:00Z").toEpochMilli(),
                    createdAtEpochMs = Instant.parse("2026-03-20T09:00:00Z").toEpochMilli(),
                    sourceId = 9L,
                    eventType = RecordEventType.OUTBREAK,
                    intensityLevel = 4
                )
            )
        )

        val exposureResult = repository.observeRecordDetail(5L, RecordEventType.EXPOSURE).first()
        val exposureDetail = (exposureResult as AppResult.Success<RecordDetail>).value as RecordDetail.Exposure
        assertEquals("outside", exposureDetail.notes)

        val outbreakResult = repository.observeRecordDetail(9L, RecordEventType.OUTBREAK).first()
        val outbreakDetail = (outbreakResult as AppResult.Success<RecordDetail>).value as RecordDetail.Outbreak
        assertEquals("Left arm", outbreakDetail.bodyLocation)
        assertEquals("upper - after workout", outbreakDetail.notes)
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
