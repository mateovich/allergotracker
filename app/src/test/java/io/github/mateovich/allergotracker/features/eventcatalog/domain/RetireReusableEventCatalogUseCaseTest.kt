package io.github.mateovich.allergotracker.features.eventcatalog.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RetireReusableEventCatalogUseCaseTest {

    @Test
    fun blocks_retirement_when_catalog_entry_has_active_references() = runBlocking {
        val repository = FakeRetireReusableEventCatalogRepository()
        val getImpact = GetReusableEventCatalogRetirementImpactUseCase(repository) { _, _ -> 4 }

        val result = RetireReusableEventCatalogUseCase(repository, getImpact)
            .invoke(ReusableEventCatalogKind.EXPOSURE_TYPE, 5L)

        assertTrue(result is AppResult.Error)
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun deletes_catalog_entry_when_no_references_remain() = runBlocking {
        val repository = FakeRetireReusableEventCatalogRepository()
        val getImpact = GetReusableEventCatalogRetirementImpactUseCase(repository) { _, _ -> 0 }

        val result = RetireReusableEventCatalogUseCase(repository, getImpact)
            .invoke(ReusableEventCatalogKind.EXPOSURE_TYPE, 5L)

        assertTrue(result is AppResult.Success)
        assertEquals(listOf(ReusableEventCatalogKind.EXPOSURE_TYPE to 5L), repository.deletedIds)
    }

    @Test
    fun returns_repository_error_when_delete_fails_inside_atomic_block() = runBlocking {
        val repository = FakeRetireReusableEventCatalogRepository(
            deleteResult = AppResult.Error(
                io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError(
                    "Couldn't retire saved value locally. Try again."
                )
            )
        )
        val getImpact = GetReusableEventCatalogRetirementImpactUseCase(repository) { _, _ -> 0 }

        val result = RetireReusableEventCatalogUseCase(repository, getImpact)
            .invoke(ReusableEventCatalogKind.EXPOSURE_TYPE, 5L)

        val error = result as AppResult.Error
        assertEquals("Couldn't retire saved value locally. Try again.", error.error.message)
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun rethrows_cancellation_when_repository_delete_is_cancelled() = runBlocking {
        val repository = FakeRetireReusableEventCatalogRepository(deleteCancellationMessage = "cancel event catalog")
        val getImpact = GetReusableEventCatalogRetirementImpactUseCase(repository) { _, _ -> 0 }
        val useCase = RetireReusableEventCatalogUseCase(repository, getImpact)

        try {
            useCase.invoke(ReusableEventCatalogKind.EXPOSURE_TYPE, 5L)
            fail("Expected cancellation to be rethrown")
        } catch (expected: kotlinx.coroutines.CancellationException) {
            assertEquals("cancel event catalog", expected.message)
            assertTrue(repository.deletedIds.isEmpty())
        }
    }
}

private class FakeRetireReusableEventCatalogRepository : ReusableEventCatalogRepository {
    constructor(
        deleteResult: AppResult<Unit> = AppResult.Success(Unit),
        deleteCancellationMessage: String? = null
    ) {
        this.deleteResult = deleteResult
        this.deleteCancellationMessage = deleteCancellationMessage
    }

    val deletedIds = mutableListOf<Pair<ReusableEventCatalogKind, Long>>()
    private var deleteResult: AppResult<Unit> = AppResult.Success(Unit)
    private var deleteCancellationMessage: String? = null

    override fun observeCatalog(kind: ReusableEventCatalogKind): Flow<List<ReusableEventCatalog>> = emptyFlow()

    override suspend fun getCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? {
        return ReusableEventCatalog(id, kind, "Pollen", 1, 1L, 1L, 1L)
    }

    override suspend fun existsCatalogEntry(kind: ReusableEventCatalogKind, id: Long): Boolean = true

    override suspend fun ensureCatalogEntry(kind: ReusableEventCatalogKind, name: String): ReusableEventCatalog = error("unused")

    override suspend fun touchCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? = null

    override suspend fun deleteCatalogEntry(kind: ReusableEventCatalogKind, id: Long): AppResult<Unit> {
        deleteCancellationMessage?.let { throw kotlinx.coroutines.CancellationException(it) }
        if (deleteResult is AppResult.Success) {
            deletedIds += kind to id
        }
        return deleteResult
    }
}
