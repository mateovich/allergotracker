package io.github.mateovich.allergotracker.features.ingestion.ui

import io.github.mateovich.allergotracker.MainDispatcherRule
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.ingestion.domain.DeleteIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.GetIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionEvent
import io.github.mateovich.allergotracker.features.ingestion.domain.IngestionRepository
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventCommand
import io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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

        viewModel.onIntent(
            EditIngestionUiIntent.SelectReusableEntity(
                IngestionReusableSelectionResult(id = 21L, name = "Tea updated", kind = ReusableEntityKind.INGREDIENT)
            )
        )
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
            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> = AppResult.Success(IngestionEvent(id, "Tea", 2, existingOccurredAt, "with honey", reusableEntityKind = ReusableEntityKind.INGREDIENT, reusableEntityId = 5L))
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
                return AppResult.Success(IngestionEvent(id, "Tea", 2, existingOccurredAt, "with honey", reusableEntityKind = ReusableEntityKind.INGREDIENT, reusableEntityId = 5L))
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
    fun load_initializes_selected_date_and_time_from_existing_record() = runBlocking {
        val viewModel = buildViewModel()

        viewModel.onIntent(EditIngestionUiIntent.Load)

        val expectedDateTime = Instant.ofEpochMilli(existingOccurredAt).atZone(ZoneId.systemDefault())
        assertEquals(expectedDateTime.toLocalDate(), viewModel.state.value.selectedDate)
        assertEquals(expectedDateTime.toLocalTime().withSecond(0).withNano(0), viewModel.state.value.selectedTime)
    }

    @Test
    fun custom_date_selection_preserves_existing_time() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditIngestionUiIntent.Load)
        val initialTime = Instant.ofEpochMilli(existingOccurredAt).atZone(ZoneId.systemDefault()).toLocalTime()
        val selectedDate = LocalDate.of(2023, 11, 10)

        viewModel.onIntent(EditIngestionUiIntent.SelectDate(selectedDate))

        val selectedDateTime = Instant.ofEpochMilli(viewModel.state.value.occurredAtEpochMs)
            .atZone(ZoneId.systemDefault())
        assertEquals(selectedDate, viewModel.state.value.selectedDate)
        assertEquals(initialTime.withSecond(0).withNano(0), selectedDateTime.toLocalTime().withSecond(0).withNano(0))
        assertNull(viewModel.state.value.selectedTimePreset)
    }

    @Test
    fun custom_time_selection_preserves_existing_date() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditIngestionUiIntent.Load)
        val initialDate = Instant.ofEpochMilli(existingOccurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val selectedTime = LocalTime.of(8, 45)

        viewModel.onIntent(EditIngestionUiIntent.SelectTime(selectedTime))

        val selectedDateTime = Instant.ofEpochMilli(viewModel.state.value.occurredAtEpochMs)
            .atZone(ZoneId.systemDefault())
        assertEquals(initialDate, selectedDateTime.toLocalDate())
        assertEquals(selectedTime, viewModel.state.value.selectedTime)
        assertNull(viewModel.state.value.selectedTimePreset)
    }

    @Test
    fun save_uses_custom_selected_timestamp() = runBlocking {
        val updatedCommands = mutableListOf<UpdateIngestionEventCommand>()
        val viewModel = buildViewModel(updatedCommands = updatedCommands)
        viewModel.onIntent(EditIngestionUiIntent.Load)

        viewModel.onIntent(EditIngestionUiIntent.SelectDate(LocalDate.of(2023, 11, 10)))
        viewModel.onIntent(EditIngestionUiIntent.SelectTime(LocalTime.of(8, 45)))
        viewModel.onIntent(EditIngestionUiIntent.Save)

        val expectedEpoch = LocalDate.of(2023, 11, 10)
            .atTime(LocalTime.of(8, 45))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        assertEquals(expectedEpoch, updatedCommands.single().occurredAtEpochMs)
    }

    @Test
    fun future_custom_timestamp_is_rejected_and_form_remains_recoverable() = runBlocking {
        val viewModel = buildViewModel()
        val futureDate = Instant.ofEpochMilli(fixedNow).atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1)
        viewModel.onIntent(EditIngestionUiIntent.Load)

        viewModel.onIntent(EditIngestionUiIntent.SelectDate(futureDate))
        viewModel.onIntent(EditIngestionUiIntent.Save)

        assertEquals("Time cannot be in the future.", viewModel.state.value.occurredAtError)
        assertTrue(!viewModel.state.value.isSaving)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun invalid_save_does_not_call_update() = runBlocking {
        var updateCalls = 0
        val repository = object : IngestionRepository {
            override suspend fun createIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")
            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> = AppResult.Success(IngestionEvent(id, "Tea", 2, existingOccurredAt, "with honey", reusableEntityKind = ReusableEntityKind.INGREDIENT, reusableEntityId = 5L))
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
            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> = AppResult.Success(IngestionEvent(id, "Tea", 2, existingOccurredAt, "with honey", reusableEntityKind = ReusableEntityKind.INGREDIENT, reusableEntityId = 5L))
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
                    AppResult.Success(IngestionEvent(id, "Tea", 2, existingOccurredAt, "with honey", reusableEntityKind = ReusableEntityKind.INGREDIENT, reusableEntityId = 5L))
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
    fun linked_record_loads_with_editable_item_name_and_reusable_identity() = runBlocking {
        val viewModel = buildReusableLinkedViewModel(kind = ReusableEntityKind.INGREDIENT)

        viewModel.onIntent(EditIngestionUiIntent.Load)

        assertEquals("Oat milk", viewModel.state.value.itemName)
        assertEquals(ReusableEntityKind.INGREDIENT, viewModel.state.value.selectedReusableEntityKind)
        assertEquals(11L, viewModel.state.value.selectedReusableEntityId)
        // Field is editable (no isItemNameReadOnly flag)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun linked_compound_loads_with_editable_field() = runBlocking {
        val viewModel = buildReusableLinkedViewModel(kind = ReusableEntityKind.COMPOUND)

        viewModel.onIntent(EditIngestionUiIntent.Load)

        assertEquals(ReusableEntityKind.COMPOUND, viewModel.state.value.selectedReusableEntityKind)
        assertEquals(11L, viewModel.state.value.selectedReusableEntityId)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun update_item_name_clears_reusable_link() = runBlocking {
        val viewModel = buildReusableLinkedViewModel(kind = ReusableEntityKind.INGREDIENT)
        viewModel.onIntent(EditIngestionUiIntent.Load)

        viewModel.onIntent(EditIngestionUiIntent.UpdateItemName("Manual override"))

        assertEquals("Manual override", viewModel.state.value.itemName)
        assertNull(viewModel.state.value.selectedReusableEntityKind)
        assertNull(viewModel.state.value.selectedReusableEntityId)
    }

    @Test
    fun linked_save_after_non_name_edits_preserves_item_name_and_reusable_identity() = runBlocking {
        val updatedCommands = mutableListOf<UpdateIngestionEventCommand>()
        val viewModel = buildReusableLinkedViewModel(updatedCommands = updatedCommands)
        viewModel.onIntent(EditIngestionUiIntent.Load)

        viewModel.onIntent(EditIngestionUiIntent.UpdateNotes("less honey"))
        viewModel.onIntent(EditIngestionUiIntent.SelectQuantityLevel(3))
        viewModel.onIntent(EditIngestionUiIntent.Save)

        val command = updatedCommands.single()
        assertEquals("Oat milk", command.itemName)
        assertEquals(ReusableEntityKind.INGREDIENT, command.reusableEntityKind)
        assertEquals(11L, command.reusableEntityId)
        assertEquals(3, command.quantityLevel)
        assertEquals("less honey", command.notes)
    }

    @Test
    fun unlinked_legacy_record_cannot_save_until_reusable_is_selected() = runBlocking {
        val updatedCommands = mutableListOf<UpdateIngestionEventCommand>()
        val viewModel = buildViewModel(updatedCommands = updatedCommands, linked = false)

        viewModel.onIntent(EditIngestionUiIntent.Load)
        viewModel.onIntent(EditIngestionUiIntent.Save)

        assertEquals("Tea", viewModel.state.value.itemName)
        assertTrue(!viewModel.state.value.canSave)
        assertTrue(updatedCommands.isEmpty())

        viewModel.onIntent(
            EditIngestionUiIntent.SelectReusableEntity(
                IngestionReusableSelectionResult(id = 21L, name = "Green tea", kind = ReusableEntityKind.INGREDIENT)
            )
        )
        viewModel.onIntent(EditIngestionUiIntent.Save)

        assertEquals(21L, updatedCommands.single().reusableEntityId)
        assertEquals("Green tea", updatedCommands.single().itemName)
    }

    @Test
    fun typing_unmatched_name_unlinks_record_and_blocks_save() = runBlocking {
        val updatedCommands = mutableListOf<UpdateIngestionEventCommand>()
        val viewModel = buildViewModel(updatedCommands = updatedCommands)
        viewModel.onIntent(EditIngestionUiIntent.Load)

        viewModel.onIntent(EditIngestionUiIntent.UpdateReusableSearchQuery("Something never saved"))
        viewModel.onIntent(EditIngestionUiIntent.Save)

        assertEquals("Something never saved", viewModel.state.value.reusableSearchQuery)
        assertNull(viewModel.state.value.selectedReusableEntityId)
        assertNull(viewModel.state.value.selectedReusableEntityKind)
        assertTrue(!viewModel.state.value.canSave)
        assertTrue(updatedCommands.isEmpty())
    }

    @Test
    fun selecting_another_entity_replaces_link_and_saves_new_identity() = runBlocking {
        val updatedCommands = mutableListOf<UpdateIngestionEventCommand>()
        val viewModel = buildViewModel(updatedCommands = updatedCommands)
        viewModel.onIntent(EditIngestionUiIntent.Load)

        viewModel.onIntent(EditIngestionUiIntent.UpdateReusableSearchQuery("Gran"))
        viewModel.onIntent(
            EditIngestionUiIntent.SelectReusableEntity(
                IngestionReusableSelectionResult(id = 9L, name = "Granola", kind = ReusableEntityKind.COMPOUND)
            )
        )
        viewModel.onIntent(EditIngestionUiIntent.Save)

        val command = updatedCommands.single()
        assertEquals("Granola", command.itemName)
        assertEquals(ReusableEntityKind.COMPOUND, command.reusableEntityKind)
        assertEquals(9L, command.reusableEntityId)
    }

    @Test
    fun reusable_selection_before_load_preserves_loaded_record_fields() = runBlocking {
        val updatedCommands = mutableListOf<UpdateIngestionEventCommand>()
        val viewModel = buildViewModel(updatedCommands = updatedCommands)

        viewModel.onIntent(
            EditIngestionUiIntent.ApplyReusableSelection(
                IngestionReusableSelectionResult(
                    id = 12L,
                    name = "Greek yogurt",
                    kind = ReusableEntityKind.INGREDIENT
                )
            )
        )
        viewModel.onIntent(EditIngestionUiIntent.Load)
        viewModel.onIntent(EditIngestionUiIntent.Save)

        assertEquals("Greek yogurt", viewModel.state.value.itemName)
        assertEquals(2, viewModel.state.value.selectedQuantityLevel)
        assertEquals("with honey", viewModel.state.value.notes)
        assertEquals(existingOccurredAt, viewModel.state.value.occurredAtEpochMs)
        assertEquals(ReusableEntityKind.INGREDIENT, viewModel.state.value.selectedReusableEntityKind)
        assertEquals(12L, viewModel.state.value.selectedReusableEntityId)
        assertEquals("Greek yogurt", updatedCommands.single().itemName)
        assertEquals(2, updatedCommands.single().quantityLevel)
        assertEquals("with honey", updatedCommands.single().notes)
        assertEquals(existingOccurredAt, updatedCommands.single().occurredAtEpochMs)
    }

    @Test
    fun partial_reusable_identity_clears_link_and_blocks_manual_save() = runBlocking {
        val updatedCommands = mutableListOf<UpdateIngestionEventCommand>()
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
                        reusableEntityKind = ReusableEntityKind.INGREDIENT,
                        reusableEntityId = null
                    )
                )
            }

            override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                updatedCommands += command
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
        viewModel.onIntent(EditIngestionUiIntent.UpdateItemName("Manual oat milk"))
        viewModel.onIntent(EditIngestionUiIntent.Save)

        assertEquals("Manual oat milk", viewModel.state.value.itemName)
        assertNull(viewModel.state.value.selectedReusableEntityKind)
        assertNull(viewModel.state.value.selectedReusableEntityId)
        assertTrue(!viewModel.state.value.canSave)
        assertTrue(updatedCommands.isEmpty())
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
        assertEquals("Saved reusable item was retired. Select another saved item before saving.", viewModel.state.value.formMessage)
        assertTrue(!viewModel.state.value.canSave)
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
        assertEquals("Saved reusable item was retired. Select another saved item before saving.", viewModel.state.value.formMessage)
        assertTrue(!viewModel.state.value.canSave)
    }

    private fun buildViewModel(
        updatedCommands: MutableList<UpdateIngestionEventCommand> = mutableListOf(),
        linked: Boolean = true
    ): EditIngestionViewModel {
        val repository = object : IngestionRepository {
            override suspend fun createIngestion(command: io.github.mateovich.allergotracker.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")

            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> {
                return AppResult.Success(
                    IngestionEvent(
                        id = id,
                        itemName = "Tea",
                        quantityLevel = 2,
                        occurredAtEpochMs = existingOccurredAt,
                        notes = "with honey",
                        reusableEntityKind = ReusableEntityKind.INGREDIENT.takeIf { linked },
                        reusableEntityId = 5L.takeIf { linked }
                    )
                )
            }

            override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                updatedCommands += command
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

    private fun buildReusableLinkedViewModel(
        kind: ReusableEntityKind = ReusableEntityKind.INGREDIENT,
        updatedCommands: MutableList<UpdateIngestionEventCommand> = mutableListOf()
    ): EditIngestionViewModel {
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
                        reusableEntityKind = kind,
                        reusableEntityId = 11L
                    )
                )
            }

            override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                updatedCommands += command
                return AppResult.Success(
                    IngestionEvent(
                        id = command.id,
                        itemName = command.itemName,
                        quantityLevel = command.quantityLevel ?: 0,
                        occurredAtEpochMs = command.occurredAtEpochMs,
                        notes = command.notes,
                        reusableEntityKind = command.reusableEntityKind,
                        reusableEntityId = command.reusableEntityId
                    )
                )
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
