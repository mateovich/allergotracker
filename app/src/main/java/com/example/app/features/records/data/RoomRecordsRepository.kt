package com.example.app.features.records.data

import com.example.app.features.exposure.data.ExposureEventDao
import com.example.app.features.ingestion.data.IngestionEventDao
import com.example.app.features.ingestion.domain.IngestionQuantityOption
import com.example.app.features.outbreak.data.OutbreakEventDao
import com.example.app.features.records.domain.RecordEventType
import com.example.app.features.records.domain.RecordSummary
import com.example.app.features.records.domain.RecordsRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow

class RoomRecordsRepository(
    private val ingestionEventDao: IngestionEventDao,
    private val exposureEventDao: ExposureEventDao,
    private val outbreakEventDao: OutbreakEventDao
) : RecordsRepository {
    override fun observeRecordCount(): Flow<Int> {
        return combine(
            ingestionEventDao.observeRecordCount(),
            exposureEventDao.observeRecordCount(),
            outbreakEventDao.observeRecordCount()
        ) { ingestionCount, exposureCount, outbreakCount ->
            ingestionCount + exposureCount + outbreakCount
        }
    }

    override fun observeRecords(): Flow<List<RecordSummary>> {
        return combine(
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
                    sourceId = entity.id,
                    eventType = RecordEventType.INGESTION
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
                    sourceId = entity.id,
                    eventType = RecordEventType.EXPOSURE
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
                    sourceId = entity.id,
                    eventType = RecordEventType.OUTBREAK
                )
            }

            (ingestionRecords + exposureRecords + outbreakRecords)
                .sortedWith(
                    compareByDescending<RecordSummary> { it.occurredAtEpochMs }
                        .thenByDescending { it.sortKey }
                        .thenBy { it.eventType.ordinal }
                )
        }
    }
}
