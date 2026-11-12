package com.alazhartex.stock.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Fabric cut styled like the physical sample cards (v1.3 swatch book): a landscape swatch with
 * pinked (zigzag) scissors edges, filling [argb]. The silhouette itself is the zigzag, so the
 * cell background shows through the notches — exactly like the printed cards.
 *
 * The canvas bounds INCLUDE the teeth: the solid body sits inset by the tooth amplitude and the
 * tips reach the canvas edge. Callers render the colour name underneath (like the printed card).
 */
@Composable
fun PinkedSwatch(
    argb: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(10f / 7f)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val amp = size.height * 0.055f
        val tooth = size.width / 13f
        val path = pinkedSilhouette(size.width, size.height, amp, tooth)
        drawPath(path = path, color = Color(argb))
        drawPath(path = path, color = Color.Black.copy(alpha = 0.20f), style = Stroke(width = 1.dp.toPx()))
    }
}

/** Zigzag-edged rounded rectangle: teeth point outward, all points inside [0,w] x [0,h]. */
private fun pinkedSilhouette(width: Float, height: Float, amp: Float, tooth: Float): Path {
    val bodyLeft = amp
    val bodyRight = width - amp
    val bodyTop = amp
    val bodyBottom = height - amp
    val p = Path()

    p.moveTo(bodyLeft, bodyTop)

    // Top edge, left -> right. Tips touch y = 0.
    var x = bodyLeft
    while (x < bodyRight - 0.5f) {
        val tipX = kotlin.math.min(x + tooth / 2f, bodyRight)
        val endX = kotlin.math.min(x + tooth, bodyRight)
        p.lineTo(tipX, 0f)
        p.lineTo(endX, bodyTop)
        x = endX
    }

    // Right edge, top -> bottom. Tips touch x = width.
    var y = bodyTop
    while (y < bodyBottom - 0.5f) {
        val tipY = kotlin.math.min(y + tooth / 2f, bodyBottom)
        val endY = kotlin.math.min(y + tooth, bodyBottom)
        p.lineTo(width, tipY)
        p.lineTo(bodyRight, endY)
        y = endY
    }

    // Bottom edge, right -> left. Tips touch y = height.
    x = bodyRight
    while (x > bodyLeft + 0.5f) {
        val tipX = kotlin.math.max(x - tooth / 2f, bodyLeft)
        val endX = kotlin.math.max(x - tooth, bodyLeft)
        p.lineTo(tipX, height)
        p.lineTo(endX, bodyBottom)
        x = endX
    }

    // Left edge, bottom -> top. Tips touch x = 0.
    y = bodyBottom
    while (y > bodyTop + 0.5f) {
        val tipY = kotlin.math.max(y - tooth / 2f, bodyTop)
        val endY = kotlin.math.max(y - tooth, bodyTop)
        p.lineTo(0f, tipY)
        p.lineTo(bodyLeft, endY)
        y = endY
    }

    p.close()
    return p
}
