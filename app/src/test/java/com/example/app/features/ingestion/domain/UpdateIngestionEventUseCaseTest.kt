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

    @Test
    fun rejects_inconsistent_reusable_identity_without_updating_repository() = runBlocking {
        val repository = FakeEditableIngestionRepository()
        val useCase = UpdateIngestionEventUseCase(repository, FakeEditableCurrentTimeProvider(fixedNow))

        val result = useCase(
            UpdateIngestionEventCommand(
                id = 7L,
                itemName = "Tea",
                quantityLevel = 2,
                occurredAtEpochMs = fixedNow,
                notes = null,
                reusableEntityKind = ReusableEntityKind.COMPOUND,
                reusableEntityId = null
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Choose a valid reusable item or clear the selection.", error.fieldErrors["itemName"])
        assertTrue(repository.updatedCommands.isEmpty())
    }

    @Test
    fun preserves_valid_reusable_identity_when_updating_repository() = runBlocking {
        val repository = FakeEditableIngestionRepository()
        val useCase = UpdateIngestionEventUseCase(repository, FakeEditableCurrentTimeProvider(fixedNow))

        val result = useCase(
            UpdateIngestionEventCommand(
                id = 7L,
                itemName = "Breakfast mix",
                quantityLevel = 2,
                occurredAtEpochMs = fixedNow,
                notes = null,
                reusableEntityKind = ReusableEntityKind.COMPOUND,
                reusableEntityId = 22L
            )
        )

        assertTrue(result is AppResult.Success)
        assertEquals(ReusableEntityKind.COMPOUND, repository.updatedCommands.single().reusableEntityKind)
        assertEquals(22L, repository.updatedCommands.single().reusableEntityId)
    }

    @Test
    fun rejects_missing_reusable_reference_before_updating_repository() = runBlocking {
        val repository = FakeEditableIngestionRepository()
        val useCase = UpdateIngestionEventUseCase(
            repository,
            FakeEditableCurrentTimeProvider(fixedNow),
            reusableEntityExists = { _, _ -> false }
        )

        val result = useCase(
            UpdateIngestionEventCommand(
                id = 7L,
                itemName = "Breakfast mix",
                quantityLevel = 2,
                occurredAtEpochMs = fixedNow,
                notes = null,
                reusableEntityKind = ReusableEntityKind.COMPOUND,
                reusableEntityId = 22L
            )
        )

        assertEquals(
            DomainError.IntegrityError("Selected reusable item is no longer available."),
            (result as AppResult.Error).error
        )
        assertTrue(repository.updatedCommands.isEmpty())
    }

    @Test
    fun rejects_stale_compound_reference_on_update() = runBlocking {
        val repository = FakeEditableIngestionRepository()
        val useCase = UpdateIngestionEventUseCase(
            repository,
            FakeEditableCurrentTimeProvider(fixedNow),
            reusableEntityExists = { kind, id ->
                kind == ReusableEntityKind.COMPOUND && id == 22L
            }
        )

        val result = useCase(
            UpdateIngestionEventCommand(
                id = 7L,
                itemName = "Breakfast mix",
                quantityLevel = 2,
                occurredAtEpochMs = fixedNow,
                notes = null,
                reusableEntityKind = ReusableEntityKind.COMPOUND,
                reusableEntityId = 99L
            )
        )

        assertEquals(
            DomainError.IntegrityError("Selected reusable item is no longer available."),
            (result as AppResult.Error).error
        )
        assertTrue(repository.updatedCommands.isEmpty())
    }

    @Test
    fun accepts_valid_compound_reference_on_update() = runBlocking {
        val repository = FakeEditableIngestionRepository()
        val useCase = UpdateIngestionEventUseCase(
            repository,
            FakeEditableCurrentTimeProvider(fixedNow),
            reusableEntityExists = { kind, id ->
                kind == ReusableEntityKind.COMPOUND && id == 22L
            }
        )

        val result = useCase(
            UpdateIngestionEventCommand(
                id = 7L,
                itemName = "Breakfast mix v2",
                quantityLevel = 3,
                occurredAtEpochMs = fixedNow,
                notes = null,
                reusableEntityKind = ReusableEntityKind.COMPOUND,
                reusableEntityId = 22L
            )
        )

        assertTrue(result is AppResult.Success)
        val saved = repository.updatedCommands.single()
        assertEquals(ReusableEntityKind.COMPOUND, saved.reusableEntityKind)
        assertEquals(22L, saved.reusableEntityId)
    }

    @Test
    fun rejects_compound_id_without_kind_on_update() = runBlocking {
        val repository = FakeEditableIngestionRepository()
        val useCase = UpdateIngestionEventUseCase(
            repository,
            FakeEditableCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            UpdateIngestionEventCommand(
                id = 7L,
                itemName = "Breakfast mix",
                quantityLevel = 2,
                occurredAtEpochMs = fixedNow,
                notes = null,
                reusableEntityKind = null,
                reusableEntityId = 22L
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Choose a valid reusable item or clear the selection.", error.fieldErrors["itemName"])
        assertTrue(repository.updatedCommands.isEmpty())
    }

    @Test
    fun rejects_zero_compound_id_on_update() = runBlocking {
        val repository = FakeEditableIngestionRepository()
        val useCase = UpdateIngestionEventUseCase(
            repository,
            FakeEditableCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            UpdateIngestionEventCommand(
                id = 7L,
                itemName = "Breakfast mix",
                quantityLevel = 2,
                occurredAtEpochMs = fixedNow,
                notes = null,
                reusableEntityKind = ReusableEntityKind.COMPOUND,
                reusableEntityId = 0L
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Choose a valid reusable item or clear the selection.", error.fieldErrors["itemName"])
        assertTrue(repository.updatedCommands.isEmpty())
    }

    @Test
    fun accepts_valid_historical_edit_timestamp() = runBlocking {
        val repository = FakeEditableIngestionRepository()
        val useCase = UpdateIngestionEventUseCase(repository, FakeEditableCurrentTimeProvider(fixedNow))

        val historicalOccurredAt = fixedNow - 86_400_000L
        val result = useCase(
            UpdateIngestionEventCommand(
                id = 7L,
                itemName = "Tea",
                quantityLevel = 2,
                occurredAtEpochMs = historicalOccurredAt,
                notes = "older record"
            )
        )

        assertTrue(result is AppResult.Success)
        assertEquals(historicalOccurredAt, repository.updatedCommands.single().occurredAtEpochMs)
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
            IngestionEvent(
                command.id,
                command.itemName,
                command.quantityLevel ?: 0,
                command.occurredAtEpochMs,
                command.notes,
                command.reusableEntityKind,
                command.reusableEntityId
            )
        )
    }

    override suspend fun deleteIngestion(id: Long): AppResult<Unit> = error("unused")
}

private class FakeEditableCurrentTimeProvider(private val now: Long) : CurrentTimeProvider {
    override fun nowEpochMillis(): Long = now
}
