package com.example.app.features.compounds.ui

import com.example.app.MainDispatcherRule
import com.example.app.core.common.error.DomainError
import com.example.app.core.common.result.AppResult
import com.example.app.features.compounds.domain.CompoundCompositionStatus
import com.example.app.features.compounds.domain.CompoundComponentKind
import com.example.app.features.compounds.domain.GetReusableCompoundRetirementImpactUseCase
import com.example.app.features.compounds.domain.GetReusableCompoundUseCase
import com.example.app.features.compounds.domain.ObserveReusableCompoundsUseCase
import com.example.app.features.compounds.domain.ReusableCompound
import com.example.app.features.compounds.domain.ReusableCompoundComponent
import com.example.app.features.compounds.domain.ReusableCompoundRepository
import com.example.app.features.compounds.domain.RetireReusableCompoundUseCase
import com.example.app.features.compounds.domain.UpdateReusableCompoundCommand
import com.example.app.features.compounds.domain.UpdateReusableCompoundUseCase
import com.example.app.features.ingredients.domain.ObserveReusableIngredientsUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditCompoundViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun load_populates_existing_value_and_save_returns_updated_selection() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.UpdateComponentProportion(index = 0, value = "75"))
        viewModel.onIntent(EditCompoundUiIntent.UpdateName("Breakfast mix plus"))
        viewModel.onIntent(EditCompoundUiIntent.Save)

        val navigation = effect.await() as EditCompoundUiEffect.NavigateBackWithSelection

        assertEquals("Breakfast mix plus", repository.updatedCommand?.name)
        assertEquals(7_500, repository.updatedCommand?.components?.first()?.proportionBasisPoints)
        assertEquals(9L, navigation.result.id)
    }

    @Test
    fun load_maps_existing_components_and_total_label() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)

        assertEquals(2, viewModel.state.value.components.size)
        assertEquals("100% total", viewModel.state.value.totalPercentLabel)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun validation_error_surfaces_inline_message() = runBlocking {
        val repository = FakeEditCompoundRepository(
            updateResult = AppResult.Error(
                DomainError.ValidationError(
                    "Please fix the highlighted fields.",
                    mapOf("name" to "A compound with this name already exists.")
                )
            )
        )
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.Save)

        assertEquals("A compound with this name already exists.", viewModel.state.value.nameError)
        assertTrue(viewModel.state.value.formMessage.isNotBlank())
    }

    @Test
    fun blank_name_shows_inline_error_and_disables_save_immediately() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.UpdateName("   "))

        assertEquals("Enter a compound name.", viewModel.state.value.nameError)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun load_filters_duplicate_and_recursive_compound_options() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)

        assertTrue(viewModel.state.value.filteredComponents.none { it.kind == "compound" && it.id == 11L })
        assertTrue(viewModel.state.value.filteredComponents.none { it.kind == "compound" && it.id == 12L })
        assertTrue(viewModel.state.value.filteredComponents.any { it.kind == "compound" && it.id == 13L })
    }

    @Test
    fun stale_recursive_add_keeps_user_in_context_with_actionable_feedback() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.AddCompoundComponent(12L))

        assertEquals(2, viewModel.state.value.components.size)
        assertEquals("This compound would create a recursive composition.", viewModel.state.value.formMessage)
        assertTrue(viewModel.state.value.isFormMessageError)
    }

    @Test
    fun save_time_cycle_error_maps_component_target_inline() = runBlocking {
        val repository = FakeEditCompoundRepository(
            updateResult = AppResult.Error(
                DomainError.ValidationError(
                    message = "Please fix the highlighted fields.",
                    fieldErrors = mapOf(
                        "components[1].target" to "This compound would create a recursive composition."
                    )
                )
            )
        )
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.Save)

        assertEquals(
            "This compound would create a recursive composition.",
            viewModel.state.value.components[1].targetError
        )
    }

    @Test
    fun zero_percent_component_disables_save_until_removed() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.UpdateComponentProportion(index = 0, value = "100"))
        viewModel.onIntent(EditCompoundUiIntent.UpdateComponentProportion(index = 1, value = "0"))

        assertEquals("100% total", viewModel.state.value.totalPercentLabel)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun removed_component_returns_to_picker_after_deletion() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.UpdateComponentProportion(index = 0, value = "100"))
        viewModel.onIntent(EditCompoundUiIntent.UpdateComponentProportion(index = 1, value = "0"))
        viewModel.onIntent(EditCompoundUiIntent.RemoveComponent(1))

        assertTrue(viewModel.state.value.filteredComponents.any { it.kind == "compound" && it.id == 11L })
    }

    @Test
    fun applying_decreasing_preset_rewrites_loaded_percentages_and_keeps_save_enabled() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.DECREASING))

        assertEquals(listOf("66.67", "33.33"), viewModel.state.value.components.map { it.proportionPercent })
        assertEquals(emptyList<Int>(), viewModel.state.value.touchedComponentOrder)
        assertEquals(CompoundCompositionPreset.DECREASING, viewModel.state.value.selectedPreset)
        assertFalse(viewModel.state.value.isFormMessageError)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun applying_preset_without_components_surfaces_actionable_error() = runBlocking {
        val repository = FakeEditCompoundRepository(withComponents = false)
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.UNIFORM))

        assertEquals("Add at least one component before applying a preset.", viewModel.state.value.componentsError)
        assertEquals("Couldn't apply uniform preset to an empty composition.", viewModel.state.value.formMessage)
        assertTrue(viewModel.state.value.isFormMessageError)
        assertEquals(emptyList<CompoundComponentRowUiModel>(), viewModel.state.value.components)
    }

    @Test
    fun applying_preset_clears_stale_validation_errors_after_failed_save() = runBlocking {
        val repository = FakeEditCompoundRepository(
            updateResult = AppResult.Error(
                DomainError.ValidationError(
                    "Please fix the highlighted fields.",
                    mapOf(
                        "components" to "Add at least one component.",
                        "componentsTotal" to "Keep the total at 100%.",
                        "components[0].proportion" to "Enter a proportion greater than 0%."
                    )
                )
            )
        )
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.Save)
        viewModel.onIntent(EditCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.UNIFORM))

        assertEquals(CompoundCompositionPreset.UNIFORM, viewModel.state.value.selectedPreset)
        assertEquals("100% total", viewModel.state.value.totalPercentLabel)
        assertTrue(viewModel.state.value.components.all { it.proportionError == null && it.targetError == null })
        assertEquals(listOf("50", "50"), viewModel.state.value.components.map { it.proportionPercent })
    }

    @Test
    fun updating_notes_clears_stale_form_message_state() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.UNIFORM))
        viewModel.onIntent(EditCompoundUiIntent.UpdateNotes("Fresh batch"))

        assertEquals("", viewModel.state.value.formMessage)
        assertFalse(viewModel.state.value.isFormMessageError)
    }

    @Test
    fun updating_notes_preserves_actionable_error_message() = runBlocking {
        val repository = FakeEditCompoundRepository(withComponents = false)
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.UNIFORM))
        viewModel.onIntent(EditCompoundUiIntent.UpdateNotes("Fresh batch"))

        assertEquals("Couldn't apply uniform preset to an empty composition.", viewModel.state.value.formMessage)
        assertTrue(viewModel.state.value.isFormMessageError)
    }

    @Test
    fun updating_search_query_preserves_actionable_error_message() = runBlocking {
        val repository = FakeEditCompoundRepository(withComponents = false)
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.ApplyPreset(CompoundCompositionPreset.UNIFORM))
        viewModel.onIntent(EditCompoundUiIntent.UpdateComponentSearchQuery("oat"))

        assertEquals("Couldn't apply uniform preset to an empty composition.", viewModel.state.value.formMessage)
        assertTrue(viewModel.state.value.isFormMessageError)
    }

    @Test
    fun updating_notes_clears_non_sticky_validation_message() = runBlocking {
        val repository = FakeEditCompoundRepository(
            updateResult = AppResult.Error(
                DomainError.ValidationError(
                    "Please fix the highlighted fields.",
                    mapOf("name" to "A compound with this name already exists.")
                )
            )
        )
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.Save)
        viewModel.onIntent(EditCompoundUiIntent.UpdateNotes("Fresh batch"))

        assertEquals("", viewModel.state.value.formMessage)
        assertFalse(viewModel.state.value.isFormMessageError)
    }

    @Test
    fun retirement_preview_shows_blocking_nested_reference_count() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(
            repository,
            countIngestionReferences = { 0 },
            countChildCompoundReferences = { 1 }
        )

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.RequestRetirementPreview)

        assertTrue(viewModel.state.value.isRetirementDialogVisible)
        assertEquals(1, viewModel.state.value.retirementImpact?.childCompoundReferenceCount)
        assertTrue(viewModel.state.value.retirementImpact?.isBlocked == true)
    }

    @Test
    fun confirm_retirement_navigates_back_after_successful_retire() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(repository)
        val effect = async { viewModel.effects.first() }

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.RequestRetirementPreview)
        viewModel.onIntent(EditCompoundUiIntent.ConfirmRetirement)

        val navigation = effect.await() as EditCompoundUiEffect.NavigateBackAfterRetirement

        assertEquals(EditCompoundUiEffect.NavigateBackAfterRetirement, navigation)
        assertFalse(viewModel.state.value.isRetirementDialogVisible)
    }

    @Test
    fun blocked_retirement_keeps_dialog_visible_without_navigation() = runBlocking {
        val repository = FakeEditCompoundRepository()
        val viewModel = buildViewModel(
            repository,
            countIngestionReferences = { 0 },
            countChildCompoundReferences = { 1 }
        )

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.RequestRetirementPreview)
        viewModel.onIntent(EditCompoundUiIntent.ConfirmRetirement)

        assertTrue(viewModel.state.value.isRetirementDialogVisible)
        assertTrue(viewModel.state.value.retirementImpact?.isBlocked == true)
    }

    @Test
    fun confirm_retirement_failure_keeps_dialog_open_and_surfaces_actionable_error() = runBlocking {
        val repository = FakeEditCompoundRepository(
            deleteResult = AppResult.Error(
                DomainError.IntegrityError("Update the nested compounds before retiring this compound.")
            )
        )
        val viewModel = buildViewModel(repository)

        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.onIntent(EditCompoundUiIntent.RequestRetirementPreview)
        viewModel.onIntent(EditCompoundUiIntent.ConfirmRetirement)

        assertTrue(viewModel.state.value.isRetirementDialogVisible)
        assertEquals(
            "Update the nested compounds before retiring this compound.",
            viewModel.state.value.formMessage
        )
        assertFalse(viewModel.state.value.isRetiring)
    }

    private fun buildViewModel(
        repository: FakeEditCompoundRepository,
        countIngestionReferences: suspend (Long) -> Int = { 0 },
        countChildCompoundReferences: suspend (Long) -> Int = { 0 }
    ): EditCompoundViewModel {
        val impactUseCase = GetReusableCompoundRetirementImpactUseCase(
            repository = repository,
            countIngestionReferences = countIngestionReferences,
            countChildCompoundReferences = countChildCompoundReferences
        )
        return EditCompoundViewModel(
            compoundId = 9L,
            getReusableCompoundUseCase = GetReusableCompoundUseCase(repository),
            updateReusableCompoundUseCase = UpdateReusableCompoundUseCase(repository),
            observeReusableIngredients = ObserveReusableIngredientsUseCase(FakeObserveIngredientsRepository()),
            observeReusableCompounds = ObserveReusableCompoundsUseCase(repository),
            getReusableCompoundRetirementImpactUseCase = impactUseCase,
            retireReusableCompoundUseCase = RetireReusableCompoundUseCase(repository, impactUseCase)
        )
    }
}

