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
import androidx.compose.runtime.mutableStateMapOf
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
import com.alazhartex.stock.ui.components.MovementPairCard
import com.alazhartex.stock.ui.components.SwatchSize
import com.alazhartex.stock.ui.components.TransferPairSpec
import com.alazhartex.stock.ui.components.paletteContentDescription
import com.alazhartex.stock.ui.components.rememberAnySwatch
import com.alazhartex.stock.ui.dateTimeString
import com.alazhartex.stock.ui.integerString
import kotlin.math.absoluteValue

private val TYPE_FILTERS = listOf("ALL", "IN", "OUT", "TRANSFER_IN", "TRANSFER_OUT", "ADJUSTMENT")

private data class PairEntry(val pairId: String, val out: com.alazhartex.stock.data.repo.MovementRowDto, val inn: com.alazhartex.stock.data.repo.MovementRowDto)

@Composable
fun MovementsScreen(app: AlAzharApp) {
    val arabicIndic = LocalArabicIndicDigits.current
    var filter by remember { mutableStateOf("ALL") }
    val pairExpanded = remember { mutableStateMapOf<String, Boolean>() }
    val rows by produceState(initialValue = null as List<com.alazhartex.stock.data.repo.MovementRowDto>?, rememberDbVersion()) {
        value = app.container.readRepository.movements()
    }

    Scaffold { padding ->
        val loaded = rows
        if (loaded == null) {
            LoadingBox(stringResource(R.string.loading), Modifier.padding(padding))
            return@Scaffold
        }
        // v1.8.0: transfer legs with the same transferPairId render as ONE connected pair card.
        val displayItems = remember(loaded, filter) {
            val byPair = loaded.filter { it.transferPairId != null }.groupBy { it.transferPairId!! }
            val consumed = mutableSetOf<String>()
            val out = mutableListOf<Any>()
            for (mv in loaded) {
                val pid = mv.transferPairId
                if (pid == null) {
                    if (filter == "ALL" || mv.type == filter) out += mv
                    continue
                }
                if (pid in consumed) continue
                val legs = byPair[pid].orEmpty()
                val transferOut = legs.firstOrNull { it.type == "TRANSFER_OUT" }
                val transferIn = legs.firstOrNull { it.type == "TRANSFER_IN" }
                if (transferOut != null && transferIn != null) {
                    if (filter == "ALL" || filter == "TRANSFER_OUT" || filter == "TRANSFER_IN") {
                        out += PairEntry(pid, transferOut, transferIn)
                        consumed += pid
                    }
                    continue
                }
                if (filter == "ALL" || mv.type == filter) out += mv
            }
            out
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                TYPE_FILTERS.forEach { t ->
                    FilterChip(selected = filter == t, onClick = { filter = t }, label = { Text(t) })
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(
                    items = displayItems,
                    key = { item ->
                        when (item) {
                            is PairEntry -> "pair-" + item.pairId
                            else -> "mv-" + (item as com.alazhartex.stock.data.repo.MovementRowDto).id
                        }
                    },
                    contentType = { item -> if (item is PairEntry) "pair" else "movement" },
                ) { item ->
                    when (item) {
                        is PairEntry -> {
                            val qty = item.out.qtyDelta.absoluteValue
                            val expanded = pairExpanded[item.pairId] ?: false
                            MovementPairCard(
                                spec = TransferPairSpec(
                                    qtyDisplay = integerString(qty, arabicIndic),
                                    routeDisplay = "${item.out.warehouseId} → ${item.inn.warehouseId}",
                                    dateDisplay = dateTimeString(item.out.atMillis),
                                    colourCode = item.out.colourCode,
                                    legOutLine = "${item.out.type} · ${integerString(item.out.qtyDelta, arabicIndic)} · ${item.out.warehouseId} · ${item.out.mappingId}",
                                    legInLine = "${item.inn.type} · +${integerString(item.inn.qtyDelta.absoluteValue, arabicIndic)} · ${item.inn.warehouseId} · ${item.inn.mappingId}",
                                    note = item.out.note.takeIf { it.isNotBlank() } ?: item.inn.note.takeIf { it.isNotBlank() },
                                    contentDescription = stringResource(R.string.transfer_pair_summary, item.out.warehouseId, item.inn.warehouseId, integerString(qty, arabicIndic)),
                                ),
                                expanded = expanded,
                                onToggle = { pairExpanded[item.pairId] = !expanded },
                                testTag = "movement_pair_${item.pairId}",
                            )
                        }
                        else -> {
                            val mv = item as com.alazhartex.stock.data.repo.MovementRowDto
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
    }
}
