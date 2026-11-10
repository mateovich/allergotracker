package io.github.mateovich.allergotracker.features.ingestion.ui

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

/**
 * Parameter struct for the generalized reusable entity search field.
 * Both Create Ingestion and Edit Ingestion populate this from their respective states.
 */
data class ReusableEntitySearchParams(
    val searchQuery: String,
    val filteredEntities: List<ReusableEntityPickerOptionUiModel>,
    val selectedEntityId: Long?,
    val isCreatingInlineIngredient: Boolean,
    val inlineIngredientError: String?,
    val showInlineIngredientDialog: Boolean,
    val inlineIngredientName: String,
    val showNewCompoundChip: Boolean = true,
    val allowEditingSelection: Boolean = false,
    val searchFieldTag: String = "ingestion-reusable-search"
)

/**
 * Callbacks for the generalized reusable entity search field.
 */
data class ReusableEntitySearchCallbacks(
    val onSearchQueryChange: (String) -> Unit,
    val onSelectEntity: (IngestionReusableSelectionResult) -> Unit,
    val onClearSelection: () -> Unit,
    val onShowInlineIngredientDialog: (prefillName: String) -> Unit,
    val onCreateInlineIngredient: (name: String) -> Unit,
    val onUpdateInlineIngredientName: (String) -> Unit,
    val onCancelInlineIngredientCreation: () -> Unit,
    val onCreateNewCompound: (() -> Unit)? = null
)

