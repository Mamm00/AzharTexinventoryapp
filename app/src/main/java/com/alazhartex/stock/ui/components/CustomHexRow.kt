package com.alazhartex.stock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.R
import com.alazhartex.stock.domain.palette.CustomSwatch

/**
 * "Pick a colour" row for the colour form dialogs (v1.3): shows the current custom swatch (or
 * "auto" when the deterministic palette resolver is in effect), opens [ColorWheelDialog] on demand,
 * and offers one-tap clear back to auto.
 */
@Composable
fun CustomHexRow(
    customHex: String?,
    onCustomHexChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showWheel by remember { mutableStateOf(false) }
    val parsed = CustomSwatch.parse(customHex)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 40dp live preview — the filled swatch, or a hatched "auto" placeholder.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(parsed?.let { androidx.compose.ui.graphics.Color(it.argb) } ?: MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (parsed == null) {
                Text(
                    stringResource(R.string.color_custom_auto_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.color_hex_label), style = MaterialTheme.typography.labelMedium)
            Text(
                if (parsed != null && customHex != null) CustomSwatch.normalizeHex(customHex).orEmpty()
                else stringResource(R.string.color_custom_auto),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { showWheel = true }) { Text(stringResource(R.string.color_pick)) }
        if (customHex != null) {
            IconButton(onClick = { onCustomHexChange(null) }) {
                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear))
            }
        }
    }

    if (showWheel) {
        ColorWheelDialog(
            initialHex = customHex,
            onDismiss = { showWheel = false },
            onConfirm = { hex ->
                onCustomHexChange(hex)
                showWheel = false
            },
        )
    }
}
