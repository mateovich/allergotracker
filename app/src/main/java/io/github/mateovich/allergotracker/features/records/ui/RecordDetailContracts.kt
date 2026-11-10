package io.github.mateovich.allergotracker.features.records.ui

data class RecordDetailFieldUiModel(
    val label: String,
    val value: String
)

data class RecordDetailUiState(
    val screenTitle: String = "Record detail",
    val typeLabel: String = "",
    val title: String = "",
    val occurredAtLabel: String = "",
    val fields: List<RecordDetailFieldUiModel> = emptyList(),
    val editRoute: String = "",
    val isLoading: Boolean = true,
    val unavailableMessage: String = "",
    val isUnavailableDialogVisible: Boolean = false,
    val editActionLabel: String = "Edit record",
    val deleteActionLabel: String = "Delete record"
)

sealed interface RecordDetailUiIntent {
    data object Load : RecordDetailUiIntent
    data object NavigateBack : RecordDetailUiIntent
    data object OpenEdit : RecordDetailUiIntent
    data object OpenDelete : RecordDetailUiIntent
    data object AcknowledgeUnavailable : RecordDetailUiIntent
}

sealed interface RecordDetailUiEffect {
    data object NavigateToRecords : RecordDetailUiEffect
    data class NavigateToRoute(val route: String) : RecordDetailUiEffect
}
