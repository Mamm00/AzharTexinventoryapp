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
import com.alazhartex.stock.ui.components.SegmentedGroup
import com.alazhartex.stock.ui.components.SegmentedOption
import com.alazhartex.stock.ui.dateTimeString

@Composable
fun AuditScreen(app: AlAzharApp) {
    var filterMode by remember { mutableStateOf("ALL") }
    val rows by produceState(initialValue = null as List<com.alazhartex.stock.data.repo.AuditRowDto>?, filterMode, rememberDbVersion()) {
        value = app.container.readRepository.audit(limit = 500, onlyBlocked = filterMode == "BLOCKED")
            .let { if (filterMode == "ALLOWED") it.filter { row -> row.result == "ALLOWED" } else it }
    }

    Scaffold { padding ->
        val loaded = rows
        if (loaded == null) {
            LoadingBox(stringResource(R.string.loading), Modifier.padding(padding))
            return@Scaffold
        }
        val filterRow: @Composable () -> Unit = {
            SegmentedGroup(
                options = listOf(
                    SegmentedOption("ALL", stringResource(R.string.label_audit_all), testTag = "audit_filter_all"),
                    SegmentedOption("ALLOWED", stringResource(R.string.audit_result_allowed), testTag = "audit_filter_allowed"),
                    SegmentedOption("BLOCKED", stringResource(R.string.audit_result_blocked), testTag = "audit_filter_blocked"),
                ),
                selected = filterMode,
                onSelected = { filterMode = it },
                testTag = "audit_filter",
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (loaded.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                filterRow()
                Text(stringResource(R.string.label_audit_empty), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            filterRow()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items = loaded, key = { it.id }, contentType = { "audit" }) { row ->
                    val blocked = row.result == "BLOCKED"
                    Card(colors = CardDefaults.cardColors(containerColor = if (blocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(row.action, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                AssistChip(onClick = {}, enabled = false, label = {
                                    Text(if (blocked) stringResource(R.string.audit_result_blocked) else stringResource(R.string.audit_result_allowed))
                                })
                            }
                            Text("${dateTimeString(row.atMillis)} · ${row.actorId} · ${row.target}", style = MaterialTheme.typography.bodySmall)
                            row.reason?.takeIf { it.isNotBlank() }?.let {
                                Text("${stringResource(R.string.label_audit_reason)}: $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
