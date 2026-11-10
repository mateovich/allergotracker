package io.github.mateovich.allergotracker.features.compounds.ui

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mateovich.allergotracker.core.designsystem.AppScreenScaffold
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompoundMergePreview
import io.github.mateovich.allergotracker.features.compounds.domain.ReusableCompoundRetirementImpact
import io.github.mateovich.allergotracker.features.ingestion.ui.IngestionReusableSelectionResult
import kotlinx.coroutines.flow.collectLatest

@Composable
fun EditCompoundScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onNavigateBackWithSelection: (IngestionReusableSelectionResult) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: EditCompoundViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.onIntent(EditCompoundUiIntent.Load)
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is EditCompoundUiEffect.NavigateBackWithSelection -> onNavigateBackWithSelection(effect.result)
                EditCompoundUiEffect.NavigateBackAfterRetirement -> onNavigateBack()
            }
        }
    }

    AppScreenScaffold(title = state.title) { contentModifier ->
        if (state.isLoading) {
            Column(modifier = contentModifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.testTag("edit-compound-loading"))
            }
            return@AppScreenScaffold
        }

        if (state.name.isBlank() && state.formMessage.isNotBlank()) {
            Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .testTag("edit-compound-load-error"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = state.formMessage, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = { viewModel.onIntent(EditCompoundUiIntent.Load) },
                    modifier = Modifier.testTag("edit-compound-retry")
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
                .testTag("edit-compound-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = state.helperMessage, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.onIntent(EditCompoundUiIntent.UpdateName(it)) },
                modifier = Modifier.fillMaxWidth().testTag("edit-compound-name"),
                label = { Text("Compound name") },
                supportingText = { state.nameError?.let { Text(it) } },
                isError = state.nameError != null,
                singleLine = true
            )
            Button(
                onClick = { viewModel.onIntent(EditCompoundUiIntent.Save) },
                enabled = state.canSave && !state.isSaving,
                modifier = Modifier.fillMaxWidth().testTag("edit-compound-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save changes")
            }
            Button(
                onClick = { viewModel.onIntent(EditCompoundUiIntent.RequestMergeFlow) },
                enabled = !state.isSaving && !state.isRetiring && !state.isMerging,
                modifier = Modifier.fillMaxWidth().testTag("edit-compound-merge")
            ) {
                Text("Merge duplicate compound")
            }
            Button(
                onClick = { viewModel.onIntent(EditCompoundUiIntent.RequestRetirementPreview) },
                enabled = !state.isSaving && !state.isRetiring,
                modifier = Modifier.fillMaxWidth().testTag("edit-compound-retire")
            ) {
                Text("Retire compound")
            }
            CompoundComposer(
                prefix = "edit-compound",
                componentSearchQuery = state.componentSearchQuery,
                availableComponents = state.availableComponents,
                filteredComponents = state.filteredComponents,
                components = state.components,
                selectedPreset = state.selectedPreset,
                totalPercentLabel = state.totalPercentLabel,
                componentsError = state.componentsError,
                totalError = state.totalError,
                onSearchQueryChange = { viewModel.onIntent(EditCompoundUiIntent.UpdateComponentSearchQuery(it)) },
                onAddIngredient = { viewModel.onIntent(EditCompoundUiIntent.AddIngredientComponent(it)) },
                onAddCompound = { viewModel.onIntent(EditCompoundUiIntent.AddCompoundComponent(it)) },
                onCreateIngredient = { viewModel.onIntent(EditCompoundUiIntent.ShowInlineIngredientDialog(it)) },
                onApplyPreset = { viewModel.onIntent(EditCompoundUiIntent.ApplyPreset(it)) },
                onProportionTextChange = { index, value ->
                    viewModel.onIntent(EditCompoundUiIntent.UpdateComponentProportion(index, value))
                },
                onProportionSliderChange = { index, basisPoints ->
                    viewModel.onIntent(EditCompoundUiIntent.AdjustComponentProportion(index, basisPoints))
                },
                onRemoveComponent = { viewModel.onIntent(EditCompoundUiIntent.RemoveComponent(it)) }
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.onIntent(EditCompoundUiIntent.UpdateNotes(it)) },
                modifier = Modifier.fillMaxWidth().testTag("edit-compound-notes"),
                label = { Text("Notes (optional)") },
                minLines = 3
            )
            if (state.formMessage.isNotBlank()) {
                Text(
                    text = state.formMessage,
                    color = if (state.isFormMessageError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.testTag("edit-compound-form-message")
                )
            }
        }

        if (state.showInlineIngredientDialog) {
            EditCompoundInlineIngredientDialog(
                name = state.inlineIngredientName,
                isSaving = state.isCreatingInlineIngredient,
                errorMessage = state.inlineIngredientError,
                onNameChange = { viewModel.onIntent(EditCompoundUiIntent.UpdateInlineIngredientName(it)) },
                onSave = { name -> viewModel.onIntent(EditCompoundUiIntent.CreateInlineIngredient(name)) },
                onCancel = { viewModel.onIntent(EditCompoundUiIntent.CancelInlineIngredientCreation) }
            )
        }

        if (state.isMergeDialogVisible) {
            AlertDialog(
                onDismissRequest = { viewModel.onIntent(EditCompoundUiIntent.DismissMergeDialog) },
                title = { Text("Merge compound?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Source compound: ${state.name}",
                            modifier = Modifier.testTag("edit-compound-merge-source")
                        )
                        OutlinedTextField(
                            value = state.mergeDestinationQuery,
                            onValueChange = { viewModel.onIntent(EditCompoundUiIntent.UpdateMergeDestinationQuery(it)) },
                            modifier = Modifier.fillMaxWidth().testTag("edit-compound-merge-query"),
                            label = { Text("Canonical compound") },
                            singleLine = true
                        )
                        if (state.mergeDestinationOptions.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                state.mergeDestinationOptions.forEach { compound ->
                                    AssistChip(
                                        onClick = { viewModel.onIntent(EditCompoundUiIntent.SelectMergeDestination(compound.id)) },
                                        label = { Text(compound.name) },
                                        modifier = Modifier.testTag("edit-compound-merge-option-${compound.id}")
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No saved compounds match this search.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("edit-compound-merge-empty")
                            )
                        }
                        state.selectedMergeDestinationId?.let { destinationId ->
                            state.mergeDestinationOptions.firstOrNull { it.id == destinationId }?.let { destination ->
                                Text(
                                    text = "Canonical destination: ${destination.name}",
                                    modifier = Modifier.testTag("edit-compound-merge-selection")
                                )
                            }
                        }
                        if (state.isLoadingMergePreview) {
                            CircularProgressIndicator(modifier = Modifier.testTag("edit-compound-merge-loading"))
                        }
                        state.mergePreview?.let { preview ->
                            Text(
                                text = buildCompoundMergeMessage(preview),
                                modifier = Modifier.testTag("edit-compound-merge-preview")
                            )
                        }
                        state.mergeError?.let { error ->
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("edit-compound-merge-error")
                            )
                        }
                    }
                },
                confirmButton = {
                    val mergePreview = state.mergePreview
                    if (mergePreview != null && !mergePreview.isBlocked) {
                        TextButton(
                            onClick = { viewModel.onIntent(EditCompoundUiIntent.ConfirmMerge) },
                            enabled = !state.isMerging,
                            modifier = Modifier.testTag("edit-compound-merge-confirm")
                        ) {
                            Text(if (state.isMerging) "Merging..." else "Confirm merge")
                        }
                    } else {
                        TextButton(
                            onClick = { viewModel.onIntent(EditCompoundUiIntent.LoadMergePreview) },
                            enabled = !state.isLoadingMergePreview,
                            modifier = Modifier.testTag("edit-compound-merge-preview-action")
                        ) {
                            Text("Preview impact")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.onIntent(EditCompoundUiIntent.DismissMergeDialog) },
                        modifier = Modifier.testTag("edit-compound-merge-dismiss")
                    ) {
                        Text(if (state.mergePreview?.isBlocked == true) "Close" else "Cancel")
                    }
                }
            )
        }

        if (state.isRetirementDialogVisible) {
            state.retirementImpact?.let { impact ->
            val message = buildCompoundRetirementMessage(impact)
            AlertDialog(
                onDismissRequest = { viewModel.onIntent(EditCompoundUiIntent.DismissRetirementDialog) },
                title = { Text("Retire compound?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(message, modifier = Modifier.testTag("edit-compound-retire-message"))
                        if (state.formMessage.isNotBlank()) {
                            Text(
                                text = state.formMessage,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("edit-compound-retire-error")
                            )
                        }
                    }
                },
                confirmButton = {
                    if (!impact.isBlocked) {
                        TextButton(
                            onClick = { viewModel.onIntent(EditCompoundUiIntent.ConfirmRetirement) },
                            enabled = !state.isRetiring,
                            modifier = Modifier.testTag("edit-compound-retire-confirm")
                        ) {
                            Text(if (state.isRetiring) "Retiring..." else "Confirm")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.onIntent(EditCompoundUiIntent.DismissRetirementDialog) },
                        modifier = Modifier.testTag("edit-compound-retire-dismiss")
                    ) {
                        Text(if (impact.isBlocked) "Close" else "Cancel")
                    }
                }
            )
            }
        }
    }
}

