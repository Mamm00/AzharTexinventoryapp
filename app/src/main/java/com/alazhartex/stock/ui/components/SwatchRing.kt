package com.alazhartex.stock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.data.repo.TopColourDto
import com.alazhartex.stock.ui.haptics.Haptics
import com.alazhartex.stock.ui.integerString
import com.alazhartex.stock.ui.navigation.LocalAnimatedVisibilityScope
import com.alazhartex.stock.ui.navigation.LocalSharedTransitionScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * v1.8.0 top-colours orbit. Each swatch carries shared-bounds key "swatch-<code>" when a
 * SharedTransitionScope is in composition — tapping navigates (caller-defined) and the
 * matching colour chip on the Inventory screen morphs out of this orbit. Tap is
 * user-initiated → detent haptic. Shows at most [maxOrbit] colours.
 */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun SwatchRing(
    colours: List<TopColourDto>,
    modifier: Modifier = Modifier,
    maxOrbit: Int = 10,
    onColourClick: ((TopColourDto) -> Unit)? = null,
    arabicDigits: Boolean = false,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val shown = colours.take(maxOrbit)
    val orbitRadius = 74.dp
    val swatch = 44.dp
    val container = (orbitRadius * 2) + swatch
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    Box(
        modifier = modifier
            .size(container)
            .semantics { contentDescription = "Top colours orbit, ${shown.size} colours" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Palette,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
        shown.forEachIndexed { index, colour ->
            val angle = (index.toDouble() / shown.size.toDouble()) * 2.0 * PI - (PI / 2.0)
            val dxPx = with(density) { (cos(angle) * orbitRadius.value).toFloat().dp.toPx() }
            val dyPx = with(density) { (sin(angle) * orbitRadius.value).toFloat().dp.toPx() }
            val sw = rememberAnySwatch(colour.family, colour.grade, colour.colourCode, colour.customHex)
            val desc = paletteContentDescription(colour.colourCode, colour.colourNameEn, colour.colourNameAr, colour.grade)
            var swatchModifier: Modifier = Modifier
                .size(swatch)
                .graphicsLayer {
                    translationX = dxPx
                    translationY = dyPx
                }
            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    swatchModifier = swatchModifier.sharedBounds(
                        sharedContentState = rememberSharedContentState("swatch-${colour.colourCode}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }
            Box(
                modifier = swatchModifier
                    .align(Alignment.Center)
                    .then(if (onColourClick != null) Modifier.testTag("swatch_orbit_${colour.colourCode}") else Modifier)
                    .semantics { contentDescription = desc }
                    .then(
                        if (onColourClick != null) {
                            Modifier.clickable {
                                Haptics.detent(view)
                                onColourClick(colour)
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(swatch)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(sw.argb)),
                )
                Text(
                    text = integerString(colour.rolls.coerceAtMost(999L), arabicDigits),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(sw.onColour),
                )
            }
        }
    }
}
