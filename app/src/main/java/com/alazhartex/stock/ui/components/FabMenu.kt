package com.alazhartex.stock.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.ui.haptics.Haptics
import com.alazhartex.stock.ui.motion.LocalReduceMotion

/** One pill action — [onClick] must route to the same audited entry point as the old tile. */
data class FabMenuAction(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit,
)

/**
 * v1.8.0 single-FAB spring-opening menu. Place as a bottom-end overlay inside the host's
 * Scaffold content Box (full-size modifier). Closes on scrim tap or back. Reduce-motion
 * collapses the springs. Opening/closing is user-initiated → detent haptics.
 */
@Composable
fun FabMenu(
    actions: List<FabMenuAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    fabContentDescription: String,
    scrimContentDescription: String,
) {
    val view = LocalView.current
    val reduceMotion = LocalReduceMotion.current
    val scrimInteraction = remember { MutableInteractionSource() }
    val spatial = if (reduceMotion) spring<Float>(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = spatial,
        label = "fab-rotation",
    )
    if (expanded) {
        BackHandler { onExpandedChange(false) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .semantics {
                    contentDescription = scrimContentDescription
                    role = Role.Button
                }
                .clickable(interactionSource = scrimInteraction, indication = null) { onExpandedChange(false) }
                .testTag("fab_scrim"),
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().then(modifier),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(androidx.compose.animation.core.tween(120)),
            exit = fadeOut(androidx.compose.animation.core.tween(80)),
        ) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { action ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = {
                            Haptics.detent(view)
                            onExpandedChange(false)
                            action.onClick()
                        },
                        modifier = Modifier.testTag("fab_action_${action.id}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(action.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                Haptics.detent(view)
                onExpandedChange(!expanded)
            },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.testTag("fab_toggle"),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = fabContentDescription,
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
            )
        }
    }
}
