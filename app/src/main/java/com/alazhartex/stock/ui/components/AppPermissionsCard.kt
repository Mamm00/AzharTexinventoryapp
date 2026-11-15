package com.alazhartex.stock.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * v1.8.0 extended app-permissions & motion card (Guide). The Reduce-motion row mirrors the
 * same DataStore toggle as Settings → Display and deep-links into it — no second source of
 * truth, no parallel state.
 */
@Composable
fun AppPermissionsCard(
    reduceMotion: Boolean,
    reduceMotionLabel: String,
    reduceMotionStatusText: String,
    bodyText: String,
    openSettingsLabel: String,
    titleText: String,
    onOpenDisplaySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth().testTag("app_permissions_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titleText, style = MaterialTheme.typography.titleMedium)
            Text(bodyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "$reduceMotionLabel: $reduceMotionStatusText" },
            ) {
                Icon(Icons.Filled.Animation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(reduceMotionLabel, style = MaterialTheme.typography.bodyMedium)
                    Text(reduceMotionStatusText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(
                    onClick = onOpenDisplaySettings,
                    modifier = Modifier.testTag("app_permissions_open_settings"),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(openSettingsLabel)
                }
            }
        }
    }
}
