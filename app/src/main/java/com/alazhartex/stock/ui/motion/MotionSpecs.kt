package com.alazhartex.stock.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

/**
 * Single source for animation specs across the v1.8.0 add-ons. Everything that would
 * normally be a spatial spring (size, offset, placement, shared bounds) or an effects
 * spec (color, alpha) must flow through here so Reduce motion can collapse it.
 * No hardcoded tween()/animate*AsState(durationMillis) in nav/motion call sites.
 */
object MotionSpecs {

    /** true when Settings → Display → Reduce motion is on. */
    @Composable
    fun reduceMotion(): Boolean = LocalReduceMotion.current

    /** Spatial spring (placement, size, offset) — snaps when reduce motion is on. */
    @Composable
    fun spatialOrFallback(): AnimationSpec<Float> =
        if (LocalReduceMotion.current) {
            snap()
        } else {
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        }

    /** Effects spec (alpha, color) — ~instant when reduce motion is on. */
    @Composable
    fun effectsOrFallback(): AnimationSpec<Float> =
        if (LocalReduceMotion.current) {
            snap()
        } else {
            tween(durationMillis = 120, easing = LinearOutSlowInEasing)
        }
}
