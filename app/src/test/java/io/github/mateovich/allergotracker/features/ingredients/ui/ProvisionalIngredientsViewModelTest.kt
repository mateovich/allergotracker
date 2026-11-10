package io.github.mateovich.allergotracker.features.ingredients.ui

import io.github.mateovich.allergotracker.MainDispatcherRule
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientCommand
import io.github.mateovich.allergotracker.features.ingredients.domain.GetReusableIngredientMergePreviewUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.GetReusableIngredientRetirementImpactUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.GetReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.MergeReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ObserveReusableIngredientsUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.RetireReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredient
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredientRepository
import io.github.mateovich.allergotracker.features.ingredients.domain.SetIngredientProvisionalUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.UpdateReusableIngredientUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProvisionalIngredientsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun list_shows_only_provisional_ingredients() {
        val repository = FakeRepository(
            listOf(
                ReusableIngredient(1L, "Oat milk", null, 1L, 1L),
                ReusableIngredient(2L, "Pizza", "from the corner shop", 1L, 1L, isProvisional = true),
                ReusableIngredient(3L, "Lasagna", null, 1L, 1L, isProvisional = true)
            )
        )

        val viewModel = ProvisionalIngredientsViewModel(ObserveReusableIngredientsUseCase(repository))

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("Pizza", "Lasagna"), viewModel.state.value.items.map { it.name })
        assertEquals("from the corner shop", viewModel.state.value.items.first().notes)
    }

    @Test
    fun list_is_empty_when_there_are_no_provisional_ingredients() {
        val repository = FakeRepository(listOf(ReusableIngredient(1L, "Oat milk", null, 1L, 1L)))

        val viewModel = ProvisionalIngredientsViewModel(ObserveReusableIngredientsUseCase(repository))

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.items.isEmpty())
    }

    @Test
    fun list_updates_when_an_item_stops_being_provisional() {
        val repository = FakeRepository(listOf(ReusableIngredient(2L, "Pizza", null, 1L, 1L, isProvisional = true)))
        val viewModel = ProvisionalIngredientsViewModel(ObserveReusableIngredientsUseCase(repository))

        runBlocking { repository.setIngredientProvisional(2L, false) }

        assertTrue(viewModel.state.value.items.isEmpty())
    }

    @Test
    fun edit_ingredient_loads_the_provisional_flag() {
        val viewModel = buildEditViewModel(FakeRepository(listOf(provisional())))

        viewModel.onIntent(EditIngredientUiIntent.Load)

        assertTrue(viewModel.state.value.isProvisional)
    }

    @Test
    fun keep_as_regular_clears_the_flag() {
        val repository = FakeRepository(listOf(provisional()))
        val viewModel = buildEditViewModel(repository)
        viewModel.onIntent(EditIngredientUiIntent.Load)

        viewModel.onIntent(EditIngredientUiIntent.KeepAsRegularIngredient)

        assertFalse(viewModel.state.value.isProvisional)
        assertFalse(repository.current(2L).isProvisional)
        assertEquals("", viewModel.state.value.formMessage)
    }

    @Test
    fun keep_as_regular_failure_keeps_the_flag_and_shows_the_message() {
        val repository = FakeRepository(listOf(provisional()), setProvisionalError = DomainError.PersistenceError("Couldn't update ingredient locally. Try again."))
        val viewModel = buildEditViewModel(repository)
        viewModel.onIntent(EditIngredientUiIntent.Load)

        viewModel.onIntent(EditIngredientUiIntent.KeepAsRegularIngredient)

        assertTrue(viewModel.state.value.isProvisional)
        assertEquals("Couldn't update ingredient locally. Try again.", viewModel.state.value.formMessage)
    }

    @Test
    fun keep_as_regular_is_ignored_for_regular_ingredients() {
        val repository = FakeRepository(listOf(ReusableIngredient(2L, "Pizza", null, 1L, 1L)))
        val viewModel = buildEditViewModel(repository)
        viewModel.onIntent(EditIngredientUiIntent.Load)

        viewModel.onIntent(EditIngredientUiIntent.KeepAsRegularIngredient)

        assertEquals(0, repository.setProvisionalCalls)
    }

    private fun provisional() = ReusableIngredient(2L, "Pizza", null, 1L, 1L, isProvisional = true)

    private fun buildEditViewModel(repository: FakeRepository): EditIngredientViewModel {
        val impact = GetReusableIngredientRetirementImpactUseCase(repository)
        val preview = GetReusableIngredientMergePreviewUseCase(repository)
        return EditIngredientViewModel(
            ingredientId = 2L,
            getReusableIngredientUseCase = GetReusableIngredientUseCase(repository),
            updateReusableIngredientUseCase = UpdateReusableIngredientUseCase(repository),
            observeReusableIngredients = ObserveReusableIngredientsUseCase(repository),
            getReusableIngredientMergePreviewUseCase = preview,
            mergeReusableIngredientUseCase = MergeReusableIngredientUseCase(
                getPreview = preview,
                getIngredient = repository::getIngredient,
                deleteIngredient = repository::deleteIngredient
            ),
            getReusableIngredientRetirementImpactUseCase = impact,
            retireReusableIngredientUseCase = RetireReusableIngredientUseCase(repository, impact),
            setIngredientProvisionalUseCase = SetIngredientProvisionalUseCase(repository)
        )
    }

    private class FakeRepository(
        initial: List<ReusableIngredient>,
        private val setProvisionalError: DomainError? = null
    ) : ReusableIngredientRepository {
        private val ingredients = MutableStateFlow(initial)
        var setProvisionalCalls = 0

        fun current(id: Long): ReusableIngredient = ingredients.value.first { it.id == id }

        override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> =
            error("unused")

        override suspend fun getIngredient(id: Long): AppResult<ReusableIngredient> =
            ingredients.value.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
                ?: AppResult.Error(DomainError.IntegrityError("Saved ingredient is no longer available."))

        override suspend fun deleteIngredient(id: Long): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun setIngredientProvisional(id: Long, isProvisional: Boolean): AppResult<ReusableIngredient> {
            setProvisionalCalls += 1
            setProvisionalError?.let { return AppResult.Error(it) }
            val updated = current(id).copy(isProvisional = isProvisional)
            ingredients.value = ingredients.value.map { if (it.id == id) updated else it }
            return AppResult.Success(updated)
        }

        override fun observeIngredients(): Flow<List<ReusableIngredient>> = ingredients
    }
}
