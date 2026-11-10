package io.github.mateovich.allergotracker.features.ingredients.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mateovich.allergotracker.core.designsystem.AppScreenScaffold
import io.github.mateovich.allergotracker.navigation.AppRoutes

@Composable
fun ProvisionalIngredientsScreen(
    onNavigateToRoute: (String) -> Unit,
    viewModelFactory: ViewModelProvider.Factory,
    viewModel: ProvisionalIngredientsViewModel = viewModel(factory = viewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    AppScreenScaffold(title = state.title) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("provisional-ingredients-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = state.helperMessage, style = MaterialTheme.typography.bodyMedium)
            if (!state.isLoading && state.items.isEmpty()) {
                Text(
                    text = state.emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("provisional-ingredients-empty")
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("provisional-ingredients-list")
            ) {
                items(state.items, key = { it.id }) { item ->
                    Surface(
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToRoute(AppRoutes.editIngredient(item.id)) }
                            .testTag("provisional-ingredient-${item.id}")
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = item.name, style = MaterialTheme.typography.titleMedium)
                            item.notes?.takeIf { it.isNotBlank() }?.let {
                                Text(text = it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
