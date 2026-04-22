package io.github.mateovich.allergotracker.features.ingestion.ui

import io.github.mateovich.allergotracker.MainDispatcherRule
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.ingestion.domain.DeleteIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.GetIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionEvent
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionRepository
import io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventCommand
import io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditIngestionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedNow = 1_700_000_000_000L
    private val existingOccurredAt = fixedNow - (2L * 60L * 60L * 1000L)

    @Test
    fun load_prefills_state_from_existing_record() = runBlocking {
        val viewModel = buildViewModel()

        viewModel.onIntent(EditIngestionUiIntent.Load)

        assertEquals("Tea", viewModel.state.value.itemName)
        assertEquals("Tea", viewModel.state.value.deleteRecordLabel)
        assertEquals(2, viewModel.state.value.selectedQuantityLevel)
        assertEquals("with honey", viewModel.state.value.notes)
        assertTrue(viewModel.state.value.canDelete)
    }

    @Test
    fun invalid_input_disables_save() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditIngestionUiIntent.Load)
        viewModel.onIntent(EditIngestionUiIntent.UpdateItemName("   "))
        viewModel.onIntent(EditIngestionUiIntent.Save)

        assertEquals("   ", viewModel.state.value.itemName)
        assertTrue(!viewModel.state.value.canSave)
        assertTrue(!viewModel.state.value.isSaving)
    }

    @Test
    fun successful_save_navigates_back_to_records() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditIngestionUiIntent.Load)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditIngestionUiIntent.UpdateItemName("Tea updated"))
        viewModel.onIntent(EditIngestionUiIntent.Save)

        assertEquals(EditIngestionUiEffect.NavigateToRecords, effect.await())
    }

    @Test
    fun delete_confirmation_can_open_and_close_without_losing_form_state() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditIngestionUiIntent.Load)

        viewModel.onIntent(EditIngestionUiIntent.ShowDeleteConfirmation)
        assertTrue(viewModel.state.value.isDeleteConfirmationVisible)

        viewModel.onIntent(EditIngestionUiIntent.DismissDeleteConfirmation)

        assertTrue(!viewModel.state.value.isDeleteConfirmationVisible)
        assertEquals("Tea", viewModel.state.value.itemName)
    }

    @Test
    fun successful_delete_navigates_back_to_records() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditIngestionUiIntent.Load)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditIngestionUiIntent.ShowDeleteConfirmation)
        viewModel.onIntent(EditIngestionUiIntent.ConfirmDelete)

        assertEquals(EditIngestionUiEffect.NavigateToRecords, effect.await())
    }

    @Test
    fun delete_confirmation_uses_persisted_label_not_unsaved_form_edit() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditIngestionUiIntent.Load)

        viewModel.onIntent(EditIngestionUiIntent.UpdateItemName("Tea edited"))

        assertEquals("Tea", viewModel.state.value.deleteRecordLabel)
        assertTrue(viewModel.state.value.canDelete)
    }

    @Test
    fun confirm_delete_without_visible_confirmation_does_not_delete() = runBlocking {
        var deleteCalls = 0
        val repository = object : IngestionRepository {
            override suspend fun createIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")
            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> = AppResult.Success(IngestionEvent(id, "Tea", 2, existingOccurredAt, "with honey"))
            override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> = AppResult.Success(IngestionEvent(command.id, command.itemName, command.quantityLevel ?: 0, command.occurredAtEpochMs, command.notes))
            override suspend fun deleteIngestion(id: Long): AppResult<Unit> {
                deleteCalls += 1
                return AppResult.Success(Unit)
            }
        }
        val viewModel = EditIngestionViewModel(
            recordId = 7L,
            getIngestionEventUseCase = GetIngestionEventUseCase(repository),
            deleteIngestionEventUseCase = DeleteIngestionEventUseCase(repository),
            updateIngestionEventUseCase = UpdateIngestionEventUseCase(repository, io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }),
            currentTimeProvider = io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditIngestionUiIntent.Load)
        viewModel.onIntent(EditIngestionUiIntent.ConfirmDelete)

        assertEquals(0, deleteCalls)
        assertTrue(!viewModel.state.value.isDeleting)
    }

    @Test
    fun failed_delete_keeps_screen_recoverable_for_retry() = runBlocking {
        var deleteAttempts = 0
        val repository = object : IngestionRepository {
            override suspend fun createIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")

            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> {
                return AppResult.Success(IngestionEvent(id, "Tea", 2, existingOccurredAt, "with honey"))
            }

            override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                return AppResult.Success(IngestionEvent(command.id, command.itemName, command.quantityLevel ?: 0, command.occurredAtEpochMs, command.notes))
            }

            override suspend fun deleteIngestion(id: Long): AppResult<Unit> {
                deleteAttempts += 1
                return if (deleteAttempts == 1) {
                    AppResult.Error(DomainError.PersistenceError("Couldn't delete ingestion locally. Try again."))
                } else {
                    AppResult.Success(Unit)
                }
            }
        }
        val viewModel = EditIngestionViewModel(
            recordId = 7L,
            getIngestionEventUseCase = GetIngestionEventUseCase(repository),
            deleteIngestionEventUseCase = DeleteIngestionEventUseCase(repository),
            updateIngestionEventUseCase = UpdateIngestionEventUseCase(repository, io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }),
            currentTimeProvider = io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditIngestionUiIntent.Load)
        viewModel.onIntent(EditIngestionUiIntent.ShowDeleteConfirmation)
        viewModel.onIntent(EditIngestionUiIntent.ConfirmDelete)

        assertEquals("Couldn't delete ingestion locally. Try again.", viewModel.state.value.formMessage)
        assertTrue(!viewModel.state.value.isDeleting)
        assertTrue(!viewModel.state.value.isDeleteConfirmationVisible)

        viewModel.onIntent(EditIngestionUiIntent.ShowDeleteConfirmation)

        assertTrue(viewModel.state.value.isDeleteConfirmationVisible)
    }

    @Test
    fun time_preset_reanchors_edit_time_from_current_time() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditIngestionUiIntent.Load)

        viewModel.onIntent(EditIngestionUiIntent.SelectTimePreset(IngestionTimePreset.ONE_HOUR_AGO))

        assertEquals(fixedNow - (60L * 60L * 1000L), viewModel.state.value.occurredAtEpochMs)
    }

    @Test
    fun invalid_save_does_not_call_update() = runBlocking {
        var updateCalls = 0
        val repository = object : IngestionRepository {
            override suspend fun createIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")
            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> = AppResult.Success(IngestionEvent(id, "Tea", 2, existingOccurredAt, "with honey"))
            override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                updateCalls += 1
                return AppResult.Success(IngestionEvent(command.id, command.itemName, command.quantityLevel ?: 0, command.occurredAtEpochMs, command.notes))
            }
            override suspend fun deleteIngestion(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val viewModel = EditIngestionViewModel(
            recordId = 7L,
            getIngestionEventUseCase = GetIngestionEventUseCase(repository),
            deleteIngestionEventUseCase = DeleteIngestionEventUseCase(repository),
            updateIngestionEventUseCase = UpdateIngestionEventUseCase(repository, io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }),
            currentTimeProvider = io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditIngestionUiIntent.Load)
        viewModel.onIntent(EditIngestionUiIntent.UpdateItemName("   "))
        viewModel.onIntent(EditIngestionUiIntent.Save)

        assertEquals(0, updateCalls)
    }

    @Test
    fun second_save_while_first_is_in_flight_is_ignored() = runBlocking {
        var updateCalls = 0
        val updateGate = CompletableDeferred<Unit>()
        val repository = object : IngestionRepository {
            override suspend fun createIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")
            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> = AppResult.Success(IngestionEvent(id, "Tea", 2, existingOccurredAt, "with honey"))
            override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                updateCalls += 1
                updateGate.await()
                return AppResult.Success(IngestionEvent(command.id, command.itemName, command.quantityLevel ?: 0, command.occurredAtEpochMs, command.notes))
            }
            override suspend fun deleteIngestion(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val viewModel = EditIngestionViewModel(
            recordId = 7L,
            getIngestionEventUseCase = GetIngestionEventUseCase(repository),
            deleteIngestionEventUseCase = DeleteIngestionEventUseCase(repository),
            updateIngestionEventUseCase = UpdateIngestionEventUseCase(repository, io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }),
            currentTimeProvider = io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditIngestionUiIntent.Load)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditIngestionUiIntent.Save)
        viewModel.onIntent(EditIngestionUiIntent.Save)
        updateGate.complete(Unit)

        assertEquals(EditIngestionUiEffect.NavigateToRecords, effect.await())
        assertEquals(1, updateCalls)
    }

    @Test
    fun failed_load_can_retry_and_recover() = runBlocking {
        var loadAttempts = 0
        val repository = object : IngestionRepository {
            override suspend fun createIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")

            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> {
                loadAttempts += 1
                return if (loadAttempts == 1) {
                    AppResult.Error(io.github.mateovich.allergotracker.core.common.error.DomainError.PersistenceError("Couldn't load ingestion locally. Try again."))
                } else {
                    AppResult.Success(IngestionEvent(id, "Tea", 2, existingOccurredAt, "with honey"))
                }
            }

            override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                return AppResult.Success(IngestionEvent(command.id, command.itemName, command.quantityLevel ?: 0, command.occurredAtEpochMs, command.notes))
            }

            override suspend fun deleteIngestion(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val viewModel = EditIngestionViewModel(
            recordId = 7L,
            getIngestionEventUseCase = GetIngestionEventUseCase(repository),
            deleteIngestionEventUseCase = DeleteIngestionEventUseCase(repository),
            updateIngestionEventUseCase = UpdateIngestionEventUseCase(repository, io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }),
            currentTimeProvider = io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditIngestionUiIntent.Load)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())

        viewModel.onIntent(EditIngestionUiIntent.Load)

        assertEquals("Tea", viewModel.state.value.itemName)
        assertEquals(2, loadAttempts)
    }

    @Test
    fun manual_name_override_clears_reusable_summary() = runBlocking {
        val repository = object : IngestionRepository {
            override suspend fun createIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")

            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> {
                return AppResult.Success(
                    IngestionEvent(
                        id = id,
                        itemName = "Oat milk",
                        quantityLevel = 2,
                        occurredAtEpochMs = existingOccurredAt,
                        notes = "with honey",
                        reusableEntityKind = io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind.INGREDIENT,
                        reusableEntityId = 11L
                    )
                )
            }

            override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                return AppResult.Success(IngestionEvent(command.id, command.itemName, command.quantityLevel ?: 0, command.occurredAtEpochMs, command.notes))
            }

            override suspend fun deleteIngestion(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val viewModel = EditIngestionViewModel(
            recordId = 7L,
            getIngestionEventUseCase = GetIngestionEventUseCase(repository),
            deleteIngestionEventUseCase = DeleteIngestionEventUseCase(repository),
            updateIngestionEventUseCase = UpdateIngestionEventUseCase(repository, io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }),
            currentTimeProvider = io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditIngestionUiIntent.Load)
        viewModel.onIntent(EditIngestionUiIntent.UpdateItemName("Manual override"))

        assertNull(viewModel.state.value.selectedReusableEntitySummary)
        assertNull(viewModel.state.value.selectedReusableEntityId)
    }

    @Test
    fun clearing_retired_reusable_selection_keeps_record_text_but_drops_stale_identity() = runBlocking {
        val repository = object : IngestionRepository {
            override suspend fun createIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")

            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> {
                return AppResult.Success(
                    IngestionEvent(
                        id = id,
                        itemName = "Oat milk",
                        quantityLevel = 2,
                        occurredAtEpochMs = existingOccurredAt,
                        notes = "with honey",
                        reusableEntityKind = io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind.INGREDIENT,
                        reusableEntityId = 11L
                    )
                )
            }

            override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                return AppResult.Success(IngestionEvent(command.id, command.itemName, command.quantityLevel ?: 0, command.occurredAtEpochMs, command.notes))
            }

            override suspend fun deleteIngestion(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val viewModel = EditIngestionViewModel(
            recordId = 7L,
            getIngestionEventUseCase = GetIngestionEventUseCase(repository),
            deleteIngestionEventUseCase = DeleteIngestionEventUseCase(repository),
            updateIngestionEventUseCase = UpdateIngestionEventUseCase(repository, io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }),
            currentTimeProvider = io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditIngestionUiIntent.Load)
        viewModel.onIntent(EditIngestionUiIntent.ClearRetiredReusableSelection)

        assertEquals("Oat milk", viewModel.state.value.itemName)
        assertNull(viewModel.state.value.selectedReusableEntityId)
        assertNull(viewModel.state.value.selectedReusableEntityKind)
        assertNull(viewModel.state.value.selectedReusableEntitySummary)
        assertEquals("Saved reusable item was retired. Review the text value before saving.", viewModel.state.value.formMessage)
    }

    @Test
    fun stale_saved_reusable_reference_is_cleared_during_load() = runBlocking {
        val repository = object : IngestionRepository {
            override suspend fun createIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")

            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> {
                return AppResult.Success(
                    IngestionEvent(
                        id = id,
                        itemName = "Oat milk",
                        quantityLevel = 2,
                        occurredAtEpochMs = existingOccurredAt,
                        notes = "with honey",
                        reusableEntityKind = io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind.INGREDIENT,
                        reusableEntityId = 11L
                    )
                )
            }

            override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                return AppResult.Success(IngestionEvent(command.id, command.itemName, command.quantityLevel ?: 0, command.occurredAtEpochMs, command.notes))
            }

            override suspend fun deleteIngestion(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        val viewModel = EditIngestionViewModel(
            recordId = 7L,
            getIngestionEventUseCase = GetIngestionEventUseCase(repository),
            deleteIngestionEventUseCase = DeleteIngestionEventUseCase(repository),
            updateIngestionEventUseCase = UpdateIngestionEventUseCase(repository, io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }),
            currentTimeProvider = io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow },
            reusableEntityExists = { _, _ -> false }
        )

        viewModel.onIntent(EditIngestionUiIntent.Load)

        assertEquals("Oat milk", viewModel.state.value.itemName)
        assertNull(viewModel.state.value.selectedReusableEntityId)
        assertNull(viewModel.state.value.selectedReusableEntityKind)
        assertNull(viewModel.state.value.selectedReusableEntitySummary)
        assertEquals("Saved reusable item was retired. Review the text value before saving.", viewModel.state.value.formMessage)
    }

    private fun buildViewModel(): EditIngestionViewModel {
        val repository = object : IngestionRepository {
            override suspend fun createIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")

            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> {
                return AppResult.Success(IngestionEvent(id, "Tea", 2, existingOccurredAt, "with honey"))
            }

            override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                return AppResult.Success(IngestionEvent(command.id, command.itemName, command.quantityLevel ?: 0, command.occurredAtEpochMs, command.notes))
            }

            override suspend fun deleteIngestion(id: Long): AppResult<Unit> = AppResult.Success(Unit)
        }
        return EditIngestionViewModel(
            recordId = 7L,
            getIngestionEventUseCase = GetIngestionEventUseCase(repository),
            deleteIngestionEventUseCase = DeleteIngestionEventUseCase(repository),
            updateIngestionEventUseCase = UpdateIngestionEventUseCase(repository, io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }),
            currentTimeProvider = io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider { fixedNow }
        )
    }
}
