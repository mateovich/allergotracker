package io.github.mateovich.allergotracker.core.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

object LocalOnlyNoticeDefaults {
    const val testTag = "local-only-notice"
    const val message = "Offline-ready. Export to share your data."
}

@Composable
fun LocalOnlyNotice(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(LocalOnlyNoticeDefaults.testTag),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = LocalOnlyNoticeDefaults.message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}
