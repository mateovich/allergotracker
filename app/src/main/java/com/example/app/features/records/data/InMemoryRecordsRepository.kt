package com.example.app.features.records.data

import com.example.app.features.records.domain.RecordsFilter
import com.example.app.features.records.domain.RecordsRepository
import com.example.app.features.records.domain.RecordSummary
import com.example.app.features.records.domain.RecordEventType
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

class InMemoryRecordsRepository(
    initialRecords: List<RecordSummary> = emptyList()
) : RecordsRepository {
    private val recordCount = MutableStateFlow(initialRecords.size)
    private val records = MutableStateFlow(initialRecords)

    override fun observeRecordCount(): Flow<Int> = recordCount.asStateFlow()

    override fun observeRecords(filters: Flow<RecordsFilter>): Flow<List<RecordSummary>> {
        val zoneId = ZoneId.systemDefault()
        return combine(records.asStateFlow(), filters) { values, activeFilters ->
            values.filter { record -> record.matches(activeFilters, zoneId) }
        }
    }
}

private fun RecordSummary.matches(filters: RecordsFilter, zoneId: ZoneId): Boolean {
    if (filters.eventTypes.isNotEmpty() && eventType !in filters.eventTypes) {
        return false
    }

    val occurredOn = Instant.ofEpochMilli(occurredAtEpochMs).atZone(zoneId).toLocalDate()
    if (filters.startDate != null && occurredOn.isBefore(filters.startDate)) {
        return false
    }
    if (filters.endDate != null && occurredOn.isAfter(filters.endDate)) {
        return false
    }

    if (filters.intensityLevels.isNotEmpty()) {
        val matchingLevels = filters.intensityLevels.map { it.level }.toSet()
        if (eventType != RecordEventType.INGESTION || ingestionQuantityLevel !in matchingLevels) {
            return false
        }
    }

    return true
}
