package com.example.app.features.compounds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.compounds.domain.GetReusableCompoundMergePreviewUseCase
import com.example.app.features.compounds.domain.GetReusableCompoundRetirementImpactUseCase
import com.example.app.features.compounds.domain.GetReusableCompoundUseCase
import com.example.app.features.compounds.domain.MergeReusableCompoundUseCase
import com.example.app.features.compounds.domain.ObserveReusableCompoundsUseCase
import com.example.app.features.compounds.domain.RetireReusableCompoundUseCase
import com.example.app.features.compounds.domain.UpdateReusableCompoundUseCase
import com.example.app.features.ingredients.domain.ObserveReusableIngredientsUseCase

class EditCompoundViewModelFactory(
    private val compoundId: Long,
    private val getReusableCompoundUseCase: GetReusableCompoundUseCase,
    private val updateReusableCompoundUseCase: UpdateReusableCompoundUseCase,
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
