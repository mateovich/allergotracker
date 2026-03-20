package com.example.app.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreenScaffold(
    title: String,
    topBarActions: @Composable (() -> Unit)? = null,
    floatingActionButton: @Composable (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                actions = { topBarActions?.invoke() }
            )
        },
        floatingActionButton = {
            floatingActionButton?.invoke()
        }
    ) { paddingValues ->
        content(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

@Composable
fun PrimaryAddFab(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .testTag("records-add-fab")
            .semantics {
                this.contentDescription = contentDescription
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = "+")
        }
    }
}

@Composable
fun EmptyStateMessage(
    title: String,
    message: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.testTag("records-empty-state")
        ) {
            Text(text = title)
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = message, textAlign = TextAlign.Center)
            if (!actionLabel.isNullOrBlank() && onActionClick != null) {
                Spacer(modifier = Modifier.size(12.dp))
                TextButton(
                    onClick = onActionClick,
                    modifier = Modifier
                        .testTag("records-empty-state-action")
                        .semantics { contentDescription = actionLabel }
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}
