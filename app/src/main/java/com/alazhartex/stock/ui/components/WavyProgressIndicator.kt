package com.alazhartex.stock.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * v1.8.0 wavy circular indicator. Two entry points:
 *  - WavyProgressIndicator(progress): determinate — sine-wave edge along the arc, for the
 *    utilisation hero and the profile session countdown.
 *  - WavyProgressIndicator.Indeterminate(morphing = true): circle → rounded-square → circle
 *    shape morph + rotating wavy head, for pull-to-refresh.
 * Respects Reduce motion (LocalReduceMotion): the phase freezes and the morph parks mid-state.
 * Track is drawn for a11y/contrast; never the only signal.
 */
@Composable
fun WavyProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 6.dp,
    waveAmplitude: Dp = 2.5.dp,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    contentDescriptionPrefix: String,
    testTag: String? = null,
) {
    val themeTrack = MaterialTheme.colorScheme.surfaceVariant
    val clamped = progress.coerceIn(0f, 1f)
    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = "$contentDescriptionPrefix ${(clamped * 100).toInt()}%"
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
            }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        val stroke = strokeWidth.toPx()
        val amp = waveAmplitude.toPx()
        val radius = (size.minDimension - stroke - amp * 2) / 2f
        if (radius <= 0f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(themeTrack, radius = radius, center = center, style = Stroke(stroke))
        if (clamped <= 0f) return@Canvas
        val sweep = 360f * clamped
        drawWavyArc(center = center, radius = radius, sweep = sweep, amp = amp, stroke = stroke, color = color, startAngle = -90f, phase = 0f)
    }
}

/** Indeterminate + optional circle⇄rounded-square morph (pull-to-refresh). */
@Composable
fun WavyIndeterminateIndicator(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
    morphShape: Boolean = true,
    contentDescription: String,
    testTag: String? = null,
) {
    val reduce = com.alazhartex.stock.ui.motion.LocalReduceMotion.current
    val theme = MaterialTheme.colorScheme
    val rotation by rememberInfiniteTransition(label = "wavy-rotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "wavy-rotation-angle",
    )
    val morph by rememberInfiniteTransition(label = "wavy-morph").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "wavy-morph-value",
    )
    val rotationNow = if (reduce) 0f else rotation
    val morphNow = if (reduce) 0.5f else morph
    val indeterminateDescription = contentDescription
    Canvas(
        modifier = modifier.semantics {
            this.contentDescription = indeterminateDescription
            progressBarRangeInfo = ProgressBarRangeInfo(0f, 0f..1f)
        }.then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        val stroke = strokeWidth.toPx()
        val sizeMin = size.minDimension
        val corner = sizeMin / 2f + (sizeMin * 0.22f - sizeMin / 2f) * morphNow
        if (morphShape) {
            drawRoundRect(
                color = theme.surfaceVariant,
                topLeft = Offset((size.width - sizeMin) / 2f + stroke / 2f, (size.height - sizeMin) / 2f + stroke / 2f),
                size = Size(sizeMin - stroke, sizeMin - stroke),
                cornerRadius = CornerRadius(corner - stroke / 2f),
                style = Stroke(stroke),
            )
        } else {
            drawCircle(theme.surfaceVariant, radius = sizeMin / 2f - stroke / 2f, center = Offset(size.width / 2f, size.height / 2f), style = Stroke(stroke))
        }
        val arcRadius = (sizeMin - stroke) / 2f - sizeMin * 0.10f
        rotate(degrees = rotationNow, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawWavyArc(
                center = Offset(size.width / 2f, size.height / 2f),
                radius = arcRadius,
                sweep = 90f,
                amp = stroke * 0.45f,
                stroke = stroke,
                color = theme.primary,
                startAngle = -90f,
                phase = rotationNow / 24f,
            )
        }
    }
}

/** Draws a sine-modulated arc from [startAngle] for [sweep] degrees. */
private fun DrawScope.drawWavyArc(
    center: Offset,
    radius: Float,
    sweep: Float,
    amp: Float,
    stroke: Float,
    color: androidx.compose.ui.graphics.Color,
    startAngle: Float,
    phase: Float,
) {
    if (sweep <= 0f) return
    val path = Path()
    val step = 3f
    val wavelength = 30f
    var angle = 0f
    var first = true
    while (angle <= sweep + 0.001f) {
        val t = angle / sweep
        val modulation = amp * kotlin.math.sin((angle / wavelength) * 2f * Math.PI.toFloat() + phase) * kotlin.math.sin(t * Math.PI.toFloat())
        val r = radius + modulation
        val theta = Math.toRadians((startAngle + angle).toDouble())
        val x = center.x + r * cos(theta).toFloat()
        val y = center.y + r * sin(theta).toFloat()
        if (first) {
            path.moveTo(x, y)
            first = false
        } else {
            path.lineTo(x, y)
        }
        angle += step
    }
    drawPath(path, color = color, style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}
