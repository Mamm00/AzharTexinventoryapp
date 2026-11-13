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
import com.alazhartex.stock.ui.components.LoadingBox
import com.alazhartex.stock.ui.dateTimeString

@Composable
fun AuditScreen(app: AlAzharApp) {
    var blockedOnly by remember { mutableStateOf(false) }
    val rows by produceState(initialValue = null as List<com.alazhartex.stock.data.repo.AuditRowDto>?, blockedOnly, rememberDbVersion()) {
        value = app.container.readRepository.audit(limit = 500, onlyBlocked = blockedOnly)
    }

    Scaffold { padding ->
        val loaded = rows
        if (loaded == null) {
            LoadingBox(stringResource(R.string.loading), Modifier.padding(padding))
            return@Scaffold
        }
        if (loaded.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    FilterChip(selected = !blockedOnly, onClick = { blockedOnly = false }, label = { Text(stringResource(R.string.label_audit_all)) })
                    FilterChip(selected = blockedOnly, onClick = { blockedOnly = true }, label = { Text(stringResource(R.string.audit_result_blocked)) })
                }
                Text(stringResource(R.string.label_audit_empty), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                FilterChip(selected = !blockedOnly, onClick = { blockedOnly = false }, label = { Text(stringResource(R.string.label_audit_all)) })
                FilterChip(selected = blockedOnly, onClick = { blockedOnly = true }, label = { Text(stringResource(R.string.audit_result_blocked)) })
            }
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
