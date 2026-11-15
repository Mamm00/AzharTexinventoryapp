package com.alazhartex.stock.ui.bento

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalConfiguration

/** Explicit span request carried on the modifier — read by [BentoGrid]'s measure policy. */
data class BentoSpanData(val columns: Int, val rows: Int)

private class BentoSpanElement(private val columns: Int, private val rows: Int) : ParentDataModifier {
    override fun androidx.compose.ui.unit.Density.modifyParentData(parentData: Any?): Any =
        BentoSpanData(columns.coerceAtLeast(1), rows.coerceAtLeast(1))

    override fun hashCode(): Int = columns * 31 + rows
    override fun equals(other: Any?): Boolean =
        other is BentoSpanElement && other.columns == columns && other.rows == rows
}

/**
 * Breakpoint-aware span: pass the columns you want on an EXPANDED (12-column) surface;
 * below 840dp the surface collapses to 4 columns and spans divide by 3 (min 1).
 * `rows` scales the same way. Reusable by any future bento surface.
 */
/**
 * Breakpoint-aware span. [columns] is the span on an EXPANDED (12-column) surface.
 * Below 840dp the surface collapses to 4 columns and the compact span defaults to
 * `(columns + 1) / 3` — pass [compactColumns] explicitly when that heuristic is wrong
 * for the content (e.g. wide values that don't fit in a quarter of a compact width).
 * `rows` scales down as well. Reusable by any future bento surface.
 */
@Composable
fun Modifier.bentoSpan(columns: Int, rows: Int = 1, compactColumns: Int? = null): Modifier {
    val expanded = LocalConfiguration.current.screenWidthDp >= 840
    val cols = if (expanded) columns.coerceIn(1, 12) else (compactColumns ?: (columns / 3)).coerceIn(1, 4)
    val rws = if (expanded) rows.coerceIn(1, 4) else (rows + 1) / 2
    return this.then(BentoSpanElement(cols, rws.coerceIn(1, 4)))
}
