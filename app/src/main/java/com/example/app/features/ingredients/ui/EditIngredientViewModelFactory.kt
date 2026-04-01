package com.example.app.features.ingredients.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.ingredients.domain.GetReusableIngredientUseCase
import com.example.app.features.ingredients.domain.UpdateReusableIngredientUseCase

class EditIngredientViewModelFactory(
    private val ingredientId: Long,
    private val getReusableIngredientUseCase: GetReusableIngredientUseCase,
    private val updateReusableIngredientUseCase: UpdateReusableIngredientUseCase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditIngredientViewModel::class.java)) {
            return EditIngredientViewModel(
                ingredientId = ingredientId,
                getReusableIngredientUseCase = getReusableIngredientUseCase,
                updateReusableIngredientUseCase = updateReusableIngredientUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
