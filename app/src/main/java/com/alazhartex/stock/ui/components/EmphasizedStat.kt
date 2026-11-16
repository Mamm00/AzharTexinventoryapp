package com.alazhartex.stock.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.ui.theme.displayLargeEmphasized
import com.alazhartex.stock.ui.theme.headlineLargeEmphasized

enum class EmphasizedStyle { Display, Headline }

/**
 * One-per-screen emphasized statistic (v1.8.0): big numeral in displayLargeEmphasized /
 * headlineLargeEmphasized with tabular figures so columns never jitter, plus an optional
 * trailing delta chip. Callers supply a merged content description ending in the primary
 * value (accessibility contract), or rely on the default "<caption>: <value>" one.
 */
@Composable
fun EmphasizedStat(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    deltaPositive: Boolean? = null,
    style: EmphasizedStyle = EmphasizedStyle.Display,
    contentDescription: String? = null,
    testTag: String? = null,
) {
    val base = when (style) {
        EmphasizedStyle.Display -> MaterialTheme.typography.displayLargeEmphasized
        EmphasizedStyle.Headline -> MaterialTheme.typography.headlineLargeEmphasized
    }.copy(fontFeatureSettings = "tnum")
    val mergedDescription = contentDescription ?: "$caption: $value"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = mergedDescription }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, style = base, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (delta != null) {
                val tint = when (deltaPositive) {
                    true -> MaterialTheme.colorScheme.primaryContainer
                    false -> MaterialTheme.colorScheme.errorContainer
                    null -> MaterialTheme.colorScheme.surfaceVariant
                }
                val onTint = when (deltaPositive) {
                    true -> MaterialTheme.colorScheme.onPrimaryContainer
                    false -> MaterialTheme.colorScheme.onErrorContainer
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Surface(shape = MaterialTheme.shapes.extraSmall, color = tint) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (deltaPositive != null) {
                            Icon(
                                if (deltaPositive) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                contentDescription = null,
                                tint = onTint,
                                modifier = Modifier.padding(end = 2.dp).align(Alignment.CenterVertically),
                            )
                        }
                        Text(delta, style = MaterialTheme.typography.labelMedium, color = onTint, maxLines = 1)
                    }
                }
            }
        }
        Text(caption, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}
