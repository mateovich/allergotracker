package com.example.app.features.compounds.domain

import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test

class RetireReusableCompoundUseCaseTest {

    @Test
    fun blocks_retirement_when_nested_compounds_reference_target() = runBlocking {
        val repository = FakeRetireReusableCompoundRepository()
        val getImpact = GetReusableCompoundRetirementImpactUseCase(
            repository = repository,
            countIngestionReferences = { 1 },
            countChildCompoundReferences = { 2 }
        )

        val result = RetireReusableCompoundUseCase(repository, getImpact).invoke(9L)

        assertTrue(result is AppResult.Error)
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun detaches_ingestion_references_before_deleting_compound() = runBlocking {
        val repository = FakeRetireReusableCompoundRepository()
        var detached = 0
        val getImpact = GetReusableCompoundRetirementImpactUseCase(
            repository = repository,
            countIngestionReferences = { 2 },
            countChildCompoundReferences = { 0 }
        )

        val result = RetireReusableCompoundUseCase(
            repository = repository,
            getImpact = getImpact,
            detachIngestionReferences = {
                detached = 2
                2
            }
        ).invoke(9L)

        assertTrue(result is AppResult.Success)
        assertEquals(2, detached)
        assertEquals(listOf(9L), repository.deletedIds)
    }

    @Test
    fun returns_integrity_error_when_detached_rows_do_not_match_preview_count() = runBlocking {
        val repository = FakeRetireReusableCompoundRepository()
        val getImpact = GetReusableCompoundRetirementImpactUseCase(
            repository = repository,
            countIngestionReferences = { 2 },
            countChildCompoundReferences = { 0 }
        )

        val result = RetireReusableCompoundUseCase(
            repository = repository,
            getImpact = getImpact,
            detachIngestionReferences = { 1 }
        ).invoke(9L)

        val error = result as AppResult.Error
        assertEquals("Couldn't safely detach linked ingestion records. Try again.", error.error.message)
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun rethrows_cancellation_during_retirement() = runBlocking {
        val repository = FakeRetireReusableCompoundRepository()
        val getImpact = GetReusableCompoundRetirementImpactUseCase(
            repository = repository,
            countIngestionReferences = { 1 },
            countChildCompoundReferences = { 0 }
        )
        val useCase = RetireReusableCompoundUseCase(
            repository = repository,
            getImpact = getImpact,
            detachIngestionReferences = { throw kotlinx.coroutines.CancellationException("cancel compound") }
        )

        try {
            useCase.invoke(9L)
            fail("Expected cancellation to be rethrown")
        } catch (expected: kotlinx.coroutines.CancellationException) {
            assertEquals("cancel compound", expected.message)
            assertTrue(repository.deletedIds.isEmpty())
        }
    }
}

private class FakeRetireReusableCompoundRepository : ReusableCompoundRepository {
    val deletedIds = mutableListOf<Long>()

    override suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound> = error("unused")

    override suspend fun getCompound(id: Long): AppResult<ReusableCompound> {
        return AppResult.Success(
            ReusableCompound(id, "Breakfast mix", null, CompoundCompositionStatus.DRAFT, emptyList(), 1L, 1L)
        )
    }

    override suspend fun deleteCompound(id: Long): AppResult<Unit> {
        deletedIds += id
        return AppResult.Success(Unit)
    }

    override fun observeCompounds(): Flow<List<ReusableCompound>> = emptyFlow()
}
