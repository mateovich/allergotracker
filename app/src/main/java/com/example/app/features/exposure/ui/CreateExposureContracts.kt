package com.example.app.features.exposure.ui

data class ExposureTimeOptionUiModel(
    val key: String,
    val label: String,
    val offsetMillis: Long
)

enum class ExposureTimePreset(
    val label: String,
    val offsetMillis: Long
) {
    NOW(label = "Now", offsetMillis = 0L),
    THIRTY_MINUTES_AGO(label = "30m ago", offsetMillis = 30L * 60L * 1000L),
    ONE_HOUR_AGO(label = "1h ago", offsetMillis = 60L * 60L * 1000L)
}

data class CreateExposureUiState(
    val title: String = "Create Exposure",
    val exposureType: String = "",
    val notes: String = "",
    val occurredAtEpochMs: Long = 0L,
    val occurredAtInput: String = "",
    val occurredAtLabel: String = "",
    val selectedTimePreset: ExposureTimePreset = ExposureTimePreset.NOW,
    val timeOptions: List<ExposureTimeOptionUiModel> = emptyList(),
    val exposureTypeError: String? = null,
    val occurredAtError: String? = null,
    val formMessage: String = "",
    val isSaving: Boolean = false,
    val canSave: Boolean = false
)

sealed interface CreateExposureUiIntent {
    data class UpdateExposureType(val value: String) : CreateExposureUiIntent
    data class UpdateNotes(val value: String) : CreateExposureUiIntent
    data class UpdateOccurredAtInput(val value: String) : CreateExposureUiIntent
    data class SelectTimePreset(val preset: ExposureTimePreset) : CreateExposureUiIntent
    data object Save : CreateExposureUiIntent
}

sealed interface CreateExposureUiEffect {
    data object NavigateToRecords : CreateExposureUiEffect
}
