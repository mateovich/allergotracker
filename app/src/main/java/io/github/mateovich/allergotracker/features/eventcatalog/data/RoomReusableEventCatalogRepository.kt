package io.github.mateovich.allergotracker.features.eventcatalog.data

import android.database.sqlite.SQLiteConstraintException
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalog
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogKind
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogRepository
import io.github.mateovich.allergotracker.features.eventcatalog.domain.canonicalizeReusableEventCatalogName
import io.github.mateovich.allergotracker.features.eventcatalog.domain.normalizeReusableEventCatalogName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomReusableEventCatalogRepository(
    private val eventCatalogDao: ReusableEventCatalogDao,
    private val nowEpochMillis: () -> Long
) : ReusableEventCatalogRepository {
    override fun observeCatalog(kind: ReusableEventCatalogKind): Flow<List<ReusableEventCatalog>> {
        return eventCatalogDao.observeByKind(kind.storageValue).map { entities ->
            entities.map(ReusableEventCatalogEntity::toDomain)
        }
    }

    override suspend fun getCatalogEntry(
        kind: ReusableEventCatalogKind,
        id: Long
    ): ReusableEventCatalog? {
        return eventCatalogDao.findById(kind.storageValue, id)?.toDomain()
    }

    override suspend fun existsCatalogEntry(
        kind: ReusableEventCatalogKind,
        id: Long
    ): Boolean {
        return eventCatalogDao.existsById(kind.storageValue, id)
    }

    override suspend fun ensureCatalogEntry(
        kind: ReusableEventCatalogKind,
        name: String
    ): ReusableEventCatalog {
        val canonicalName = canonicalizeReusableEventCatalogName(name)
        val normalizedName = normalizeReusableEventCatalogName(name)
        val now = nowEpochMillis()
        val existing = eventCatalogDao.findByNormalizedName(kind.storageValue, normalizedName)
        if (existing != null) {
            val updated = existing.copy(
                name = canonicalName,
                usageCount = existing.usageCount + 1,
                updatedAtEpochMs = now,
                lastUsedAtEpochMs = now
            )
            eventCatalogDao.update(updated)
            return updated.toDomain()
        }

        val entity = ReusableEventCatalogEntity(
            kind = kind.storageValue,
            name = canonicalName,
            normalizedName = normalizedName,
            usageCount = 1,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            lastUsedAtEpochMs = now
        )
        val id = eventCatalogDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    override suspend fun touchCatalogEntry(
        kind: ReusableEventCatalogKind,
        id: Long
    ): ReusableEventCatalog? {
        val existing = eventCatalogDao.findById(kind.storageValue, id) ?: return null
        val now = nowEpochMillis()
        val updated = existing.copy(
            usageCount = existing.usageCount + 1,
            updatedAtEpochMs = now,
            lastUsedAtEpochMs = now
        )
        eventCatalogDao.update(updated)
        return updated.toDomain()
    }

    override suspend fun updateCatalogEntry(
        kind: ReusableEventCatalogKind,
        id: Long,
        name: String
    ): AppResult<ReusableEventCatalog> {
        return try {
            val existing = eventCatalogDao.findById(kind.storageValue, id)
                ?: return AppResult.Error(DomainError.IntegrityError("Saved value is no longer available."))
            val canonicalName = canonicalizeReusableEventCatalogName(name)
            val normalizedName = normalizeReusableEventCatalogName(name)
            eventCatalogDao.findByNormalizedName(kind.storageValue, normalizedName)?.let { conflict ->
                if (conflict.id != id) {
                    return AppResult.Error(
                        DomainError.ValidationError(
                            message = "Please fix the highlighted fields.",
                            fieldErrors = mapOf("name" to duplicateNameMessage(kind))
                        )
                    )
                }
            }
            val updated = existing.copy(
                name = canonicalName,
                normalizedName = normalizedName,
                updatedAtEpochMs = nowEpochMillis()
            )
            if (eventCatalogDao.update(updated) == 0) {
                return AppResult.Error(DomainError.IntegrityError("Saved value is no longer available."))
            }
            AppResult.Success(updated.toDomain())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't update saved value locally. Try again."))
        }
    }

    override suspend fun deleteCatalogEntry(
        kind: ReusableEventCatalogKind,
        id: Long
    ): AppResult<Unit> {
        return try {
            if (eventCatalogDao.deleteById(kind.storageValue, id) == 0) {
                AppResult.Error(DomainError.IntegrityError("Saved value is no longer available."))
            } else {
                AppResult.Success(Unit)
            }
        } catch (_: SQLiteConstraintException) {
            AppResult.Error(
                DomainError.IntegrityError(
                    "This saved value is still linked to other records. Replace or clear those references before retiring it."
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppResult.Error(DomainError.PersistenceError("Couldn't retire saved value locally. Try again."))
        }
    }
}

private fun duplicateNameMessage(kind: ReusableEventCatalogKind): String {
    return when (kind) {
        ReusableEventCatalogKind.EXPOSURE_TYPE -> "A saved exposure type with this name already exists."
        ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> "A saved outbreak descriptor with this name already exists."
        ReusableEventCatalogKind.BODY_LOCATION -> "A saved body location with this name already exists."
    }
}

private fun ReusableEventCatalogEntity.toDomain(): ReusableEventCatalog {
    return ReusableEventCatalog(
        id = id,
        kind = ReusableEventCatalogKind.fromStorageValue(kind)
            ?: error("Unknown reusable event catalog kind: $kind"),
        name = name,
        usageCount = usageCount,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        lastUsedAtEpochMs = lastUsedAtEpochMs
    )
}
