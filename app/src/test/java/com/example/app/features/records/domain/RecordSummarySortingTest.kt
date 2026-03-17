package com.example.app.features.records.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordSummarySortingTest {
    @Test
    fun records_with_equal_occurred_at_are_sorted_by_last_saved_first() {
        val occurredAt = 1_700_000_000_000L
        val older = RecordSummary(
            id = "ingestion-1",
            sortKey = 1L,
            typeLabel = "Ingestion",
            title = "A",
            detailLabel = "",
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
}
