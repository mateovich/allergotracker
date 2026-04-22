package io.github.mateovich.allergotracker.features.exposure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mateovich.allergotracker.core.designsystem.AppScreenScaffold
import io.github.mateovich.allergotracker.core.designsystem.LocalOnlyNotice
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogKind
import io.github.mateovich.allergotracker.features.eventcatalog.ui.EditedEventCatalogSelectionResult
import io.github.mateovich.allergotracker.navigation.AppRoutes
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CreateExposureScreen(
    onNavigateToRoute: (String) -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    editedCatalogSelection: EditedEventCatalogSelectionResult? = null,
    retiredCatalogKind: ReusableEventCatalogKind? = null,
    onEditedCatalogConsumed: () -> Unit = {},
    viewModel: CreateExposureViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(editedCatalogSelection) {
        editedCatalogSelection?.takeIf { it.kind == ReusableEventCatalogKind.EXPOSURE_TYPE }?.let {
            viewModel.onIntent(CreateExposureUiIntent.SelectExposureCatalogOption(it.id, it.name))
            onEditedCatalogConsumed()
        }
    }

    LaunchedEffect(retiredCatalogKind) {
        if (retiredCatalogKind == ReusableEventCatalogKind.EXPOSURE_TYPE) {
            viewModel.onIntent(CreateExposureUiIntent.ClearRetiredExposureCatalogSelection)
            onEditedCatalogConsumed()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                CreateExposureUiEffect.NavigateToRecords -> onNavigateToRoute(AppRoutes.RECORDS)
                is CreateExposureUiEffect.NavigateToEditEventCatalog -> onNavigateToRoute(
                    AppRoutes.editEventCatalog(effect.kindStorageValue, effect.id)
                )
            }
        }
    }

    AppScreenScaffold(title = state.title) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp)
                .testTag("create-exposure-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LocalOnlyNotice()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Exposure type", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = state.exposureType,
                    onValueChange = { viewModel.onIntent(CreateExposureUiIntent.UpdateExposureType(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exposure-type"),
                    label = { Text("What were you exposed to?") },
                    supportingText = {
                        state.exposureTypeError?.let { Text(it) }
                    },
                    isError = state.exposureTypeError != null,
                    singleLine = true
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.showCreateExposureTypeOption) {
                        AssistChip(
                            onClick = { viewModel.onIntent(CreateExposureUiIntent.UseTypedExposureType) },
                            label = { Text("Use \"${state.exposureType.trim()}\"") },
                            modifier = Modifier.testTag("exposure-type-create-inline")
                        )
                    }
                    state.filteredExposureTypes.forEach { option ->
                        AssistChip(
                            onClick = {
                                viewModel.onIntent(
                                    CreateExposureUiIntent.SelectExposureCatalogOption(option.id, option.name)
                                )
                            },
                            label = { Text(option.name) },
                            modifier = Modifier.testTag("exposure-type-option-${option.id}")
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
                        modifier = Modifier.testTag("exposure-type-empty")
                    )
                }
                state.selectedExposureTypeSummary?.let { summary ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("exposure-type-summary")
                        )
                        if (state.selectedExposureTypeCatalogId != null) {
                            TextButton(
                                onClick = { viewModel.onIntent(CreateExposureUiIntent.ClearExposureCatalogSelection) },
                                modifier = Modifier.testTag("exposure-type-clear")
                            ) {
                                Text("Clear saved selection")
                            }
                            TextButton(
                                onClick = { viewModel.onIntent(CreateExposureUiIntent.EditSelectedExposureCatalog) },
                                modifier = Modifier.testTag("exposure-type-edit")
                            ) {
                                Text("Edit saved value")
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "When did it happen?", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = state.occurredAtInput,
                    onValueChange = { viewModel.onIntent(CreateExposureUiIntent.UpdateOccurredAtInput(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exposure-occurred-at-input"),
                    label = { Text("Date and time") },
                    supportingText = {
                        state.occurredAtError?.let { Text(it) }
                    },
                    isError = state.occurredAtError != null,
                    singleLine = true
                )
                Text(
                    text = state.occurredAtLabel,
                    modifier = Modifier.testTag("exposure-occurred-at-label")
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposureTimePreset.entries.forEach { preset ->
                        AssistChip(
                            onClick = { viewModel.onIntent(CreateExposureUiIntent.SelectTimePreset(preset)) },
                            label = { Text(preset.label) },
                            modifier = Modifier.testTag("exposure-time-${preset.name.lowercase()}")
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "How intense was it?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.intensityOptions.forEach { option ->
                        FilterChip(
                            selected = state.selectedIntensityLevel == option.level,
                            onClick = { viewModel.onIntent(CreateExposureUiIntent.SelectIntensityLevel(option.level)) },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("exposure-intensity-${option.level}")
                        )
                    }
                }
                state.intensityLevelError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("exposure-intensity-error")
                    )
                }
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.onIntent(CreateExposureUiIntent.UpdateNotes(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exposure-notes"),
                label = { Text("Notes (optional)") },
                minLines = 3
            )

            if (state.formMessage.isNotBlank()) {
                Text(
                    text = state.formMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("exposure-form-message")
                )
            }

            Button(
                onClick = {
                    focusManager.clearFocus(force = true)
                    viewModel.onIntent(CreateExposureUiIntent.Save)
                },
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("exposure-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save exposure")
            }
        }
    }
}
