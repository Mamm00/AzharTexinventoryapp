package com.alazhartex.stock.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.ui.haptics.Haptics
import com.alazhartex.stock.ui.motion.LocalReduceMotion
import com.alazhartex.stock.ui.reports.SplitPhase

/**
 * v1.8.0 two-segment split button: leading = primary action (label → spinner → success
 * tick morph), trailing = compact toggle (letterhead). The morph is an
 * animateFloatAsState spring on the check icon (defaultSpatialSpec semantics) + Crossfade;
 * snaps under Reduce motion. Transition to Done fires a confirm haptic (user-initiated
 * completion, never ambient).
 */
@Composable
fun SplitButton(
    primaryLabel: String,
    phase: SplitPhase,
    onPrimary: () -> Unit,
    letterheadEnabled: Boolean,
    onLetterheadChange: (Boolean) -> Unit,
    letterheadLabel: String,
    modifier: Modifier = Modifier,
    testTag: String = "letterhead_split",
) {
    val view = LocalView.current
    val reduceMotion = LocalReduceMotion.current
    val spatial = if (reduceMotion) spring<Float>(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    val tickScale by animateFloatAsState(
        targetValue = if (phase == SplitPhase.Done) 1f else 0.4f,
        animationSpec = spatial,
        label = "split-tick",
    )
    LaunchedEffect(phase) {
        if (phase == SplitPhase.Done) Haptics.confirm(view)
    }
    Row(
        modifier = modifier.height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            onClick = onPrimary,
            modifier = Modifier
                .weight(1f)
                .testTag("${testTag}_primary")
                .semantics {
                    contentDescription = primaryLabel
                    role = Role.Button
                },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 18.dp).height(52.dp)) {
                when (phase) {
                    SplitPhase.Idle -> Text(primaryLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
                    SplitPhase.Working -> CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    SplitPhase.Done -> Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.scale(tickScale),
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp, topStart = 8.dp, bottomStart = 8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .testTag("${testTag}_toggle")
                .semantics { contentDescription = letterheadLabel },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp).height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Switch(checked = letterheadEnabled, onCheckedChange = onLetterheadChange)
            }
        }
    }
}
