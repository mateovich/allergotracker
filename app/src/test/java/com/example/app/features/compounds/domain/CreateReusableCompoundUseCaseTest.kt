package com.example.app.features.compounds.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.domain.CompoundComponentKind.COMPOUND
import com.example.app.features.compounds.domain.CompoundComponentKind.INGREDIENT
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

        val result = useCase(
            CreateReusableCompoundCommand(
                name = "   ",
                notes = null,
                components = listOf(validIngredientComponent())
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Enter a compound name.", error.fieldErrors["name"])
        assertTrue(repository.createdCommands.isEmpty())
    }

    @Test
    fun returns_field_error_when_component_list_is_empty() = runBlocking {
        val repository = FakeReusableCompoundRepository()
        val useCase = CreateReusableCompoundUseCase(repository)

        val result = useCase(
            CreateReusableCompoundCommand(
                name = "Breakfast mix",
                notes = null,
                components = emptyList()
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Add at least one component.", error.fieldErrors["components"])
        assertTrue(repository.createdCommands.isEmpty())
    }

    @Test
    fun returns_field_error_when_component_proportion_is_missing_or_zero() = runBlocking {
        val repository = FakeReusableCompoundRepository()
        val useCase = CreateReusableCompoundUseCase(repository)

        val result = useCase(
            CreateReusableCompoundCommand(
                name = "Breakfast mix",
                notes = null,
                components = listOf(
                    CreateReusableCompoundComponentCommand(
                        kind = INGREDIENT,
                        reusableEntityId = 7L,
                        proportionBasisPoints = 0
                    )
                )
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Enter a proportion greater than 0%.", error.fieldErrors["components[0].proportion"])
        assertTrue(repository.createdCommands.isEmpty())
    }

    @Test
    fun returns_field_error_when_total_proportion_is_not_100_percent() = runBlocking {
        val repository = FakeReusableCompoundRepository()
        val useCase = CreateReusableCompoundUseCase(repository)

        val result = useCase(
            CreateReusableCompoundCommand(
                name = "Breakfast mix",
                notes = null,
                components = listOf(
                    validIngredientComponent(proportionBasisPoints = 6_000),
                    validCompoundComponent(proportionBasisPoints = 2_000)
                )
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Total proportion must equal 100%.", error.fieldErrors["componentsTotal"])
        assertTrue(repository.createdCommands.isEmpty())
    }

    @Test
    fun creates_complete_compound_with_normalized_name_and_ordered_components() = runBlocking {
        val repository = FakeReusableCompoundRepository()
        val useCase = CreateReusableCompoundUseCase(repository)

        val result = useCase(
            CreateReusableCompoundCommand(
                name = "  Breakfast   mix  ",
                notes = "  base  ",
                components = listOf(
                    validIngredientComponent(proportionBasisPoints = 6_000),
                    validCompoundComponent(proportionBasisPoints = 4_000)
                )
            )
        )

        val success = result as AppResult.Success<ReusableCompound>
        assertEquals("Breakfast mix", success.value.name)
        assertEquals(CompoundCompositionStatus.COMPLETE, success.value.compositionStatus)
        assertEquals(2, success.value.components.size)
        assertEquals("base", repository.createdCommands.single().notes)
        assertEquals(6_000, repository.createdCommands.single().components.first().proportionBasisPoints)
    }
}

private fun validIngredientComponent(
    proportionBasisPoints: Int = 10_000
): CreateReusableCompoundComponentCommand {
    return CreateReusableCompoundComponentCommand(
        kind = INGREDIENT,
        reusableEntityId = 7L,
        proportionBasisPoints = proportionBasisPoints
    )
}

private fun validCompoundComponent(
    proportionBasisPoints: Int = 4_000
): CreateReusableCompoundComponentCommand {
    return CreateReusableCompoundComponentCommand(
        kind = COMPOUND,
        reusableEntityId = 9L,
        proportionBasisPoints = proportionBasisPoints
    )
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
                compositionStatus = CompoundCompositionStatus.COMPLETE,
                components = command.components.mapIndexed { index, component ->
                    ReusableCompoundComponent(
                        id = index + 1L,
                        position = index,
                        kind = component.kind,
                        ingredientId = component.reusableEntityId.takeIf { component.kind == INGREDIENT },
                        childCompoundId = component.reusableEntityId.takeIf { component.kind == COMPOUND },
                        proportionBasisPoints = component.proportionBasisPoints
                    )
                },
                createdAtEpochMs = 10L,
                updatedAtEpochMs = 10L
            )
        )
    }

    override fun observeCompounds(): Flow<List<ReusableCompound>> = emptyFlow()
}
