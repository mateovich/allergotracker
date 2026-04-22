package io.github.mateovich.allergotracker.features.compounds.ui

import io.github.mateovich.allergotracker.MainDispatcherRule
import io.github.mateovich.allergotracker.core.common.error.DomainError
import io.github.mateovich.allergotracker.core.common.result.AppResult
import io.github.mateovich.allergotracker.features.compounds.domain.CompoundComponentKind
import io.github.mateovich.allergotracker.features.compounds.domain.CompoundCompositionStatus
import io.github.mateovich.allergotracker.features.compounds.domain.CreateReusableCompoundComponentCommand
import io.github.mateovich.allergotracker.features.compounds.domain.CreateReusableCompoundCommand
import io.github.mateovich.allergotracker.features.compounds.domain.CreateReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompound
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompoundComponent
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompoundRepository
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredient
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import io.github.mateovich.allergotracker.features.ingestion.ui.IngestionReusableSelectionResult
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals("Add at least one component.", viewModel.state.value.componentsError)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())
    }

    @Test
    fun successful_save_returns_selected_name_to_parent_flow() = runBlocking {
        val viewModel = buildViewModel()
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(CreateCompoundUiIntent.UpdateName("Breakfast mix"))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentProportion(index = 0, value = "100"))
        viewModel.onIntent(CreateCompoundUiIntent.Save)

        assertEquals(
            CreateCompoundUiEffect.NavigateBackWithSelection(
                IngestionReusableSelectionResult(1L, "Breakfast mix", ReusableEntityKind.COMPOUND)
            ),
            effect.await()
        )
    }

    @Test
    fun selecting_distinct_components_and_updating_proportion_keeps_inline_state_in_sync() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(8L))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentProportion(index = 0, value = "60"))

        val firstRow = viewModel.state.value.components[0]
        val secondRow = viewModel.state.value.components[1]
        assertEquals("Oat milk", firstRow.name)
        assertEquals("60", firstRow.proportionPercent)
        assertEquals("40", secondRow.proportionPercent)
        assertEquals("100% total", viewModel.state.value.totalPercentLabel)
        assertNull(viewModel.state.value.componentsError)
    }

    @Test
    fun duplicate_component_is_hidden_after_selection_and_stale_add_shows_feedback() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))

        assertEquals(1, viewModel.state.value.components.size)
        assertTrue(viewModel.state.value.filteredComponents.none { it.kind == "ingredient" && it.id == 7L })
        assertEquals(
            "This ingredient is already in the composition. Edit the existing row instead.",
            viewModel.state.value.formMessage
        )
    }

    @Test
    fun save_time_validation_maps_component_target_error_inline() {
        val viewModel = buildViewModel(
            createResult = AppResult.Error(
                DomainError.ValidationError(
                    message = "Please fix the highlighted fields.",
                    fieldErrors = mapOf(
                        "components[0].target" to "This ingredient is already in the composition. Edit the existing row instead."
                    )
                )
            )
        )

        viewModel.onIntent(CreateCompoundUiIntent.UpdateName("Breakfast mix"))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentProportion(index = 0, value = "100"))
        viewModel.onIntent(CreateCompoundUiIntent.Save)

        assertEquals(
            "This ingredient is already in the composition. Edit the existing row instead.",
            viewModel.state.value.components.first().targetError
        )
    }

    @Test
    fun selecting_compound_hides_cyclic_counterpart_from_picker() {
        val viewModel = buildViewModelWithCyclicCompounds()

        viewModel.onIntent(CreateCompoundUiIntent.AddCompoundComponent(20L))

        assertTrue(viewModel.state.value.filteredComponents.none { it.kind == "compound" && it.id == 21L })
        assertTrue(viewModel.state.value.filteredComponents.any { it.kind == "compound" && it.id == 22L })
    }

    @Test
    fun stale_cyclic_add_on_create_shows_recursive_feedback() {
        val viewModel = buildViewModelWithCyclicCompounds()

        viewModel.onIntent(CreateCompoundUiIntent.AddCompoundComponent(20L))
        viewModel.onIntent(CreateCompoundUiIntent.AddCompoundComponent(21L))

        assertEquals(1, viewModel.state.value.components.size)
        assertEquals(
            "This compound would create a recursive composition.",
            viewModel.state.value.formMessage
        )
        assertTrue(viewModel.state.value.isFormMessageError)
    }

    @Test
    fun decimal_proportions_keep_precise_total_label() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(8L))
        viewModel.onIntent(CreateCompoundUiIntent.AddCompoundComponent(9L))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentProportion(index = 0, value = "33.34"))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentProportion(index = 1, value = "33.34"))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentProportion(index = 2, value = "33.34"))

        assertEquals("100% total", viewModel.state.value.totalPercentLabel)
        assertEquals(10_000, viewModel.state.value.components.sumOf { it.proportionBasisPoints ?: 0 })
        assertTrue(viewModel.state.value.components.all { it.proportionBasisPoints != null })
    }

    @Test
    fun removed_component_returns_to_picker_after_deletion() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(8L))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentProportion(index = 0, value = "100"))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentProportion(index = 1, value = "0"))
        viewModel.onIntent(CreateCompoundUiIntent.RemoveComponent(1))

        assertTrue(viewModel.state.value.filteredComponents.any { it.kind == "ingredient" && it.id == 8L })
    }

    @Test
    fun zero_percent_component_disables_save_until_removed() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.UpdateName("Breakfast mix"))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(8L))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentProportion(index = 0, value = "100"))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentProportion(index = 1, value = "0"))

        assertEquals("100% total", viewModel.state.value.totalPercentLabel)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun applying_uniform_preset_rewrites_percentages_and_preserves_saveability() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.UpdateName("Breakfast mix"))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(8L))
        viewModel.onIntent(CreateCompoundUiIntent.AddCompoundComponent(9L))
        viewModel.onIntent(CreateCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.UNIFORM))

        assertEquals(listOf("33.33", "33.33", "33.34"), viewModel.state.value.components.map { it.proportionPercent })
        assertEquals(emptyList<Int>(), viewModel.state.value.touchedComponentOrder)
        assertEquals(CompoundCompositionPreset.UNIFORM, viewModel.state.value.selectedPreset)
        assertEquals("100% total", viewModel.state.value.totalPercentLabel)
        assertFalse(viewModel.state.value.isFormMessageError)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun applying_preset_without_components_surfaces_actionable_error() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.UNIFORM))

        assertEquals("Add at least one component before applying a preset.", viewModel.state.value.componentsError)
        assertEquals("Couldn't apply uniform preset to an empty composition.", viewModel.state.value.formMessage)
        assertTrue(viewModel.state.value.isFormMessageError)
        assertNull(viewModel.state.value.selectedPreset)
    }

    @Test
    fun applying_preset_clears_stale_component_errors() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.Save)
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(8L))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateName("Breakfast mix"))
        viewModel.onIntent(CreateCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.DECREASING))

        assertNull(viewModel.state.value.componentsError)
        assertNull(viewModel.state.value.totalError)
        assertTrue(viewModel.state.value.components.all { it.proportionError == null && it.targetError == null })
        assertEquals(listOf("66.67", "33.33"), viewModel.state.value.components.map { it.proportionPercent })
    }

    @Test
    fun updating_notes_clears_stale_form_message_state() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.UNIFORM))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateNotes("Fresh batch"))

        assertEquals("", viewModel.state.value.formMessage)
        assertFalse(viewModel.state.value.isFormMessageError)
    }

    @Test
    fun updating_notes_preserves_actionable_error_message() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.UNIFORM))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateNotes("Fresh batch"))

        assertEquals("Couldn't apply uniform preset to an empty composition.", viewModel.state.value.formMessage)
        assertTrue(viewModel.state.value.isFormMessageError)
    }

    @Test
    fun updating_name_preserves_actionable_error_message() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.UNIFORM))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateName("Breakfast mix"))

        assertEquals("Couldn't apply uniform preset to an empty composition.", viewModel.state.value.formMessage)
        assertTrue(viewModel.state.value.isFormMessageError)
    }

    @Test
    fun updating_name_clears_non_sticky_validation_message() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.Save)
        viewModel.onIntent(CreateCompoundUiIntent.UpdateName("Breakfast mix"))

        assertEquals("", viewModel.state.value.formMessage)
        assertFalse(viewModel.state.value.isFormMessageError)
    }

    private fun buildViewModel(
        createResult: AppResult<ReusableCompound>? = null
    ): CreateCompoundViewModel {
        val ingredients = MutableStateFlow(
            listOf(
                ReusableIngredient(
                    id = 7L,
                    name = "Oat milk",
                    notes = null,
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 2L
                ),
                ReusableIngredient(
                    id = 8L,
                    name = "Banana",
                    notes = null,
                    createdAtEpochMs = 3L,
                    updatedAtEpochMs = 4L
                )
            )
        )
        return CreateCompoundViewModel(
            createReusableCompoundUseCase = CreateReusableCompoundUseCase(
                repository = object : ReusableCompoundRepository {
                    override suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound> {
                        return createResult ?: AppResult.Success(
                            ReusableCompound(
                                id = 1L,
                                name = command.name,
                                notes = command.notes,
                                compositionStatus = CompoundCompositionStatus.COMPLETE,
                                components = command.components.mapIndexed { index, component ->
                                    ReusableCompoundComponent(
                                        id = index + 1L,
                                        position = index,
                                        kind = component.kind,
                                        ingredientId = component.reusableEntityId.takeIf {
                                            component.kind == CompoundComponentKind.INGREDIENT
                                        },
                                        childCompoundId = component.reusableEntityId.takeIf {
                                            component.kind == CompoundComponentKind.COMPOUND
                                        },
                                        proportionBasisPoints = component.proportionBasisPoints
                                    )
                                },
                                createdAtEpochMs = 1L,
                                updatedAtEpochMs = 1L
                            )
                        )
                    }

                    override fun observeCompounds(): Flow<List<ReusableCompound>> = emptyFlow()
                }
            ),
            observeReusableIngredients = { ingredients },
            observeReusableCompounds = {
                MutableStateFlow(
                    listOf(
                        ReusableCompound(
                            id = 9L,
                            name = "Granola",
                            notes = null,
                            compositionStatus = CompoundCompositionStatus.COMPLETE,
                            components = emptyList(),
                            createdAtEpochMs = 1L,
                            updatedAtEpochMs = 1L
                        )
                    )
                )
            }
        )
    }

    /**
     * Fixture with cyclic compound graph:
     *   id=20 ("Alpha") → id=21 ("Beta") → id=20 (cycle)
     *   id=22 ("Gamma") — safe, no cycle
     */
    private fun buildViewModelWithCyclicCompounds(): CreateCompoundViewModel {
        val ingredients = MutableStateFlow(
            listOf(
                ReusableIngredient(
                    id = 7L,
                    name = "Oat milk",
                    notes = null,
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 2L
                )
            )
        )
        val alpha = ReusableCompound(
            id = 20L,
            name = "Alpha",
            notes = null,
            compositionStatus = CompoundCompositionStatus.COMPLETE,
            components = listOf(
                ReusableCompoundComponent(
                    id = 1L,
                    position = 0,
                    kind = CompoundComponentKind.COMPOUND,
                    ingredientId = null,
                    childCompoundId = 21L,
                    proportionBasisPoints = 10_000
                )
            ),
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 2L
        )
        val beta = ReusableCompound(
            id = 21L,
            name = "Beta",
            notes = null,
            compositionStatus = CompoundCompositionStatus.COMPLETE,
            components = listOf(
                ReusableCompoundComponent(
                    id = 2L,
                    position = 0,
                    kind = CompoundComponentKind.COMPOUND,
                    ingredientId = null,
                    childCompoundId = 20L,
                    proportionBasisPoints = 10_000
                )
            ),
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 3L
        )
        val gamma = ReusableCompound(
            id = 22L,
            name = "Gamma",
            notes = null,
            compositionStatus = CompoundCompositionStatus.COMPLETE,
            components = emptyList(),
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 4L
        )
        return CreateCompoundViewModel(
            createReusableCompoundUseCase = CreateReusableCompoundUseCase(
                repository = object : ReusableCompoundRepository {
                    override suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound> =
                        AppResult.Success(
                            ReusableCompound(
                                id = 99L,
                                name = command.name,
                                notes = command.notes,
                                compositionStatus = CompoundCompositionStatus.COMPLETE,
                                components = emptyList(),
                                createdAtEpochMs = 1L,
                                updatedAtEpochMs = 1L
                            )
                        )

                    override fun observeCompounds(): Flow<List<ReusableCompound>> = emptyFlow()
                }
            ),
            observeReusableIngredients = { ingredients },
            observeReusableCompounds = { MutableStateFlow(listOf(alpha, beta, gamma)) }
        )
    }
}
