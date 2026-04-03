package com.example.app.features.outbreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.imePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.core.designsystem.AppScreenScaffold
import com.example.app.core.designsystem.LocalOnlyNotice
import com.example.app.features.eventcatalog.domain.ReusableEventCatalogKind
import com.example.app.features.eventcatalog.ui.EditedEventCatalogSelectionResult
import com.example.app.navigation.AppRoutes
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EditOutbreakScreen(
    onNavigateToRoute: (String) -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    openDeleteOnStart: Boolean = false,
    editedCatalogSelection: EditedEventCatalogSelectionResult? = null,
    retiredCatalogKind: ReusableEventCatalogKind? = null,
    onEditedCatalogConsumed: () -> Unit = {},
    viewModel: EditOutbreakViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(editedCatalogSelection) {
        when (editedCatalogSelection?.kind) {
            ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> {
                viewModel.onIntent(EditOutbreakUiIntent.SelectOutbreakDescriptorCatalogOption(editedCatalogSelection.id, editedCatalogSelection.name))
                onEditedCatalogConsumed()
            }

            ReusableEventCatalogKind.BODY_LOCATION -> {
                viewModel.onIntent(EditOutbreakUiIntent.SelectBodyLocationCatalogOption(editedCatalogSelection.id, editedCatalogSelection.name))
                onEditedCatalogConsumed()
            }

            else -> Unit
        }
    }

    LaunchedEffect(retiredCatalogKind) {
        when (retiredCatalogKind) {
            ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> {
                viewModel.onIntent(EditOutbreakUiIntent.ClearRetiredOutbreakDescriptorCatalogSelection)
                onEditedCatalogConsumed()
            }

            ReusableEventCatalogKind.BODY_LOCATION -> {
                viewModel.onIntent(EditOutbreakUiIntent.ClearRetiredBodyLocationCatalogSelection)
                onEditedCatalogConsumed()
            }

            null, ReusableEventCatalogKind.EXPOSURE_TYPE -> Unit
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.onIntent(EditOutbreakUiIntent.Load)
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                EditOutbreakUiEffect.NavigateToRecords -> onNavigateToRoute(AppRoutes.RECORDS)
                is EditOutbreakUiEffect.NavigateToEditEventCatalog -> onNavigateToRoute(
                    AppRoutes.editEventCatalog(effect.kindStorageValue, effect.id)
                )
            }
        }
    }

    LaunchedEffect(openDeleteOnStart, state.isLoading, state.canDelete, state.isDeleteConfirmationVisible) {
        if (openDeleteOnStart && !state.isLoading && state.canDelete && !state.isDeleteConfirmationVisible) {
            viewModel.onIntent(EditOutbreakUiIntent.ShowDeleteConfirmation)
        }
    }

    AppScreenScaffold(title = state.title) { contentModifier ->
        if (state.isLoading) {
            Column(modifier = contentModifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("edit-outbreak-loading"))
            }
            return@AppScreenScaffold
        }

        if (state.outbreakDescriptor.isBlank() && state.formMessage.isNotBlank()) {
            Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .testTag("edit-outbreak-load-error"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = state.formMessage, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = { viewModel.onIntent(EditOutbreakUiIntent.Load) },
                    modifier = Modifier.testTag("edit-outbreak-retry")
                ) {
                    Text("Retry")
                }
            }
            return@AppScreenScaffold
        }

        Column(
            modifier = contentModifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp).testTag("edit-outbreak-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isDeleteConfirmationVisible) {
                AlertDialog(
                    onDismissRequest = { viewModel.onIntent(EditOutbreakUiIntent.DismissDeleteConfirmation) },
                    title = { Text("Delete record?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(1.dp)
                                    .alpha(0f)
                                    .testTag("edit-outbreak-delete")
                                    .clickable(enabled = true) {}
                            )
                            Text("Delete outbreak record \"${state.deleteRecordLabel}\"? This cannot be undone.")
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.onIntent(EditOutbreakUiIntent.ConfirmDelete) },
                            modifier = Modifier.testTag("edit-outbreak-delete-confirm"),
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
                            onClick = { viewModel.onIntent(EditOutbreakUiIntent.DismissDeleteConfirmation) },
                            modifier = Modifier.testTag("edit-outbreak-delete-cancel")
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
            LocalOnlyNotice()
            OutbreakCatalogField(
                title = "What happened?",
                value = state.outbreakDescriptor,
                error = state.outbreakDescriptorError,
                filteredOptions = state.filteredOutbreakDescriptors,
                showCreateOption = state.showCreateOutbreakDescriptorOption,
                emptyTag = "edit-outbreak-descriptor-empty",
                fieldTag = "edit-outbreak-descriptor",
                createTag = "edit-outbreak-descriptor-create-inline",
                optionTagPrefix = "edit-outbreak-descriptor-option",
                summary = state.selectedOutbreakDescriptorSummary,
                clearSelectionTag = "edit-outbreak-descriptor-clear",
                selectedCatalogId = state.selectedOutbreakDescriptorCatalogId,
                onValueChange = { viewModel.onIntent(EditOutbreakUiIntent.UpdateOutbreakDescriptor(it)) },
                onCreateTyped = { viewModel.onIntent(EditOutbreakUiIntent.UseTypedOutbreakDescriptor) },
                onSelectOption = { id, name ->
                    viewModel.onIntent(EditOutbreakUiIntent.SelectOutbreakDescriptorCatalogOption(id, name))
                },
                onClearSelection = { viewModel.onIntent(EditOutbreakUiIntent.ClearOutbreakDescriptorCatalogSelection) },
                onEditSelection = { viewModel.onIntent(EditOutbreakUiIntent.EditSelectedOutbreakDescriptorCatalog) }
            )
            OutbreakCatalogField(
                title = "Where on the body?",
                value = state.bodyLocation,
                error = state.bodyLocationError,
                filteredOptions = state.filteredBodyLocations,
                showCreateOption = state.showCreateBodyLocationOption,
                emptyTag = "edit-outbreak-body-location-empty",
                fieldTag = "edit-outbreak-body-location",
                createTag = "edit-outbreak-body-location-create-inline",
                optionTagPrefix = "edit-outbreak-body-location-option",
                summary = state.selectedBodyLocationSummary,
                clearSelectionTag = "edit-outbreak-body-location-clear",
                selectedCatalogId = state.selectedBodyLocationCatalogId,
                onValueChange = { viewModel.onIntent(EditOutbreakUiIntent.UpdateBodyLocation(it)) },
                onCreateTyped = { viewModel.onIntent(EditOutbreakUiIntent.UseTypedBodyLocation) },
                onSelectOption = { id, name ->
                    viewModel.onIntent(EditOutbreakUiIntent.SelectBodyLocationCatalogOption(id, name))
                },
                onClearSelection = { viewModel.onIntent(EditOutbreakUiIntent.ClearBodyLocationCatalogSelection) },
                onEditSelection = { viewModel.onIntent(EditOutbreakUiIntent.EditSelectedBodyLocationCatalog) }
            )
            if (state.formMessage.isNotBlank()) {
                Text(text = state.formMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("edit-outbreak-form-message"))
            }
            Button(
                onClick = {
                    focusManager.clearFocus(force = true)
                    viewModel.onIntent(EditOutbreakUiIntent.Save)
                },
                enabled = state.canSave && !state.isSaving && !state.isDeleting,
                modifier = Modifier.fillMaxWidth().testTag("edit-outbreak-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save changes")
            }
            OutlinedButton(
                onClick = {
                    focusManager.clearFocus(force = true)
                    viewModel.onIntent(EditOutbreakUiIntent.ShowDeleteConfirmation)
                },
                enabled = state.canDelete && !state.isSaving && !state.isDeleting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(if (state.isDeleteConfirmationVisible) "edit-outbreak-delete-button" else "edit-outbreak-delete")
            ) {
                Text(if (state.isDeleting) "Deleting..." else "Delete record", color = MaterialTheme.colorScheme.error)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "When did it happen?", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = state.occurredAtInput,
                    onValueChange = { viewModel.onIntent(EditOutbreakUiIntent.UpdateOccurredAtInput(it)) },
                    modifier = Modifier.fillMaxWidth().testTag("edit-outbreak-occurred-at-input"),
                    label = { Text("Date and time") },
                    supportingText = { state.occurredAtError?.let { Text(it) } },
                    isError = state.occurredAtError != null,
                    singleLine = true
                )
                if (state.occurredAtLabel.isNotBlank()) {
                    Text(text = state.occurredAtLabel, modifier = Modifier.testTag("edit-outbreak-occurred-at-label"))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.timeOptions.forEach { option ->
                        val preset = OutbreakTimePreset.valueOf(option.key)
                        AssistChip(
                            onClick = { viewModel.onIntent(EditOutbreakUiIntent.SelectTimePreset(preset)) },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("edit-outbreak-time-${preset.name.lowercase()}")
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "How intense was it?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.intensityOptions.forEach { option ->
                        FilterChip(
                            selected = state.selectedIntensityLevel == option.level,
                            onClick = { viewModel.onIntent(EditOutbreakUiIntent.SelectIntensityLevel(option.level)) },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("edit-outbreak-intensity-${option.level}")
                        )
                    }
                }
                state.intensityError?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            }
            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.onIntent(EditOutbreakUiIntent.UpdateNotes(it)) },
                modifier = Modifier.fillMaxWidth().testTag("edit-outbreak-notes"),
                label = { Text("Notes (optional)") },
                minLines = 3
            )
        }
    }
}
