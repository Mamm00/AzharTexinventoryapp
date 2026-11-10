package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.ui.LocalArabicIndicDigits
import com.alazhartex.stock.ui.components.LoadingBox
import com.alazhartex.stock.ui.integerString

@Composable
fun CrossViewScreen(app: AlAzharApp) {
    val arabicIndic = LocalArabicIndicDigits.current
    val data by produceState(initialValue = null as CrossData?, Unit) {
        val cards = app.container.readRepository.shedCards()
        val lines = app.container.readRepository.inventoryLines().filter { it.isActive }
        val sheds = cards.map { it.shedId }
        val byColour = lines.groupBy { "${it.colourCode}" }
        val rows = byColour.keys.sorted().map { colour ->
            colour to lines.filter { it.colourCode == colour }.associate { it.warehouseId to it.closing }
        }
        value = CrossData(sheds, rows)
    }

    Scaffold { padding ->
        val d = data
        if (d == null) {
            LoadingBox(stringResource(R.string.loading), Modifier.padding(padding))
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            Text(stringResource(R.string.cross_read_only), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            val hScroll = rememberScrollState()
            Row(modifier = Modifier.horizontalScroll(hScroll).padding(top = 8.dp)) {
                Column(modifier = Modifier.width(120.dp)) {
                    Text(stringResource(R.string.label_colour), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                }
                d.sheds.forEach { shed ->
                    Column(modifier = Modifier.width(92.dp)) {
                        Text(shed.removePrefix("wh-"), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    }
                }
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(count = d.rows.size, key = { d.rows[it].first }) { index ->
                    val (colour, perShed) = d.rows[index]
                    Row(modifier = Modifier.horizontalScroll(hScroll).padding(vertical = 2.dp)) {
                        Column(modifier = Modifier.width(120.dp)) { Text(colour, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium) }
                        d.sheds.forEach { shed ->
                            Column(modifier = Modifier.width(92.dp)) {
                                val v = perShed[shed]
                                Text(if (v == null || v == 0L) "·" else integerString(v, arabicIndic), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private data class CrossData(val sheds: List<String>, val rows: List<Pair<String, Map<String, Long>>>)
