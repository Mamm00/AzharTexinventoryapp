package com.alazhartex.stock.ui.motion

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * App-wide "reduce motion" switch (v1.8.0), persisted via Settings → Display and provided
 * from AlAzharRoot. When true: spatial springs collapse to snap(), effects specs collapse
 * to a fast fade, SharedTransitionLayout morphs are skipped by call sites, and indeterminate
 * glyphs stop looping. Ambient drift status NEVER plays haptics regardless of this flag.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }
