package com.example.app.features.outbreak.ui

import com.example.app.features.eventcatalog.ui.ReusableEventCatalogOptionUiModel

data class OutbreakTimeOptionUiModel(
    val key: String,
    val label: String,
    val offsetMillis: Long
)

enum class OutbreakTimePreset(
    val label: String,
    val offsetMillis: Long
) {
    NOW(label = "Now", offsetMillis = 0L),
    THIRTY_MINUTES_AGO(label = "30m ago", offsetMillis = 30L * 60L * 1000L),
    ONE_HOUR_AGO(label = "1h ago", offsetMillis = 60L * 60L * 1000L)
}

data class CreateOutbreakUiState(
    val title: String = "Create Outbreak",
    val outbreakDescriptor: String = "",
    val bodyLocation: String = "",
    val notes: String = "",
    val occurredAtEpochMs: Long = 0L,
    val occurredAtInput: String = "",
    val occurredAtLabel: String = "",
    val isOccurredAtManuallyEdited: Boolean = false,
    val selectedTimePreset: OutbreakTimePreset = OutbreakTimePreset.NOW,
    val timeOptions: List<OutbreakTimeOptionUiModel> = emptyList(),
    val selectedOutbreakDescriptorCatalogId: Long? = null,
    val selectedOutbreakDescriptorSummary: String? = null,
    val availableOutbreakDescriptors: List<ReusableEventCatalogOptionUiModel> = emptyList(),
    val filteredOutbreakDescriptors: List<ReusableEventCatalogOptionUiModel> = emptyList(),
    val showCreateOutbreakDescriptorOption: Boolean = false,
    val selectedBodyLocationCatalogId: Long? = null,
    val selectedBodyLocationSummary: String? = null,
    val availableBodyLocations: List<ReusableEventCatalogOptionUiModel> = emptyList(),
    val filteredBodyLocations: List<ReusableEventCatalogOptionUiModel> = emptyList(),
    val showCreateBodyLocationOption: Boolean = false,
    val outbreakDescriptorError: String? = null,
    val bodyLocationError: String? = null,
    val occurredAtError: String? = null,
    val formMessage: String = "",
    val isSaving: Boolean = false,
    val canSave: Boolean = false
)

sealed interface CreateOutbreakUiIntent {
    data class UpdateOutbreakDescriptor(val value: String) : CreateOutbreakUiIntent
    data class SelectOutbreakDescriptorCatalogOption(val id: Long, val name: String) : CreateOutbreakUiIntent
    data object UseTypedOutbreakDescriptor : CreateOutbreakUiIntent
    data object ClearOutbreakDescriptorCatalogSelection : CreateOutbreakUiIntent
    data object ClearRetiredOutbreakDescriptorCatalogSelection : CreateOutbreakUiIntent
    data object EditSelectedOutbreakDescriptorCatalog : CreateOutbreakUiIntent
    data class UpdateBodyLocation(val value: String) : CreateOutbreakUiIntent
    data class SelectBodyLocationCatalogOption(val id: Long, val name: String) : CreateOutbreakUiIntent
    data object UseTypedBodyLocation : CreateOutbreakUiIntent
    data object ClearBodyLocationCatalogSelection : CreateOutbreakUiIntent
    data object ClearRetiredBodyLocationCatalogSelection : CreateOutbreakUiIntent
    data object EditSelectedBodyLocationCatalog : CreateOutbreakUiIntent
    data class UpdateNotes(val value: String) : CreateOutbreakUiIntent
    data class UpdateOccurredAtInput(val value: String) : CreateOutbreakUiIntent
    data class SelectTimePreset(val preset: OutbreakTimePreset) : CreateOutbreakUiIntent
    data object Save : CreateOutbreakUiIntent
}

sealed interface CreateOutbreakUiEffect {
    data object NavigateToRecords : CreateOutbreakUiEffect
    data class NavigateToEditEventCatalog(val kindStorageValue: String, val id: Long) : CreateOutbreakUiEffect
}
