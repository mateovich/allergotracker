package io.github.mateovich.allergotracker.features.exposure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mateovich.allergotracker.core.designsystem.AppScreenScaffold
import io.github.mateovich.allergotracker.core.designsystem.DateTimePickerSection
import io.github.mateovich.allergotracker.core.designsystem.LocalOnlyNotice
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogKind
import io.github.mateovich.allergotracker.features.eventcatalog.ui.EditedEventCatalogSelectionResult
import io.github.mateovich.allergotracker.navigation.AppRoutes
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EditExposureScreen(
    onNavigateToRoute: (String) -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    openDeleteOnStart: Boolean = false,
    editedCatalogSelection: EditedEventCatalogSelectionResult? = null,
    retiredCatalogKind: ReusableEventCatalogKind? = null,
    onEditedCatalogConsumed: () -> Unit = {},
    viewModel: EditExposureViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(editedCatalogSelection) {
        editedCatalogSelection?.takeIf { it.kind == ReusableEventCatalogKind.EXPOSURE_TYPE }?.let {
            viewModel.onIntent(EditExposureUiIntent.SelectExposureCatalogOption(it.id, it.name))
            onEditedCatalogConsumed()
        }
    }

    LaunchedEffect(retiredCatalogKind) {
        if (retiredCatalogKind == ReusableEventCatalogKind.EXPOSURE_TYPE) {
            viewModel.onIntent(EditExposureUiIntent.ClearRetiredExposureCatalogSelection)
            onEditedCatalogConsumed()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.onIntent(EditExposureUiIntent.Load)
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                EditExposureUiEffect.NavigateToRecords -> onNavigateToRoute(AppRoutes.RECORDS)
                is EditExposureUiEffect.NavigateToEditEventCatalog -> onNavigateToRoute(
                    AppRoutes.editEventCatalog(effect.kindStorageValue, effect.id)
                )
            }
        }
    }

    LaunchedEffect(openDeleteOnStart, state.isLoading, state.canDelete, state.isDeleteConfirmationVisible) {
        if (openDeleteOnStart && !state.isLoading && state.canDelete && !state.isDeleteConfirmationVisible) {
            viewModel.onIntent(EditExposureUiIntent.ShowDeleteConfirmation)
        }
    }

    AppScreenScaffold(title = state.title) { contentModifier ->
        if (state.isLoading) {
            Column(modifier = contentModifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("edit-exposure-loading"))
            }
            return@AppScreenScaffold
        }

        if (state.exposureType.isBlank() && state.formMessage.isNotBlank()) {
            Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .testTag("edit-exposure-load-error"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = state.formMessage, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = { viewModel.onIntent(EditExposureUiIntent.Load) },
                    modifier = Modifier.testTag("edit-exposure-retry")
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
                .testTag("edit-exposure-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isDeleteConfirmationVisible) {
                AlertDialog(
                    onDismissRequest = { viewModel.onIntent(EditExposureUiIntent.DismissDeleteConfirmation) },
                    title = { Text("Delete record?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(1.dp)
                                    .alpha(0f)
                                    .testTag("edit-exposure-delete")
                                    .clickable(enabled = true) {}
                            )
                            Text("Delete exposure record \"${state.deleteRecordLabel}\"? This cannot be undone.")
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.onIntent(EditExposureUiIntent.ConfirmDelete) },
                            modifier = Modifier.testTag("edit-exposure-delete-confirm"),
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
                            onClick = { viewModel.onIntent(EditExposureUiIntent.DismissDeleteConfirmation) },
                            modifier = Modifier.testTag("edit-exposure-delete-cancel")
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
            LocalOnlyNotice()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Exposure type", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = state.exposureType,
                    onValueChange = { viewModel.onIntent(EditExposureUiIntent.UpdateExposureType(it)) },
                    modifier = Modifier.fillMaxWidth().testTag("edit-exposure-type"),
                    label = { Text("What were you exposed to?") },
                    supportingText = { state.exposureTypeError?.let { Text(it) } },
                    isError = state.exposureTypeError != null,
                    singleLine = true
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.showCreateExposureTypeOption) {
                        AssistChip(
                            onClick = { viewModel.onIntent(EditExposureUiIntent.UseTypedExposureType) },
                            label = { Text("Use \"${state.exposureType.trim()}\"") },
                            modifier = Modifier.testTag("edit-exposure-type-create-inline")
                        )
                    }
                    state.filteredExposureTypes.forEach { option ->
                        AssistChip(
                            onClick = {
                                viewModel.onIntent(
                                    EditExposureUiIntent.SelectExposureCatalogOption(option.id, option.name)
                                )
                            },
                            label = { Text(option.name) },
                            modifier = Modifier.testTag("edit-exposure-type-option-${option.id}")
                        )
                    }
                }
                if (state.filteredExposureTypes.isEmpty()) {
                    Text(
                        text = if (state.exposureType.isBlank()) {
                            "Recent saved exposure types appear here."
                        } else {
                            "No saved exposure types match yet. Use the typed value to save it inline."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("edit-exposure-type-empty")
                    )
                }
                state.selectedExposureTypeSummary?.let { summary ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("edit-exposure-type-summary")
                        )
                        if (state.selectedExposureTypeCatalogId != null) {
                            TextButton(
                                onClick = { viewModel.onIntent(EditExposureUiIntent.ClearExposureCatalogSelection) },
                                modifier = Modifier.testTag("edit-exposure-type-clear")
                            ) {
                                Text("Clear saved selection")
                            }
                            TextButton(
                                onClick = { viewModel.onIntent(EditExposureUiIntent.EditSelectedExposureCatalog) },
                                modifier = Modifier.testTag("edit-exposure-type-edit")
                            ) {
                                Text("Edit saved value")
                            }
                        }
                    }
                }
            }

            DateTimePickerSection(
                title = "When did it happen?",
                value = state.occurredAtInput,
                timeLabel = state.occurredAtLabel,
                dateButtonLabel = viewModel.formatOccurredOnLabel(state.selectedOccurredOn),
                timeButtonLabel = viewModel.formatOccurredTimeLabel(state.selectedOccurredTime),
                selectedDate = state.selectedOccurredOn,
                selectedTime = state.selectedOccurredTime,
                inputTag = "edit-exposure-occurred-at-input",
                labelTag = "edit-exposure-occurred-at-label",
                dateButtonTag = "edit-exposure-occurred-at-date-picker",
                timeButtonTag = "edit-exposure-occurred-at-time-picker",
                error = state.occurredAtError,
                onDateSelected = { viewModel.onIntent(EditExposureUiIntent.SelectOccurredOn(it)) },
                onTimeSelected = { viewModel.onIntent(EditExposureUiIntent.SelectOccurredTime(it)) },
                presetContent = {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExposureTimePreset.entries.forEach { preset ->
                            AssistChip(
                                onClick = { viewModel.onIntent(EditExposureUiIntent.SelectTimePreset(preset)) },
                                label = { Text(preset.label) },
                                modifier = Modifier.testTag("edit-exposure-time-${preset.name.lowercase()}")
                            )
                        }
                    }
                }
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "How intense was it?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.intensityOptions.forEach { option ->
                        FilterChip(
                            selected = state.selectedIntensityLevel == option.level,
                            onClick = { viewModel.onIntent(EditExposureUiIntent.SelectIntensityLevel(option.level)) },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("edit-exposure-intensity-${option.level}")
                        )
                    }
                }
                state.intensityLevelError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("edit-exposure-intensity-error")
                    )
                }
            }
            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.onIntent(EditExposureUiIntent.UpdateNotes(it)) },
                modifier = Modifier.fillMaxWidth().testTag("edit-exposure-notes"),
                label = { Text("Notes (optional)") },
                minLines = 3
            )
            if (state.formMessage.isNotBlank()) {
                Text(text = state.formMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("edit-exposure-form-message"))
            }
            Button(
                onClick = {
                    focusManager.clearFocus(force = true)
                    viewModel.onIntent(EditExposureUiIntent.Save)
                },
                enabled = state.canSave && !state.isSaving && !state.isDeleting,
                modifier = Modifier.fillMaxWidth().testTag("edit-exposure-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save changes")
            }
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus(force = true)
                    viewModel.onIntent(EditExposureUiIntent.ShowDeleteConfirmation)
                },
                enabled = state.canDelete && !state.isSaving && !state.isDeleting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(if (state.isDeleteConfirmationVisible) "edit-exposure-delete-button" else "edit-exposure-delete")
            ) {
                Text(if (state.isDeleting) "Deleting..." else "Delete record", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
