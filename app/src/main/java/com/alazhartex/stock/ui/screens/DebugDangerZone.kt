package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.data.local.AccountEntity
import com.alazhartex.stock.data.repo.DebugRepository
import com.alazhartex.stock.ui.LocalSnackbar
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v1.2.0 — debug danger zone: full control over the store with NO authorization checks, for any
 * signed-in account. Everything is audited; destructive actions take a VACUUM INTO safety backup
 * first (files/backups/, last 3 kept). Masters (fabrics/colours) are never touched by the wipe.
 */
@Composable
fun DebugDangerZoneCard(app: AlAzharApp, actorId: String) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    var refreshKey by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    val warehouses by produceState(initialValue = emptyList<DebugRepository.WarehouseSummary>(), refreshKey) {
        value = withContext(Dispatchers.IO) { app.container.debugRepository.warehouseSummaries() }
    }
    val accounts by produceState(initialValue = emptyList<AccountEntity>(), refreshKey) {
        value = withContext(Dispatchers.IO) { app.container.debugRepository.accounts() }
    }

    var addOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DebugRepository.WarehouseSummary?>(null) }
    var deleting by remember { mutableStateOf<DebugRepository.WarehouseSummary?>(null) }
    val deletingCounts by produceState<DebugRepository.WarehouseCascadeCounts?>(null, deleting) {
        value = deleting?.let { target -> withContext(Dispatchers.IO) { app.container.debugRepository.warehouseCascadeCounts(target.id) } }
    }
    var wipeOpen by remember { mutableStateOf(false) }
    val wipeCounts by produceState<DebugRepository.OperationalCounts?>(null, wipeOpen) {
        if (wipeOpen) {
            value = withContext(Dispatchers.IO) { app.container.debugRepository.operationalCounts() }
        }
    }
    var reloadOpen by remember { mutableStateOf(false) }

    fun runAction(action: suspend () -> Unit, toast: Int, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            busy = true
            var ok = false
            try {
                withContext(Dispatchers.IO) { action() }
                ok = true
                snackbar.showSnackbar(context.getString(toast))
            } catch (t: Throwable) {
                snackbar.showSnackbar("Error: ${t.message ?: t.javaClass.simpleName}")
            }
            refreshKey += 1
            busy = false
            onDone(ok)
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.debug_zone_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.debug_zone_summary), style = MaterialTheme.typography.bodySmall)

            // ---- warehouses ----
            Row {
                Text(
                    stringResource(R.string.debug_warehouses_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Button(enabled = !busy, onClick = { addOpen = true }) { Text(stringResource(R.string.action_debug_add_warehouse)) }
            }
            if (warehouses.isEmpty()) {
                Text(stringResource(R.string.debug_warehouses_empty), style = MaterialTheme.typography.bodySmall)
            }
            warehouses.forEach { w ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${w.nameAr} — ${w.nameEn}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.debug_row_meta, w.id, w.capacityRolls, w.keeperUsername),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            stringResource(R.string.debug_row_stats, w.closing, w.activeRolls),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(enabled = !busy, onClick = { editing = w }) { Text(stringResource(R.string.action_edit)) }
                    TextButton(
                        enabled = !busy,
                        onClick = { deleting = w },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.action_delete)) }
                }
            }

            // ---- destructive global actions ----
            Text(stringResource(R.string.action_keep_masters_note), style = MaterialTheme.typography.labelSmall)
            Button(
                enabled = !busy,
                onClick = { wipeOpen = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.action_wipe_all)) }
            Button(enabled = !busy, onClick = { reloadOpen = true }) { Text(stringResource(R.string.action_reload_demo)) }
        }
    }

    // ---- add / edit warehouse dialog ----
    WarehouseFormDialog(
        open = addOpen || editing != null,
        existing = editing,
        accounts = accounts,
        onDismiss = { addOpen = false; editing = null },
        onSave = { id, nameAr, nameEn, capacity, keeperId ->
            val target = editing
            val close = { ok: Boolean -> if (ok) { addOpen = false; editing = null } }
            if (target == null) {
                runAction({
                    app.container.debugRepository.addWarehouse(actorId, id, nameAr, nameEn, capacity, keeperId)
                }, R.string.toast_warehouse_added, close)
            } else {
                runAction({
                    app.container.debugRepository.updateWarehouse(actorId, target.id, nameAr, nameEn, capacity, keeperId)
                }, R.string.toast_warehouse_updated, close)
            }
        },
    )

    // ---- delete warehouse dialog (cascade preview) ----
    deleting?.let { target ->
        val counts = deletingCounts
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.dialog_delete_warehouse_title)) },
            text = {
                Text(
                    if (counts != null) {
                        stringResource(R.string.dialog_delete_warehouse_body, counts.mappings, counts.inventoryRows, counts.movements, counts.rolls, counts.assignments)
                    } else {
                        "…"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("debug_delete_confirm"),
                    onClick = {
                        runAction(
                            action = { app.container.debugRepository.deleteWarehouse(actorId, target.id) },
                            toast = R.string.toast_warehouse_deleted,
                            onDone = { ok -> if (ok) deleting = null },
                        )
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    // ---- wipe ALL operational data dialog ----
    if (wipeOpen) {
        val counts = wipeCounts
        AlertDialog(
            onDismissRequest = { wipeOpen = false },
            title = { Text(stringResource(R.string.dialog_wipe_all_title)) },
            text = {
                Text(
                    if (counts != null) {
                        stringResource(
                            R.string.dialog_wipe_all_body,
                            counts.warehouses, counts.assignments, counts.mappings, counts.inventoryRows,
                            counts.movements, counts.rolls, counts.fabrics, counts.colours,
                        )
                    } else {
                        "…"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("debug_wipe_confirm"),
                    onClick = {
                        runAction(
                            action = { app.container.debugRepository.wipeOperationalData(actorId) },
                            toast = R.string.toast_operational_wiped,
                            onDone = { ok -> if (ok) wipeOpen = false },
                        )
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.dialog_wipe_all_confirm)) }
            },
            dismissButton = { TextButton(onClick = { wipeOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    // ---- reload demo data dialog ----
    if (reloadOpen) {
        AlertDialog(
            onDismissRequest = { reloadOpen = false },
            title = { Text(stringResource(R.string.dialog_reload_demo_title)) },
            text = { Text(stringResource(R.string.dialog_reload_demo_body)) },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("debug_reload_confirm"),
                    onClick = {
                        runAction(
                            action = { app.container.debugRepository.reloadDemoData(actorId) },
                            toast = R.string.toast_demo_reloaded,
                            onDone = { ok -> if (ok) reloadOpen = false },
                        )
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_reload_demo)) }
            },
            dismissButton = { TextButton(onClick = { reloadOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun WarehouseFormDialog(
    open: Boolean,
    existing: DebugRepository.WarehouseSummary?,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (id: String, nameAr: String, nameEn: String, capacity: Long, keeperId: String) -> Unit,
) {
    if (!open) return
    var id by remember(existing?.id) { mutableStateOf(existing?.id ?: "wh-" + UUID.randomUUID().toString().take(6)) }
    var nameAr by remember(existing?.id) { mutableStateOf(existing?.nameAr ?: "") }
    var nameEn by remember(existing?.id) { mutableStateOf(existing?.nameEn ?: "") }
    var capacity by remember(existing?.id) { mutableStateOf(existing?.capacityRolls?.toString() ?: "1000") }
    // Null until the user touches a chip — the effective keeper falls back to the first available keeper.
    var keeperOverride by remember(existing?.id) { mutableStateOf<String?>(existing?.keeperAccountId) }
    val effectiveKeeper = keeperOverride
        ?: accounts.firstOrNull { it.role == "KEEPER" }?.id
        ?: accounts.firstOrNull()?.id
        ?: "admin"

    val idValid = id.matches(Regex("^[a-z0-9][a-z0-9_-]{0,31}$"))
    val namesValid = nameAr.isNotBlank() && nameEn.isNotBlank()
    val capacityValue = capacity.toLongOrNull()
    val capacityValid = capacityValue != null && capacityValue >= 0
    val formValid = idValid && namesValid && capacityValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.action_debug_add_warehouse else R.string.action_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { input -> selectedIdFilter(input)?.let { id = it } },
                    modifier = Modifier.testTag("debug_field_id"),
                    label = { Text(stringResource(R.string.debug_field_id)) },
                    singleLine = true,
                    enabled = existing == null,
                    isError = !idValid,
                )
                OutlinedTextField(
                    value = nameAr,
                    onValueChange = { nameAr = it.take(80) },
                    modifier = Modifier.testTag("debug_field_name_ar"),
                    label = { Text(stringResource(R.string.debug_field_name_ar)) },
                    singleLine = true,
                    isError = nameAr.isBlank(),
                )
                OutlinedTextField(
                    value = nameEn,
                    onValueChange = { nameEn = it.take(80) },
                    modifier = Modifier.testTag("debug_field_name_en"),
                    label = { Text(stringResource(R.string.debug_field_name_en)) },
                    singleLine = true,
                    isError = nameEn.isBlank(),
                )
                OutlinedTextField(
                    value = capacity,
                    onValueChange = { input -> if (input.all { it.isDigit() }) capacity = input.take(7) },
                    modifier = Modifier.testTag("debug_field_capacity"),
                    label = { Text(stringResource(R.string.label_capacity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !capacityValid,
                )
                Text(stringResource(R.string.debug_field_keeper), style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = effectiveKeeper == account.id,
                            onClick = { keeperOverride = account.id },
                            label = { Text("${account.username} (${account.role})") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = formValid,
                modifier = Modifier.testTag("debug_form_save"),
                onClick = {
                    onSave(id.trim().lowercase(), nameAr.trim(), nameEn.trim(), capacityValue ?: 0L, effectiveKeeper)
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Test-tag helpers kept public for instrumentation tests. */
const val TAG_DELETE_CONFIRM = "debug_delete_confirm"
const val TAG_WIPE_CONFIRM = "debug_wipe_confirm"
const val TAG_RELOAD_CONFIRM = "debug_reload_confirm"
const val TAG_FORM_SAVE = "debug_form_save"

private fun selectedIdFilter(input: String): String? =
    input.lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.takeIf { it.length <= 32 }
