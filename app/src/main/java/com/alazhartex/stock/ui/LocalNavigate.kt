package com.alazhartex.stock.ui

/** Cross-screen navigation hook provided by AppRoot (screens must not hold the NavController). */
val LocalNavigate = androidx.compose.runtime.compositionLocalOf<(String) -> Unit> { { } }
