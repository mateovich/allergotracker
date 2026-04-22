package io.github.mateovich.allergotracker.features.compounds.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import io.github.mateovich.allergotracker.core.designsystem.AppScreenScaffold
import io.github.mateovich.allergotracker.core.designsystem.LocalOnlyNotice
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
            LocalOnlyNotice()
            Text(text = state.helperMessage, style = MaterialTheme.typography.bodyMedium)

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
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compound-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save compound")
            }

            CompoundComposer(
                prefix = "compound",
                componentSearchQuery = state.componentSearchQuery,
                filteredComponents = state.filteredComponents,
                components = state.components,
                selectedPreset = state.selectedPreset,
                totalPercentLabel = state.totalPercentLabel,
                componentsError = state.componentsError,
                totalError = state.totalError,
                onSearchQueryChange = { viewModel.onIntent(CreateCompoundUiIntent.UpdateComponentSearchQuery(it)) },
                onAddIngredient = { viewModel.onIntent(CreateCompoundUiIntent.AddIngredientComponent(it)) },
                onAddCompound = { viewModel.onIntent(CreateCompoundUiIntent.AddCompoundComponent(it)) },
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
    }
}