private class FakeEditCompoundRepository(
    private val updateResult: AppResult<ReusableCompound>? = null,
    private val deleteResult: AppResult<Unit> = AppResult.Success(Unit),
    private val withComponents: Boolean = true
) : ReusableCompoundRepository {
    var updatedCommand: UpdateReusableCompoundCommand? = null

    override suspend fun createCompound(command: com.example.app.features.compounds.domain.CreateReusableCompoundCommand): AppResult<ReusableCompound> {
        error("unused")
    }

    override suspend fun getCompound(id: Long): AppResult<ReusableCompound> {
        return AppResult.Success(
            ReusableCompound(
                id = id,
                name = "Breakfast mix",
                notes = "Draft",
                compositionStatus = CompoundCompositionStatus.DRAFT,
                components = if (withComponents) {
                    listOf(
                        ReusableCompoundComponent(
                            id = 1L,
                            position = 0,
                            kind = CompoundComponentKind.INGREDIENT,
                            ingredientId = 7L,
                            childCompoundId = null,
                            proportionBasisPoints = 6_000
                        ),
                        ReusableCompoundComponent(
                            id = 2L,
                            position = 1,
                            kind = CompoundComponentKind.COMPOUND,
                            ingredientId = null,
                            childCompoundId = 11L,
                            proportionBasisPoints = 4_000
                        )
                    )
                } else {
                    emptyList()
                },
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L
            )
        )
    }

    override suspend fun updateCompound(command: UpdateReusableCompoundCommand): AppResult<ReusableCompound> {
        updatedCommand = command
        return updateResult ?: AppResult.Success(
            ReusableCompound(
                id = command.id,
                name = command.name,
                notes = command.notes,
                compositionStatus = CompoundCompositionStatus.DRAFT,
                components = command.components.mapIndexed { index, component ->
                    ReusableCompoundComponent(
                        id = index + 1L,
                        position = index,
                        kind = component.kind,
                        ingredientId = component.reusableEntityId.takeIf { component.kind == CompoundComponentKind.INGREDIENT },
                        childCompoundId = component.reusableEntityId.takeIf { component.kind == CompoundComponentKind.COMPOUND },
                        proportionBasisPoints = component.proportionBasisPoints
                    )
                },
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 2L
            )
        )
    }

    override suspend fun deleteCompound(id: Long): AppResult<Unit> = deleteResult

    override fun observeCompounds(): Flow<List<ReusableCompound>> {
        return MutableStateFlow(
            listOf(
                ReusableCompound(
                    id = 9L,
                    name = "Breakfast mix",
                    notes = null,
                    compositionStatus = CompoundCompositionStatus.COMPLETE,
                    components = listOf(
                        ReusableCompoundComponent(
                            id = 1L,
                            position = 0,
                            kind = CompoundComponentKind.COMPOUND,
                            ingredientId = null,
                            childCompoundId = 11L,
                            proportionBasisPoints = 10_000
                        )
                    ),
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L
                ),
                ReusableCompound(
                    id = 11L,
                    name = "Berry base",
                    notes = null,
                    compositionStatus = CompoundCompositionStatus.COMPLETE,
                    components = emptyList(),
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 3L
                ),
                ReusableCompound(
                    id = 12L,
                    name = "Recursive base",
                    notes = null,
                    compositionStatus = CompoundCompositionStatus.COMPLETE,
                    components = listOf(
                        ReusableCompoundComponent(
                            id = 2L,
                            position = 0,
                            kind = CompoundComponentKind.COMPOUND,
                            ingredientId = null,
                            childCompoundId = 9L,
                            proportionBasisPoints = 10_000
                        )
                    ),
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 4L
                ),
                ReusableCompound(
                    id = 13L,
                    name = "Safe crunch",
                    notes = null,
                    compositionStatus = CompoundCompositionStatus.COMPLETE,
                    components = emptyList(),
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 5L
                )
            )
        )
    }
}

private class FakeObserveIngredientsRepository : com.example.app.features.ingredients.domain.ReusableIngredientRepository {
    override suspend fun createIngredient(
        command: com.example.app.features.ingredients.domain.CreateReusableIngredientCommand
    ): AppResult<com.example.app.features.ingredients.domain.ReusableIngredient> = error("unused")

    override fun observeIngredients(): Flow<List<com.example.app.features.ingredients.domain.ReusableIngredient>> = emptyFlow()
}
