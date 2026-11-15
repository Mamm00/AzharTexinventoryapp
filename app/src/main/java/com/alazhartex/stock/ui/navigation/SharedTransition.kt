@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.alazhartex.stock.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.SharedTransitionScope

/** Provided by the NavHost wrapper in AlAzharRoot; null when shared transitions are unavailable. */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Per-destination scope; null when the destination is outside a SharedTransitionLayout. */
val LocalAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
