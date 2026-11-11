package com.alazhartex.stock.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.domain.model.DuplicateValueException
import com.alazhartex.stock.domain.model.RbacDeniedException
import com.alazhartex.stock.domain.model.ReferencedByException
import com.alazhartex.stock.domain.model.SessionClockAnomalyException
import com.alazhartex.stock.domain.model.ShortfallException

const val ACTOR_ADMIN = "admin"

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Loaded<T>(val value: T) : LoadState<T>
    data class Failed(val cause: Throwable) : LoadState<Nothing>
}

/** Simple refresh counter for reloads after mutations. */
class RefreshScope {
    val tick = mutableIntStateOf(0)
    fun bump() { tick.intValue += 1 }
}

@Composable
fun rememberRefresh(): RefreshScope = remember { RefreshScope() }

/** Maps domain exceptions to snackbar-safe user messages (RBAC-first). Plain function — callers pass a Context (it is called from coroutines, not composition). */
fun snackbarFor(context: android.content.Context, error: Throwable): String {
    return when (error) {
        is ShortfallException -> context.resources.getQuantityString(R.plurals.toast_shortfall, error.shortfall.toInt(), error.shortfall)
        is RbacDeniedException -> context.getString(R.string.toast_rbac_denied, error.reasonKey)
        is SessionClockAnomalyException -> context.getString(R.string.toast_session_anomaly)
        is DuplicateValueException ->
            if (error.reasonKey == "colour_duplicate_error") context.getString(R.string.colour_duplicate_error) else context.getString(R.string.fabric_duplicate_error)
        is ReferencedByException -> context.getString(
            when (error.reasonKey) {
                "referenced_by_movements" -> R.string.referenced_by_movements
                "referenced_by_rolls" -> R.string.referenced_by_rolls
                else -> R.string.referenced_by_mappings
            },
        )
        else -> context.getString(R.string.error_generic)
    }
}

suspend fun AlAzharApp.snack(host: SnackbarHostState, message: String) = host.showSnackbar(message)

/** Cross-screen navigation hook provided by AppRoot (screens must not hold the NavController). */
val LocalNavigate = androidx.compose.runtime.compositionLocalOf<(String) -> Unit> { { } }
