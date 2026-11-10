package io.github.mateovich.allergotracker.features.ingestion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.mateovich.allergotracker.features.compounds.domain.ObserveReusableCompoundsUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.DeleteIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.CurrentTimeProvider
import io.github.mateovich.allergotracker.features.ingestion.domain.GetIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind
import io.github.mateovich.allergotracker.features.ingestion.domain.UpdateIngestionEventUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.CreateReusableIngredientUseCase
import io.github.mateovich.allergotracker.features.ingredients.domain.ObserveReusableIngredientsUseCase

class EditIngestionViewModelFactory(
    private val recordId: Long,
    private val getIngestionEventUseCase: GetIngestionEventUseCase,
    private val deleteIngestionEventUseCase: DeleteIngestionEventUseCase,
    private val updateIngestionEventUseCase: UpdateIngestionEventUseCase,
    private val currentTimeProvider: CurrentTimeProvider,
    private val reusableEntityExists: suspend (ReusableEntityKind, Long) -> Boolean,
    private val observeReusableIngredientsUseCase: ObserveReusableIngredientsUseCase? = null,
    private val observeReusableCompoundsUseCase: ObserveReusableCompoundsUseCase? = null,
    private val createReusableIngredientUseCase: CreateReusableIngredientUseCase? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditIngestionViewModel::class.java)) {
            return EditIngestionViewModel(
                recordId,
                getIngestionEventUseCase,
                deleteIngestionEventUseCase,
                updateIngestionEventUseCase,
                currentTimeProvider,
                reusableEntityExists,
                observeReusableIngredientsUseCase,
                observeReusableCompoundsUseCase,
                createReusableIngredientUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
