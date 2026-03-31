package com.example.app.features.ingestion.data

import android.database.sqlite.SQLiteConstraintException
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.CreateIngestionEventCommand
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.IngestionEvent
import com.example.app.features.ingestion.domain.IngestionRepository
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.ingestion.domain.UpdateIngestionEventCommand

/** Local source of truth for ingestion events backed by Room only. */
class RoomIngestionRepository(
    private val ingestionEventDao: IngestionEventDao,
    private val currentTimeProvider: CurrentTimeProvider,
    private val reusableEntityExists: suspend (ReusableEntityKind, Long) -> Boolean = { _, _ -> true }
) : IngestionRepository {
    override suspend fun createIngestion(command: CreateIngestionEventCommand): AppResult<IngestionEvent> {
        return try {
            if (!command.hasConsistentReusableIdentity()) {
                return AppResult.Error(DomainError.ValidationError("Please fix the highlighted fields.", mapOf("itemName" to "Choose a valid reusable item or clear the selection.")))
            }
            if (!command.reusableReferenceExists(reusableEntityExists)) {
                return AppResult.Error(DomainError.IntegrityError("Selected reusable item is no longer available."))
            }
            val entity = IngestionEventEntity(
                itemName = command.itemName,
                quantityLevel = command.quantityLevel ?: 0,
                occurredAtEpochMs = command.occurredAtEpochMs,
                notes = command.notes,
                reusableEntityKind = command.reusableEntityKind?.storageValue,
                reusableEntityId = command.reusableEntityId,
                createdAtEpochMs = currentTimeProvider.nowEpochMillis()
            )
            val id = ingestionEventDao.insert(entity)
            AppResult.Success(
                IngestionEvent(
                    id = id,
                    itemName = entity.itemName,
                    quantityLevel = entity.quantityLevel,
                    occurredAtEpochMs = entity.occurredAtEpochMs,
                    notes = entity.notes,
                    reusableEntityKind = ReusableEntityKind.fromStorageValue(entity.reusableEntityKind),
                    reusableEntityId = entity.reusableEntityId
                )
            )
        } catch (_: Exception) {
            AppResult.Error(
                DomainError.PersistenceError("Couldn't save ingestion locally. Try again.")
            )
        }
    }

    override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> {
        return try {
            val entity = ingestionEventDao.getById(id)
                ?: return AppResult.Error(DomainError.IntegrityError("Ingestion record no longer exists."))
            AppResult.Success(entity.toDomain())
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't load ingestion locally. Try again."))
        }
    }

    override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
        return try {
            if (!command.hasConsistentReusableIdentity()) {
                return AppResult.Error(DomainError.ValidationError("Please fix the highlighted fields.", mapOf("itemName" to "Choose a valid reusable item or clear the selection.")))
            }
            if (!command.reusableReferenceExists(reusableEntityExists)) {
                return AppResult.Error(DomainError.IntegrityError("Selected reusable item is no longer available."))
            }
            val currentEntity = ingestionEventDao.getById(command.id)
                ?: return AppResult.Error(DomainError.IntegrityError("Ingestion record no longer exists."))
            val updatedEntity = currentEntity.copy(
                itemName = command.itemName,
                quantityLevel = command.quantityLevel ?: 0,
                occurredAtEpochMs = command.occurredAtEpochMs,
                notes = command.notes,
                reusableEntityKind = command.reusableEntityKind?.storageValue,
                reusableEntityId = command.reusableEntityId
            )
            val updatedCount = ingestionEventDao.update(updatedEntity)
            if (updatedCount == 0) {
                return AppResult.Error(DomainError.IntegrityError("Ingestion record no longer exists."))
            }
            AppResult.Success(updatedEntity.toDomain())
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't update ingestion locally. Try again."))
        }
    }

    override suspend fun deleteIngestion(id: Long): AppResult<Unit> {
        return try {
            ingestionEventDao.getById(id)
                ?: return AppResult.Error(DomainError.IntegrityError("Ingestion record no longer exists."))
            val deletedCount = ingestionEventDao.deleteById(id)
            if (deletedCount == 0) {
                return AppResult.Error(DomainError.IntegrityError("Ingestion record no longer exists."))
            }
            AppResult.Success(Unit)
        } catch (_: SQLiteConstraintException) {
            AppResult.Error(DomainError.IntegrityError("Ingestion record could not be deleted safely."))
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't delete ingestion locally. Try again."))
        }
    }

    private fun IngestionEventEntity.toDomain(): IngestionEvent {
        return IngestionEvent(
            id = id,
            itemName = itemName,
            quantityLevel = quantityLevel,
            occurredAtEpochMs = occurredAtEpochMs,
            notes = notes,
            reusableEntityKind = ReusableEntityKind.fromStorageValue(reusableEntityKind),
            reusableEntityId = reusableEntityId
        )
    }
}

private fun CreateIngestionEventCommand.hasConsistentReusableIdentity(): Boolean {
    return (reusableEntityKind == null) == (reusableEntityId == null) && (reusableEntityId == null || reusableEntityId > 0L)
}

private fun UpdateIngestionEventCommand.hasConsistentReusableIdentity(): Boolean {
    return (reusableEntityKind == null) == (reusableEntityId == null) && (reusableEntityId == null || reusableEntityId > 0L)
}

private suspend fun CreateIngestionEventCommand.reusableReferenceExists(
    reusableEntityExists: suspend (ReusableEntityKind, Long) -> Boolean
): Boolean {
    val kind = reusableEntityKind ?: return true
    val id = reusableEntityId ?: return true
    return reusableEntityExists(kind, id)
}

private suspend fun UpdateIngestionEventCommand.reusableReferenceExists(
    reusableEntityExists: suspend (ReusableEntityKind, Long) -> Boolean
): Boolean {
    val kind = reusableEntityKind ?: return true
    val id = reusableEntityId ?: return true
    return reusableEntityExists(kind, id)
}
