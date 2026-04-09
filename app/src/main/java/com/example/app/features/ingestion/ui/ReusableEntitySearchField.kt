package com.example.app.features.ingestion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun ReusableEntitySearchField(
    state: CreateIngestionUiState,
    onIntent: (CreateIngestionUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasSelection = state.selectedReusableEntityId != null

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasSelection) {
            // Read-only field showing selected entity name
            OutlinedTextField(
                value = state.reusableSearchQuery,
                onValueChange = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ingestion-reusable-search"),
                label = { Text("What did you ingest?") },
                readOnly = true,
                trailingIcon = {
                    TextButton(
                        onClick = {
                            onIntent(CreateIngestionUiIntent.UpdateReusableSearchQuery(""))
                            onIntent(CreateIngestionUiIntent.UpdateItemName(""))
                        }
                    ) {
                        Text("Clear")
                    }
                },
                singleLine = true
            )
        } else {
            // Editable search field
            OutlinedTextField(
                value = state.reusableSearchQuery,
                onValueChange = { query ->
                    onIntent(CreateIngestionUiIntent.UpdateReusableSearchQuery(query))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ingestion-reusable-search"),
                label = { Text("What did you ingest?") },
                singleLine = true
            )

            // FlowRow: chip "New:'name'" first, then matching saved entities — same pattern as
            // CreateExposureScreen / CreateOutbreakScreen
            val showCreateChip = state.reusableSearchQuery.isNotBlank()
            val isExactMatch = state.hasNormalizedExactMatch()

            if (showCreateChip || state.filteredReusableEntities.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("ingestion-search-dropdown")
                ) {
                    // "New ingredient" chip — first in flow, 3 states
                    if (showCreateChip) {
                        AssistChip(
                            onClick = {
                                if (!isExactMatch) {
                                    onIntent(
                                        CreateIngestionUiIntent.ShowInlineIngredientDialog(
                                            state.reusableSearchQuery
                                        )
                                    )
                                }
                            },
                            enabled = !isExactMatch,
                            label = {
                                Text(
                                    if (isExactMatch) "'${state.reusableSearchQuery}' already saved"
                                    else "New: '${state.reusableSearchQuery}'"
                                )
                            },
                            modifier = Modifier.testTag("ingestion-create-inline-ingredient")
                        )
                    }

                    // Matching saved entities (ingredients + compounds)
                    state.filteredReusableEntities.forEach { option ->
                        AssistChip(
                            onClick = {
                                onIntent(
                                    CreateIngestionUiIntent.SelectReusableEntity(
                                        IngestionReusableSelectionResult(
                                            id = option.id,
                                            name = option.name,
                                            kind = option.kind
                                        )
                                    )
                                )
                            },
                            label = { Text("${option.name} - ${option.supportingLabel}") },
                            modifier = Modifier.testTag(
                                "ingestion-existing-${option.kind.storageValue}-${option.id}"
                            )
                        )
                    }
                }
            } else if (state.reusableSearchQuery.isBlank()) {
                Text(
                    text = "Recent saved items appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("ingestion-reusable-empty")
                )
            }

            // "New compound" chip — full-screen compound creation (separate row, always visible)
            AssistChip(
                onClick = { onIntent(CreateIngestionUiIntent.CreateNewCompound) },
                label = { Text("New compound") },
                modifier = Modifier.testTag("ingestion-create-compound")
            )
        }
    }

    // Inline ingredient creation dialog
    if (state.showInlineIngredientDialog) {
        InlineIngredientCreationDialog(
            initialName = state.inlineIngredientName,
            isSaving = state.isCreatingInlineIngredient,
            errorMessage = state.inlineIngredientError,
            onSave = { name ->
                onIntent(CreateIngestionUiIntent.CreateInlineIngredient(name))
            },
            onCancel = {
                onIntent(CreateIngestionUiIntent.CancelInlineIngredientCreation)
            }
        )
    }
}

@Composable
private fun InlineIngredientCreationDialog(
    initialName: String,
    isSaving: Boolean,
    errorMessage: String?,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
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
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("ingestion-inline-ingredient-name"),
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
                modifier = Modifier.testTag("ingestion-inline-ingredient-save")
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
                modifier = Modifier.testTag("ingestion-inline-ingredient-cancel")
            ) {
                Text("Cancel")
            }
        }
    )
}

// Normalize identical to CreateReusableIngredientUseCase.normalizeReusableName():
// trim + collapse internal whitespace + lowercase for comparison
private fun CreateIngestionUiState.hasNormalizedExactMatch(): Boolean {
    val normalizedQuery = reusableSearchQuery.trim().replace(Regex("\\s+"), " ").lowercase()
    if (normalizedQuery.isBlank()) return false
    return filteredReusableEntities.any {
        it.name.trim().replace(Regex("\\s+"), " ").lowercase() == normalizedQuery
    }
}
