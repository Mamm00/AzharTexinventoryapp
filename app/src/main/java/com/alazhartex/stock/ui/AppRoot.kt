package com.alazhartex.stock.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.ui.components.LoadingBox
import com.alazhartex.stock.ui.navigation.Routes
import com.alazhartex.stock.ui.screens.AuditScreen
import com.alazhartex.stock.ui.screens.ColoursScreen
import com.alazhartex.stock.ui.screens.CrossViewScreen
import com.alazhartex.stock.ui.screens.DashboardScreen
import com.alazhartex.stock.ui.screens.GuideScreen
import com.alazhartex.stock.ui.screens.InventoryScreen
import com.alazhartex.stock.ui.screens.MastersScreen
import com.alazhartex.stock.ui.screens.MovementsScreen
import com.alazhartex.stock.ui.screens.ProfileScreen
import com.alazhartex.stock.ui.screens.ReportsScreen
import com.alazhartex.stock.ui.screens.SettingsScreen
import com.alazhartex.stock.ui.screens.StockScreen
import kotlinx.coroutines.launch

val LocalSnackbar = androidx.compose.runtime.compositionLocalOf<SnackbarHostState> { error("snackbar host not provided") }

private data class DrawerDestination(val route: String, val labelRes: Int, val icon: ImageVector)

private val DESTINATIONS = listOf(
    DrawerDestination(Routes.GUIDE, R.string.nav_guide, Icons.Filled.MenuBook),
    DrawerDestination(Routes.STOCK, R.string.nav_stock, Icons.Filled.Inventory2),
    DrawerDestination(Routes.INVENTORY, R.string.nav_inventory, Icons.Filled.ListAlt),
    DrawerDestination(Routes.MOVEMENTS, R.string.nav_movements, Icons.Filled.SwapHoriz),
    DrawerDestination(Routes.COLOURS, R.string.nav_colours, Icons.Filled.Palette),
    DrawerDestination(Routes.CROSS_VIEW, R.string.nav_crossview, Icons.Filled.GridView),
    DrawerDestination(Routes.DASHBOARD, R.string.nav_dashboard, Icons.Filled.Dashboard),
    DrawerDestination(Routes.REPORTS, R.string.nav_reports, Icons.Filled.Assessment),
    DrawerDestination(Routes.MASTERS, R.string.nav_masters, Icons.Filled.Storage),
    DrawerDestination(Routes.AUDIT, R.string.nav_audit, Icons.Filled.FactCheck),
    DrawerDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
    DrawerDestination(Routes.PROFILE, R.string.nav_profile, Icons.Filled.Person),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlAzharRoot(app: AlAzharApp) {
    val snackbarHostState = remember { SnackbarHostState() }
    val arabicIndic by app.container.digitsFlow.collectAsStateWithLifecycle()
    val bootstrapped by app.container.bootstrapped.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    androidx.compose.runtime.CompositionLocalProvider(
        LocalArabicIndicDigits provides arabicIndic,
        LocalSnackbar provides snackbarHostState,
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Brand mark sits on a white chip so it stays legible on light and dark drawers.
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.alazhartex_logo),
                                contentDescription = null, // decorative — app name text sits beside it
                                modifier = Modifier.height(40.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                text = stringResource(R.string.brand_tagline),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items = DESTINATIONS, key = { it.route }, contentType = { "nav" }) { dest ->
                            NavigationDrawerItem(
                                label = { Text(stringResource(dest.labelRes)) },
                                icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(dest.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            )
                        }
                    }
                }
            },
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.app_name))
                            }
                        },
                    )
                },
            ) { padding ->
                if (!bootstrapped) {
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        LoadingBox(stringResource(R.string.loading))
                    }
                } else {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.STOCK,
                        modifier = Modifier.fillMaxSize().padding(padding),
                    ) {
                        composable(Routes.GUIDE) { GuideScreen() }
                        composable(Routes.STOCK) { StockScreen(app) }
                        composable(Routes.INVENTORY) { InventoryScreen(app) }
                        composable(Routes.MOVEMENTS) { MovementsScreen(app) }
                        composable(Routes.COLOURS) { ColoursScreen(app) }
                        composable(Routes.CROSS_VIEW) { CrossViewScreen(app) }
                        composable(Routes.DASHBOARD) { DashboardScreen(app) }
                        composable(Routes.REPORTS) { ReportsScreen(app) }
                        composable(Routes.MASTERS) { MastersScreen(app) }
                        composable(Routes.AUDIT) { AuditScreen(app) }
                        composable(Routes.SETTINGS) { SettingsScreen(app) }
                        composable(Routes.PROFILE) { ProfileScreen(app) }
                    }
                }
            }
        }
    }
}
