package com.example.app.features.eventcatalog.domain

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
}
