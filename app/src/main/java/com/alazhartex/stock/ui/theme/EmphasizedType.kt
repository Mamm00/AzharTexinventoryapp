package com.alazhartex.stock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * v1.8.0 emphasized display styles — the app has no other emphasized type scale, so the
 * v1.8.0 "emphasized" tokens are defined here and used ONLY by EmphasizedStat surfaces
 * (one per screen, enforced by the OneEmphasizedStatPerScreen detekt rule).
 * Tabular figures come from the call site (TextStyle.fontFeatureSettings = "tnum").
 */
val Typography.displayLargeEmphasized: TextStyle
    get() = displayLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)

val Typography.headlineLargeEmphasized: TextStyle
    get() = headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.25).sp)
