package com.example.app.features.records.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordSummarySortingTest {
    @Test
    fun records_are_sorted_newest_first_by_occurred_at() {
        val oldest = record(id = "ingestion-1", occurredAtEpochMs = 100L, createdAtEpochMs = 100L, sourceId = 1L, eventType = RecordEventType.INGESTION)
        val newest = record(id = "exposure-2", occurredAtEpochMs = 300L, createdAtEpochMs = 150L, sourceId = 2L, eventType = RecordEventType.EXPOSURE)
        val middle = record(id = "outbreak-3", occurredAtEpochMs = 200L, createdAtEpochMs = 200L, sourceId = 3L, eventType = RecordEventType.OUTBREAK)

        val sorted = listOf(middle, oldest, newest).sortedWith(RecordSummaryComparator)

        assertEquals(listOf(newest, middle, oldest).map { it.id }, sorted.map { it.id })
    }

    @Test
    fun records_with_equal_occurred_at_are_sorted_by_last_saved_first() {
        val occurredAt = 1_700_000_000_000L
        val older = record(
            id = "ingestion-1",
            occurredAtEpochMs = occurredAt,
            createdAtEpochMs = 100L,
            sourceId = 1L,
            eventType = RecordEventType.INGESTION
        )
        val newer = older.copy(
            id = "outbreak-9",
            sortKey = 9L,
            typeLabel = "Outbreak",
            title = "B",
            createdAtEpochMs = 200L,
            sourceId = 9L,
            eventType = RecordEventType.OUTBREAK
        )

        val sorted = listOf(older, newer).sortedWith(RecordSummaryComparator)

        assertEquals(listOf(newer, older).map { it.id }, sorted.map { it.id })
    }

    @Test
    fun records_with_equal_occurred_at_and_created_at_use_stable_deterministic_fallbacks() {
        val occurredAt = 1_700_000_000_000L
        val createdAt = 1_700_000_000_500L
        val lowerId = record(
            id = "outbreak-1",
            occurredAtEpochMs = occurredAt,
            createdAtEpochMs = createdAt,
            sourceId = 1L,
            eventType = RecordEventType.OUTBREAK
        )
        val higherId = lowerId.copy(id = "exposure-2", sortKey = 2L, sourceId = 2L, eventType = RecordEventType.EXPOSURE)
        val sameIdsDifferentType = lowerId.copy(id = "ingestion-1", eventType = RecordEventType.INGESTION)

        val sorted = listOf(lowerId, sameIdsDifferentType, higherId).sortedWith(RecordSummaryComparator)

        assertEquals(listOf(higherId, sameIdsDifferentType, lowerId).map { it.id }, sorted.map { it.id })
    }

    @Test
    fun preserving_created_at_keeps_longitudinal_tie_break_stable_after_historical_edit() {
        val editedRecord = record(
            id = "exposure-8",
            occurredAtEpochMs = 1_700_000_000_000L,
            createdAtEpochMs = 100L,
            sourceId = 8L,
            eventType = RecordEventType.EXPOSURE
        )
        val unchangedRecord = record(
            id = "ingestion-4",
            occurredAtEpochMs = 1_700_000_000_000L,
            createdAtEpochMs = 200L,
            sourceId = 4L,
            eventType = RecordEventType.INGESTION
        )

        val sorted = listOf(editedRecord, unchangedRecord).sortedWith(RecordSummaryComparator)

        assertEquals(listOf("ingestion-4", "exposure-8"), sorted.map { it.id })
    }

    private fun record(
        id: String,
        occurredAtEpochMs: Long,
        createdAtEpochMs: Long,
        sourceId: Long,
        eventType: RecordEventType
    ): RecordSummary {
        return RecordSummary(
            id = id,
            sortKey = sourceId,
            typeLabel = eventType.name,
            title = id,
            detailLabel = "",
            occurredAtEpochMs = occurredAtEpochMs,
            createdAtEpochMs = createdAtEpochMs,
            sourceId = sourceId,
            eventType = eventType,
            intensityLevel = null
        )
    }
}
