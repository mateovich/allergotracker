package io.github.mateovich.allergotracker.features.records.data

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.exposure.data.ExposureEventDao
import io.github.mateovich.allergotracker.features.ingestion.data.IngestionEventDao
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionQuantityOption
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import io.github.mateovich.allergotracker.features.outbreak.data.OutbreakEventDao
import io.github.mateovich.allergotracker.features.records.domain.RecordDetail
import io.github.mateovich.allergotracker.features.records.domain.RecordDetailSnapshot
import io.github.mateovich.allergotracker.features.records.domain.RecordEventType
import io.github.mateovich.allergotracker.features.records.domain.RecordsFilter
import io.github.mateovich.allergotracker.features.records.domain.RecordSummary
import io.github.mateovich.allergotracker.features.records.domain.RecordSummaryComparator
import io.github.mateovich.allergotracker.features.records.domain.RecordsRepository
import io.github.mateovich.allergotracker.features.records.domain.recordIntensityLabel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
            val ingestionRecords = ingestionEntities.map { entity -> entity.toRecordSummary() }
            val exposureRecords = exposureEntities.map { entity -> entity.toRecordSummary() }
            val outbreakRecords = outbreakEntities.map { entity -> entity.toRecordSummary() }

            (ingestionRecords + exposureRecords + outbreakRecords)
                .sortedWith(RecordSummaryComparator)
        }

        return combine(recordsFlow, filters) { records, activeFilters ->
            records.filter { record -> record.matches(activeFilters, zoneId) }
        }
    }

    override fun observeRecordDetail(recordId: Long, eventType: RecordEventType): Flow<AppResult<RecordDetail>> {
        return when (eventType) {
            RecordEventType.INGESTION -> ingestionEventDao.observeById(recordId).map { entity ->
                if (entity != null) {
                    AppResult.Success(entity.toRecordDetail())
                } else {
                    AppResult.Error(DomainError.PersistenceError("This record is no longer available."))
                }
            }

            RecordEventType.EXPOSURE -> exposureEventDao.observeById(recordId).map { entity ->
                if (entity != null) {
                    AppResult.Success(entity.toRecordDetail())
                } else {
                    AppResult.Error(DomainError.PersistenceError("This record is no longer available."))
                }
            }

            RecordEventType.OUTBREAK -> outbreakEventDao.observeById(recordId).map { entity ->
                if (entity != null) {
                    AppResult.Success(entity.toRecordDetail())
                } else {
                    AppResult.Error(DomainError.PersistenceError("This record is no longer available."))
                }
            }
        }
    }
}

private fun io.github.mateovich.allergotracker.features.ingestion.data.IngestionEventEntity.toRecordSummary(): RecordSummary {
    return RecordSummary(
        id = "ingestion-$id",
        sortKey = id,
        typeLabel = "Ingestion",
        title = itemName,
        detailLabel = listOfNotNull(
            IngestionQuantityOption.fromLevel(quantityLevel)?.label,
            ReusableEntityKind.fromStorageValue(reusableEntityKind)?.label
        ).joinToString(" - "),
        occurredAtEpochMs = occurredAtEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        sourceId = id,
        eventType = RecordEventType.INGESTION,
        intensityLevel = quantityLevel,
        detailSnapshot = RecordDetailSnapshot.Ingestion(
            quantityLabel = IngestionQuantityOption.fromLevel(quantityLevel)?.label.orEmpty(),
            reusableEntityKind = ReusableEntityKind.fromStorageValue(reusableEntityKind),
            reusableEntityId = reusableEntityId,
            notes = notes
        )
    )
}

private fun io.github.mateovich.allergotracker.features.exposure.data.ExposureEventEntity.toRecordSummary(): RecordSummary {
    return RecordSummary(
        id = "exposure-$id",
        sortKey = id,
        typeLabel = "Exposure",
        title = exposureType,
        detailLabel = listOfNotNull(
            recordIntensityLabel(intensityLevel),
            notes?.takeIf { it.isNotBlank() }
        ).joinToString(" - ").ifBlank { "Exposure recorded" },
        occurredAtEpochMs = occurredAtEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        sourceId = id,
        eventType = RecordEventType.EXPOSURE,
        intensityLevel = intensityLevel,
        detailSnapshot = RecordDetailSnapshot.Exposure(
            intensityLabel = recordIntensityLabel(intensityLevel),
            notes = notes
        )
    )
}

private fun io.github.mateovich.allergotracker.features.outbreak.data.OutbreakEventEntity.toRecordSummary(): RecordSummary {
    return RecordSummary(
        id = "outbreak-$id",
        sortKey = id,
        typeLabel = "Outbreak",
        title = outbreakDescriptor,
        detailLabel = listOfNotNull(
            recordIntensityLabel(intensityLevel),
            bodyLocation.takeIf { it.isNotBlank() },
            notes?.takeIf { it.isNotBlank() }
        ).joinToString(" - "),
        occurredAtEpochMs = occurredAtEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        sourceId = id,
        eventType = RecordEventType.OUTBREAK,
        intensityLevel = intensityLevel,
        detailSnapshot = RecordDetailSnapshot.Outbreak(
            bodyLocation = bodyLocation,
            intensityLabel = recordIntensityLabel(intensityLevel),
            notes = notes
        )
    )
}

private fun io.github.mateovich.allergotracker.features.ingestion.data.IngestionEventEntity.toRecordDetail(): RecordDetail {
    return RecordDetail.Ingestion(
        recordId = id,
        occurredAtEpochMs = occurredAtEpochMs,
        itemName = itemName,
        quantityLabel = IngestionQuantityOption.fromLevel(quantityLevel)?.label.orEmpty(),
        reusableEntityKind = ReusableEntityKind.fromStorageValue(reusableEntityKind),
        reusableEntityId = reusableEntityId,
        notes = notes
    )
}

private fun io.github.mateovich.allergotracker.features.exposure.data.ExposureEventEntity.toRecordDetail(): RecordDetail {
    return RecordDetail.Exposure(
        recordId = id,
        occurredAtEpochMs = occurredAtEpochMs,
        exposureType = exposureType,
        intensityLabel = recordIntensityLabel(intensityLevel),
        notes = notes
    )
}

private fun io.github.mateovich.allergotracker.features.outbreak.data.OutbreakEventEntity.toRecordDetail(): RecordDetail {
    return RecordDetail.Outbreak(
        recordId = id,
        occurredAtEpochMs = occurredAtEpochMs,
        outbreakDescriptor = outbreakDescriptor,
        bodyLocation = bodyLocation,
        intensityLabel = recordIntensityLabel(intensityLevel),
        notes = notes
    )
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
