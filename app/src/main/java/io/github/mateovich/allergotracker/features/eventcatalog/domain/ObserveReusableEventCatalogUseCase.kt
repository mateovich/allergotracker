package io.github.mateovich.allergotracker.features.eventcatalog.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ObserveReusableEventCatalogUseCase(
    private val repository: ReusableEventCatalogRepository
) {
    operator fun invoke(kind: ReusableEventCatalogKind): Flow<List<ReusableEventCatalog>> {
        return repository.observeCatalog(kind)
    }
}

fun emptyObserveReusableEventCatalogUseCase(): ObserveReusableEventCatalogUseCase {
    return ObserveReusableEventCatalogUseCase(EmptyReusableEventCatalogRepository)
}

private object EmptyReusableEventCatalogRepository : ReusableEventCatalogRepository {
    override fun observeCatalog(kind: ReusableEventCatalogKind): Flow<List<ReusableEventCatalog>> = flowOf(emptyList())

    override suspend fun getCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? = null

    override suspend fun existsCatalogEntry(kind: ReusableEventCatalogKind, id: Long): Boolean = false

    override suspend fun ensureCatalogEntry(kind: ReusableEventCatalogKind, name: String): ReusableEventCatalog {
        error("unused")
    }

    override suspend fun touchCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? = null

    override suspend fun updateCatalogEntry(
        kind: ReusableEventCatalogKind,
        id: Long,
        name: String
    ): AppResult<ReusableEventCatalog> {
        error("unused")
    }
}
