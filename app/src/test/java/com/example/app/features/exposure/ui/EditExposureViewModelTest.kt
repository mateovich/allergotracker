package com.example.app.features.exposure.ui

import com.example.app.MainDispatcherRule
import com.example.app.core.common.result.AppResult
import com.example.app.features.exposure.domain.ExposureEvent
import com.example.app.features.exposure.domain.ExposureRepository
import com.example.app.features.exposure.domain.GetExposureEventUseCase
import com.example.app.features.exposure.domain.UpdateExposureEventCommand
import com.example.app.features.exposure.domain.UpdateExposureEventUseCase
import com.example.app.features.ingestion.domain.CurrentTimeProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditExposureViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixedNow = 1_700_000_000_000L
    private val existingOccurredAt = fixedNow - (2L * 60L * 60L * 1000L)

    @Test
    fun load_prefills_state_from_existing_record() = runBlocking {
        val viewModel = buildViewModel()

        viewModel.onIntent(EditExposureUiIntent.Load)

        assertEquals("Pollen", viewModel.state.value.exposureType)
        assertEquals("outside", viewModel.state.value.notes)
        assertTrue(viewModel.state.value.occurredAtInput.isNotBlank())
    }

    @Test
    fun invalid_save_keeps_user_input_and_shows_inline_errors() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditExposureUiIntent.Load)
        viewModel.onIntent(EditExposureUiIntent.UpdateExposureType("   "))
        viewModel.onIntent(EditExposureUiIntent.Save)

        assertEquals("   ", viewModel.state.value.exposureType)
        assertEquals("Enter what you were exposed to.", viewModel.state.value.exposureTypeError)
    }

    @Test
    fun successful_save_navigates_back_to_records() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditExposureUiIntent.Load)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditExposureUiIntent.UpdateExposureType("Dust"))
        viewModel.onIntent(EditExposureUiIntent.Save)

        assertEquals(EditExposureUiEffect.NavigateToRecords, effect.await())
    }

    @Test
    fun valid_manual_datetime_input_updates_visible_time_label() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditExposureUiIntent.Load)

        viewModel.onIntent(EditExposureUiIntent.UpdateOccurredAtInput("2023-11-14 21:13"))

        assertEquals("21:13", viewModel.state.value.occurredAtLabel)
    }

    @Test
    fun time_preset_reanchors_edit_time_from_current_time() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onIntent(EditExposureUiIntent.Load)

        viewModel.onIntent(EditExposureUiIntent.SelectTimePreset(ExposureTimePreset.ONE_HOUR_AGO))

        assertEquals(fixedNow - (60L * 60L * 1000L), viewModel.state.value.occurredAtEpochMs)
    }

    @Test
    fun failed_load_can_retry_and_recover() = runBlocking {
        var loadAttempts = 0
        val repository = object : ExposureRepository {
            override suspend fun createExposure(command: com.example.app.features.exposure.domain.CreateExposureEventCommand): AppResult<ExposureEvent> = error("unused")

            override suspend fun getExposure(id: Long): AppResult<ExposureEvent> {
                loadAttempts += 1
                return if (loadAttempts == 1) {
                    AppResult.Error(com.example.app.core.common.error.DomainError.PersistenceError("Couldn't load exposure locally. Try again."))
                } else {
                    AppResult.Success(ExposureEvent(id, "Pollen", existingOccurredAt, "outside"))
                }
            }

            override suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent> {
                return AppResult.Success(ExposureEvent(command.id, command.exposureType, command.occurredAtEpochMs, command.notes))
            }
        }
        val viewModel = EditExposureViewModel(
            recordId = 5L,
            getExposureEventUseCase = GetExposureEventUseCase(repository),
            updateExposureEventUseCase = UpdateExposureEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )

        viewModel.onIntent(EditExposureUiIntent.Load)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())

        viewModel.onIntent(EditExposureUiIntent.Load)

        assertEquals("Pollen", viewModel.state.value.exposureType)
        assertEquals(2, loadAttempts)
    }

    private fun buildViewModel(): EditExposureViewModel {
        val repository = object : ExposureRepository {
            override suspend fun createExposure(command: com.example.app.features.exposure.domain.CreateExposureEventCommand): AppResult<ExposureEvent> = error("unused")

            override suspend fun getExposure(id: Long): AppResult<ExposureEvent> {
                return AppResult.Success(ExposureEvent(id, "Pollen", existingOccurredAt, "outside"))
            }

            override suspend fun updateExposure(command: UpdateExposureEventCommand): AppResult<ExposureEvent> {
                return AppResult.Success(ExposureEvent(command.id, command.exposureType, command.occurredAtEpochMs, command.notes))
            }
        }
        return EditExposureViewModel(
            recordId = 5L,
            getExposureEventUseCase = GetExposureEventUseCase(repository),
            updateExposureEventUseCase = UpdateExposureEventUseCase(repository, CurrentTimeProvider { fixedNow }),
            currentTimeProvider = CurrentTimeProvider { fixedNow }
        )
    }
}
