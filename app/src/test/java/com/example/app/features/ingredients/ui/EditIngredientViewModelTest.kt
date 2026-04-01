package com.example.app.features.ingredients.ui

import com.example.app.MainDispatcherRule
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.ingredients.domain.GetReusableIngredientUseCase
import com.example.app.features.ingredients.domain.ReusableIngredient
import com.example.app.features.ingredients.domain.ReusableIngredientRepository
import com.example.app.features.ingredients.domain.UpdateReusableIngredientCommand
import com.example.app.features.ingredients.domain.UpdateReusableIngredientUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditIngredientViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun load_populates_existing_value_and_successful_save_returns_updated_selection() = runBlocking {
        val repository = FakeEditIngredientRepository()
        val viewModel = EditIngredientViewModel(
            ingredientId = 7L,
            getReusableIngredientUseCase = GetReusableIngredientUseCase(repository),
            updateReusableIngredientUseCase = UpdateReusableIngredientUseCase(repository)
        )
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditIngredientUiIntent.Load)
        viewModel.onIntent(EditIngredientUiIntent.UpdateName("Oat milk plus"))
        viewModel.onIntent(EditIngredientUiIntent.Save)

        val navigation = effect.await() as EditIngredientUiEffect.NavigateBackWithSelection

        assertEquals("Oat milk plus", repository.updatedCommand?.name)
        assertEquals(7L, navigation.result.id)
        assertEquals("Oat milk plus", navigation.result.name)
    }

    @Test
    fun validation_error_surfaces_inline_message() = runBlocking {
        val repository = FakeEditIngredientRepository(
            updateResult = AppResult.Error(
                DomainError.ValidationError(
                    "Please fix the highlighted fields.",
                    mapOf("name" to "An ingredient with this name already exists.")
                )
            )
        )
        val viewModel = EditIngredientViewModel(
            ingredientId = 7L,
            getReusableIngredientUseCase = GetReusableIngredientUseCase(repository),
            updateReusableIngredientUseCase = UpdateReusableIngredientUseCase(repository)
        )

        viewModel.onIntent(EditIngredientUiIntent.Load)
        viewModel.onIntent(EditIngredientUiIntent.Save)

        assertEquals("An ingredient with this name already exists.", viewModel.state.value.nameError)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())
    }

    @Test
    fun blank_name_shows_inline_error_and_disables_save_immediately() = runBlocking {
        val repository = FakeEditIngredientRepository()
        val viewModel = EditIngredientViewModel(
            ingredientId = 7L,
            getReusableIngredientUseCase = GetReusableIngredientUseCase(repository),
            updateReusableIngredientUseCase = UpdateReusableIngredientUseCase(repository)
        )

        viewModel.onIntent(EditIngredientUiIntent.Load)
        viewModel.onIntent(EditIngredientUiIntent.UpdateName("   "))

        assertEquals("Enter an ingredient name.", viewModel.state.value.nameError)
        assertFalse(viewModel.state.value.canSave)
    }
}

private class FakeEditIngredientRepository(
    private val updateResult: AppResult<ReusableIngredient>? = null
) : ReusableIngredientRepository {
    var updatedCommand: UpdateReusableIngredientCommand? = null

    override suspend fun createIngredient(command: com.example.app.features.ingredients.domain.CreateReusableIngredientCommand): AppResult<ReusableIngredient> {
        error("unused")
    }

    override suspend fun getIngredient(id: Long): AppResult<ReusableIngredient> {
        return AppResult.Success(
            ReusableIngredient(
                id = id,
                name = "Oat milk",
                notes = "Staple",
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )
    }

    override suspend fun updateIngredient(command: UpdateReusableIngredientCommand): AppResult<ReusableIngredient> {
        updatedCommand = command
        return updateResult ?: AppResult.Success(
            ReusableIngredient(
                id = command.id,
                name = command.name,
                notes = command.notes,
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 2L
            )
        )
    }

    override fun observeIngredients(): Flow<List<ReusableIngredient>> = emptyFlow()
}
