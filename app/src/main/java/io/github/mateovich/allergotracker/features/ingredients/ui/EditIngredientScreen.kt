package io.github.mateovich.allergotracker.features.ingredients.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import io.github.mateovich.allergotracker.core.designsystem.AppScreenScaffold
import io.github.mateovich.allergotracker.features.ingestion.ui.IngestionReusableSelectionResult
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredientMergePreview
import io.github.mateovich.allergotracker.features.ingredients.domain.ReusableIngredientRetirementImpact
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EditIngredientScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onNavigateBackWithSelection: (IngestionReusableSelectionResult) -> Unit,
    onNavigateToCreateCompound: (String) -> Unit,
    onNavigateBack: () -> Unit,
    selectedReusableEntity: IngestionReusableSelectionResult? = null,
    onReusableEntityConsumed: () -> Unit = {},
    viewModel: EditIngredientViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(selectedReusableEntity) {
        selectedReusableEntity?.let {
            onNavigateBackWithSelection(it)
            onReusableEntityConsumed()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.onIntent(EditIngredientUiIntent.Load)
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is EditIngredientUiEffect.NavigateBackWithSelection -> onNavigateBackWithSelection(effect.result)
                is EditIngredientUiEffect.NavigateToCreateCompound -> onNavigateToCreateCompound(
                    io.github.mateovich.allergotracker.navigation.AppRoutes.createCompound(
                        prefillName = effect.name,
                        seedIngredientId = effect.id,
                        seedIngredientName = effect.name
                    )
                )
                EditIngredientUiEffect.NavigateBackAfterRetirement -> onNavigateBack()
            }
        }
    }

    AppScreenScaffold(title = state.title) { contentModifier ->
        if (state.isLoading) {
            Column(modifier = contentModifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("edit-ingredient-loading"))
            }
            return@AppScreenScaffold
        }

        if (state.name.isBlank() && state.formMessage.isNotBlank()) {
            Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .testTag("edit-ingredient-load-error"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = state.formMessage, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = { viewModel.onIntent(EditIngredientUiIntent.Load) },
                    modifier = Modifier.testTag("edit-ingredient-retry")
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
                .testTag("edit-ingredient-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.onIntent(EditIngredientUiIntent.UpdateName(it)) },
                modifier = Modifier.fillMaxWidth().testTag("edit-ingredient-name"),
                label = { Text("Ingredient name") },
                supportingText = { state.nameError?.let { Text(it) } },
                isError = state.nameError != null,
                singleLine = true
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.onIntent(EditIngredientUiIntent.UpdateNotes(it)) },
                modifier = Modifier.fillMaxWidth().testTag("edit-ingredient-notes"),
                label = { Text("Notes (optional)") },
                minLines = 3
            )
            if (state.isProvisional) {
                Text(
                    text = "Provisional compound: complete it with \"Make it a compound\" or keep it as a regular ingredient.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("edit-ingredient-provisional-notice")
                )
            }
            if (state.formMessage.isNotBlank()) {
                Text(
                    text = state.formMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("edit-ingredient-form-message")
                )
            }
            Button(
                onClick = { viewModel.onIntent(EditIngredientUiIntent.Save) },
                enabled = state.canSave && !state.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("edit-ingredient-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save changes")
            }
            Button(
                onClick = { viewModel.onIntent(EditIngredientUiIntent.MakeIntoCompound) },
                enabled = !state.isSaving && !state.isRetiring && !state.isMerging,
                modifier = Modifier.fillMaxWidth().testTag("edit-ingredient-make-compound")
            ) {
                Text("Make it a compound")
            }
            if (state.isProvisional) {
                Button(
                    onClick = { viewModel.onIntent(EditIngredientUiIntent.KeepAsRegularIngredient) },
                    enabled = !state.isSaving && !state.isRetiring && !state.isMerging,
                    modifier = Modifier.fillMaxWidth().testTag("edit-ingredient-keep-regular")
                ) {
                    Text("Keep as regular ingredient")
                }
            }
            Button(
                onClick = { viewModel.onIntent(EditIngredientUiIntent.RequestMergeFlow) },
                enabled = !state.isSaving && !state.isRetiring && !state.isMerging,
                modifier = Modifier.fillMaxWidth().testTag("edit-ingredient-merge")
            ) {
                Text("Merge duplicate ingredient")
            }
            Button(
                onClick = { viewModel.onIntent(EditIngredientUiIntent.RequestRetirementPreview) },
                enabled = !state.isSaving && !state.isRetiring,
                modifier = Modifier.fillMaxWidth().testTag("edit-ingredient-retire")
            ) {
                Text("Retire ingredient")
            }
        }

        if (state.isMergeDialogVisible) {
            AlertDialog(
                onDismissRequest = { viewModel.onIntent(EditIngredientUiIntent.DismissMergeDialog) },
                title = { Text("Merge ingredient?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Source ingredient: ${state.name}",
                            modifier = Modifier.testTag("edit-ingredient-merge-source")
                        )
                        OutlinedTextField(
                            value = state.mergeDestinationQuery,
                            onValueChange = { viewModel.onIntent(EditIngredientUiIntent.UpdateMergeDestinationQuery(it)) },
                            modifier = Modifier.fillMaxWidth().testTag("edit-ingredient-merge-query"),
                            label = { Text("Canonical ingredient") },
                            singleLine = true
                        )
                        if (state.mergeDestinationOptions.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                state.mergeDestinationOptions.forEach { ingredient ->
                                    AssistChip(
                                        onClick = { viewModel.onIntent(EditIngredientUiIntent.SelectMergeDestination(ingredient.id)) },
                                        label = { Text(ingredient.name) },
                                        modifier = Modifier.testTag("edit-ingredient-merge-option-${ingredient.id}")
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No saved ingredients match this search.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("edit-ingredient-merge-empty")
                            )
                        }
                        state.selectedMergeDestinationId?.let { destinationId ->
                            state.mergeDestinationOptions.firstOrNull { it.id == destinationId }?.let { destination ->
                                Text(
                                    text = "Canonical destination: ${destination.name}",
                                    modifier = Modifier.testTag("edit-ingredient-merge-selection")
                                )
                            }
                        }
                        if (state.isLoadingMergePreview) {
                            CircularProgressIndicator(modifier = Modifier.testTag("edit-ingredient-merge-loading"))
                        }
                        state.mergePreview?.let { preview ->
                            Text(
                                text = buildIngredientMergeMessage(preview),
                                modifier = Modifier.testTag("edit-ingredient-merge-preview")
                            )
                        }
                        state.mergeError?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("edit-ingredient-merge-error")
                            )
                        }
                    }
                },
                confirmButton = {
                    val mergePreview = state.mergePreview
                    if (mergePreview != null && !mergePreview.isBlocked) {
                        TextButton(
                            onClick = { viewModel.onIntent(EditIngredientUiIntent.ConfirmMerge) },
                            enabled = !state.isMerging,
                            modifier = Modifier.testTag("edit-ingredient-merge-confirm")
                        ) {
                            Text(if (state.isMerging) "Merging..." else "Confirm merge")
                        }
                    } else {
                        TextButton(
                            onClick = { viewModel.onIntent(EditIngredientUiIntent.LoadMergePreview) },
                            enabled = !state.isLoadingMergePreview,
                            modifier = Modifier.testTag("edit-ingredient-merge-preview-action")
                        ) {
                            Text("Preview impact")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.onIntent(EditIngredientUiIntent.DismissMergeDialog) },
                        modifier = Modifier.testTag("edit-ingredient-merge-dismiss")
                    ) {
                        Text(if (state.mergePreview?.isBlocked == true) "Close" else "Cancel")
                    }
                }
            )
        }

        if (state.isRetirementDialogVisible) {
            state.retirementImpact?.let { impact ->
            val message = buildIngredientRetirementMessage(impact)
            AlertDialog(
                onDismissRequest = { viewModel.onIntent(EditIngredientUiIntent.DismissRetirementDialog) },
                title = { Text("Retire ingredient?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(message, modifier = Modifier.testTag("edit-ingredient-retire-message"))
                        if (state.formMessage.isNotBlank()) {
                            Text(
                                text = state.formMessage,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("edit-ingredient-retire-error")
                            )
                        }
                    }
                },
                confirmButton = {
                    if (!impact.isBlocked) {
                        TextButton(
                            onClick = { viewModel.onIntent(EditIngredientUiIntent.ConfirmRetirement) },
                            enabled = !state.isRetiring,
                            modifier = Modifier.testTag("edit-ingredient-retire-confirm")
                        ) {
                            Text(if (state.isRetiring) "Retiring..." else "Confirm")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.onIntent(EditIngredientUiIntent.DismissRetirementDialog) },
                        modifier = Modifier.testTag("edit-ingredient-retire-dismiss")
                    ) {
                        Text(if (impact.isBlocked) "Close" else "Cancel")
                    }
                }
            )
            }
        }
    }
}

