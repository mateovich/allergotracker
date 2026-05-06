package io.github.mateovich.allergotracker.features.exposure.data

import android.database.sqlite.SQLiteConstraintException
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogKind
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogRepository
import io.github.mateovich.allergotracker.features.exposure.domain.CreateExposureEventCommand
import io.github.mateovich.allergotracker.features.exposure.domain.ExposureEvent
import io.github.mateovich.allergotracker.features.exposure.domain.ExposureRepository
import io.github.mateovich.allergotracker.features.exposure.domain.UpdateExposureEventCommand
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider

/** Local source of truth for exposure events backed by Room only. */
class RoomExposureRepository(
    private val exposureEventDao: ExposureEventDao,
    private val currentTimeProvider: CurrentTimeProvider,
    private val eventCatalogRepository: ReusableEventCatalogRepository? = null
) : ExposureRepository {
    override suspend fun createExposure(command: CreateExposureEventCommand): AppResult<ExposureEvent> {
        return try {
            val exposureCatalogId = resolveExposureCatalogId(command.exposureTypeCatalogId, command.exposureType)
            val entity = ExposureEventEntity(
                exposureType = command.exposureType,
                occurredAtEpochMs = command.occurredAtEpochMs,
                notes = command.notes,
                durationMinutes = command.durationMinutes,
                intensityLevel = command.intensityLevel,
                createdAtEpochMs = currentTimeProvider.nowEpochMillis(),
                exposureTypeCatalogId = exposureCatalogId,
                place = command.place
            )
            val id = exposureEventDao.insert(entity)
            AppResult.Success(
                ExposureEvent(
                    id = id,
                    exposureType = entity.exposureType,
                    occurredAtEpochMs = entity.occurredAtEpochMs,
                    durationMinutes = entity.durationMinutes,
                    place = entity.place,
                    notes = entity.notes,
                    intensityLevel = entity.intensityLevel,
                    exposureTypeCatalogId = entity.exposureTypeCatalogId
                )
            )
        } catch (error: MissingReusableCatalogReferenceException) {
            AppResult.Error(DomainError.IntegrityError(error.message ?: "Saved exposure type no longer exists."))
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
                durationMinutes = command.durationMinutes,
                place = command.place,
                notes = command.notes,
                intensityLevel = command.intensityLevel,
                exposureTypeCatalogId = resolveExposureCatalogId(command.exposureTypeCatalogId, command.exposureType)
            )
            val updatedCount = exposureEventDao.update(updatedEntity)
            if (updatedCount == 0) {
                return AppResult.Error(DomainError.IntegrityError("Exposure record no longer exists."))
            }
            AppResult.Success(updatedEntity.toDomain())
        } catch (error: MissingReusableCatalogReferenceException) {
            AppResult.Error(DomainError.IntegrityError(error.message ?: "Saved exposure type no longer exists."))
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't update exposure locally. Try again."))
        }
    }

    override suspend fun deleteExposure(id: Long): AppResult<Unit> {
        return try {
            exposureEventDao.getById(id)
                ?: return AppResult.Error(DomainError.IntegrityError("Exposure record no longer exists."))
            val deletedCount = exposureEventDao.deleteById(id)
            if (deletedCount == 0) {
                return AppResult.Error(DomainError.IntegrityError("Exposure record no longer exists."))
            }
            AppResult.Success(Unit)
        } catch (_: SQLiteConstraintException) {
            AppResult.Error(DomainError.IntegrityError("Exposure record could not be deleted safely."))
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't delete exposure locally. Try again."))
        }
    }

    private fun ExposureEventEntity.toDomain(): ExposureEvent {
        return ExposureEvent(
            id = id,
            exposureType = exposureType,
            occurredAtEpochMs = occurredAtEpochMs,
            durationMinutes = durationMinutes,
            place = place,
            notes = notes,
            intensityLevel = intensityLevel,
            exposureTypeCatalogId = exposureTypeCatalogId
        )
    }

    private suspend fun resolveExposureCatalogId(selectedCatalogId: Long?, name: String): Long? {
        if (name.isBlank()) return null
        val repository = eventCatalogRepository ?: return selectedCatalogId
        if (selectedCatalogId != null) {
            return repository.touchCatalogEntry(ReusableEventCatalogKind.EXPOSURE_TYPE, selectedCatalogId)?.id
                ?: throw MissingReusableCatalogReferenceException("Saved exposure type no longer exists.")
        }
        return repository.ensureCatalogEntry(ReusableEventCatalogKind.EXPOSURE_TYPE, name).id
    }
}

private class MissingReusableCatalogReferenceException(message: String) : IllegalStateException(message)
