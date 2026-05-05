package io.github.mateovich.allergotracker.features.ingestion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mateovich.allergotracker.core.designsystem.AppScreenScaffold
import io.github.mateovich.allergotracker.core.designsystem.DateTimePickerSection
import io.github.mateovich.allergotracker.core.designsystem.LocalOnlyNotice
import io.github.mateovich.allergotracker.navigation.AppRoutes
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EditIngestionScreen(
    onNavigateToRoute: (String) -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    openDeleteOnStart: Boolean = false,
    selectedReusableEntity: IngestionReusableSelectionResult? = null,
    retiredReusableSelection: Boolean = false,
    onReusableEntityConsumed: () -> Unit = {},
    viewModel: EditIngestionViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current
    var shouldAutoOpenDelete by remember(openDeleteOnStart) { mutableStateOf(openDeleteOnStart) }

    LaunchedEffect(selectedReusableEntity) {
        selectedReusableEntity?.let {
            viewModel.onIntent(EditIngestionUiIntent.ApplyReusableSelection(it))
            onReusableEntityConsumed()
        }
    }

    LaunchedEffect(retiredReusableSelection) {
        if (retiredReusableSelection) {
            viewModel.onIntent(EditIngestionUiIntent.ClearRetiredReusableSelection)
            onReusableEntityConsumed()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.onIntent(EditIngestionUiIntent.Load)
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                EditIngestionUiEffect.NavigateToRecords -> onNavigateToRoute(AppRoutes.RECORDS)
                is EditIngestionUiEffect.NavigateToEditReusableEntity -> onNavigateToRoute(
                    when (effect.kind) {
                        io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind.INGREDIENT -> AppRoutes.editIngredient(effect.id)
                        io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind.COMPOUND -> AppRoutes.editCompound(effect.id)
                    }
                )
            }
        }
    }

    LaunchedEffect(shouldAutoOpenDelete, state.isLoading, state.canDelete) {
        if (shouldAutoOpenDelete && !state.isLoading && state.canDelete) {
            shouldAutoOpenDelete = false
            viewModel.onIntent(EditIngestionUiIntent.ShowDeleteConfirmation)
        }
    }

    AppScreenScaffold(title = state.title) { contentModifier ->
        if (state.isLoading) {
            Column(
                modifier = contentModifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag("edit-ingestion-loading"))
            }
            return@AppScreenScaffold
        }

        if (state.itemName.isBlank() && state.formMessage.isNotBlank()) {
            Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .testTag("edit-ingestion-load-error"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = state.formMessage, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = { viewModel.onIntent(EditIngestionUiIntent.Load) },
                    modifier = Modifier.testTag("edit-ingestion-retry")
                ) {
                    Text("Retry")
                }
            }
            return@AppScreenScaffold
        }

        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp)
                .testTag("edit-ingestion-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isDeleteConfirmationVisible) {
                AlertDialog(
                    onDismissRequest = { viewModel.onIntent(EditIngestionUiIntent.DismissDeleteConfirmation) },
                    title = { Text("Delete record?") },
                    text = { Text("Delete ingestion record \"${state.deleteRecordLabel}\"? This cannot be undone.") },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.onIntent(EditIngestionUiIntent.ConfirmDelete) },
                            modifier = Modifier.testTag("edit-ingestion-delete-confirm"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { viewModel.onIntent(EditIngestionUiIntent.DismissDeleteConfirmation) },
                            modifier = Modifier.testTag("edit-ingestion-delete-cancel")
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
            LocalOnlyNotice()
            state.selectedReusableEntitySummary?.let { summary ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = summary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("edit-ingestion-reusable-summary"))
                    TextButton(
                        onClick = { viewModel.onIntent(EditIngestionUiIntent.EditSelectedReusableEntity) },
                        modifier = Modifier.testTag("edit-ingestion-reusable-edit")
                    ) {
                        Text("Edit saved reusable item")
                    }
                }
            }
            OutlinedTextField(
                value = state.itemName,
                onValueChange = { viewModel.onIntent(EditIngestionUiIntent.UpdateItemName(it)) },
                modifier = Modifier.fillMaxWidth().testTag("edit-ingestion-item-name"),
                label = { Text("What did you ingest?") },
                supportingText = { state.itemNameError?.let { Text(it) } },
                isError = state.itemNameError != null,
                singleLine = true
            )
            DateTimePickerSection(
                title = "When did it happen?",
                timeLabel = state.occurredAtLabel,
                dateButtonLabel = viewModel.formatSelectedDateLabel(state.selectedDate),
                timeButtonLabel = viewModel.formatSelectedTimeLabel(state.selectedTime),
                selectedDate = state.selectedDate,
                selectedTime = state.selectedTime,
                sectionTag = "edit-ingestion-occurred-at-section",
                labelTag = "edit-ingestion-occurred-at-label",
                dateButtonTag = "edit-ingestion-date-picker",
                timeButtonTag = "edit-ingestion-time-picker",
                errorTag = "edit-ingestion-occurred-at-error",
                error = state.occurredAtError,
                onDateSelected = { viewModel.onIntent(EditIngestionUiIntent.SelectDate(it)) },
                onTimeSelected = { viewModel.onIntent(EditIngestionUiIntent.SelectTime(it)) },
                presetContent = {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IngestionTimePreset.entries.forEach { preset ->
                            AssistChip(
                                onClick = { viewModel.onIntent(EditIngestionUiIntent.SelectTimePreset(preset)) },
                                label = { Text(preset.label) },
                                modifier = Modifier.testTag("edit-ingestion-time-${preset.name.lowercase()}")
                            )
                        }
                    }
                }
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "How much?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.quantityOptions.forEach { option ->
                        FilterChip(
                            selected = state.selectedQuantityLevel == option.level,
                            onClick = { viewModel.onIntent(EditIngestionUiIntent.SelectQuantityLevel(option.level)) },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("edit-ingestion-quantity-${option.level}")
                        )
                    }
                }
                state.quantityError?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            }
            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.onIntent(EditIngestionUiIntent.UpdateNotes(it)) },
                modifier = Modifier.fillMaxWidth().testTag("edit-ingestion-notes"),
                label = { Text("Notes (optional)") },
                minLines = 3
            )
            if (state.formMessage.isNotBlank()) {
                Text(
                    text = state.formMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("edit-ingestion-form-message")
                )
            }
            Button(
                onClick = {
                    focusManager.clearFocus(force = true)
                    viewModel.onIntent(EditIngestionUiIntent.Save)
                },
                enabled = state.canSave && !state.isSaving && !state.isDeleting,
                modifier = Modifier.fillMaxWidth().testTag("edit-ingestion-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save changes")
            }
            OutlinedButton(
                onClick = { viewModel.onIntent(EditIngestionUiIntent.ShowDeleteConfirmation) },
                enabled = state.canDelete && !state.isSaving && !state.isDeleting,
                modifier = Modifier.fillMaxWidth().testTag("edit-ingestion-delete")
            ) {
                Text(if (state.isDeleting) "Deleting..." else "Delete record", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
