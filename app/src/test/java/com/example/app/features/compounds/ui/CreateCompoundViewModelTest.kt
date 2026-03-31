package com.example.app.features.compounds.ui

import com.example.app.MainDispatcherRule
import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.domain.CompoundCompositionStatus
import com.example.app.features.compounds.domain.CreateReusableCompoundCommand
import com.example.app.features.compounds.domain.CreateReusableCompoundUseCase
import com.example.app.features.compounds.domain.ReusableCompound
import com.example.app.features.compounds.domain.ReusableCompoundRepository
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.ingestion.ui.IngestionReusableSelectionResult
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateCompoundViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun invalid_save_surfaces_inline_name_error() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.Save)

        assertEquals("Enter a compound name.", viewModel.state.value.nameError)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())
    }

    @Test
    fun successful_save_returns_selected_name_to_parent_flow() = runBlocking {
        val viewModel = buildViewModel()
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(CreateCompoundUiIntent.UpdateName("Breakfast mix"))
        viewModel.onIntent(CreateCompoundUiIntent.Save)

        assertEquals(
            CreateCompoundUiEffect.NavigateBackWithSelection(
                IngestionReusableSelectionResult(1L, "Breakfast mix", ReusableEntityKind.COMPOUND)
            ),
            effect.await()
        )
    }

    private fun buildViewModel(): CreateCompoundViewModel {
        return CreateCompoundViewModel(
            createReusableCompoundUseCase = CreateReusableCompoundUseCase(
                repository = object : ReusableCompoundRepository {
                    override suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound> {
                        return AppResult.Success(
                            ReusableCompound(
                                id = 1L,
                                name = command.name,
                                notes = command.notes,
                                compositionStatus = CompoundCompositionStatus.DRAFT,
                                components = emptyList(),
                                createdAtEpochMs = 1L,
                                updatedAtEpochMs = 1L
                            )
                        )
                    }

                    override fun observeCompounds(): Flow<List<ReusableCompound>> = emptyFlow()
                }
            )
        )
    }
}
