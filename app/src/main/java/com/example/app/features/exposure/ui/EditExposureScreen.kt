package com.example.app.features.exposure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
fun EditExposureScreen(
    onNavigateToRoute: (String) -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    viewModel: EditExposureViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onIntent(EditExposureUiIntent.Load)
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                EditExposureUiEffect.NavigateToRecords -> onNavigateToRoute(AppRoutes.RECORDS)
            }
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
            modifier = contentModifier.fillMaxSize().padding(16.dp).testTag("edit-exposure-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.exposureType,
                onValueChange = { viewModel.onIntent(EditExposureUiIntent.UpdateExposureType(it)) },
                modifier = Modifier.fillMaxWidth().testTag("edit-exposure-type"),
                label = { Text("What were you exposed to?") },
                supportingText = { state.exposureTypeError?.let { Text(it) } },
                isError = state.exposureTypeError != null,
                singleLine = true
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "When did it happen?", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = state.occurredAtInput,
                    onValueChange = { viewModel.onIntent(EditExposureUiIntent.UpdateOccurredAtInput(it)) },
                    modifier = Modifier.fillMaxWidth().testTag("edit-exposure-occurred-at-input"),
                    label = { Text("Date and time") },
                    supportingText = { state.occurredAtError?.let { Text(it) } },
                    isError = state.occurredAtError != null,
                    singleLine = true
                )
                Text(text = state.occurredAtLabel, modifier = Modifier.testTag("edit-exposure-occurred-at-label"))
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
                onClick = { viewModel.onIntent(EditExposureUiIntent.Save) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("edit-exposure-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save changes")
            }
        }
    }
}
