package com.example.app.features.eventcatalog.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UpdateReusableEventCatalogUseCaseTest {

    @Test
    fun trims_and_syncs_references_after_successful_update() = runBlocking {
        val repository = FakeUpdateReusableEventCatalogRepository()
        var synced: Triple<ReusableEventCatalogKind, Long, String>? = null
        val useCase = UpdateReusableEventCatalogUseCase(
            repository = repository,
            syncReferences = { kind, id, name ->
                synced = Triple(kind, id, name)
                0
            }
        )

        val result = useCase(ReusableEventCatalogKind.EXPOSURE_TYPE, 5L, "  Left   arm  ")

        assertEquals("Left arm", (result as AppResult.Success).value.name)
        assertEquals(Triple(ReusableEventCatalogKind.EXPOSURE_TYPE, 5L, "Left arm"), synced)
    }

    @Test
    fun blank_name_returns_field_error() = runBlocking {
        val useCase = UpdateReusableEventCatalogUseCase(FakeUpdateReusableEventCatalogRepository())

        val result = useCase(ReusableEventCatalogKind.BODY_LOCATION, 5L, "   ")

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Enter where on the body it happened.", error.fieldErrors["name"])
        assertTrue(error.message.isNotBlank())
    }

    @Test
    fun mismatch_between_reference_count_and_synced_rows_returns_integrity_error() = runBlocking {
        val useCase = UpdateReusableEventCatalogUseCase(
            repository = FakeUpdateReusableEventCatalogRepository(),
            syncReferences = { _, _, _ -> 0 },
            countReferences = { _, _ -> 1 }
        )

        val result = useCase(ReusableEventCatalogKind.EXPOSURE_TYPE, 5L, "Dust")

        val error = (result as AppResult.Error).error as DomainError.IntegrityError
        assertEquals("Couldn't keep linked saved values in sync. Try again.", error.message)
    }

    @Test
    fun sync_exception_is_mapped_to_persistence_error() = runBlocking {
        val useCase = UpdateReusableEventCatalogUseCase(
            repository = FakeUpdateReusableEventCatalogRepository(),
            syncReferences = { _, _, _ -> throw IllegalStateException("boom") },
            countReferences = { _, _ -> 1 }
        )

        val result = useCase(ReusableEventCatalogKind.EXPOSURE_TYPE, 5L, "Dust")

        val error = (result as AppResult.Error).error as DomainError.PersistenceError
        assertEquals("Couldn't update saved value locally. Try again.", error.message)
    }

    @Test
    fun cancellation_is_rethrown_instead_of_mapped_to_persistence_error() = runBlocking {
        val useCase = UpdateReusableEventCatalogUseCase(
            repository = FakeUpdateReusableEventCatalogRepository(),
            syncReferences = { _, _, _ -> throw CancellationException("cancel") },
            countReferences = { _, _ -> 1 }
        )

        try {
            useCase(ReusableEventCatalogKind.EXPOSURE_TYPE, 5L, "Dust")
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }
    }
}

private class FakeUpdateReusableEventCatalogRepository : ReusableEventCatalogRepository {
    override fun observeCatalog(kind: ReusableEventCatalogKind): Flow<List<ReusableEventCatalog>> = emptyFlow()

    override suspend fun getCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? = null

    override suspend fun existsCatalogEntry(kind: ReusableEventCatalogKind, id: Long): Boolean = true

    override suspend fun ensureCatalogEntry(kind: ReusableEventCatalogKind, name: String): ReusableEventCatalog {
        error("unused")
    }

    override suspend fun touchCatalogEntry(kind: ReusableEventCatalogKind, id: Long): ReusableEventCatalog? = null

    override suspend fun updateCatalogEntry(
        kind: ReusableEventCatalogKind,
        id: Long,
        name: String
    ): AppResult<ReusableEventCatalog> {
        return AppResult.Success(
            ReusableEventCatalog(
                id = id,
                kind = kind,
                name = name,
                usageCount = 1,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 2L,
                lastUsedAtEpochMs = 1L
            )
        )
    }
}
