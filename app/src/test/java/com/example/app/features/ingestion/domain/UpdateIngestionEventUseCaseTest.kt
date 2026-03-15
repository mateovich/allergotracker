package com.example.app.features.ingestion.domain

import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateIngestionEventUseCaseTest {

    private val fixedNow = 1_700_000_000_000L

    @Test
    fun rejects_invalid_required_values_without_updating_repository() = runBlocking {
        val repository = FakeEditableIngestionRepository()
        val useCase = UpdateIngestionEventUseCase(repository, FakeEditableCurrentTimeProvider(fixedNow))

        val result = useCase(UpdateIngestionEventCommand(7L, "   ", null, fixedNow, ""))

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Enter what you ingested.", error.fieldErrors["itemName"])
        assertEquals("Choose a quantity.", error.fieldErrors["quantityLevel"])
        assertTrue(repository.updatedCommands.isEmpty())
    }

    @Test
    fun trims_valid_values_before_updating_repository() = runBlocking {
        val repository = FakeEditableIngestionRepository()
        val useCase = UpdateIngestionEventUseCase(repository, FakeEditableCurrentTimeProvider(fixedNow))

        val result = useCase(UpdateIngestionEventCommand(7L, "  Tea  ", 2, fixedNow, "  with honey  "))

        val success = result as AppResult.Success
        assertEquals("Tea", success.value.itemName)
        assertEquals("Tea", repository.updatedCommands.single().itemName)
        assertEquals("with honey", repository.updatedCommands.single().notes)
    }
}

private class FakeEditableIngestionRepository : IngestionRepository {
    val updatedCommands = mutableListOf<UpdateIngestionEventCommand>()

    override suspend fun createIngestion(command: CreateIngestionEventCommand): AppResult<IngestionEvent> {
        error("unused")
    }

    override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> {
        return AppResult.Success(IngestionEvent(id, "Tea", 2, 1_700_000_000_000L, "notes"))
    }

    override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
        updatedCommands += command
        return AppResult.Success(
            IngestionEvent(command.id, command.itemName, command.quantityLevel ?: 0, command.occurredAtEpochMs, command.notes)
        )
    }

    override suspend fun deleteIngestion(id: Long): AppResult<Unit> = error("unused")
}

private class FakeEditableCurrentTimeProvider(private val now: Long) : CurrentTimeProvider {
    override fun nowEpochMillis(): Long = now
}
