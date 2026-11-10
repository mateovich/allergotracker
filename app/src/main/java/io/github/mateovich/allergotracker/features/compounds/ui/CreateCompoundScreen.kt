package io.github.mateovich.allergotracker.features.compounds.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CreateCompoundScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onNavigateBackWithSelection: (io.github.mateovich.allergotracker.features.ingestion.ui.IngestionReusableSelectionResult) -> Unit,
    viewModel: CreateCompoundViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is CreateCompoundUiEffect.NavigateBackWithSelection -> onNavigateBackWithSelection(effect.result)
            }
        }
    }

    state.replacementConfirmation?.let { confirmation ->
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(CreateCompoundUiIntent.DismissReplacementConfirmation) },
            title = { Text("Replace ingredient?") },
            text = {
                Text(
                    "\"${confirmation.ingredientName}\" will be deleted and replaced by this compound. " +
                        "${confirmation.ingestionReferenceCount} saved record(s) and " +
                        "${confirmation.compoundReferenceCount} compound component(s) that use it will point to the new compound. " +
                        "This can't be undone.",
                    modifier = Modifier.testTag("compound-replace-message")
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onIntent(CreateCompoundUiIntent.ConfirmReplacement) },
                    modifier = Modifier.testTag("compound-replace-confirm")
                ) {
                    Text("Replace")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.onIntent(CreateCompoundUiIntent.DismissReplacementConfirmation) },
                    modifier = Modifier.testTag("compound-replace-cancel")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    AppScreenScaffold(title = state.title) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp)
                .testTag("create-compound-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = state.helperMessage, style = MaterialTheme.typography.bodyMedium)
            state.replacedIngredientName?.let { ingredientName ->
                Text(
                    text = "Saving will replace the ingredient \"$ingredientName\" with this compound.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("compound-replace-notice")
                )
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.onIntent(CreateCompoundUiIntent.UpdateName(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compound-name"),
                label = { Text("Compound name") },
                supportingText = { state.nameError?.let { Text(it) } },
                isError = state.nameError != null,
                singleLine = true
            )

            Button(
                onClick = { viewModel.onIntent(CreateCompoundUiIntent.Save) },
                enabled = !state.isSaving && (state.replacedIngredientName == null || state.canSave),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compound-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save compound")
            }

            CompoundComposer(
                prefix = "compound",
                componentSearchQuery = state.componentSearchQuery,
                availableComponents = state.availableComponents,
                filteredComponents = state.filteredComponents,
                components = state.components,
                selectedPreset = state.selectedPreset,
                totalPercentLabel = state.totalPercentLabel,
                componentsError = state.componentsError,
                totalError = state.totalError,
                onSearchQueryChange = { viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentSearchQuery(it)) },
                onAddIngredient = { viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(it)) },
                onAddCompound = { viewModel.onIntent(CreateCompoundUiIntent.AddCompoundComponent(it)) },
                onCreateIngredient = { viewModel.onIntent(CreateCompoundUiIntent.ShowInlineIngredientDialog(it)) },
                onApplyPreset = { viewModel.onIntent(CreateCompoundUiIntent.ApplyPreset(it)) },
                onProportionTextChange = { index, value ->
                    viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentProportion(index, value))
                },
                onProportionSliderChange = { index, basisPoints ->
                    viewModel.onIntent(CreateCompoundUiIntent.AdjustComponentProportion(index, basisPoints))
                },
                onRemoveComponent = { viewModel.onIntent(CreateCompoundUiIntent.RemoveComponent(it)) }
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.onIntent(CreateCompoundUiIntent.UpdateNotes(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compound-notes"),
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
                    modifier = Modifier.testTag("compound-form-message")
                )
            }
        }

        if (state.showInlineIngredientDialog) {
            CreateCompoundInlineIngredientDialog(
                name = state.inlineIngredientName,
                isSaving = state.isCreatingInlineIngredient,
                errorMessage = state.inlineIngredientError,
                onNameChange = { viewModel.onIntent(CreateCompoundUiIntent.UpdateInlineIngredientName(it)) },
                onSave = { name -> viewModel.onIntent(CreateCompoundUiIntent.CreateInlineIngredient(name)) },
                onCancel = { viewModel.onIntent(CreateCompoundUiIntent.CancelInlineIngredientCreation) }
            )
        }
    }
}

@Composable
private fun CreateCompoundInlineIngredientDialog(
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
        onDismissRequest = onCancel,
        title = { Text("Create new ingredient") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("compound-inline-ingredient-name"),
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
                modifier = Modifier.testTag("compound-inline-ingredient-save")
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
                modifier = Modifier.testTag("compound-inline-ingredient-cancel")
            ) {
                Text("Cancel")
            }
        }
    )
}
