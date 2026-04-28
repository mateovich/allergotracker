package io.github.mateovich.allergotracker.features.ingestion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.imePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mateovich.allergotracker.core.designsystem.AppScreenScaffold
import io.github.mateovich.allergotracker.core.designsystem.LocalOnlyNotice
import io.github.mateovich.allergotracker.navigation.AppRoutes
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.unit.dp

@Composable
fun CreateIngestionScreen(
    onNavigateToRoute: (String) -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    selectedReusableEntity: IngestionReusableSelectionResult? = null,
    retiredReusableSelection: Boolean = false,
    onReusableEntityConsumed: () -> Unit = {},
    viewModel: CreateIngestionViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(selectedReusableEntity) {
        selectedReusableEntity?.let {
            viewModel.onIntent(CreateIngestionUiIntent.ApplyReusableSelection(it))
            onReusableEntityConsumed()
        }
    }

    LaunchedEffect(retiredReusableSelection) {
        if (retiredReusableSelection) {
            viewModel.onIntent(CreateIngestionUiIntent.ClearRetiredReusableSelection)
            onReusableEntityConsumed()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                CreateIngestionUiEffect.NavigateToRecords -> onNavigateToRoute(AppRoutes.RECORDS)
                is CreateIngestionUiEffect.NavigateToCreateCompound -> onNavigateToRoute(
                    AppRoutes.createCompound(prefillName = effect.prefillName)
                )
                is CreateIngestionUiEffect.NavigateToEditReusableEntity -> onNavigateToRoute(
                    when (effect.kind) {
                        io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind.INGREDIENT -> AppRoutes.editIngredient(effect.id)
                        io.github.mateovich.allergotracker.features.ingestion.domain.ReusableEntityKind.COMPOUND -> AppRoutes.editCompound(effect.id)
                    }
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
                .testTag("create-ingestion-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LocalOnlyNotice()

            // Unified "What did you ingest?" field that handles:
            // - Manual text entry (when no reusable entity selected)
            // - Entity search and selection
            // - Inline ingredient creation
            ReusableEntitySearchField(
                state = state,
                onIntent = { viewModel.onIntent(it) }
            )

            // Show summary + actions only when a reusable entity is selected
            state.selectedReusableEntitySummary?.let { summary ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("ingestion-reusable-summary")
                    )
                    TextButton(
                        onClick = {
                            viewModel.onIntent(CreateIngestionUiIntent.UpdateReusableSearchQuery(""))
                            viewModel.onIntent(CreateIngestionUiIntent.UpdateItemName(""))
                        },
                        modifier = Modifier.testTag("ingestion-reusable-clear")
                    ) {
                        Text("Clear reusable selection")
                    }
                    TextButton(
                        onClick = { viewModel.onIntent(CreateIngestionUiIntent.EditSelectedReusableEntity) },
                        modifier = Modifier.testTag("ingestion-reusable-edit")
                    ) {
                        Text("Edit saved reusable item")
                    }
                }
            }

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

            if (state.formMessage.isNotBlank()) {
                Text(
                    text = state.formMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("ingestion-form-message")
                )
            }

            Button(
                onClick = {
                    focusManager.clearFocus(force = true)
                    viewModel.onIntent(CreateIngestionUiIntent.Save)
                },
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ingestion-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save ingestion")
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
