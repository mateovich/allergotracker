package com.example.app.features.ingestion.ui

data class QuantityOptionUiModel(
    val level: Int,
    val label: String
)

enum class IngestionTimePreset(
    val label: String,
    val offsetMillis: Long
) {
    NOW(label = "Now", offsetMillis = 0L),
    THIRTY_MINUTES_AGO(label = "30m ago", offsetMillis = 30L * 60L * 1000L),
    ONE_HOUR_AGO(label = "1h ago", offsetMillis = 60L * 60L * 1000L)
}

data class CreateIngestionUiState(
    val title: String = "Create Ingestion",
    val itemName: String = "",
    val notes: String = "",
    val occurredAtEpochMs: Long = 0L,
    val occurredAtLabel: String = "",
    val selectedTimePreset: IngestionTimePreset = IngestionTimePreset.NOW,
    val quantityOptions: List<QuantityOptionUiModel> = emptyList(),
    val selectedQuantityLevel: Int? = null,
    val itemNameError: String? = null,
    val quantityError: String? = null,
    val occurredAtError: String? = null,
    val formMessage: String = "",
    val isSaving: Boolean = false,
    val canSave: Boolean = false
)

sealed interface CreateIngestionUiIntent {
    data class UpdateItemName(val value: String) : CreateIngestionUiIntent
    data class UpdateNotes(val value: String) : CreateIngestionUiIntent
    data class SelectQuantityLevel(val level: Int) : CreateIngestionUiIntent
    data class SelectTimePreset(val preset: IngestionTimePreset) : CreateIngestionUiIntent
    data object Save : CreateIngestionUiIntent
}

sealed interface CreateIngestionUiEffect {
    data object NavigateToRecords : CreateIngestionUiEffect
}
