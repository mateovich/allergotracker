package com.example.app.features.exposure.data

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.exposure.domain.CreateExposureEventCommand
import com.example.app.features.exposure.domain.ExposureEvent
import com.example.app.features.exposure.domain.ExposureRepository
import com.example.app.features.exposure.domain.UpdateExposureEventCommand
import com.example.app.features.ingestion.domain.CurrentTimeProvider

class RoomExposureRepository(
    private val exposureEventDao: ExposureEventDao,
    private val currentTimeProvider: CurrentTimeProvider
) : ExposureRepository {
    override suspend fun createExposure(command: CreateExposureEventCommand): AppResult<ExposureEvent> {
        return try {
            val entity = ExposureEventEntity(
                exposureType = command.exposureType,
                occurredAtEpochMs = command.occurredAtEpochMs,
                notes = command.notes,
                createdAtEpochMs = currentTimeProvider.nowEpochMillis()
            )
            val id = exposureEventDao.insert(entity)
            AppResult.Success(
                ExposureEvent(
                    id = id,
                    exposureType = entity.exposureType,
                    occurredAtEpochMs = entity.occurredAtEpochMs,
                    notes = entity.notes
                )
            )
        } catch (_: Exception) {
            AppResult.Error(
                DomainError.PersistenceError("Couldn't save exposure locally. Try again.")
            )
        }
    }

    override suspend fun getExposure(id: Long): AppResult<ExposureEvent> {
        return try {
            val entity = exposureEventDao.getById(id)
                ?: return AppResult.Error(DomainError.IntegrityError("Exposure record no longer exists."))
            AppResult.Success(entity.toDomain())
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't load exposure locally. Try again."))
        }
    }

    override suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent> {
        return try {
            val currentEntity = exposureEventDao.getById(command.id)
                ?: return AppResult.Error(DomainError.IntegrityError("Exposure record no longer exists."))
            val updatedEntity = currentEntity.copy(
                exposureType = command.exposureType,
                occurredAtEpochMs = command.occurredAtEpochMs,
                notes = command.notes
            )
            val updatedCount = exposureEventDao.update(updatedEntity)
            if (updatedCount == 0) {
                return AppResult.Error(DomainError.IntegrityError("Exposure record no longer exists."))
            }
            AppResult.Success(updatedEntity.toDomain())
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't update exposure locally. Try again."))
        }
    }

    private fun ExposureEventEntity.toDomain(): ExposureEvent {
        return ExposureEvent(
            id = id,
            exposureType = exposureType,
            occurredAtEpochMs = occurredAtEpochMs,
            notes = notes
        )
    }
}