private fun buildIngredientRetirementMessage(impact: ReusableIngredientRetirementImpact): String {
    return buildString {
        appendLine("Current references:")
        appendLine("- Ingestion records: ${impact.ingestionReferenceCount}")
        appendLine("- Compound components: ${impact.compoundReferenceCount}")
        appendLine()
        append(
            if (impact.isBlocked) {
                "Safe path: replace this ingredient in the linked compound components, then try retirement again."
            } else if (impact.requiresDetachment) {
                "Safe path: retirement will detach the linked ingestion records and keep their current text labels for manual review."
            } else {
                "Safe path: no active references remain, so this ingredient can be removed from the reusable catalog."
            }
        )
    }.trimEnd()
}

private fun buildIngredientMergeMessage(preview: ReusableIngredientMergePreview): String {
    return buildString {
        appendLine("Impact preview:")
        appendLine("- Destination: ${preview.destination.name}")
        appendLine("- Ingestion records moved: ${preview.ingestionReferenceCount}")
        appendLine("- Compound components moved: ${preview.compoundReferenceCount}")
        if (preview.conflictingCompoundCount > 0) {
            appendLine("- Conflicting compounds: ${preview.conflictingCompoundCount}")
        }
        appendLine()
        append(
            preview.blockMessage
                ?: "Safe path: references will move to the canonical ingredient and the duplicate source will be removed."
        )
    }.trimEnd()
}
