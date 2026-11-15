package com.alazhartex.stock.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.ui.haptics.Haptics
import com.alazhartex.stock.ui.motion.LocalReduceMotion

/** Pre-formatted legs for a transfer pair — all strings resolved by the caller. */
data class TransferPairSpec(
    val qtyDisplay: String,
    val routeDisplay: String,
    val dateDisplay: String,
    val colourCode: String,
    val legOutLine: String,
    val legInLine: String,
    val note: String?,
    val contentDescription: String,
)

/**
 * v1.8.0 connected transfer card: both legs of a warehouse→warehouse transfer render as
 * ONE card joined by a vertical spring-animated connector — collapsed shows the summary,
 * expanded shows both legs. Expand/collapse is a single unit (AnimatedVisibility +
 * vertical expand spring). Snaps under Reduce motion. Tap = user-initiated toggle with
 * detent haptic.
 */
@Composable
fun MovementPairCard(
    spec: TransferPairSpec,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val view = LocalView.current
    val reduceMotion = LocalReduceMotion.current
    val expandSpec: FiniteAnimationSpec<androidx.compose.ui.unit.IntSize> = if (reduceMotion) tween(0) else spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    val effectsSpec = if (reduceMotion) tween<Float>(0) else tween(120)
    Card(
        colors = CardDefaults.cardColors(),
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        Column(
            modifier = Modifier
                .clickable {
                    Haptics.detent(view)
                    onToggle()
                }
                .semantics {
                    contentDescription = spec.contentDescription
                    role = Role.Button
                }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(spec.routeDisplay, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
                Surface(shape = RoundedCornerShape(percent = 50), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        spec.qtyDisplay,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text("${spec.colourCode} · ${spec.dateDisplay}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!spec.note.isNullOrBlank()) {
                Text(spec.note, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = expandSpec) + fadeIn(effectsSpec),
                exit = shrinkVertically(animationSpec = expandSpec) + fadeOut(effectsSpec),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.padding(top = 4.dp)) {
                    LegRow(spec.legOutLine, isOutbound = true)
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .width(2.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                    )
                    LegRow(spec.legInLine, isOutbound = false)
                }
            }
        }
    }
}

@Composable
private fun LegRow(line: String, isOutbound: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (isOutbound) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
        )
        Text(line, style = MaterialTheme.typography.bodySmall)
    }
}
