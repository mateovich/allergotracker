package com.example.app.features.ingestion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.app.core.designsystem.AppScreenScaffold
import com.example.app.core.designsystem.LocalOnlyNotice
import com.example.app.navigation.AppRoutes
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.unit.dp

@Composable
fun CreateIngestionScreen(
    onNavigateToRoute: (String) -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    viewModel: CreateIngestionViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                CreateIngestionUiEffect.NavigateToRecords -> onNavigateToRoute(AppRoutes.RECORDS)
            }
        }
    }

    AppScreenScaffold(title = state.title) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("create-ingestion-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LocalOnlyNotice()

            OutlinedTextField(
                value = state.itemName,
                onValueChange = { viewModel.onIntent(CreateIngestionUiIntent.UpdateItemName(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ingestion-item-name"),
                label = { Text("What did you ingest?") },
                supportingText = {
                    state.itemNameError?.let { Text(it) }
                },
                isError = state.itemNameError != null,
                singleLine = true
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "When did it happen?", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = state.occurredAtLabel,
                    modifier = Modifier.testTag("ingestion-occurred-at-label")
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IngestionTimePreset.entries.forEach { preset ->
                        AssistChip(
                            onClick = { viewModel.onIntent(CreateIngestionUiIntent.SelectTimePreset(preset)) },
                            label = { Text(preset.label) },
                            modifier = Modifier.testTag("ingestion-time-${preset.name.lowercase()}")
                        )
                    }
                }
                state.occurredAtError?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "How much?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.quantityOptions.forEach { option ->
                        FilterChip(
                            selected = state.selectedQuantityLevel == option.level,
                            onClick = {
                                viewModel.onIntent(CreateIngestionUiIntent.SelectQuantityLevel(option.level))
                            },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("ingestion-quantity-${option.level}")
                        )
                    }
                }
                state.quantityError?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.onIntent(CreateIngestionUiIntent.UpdateNotes(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ingestion-notes"),
                label = { Text("Notes (optional)") },
                minLines = 3
            )

            if (state.formMessage.isNotBlank()) {
                Text(
                    text = state.formMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("ingestion-form-message")
                )
            }

            Button(
                onClick = { viewModel.onIntent(CreateIngestionUiIntent.Save) },
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ingestion-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save ingestion")
            }
        }
    }
}

@Composable
internal fun CreateEventFormScreen(
    title: String,
    testTag: String
) {
    AppScreenScaffold(title = title) { contentModifier ->
        Box(
            modifier = contentModifier
                .fillMaxSize()
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "$title form")
        }
    }
}
