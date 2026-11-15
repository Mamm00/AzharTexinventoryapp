package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.alazhartex.stock.ui.LocalNavigate
import com.alazhartex.stock.ui.navigation.Routes
import com.alazhartex.stock.ui.navigation.LocalAnimatedVisibilityScope
import com.alazhartex.stock.ui.navigation.LocalSharedTransitionScope
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.ui.LocalArabicIndicDigits
import com.alazhartex.stock.ui.components.ColourSwatch
import com.alazhartex.stock.ui.components.LoadingBox
import com.alazhartex.stock.ui.components.SwatchSize
import com.alazhartex.stock.ui.components.paletteContentDescription
import com.alazhartex.stock.ui.components.rememberAnySwatch
import com.alazhartex.stock.ui.integerString

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun InventoryScreen(app: AlAzharApp) {
    val arabicIndic = LocalArabicIndicDigits.current
    val navigate = LocalNavigate.current
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    var query by remember { mutableStateOf("") }
    var colourFilter by remember { mutableStateOf<String?>(null) }
    // One-shot: dashboard swatch ring seeds the pre-filter on first composition after navigation.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        app.container.pendingInventoryColourFilter.value?.let { pending ->
            colourFilter = pending
            app.container.pendingInventoryColourFilter.value = null
        }
    }
    val lines by produceState(initialValue = null as List<com.alazhartex.stock.data.repo.InventoryLineDto>?, rememberDbVersion()) {
        value = app.container.readRepository.inventoryLines()
    }

    Scaffold { padding ->
        val loaded = lines
        if (loaded == null) {
            LoadingBox(stringResource(R.string.loading), Modifier.padding(padding))
            return@Scaffold
        }
        val filtered = remember(loaded, query, colourFilter) {
            val q = query.trim().lowercase()
            loaded.filter { line ->
                (colourFilter == null || line.colourCode.equals(colourFilter, ignoreCase = true)) &&
                    (q.isEmpty() ||
                        line.fabricNameEn.lowercase().contains(q) ||
                        line.fabricNameAr.contains(query.trim()) ||
                        line.colourCode.lowercase().contains(q) ||
                        line.colourNameEn.lowercase().contains(q) ||
                        line.colourNameAr.contains(query.trim()) ||
                        line.warehouseId.lowercase().contains(q))
            }
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                label = { Text(stringResource(R.string.label_search)) },
                singleLine = true,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            colourFilter?.let { active ->
                var chipModifier: Modifier = Modifier.padding(bottom = 8.dp)
                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        chipModifier = chipModifier.sharedBounds(
                            sharedContentState = rememberSharedContentState("swatch-$active"),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                }
                FilterChip(
                    selected = true,
                    onClick = { colourFilter = null },
                    label = { Text(active) },
                    leadingIcon = {
                        val dotLine = loaded.firstOrNull { it.colourCode.equals(active, ignoreCase = true) }
                        val sw = dotLine?.let { rememberAnySwatch(it.family, it.grade, it.colourCode, it.customHex) }
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.size(16.dp).clip(CircleShape).background(sw?.let { androidx.compose.ui.graphics.Color(it.argb) } ?: MaterialTheme.colorScheme.primary),
                        )
                    },
                    modifier = chipModifier,
                )
            }
            if (filtered.isEmpty()) {
                Text(stringResource(R.string.label_empty_no_rows), modifier = Modifier.padding(16.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = filtered, key = { it.mappingId }, contentType = { "inventory_line" }) { line ->
                    val sharedKey = "inventory-${line.mappingId}"
                    var cardModifier: Modifier = Modifier
                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            cardModifier = cardModifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(sharedKey),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        }
                    }
                    cardModifier = cardModifier.clickable { navigate(Routes.inventoryDetail(line.mappingId)) }
                    Card(colors = CardDefaults.cardColors(), modifier = cardModifier) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            val lineSw = rememberAnySwatch(line.family, line.grade, line.colourCode, line.customHex)
                            ColourSwatch(
                                value = lineSw.argb,
                                onColour = lineSw.onColour,
                                contentDescription = paletteContentDescription(line.colourCode, line.colourNameEn, line.colourNameAr, line.grade),
                                size = SwatchSize.Small,
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${line.fabricNameEn} · ${line.colourCode} ${line.colourNameEn}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${line.warehouseId} · ${stringResource(R.string.label_grade)} ${line.grade} · ${line.family}${if (!line.isActive) " · ✕" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "${stringResource(R.string.label_total_rolls)}: ${integerString(line.closing, arabicIndic)} · ${stringResource(R.string.label_inactive_rolls)}: ${integerString(line.inactiveRolls, arabicIndic)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            }
                        }
                    }
                }
            }
        }
    }
}
