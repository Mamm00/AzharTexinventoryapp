package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.ui.LocalArabicIndicDigits
import com.alazhartex.stock.ui.components.ColourSwatch
import com.alazhartex.stock.ui.components.LoadingBox
import com.alazhartex.stock.ui.components.SwatchSize
import com.alazhartex.stock.ui.components.paletteContentDescription
import com.alazhartex.stock.ui.components.rememberAnySwatch
import com.alazhartex.stock.ui.integerString

@Composable
fun InventoryScreen(app: AlAzharApp) {
    val arabicIndic = LocalArabicIndicDigits.current
    var query by remember { mutableStateOf("") }
    val lines by produceState(initialValue = null as List<com.alazhartex.stock.data.repo.InventoryLineDto>?, Unit) {
        value = app.container.readRepository.inventoryLines()
    }

    Scaffold { padding ->
        val loaded = lines
        if (loaded == null) {
            LoadingBox(stringResource(R.string.loading), Modifier.padding(padding))
            return@Scaffold
        }
        val filtered = remember(loaded, query) {
            val q = query.trim().lowercase()
            if (q.isEmpty()) loaded else loaded.filter {
                it.fabricNameEn.lowercase().contains(q) ||
                    it.fabricNameAr.contains(query.trim()) ||
                    it.colourCode.lowercase().contains(q) ||
                    it.colourNameEn.lowercase().contains(q) ||
                    it.colourNameAr.contains(query.trim()) ||
                    it.warehouseId.lowercase().contains(q)
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
            if (filtered.isEmpty()) {
                Text(stringResource(R.string.label_empty_no_rows), modifier = Modifier.padding(16.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = filtered, key = { it.mappingId }, contentType = { "inventory_line" }) { line ->
                    Card(colors = CardDefaults.cardColors()) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            val lineSw = rememberAnySwatch(line.family, line.grade, line.colourCode)
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
