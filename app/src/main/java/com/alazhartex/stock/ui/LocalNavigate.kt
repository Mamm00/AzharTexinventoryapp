package com.alazhartex.stock.ui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/** Cross-screen navigation hook provided by AppRoot (screens must not hold the NavController). */
val LocalNavigate: ProvidableCompositionLocal<(String) -> Unit> = compositionLocalOf { { } }
