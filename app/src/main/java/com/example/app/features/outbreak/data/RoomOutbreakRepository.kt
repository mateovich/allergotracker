package com.example.app.features.outbreak.data

import android.database.sqlite.SQLiteConstraintException
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.outbreak.domain.CreateOutbreakEventCommand
import com.example.app.features.outbreak.domain.OutbreakEvent
import com.example.app.features.outbreak.domain.OutbreakRepository
import com.example.app.features.outbreak.domain.UpdateOutbreakEventCommand

/** Local source of truth for outbreak events backed by Room only. */
class RoomOutbreakRepository(
    private val outbreakEventDao: OutbreakEventDao,
    private val currentTimeProvider: CurrentTimeProvider
) : OutbreakRepository {
    override suspend fun createOutbreak(command: CreateOutbreakEventCommand): AppResult<OutbreakEvent> {
        return try {
            val entity = OutbreakEventEntity(
                outbreakDescriptor = command.outbreakDescriptor,
                bodyLocation = command.bodyLocation,
                occurredAtEpochMs = command.occurredAtEpochMs,
                notes = command.notes,
                createdAtEpochMs = currentTimeProvider.nowEpochMillis()
            )
            val id = outbreakEventDao.insert(entity)
            AppResult.Success(
                OutbreakEvent(
                    id = id,
                    outbreakDescriptor = entity.outbreakDescriptor,
                    bodyLocation = entity.bodyLocation,
                    occurredAtEpochMs = entity.occurredAtEpochMs,
                    notes = entity.notes
                )
            )
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
                notes = command.notes
            )
            val updatedCount = outbreakEventDao.update(updatedEntity)
            if (updatedCount == 0) {
                return AppResult.Error(DomainError.IntegrityError("Outbreak record no longer exists."))
            }
            AppResult.Success(updatedEntity.toDomain())
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
            notes = notes
        )
    }
}
