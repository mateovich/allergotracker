package com.example.app.features.ingredients.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateReusableIngredientUseCaseTest {

    @Test
    fun returns_field_error_when_name_is_blank() = runBlocking {
        val repository = FakeReusableIngredientRepository()
        val useCase = CreateReusableIngredientUseCase(repository)

        val result = useCase(CreateReusableIngredientCommand(name = "   ", notes = "note"))

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Enter an ingredient name.", error.fieldErrors["name"])
        assertTrue(repository.createdCommands.isEmpty())
    }

    @Test
    fun trims_and_collapses_internal_whitespace_before_persisting() = runBlocking {
        val repository = FakeReusableIngredientRepository()
        val useCase = CreateReusableIngredientUseCase(repository)

        val result = useCase(CreateReusableIngredientCommand(name = "  Oat   milk  ", notes = "  staple  "))

        val success = result as AppResult.Success<ReusableIngredient>
        assertEquals("Oat milk", success.value.name)
        assertEquals("staple", repository.createdCommands.single().notes)
    }
}

private class FakeReusableIngredientRepository : ReusableIngredientRepository {
    val createdCommands = mutableListOf<CreateReusableIngredientCommand>()

    override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> {
        createdCommands += command
        return AppResult.Success(
            ReusableIngredient(
                id = 1L,
                name = command.name,
                notes = command.notes,
                createdAtEpochMs = 10L,
                updatedAtEpochMs = 10L
            )
        )
    }

    override fun observeIngredients(): Flow<List<ReusableIngredient>> = emptyFlow()
}
