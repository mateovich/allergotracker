package io.github.mateovich.allergotracker.features.ingredients.ui

data class ProvisionalIngredientRowUiModel(
    val id: Long,
    val name: String,
    val notes: String?
)

data class ProvisionalIngredientsUiState(
    val title: String = "Provisional compounds",
    val helperMessage: String = "Items saved as provisional compounds. Open one to turn it into a real compound.",
    val emptyMessage: String = "No provisional compounds. Save one from the ingestion form when you don't know its composition yet.",
    val items: List<ProvisionalIngredientRowUiModel> = emptyList(),
    val isLoading: Boolean = true
)
