package io.github.mateovich.allergotracker.features.outbreak.data

import android.database.sqlite.SQLiteConstraintException
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogKind
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogRepository
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import io.github.mateovich.allergotracker.features.outbreak.domain.CreateOutbreakEventCommand
import io.github.mateovich.allergotracker.features.outbreak.domain.OutbreakEvent
import io.github.mateovich.allergotracker.features.outbreak.domain.OutbreakRepository
import io.github.mateovich.allergotracker.features.outbreak.domain.UpdateOutbreakEventCommand

/** Local source of truth for outbreak events backed by Room only. */
class RoomOutbreakRepository(
    private val outbreakEventDao: OutbreakEventDao,
    private val currentTimeProvider: CurrentTimeProvider,
    private val eventCatalogRepository: ReusableEventCatalogRepository? = null
) : OutbreakRepository {
    override suspend fun createOutbreak(command: CreateOutbreakEventCommand): AppResult<OutbreakEvent> {
        return try {
            val outbreakDescriptorCatalogId = resolveCatalogId(
                kind = ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR,
                selectedCatalogId = command.outbreakDescriptorCatalogId,
                name = command.outbreakDescriptor
            )
            val bodyLocationCatalogId = resolveCatalogId(
                kind = ReusableEventCatalogKind.BODY_LOCATION,
                selectedCatalogId = command.bodyLocationCatalogId,
                name = command.bodyLocation
            )
            val entity = OutbreakEventEntity(
                outbreakDescriptor = command.outbreakDescriptor,
                bodyLocation = command.bodyLocation,
                occurredAtEpochMs = command.occurredAtEpochMs,
                notes = command.notes,
                durationMinutes = command.durationMinutes,
                intensityLevel = command.intensityLevel,
                createdAtEpochMs = currentTimeProvider.nowEpochMillis(),
                outbreakDescriptorCatalogId = outbreakDescriptorCatalogId,
                bodyLocationCatalogId = bodyLocationCatalogId
            )
            val id = outbreakEventDao.insert(entity)
            AppResult.Success(
                OutbreakEvent(
                    id = id,
                    outbreakDescriptor = entity.outbreakDescriptor,
                    bodyLocation = entity.bodyLocation,
                    occurredAtEpochMs = entity.occurredAtEpochMs,
                    notes = entity.notes,
                    durationMinutes = entity.durationMinutes,
                    intensityLevel = entity.intensityLevel,
                    outbreakDescriptorCatalogId = entity.outbreakDescriptorCatalogId,
                    bodyLocationCatalogId = entity.bodyLocationCatalogId
                )
            )
        } catch (error: MissingReusableCatalogReferenceException) {
            AppResult.Error(DomainError.IntegrityError(error.message ?: "Saved reusable value no longer exists."))
        } catch (_: Exception) {
            AppResult.Error(
                DomainError.PersistenceError("Couldn't save outbreak locally. Try again.")
            )
        }
    }

    override suspend fun getOutbreak(id: Long): AppResult<OutbreakEvent> {
        return try {
            val entity = outbreakEventDao.getById(id)
                ?: return AppResult.Error(DomainError.IntegrityError("Outbreak record no longer exists."))
            AppResult.Success(entity.toDomain())
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't load outbreak locally. Try again."))
        }
    }

    override suspend fun updateOutbreak(command: UpdateOutbreakEventCommand): AppResult<OutbreakEvent> {
        return try {
            val currentEntity = outbreakEventDao.getById(command.id)
                ?: return AppResult.Error(DomainError.IntegrityError("Outbreak record no longer exists."))
            val updatedEntity = currentEntity.copy(
                outbreakDescriptor = command.outbreakDescriptor,
                bodyLocation = command.bodyLocation,
                occurredAtEpochMs = command.occurredAtEpochMs,
                notes = command.notes,
                durationMinutes = command.durationMinutes,
                intensityLevel = command.intensityLevel,
                outbreakDescriptorCatalogId = resolveCatalogId(
                    kind = ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR,
                    selectedCatalogId = command.outbreakDescriptorCatalogId,
                    name = command.outbreakDescriptor
                ),
                bodyLocationCatalogId = resolveCatalogId(
                    kind = ReusableEventCatalogKind.BODY_LOCATION,
                    selectedCatalogId = command.bodyLocationCatalogId,
                    name = command.bodyLocation
                )
            )
            val updatedCount = outbreakEventDao.update(updatedEntity)
            if (updatedCount == 0) {
                return AppResult.Error(DomainError.IntegrityError("Outbreak record no longer exists."))
            }
            AppResult.Success(updatedEntity.toDomain())
        } catch (error: MissingReusableCatalogReferenceException) {
            AppResult.Error(DomainError.IntegrityError(error.message ?: "Saved reusable value no longer exists."))
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't update outbreak locally. Try again."))
        }
    }

    override suspend fun deleteOutbreak(id: Long): AppResult<Unit> {
        return try {
            outbreakEventDao.getById(id)
                ?: return AppResult.Error(DomainError.IntegrityError("Outbreak record no longer exists."))
            val deletedCount = outbreakEventDao.deleteById(id)
            if (deletedCount == 0) {
                return AppResult.Error(DomainError.IntegrityError("Outbreak record no longer exists."))
            }
            AppResult.Success(Unit)
        } catch (_: SQLiteConstraintException) {
            AppResult.Error(DomainError.IntegrityError("Outbreak record could not be deleted safely."))
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't delete outbreak locally. Try again."))
        }
    }

    private fun OutbreakEventEntity.toDomain(): OutbreakEvent {
        return OutbreakEvent(
            id = id,
            outbreakDescriptor = outbreakDescriptor,
            bodyLocation = bodyLocation,
            occurredAtEpochMs = occurredAtEpochMs,
            notes = notes,
            durationMinutes = durationMinutes,
            intensityLevel = intensityLevel,
            outbreakDescriptorCatalogId = outbreakDescriptorCatalogId,
            bodyLocationCatalogId = bodyLocationCatalogId
        )
    }

    private suspend fun resolveCatalogId(
        kind: ReusableEventCatalogKind,
        selectedCatalogId: Long?,
        name: String
    ): Long? {
        if (name.isBlank()) return null
        val repository = eventCatalogRepository ?: return selectedCatalogId
        if (selectedCatalogId != null) {
            return repository.touchCatalogEntry(kind, selectedCatalogId)?.id
                ?: throw MissingReusableCatalogReferenceException(
                    when (kind) {
                        ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> "Saved outbreak descriptor no longer exists."
                        ReusableEventCatalogKind.BODY_LOCATION -> "Saved body location no longer exists."
                        ReusableEventCatalogKind.EXPOSURE_TYPE -> "Saved exposure type no longer exists."
                    }
                )
        }
        return repository.ensureCatalogEntry(kind, name).id
    }
}

private class MissingReusableCatalogReferenceException(message: String) : IllegalStateException(message)