@Composable
internal fun ReusableEntitySearchField(
    state: CreateIngestionUiState,
    onIntent: (CreateIngestionUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    ReusableEntitySearchFieldCore(
        params = ReusableEntitySearchParams(
            searchQuery = state.reusableSearchQuery,
            filteredEntities = state.filteredReusableEntities,
            selectedEntityId = state.selectedReusableEntityId,
            isCreatingInlineIngredient = state.isCreatingInlineIngredient,
            inlineIngredientError = state.inlineIngredientError,
            showInlineIngredientDialog = state.showInlineIngredientDialog,
            inlineIngredientName = state.inlineIngredientName,
            showNewCompoundChip = true
        ),
        callbacks = ReusableEntitySearchCallbacks(
            onSearchQueryChange = { query ->
                onIntent(CreateIngestionUiIntent.UpdateReusableSearchQuery(query))
            },
            onSelectEntity = { result ->
                onIntent(CreateIngestionUiIntent.SelectReusableEntity(result))
            },
            onClearSelection = {
                onIntent(CreateIngestionUiIntent.UpdateReusableSearchQuery(""))
                onIntent(CreateIngestionUiIntent.UpdateItemName(""))
            },
            onShowInlineIngredientDialog = { prefillName ->
                onIntent(CreateIngestionUiIntent.ShowInlineIngredientDialog(prefillName))
            },
            onCreateInlineIngredient = { name ->
                onIntent(CreateIngestionUiIntent.CreateInlineIngredient(name))
            },
            onUpdateInlineIngredientName = { name ->
                onIntent(CreateIngestionUiIntent.UpdateInlineIngredientName(name))
            },
            onCancelInlineIngredientCreation = {
                onIntent(CreateIngestionUiIntent.CancelInlineIngredientCreation)
            },
            onCreateNewCompound = {
                onIntent(CreateIngestionUiIntent.CreateNewCompound)
            }
        ),
        modifier = modifier
    )
}

@Composable
internal fun EditIngestionReusableSearchField(
    state: EditIngestionUiState,
    onIntent: (EditIngestionUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    ReusableEntitySearchFieldCore(
        params = ReusableEntitySearchParams(
            searchQuery = state.reusableSearchQuery,
            filteredEntities = state.filteredReusableEntities,
            selectedEntityId = state.selectedReusableEntityId,
            isCreatingInlineIngredient = state.isCreatingInlineIngredient,
            inlineIngredientError = state.inlineIngredientError,
            showInlineIngredientDialog = state.showInlineIngredientDialog,
            inlineIngredientName = state.inlineIngredientName,
            showNewCompoundChip = false,
            allowEditingSelection = true,
            searchFieldTag = "edit-ingestion-item-name"
        ),
        callbacks = ReusableEntitySearchCallbacks(
            onSearchQueryChange = { query ->
                onIntent(EditIngestionUiIntent.UpdateReusableSearchQuery(query))
            },
            onSelectEntity = { result ->
                onIntent(EditIngestionUiIntent.SelectReusableEntity(result))
            },
            onClearSelection = {
                onIntent(EditIngestionUiIntent.UpdateReusableSearchQuery(""))
            },
            onShowInlineIngredientDialog = { prefillName ->
                onIntent(EditIngestionUiIntent.ShowInlineIngredientDialog(prefillName))
            },
            onCreateInlineIngredient = { name ->
                onIntent(EditIngestionUiIntent.CreateInlineIngredient(name))
            },
            onUpdateInlineIngredientName = { name ->
                onIntent(EditIngestionUiIntent.UpdateInlineIngredientName(name))
            },
            onCancelInlineIngredientCreation = {
                onIntent(EditIngestionUiIntent.CancelInlineIngredientCreation)
            },
            onCreateNewCompound = null
        ),
        modifier = modifier
    )
}

@Composable
private fun ReusableEntitySearchFieldCore(
    params: ReusableEntitySearchParams,
    callbacks: ReusableEntitySearchCallbacks,
    modifier: Modifier = Modifier
) {
    val hasSelection = params.selectedEntityId != null

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasSelection && !params.allowEditingSelection) {
            OutlinedTextField(
                value = params.searchQuery,
                onValueChange = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(params.searchFieldTag),
                label = { Text("What did you ingest?") },
                readOnly = true,
                trailingIcon = {
                    TextButton(
                        onClick = { callbacks.onClearSelection() }
                    ) {
                        Text("Clear")
                    }
                },
                singleLine = true
            )
        } else {
            OutlinedTextField(
                value = params.searchQuery,
                onValueChange = { query -> callbacks.onSearchQueryChange(query) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(params.searchFieldTag),
                label = { Text("What did you ingest?") },
                singleLine = true
            )

            val showCreateChip = params.searchQuery.isNotBlank()
            val isExactMatch = params.hasNormalizedExactMatch()

            if (!hasSelection && (showCreateChip || params.filteredEntities.isNotEmpty())) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("ingestion-search-dropdown")
                ) {
                    if (showCreateChip) {
                        AssistChip(
                            onClick = {
                                if (!isExactMatch) {
                                    callbacks.onShowInlineIngredientDialog(params.searchQuery)
                                }
                            },
                            enabled = !isExactMatch,
                            label = {
                                Text(
                                    if (isExactMatch) "'${params.searchQuery}' already saved"
                                    else "New: '${params.searchQuery}'"
                                )
                            },
                            modifier = Modifier.testTag("ingestion-create-inline-ingredient")
                        )
                    }

                    params.filteredEntities.forEach { option ->
                        AssistChip(
                            onClick = {
                                callbacks.onSelectEntity(
                                    IngestionReusableSelectionResult(
                                        id = option.id,
                                        name = option.name,
                                        kind = option.kind
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
            } else if (!hasSelection && params.searchQuery.isBlank()) {
                Text(
                    text = "Recent saved items appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("ingestion-reusable-empty")
                )
            }

            if (params.showNewCompoundChip && callbacks.onCreateNewCompound != null) {
                AssistChip(
                    onClick = { callbacks.onCreateNewCompound.invoke() },
                    label = { Text("New compound") },
                    modifier = Modifier.testTag("ingestion-create-compound")
                )
            }
        }
    }

    if (params.showInlineIngredientDialog) {
        InlineIngredientCreationDialog(
            initialName = params.inlineIngredientName,
            isSaving = params.isCreatingInlineIngredient,
            errorMessage = params.inlineIngredientError,
            onSave = { name -> callbacks.onCreateInlineIngredient(name) },
            onCancel = { callbacks.onCancelInlineIngredientCreation() }
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
private fun ReusableEntitySearchParams.hasNormalizedExactMatch(): Boolean {
    val normalizedQuery = searchQuery.trim().replace(Regex("\\s+"), " ").lowercase()
    if (normalizedQuery.isBlank()) return false
    return filteredEntities.any {
        it.name.trim().replace(Regex("\\s+"), " ").lowercase() == normalizedQuery
    }
}
