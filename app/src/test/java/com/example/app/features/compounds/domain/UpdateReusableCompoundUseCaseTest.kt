package com.example.app.features.compounds.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class UpdateReusableCompoundUseCaseTest {

    @Test
    fun mismatch_between_reference_count_and_synced_rows_returns_integrity_error() = runBlocking {
        val useCase = UpdateReusableCompoundUseCase(
            repository = FakeUpdateReusableCompoundRepository(),
            syncReferences = { _, _ -> 0 },
            countReferences = { 1 }
        )

        val result = useCase(UpdateReusableCompoundCommand(9L, "Breakfast mix plus", null))

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

        val result = useCase(UpdateReusableCompoundCommand(9L, "Breakfast mix plus", null))

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
            useCase(UpdateReusableCompoundCommand(9L, "Breakfast mix plus", null))
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        }
    }
}

private class FakeUpdateReusableCompoundRepository : ReusableCompoundRepository {
    override suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound> = error("unused")

    override suspend fun updateCompound(command: UpdateReusableCompoundCommand): AppResult<ReusableCompound> {
        return AppResult.Success(
            ReusableCompound(
                id = command.id,
                name = command.name,
                notes = command.notes,
                compositionStatus = CompoundCompositionStatus.DRAFT,
                components = emptyList(),
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 2L
            )
        )
    }

    override fun observeCompounds(): Flow<List<ReusableCompound>> = emptyFlow()
}
