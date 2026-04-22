package io.github.mateovich.allergotracker.features.ingredients.domain

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class UpdateReusableIngredientUseCaseTest {

    @Test
    fun mismatch_between_reference_count_and_synced_rows_returns_integrity_error() = runBlocking {
        val useCase = UpdateReusableIngredientUseCase(
            repository = FakeUpdateReusableIngredientRepository(),
            syncReferences = { _, _ -> 0 },
            countReferences = { 1 }
        )

        val result = useCase(UpdateReusableIngredientCommand(7L, "Oat milk plus", null))

        val error = (result as AppResult.Error).error as DomainError.IntegrityError
        assertEquals("Couldn't keep linked ingredient records in sync. Try again.", error.message)
    }

    @Test
    fun sync_exception_is_mapped_to_persistence_error() = runBlocking {
        val useCase = UpdateReusableIngredientUseCase(
            repository = FakeUpdateReusableIngredientRepository(),
            syncReferences = { _, _ -> throw IllegalStateException("boom") },
            countReferences = { 1 }
        )

        val result = useCase(UpdateReusableIngredientCommand(7L, "Oat milk plus", null))

        val error = (result as AppResult.Error).error as DomainError.PersistenceError
        assertEquals("Couldn't update ingredient locally. Try again.", error.message)
    }

    @Test
    fun cancellation_is_rethrown_instead_of_mapped_to_persistence_error() = runBlocking {
        val useCase = UpdateReusableIngredientUseCase(
            repository = FakeUpdateReusableIngredientRepository(),
            syncReferences = { _, _ -> throw CancellationException("cancel") },
            countReferences = { 1 }
        )

        try {
            useCase(UpdateReusableIngredientCommand(7L, "Oat milk plus", null))
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }
    }
}

private class FakeUpdateReusableIngredientRepository : ReusableIngredientRepository {
    override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> = error("unused")

    override suspend fun updateIngredient(command: UpdateReusableIngredientCommand): AppResult<ReusableIngredient> {
        return AppResult.Success(
            ReusableIngredient(
                id = command.id,
                name = command.name,
                notes = command.notes,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 2L
            )
        )
    }

    override fun observeIngredients(): Flow<List<ReusableIngredient>> = emptyFlow()
}
