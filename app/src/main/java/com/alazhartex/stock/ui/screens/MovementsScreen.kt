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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import com.alazhartex.stock.ui.dateTimeString
import com.alazhartex.stock.ui.integerString

private val TYPE_FILTERS = listOf("ALL", "IN", "OUT", "TRANSFER_IN", "TRANSFER_OUT", "ADJUSTMENT")

@Composable
fun MovementsScreen(app: AlAzharApp) {
    val arabicIndic = LocalArabicIndicDigits.current
    var filter by remember { mutableStateOf("ALL") }
    val rows by produceState(initialValue = null as List<com.alazhartex.stock.data.repo.MovementRowDto>?, rememberDbVersion()) {
        value = app.container.readRepository.movements()
    }

    Scaffold { padding ->
        val loaded = rows
        if (loaded == null) {
            LoadingBox(stringResource(R.string.loading), Modifier.padding(padding))
            return@Scaffold
        }
        val filtered = remember(loaded, filter) { if (filter == "ALL") loaded else loaded.filter { it.type == filter } }
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                TYPE_FILTERS.forEach { t ->
                    FilterChip(selected = filter == t, onClick = { filter = t }, label = { Text(t) })
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items = filtered, key = { it.id }, contentType = { "movement" }) { mv ->
                    Card(colors = CardDefaults.cardColors()) {
                        Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            val mvSw = rememberAnySwatch(mv.family, mv.grade, mv.colourCode, mv.customHex)
                            ColourSwatch(
                                value = mvSw.argb,
                                onColour = mvSw.onColour,
                                contentDescription = paletteContentDescription(mv.colourCode, mv.colourNameEn, mv.colourNameAr, mv.grade),
                                size = SwatchSize.Small,
                            )
                            Text(
                                text = mv.type,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (mv.qtyDelta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = (if (mv.qtyDelta >= 0) "+" else "") + integerString(mv.qtyDelta, arabicIndic),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Column(modifier = Modifier.weight(1.6f)) {
                                Text("${mv.warehouseId} · ${mv.colourCode} · ${mv.mappingId}", style = MaterialTheme.typography.bodySmall)
                                Text(dateTimeString(mv.atMillis), style = MaterialTheme.typography.bodySmall)
                                if (mv.note.isNotBlank()) Text(mv.note, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
