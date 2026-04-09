package com.example.app.features.ingestion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.app.features.compounds.domain.ObserveReusableCompoundsUseCase
import com.example.app.features.ingestion.domain.CreateIngestionDefaultsUseCase
import com.example.app.features.ingestion.domain.CreateIngestionEventUseCase
import com.example.app.features.ingredients.domain.CreateReusableIngredientUseCase
import com.example.app.features.ingredients.domain.ObserveReusableIngredientsUseCase

class CreateIngestionViewModelFactory(
    private val createIngestionDefaultsUseCase: CreateIngestionDefaultsUseCase,
    private val createIngestionEventUseCase: CreateIngestionEventUseCase,
    private val observeReusableIngredientsUseCase: ObserveReusableIngredientsUseCase,
    private val observeReusableCompoundsUseCase: ObserveReusableCompoundsUseCase,
    private val createReusableIngredientUseCase: CreateReusableIngredientUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateIngestionViewModel::class.java)) {
            return CreateIngestionViewModel(
                createIngestionDefaultsUseCase = createIngestionDefaultsUseCase,
                createIngestionEventUseCase = createIngestionEventUseCase,
                observeReusableIngredientsUseCase = observeReusableIngredientsUseCase,
                observeReusableCompoundsUseCase = observeReusableCompoundsUseCase,
                createReusableIngredientUseCase = createReusableIngredientUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
