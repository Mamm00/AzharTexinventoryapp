package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
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
import com.alazhartex.stock.ui.components.LoadingBox

@Composable
fun ColoursScreen(app: AlAzharApp) {
    var query by remember { mutableStateOf("") }
    val colours by produceState(initialValue = null as List<com.alazhartex.stock.data.repo.ColourRowDto>?, Unit) {
        value = app.container.readRepository.colours()
    }

    Scaffold { padding ->
        val loaded = colours
        if (loaded == null) {
            LoadingBox(stringResource(R.string.loading), Modifier.padding(padding))
            return@Scaffold
        }
        val filtered = remember(loaded, query) {
            val q = query.trim().lowercase()
            if (q.isEmpty()) loaded else loaded.filter {
                it.code.lowercase().contains(q) || it.nameEn.lowercase().contains(q) ||
                    it.nameAr.contains(query.trim()) || it.family.lowercase().contains(q) ||
                    it.grade.lowercase().contains(q)
            }
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                label = { Text(stringResource(R.string.label_search)) },
                singleLine = true,
            )
            Text(
                "${filtered.size} / ${loaded.size}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items = filtered, key = { it.id }, contentType = { "colour" }) { colour ->
                    Card(colors = CardDefaults.cardColors()) {
                        Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${colour.code} — ${colour.nameEn}", style = MaterialTheme.typography.titleMedium)
                                Text(colour.nameAr, style = MaterialTheme.typography.bodyMedium)
                                Text("${colour.family}", style = MaterialTheme.typography.bodySmall)
                            }
                            AssistChip(onClick = {}, label = { Text(colour.grade) }, enabled = false)
                            if (colour.keeperCode != null) {
                                AssistChip(onClick = {}, label = { Text(colour.keeperCode) }, enabled = false)
                            }
                        }
                    }
                }
            }
        }
    }
}
