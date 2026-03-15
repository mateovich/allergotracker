package com.example.app.features.ingestion.ui

import com.example.app.MainDispatcherRule
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingestion.domain.GetIngestionEventUseCase
import com.example.app.features.ingestion.domain.IngestionEvent
import com.example.app.features.ingestion.domain.IngestionRepository
import com.example.app.features.ingestion.domain.UpdateIngestionEventCommand
import com.example.app.features.ingestion.domain.UpdateIngestionEventUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        assertEquals(2, viewModel.state.value.selectedQuantityLevel)
        assertEquals("with honey", viewModel.state.value.notes)
    }

    @Test
    fun invalid_save_keeps_user_input_and_shows_inline_errors() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditIngestionUiIntent.Load)
        viewModel.onIntent(EditIngestionUiIntent.UpdateItemName("   "))
        viewModel.onIntent(EditIngestionUiIntent.Save)

        assertEquals("   ", viewModel.state.value.itemName)
        assertEquals("Enter what you ingested.", viewModel.state.value.itemNameError)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())
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
    fun time_preset_reanchors_edit_time_from_current_time() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditIngestionUiIntent.Load)

        viewModel.onIntent(EditIngestionUiIntent.SelectTimePreset(IngestionTimePreset.ONE_HOUR_AGO))

        assertEquals(fixedNow - (60L * 60L * 1000L), viewModel.state.value.occurredAtEpochMs)
    }

    @Test
    fun failed_load_can_retry_and_recover() = runBlocking {
        var loadAttempts = 0
        val repository = object : IngestionRepository {
            override suspend fun createIngestion(command: com.example.app.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")

            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> {
                loadAttempts += 1
                return if (loadAttempts == 1) {
                    AppResult.Error(com.example.app.core.common.error.DomainError.PersistenceError("Couldn't load ingestion locally. Try again."))
                } else {
                    AppResult.Success(IngestionEvent(id, "Tea", 2, existingOccurredAt, "with honey"))
                }
            }

            override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                return AppResult.Success(IngestionEvent(command.id, command.itemName, command.quantityLevel ?: 0, command.occurredAtEpochMs, command.notes))
            }
        }
        val viewModel = EditIngestionViewModel(
            recordId = 7L,
            getIngestionEventUseCase = GetIngestionEventUseCase(repository),
            updateIngestionEventUseCase = UpdateIngestionEventUseCase(repository, com.example.app.features.ingestion.domain.CurrentTimeProvider { fixedNow }),
            currentTimeProvider = com.example.app.features.ingestion.domain.CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditIngestionUiIntent.Load)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())

        viewModel.onIntent(EditIngestionUiIntent.Load)

        assertEquals("Tea", viewModel.state.value.itemName)
        assertEquals(2, loadAttempts)
    }

    private fun buildViewModel(): EditIngestionViewModel {
        val repository = object : IngestionRepository {
            override suspend fun createIngestion(command: com.example.app.features.ingestion.domain.CreateIngestionEventCommand): AppResult<IngestionEvent> = error("unused")

            override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> {
                return AppResult.Success(IngestionEvent(id, "Tea", 2, existingOccurredAt, "with honey"))
            }

            override suspend fun updateIngestion(command: UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                return AppResult.Success(IngestionEvent(command.id, command.itemName, command.quantityLevel ?: 0, command.occurredAtEpochMs, command.notes))
            }
        }
        return EditIngestionViewModel(
            recordId = 7L,
            getIngestionEventUseCase = GetIngestionEventUseCase(repository),
            updateIngestionEventUseCase = UpdateIngestionEventUseCase(repository, com.example.app.features.ingestion.domain.CurrentTimeProvider { fixedNow }),
            currentTimeProvider = com.example.app.features.ingestion.domain.CurrentTimeProvider { fixedNow }
        )
    }
}
