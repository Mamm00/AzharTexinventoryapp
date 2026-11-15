package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.ui.LocalArabicIndicDigits
import com.alazhartex.stock.ui.LocalNavigate
import com.alazhartex.stock.ui.components.LoadingBox
import com.alazhartex.stock.ui.components.WavyIndeterminateIndicator
import com.alazhartex.stock.ui.components.FabMenu
import com.alazhartex.stock.ui.components.FabMenuAction
import com.alazhartex.stock.ui.haptics.Haptics
import com.alazhartex.stock.ui.bento.BentoStrings
import com.alazhartex.stock.ui.bento.DashboardBento
import com.alazhartex.stock.ui.dashboard.DashboardViewModel
import com.alazhartex.stock.ui.dashboard.DashboardViewModelFactory
import com.alazhartex.stock.ui.navigation.Routes

private data class QuickAction(val route: String, val labelRes: Int, val icon: ImageVector)

/** Every destination except the dashboard itself — "access to everything" in one tap. */
private val QUICK_ACTIONS = listOf(
    QuickAction(Routes.STOCK, R.string.nav_stock, Icons.Filled.Inventory2),
    QuickAction(Routes.INVENTORY, R.string.nav_inventory, Icons.Filled.ListAlt),
    QuickAction(Routes.MOVEMENTS, R.string.nav_movements, Icons.Filled.SwapHoriz),
    QuickAction(Routes.COLOURS, R.string.nav_colours, Icons.Filled.Palette),
    QuickAction(Routes.CROSS_VIEW, R.string.nav_crossview, Icons.Filled.GridView),
    QuickAction(Routes.SWATCH_BOOK, R.string.nav_swatch_book, Icons.Filled.ContentCut),
    QuickAction(Routes.REPORTS, R.string.nav_reports, Icons.Filled.Assessment),
    QuickAction(Routes.MASTERS, R.string.nav_masters, Icons.Filled.Storage),
    QuickAction(Routes.FABRICS_EDIT, R.string.nav_fabrics_edit, Icons.Filled.Edit),
    QuickAction(Routes.AUDIT, R.string.nav_audit, Icons.Filled.FactCheck),
    QuickAction(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
    QuickAction(Routes.DASHBOARD_SETTINGS, R.string.nav_dashboard_settings, Icons.Filled.Tune),
    QuickAction(Routes.PROFILE, R.string.nav_profile, Icons.Filled.Person),
    QuickAction(Routes.GUIDE, R.string.nav_guide, Icons.Filled.MenuBook),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(app: AlAzharApp) {
    val arabicIndic = LocalArabicIndicDigits.current
    val navigate = LocalNavigate.current
    var refreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()
    val hapticsView = androidx.compose.ui.platform.LocalView.current
    val viewModel: DashboardViewModel = viewModel(
        factory = remember { DashboardViewModelFactory(app.container.readRepository, app.container.overrideStore) },
    )
    val data by viewModel.uiState.collectAsStateWithLifecycle()
    var fabExpanded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        if (data.loading) {
            LoadingBox(stringResource(R.string.loading), Modifier.padding(padding))
            return@Scaffold
        }
        if (data.loadError != null && data.topColours.isEmpty() && data.topSheds.isEmpty() && data.totalRolls == 0L && data.capacityRolls == 0L) {
            // Fatal, nothing cached yet — surface the failure instead of crashing the composition.
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.error_generic), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            indicator = {
                // Render only during the active refresh — a permanently-pinned indicator
                // overlaps the hero card when idle (seen 2026-11-16 on device).
                if (refreshing) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                    ) {
                        WavyIndeterminateIndicator(
                            modifier = Modifier.width(44.dp).height(44.dp),
                            contentDescription = stringResource(R.string.loading),
                        )
                    }
                }
            },
            onRefresh = {
                Haptics.detent(hapticsView)
                refreshing = true
                // Local-only invalidation: re-resolve Room per call; no network involved.
                com.alazhartex.stock.data.local.DbProvider.bumpVersion()
                refreshScope.launch {
                    delay(650)
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- v1.8.0 bento (hero + KPIs + drift plane + top sheds + top colours) ----
            item {
                val bentoStrings = BentoStrings(
                    heroCaption = stringResource(R.string.label_utilisation),
                    totalLabel = stringResource(R.string.label_total_rolls),
                    activeLabel = stringResource(R.string.label_active_rolls),
                    inactiveLabel = stringResource(R.string.label_inactive_rolls),
                    capacityLabel = stringResource(R.string.label_capacity),
                    bucketGreen = stringResource(R.string.bucket_green),
                    bucketAmber = stringResource(R.string.bucket_amber),
                    bucketRed = stringResource(R.string.bucket_red),
                    bucketOver = stringResource(R.string.bucket_over_capacity),
                    bucketEmpty = stringResource(R.string.label_empty_no_rows),
                    driftOk = stringResource(R.string.label_drift_ok),
                    driftDetected = { count -> context.getString(R.string.label_drift_detected_detail, count) },
                    overridesOk = stringResource(R.string.banner_overrides_ok),
                    overridesMismatch = { count -> context.getString(R.string.banner_overrides_mismatch, count) },
                    topShedsCaption = stringResource(R.string.label_top_sheds),
                    topColoursCaption = stringResource(R.string.label_top_colours),
                    emptyLabel = stringResource(R.string.label_empty_no_rows),
                )
                DashboardBento(
                    totalRolls = data.totalRolls,
                    activeRolls = data.activeRolls,
                    inactiveRolls = data.inactiveRolls,
                    capacityRolls = data.capacityRolls,
                    percentDisplay = data.percentDisplay,
                    percentFraction = data.percentFraction,
                    bucket = data.bucket,
                    topSheds = data.topSheds,
                    topColours = data.topColours,
                    driftViolations = data.driftViolations,
                    overridesEnabled = data.overridesEnabled,
                    overrideDiffs = data.overrideFieldDiffs.size,
                    arabicIndic = arabicIndic,
                    strings = bentoStrings,
                    onColourClick = { colour ->
                        app.container.pendingInventoryColourFilter.value = colour.colourCode
                        navigate(Routes.INVENTORY)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---- Quick actions --------------------------------------------------
            item {
                Text(
                    stringResource(R.string.dashboard_quick_actions),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(items = QUICK_ACTIONS.chunked(4), key = { row -> row.joinToString("_") { it.route } }, contentType = { "quick_row" }) { rowActions ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowActions.forEach { action ->
                        QuickTile(action, Modifier.weight(1f)) { navigate(action.route) }
                    }
                    // pad the last row so tile weights stay even
                    repeat(4 - rowActions.size) { Box(Modifier.weight(1f)) }
                }
            }

            item { Box(Modifier.height(8.dp)) }
        }
        }
        val fabActions = listOf(
            FabMenuAction("add_roll", stringResource(R.string.action_add_roll), Icons.Filled.Add, onClick = { navigate(Routes.STOCK) }),
            FabMenuAction("new_movement", stringResource(R.string.fab_new_movement), Icons.Filled.SwapHoriz, onClick = { navigate(Routes.MOVEMENTS) }),
            FabMenuAction("export", stringResource(R.string.fab_export), Icons.Filled.Share, onClick = { navigate(Routes.REPORTS) }),
            FabMenuAction("switch_shed", stringResource(R.string.action_switch_shed), Icons.Filled.Warehouse, onClick = { navigate(Routes.STOCK) }),
        )
        FabMenu(
            actions = fabActions,
            expanded = fabExpanded,
            onExpandedChange = { fabExpanded = it },
            fabContentDescription = stringResource(R.string.fab_menu_open),
            scrimContentDescription = stringResource(R.string.fab_scrim_label),
        )
        }
    }
}




@Composable
private fun QuickTile(action: QuickAction, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val label = stringResource(action.labelRes)
    Card(
        modifier = modifier
            .testTag("dash_tile_${action.route}")
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().height(88.dp).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(action.icon, contentDescription = label, modifier = Modifier.width(26.dp).height(26.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