@Composable
private fun EditCompoundInlineIngredientDialog(
    name: String,
    isSaving: Boolean,
    errorMessage: String?,
    onNameChange: (String) -> Unit,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onCancel() },
        title = { Text("Create new ingredient") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("edit-compound-inline-ingredient-name"),
                    label = { Text("Ingredient name") },
                    supportingText = {
                        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    isError = errorMessage != null,
                    singleLine = true,
                    enabled = !isSaving
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) },
                enabled = !isSaving && name.isNotBlank(),
                modifier = Modifier.testTag("edit-compound-inline-ingredient-save")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(if (isSaving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !isSaving,
                modifier = Modifier.testTag("edit-compound-inline-ingredient-cancel")
            ) {
                Text("Cancel")
            }
        }
    )
}

private fun buildCompoundMergeMessage(preview: ReusableCompoundMergePreview): String {
    return buildString {
        appendLine("Impact preview:")
        appendLine("- Destination: ${preview.destination.name}")
        appendLine("- Ingestion records moved: ${preview.ingestionReferenceCount}")
        appendLine("- Parent compound references moved: ${preview.parentCompoundReferenceCount}")
        if (preview.conflictingParentCompoundCount > 0) {
            appendLine("- Conflicting parent compounds: ${preview.conflictingParentCompoundCount}")
        }
        if (preview.recursiveParentCompoundCount > 0) {
            appendLine("- Recursive parent compounds: ${preview.recursiveParentCompoundCount}")
        }
        appendLine()
        append(
            preview.blockMessage
                ?: "Safe path: references will move to the canonical compound and the duplicate source will be removed."
        )
    }.trimEnd()
}

private fun buildCompoundRetirementMessage(impact: ReusableCompoundRetirementImpact): String {
    return buildString {
        appendLine("Current references:")
        appendLine("- Ingestion records: ${impact.ingestionReferenceCount}")
        appendLine("- Nested compounds: ${impact.childCompoundReferenceCount}")
        appendLine()
        append(
            if (impact.isBlocked) {
                "Safe path: update the linked nested compounds first, then retry retirement."
            } else if (impact.requiresDetachment) {
                "Safe path: retirement will detach the linked ingestion records and keep their current text labels for manual review."
            } else {
                "Safe path: no active references remain, so this compound can be removed from the reusable catalog."
            }
        )
    }.trimEnd()
}
