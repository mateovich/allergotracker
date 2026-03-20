package com.example.app.features.records.data

import com.example.app.features.exposure.data.ExposureEventDao
import com.example.app.features.ingestion.data.IngestionEventDao
import com.example.app.features.ingestion.domain.IngestionQuantityOption
import com.example.app.features.outbreak.data.OutbreakEventDao
import com.example.app.features.records.domain.RecordEventType
import com.example.app.features.records.domain.RecordsFilter
import com.example.app.features.records.domain.RecordSummary
import com.example.app.features.records.domain.RecordSummaryComparator
import com.example.app.features.records.domain.RecordsRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

/** Reactive records timeline derived directly from Room flows for all event types. */
class RoomRecordsRepository(
    private val ingestionEventDao: IngestionEventDao,
    private val exposureEventDao: ExposureEventDao,
    private val outbreakEventDao: OutbreakEventDao
) : RecordsRepository {
    private val zoneId = ZoneId.systemDefault()

    override fun observeRecordCount(): Flow<Int> {
        return combine(
            ingestionEventDao.observeRecordCount(),
            exposureEventDao.observeRecordCount(),
            outbreakEventDao.observeRecordCount()
        ) { ingestionCount, exposureCount, outbreakCount ->
            ingestionCount + exposureCount + outbreakCount
        }
    }

    override fun observeRecords(filters: Flow<RecordsFilter>): Flow<List<RecordSummary>> {
        val recordsFlow = combine(
            ingestionEventDao.observeIngestionEvents(),
            exposureEventDao.observeExposureEvents(),
            outbreakEventDao.observeOutbreakEvents()
        ) { ingestionEntities, exposureEntities, outbreakEntities ->
            val ingestionRecords = ingestionEntities.map { entity ->
                RecordSummary(
                    id = "ingestion-${entity.id}",
                    sortKey = entity.id,
                    typeLabel = "Ingestion",
                    title = entity.itemName,
                    detailLabel = IngestionQuantityOption.fromLevel(entity.quantityLevel)?.label.orEmpty(),
                    occurredAtEpochMs = entity.occurredAtEpochMs,
                    createdAtEpochMs = entity.createdAtEpochMs,
                    sourceId = entity.id,
                    eventType = RecordEventType.INGESTION,
                    ingestionQuantityLevel = entity.quantityLevel
                )
            }

            val exposureRecords = exposureEntities.map { entity ->
                RecordSummary(
                    id = "exposure-${entity.id}",
                    sortKey = entity.id,
                    typeLabel = "Exposure",
                    title = entity.exposureType,
                    detailLabel = entity.notes?.takeIf { it.isNotBlank() } ?: "Exposure recorded",
                    occurredAtEpochMs = entity.occurredAtEpochMs,
                    createdAtEpochMs = entity.createdAtEpochMs,
                    sourceId = entity.id,
                    eventType = RecordEventType.EXPOSURE,
                    ingestionQuantityLevel = null
                )
            }

            val outbreakRecords = outbreakEntities.map { entity ->
                RecordSummary(
                    id = "outbreak-${entity.id}",
                    sortKey = entity.id,
                    typeLabel = "Outbreak",
                    title = entity.outbreakDescriptor,
                    detailLabel = listOfNotNull(
                        entity.bodyLocation.takeIf { it.isNotBlank() },
                        entity.notes?.takeIf { it.isNotBlank() }
                    ).joinToString(" - "),
                    occurredAtEpochMs = entity.occurredAtEpochMs,
                    createdAtEpochMs = entity.createdAtEpochMs,
                    sourceId = entity.id,
                    eventType = RecordEventType.OUTBREAK,
                    ingestionQuantityLevel = null
                )
            }

            (ingestionRecords + exposureRecords + outbreakRecords)
                .sortedWith(RecordSummaryComparator)
        }

        return combine(recordsFlow, filters) { records, activeFilters ->
            records.filter { record -> record.matches(activeFilters, zoneId) }
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
