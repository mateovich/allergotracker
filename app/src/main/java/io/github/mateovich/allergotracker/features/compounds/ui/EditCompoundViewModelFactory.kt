package io.github.mateovich.allergotracker.features.compounds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mateovich.allergotracker.features.compounds.domain.GetReusableCompoundMergePreviewUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.GetReusableCompoundRetirementImpactUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.GetReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.MergeReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.ObserveReusableCompoundsUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.RetireReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.compounds.domain.UpdateReusableCompoundUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ObserveReusableIngredientsUseCase

class EditCompoundViewModelFactory(
    private val compoundId: Long,
    private val getReusableCompoundUseCase: GetReusableCompoundUseCase,
    private val updateReusableCompoundUseCase: UpdateReusableCompoundUseCase,
    private val createReusableIngredientUseCase: CreateReusableIngredientUseCase,
    private val observeReusableIngredients: ObserveReusableIngredientsUseCase,
    private val observeReusableCompounds: ObserveReusableCompoundsUseCase,
    private val getReusableCompoundMergePreviewUseCase: GetReusableCompoundMergePreviewUseCase,
    private val mergeReusableCompoundUseCase: MergeReusableCompoundUseCase,
    private val getReusableCompoundRetirementImpactUseCase: GetReusableCompoundRetirementImpactUseCase,
    private val retireReusableCompoundUseCase: RetireReusableCompoundUseCase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditCompoundViewModel::class.java)) {
            return EditCompoundViewModel(
                compoundId = compoundId,
                getReusableCompoundUseCase = getReusableCompoundUseCase,
                updateReusableCompoundUseCase = updateReusableCompoundUseCase,
                createReusableIngredientUseCase = createReusableIngredientUseCase,
                observeReusableIngredients = observeReusableIngredients,
                observeReusableCompounds = observeReusableCompounds,
                getReusableCompoundMergePreviewUseCase = getReusableCompoundMergePreviewUseCase,
                mergeReusableCompoundUseCase = mergeReusableCompoundUseCase,
                getReusableCompoundRetirementImpactUseCase = getReusableCompoundRetirementImpactUseCase,
                retireReusableCompoundUseCase = retireReusableCompoundUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
