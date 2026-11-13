package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import com.alazhartex.stock.ui.components.ColourSwatch
import com.alazhartex.stock.ui.components.LoadingBox
import com.alazhartex.stock.ui.components.SwatchSize
import com.alazhartex.stock.ui.components.UtilisationGauge
import com.alazhartex.stock.ui.components.bucketLabelFor
import com.alazhartex.stock.ui.components.paletteContentDescription
import com.alazhartex.stock.ui.components.rememberAnySwatch
import com.alazhartex.stock.ui.integerString
import com.alazhartex.stock.ui.navigation.Routes
import com.alazhartex.stock.ui.percentDisplay

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
    QuickAction(Routes.PROFILE, R.string.nav_profile, Icons.Filled.Person),
    QuickAction(Routes.GUIDE, R.string.nav_guide, Icons.Filled.MenuBook),
)

@Composable
fun DashboardScreen(app: AlAzharApp) {
    val arabicIndic = LocalArabicIndicDigits.current
    val navigate = LocalNavigate.current
    val dashboard by produceState(initialValue = null as com.alazhartex.stock.data.repo.DashboardDto?, rememberDbVersion()) {
        // Keep the last good snapshot if a database rebuild races an in-flight query.
        value = runCatching { app.container.readRepository.dashboard() }.getOrElse { cause -> value ?: throw cause }
    }

    Scaffold { padding ->
        val data = dashboard
        if (data == null) {
            LoadingBox(stringResource(R.string.loading), Modifier.padding(padding))
            return@Scaffold
        }
        val bucketLabel = bucketLabelFor(
            data.bucket,
            stringResource(R.string.bucket_green),
            stringResource(R.string.bucket_amber),
            stringResource(R.string.bucket_red),
            stringResource(R.string.bucket_over_capacity),
            stringResource(R.string.label_empty_no_rows),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Hero -----------------------------------------------------------
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dash_hero")
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.nav_dashboard),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        stringResource(R.string.dashboard_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    UtilisationGauge(
                        label = stringResource(R.string.label_utilisation),
                        percentDisplay = percentDisplay(data.percentDisplay, arabicIndic),
                        bucket = data.bucket,
                        bucketLabel = bucketLabel,
                        percentFraction = ((data.percentDisplay?.toFloatOrNull() ?: 0f) / 100f),
                        contentDescriptionPrefix = stringResource(R.string.app_name),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KpiCell(integerString(data.totalActiveRolls, arabicIndic), stringResource(R.string.label_total_rolls), Modifier.weight(1f))
                        KpiCell(integerString(data.totalCapacity, arabicIndic), stringResource(R.string.label_capacity), Modifier.weight(1f))
                        KpiCell(integerString(data.inactiveRolls, arabicIndic), stringResource(R.string.label_inactive_rolls), Modifier.weight(1f))
                    }
                    DriftBanner(ok = data.driftViolations == 0, count = data.driftViolations)
                }
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

            // ---- Top colours ---------------------------------------------------
            item {
                Text(
                    stringResource(R.string.label_top_colours),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            item {
                if (data.topColours.isEmpty()) {
                    Text(stringResource(R.string.label_empty_no_rows), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        data.topColours.forEach { tc ->
                            val tcSw = rememberAnySwatch(tc.family, tc.grade, tc.colourCode, tc.customHex)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    ColourSwatch(
                                        value = tcSw.argb,
                                        onColour = tcSw.onColour,
                                        contentDescription = paletteContentDescription(tc.colourCode, tc.colourNameEn, tc.colourNameAr, tc.grade),
                                        size = SwatchSize.Small,
                                    )
                                    Column {
                                        Text(tc.colourCode, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        Text(tc.colourNameEn, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        Text(integerString(tc.rolls, arabicIndic), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- Top sheds ------------------------------------------------------
            item {
                Text(
                    stringResource(R.string.label_top_sheds),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(items = data.topSheds, key = { it.shedId }, contentType = { "shed" }) { shed ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(shed.nameEn, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(integerString(shed.activeRolls, arabicIndic), style = MaterialTheme.typography.bodyLarge)
                            Text("${shed.percentDisplay ?: "—"}%", style = MaterialTheme.typography.labelLarge)
                        }
                        val fraction = percentFraction(shed.percentDisplay)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
            item { Box(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun KpiCell(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun DriftBanner(ok: Boolean, count: Int) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
            )
            Text(
                if (ok) {
                    stringResource(R.string.label_drift_ok)
                } else {
                    stringResource(R.string.label_drift_detected) + ": " + count
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (ok) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
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

private fun percentFraction(percentDisplay: String?): Float =
    ((percentDisplay?.toFloatOrNull() ?: 0f) / 100f).coerceIn(0f, 1f)
