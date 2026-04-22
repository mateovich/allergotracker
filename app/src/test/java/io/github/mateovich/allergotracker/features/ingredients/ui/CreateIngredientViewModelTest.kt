package io.github.mateovich.allergotracker.features.ingredients.ui

import io.github.mateovich.allergotracker.MainDispatcherRule
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import io.github.mateovich.allergotracker.features.ingestion.ui.IngestionReusableSelectionResult
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientCommand
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredient
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredientRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateIngredientViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun invalid_save_surfaces_inline_name_error() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateIngredientUiIntent.Save)

        assertEquals("Enter an ingredient name.", viewModel.state.value.nameError)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())
    }

    @Test
    fun successful_save_returns_selected_name_to_parent_flow() = runBlocking {
        val viewModel = buildViewModel()
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(CreateIngredientUiIntent.UpdateName("Oat milk"))
        viewModel.onIntent(CreateIngredientUiIntent.Save)

        assertEquals(
            CreateIngredientUiEffect.NavigateBackWithSelection(
                IngestionReusableSelectionResult(1L, "Oat milk", ReusableEntityKind.INGREDIENT)
            ),
            effect.await()
        )
    }

    private fun buildViewModel(): CreateIngredientViewModel {
        return CreateIngredientViewModel(
            createReusableIngredientUseCase = CreateReusableIngredientUseCase(
                repository = object : ReusableIngredientRepository {
                    override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> {
                        return AppResult.Success(
                            ReusableIngredient(
                                id = 1L,
                                name = command.name,
                                notes = command.notes,
                                createdAtEpochMs = 1L,
                                updatedAtEpochMs = 1L
                            )
                        )
                    }

                    override fun observeIngredients(): Flow<List<ReusableIngredient>> = emptyFlow()
                }
            )
        )
    }
}
