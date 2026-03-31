package com.example.app.features.eventcatalog.data

import com.example.app.features.eventcatalog.domain.ReusableEventCatalog
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogRepository
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
        val trimmedName = name.trim()
        val normalizedName = trimmedName.normalizedKey()
        val now = nowEpochMillis()
        val existing = eventCatalogDao.findByNormalizedName(kind.storageValue, normalizedName)
        if (existing != null) {
            val updated = existing.copy(
                usageCount = existing.usageCount + 1,
                updatedAtEpochMs = now,
                lastUsedAtEpochMs = now
            )
            eventCatalogDao.update(updated)
            return updated.toDomain()
        }

        val entity = ReusableEventCatalogEntity(
            kind = kind.storageValue,
            name = trimmedName,
            normalizedName = normalizedName,
            usageCount = 1,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            lastUsedAtEpochMs = now
        )
        val id = eventCatalogDao.insert(entity)
        return entity.copy(id = id).toDomain()
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

private fun String.normalizedKey(): String = trim().lowercase()
