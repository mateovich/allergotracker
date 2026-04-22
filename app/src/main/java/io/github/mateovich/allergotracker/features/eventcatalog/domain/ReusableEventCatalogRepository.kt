package io.github.mateovich.allergotracker.features.eventcatalog.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

interface ReusableEventCatalogRepository {
    fun observeCatalog(kind: ReusableEventCatalogKind): Flow<List<ReusableEventCatalog>>

    suspend fun getCatalogEntry(
        kind: ReusableEventCatalogKind,
        id: Long
    ): ReusableEventCatalog?

    suspend fun existsCatalogEntry(
        kind: ReusableEventCatalogKind,
        id: Long
    ): Boolean

    suspend fun ensureCatalogEntry(
        kind: ReusableEventCatalogKind,
        name: String
    ): ReusableEventCatalog

    suspend fun touchCatalogEntry(
        kind: ReusableEventCatalogKind,
        id: Long
    ): ReusableEventCatalog?

    suspend fun updateCatalogEntry(
        kind: ReusableEventCatalogKind,
        id: Long,
        name: String
    ): AppResult<ReusableEventCatalog> =
        AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError("Unsupported operation."))

    suspend fun deleteCatalogEntry(
        kind: ReusableEventCatalogKind,
        id: Long
    ): AppResult<Unit> =
        AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError("Unsupported operation."))
}
