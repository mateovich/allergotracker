package com.example.app.features.eventcatalog.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class GetReusableEventCatalogEntryUseCaseTest {

    @Test
    fun missing_entry_returns_integrity_error() = runBlocking {
        val useCase = GetReusableEventCatalogEntryUseCase(FakeGetReusableEventCatalogRepository())

        val result = useCase(ReusableEventCatalogKind.EXPOSURE_TYPE, 5L)

        val error = (result as AppResult.Error).error as DomainError.IntegrityError
        assertEquals("Saved value is no longer available.", error.message)
    }

    @Test
    fun cancellation_is_rethrown_instead_of_mapped_to_persistence_error() = runBlocking {
        val useCase = GetReusableEventCatalogEntryUseCase(
            FakeGetReusableEventCatalogRepository(getFailure = CancellationException("cancel"))
        )

        try {
            useCase(ReusableEventCatalogKind.EXPOSURE_TYPE, 5L)
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }
    }
}

private class FakeGetReusableEventCatalogRepository(
    private val getFailure: Exception? = null
) : ReusableEventCatalogRepository {
    override fun observeCatalog(kind: ReusableEventCatalogKind): Flow<List<ReusableEventCatalog>> = emptyFlow()

    override suspend fun getCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? {
        getFailure?.let { throw it }
        return null
    }

    override suspend fun existsCatalogEntry(kind: ReusableEventCatalogKind, id: Long): Boolean = false

    override suspend fun ensureCatalogEntry(kind: ReusableEventCatalogKind, name: String): ReusableEventCatalog {
        error("unused")
    }

    override suspend fun touchCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? = null
}
