package io.github.mateovich.allergotracker.features.ingestion.domain

import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateIngestionEventUseCaseTest {

    private val fixedNow = 1_700_000_000_000L

    @Test
    fun returns_field_errors_when_required_values_are_missing() = runBlocking {
        val repository = FakeIngestionRepository()
        val useCase = CreateIngestionEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateIngestionEventCommand(
                itemName = "   ",
                quantityLevel = null,
                occurredAtEpochMs = fixedNow,
                notes = ""
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Enter what you ingested.", error.fieldErrors["itemName"])
        assertEquals("Choose a quantity.", error.fieldErrors["quantityLevel"])
        assertTrue(repository.savedCommands.isEmpty())
    }

    @Test
    fun rejects_future_occurrence_time() = runBlocking {
        val useCase = CreateIngestionEventUseCase(
            repository = FakeIngestionRepository(),
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateIngestionEventCommand(
                itemName = "Oatmeal",
                quantityLevel = 3,
                occurredAtEpochMs = fixedNow + 60_000L,
                notes = null
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Time cannot be in the future.", error.fieldErrors["occurredAtEpochMs"])
    }

    @Test
    fun rejects_quantity_outside_supported_scale() = runBlocking {
        val repository = FakeIngestionRepository()
        val useCase = CreateIngestionEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateIngestionEventCommand(
                itemName = "Oatmeal",
                quantityLevel = 6,
                occurredAtEpochMs = fixedNow,
                notes = null
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Choose a valid quantity.", error.fieldErrors["quantityLevel"])
        assertTrue(repository.savedCommands.isEmpty())
    }

    @Test
    fun persists_valid_ingestion_through_repository() = runBlocking {
        val repository = FakeIngestionRepository()
        val useCase = CreateIngestionEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateIngestionEventCommand(
                itemName = "Oatmeal",
                quantityLevel = 3,
                occurredAtEpochMs = fixedNow,
                notes = "with berries"
            )
        )

        val success = result as AppResult.Success
        assertEquals("Oatmeal", success.value.itemName)
        assertEquals(3, success.value.quantityLevel)
        assertEquals(1, repository.savedCommands.size)
    }

    @Test
    fun rejects_inconsistent_reusable_identity_before_hitting_repository() = runBlocking {
        val repository = FakeIngestionRepository()
        val useCase = CreateIngestionEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateIngestionEventCommand(
                itemName = "Oatmeal",
                quantityLevel = 3,
                occurredAtEpochMs = fixedNow,
                notes = null,
                reusableEntityKind = ReusableEntityKind.INGREDIENT,
                reusableEntityId = null
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Choose a valid reusable item or clear the selection.", error.fieldErrors["itemName"])
        assertTrue(repository.savedCommands.isEmpty())
    }

    @Test
    fun passes_valid_reusable_identity_through_to_repository() = runBlocking {
        val repository = FakeIngestionRepository()
        val useCase = CreateIngestionEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateIngestionEventCommand(
                itemName = "Oat milk",
                quantityLevel = 2,
                occurredAtEpochMs = fixedNow,
                notes = null,
                reusableEntityKind = ReusableEntityKind.INGREDIENT,
                reusableEntityId = 11L
            )
        )

        assertTrue(result is AppResult.Success)
        assertEquals(ReusableEntityKind.INGREDIENT, repository.savedCommands.single().reusableEntityKind)
        assertEquals(11L, repository.savedCommands.single().reusableEntityId)
    }

    @Test
    fun rejects_missing_reusable_reference_before_hitting_repository() = runBlocking {
        val repository = FakeIngestionRepository()
        val useCase = CreateIngestionEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow),
            reusableEntityExists = { _, _ -> false }
        )

        val result = useCase(
            CreateIngestionEventCommand(
                itemName = "Oat milk",
                quantityLevel = 2,
                occurredAtEpochMs = fixedNow,
                notes = null,
                reusableEntityKind = ReusableEntityKind.INGREDIENT,
                reusableEntityId = 11L
            )
        )

        assertEquals(
            DomainError.IntegrityError("Selected reusable item is no longer available."),
            (result as AppResult.Error).error
        )
        assertTrue(repository.savedCommands.isEmpty())
    }

    @Test
    fun rejects_missing_compound_reference_before_hitting_repository() = runBlocking {
        val repository = FakeIngestionRepository()
        val useCase = CreateIngestionEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow),
            reusableEntityExists = { kind, id ->
                kind == ReusableEntityKind.COMPOUND && id == 42L
            }
        )

        val result = useCase(
            CreateIngestionEventCommand(
                itemName = "Breakfast mix",
                quantityLevel = 3,
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
        assertTrue(repository.savedCommands.isEmpty())
    }

    @Test
    fun accepts_valid_compound_reference_and_passes_to_repository() = runBlocking {
        val repository = FakeIngestionRepository()
        val useCase = CreateIngestionEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow),
            reusableEntityExists = { kind, id ->
                kind == ReusableEntityKind.COMPOUND && id == 42L
            }
        )

        val result = useCase(
            CreateIngestionEventCommand(
                itemName = "Breakfast mix",
                quantityLevel = 3,
                occurredAtEpochMs = fixedNow,
                notes = null,
                reusableEntityKind = ReusableEntityKind.COMPOUND,
                reusableEntityId = 42L
            )
        )

        assertTrue(result is AppResult.Success)
        val saved = repository.savedCommands.single()
        assertEquals(ReusableEntityKind.COMPOUND, saved.reusableEntityKind)
        assertEquals(42L, saved.reusableEntityId)
    }

    @Test
    fun rejects_compound_id_with_no_kind() = runBlocking {
        val repository = FakeIngestionRepository()
        val useCase = CreateIngestionEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateIngestionEventCommand(
                itemName = "Breakfast mix",
                quantityLevel = 3,
                occurredAtEpochMs = fixedNow,
                notes = null,
                reusableEntityKind = null,
                reusableEntityId = 42L
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Choose a valid reusable item or clear the selection.", error.fieldErrors["itemName"])
        assertTrue(repository.savedCommands.isEmpty())
    }

    @Test
    fun rejects_zero_compound_id() = runBlocking {
        val repository = FakeIngestionRepository()
        val useCase = CreateIngestionEventUseCase(
            repository = repository,
            currentTimeProvider = FakeCurrentTimeProvider(fixedNow)
        )

        val result = useCase(
            CreateIngestionEventCommand(
                itemName = "Breakfast mix",
                quantityLevel = 3,
                occurredAtEpochMs = fixedNow,
                notes = null,
                reusableEntityKind = ReusableEntityKind.COMPOUND,
                reusableEntityId = 0L
            )
        )

        val error = (result as AppResult.Error).error as DomainError.ValidationError
        assertEquals("Choose a valid reusable item or clear the selection.", error.fieldErrors["itemName"])
        assertTrue(repository.savedCommands.isEmpty())
    }
}

private class FakeIngestionRepository : IngestionRepository {
    val savedCommands = mutableListOf<CreateIngestionEventCommand>()

    override suspend fun createIngestion(command: CreateIngestionEventCommand): AppResult<IngestionEvent> {
        savedCommands += command
        return AppResult.Success(
            IngestionEvent(
                id = 1L,
                itemName = command.itemName,
                quantityLevel = command.quantityLevel ?: 0,
                occurredAtEpochMs = command.occurredAtEpochMs,
                notes = command.notes,
                reusableEntityKind = command.reusableEntityKind,
                reusableEntityId = command.reusableEntityId
            )
        )
    }

    override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> {
        return AppResult.Error(DomainError.IntegrityError("unused"))
    }

    override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
        return AppResult.Error(DomainError.IntegrityError("unused"))
    }

    override suspend fun deleteIngestion(id: Long): AppResult<Unit> {
        return AppResult.Error(DomainError.IntegrityError("unused"))
    }
}

private class FakeCurrentTimeProvider(
    private val nowEpochMs: Long
) : CurrentTimeProvider {
    override fun nowEpochMillis(): Long = nowEpochMs
}
