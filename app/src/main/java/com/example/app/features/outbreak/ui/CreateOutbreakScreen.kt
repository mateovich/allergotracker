package com.example.app.features.outbreak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import com.example.app.core.designsystem.LocalOnlyNotice
import com.example.app.navigation.AppRoutes
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CreateOutbreakScreen(
    onNavigateToRoute: (String) -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    viewModel: CreateOutbreakViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                CreateOutbreakUiEffect.NavigateToRecords -> onNavigateToRoute(AppRoutes.RECORDS)
            }
        }
    }

    AppScreenScaffold(title = state.title) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("create-outbreak-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LocalOnlyNotice()

            OutlinedTextField(
                value = state.outbreakDescriptor,
                onValueChange = { viewModel.onIntent(CreateOutbreakUiIntent.UpdateOutbreakDescriptor(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("outbreak-descriptor"),
                label = { Text("What happened?") },
                supportingText = {
                    state.outbreakDescriptorError?.let { Text(it) }
                },
                isError = state.outbreakDescriptorError != null,
                singleLine = true
            )

            OutlinedTextField(
                value = state.bodyLocation,
                onValueChange = { viewModel.onIntent(CreateOutbreakUiIntent.UpdateBodyLocation(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("outbreak-body-location"),
                label = { Text("Where on the body?") },
                supportingText = {
                    state.bodyLocationError?.let { Text(it) }
                },
                isError = state.bodyLocationError != null,
                singleLine = true
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "When did it happen?", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = state.occurredAtInput,
                    onValueChange = { viewModel.onIntent(CreateOutbreakUiIntent.UpdateOccurredAtInput(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("outbreak-occurred-at-input"),
                    label = { Text("Date and time") },
                    supportingText = {
                        state.occurredAtError?.let { Text(it) }
                    },
                    isError = state.occurredAtError != null,
                    singleLine = true
                )
                if (state.occurredAtLabel.isNotBlank()) {
                    Text(
                        text = state.occurredAtLabel,
                        modifier = Modifier.testTag("outbreak-occurred-at-label")
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.timeOptions.forEach { option ->
                        val preset = OutbreakTimePreset.valueOf(option.key)
                        AssistChip(
                            onClick = { viewModel.onIntent(CreateOutbreakUiIntent.SelectTimePreset(preset)) },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("outbreak-time-${preset.name.lowercase()}")
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.onIntent(CreateOutbreakUiIntent.UpdateNotes(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("outbreak-notes"),
                label = { Text("Notes (optional)") },
                minLines = 3
            )

            if (state.formMessage.isNotBlank()) {
                Text(
                    text = state.formMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("outbreak-form-message")
                )
            }

            Button(
                onClick = { viewModel.onIntent(CreateOutbreakUiIntent.Save) },
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("outbreak-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save outbreak")
            }
        }
    }
}
