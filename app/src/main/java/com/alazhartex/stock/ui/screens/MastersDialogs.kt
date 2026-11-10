package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.data.repo.MastersSnapshot
import com.alazhartex.stock.ui.LocalSnackbar
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
private fun DialogScaffold(title: String, onSave: suspend () -> Unit, onCancel: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    Dialog(onDismissRequest = onCancel) {
        Card(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                content()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { onSave() } }) { Text(stringResource(R.string.action_save)) }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        }
    }
}

@Composable
fun AddFabricDialog(app: AlAzharApp, snackbar: SnackbarHostState, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nameEn by remember { mutableStateOf("") }
    var nameAr by remember { mutableStateOf("") }
    var family by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    DialogScaffold(
        title = stringResource(R.string.masters_add_fabric),
        onSave = {
            if (busy || nameEn.isBlank() || nameAr.isBlank() || family.isBlank()) {
                // ignore — validation feedback is the enabled state of the fields
            } else {
            busy = true
            val message = try {
                app.container.mastersSettingsRepository.addFabric(
                    ACTOR_ADMIN, nameEn.trim(), nameAr.trim(), family.trim(), UUID.randomUUID().toString(),
                )
                context.getString(R.string.toast_fabric_added)
            } catch (t: Throwable) {
                snackbarFor(t)
            }
            snackbar.showSnackbar(message)
            onClose()
            }
        },
        onCancel = onClose,
    ) {
        OutlinedTextField(value = nameEn, onValueChange = { nameEn = it.take(120) }, label = { Text("Name (EN)") }, singleLine = true)
        OutlinedTextField(value = nameAr, onValueChange = { nameAr = it.take(120) }, label = { Text("Name (AR)") }, singleLine = true)
        OutlinedTextField(value = family, onValueChange = { family = it.take(60) }, label = { Text(stringResource(R.string.label_family)) }, singleLine = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddColourDialog(app: AlAzharApp, snackbar: SnackbarHostState, onClose: () -> Unit) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var nameAr by remember { mutableStateOf("") }
    var family by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("A") }
    var keeperCode by remember { mutableStateOf("") }
    var gradeExpanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    DialogScaffold(
        title = stringResource(R.string.masters_add_colour),
        onSave = {
            if (busy || code.isBlank() || nameEn.isBlank() || nameAr.isBlank() || family.isBlank()) {
            } else {
            busy = true
            val message = try {
                app.container.mastersSettingsRepository.addColour(
                    ACTOR_ADMIN, code.trim(), nameEn.trim(), nameAr.trim(), grade, family.trim(),
                    keeperCode.trim().ifBlank { null }, UUID.randomUUID().toString(),
                )
                context.getString(R.string.toast_colour_added)
            } catch (t: Throwable) {
                snackbarFor(t)
            }
            snackbar.showSnackbar(message)
            onClose()
            }
        },
        onCancel = onClose,
    ) {
        OutlinedTextField(value = code, onValueChange = { code = it.take(20) }, label = { Text("Code") }, singleLine = true)
        OutlinedTextField(value = nameEn, onValueChange = { nameEn = it.take(120) }, label = { Text("Name (EN)") }, singleLine = true)
        OutlinedTextField(value = nameAr, onValueChange = { nameAr = it.take(120) }, label = { Text("Name (AR)") }, singleLine = true)
        OutlinedTextField(value = family, onValueChange = { family = it.take(60) }, label = { Text(stringResource(R.string.label_family)) }, singleLine = true)
        ExposedDropdownMenuBox(expanded = gradeExpanded, onExpandedChange = { gradeExpanded = !gradeExpanded }) {
            OutlinedTextField(
                readOnly = true,
                value = grade,
                onValueChange = {},
                label = { Text(stringResource(R.string.label_grade)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = gradeExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
            )
            ExposedDropdownMenu(expanded = gradeExpanded, onDismissRequest = { gradeExpanded = false }) {
                listOf("A", "B", "C", "D").forEach { g ->
                    DropdownMenuItem(text = { Text(g) }, onClick = { grade = g; gradeExpanded = false })
                }
            }
        }
        OutlinedTextField(value = keeperCode, onValueChange = { keeperCode = it.take(20) }, label = { Text(stringResource(R.string.label_keeper_code)) }, singleLine = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMappingDialog(app: AlAzharApp, snackbar: SnackbarHostState, snapshot: MastersSnapshot?, onClose: () -> Unit) {
    val context = LocalContext.current
    var warehouseId by remember { mutableStateOf(snapshot?.warehouses?.firstOrNull()?.id ?: "") }
    var fabricId by remember { mutableStateOf(snapshot?.fabrics?.firstOrNull()?.id ?: "") }
    var colourId by remember { mutableStateOf(snapshot?.colours?.firstOrNull()?.id ?: "") }
    var busy by remember { mutableStateOf(false) }

    DialogScaffold(
        title = stringResource(R.string.masters_add_mapping),
        onSave = {
            if (busy || warehouseId.isBlank() || fabricId.isBlank() || colourId.isBlank()) {
            } else {
            busy = true
            val message = try {
                app.container.mastersSettingsRepository.addMapping(
                    ACTOR_ADMIN, warehouseId, fabricId, colourId, UUID.randomUUID().toString(),
                )
                context.getString(R.string.toast_mapping_added)
            } catch (t: Throwable) {
                snackbarFor(t)
            }
            snackbar.showSnackbar(message)
            onClose()
            }
        },
        onCancel = onClose,
    ) {
        DropdownField(stringResource(R.string.label_warehouse), warehouseId, snapshot?.warehouses?.map { it.id to "${it.id} (${it.nameEn})" } ?: emptyList()) { warehouseId = it }
        DropdownField(stringResource(R.string.label_fabric), fabricId, snapshot?.fabrics?.map { it.id to it.nameEn } ?: emptyList()) { fabricId = it }
        DropdownField(stringResource(R.string.label_colour), colourId, snapshot?.colours?.map { it.id to "${it.code} — ${it.nameEn}" } ?: emptyList()) { colourId = it }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(label: String, selectedId: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: selectedId
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            readOnly = true,
            value = selectedLabel,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, optionLabel) ->
                DropdownMenuItem(text = { Text(optionLabel) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}
