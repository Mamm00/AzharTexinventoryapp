package com.alazhartex.stock.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.domain.gauge.GaugeBucket
import com.alazhartex.stock.ui.theme.GaugeAmber
import com.alazhartex.stock.ui.theme.GaugeGreen
import com.alazhartex.stock.ui.theme.GaugeRed

fun gaugeColor(bucket: GaugeBucket?): Color = when (bucket) {
    GaugeBucket.GREEN -> GaugeGreen
    GaugeBucket.AMBER -> GaugeAmber
    GaugeBucket.RED -> GaugeRed
    null -> MaterialTheme.colorScheme.outline
}

/**
 * Accessible utilisation gauge (T16): never colour-only — always bucket label + value text
 * + full contentDescription ("<shed> utilisation X% — bucket").
 */
@Composable
fun UtilisationGauge(
    label: String,
    percentDisplay: String?,
    bucket: GaugeBucket?,
    bucketLabel: String,
    percentFraction: Float,
    modifier: Modifier = Modifier,
    contentDescriptionPrefix: String,
) {
    val animated by animateFloatAsState(targetValue = percentFraction.coerceIn(0f, 1.5f) / 1.5f, label = "gauge")
    val color = gaugeColor(bucket)
    val desc = "$contentDescriptionPrefix ${percentDisplay ?: "—"} $bucketLabel"
    Column(
        modifier = modifier
            .semantics { contentDescription = desc }
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = percentDisplay?.let { "$it%" } ?: "—",
                style = MaterialTheme.typography.titleLarge,
                color = if (bucket == null) MaterialTheme.colorScheme.onSurface else color,
            )
        }
        LinearProgressIndicator(
            progress = { animated.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(MaterialTheme.shapes.medium),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(0.dp))
            Text(text = bucketLabel, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
fun LabeledRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LoadingBox(message: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.width(12.dp))
        Text(message)
    }
}

fun bucketLabelFor(bucket: GaugeBucket?, green: String, amber: String, red: String, over: String, empty: String): String = when (bucket) {
    GaugeBucket.GREEN -> green
    GaugeBucket.AMBER -> amber
    GaugeBucket.RED -> "$red · $over"
    null -> empty
}
