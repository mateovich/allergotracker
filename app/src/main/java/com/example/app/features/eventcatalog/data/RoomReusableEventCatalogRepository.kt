package com.example.app.features.eventcatalog.data

import com.example.app.features.eventcatalog.domain.ReusableEventCatalog
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogRepository
import com.example.app.features.eventcatalog.domain.canonicalizeReusableEventCatalogName
import com.example.app.features.eventcatalog.domain.normalizeReusableEventCatalogName
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
