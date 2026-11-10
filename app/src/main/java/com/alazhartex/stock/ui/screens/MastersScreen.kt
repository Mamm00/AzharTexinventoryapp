package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.domain.model.InvariantViolationException
import com.alazhartex.stock.ui.LocalSnackbar
import com.alazhartex.stock.ui.components.LabeledRow
import com.alazhartex.stock.ui.components.LoadingBox
import kotlinx.coroutines.launch
import java.util.UUID

private enum class MastersTab { WAREHOUSES, FABRICS, COLOURS, MAPPINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MastersScreen(app: AlAzharApp) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val refresh = rememberRefresh()
    val tick = refresh.tick.intValue
    var tab by remember { mutableStateOf(MastersTab.WAREHOUSES) }
    val snapshot by produceState(initialValue = null as com.alazhartex.stock.data.repo.MastersSnapshot?, tick) {
        value = app.container.readRepository.masters()
    }
    var showAddFabric by remember { mutableStateOf(false) }
    var showAddColour by remember { mutableStateOf(false) }
    var showAddMapping by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<com.alazhartex.stock.data.local.FabricEntity?>(null) }

    Scaffold { padding ->
        val data = snapshot
        if (data == null) {
            LoadingBox(stringResource(R.string.loading), Modifier.padding(padding))
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Text("${stringResource(R.string.label_sheds)}: ${data.warehouses.size}", style = MaterialTheme.typography.labelLarge)
                Text("${stringResource(R.string.label_fabrics)}: ${data.fabrics.size}", style = MaterialTheme.typography.labelLarge)
                Text("${stringResource(R.string.label_colours)}: ${data.colours.size}", style = MaterialTheme.typography.labelLarge)
                Text("${stringResource(R.string.label_mappings)}: ${data.mappingCount}", style = MaterialTheme.typography.labelLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MastersTab.entries.forEach { t ->
                    FilterChip(
                        selected = tab == t,
                        onClick = { tab = t },
                        label = {
                            Text(stringResource(when (t) {
                                MastersTab.WAREHOUSES -> R.string.tab_warehouses
                                MastersTab.FABRICS -> R.string.tab_fabrics
                                MastersTab.COLOURS -> R.string.tab_colours
                                MastersTab.MAPPINGS -> R.string.tab_mappings
                            }))
                        },
                    )
                }
            }
            when (tab) {
                MastersTab.WAREHOUSES -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                        items(items = data.warehouses, key = { it.id }, contentType = { "wh" }) { wh ->
                            Card(colors = CardDefaults.cardColors()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(wh.id, style = MaterialTheme.typography.titleMedium)
                                    Text("${wh.nameEn} · ${wh.nameAr}", style = MaterialTheme.typography.bodyMedium)
                                    LabeledRow(stringResource(R.string.label_capacity), wh.capacityRolls.toString())
                                    LabeledRow(stringResource(R.string.label_assigned_sheds), wh.keeperAccountId)
                                }
                            }
                        }
                    }
                }
                MastersTab.FABRICS -> {
                    Button(onClick = { showAddFabric = true }, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(stringResource(R.string.masters_add_fabric))
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(items = data.fabrics, key = { it.id }, contentType = { "fabric" }) { fabric ->
                            Card(colors = CardDefaults.cardColors()) {
                                Row(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(fabric.nameEn, style = MaterialTheme.typography.titleMedium)
                                        Text("${fabric.nameAr} · ${fabric.family}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    TextButton(onClick = { deleteTarget = fabric }) { Text(stringResource(R.string.action_delete)) }
                                }
                            }
                        }
                    }
                }
                MastersTab.COLOURS -> {
                    Button(onClick = { showAddColour = true }, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(stringResource(R.string.masters_add_colour))
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(items = data.colours, key = { it.id }, contentType = { "colour" }) { colour ->
                            Card(colors = CardDefaults.cardColors()) {
                                Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${colour.code} — ${colour.nameEn}", style = MaterialTheme.typography.titleMedium)
                                        Text("${colour.nameAr} · ${colour.family}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Text(colour.grade, style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                        }
                    }
                }
                MastersTab.MAPPINGS -> {
                    Button(onClick = { showAddMapping = true }, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(stringResource(R.string.masters_add_mapping))
                    }
                    Text("${stringResource(R.string.label_mappings)}: ${data.mappingCount}", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    if (showAddFabric) {
        AddFabricDialog(app, snackbar) { showAddFabric = false; refresh.bump() }
    }
    if (showAddColour) {
        AddColourDialog(app, snackbar) { showAddColour = false; refresh.bump() }
    }
    if (showAddMapping) {
        AddMappingDialog(app, snackbar, snapshot) { showAddMapping = false; refresh.bump() }
    }
    deleteTarget?.let { fabric ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm_delete_fabric)) },
            text = { Text("${fabric.nameEn} · ${fabric.nameAr}") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val message = try {
                            app.container.mastersSettingsRepository.deleteFabric(ACTOR_ADMIN, fabric.id, UUID.randomUUID().toString())
                            context.getString(R.string.toast_saved)
                        } catch (e: InvariantViolationException) {
                            context.getString(R.string.toast_delete_blocked)
                        } catch (t: Throwable) {
                            snackbarFor(context, t)
                        }
                        snackbar.showSnackbar(message)
                        deleteTarget = null
                        refresh.bump()
                    }
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
