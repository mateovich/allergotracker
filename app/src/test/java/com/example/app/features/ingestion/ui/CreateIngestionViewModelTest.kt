package com.example.app.features.ingestion.ui

import com.example.app.MainDispatcherRule
import com.example.app.features.ingestion.domain.CreateIngestionDefaultsUseCase
import com.example.app.features.ingestion.domain.CreateIngestionEventCommand
import com.example.app.features.ingestion.domain.CreateIngestionEventUseCase
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import com.example.app.features.ingestion.domain.IngestionEvent
import com.example.app.features.ingestion.domain.IngestionRepository
import com.example.app.core.common.result.AppResult
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateIngestionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedNow = 1_700_000_000_000L

    @Test
    fun initializes_with_contextual_defaults_when_form_opens() {
        val viewModel = buildViewModel()
        val expectedTimeLabel = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(fixedNow))

        assertEquals(expectedTimeLabel, viewModel.state.value.occurredAtLabel)
        assertEquals(IngestionTimePreset.NOW, viewModel.state.value.selectedTimePreset)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun invalid_save_keeps_user_input_and_shows_inline_errors() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateIngestionUiIntent.UpdateItemName("Yogurt"))
        viewModel.onIntent(CreateIngestionUiIntent.Save)

        assertEquals("Yogurt", viewModel.state.value.itemName)
        assertEquals("Choose a quantity.", viewModel.state.value.quantityError)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())
    }

    @Test
    fun successful_save_emits_navigation_back_to_records() = runBlocking {
        val viewModel = buildViewModel()
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(CreateIngestionUiIntent.UpdateItemName("Tea"))
        viewModel.onIntent(CreateIngestionUiIntent.SelectQuantityLevel(2))
        viewModel.onIntent(CreateIngestionUiIntent.Save)

        assertEquals(CreateIngestionUiEffect.NavigateToRecords, effect.await())
    }

    private fun buildViewModel(): CreateIngestionViewModel {
        val timeProvider = object : CurrentTimeProvider {
            override fun nowEpochMillis(): Long = fixedNow
        }
        return CreateIngestionViewModel(
            createIngestionDefaultsUseCase = CreateIngestionDefaultsUseCase(timeProvider),
            createIngestionEventUseCase = CreateIngestionEventUseCase(
                repository = object : IngestionRepository {
                    override suspend fun createIngestion(command: CreateIngestionEventCommand): AppResult<IngestionEvent> {
                        return AppResult.Success(
                            IngestionEvent(
                                id = 7L,
                                itemName = command.itemName,
                                quantityLevel = command.quantityLevel ?: 0,
                                occurredAtEpochMs = command.occurredAtEpochMs,
                                notes = command.notes
                            )
                        )
                    }

                    override suspend fun getIngestion(id: Long): AppResult<IngestionEvent> {
                        error("Not used in create tests")
                    }

                    override suspend fun updateIngestion(command: com.example.app.features.ingestion.domain.UpdateIngestionEventCommand): AppResult<IngestionEvent> {
                        error("Not used in create tests")
                    }
                },
                currentTimeProvider = timeProvider
            )
        )
    }
}
