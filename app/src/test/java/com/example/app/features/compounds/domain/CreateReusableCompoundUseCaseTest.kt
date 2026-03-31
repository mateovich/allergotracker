package com.example.app.features.compounds.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateReusableCompoundUseCaseTest {

    @Test
    fun returns_field_error_when_name_is_blank() = runBlocking {
        val repository = FakeReusableCompoundRepository()
        val useCase = CreateReusableCompoundUseCase(repository)

        val result = useCase(CreateReusableCompoundCommand(name = "   ", notes = null))

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Enter a compound name.", error.fieldErrors["name"])
        assertTrue(repository.createdCommands.isEmpty())
    }

    @Test
    fun creates_draft_compound_with_normalized_name_for_future_composition_work() = runBlocking {
        val repository = FakeReusableCompoundRepository()
        val useCase = CreateReusableCompoundUseCase(repository)

        val result = useCase(CreateReusableCompoundCommand(name = "  Breakfast   mix  ", notes = "  base  "))

        val success = result as AppResult.Success<ReusableCompound>
        assertEquals("Breakfast mix", success.value.name)
        assertEquals(CompoundCompositionStatus.DRAFT, success.value.compositionStatus)
        assertTrue(success.value.components.isEmpty())
    }
}

private class FakeReusableCompoundRepository : ReusableCompoundRepository {
    val createdCommands = mutableListOf<CreateReusableCompoundCommand>()

    override suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound> {
        createdCommands += command
        return AppResult.Success(
            ReusableCompound(
                id = 1L,
                name = command.name,
                notes = command.notes,
                compositionStatus = CompoundCompositionStatus.DRAFT,
                components = emptyList(),
                createdAtEpochMs = 10L,
                updatedAtEpochMs = 10L
            )
        )
    }

    override fun observeCompounds(): Flow<List<ReusableCompound>> = emptyFlow()
}
