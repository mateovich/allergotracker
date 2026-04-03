package com.example.app.features.records.data

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.IngestionQuantityOption
import com.example.app.features.records.domain.RecordDetail
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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

    override fun observeRecordDetail(recordId: Long, eventType: RecordEventType): Flow<AppResult<RecordDetail>> {
        return records.asStateFlow().map { values ->
            val record = values.firstOrNull { it.sourceId == recordId && it.eventType == eventType }
            if (record == null) {
                AppResult.Error(DomainError.PersistenceError("This record is no longer available."))
            } else {
                AppResult.Success(
                    when (eventType) {
                        RecordEventType.INGESTION -> RecordDetail.Ingestion(
                            recordId = record.sourceId,
                            occurredAtEpochMs = record.occurredAtEpochMs,
                            itemName = record.title,
                            quantityLabel = record.detailLabel.ifBlank {
                                IngestionQuantityOption.fromLevel(record.intensityLevel ?: 0)?.label.orEmpty()
                            },
                            reusableEntityKind = null,
                            reusableEntityId = null,
                            notes = null
                        )

                        RecordEventType.EXPOSURE -> RecordDetail.Exposure(
                            recordId = record.sourceId,
                            occurredAtEpochMs = record.occurredAtEpochMs,
                            exposureType = record.title,
                            intensityLabel = IngestionQuantityOption.fromLevel(record.intensityLevel ?: 0)?.label.orEmpty(),
                            notes = record.detailLabel.takeIf { it.isNotBlank() && it != "Exposure recorded" }
                        )

                        RecordEventType.OUTBREAK -> RecordDetail.Outbreak(
                            recordId = record.sourceId,
                            occurredAtEpochMs = record.occurredAtEpochMs,
                            outbreakDescriptor = record.title,
                            bodyLocation = record.detailLabel.substringBefore(" - ").ifBlank { "Unknown" },
                            intensityLabel = IngestionQuantityOption.fromLevel(record.intensityLevel ?: 0)?.label.orEmpty(),
                            notes = record.detailLabel.substringAfter(" - ", "").ifBlank { null }
                        )
                    }
                )
            }
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
        if (intensityLevel !in matchingLevels) {
            return false
        }
    }

    return true
}
