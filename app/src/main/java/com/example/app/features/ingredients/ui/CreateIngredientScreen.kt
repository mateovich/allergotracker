package com.example.app.features.ingredients.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.example.app.core.designsystem.AppScreenScaffold
import com.example.app.core.designsystem.LocalOnlyNotice
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CreateIngredientScreen(
    viewModelFactory: ViewModelProvider.Factory,
    onNavigateBackWithSelection: (com.example.app.features.ingestion.ui.IngestionReusableSelectionResult) -> Unit,
    viewModel: CreateIngredientViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is CreateIngredientUiEffect.NavigateBackWithSelection -> onNavigateBackWithSelection(effect.result)
            }
        }
    }

    AppScreenScaffold(title = state.title) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("create-ingredient-form-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LocalOnlyNotice()

            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.onIntent(CreateIngredientUiIntent.UpdateName(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ingredient-name"),
                label = { Text("Ingredient name") },
                supportingText = { state.nameError?.let { Text(it) } },
                isError = state.nameError != null,
                singleLine = true
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.onIntent(CreateIngredientUiIntent.UpdateNotes(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ingredient-notes"),
                label = { Text("Notes (optional)") },
                minLines = 3
            )

            if (state.formMessage.isNotBlank()) {
                Text(
                    text = state.formMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("ingredient-form-message")
                )
            }

            Button(
                onClick = { viewModel.onIntent(CreateIngredientUiIntent.Save) },
                enabled = state.canSave && !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ingredient-save")
            ) {
                Text(if (state.isSaving) "Saving..." else "Save ingredient")
            }
        }
    }
}
