package com.example.app.features.outbreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.core.designsystem.AppScreenScaffold
import com.example.app.navigation.AppRoutes
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EditOutbreakScreen(
    onNavigateToRoute: (String) -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    viewModel: EditOutbreakViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onIntent(EditOutbreakUiIntent.Load)
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                EditOutbreakUiEffect.NavigateToRecords -> onNavigateToRoute(AppRoutes.RECORDS)
            }
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
            modifier = contentModifier.fillMaxSize().padding(16.dp).testTag("edit-outbreak-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isDeleteConfirmationVisible) {
                AlertDialog(
                    onDismissRequest = { viewModel.onIntent(EditOutbreakUiIntent.DismissDeleteConfirmation) },
                    title = { Text("Delete record?") },
                    text = {
                        Text(
                            "Delete outbreak record \"${state.outbreakDescriptor}\" at \"${state.bodyLocation}\"? This cannot be undone."
                        )
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
            OutlinedTextField(
                value = state.outbreakDescriptor,
                onValueChange = { viewModel.onIntent(EditOutbreakUiIntent.UpdateOutbreakDescriptor(it)) },
                modifier = Modifier.fillMaxWidth().testTag("edit-outbreak-descriptor"),
                label = { Text("What happened?") },
                supportingText = { state.outbreakDescriptorError?.let { Text(it) } },
                isError = state.outbreakDescriptorError != null,
                singleLine = true
            )
            OutlinedTextField(
                value = state.bodyLocation,
                onValueChange = { viewModel.onIntent(EditOutbreakUiIntent.UpdateBodyLocation(it)) },
                modifier = Modifier.fillMaxWidth().testTag("edit-outbreak-body-location"),
                label = { Text("Where on the body?") },
                supportingText = { state.bodyLocationError?.let { Text(it) } },
                isError = state.bodyLocationError != null,
                singleLine = true
            )
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
            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.onIntent(EditOutbreakUiIntent.UpdateNotes(it)) },
                modifier = Modifier.fillMaxWidth().testTag("edit-outbreak-notes"),
                label = { Text("Notes (optional)") },
                minLines = 3
            )
            if (state.formMessage.isNotBlank()) {
                Text(text = state.formMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("edit-outbreak-form-message"))
            }
            Button(
                onClick = { viewModel.onIntent(EditOutbreakUiIntent.Save) },
                enabled = state.canSave && !state.isSaving && !state.isDeleting,
                modifier = Modifier.fillMaxWidth().testTag("edit-outbreak-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save changes")
            }
            OutlinedButton(
                onClick = { viewModel.onIntent(EditOutbreakUiIntent.ShowDeleteConfirmation) },
                enabled = !state.isSaving && !state.isDeleting,
                modifier = Modifier.fillMaxWidth().testTag("edit-outbreak-delete")
            ) {
                Text(if (state.isDeleting) "Deleting..." else "Delete record", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
