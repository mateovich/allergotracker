package com.example.app.features.eventcatalog.domain

import com.example.app.core.common.result.AppResult
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
        AppResult.Error(com.example.app.core.common.error.DomainError.PersistenceError("Unsupported operation."))

    suspend fun deleteCatalogEntry(
        kind: ReusableEventCatalogKind,
        id: Long
    ): AppResult<Unit> =
        AppResult.Error(com.example.app.core.common.error.DomainError.PersistenceError("Unsupported operation."))
}
