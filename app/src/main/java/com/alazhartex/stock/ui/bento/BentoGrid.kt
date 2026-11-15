package com.alazhartex.stock.ui.bento

import androidx.compose.foundation.layout.Layout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** Default row unit used only when a child spans multiple rows (rows > 1). */
private val RowUnit = 148.dp
private val GridGap = 8.dp

/**
 * v1.8.0 bento grid — 12 columns expanded (≥840dp), 4 below. Children declare spans via
 * [bentoSpan]; unspanned children take 3 columns expanded / 1 column compact. Rows are placed greedily left-to-right in
 * layout direction; a row's height is the tallest measured child; when a child can't fit the
 * remaining columns it wraps to the next row. RTL is handled by the caller's Layout direction
 * through placeRelative.
 */
@Composable
fun BentoGrid(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val columns = if (LocalConfiguration.current.screenWidthDp >= 840) 12 else 4
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val gap = GridGap.roundToPx()
        val rowUnitPx = RowUnit.roundToPx()
        val width = constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: 1080
        val unit = (width - gap * (columns - 1)) / columns

        data class Placed(val placeable: Placeable, val x: Int, val y: Int)

        val placed = ArrayList<Placed>(measurables.size)
        var cursorX = 0
        var cursorY = 0
        var usedCols = 0
        var rowHeight = 0

        for (measurable in measurables) {
            val span = (measurable.parentData as? BentoSpanData) ?: BentoSpanData(3, 1)
            val cellColumns = span.columns.coerceIn(1, columns)
            if (usedCols + cellColumns > columns) {
                cursorY += rowHeight + gap
                cursorX = 0
                usedCols = 0
                rowHeight = 0
            }
            val cellWidth = unit * cellColumns + gap * (cellColumns - 1)
            val targetHeight = if (span.rows > 1) rowUnitPx * span.rows + gap * (span.rows - 1) else Constraints.Infinity
            val placeable = measurable.measure(
                Constraints(minWidth = cellWidth, maxWidth = cellWidth, minHeight = 0, maxHeight = targetHeight),
            )
            placed += Placed(placeable, cursorX, cursorY)
            cursorX += cellWidth + gap
            usedCols += cellColumns
            rowHeight = max(rowHeight, placeable.height)
        }
        val totalHeight = if (placed.isEmpty()) 0 else cursorY + rowHeight
        layout(width, totalHeight) {
            placed.forEach { it.placeable.placeRelative(it.x, it.y) }
        }
    }
}
