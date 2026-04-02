package com.example.app.features.ingredients.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.ingredients.domain.GetReusableIngredientRetirementImpactUseCase
import com.example.app.features.ingredients.domain.GetReusableIngredientUseCase
import com.example.app.features.ingredients.domain.RetireReusableIngredientUseCase
import com.example.app.features.ingredients.domain.UpdateReusableIngredientUseCase

class EditIngredientViewModelFactory(
    private val ingredientId: Long,
    private val getReusableIngredientUseCase: GetReusableIngredientUseCase,
    private val updateReusableIngredientUseCase: UpdateReusableIngredientUseCase,
    private val getReusableIngredientRetirementImpactUseCase: GetReusableIngredientRetirementImpactUseCase,
    private val retireReusableIngredientUseCase: RetireReusableIngredientUseCase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditIngredientViewModel::class.java)) {
            return EditIngredientViewModel(
                ingredientId = ingredientId,
                getReusableIngredientUseCase = getReusableIngredientUseCase,
                updateReusableIngredientUseCase = updateReusableIngredientUseCase,
                getReusableIngredientRetirementImpactUseCase = getReusableIngredientRetirementImpactUseCase,
                retireReusableIngredientUseCase = retireReusableIngredientUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
