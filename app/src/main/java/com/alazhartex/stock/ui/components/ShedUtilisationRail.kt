package com.alazhartex.stock.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.domain.gauge.GaugeBucket
import com.alazhartex.stock.ui.motion.LocalReduceMotion
import com.alazhartex.stock.ui.theme.GaugeAmber
import com.alazhartex.stock.ui.theme.GaugeGreen
import com.alazhartex.stock.ui.theme.GaugeRed

/**
 * v1.8.0 shed utilisation rail: three explicit zone segments (green <50 · amber 50..=85 ·
 * red >85), shown BEFORE the fill reaches them. Fill scale is 0..=150% (1.5× capacity),
 * matching UtilisationGauge's normalisation. Zone boundaries are hairlines drawn above
 * the fill. defaultSpatialSpec-equivalent spring; snaps under Reduce motion. Never
 * colour-only: callers keep the bucket label adjacent (as in Stock shed cards today).
 */
@Composable
fun ShedUtilisationRail(
    percentFraction: Float,
    bucket: GaugeBucket?,
    modifier: Modifier = Modifier,
    contentDescriptionPrefix: String,
    testTag: String? = null,
) {
    val reduceMotion = LocalReduceMotion.current
    val animated by animateFloatAsState(
        targetValue = (percentFraction / 1.5f).coerceIn(0f, 1f),
        animationSpec = if (reduceMotion) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "shed-rail",
    )
    val fillColor = gaugeColor(bucket)
    val zoneAlpha = 0.22f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(MaterialTheme.shapes.small)
            .semantics { contentDescription = "$contentDescriptionPrefix ${(percentFraction * 100).toInt()}%" }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            Box(Modifier.weight(50f).height(10.dp).background(GaugeGreen.copy(alpha = zoneAlpha)))
            Box(Modifier.weight(35f).height(10.dp).background(GaugeAmber.copy(alpha = zoneAlpha)))
            Box(Modifier.weight(65f).height(10.dp).background(GaugeRed.copy(alpha = zoneAlpha)))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(10.dp)
                .background(fillColor),
        )
        Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            val hairline = Color.Black.copy(alpha = 0.35f)
            val x1 = size.width * (50f / 150f)
            val x2 = size.width * (85f / 150f)
            drawLine(hairline, Offset(x1, 0f), Offset(x1, size.height), strokeWidth = 2f)
            drawLine(hairline, Offset(x2, 0f), Offset(x2, size.height), strokeWidth = 2f)
        }
    }
}
