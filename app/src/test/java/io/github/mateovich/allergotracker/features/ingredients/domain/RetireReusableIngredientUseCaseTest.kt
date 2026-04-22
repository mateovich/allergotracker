package io.github.mateovich.allergotracker.features.ingredients.domain

import io.github.mateovich.allergotracker.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test

class RetireReusableIngredientUseCaseTest {

    @Test
    fun blocks_retirement_when_compound_components_still_reference_ingredient() = runBlocking {
        val repository = FakeRetireReusableIngredientRepository()
        val getImpact = GetReusableIngredientRetirementImpactUseCase(
            repository = repository,
            countIngestionReferences = { 1 },
            countCompoundReferences = { 2 }
        )

        val result = RetireReusableIngredientUseCase(repository, getImpact).invoke(7L)

        assertTrue(result is AppResult.Error)
        assertEquals(0, repository.deletedIds.size)
    }

    @Test
    fun detaches_ingestion_references_before_deleting_ingredient() = runBlocking {
        val repository = FakeRetireReusableIngredientRepository()
        var detached = 0
        val getImpact = GetReusableIngredientRetirementImpactUseCase(
            repository = repository,
            countIngestionReferences = { 3 },
            countCompoundReferences = { 0 }
        )

        val result = RetireReusableIngredientUseCase(
            repository = repository,
            getImpact = getImpact,
            detachIngestionReferences = {
                detached = 3
                3
            }
        ).invoke(7L)

        assertTrue(result is AppResult.Success)
        assertEquals(3, detached)
        assertEquals(listOf(7L), repository.deletedIds)
    }

    @Test
    fun returns_integrity_error_when_detached_rows_do_not_match_preview_count() = runBlocking {
        val repository = FakeRetireReusableIngredientRepository()
        val getImpact = GetReusableIngredientRetirementImpactUseCase(
            repository = repository,
            countIngestionReferences = { 3 },
            countCompoundReferences = { 0 }
        )

        val result = RetireReusableIngredientUseCase(
            repository = repository,
            getImpact = getImpact,
            detachIngestionReferences = { 2 }
        ).invoke(7L)

        val error = result as AppResult.Error
        assertEquals("Couldn't safely detach linked ingestion records. Try again.", error.error.message)
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun rethrows_cancellation_during_retirement() = runBlocking {
        val repository = FakeRetireReusableIngredientRepository()
        val getImpact = GetReusableIngredientRetirementImpactUseCase(
            repository = repository,
            countIngestionReferences = { 1 },
            countCompoundReferences = { 0 }
        )
        val useCase = RetireReusableIngredientUseCase(
            repository = repository,
            getImpact = getImpact,
            detachIngestionReferences = { throw kotlinx.coroutines.CancellationException("cancel ingredient") }
        )

        try {
            useCase.invoke(7L)
            fail("Expected cancellation to be rethrown")
        } catch (expected: kotlinx.coroutines.CancellationException) {
            assertEquals("cancel ingredient", expected.message)
            assertTrue(repository.deletedIds.isEmpty())
        }
    }
}

private class FakeRetireReusableIngredientRepository : ReusableIngredientRepository {
    val deletedIds = mutableListOf<Long>()

    override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> = error("unused")

    override suspend fun getIngredient(id: Long): AppResult<ReusableIngredient> {
        return AppResult.Success(ReusableIngredient(id, "Oat milk", null, 1L, 1L))
    }

    override suspend fun deleteIngredient(id: Long): AppResult<Unit> {
        deletedIds += id
        return AppResult.Success(Unit)
    }

    override fun observeIngredients(): Flow<List<ReusableIngredient>> = emptyFlow()
}
