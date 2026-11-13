package com.alazhartex.stock.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/**
 * Live invalidation token from DbProvider (bumped on every committed write and after
 * DbProvider.refresh()). Screens key their loaders on this so they re-query the CURRENT
 * database instance instead of racing a stale one.
 */
@Composable
fun rememberDbVersion(): Long =
    com.alazhartex.stock.data.local.DbProvider.dataVersion.collectAsStateWithLifecycle().value

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
