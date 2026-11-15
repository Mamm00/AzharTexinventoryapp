package com.alazhartex.stock.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.alazhartex.stock.ui.haptics.Haptics
import com.alazhartex.stock.ui.motion.LocalReduceMotion
import kotlin.math.abs

data class SegmentedOption<T>(val value: T, val label: String, val testTag: String? = null)

/**
 * v1.8.0 segmented group with a morphing indicator: fully round at rest, momentarily
 * squaring toward a rounded rect while it travels, springed with a defaultSpatial-style
 * spec. RTL-aware; ≥48dp touch targets; user-initiated selection → detent haptic (never ambient).
 */
@Composable
fun <T> SegmentedGroup(
    options: List<SegmentedOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    require(options.isNotEmpty())
    val view = LocalView.current
    val reduceMotion = LocalReduceMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val selectedIndex = options.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = if (reduceMotion) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "segment-index",
    )
    // morph: 0 at rest (pill) → 1 mid-travel (rounded rect)
    val travel = abs(animatedIndex - selectedIndex).coerceIn(0f, 1f)
    val corner = lerp(22.dp, 12.dp, travel)
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHighest)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        Layout(
            content = {
                Box(
                    Modifier
                        .layoutId("indicator")
                        .clip(RoundedCornerShape(corner))
                        .background(scheme.primaryContainer),
                )
                options.forEach { option ->
                    val isSelected = option.value == selected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .layoutId("cell")
                            .then(if (option.testTag != null) Modifier.testTag(option.testTag) else Modifier)
                            .semantics {
                                this.selected = isSelected
                                this.role = Role.Tab
                            }
                            .clickable {
                                if (!isSelected) {
                                    Haptics.detent(view)
                                    onSelected(option.value)
                                }
                            }
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            val totalWidth = constraints.maxWidth
            val cellWidth = totalWidth / options.size
            val cellConstraints = constraints.copy(minWidth = cellWidth, maxWidth = cellWidth, minHeight = 0)
            val cells = measurables.filter { it.layoutId == "cell" }.map { it.measure(cellConstraints) }
            val rowHeight = maxOf(cells.maxOfOrNull { it.height } ?: 0, 48.dp.roundToPx())
            val indicator = measurables.first { it.layoutId == "indicator" }
                .measure(constraints.copy(minWidth = cellWidth, maxWidth = cellWidth, minHeight = rowHeight, maxHeight = rowHeight))
            val indexFloat = animatedIndex
            val indicatorX = if (rtl) (totalWidth - ((indexFloat + 1f) * cellWidth).toInt()) else (indexFloat * cellWidth).toInt()
            layout(totalWidth, rowHeight) {
                indicator.place(indicatorX, 0)
                cells.forEachIndexed { i, placeable ->
                    val x = if (rtl) totalWidth - (i + 1) * cellWidth else i * cellWidth
                    placeable.place(x, 0)
                }
            }
        }
    }
}
