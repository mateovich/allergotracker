package com.example.app.features.records.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.core.designsystem.AppScreenScaffold
import com.example.app.core.designsystem.EmptyStateMessage
import com.example.app.core.designsystem.PrimaryAddFab
import com.example.app.features.ingestion.domain.IngestionQuantityOption
import com.example.app.features.records.domain.RecordEventType
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RecordsScreen(
    onNavigateToRoute: (String) -> Unit = {},
    viewModelFactory: ViewModelProvider.Factory,
    viewModel: RecordsViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onIntent(RecordsUiIntent.Load)
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is RecordsUiEffect.NavigateToCreateForm -> onNavigateToRoute(effect.route)
                is RecordsUiEffect.NavigateToEditForm -> onNavigateToRoute(effect.route)
            }
        }
    }

    AppScreenScaffold(
        title = state.title,
        topBarActions = {
            TextButton(
                onClick = { viewModel.onIntent(RecordsUiIntent.OpenFilterDialog) },
                modifier = Modifier
                    .testTag("records-filter-action")
                    .semantics { contentDescription = state.filterActionLabel }
            ) {
                Text(text = "Filter")
            }
        }
    ) { contentModifier ->
        Box(
            modifier = contentModifier
                .fillMaxSize()
                .testTag("records-screen-content")
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (state.activeFilters.hasActiveFilters) {
                    ActiveFilterSummary(
                        state = state,
                        onClearFilters = { viewModel.onIntent(RecordsUiIntent.ClearFilters) }
                    )
                }

                if (!state.hasRecords) {
                    EmptyStateMessage(
                        title = state.emptyStateTitle,
                        message = state.emptyStateMessage,
                        actionLabel = state.emptyStateActionLabel,
                        onActionClick = if (state.emptyStateActionLabel != null) {
                            { viewModel.onIntent(RecordsUiIntent.ClearFilters) }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .fillMaxWidth()
                            .testTag("records-list")
                    ) {
                        items(state.records, key = { it.id }) { record ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = record.isEditable) {
                                        viewModel.onIntent(RecordsUiIntent.OpenRecord(record.id))
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag("record-item-${record.id}")
                            ) {
                                Text(text = record.typeLabel, style = MaterialTheme.typography.labelMedium)
                                Text(text = record.title, style = MaterialTheme.typography.titleMedium)
                                if (record.detailLabel.isNotBlank()) {
                                    Text(text = record.detailLabel)
                                }
                                Text(text = record.occurredAtLabel)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            if (state.isQuickPickerVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("records-add-menu-scrim")
                        .clickable { viewModel.onIntent(RecordsUiIntent.DismissAddEntryPoint) }
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .wrapContentSize()
            ) {
                PrimaryAddFab(
                    contentDescription = state.addActionLabel,
                    onClick = { viewModel.onIntent(RecordsUiIntent.OpenAddEntryPoint) }
                )
                DropdownMenu(
                    expanded = state.isQuickPickerVisible,
                    onDismissRequest = {
                        viewModel.onIntent(RecordsUiIntent.DismissAddEntryPoint)
                    },
                    properties = PopupProperties(focusable = true),
                    modifier = Modifier
                        .testTag("records-add-menu")
                        .semantics {
                            contentDescription = state.quickPickerLabel
                        }
                ) {
                    EventType.entries.forEach { eventType ->
                        DropdownMenuItem(
                            text = { Text(text = eventType.label) },
                            onClick = {
                                viewModel.onIntent(RecordsUiIntent.SelectEventType(eventType))
                            },
                            modifier = Modifier.semantics {
                                contentDescription = eventType.label
                            }
                        )
                    }
                }
            }
        }
    }

    if (state.isFilterDialogVisible) {
        RecordsFilterDialog(
            state = state,
            onDismiss = { viewModel.onIntent(RecordsUiIntent.DismissFilterDialog) },
            onApply = { viewModel.onIntent(RecordsUiIntent.ApplyFilters) },
            onReset = { viewModel.onIntent(RecordsUiIntent.ClearFilters) },
            onStartDateChanged = { viewModel.onIntent(RecordsUiIntent.UpdateFilterStartDate(it)) },
            onEndDateChanged = { viewModel.onIntent(RecordsUiIntent.UpdateFilterEndDate(it)) },
            onToggleEventType = { viewModel.onIntent(RecordsUiIntent.ToggleFilterEventType(it)) },
            onToggleIntensity = { viewModel.onIntent(RecordsUiIntent.ToggleFilterIntensity(it)) }
        )
    }
}

@Composable
private fun ActiveFilterSummary(
    state: RecordsUiState,
    onClearFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("records-active-filters"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Active filters", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.activeFilters.summaryLabels.forEachIndexed { index, label ->
                FilterChip(
                    selected = true,
                    onClick = {},
                    enabled = false,
                    label = { Text(text = label) },
                    modifier = Modifier
                        .testTag("records-active-filter-$index")
                        .semantics {
                            contentDescription = "Active filter: $label"
                        }
                )
            }
        }
        TextButton(
            onClick = onClearFilters,
            modifier = Modifier
                .testTag("records-clear-filters")
                .semantics { contentDescription = state.activeFilters.resetLabel }
        ) {
            Text(text = state.activeFilters.resetLabel)
        }
    }
}

@Composable
private fun RecordsFilterDialog(
    state: RecordsUiState,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onStartDateChanged: (String) -> Unit,
    onEndDateChanged: (String) -> Unit,
    onToggleEventType: (RecordEventType) -> Unit,
    onToggleIntensity: (IngestionQuantityOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Filter records") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.filterDraft.startDateInput,
                    onValueChange = onStartDateChanged,
                    label = { Text("From date") },
                    supportingText = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("records-filter-start-date")
                )
                OutlinedTextField(
                    value = state.filterDraft.endDateInput,
                    onValueChange = onEndDateChanged,
                    label = { Text("To date") },
                    supportingText = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("records-filter-end-date")
                )

                Text(text = "Type", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecordEventType.entries.forEach { eventType ->
                        FilterChip(
                            selected = eventType in state.filterDraft.selectedEventTypes,
                            onClick = { onToggleEventType(eventType) },
                            label = { Text(eventType.label) },
                            modifier = Modifier.testTag("records-filter-type-${eventType.name.lowercase()}")
                        )
                    }
                }

                Text(text = "Intensity", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = state.intensityHint,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("records-filter-intensity-hint")
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IngestionQuantityOption.entries.forEach { option ->
                        FilterChip(
                            selected = option in state.filterDraft.selectedIntensityLevels,
                            onClick = { onToggleIntensity(option) },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("records-filter-intensity-${option.level}")
                        )
                    }
                }

                state.filterDraft.errorMessage?.let { errorMessage ->
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("records-filter-error")
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReset, modifier = Modifier.testTag("records-filter-reset")) {
                    Text(text = "Reset")
                }
                TextButton(onClick = onApply, modifier = Modifier.testTag("records-filter-apply")) {
                    Text(text = "Apply")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("records-filter-cancel")) {
                Text(text = "Cancel")
            }
        }
    )
}
