package com.example.app.features.compounds.domain

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

class UpdateReusableCompoundUseCaseTest {

    @Test
    fun invalid_total_returns_validation_error_before_persistence() = runBlocking {
        val repository = FakeUpdateReusableCompoundRepository()
        val useCase = UpdateReusableCompoundUseCase(repository)

        val result = useCase(
            UpdateReusableCompoundCommand(
                id = 9L,
                name = "Breakfast mix plus",
                notes = null,
                components = listOf(
                    UpdateReusableCompoundComponentCommand(
                        kind = CompoundComponentKind.INGREDIENT,
                        reusableEntityId = 7L,
                        proportionBasisPoints = 6_000
                    )
                )
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Total proportion must equal 100%.", error.fieldErrors["componentsTotal"])
        assertEquals(0, repository.updatedCommands.size)
    }

    @Test
    fun direct_self_reference_returns_field_error() = runBlocking {
        val repository = FakeUpdateReusableCompoundRepository()
        val useCase = UpdateReusableCompoundUseCase(repository)

        val result = useCase(
            UpdateReusableCompoundCommand(
                id = 9L,
                name = "Breakfast mix plus",
                notes = null,
                components = listOf(
                    UpdateReusableCompoundComponentCommand(
                        kind = CompoundComponentKind.COMPOUND,
                        reusableEntityId = 9L,
                        proportionBasisPoints = 10_000
                    )
                )
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("A compound cannot include itself.", error.fieldErrors["components[0].target"])
        assertEquals(0, repository.updatedCommands.size)
    }

    @Test
    fun duplicate_component_returns_field_error_before_persistence() = runBlocking {
        val repository = FakeUpdateReusableCompoundRepository()
        val useCase = UpdateReusableCompoundUseCase(repository)

        val result = useCase(
            UpdateReusableCompoundCommand(
                id = 9L,
                name = "Breakfast mix plus",
                notes = null,
                components = listOf(
                    UpdateReusableCompoundComponentCommand(
                        kind = CompoundComponentKind.INGREDIENT,
                        reusableEntityId = 7L,
                        proportionBasisPoints = 5_000
                    ),
                    UpdateReusableCompoundComponentCommand(
                        kind = CompoundComponentKind.INGREDIENT,
                        reusableEntityId = 7L,
                        proportionBasisPoints = 5_000
                    )
                )
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals(
            "This ingredient is already in the composition. Edit the existing row instead.",
            error.fieldErrors["components[1].target"]
        )
        assertEquals(0, repository.updatedCommands.size)
    }

    @Test
    fun repository_cycle_validation_is_returned_without_becoming_persistence_error() = runBlocking {
        val repository = FakeUpdateReusableCompoundRepository(
            updateResult = AppResult.Error(
                DomainError.ValidationError(
                    message = "Please fix the highlighted fields.",
                    fieldErrors = mapOf(
                        "components[1].target" to "This compound would create a recursive composition."
                    )
                )
            )
        )
        val useCase = UpdateReusableCompoundUseCase(repository)

        val result = useCase(validUpdateCommand())

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals(
            "This compound would create a recursive composition.",
            error.fieldErrors["components[1].target"]
        )
    }

    @Test
    fun successful_edit_with_linked_records_syncs_name_to_all_references() = runBlocking {
        val syncedCalls = mutableListOf<Pair<Long, String>>()
        val useCase = UpdateReusableCompoundUseCase(
            repository = FakeUpdateReusableCompoundRepository(),
            syncReferences = { id, name ->
                syncedCalls += id to name
                3
            },
            countReferences = { 3 }
        )

        val result = useCase(validUpdateCommand())

        assertTrue(result is AppResult.Success)
        assertEquals(1, syncedCalls.size)
        assertEquals(9L, syncedCalls.first().first)
        assertEquals("Breakfast mix plus", syncedCalls.first().second)
    }

    @Test
    fun mismatch_between_reference_count_and_synced_rows_returns_integrity_error() = runBlocking {
        val useCase = UpdateReusableCompoundUseCase(
            repository = FakeUpdateReusableCompoundRepository(),
            syncReferences = { _, _ -> 0 },
            countReferences = { 1 }
        )

        val result = useCase(validUpdateCommand())

        val error = (result as AppResult.Error).error as DomainError.IntegrityError
        assertEquals("Couldn't keep linked compound records in sync. Try again.", error.message)
    }

    @Test
    fun sync_exception_is_mapped_to_persistence_error() = runBlocking {
        val useCase = UpdateReusableCompoundUseCase(
            repository = FakeUpdateReusableCompoundRepository(),
            syncReferences = { _, _ -> throw IllegalStateException("boom") },
            countReferences = { 1 }
        )

        val result = useCase(validUpdateCommand())

        val error = (result as AppResult.Error).error as DomainError.PersistenceError
        assertEquals("Couldn't update compound locally. Try again.", error.message)
    }

    @Test
    fun cancellation_is_rethrown_instead_of_mapped_to_persistence_error() = runBlocking {
        val useCase = UpdateReusableCompoundUseCase(
            repository = FakeUpdateReusableCompoundRepository(),
            syncReferences = { _, _ -> throw CancellationException("cancel") },
            countReferences = { 1 }
        )

        try {
            useCase(validUpdateCommand())
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }
    }
}

private class FakeUpdateReusableCompoundRepository(
    private val updateResult: AppResult<ReusableCompound> = AppResult.Success(
        ReusableCompound(
            id = 9L,
            name = "Breakfast mix plus",
            notes = null,
            compositionStatus = CompoundCompositionStatus.DRAFT,
            components = validUpdateCommand().components.mapIndexed { index, component ->
                ReusableCompoundComponent(
                    id = index + 1L,
                    position = index,
                    kind = component.kind,
                    ingredientId = component.reusableEntityId.takeIf { component.kind == CompoundComponentKind.INGREDIENT },
                    childCompoundId = component.reusableEntityId.takeIf { component.kind == CompoundComponentKind.COMPOUND },
                    proportionBasisPoints = component.proportionBasisPoints
                )
            },
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 2L
        )
    )
) : ReusableCompoundRepository {
    val updatedCommands = mutableListOf<UpdateReusableCompoundCommand>()

    override suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound> = error("unused")

    override suspend fun updateCompound(command: UpdateReusableCompoundCommand): AppResult<ReusableCompound> {
        updatedCommands += command
        return updateResult
    }

    override fun observeCompounds(): Flow<List<ReusableCompound>> = emptyFlow()
}

private fun validUpdateCommand(): UpdateReusableCompoundCommand {
    return UpdateReusableCompoundCommand(
        id = 9L,
        name = "Breakfast mix plus",
        notes = null,
        components = listOf(
            UpdateReusableCompoundComponentCommand(
                kind = CompoundComponentKind.INGREDIENT,
                reusableEntityId = 7L,
                proportionBasisPoints = 6_000
            ),
            UpdateReusableCompoundComponentCommand(
                kind = CompoundComponentKind.COMPOUND,
                reusableEntityId = 8L,
                proportionBasisPoints = 4_000
            )
        )
    )
}
