package com.example.app.features.compounds.ui

import com.example.app.MainDispatcherRule
import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.domain.CompoundComponentKind
import com.example.app.features.compounds.domain.CompoundCompositionStatus
import com.example.app.features.compounds.domain.CreateReusableCompoundComponentCommand
import com.example.app.features.compounds.domain.CreateReusableCompoundCommand
import com.example.app.features.compounds.domain.CreateReusableCompoundUseCase
import com.example.app.features.compounds.domain.ReusableCompound
import com.example.app.features.compounds.domain.ReusableCompoundComponent
import com.example.app.features.compounds.domain.ReusableCompoundRepository
import com.example.app.features.ingredients.domain.ReusableIngredient
import com.example.app.features.ingestion.domain.ReusableEntityKind
import com.example.app.features.ingestion.ui.IngestionReusableSelectionResult
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
    fun selecting_component_and_updating_proportion_keeps_inline_state_in_sync() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
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
    fun decimal_proportions_keep_precise_total_label() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentProportion(index = 0, value = "33.34"))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentProportion(index = 1, value = "33.34"))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentProportion(index = 2, value = "33.34"))

        assertEquals("100% total", viewModel.state.value.totalPercentLabel)
        assertEquals(10_000, viewModel.state.value.components.sumOf { it.proportionBasisPoints ?: 0 })
        assertTrue(viewModel.state.value.components.all { it.proportionBasisPoints != null })
    }

    @Test
    fun zero_percent_component_disables_save_until_removed() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.UpdateName("Breakfast mix"))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
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
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.UNIFORM))

        assertEquals(listOf("33.33", "33.33", "33.34"), viewModel.state.value.components.map { it.proportionPercent })
        assertEquals(emptyList<Int>(), viewModel.state.value.touchedComponentOrder)
        assertEquals(CompoundCompositionPreset.UNIFORM, viewModel.state.value.selectedPreset)
        assertEquals("100% total", viewModel.state.value.totalPercentLabel)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun applying_preset_clears_stale_component_errors() {
        val viewModel = buildViewModel()

        viewModel.onIntent(CreateCompoundUiIntent.Save)
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(7L))
        viewModel.onIntent(CreateCompoundUiIntent.UpdateName("Breakfast mix"))
        viewModel.onIntent(CreateCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.DECREASING))

        assertNull(viewModel.state.value.componentsError)
        assertNull(viewModel.state.value.totalError)
        assertTrue(viewModel.state.value.components.all { it.proportionError == null && it.targetError == null })
        assertEquals(listOf("66.67", "33.33"), viewModel.state.value.components.map { it.proportionPercent })
    }

    private fun buildViewModel(): CreateCompoundViewModel {
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
        return CreateCompoundViewModel(
            createReusableCompoundUseCase = CreateReusableCompoundUseCase(
                repository = object : ReusableCompoundRepository {
                    override suspend fun createCompound(command: CreateReusableCompoundCommand): AppResult<ReusableCompound> {
                        return AppResult.Success(
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
            observeReusableCompounds = { emptyFlow() }
        )
    }
}
