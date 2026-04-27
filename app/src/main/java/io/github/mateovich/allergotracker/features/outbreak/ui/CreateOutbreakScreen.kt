package io.github.mateovich.allergotracker.features.outbreak.ui

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
import io.github.mateovich.allergotracker.core.designsystem.DateTimePickerSection
import io.github.mateovich.allergotracker.core.designsystem.LocalOnlyNotice
import io.github.mateovich.allergotracker.features.eventcatalog.domain.ReusableEventCatalogKind
import io.github.mateovich.allergotracker.features.eventcatalog.ui.EditedEventCatalogSelectionResult
import io.github.mateovich.allergotracker.navigation.AppRoutes
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CreateOutbreakScreen(
    onNavigateToRoute: (String) -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    editedCatalogSelection: EditedEventCatalogSelectionResult? = null,
    retiredCatalogKind: ReusableEventCatalogKind? = null,
    onEditedCatalogConsumed: () -> Unit = {},
    viewModel: CreateOutbreakViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(editedCatalogSelection) {
        when (editedCatalogSelection?.kind) {
            ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> {
                viewModel.onIntent(CreateOutbreakUiIntent.SelectOutbreakDescriptorCatalogOption(editedCatalogSelection.id, editedCatalogSelection.name))
                onEditedCatalogConsumed()
            }

            ReusableEventCatalogKind.BODY_LOCATION -> {
                viewModel.onIntent(CreateOutbreakUiIntent.SelectBodyLocationCatalogOption(editedCatalogSelection.id, editedCatalogSelection.name))
                onEditedCatalogConsumed()
            }

            else -> Unit
        }
    }

    LaunchedEffect(retiredCatalogKind) {
        when (retiredCatalogKind) {
            ReusableEventCatalogKind.OUTBREAK_DESCRIPTOR -> {
                viewModel.onIntent(CreateOutbreakUiIntent.ClearRetiredOutbreakDescriptorCatalogSelection)
                onEditedCatalogConsumed()
            }

            ReusableEventCatalogKind.BODY_LOCATION -> {
                viewModel.onIntent(CreateOutbreakUiIntent.ClearRetiredBodyLocationCatalogSelection)
                onEditedCatalogConsumed()
            }

            null, ReusableEventCatalogKind.EXPOSURE_TYPE -> Unit
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                CreateOutbreakUiEffect.NavigateToRecords -> onNavigateToRoute(AppRoutes.RECORDS)
                is CreateOutbreakUiEffect.NavigateToEditEventCatalog -> onNavigateToRoute(
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
                .testTag("create-outbreak-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LocalOnlyNotice()

            OutbreakCatalogField(
                title = "What happened?",
                value = state.outbreakDescriptor,
                error = state.outbreakDescriptorError,
                filteredOptions = state.filteredOutbreakDescriptors,
                showCreateOption = state.showCreateOutbreakDescriptorOption,
                emptyTag = "outbreak-descriptor-empty",
                fieldTag = "outbreak-descriptor",
                createTag = "outbreak-descriptor-create-inline",
                optionTagPrefix = "outbreak-descriptor-option",
                summary = state.selectedOutbreakDescriptorSummary,
                clearSelectionTag = "outbreak-descriptor-clear",
                selectedCatalogId = state.selectedOutbreakDescriptorCatalogId,
                onValueChange = { viewModel.onIntent(CreateOutbreakUiIntent.UpdateOutbreakDescriptor(it)) },
                onCreateTyped = { viewModel.onIntent(CreateOutbreakUiIntent.UseTypedOutbreakDescriptor) },
                onSelectOption = { id, name ->
                    viewModel.onIntent(CreateOutbreakUiIntent.SelectOutbreakDescriptorCatalogOption(id, name))
                },
                onClearSelection = { viewModel.onIntent(CreateOutbreakUiIntent.ClearOutbreakDescriptorCatalogSelection) },
                onEditSelection = { viewModel.onIntent(CreateOutbreakUiIntent.EditSelectedOutbreakDescriptorCatalog) }
            )

            OutbreakCatalogField(
                title = "Where on the body?",
                value = state.bodyLocation,
                error = state.bodyLocationError,
                filteredOptions = state.filteredBodyLocations,
                showCreateOption = state.showCreateBodyLocationOption,
                emptyTag = "outbreak-body-location-empty",
                fieldTag = "outbreak-body-location",
                createTag = "outbreak-body-location-create-inline",
                optionTagPrefix = "outbreak-body-location-option",
                summary = state.selectedBodyLocationSummary,
                clearSelectionTag = "outbreak-body-location-clear",
                selectedCatalogId = state.selectedBodyLocationCatalogId,
                onValueChange = { viewModel.onIntent(CreateOutbreakUiIntent.UpdateBodyLocation(it)) },
                onCreateTyped = { viewModel.onIntent(CreateOutbreakUiIntent.UseTypedBodyLocation) },
                onSelectOption = { id, name ->
                    viewModel.onIntent(CreateOutbreakUiIntent.SelectBodyLocationCatalogOption(id, name))
                },
                onClearSelection = { viewModel.onIntent(CreateOutbreakUiIntent.ClearBodyLocationCatalogSelection) },
                onEditSelection = { viewModel.onIntent(CreateOutbreakUiIntent.EditSelectedBodyLocationCatalog) }
            )

            DateTimePickerSection(
                title = "When did it happen?",
                timeLabel = state.occurredAtLabel,
                dateButtonLabel = viewModel.formatOccurredOnLabel(state.selectedOccurredOn),
                timeButtonLabel = viewModel.formatOccurredTimeLabel(state.selectedOccurredTime),
                selectedDate = state.selectedOccurredOn,
                selectedTime = state.selectedOccurredTime,
                sectionTag = "outbreak-occurred-at-section",
                labelTag = "outbreak-occurred-at-label",
                dateButtonTag = "outbreak-occurred-at-date-picker",
                timeButtonTag = "outbreak-occurred-at-time-picker",
                errorTag = "outbreak-occurred-at-error",
                error = state.occurredAtError,
                onDateSelected = { viewModel.onIntent(CreateOutbreakUiIntent.SelectOccurredOn(it)) },
                onTimeSelected = { viewModel.onIntent(CreateOutbreakUiIntent.SelectOccurredTime(it)) },
                presetContent = {
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
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "How intense was it?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.intensityOptions.forEach { option ->
                        FilterChip(
                            selected = state.selectedIntensityLevel == option.level,
                            onClick = { viewModel.onIntent(CreateOutbreakUiIntent.SelectIntensityLevel(option.level)) },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("outbreak-intensity-${option.level}")
                        )
                    }
                }
                state.intensityError?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
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
                onClick = {
                    focusManager.clearFocus(force = true)
                    viewModel.onIntent(CreateOutbreakUiIntent.Save)
                },
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

@Composable
internal fun OutbreakCatalogField(
    title: String,
    value: String,
    error: String?,
    filteredOptions: List<io.github.mateovich.allergotracker.features.eventcatalog.ui.ReusableEventCatalogOptionUiModel>,
    showCreateOption: Boolean,
    emptyTag: String,
    fieldTag: String,
    createTag: String,
    optionTagPrefix: String,
    summary: String?,
    clearSelectionTag: String,
    selectedCatalogId: Long?,
    onValueChange: (String) -> Unit,
    onCreateTyped: () -> Unit,
    onSelectOption: (Long, String) -> Unit,
    onClearSelection: () -> Unit,
    onEditSelection: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().testTag(fieldTag),
            label = { Text(title) },
            supportingText = { error?.let { Text(it) } },
            isError = error != null,
            singleLine = true
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showCreateOption) {
                AssistChip(
                    onClick = onCreateTyped,
                    label = { Text("Use \"${value.trim()}\"") },
                    modifier = Modifier.testTag(createTag)
                )
            }
            filteredOptions.forEach { option ->
                AssistChip(
                    onClick = { onSelectOption(option.id, option.name) },
                    label = { Text(option.name) },
                    modifier = Modifier.testTag("$optionTagPrefix-${option.id}")
                )
            }
        }
        if (filteredOptions.isEmpty()) {
            Text(
                text = if (value.isBlank()) {
                    "Recent saved values appear here."
                } else {
                    "No saved values match yet. Use the typed value to save it inline."
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(emptyTag)
            )
        }
        summary?.let {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
                if (selectedCatalogId != null) {
                    TextButton(
                        onClick = onClearSelection,
                        modifier = Modifier.testTag(clearSelectionTag)
                    ) {
                        Text("Clear saved selection")
                    }
                    TextButton(onClick = onEditSelection, modifier = Modifier.testTag("$clearSelectionTag-edit")) {
                        Text("Edit saved value")
                    }
                }
            }
        }
    }
}
