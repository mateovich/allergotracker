package io.github.mateovich.allergotracker.features.compounds.ui

import io.github.mateovich.allergotracker.MainDispatcherRule
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.compounds.domain.CompoundCompositionStatus
import io.github.mateovich.allergotracker.features.compounds.domain.CreateReusableCompoundCommand
import io.github.mateovich.allergotracker.features.compounds.domain.CreateReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.ReplaceIngredientWithCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompound
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompoundRepository
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import io.github.mateovich.allergotracker.features.ingestion.ui.IngestionReusableSelectionResult
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientCommand
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredient
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredientRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateCompoundReplaceIngredientViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val oatMilk = IngestionReusableSelectionResult(id = 7L, name = "Oat milk", kind = ReusableEntityKind.INGREDIENT)

    @Test
    fun replacing_view_model_exposes_ingredient_name_and_prefills_it() {
        val world = World()
        val viewModel = world.viewModel()

        assertEquals("Oat milk", viewModel.state.value.replacedIngredientName)
        assertEquals("Oat milk", viewModel.state.value.name)
        assertTrue(viewModel.state.value.components.isEmpty())
    }

    @Test
    fun replaced_ingredient_is_hidden_from_component_suggestions() {
        val viewModel = World().viewModel()

        viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentSearchQuery("oat"))

        assertTrue(viewModel.state.value.filteredComponents.none { it.id == 7L })
        assertTrue(viewModel.state.value.filteredComponents.any { it.id == 8L })
    }

    @Test
    fun save_asks_for_confirmation_with_impact_and_does_not_replace_yet() {
        val world = World(ingestionRefs = 3, componentRefs = 2)
        val viewModel = world.viewModel()
        viewModel.addOatsAt100()

        viewModel.onIntent(CreateCompoundUiIntent.Save)

        val confirmation = viewModel.state.value.replacementConfirmation
        assertEquals(ReplaceIngredientConfirmationUiModel("Oat milk", 3, 2), confirmation)
        assertEquals(0, world.replaceCalls)
        assertEquals(0, world.plainCreateCalls)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun dismissing_confirmation_changes_nothing() {
        val world = World()
        val viewModel = world.viewModel()
        viewModel.addOatsAt100()
        viewModel.onIntent(CreateCompoundUiIntent.Save)

        viewModel.onIntent(CreateCompoundUiIntent.DismissReplacementConfirmation)

        assertNull(viewModel.state.value.replacementConfirmation)
        assertEquals(0, world.replaceCalls)
        assertEquals(0, world.plainCreateCalls)
    }

    @Test
    fun confirming_replaces_and_navigates_back_with_the_new_compound() = runBlocking {
        val world = World(ingestionRefs = 1)
        val viewModel = world.viewModel()
        viewModel.addOatsAt100()
        viewModel.onIntent(CreateCompoundUiIntent.Save)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(CreateCompoundUiIntent.ConfirmReplacement)

        assertEquals(
            CreateCompoundUiEffect.NavigateBackWithSelection(
                IngestionReusableSelectionResult(id = 100L, name = "Oat milk", kind = ReusableEntityKind.COMPOUND)
            ),
            withTimeout(5_000) { effect.await() }
        )
        assertEquals(1, world.replaceCalls)
        assertEquals(7L, world.lastReplacedIngredientId)
        assertEquals(0, world.plainCreateCalls)
        assertNull(viewModel.state.value.replacementConfirmation)
    }

    @Test
    fun replacement_error_is_shown_and_keeps_the_form_for_retry() {
        val world = World(replaceError = DomainError.IntegrityError("Replacing \"Oat milk\" would make a compound contain itself."))
        val viewModel = world.viewModel()
        viewModel.addOatsAt100()
        viewModel.onIntent(CreateCompoundUiIntent.Save)

        viewModel.onIntent(CreateCompoundUiIntent.ConfirmReplacement)

        assertTrue(viewModel.state.value.isFormMessageError)
        assertTrue(viewModel.state.value.formMessage.contains("contain itself"))
        assertFalse(viewModel.state.value.isSaving)
        assertEquals(1, viewModel.state.value.components.size)
    }

    @Test
    fun save_without_a_valid_composition_does_not_ask_for_confirmation() {
        val world = World()
        val viewModel = world.viewModel()

        viewModel.onIntent(CreateCompoundUiIntent.Save)

        assertNull(viewModel.state.value.replacementConfirmation)
        assertFalse(viewModel.state.value.canSave)
        assertEquals(0, world.replaceCalls)
    }

    @Test
    fun compound_without_seed_ingredient_still_saves_directly() = runBlocking {
        val world = World()
        val viewModel = world.viewModel(seed = null, name = "Breakfast")
        viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentSearchQuery("oat"))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentProportion(index = 0, value = "100"))
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(CreateCompoundUiIntent.Save)

        assertEquals(1, world.plainCreateCalls)
        assertEquals(0, world.replaceCalls)
        assertNull(viewModel.state.value.replacedIngredientName)
        assertTrue(withTimeout(5_000) { effect.await() } is CreateCompoundUiEffect.NavigateBackWithSelection)
    }

    private fun CreateCompoundViewModel.addOatsAt100() {
        onIntent(CreateCompoundUiIntent.UpdateComponentSearchQuery("oat"))
        onIntent(CreateCompoundUiIntent.AddIngredientComponent(8L))
        onIntent(CreateCompoundUiIntent.UpdateComponentProportion(index = 0, value = "100"))
    }

    private inner class World(
        private val ingestionRefs: Int = 0,
        private val componentRefs: Int = 0,
        private val replaceError: DomainError? = null
    ) {
        var plainCreateCalls = 0
        var replaceCalls = 0
        var lastReplacedIngredientId: Long? = null

        private val ingredients = listOf(
            ReusableIngredient(7L, "Oat milk", null, 1L, 2L),
            ReusableIngredient(8L, "Oats", null, 3L, 4L)
        )

        private fun compoundFor(command: CreateReusableCompoundCommand) = ReusableCompound(
            id = 100L,
            name = command.name,
            notes = command.notes,
            compositionStatus = CompoundCompositionStatus.COMPLETE,
            components = emptyList(),
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L
        )

        fun viewModel(
            seed: IngestionReusableSelectionResult? = oatMilk,
            name: String = "Oat milk"
        ): CreateCompoundViewModel {
            val createUseCase = CreateReusableCompoundUseCase(
                repository = object : ReusableCompoundRepository {
                    override suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound> {
                        plainCreateCalls += 1
                        return AppResult.Success(compoundFor(command))
                    }

                    override fun observeCompounds(): Flow<List<ReusableCompound>> = flowOf(emptyList())
                }
            )
            val replaceUseCase = ReplaceIngredientWithCompoundUseCase(
                createCompound = { command -> AppResult.Success(compoundFor(command)) },
                getIngredientName = { AppResult.Success("Oat milk") },
                deleteIngredient = { AppResult.Success(Unit) },
                countIngestionReferences = { ingestionRefs },
                countComponentReferences = { componentRefs },
                findParentCompoundIds = { emptyList() },
                findChildCompoundEdges = { emptyList() },
                reassignIngestionReferences = { ingredientId, _, _ ->
                    replaceCalls += 1
                    lastReplacedIngredientId = ingredientId
                    ingestionRefs
                },
                replaceComponentReferences = { _, _ -> componentRefs },
                runAtomically = { block ->
                    replaceError?.let { AppResult.Error(it) } ?: block()
                }
            )
            return CreateCompoundViewModel(
                createReusableCompoundUseCase = createUseCase,
                createReusableIngredientUseCase = CreateReusableIngredientUseCase(
                    repository = object : ReusableIngredientRepository {
                        override suspend fun createIngredient(command: CreateReusableIngredientCommand): AppResult<ReusableIngredient> =
                            error("unused")

                        override fun observeIngredients(): Flow<List<ReusableIngredient>> = flowOf(ingredients)
                    }
                ),
                observeReusableIngredients = { flowOf(ingredients) },
                observeReusableCompounds = { flowOf(emptyList()) },
                replaceIngredientWithCompoundUseCase = replaceUseCase,
                initialName = name,
                initialSeedIngredient = seed
            )
        }
    }
}
