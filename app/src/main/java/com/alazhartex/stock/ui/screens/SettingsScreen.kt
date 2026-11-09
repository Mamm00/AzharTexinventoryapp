package com.alazhartex.stock.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.core.content.FileProvider
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.MainActivity
import com.alazhartex.stock.R
import com.alazhartex.stock.data.transfer.ExportImportManager
import com.alazhartex.stock.ui.LocalSnackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun SettingsScreen(app: AlAzharApp) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences(MainActivity.LOCALE_PREFS, Context.MODE_PRIVATE)
    var language by remember { mutableStateOf(prefs.getString(MainActivity.KEY_LANGUAGE, "ar") ?: "ar") }
    var digits by remember { mutableStateOf(app.container.digitsFlow.value) }
    val storedSettings by produceState(initialValue = emptyMap(), Unit) {
        value = app.container.readRepository.settingsAll().associate { it.key to it.value }
    }
    var legalName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var taxId by remember { mutableStateOf("") }
    var logoUri by remember { mutableStateOf("") }
    var loadedProfile by remember { mutableStateOf(false) }
    if (!loadedProfile && storedSettings.isNotEmpty()) {
        legalName = storedSettings["profile.legal_name"].orEmpty()
        address = storedSettings["profile.address"].orEmpty()
        phone = storedSettings["profile.phone"].orEmpty()
        email = storedSettings["profile.email"].orEmpty()
        taxId = storedSettings["profile.tax_id"].orEmpty()
        logoUri = storedSettings["profile.logo_uri"].orEmpty()
        loadedProfile = true
    }

    var importStage by remember { mutableStateOf<File?>(null) }
    var importSidecar by remember { mutableStateOf<File?>(null) }
    var importPreview by remember { mutableStateOf<ExportImportManager.ImportPreview?>(null) }
    var busy by remember { mutableStateOf(false) }

    val pickDb = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            try {
                val stream = context.contentResolver.openInputStream(uri) ?: return@launch
                importStage = withContext(Dispatchers.IO) { app.container.exportImportManager.stageImport(stream, isSidecar = false) }
            } finally {
                busy = false
            }
        }
    }
    val pickSidecar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            try {
                val stream = context.contentResolver.openInputStream(uri) ?: return@launch
                importSidecar = withContext(Dispatchers.IO) { app.container.exportImportManager.stageImport(stream, isSidecar = true) }
            } finally {
                busy = false
            }
        }
    }

    // When both files are staged, run the ladder.
    androidx.compose.runtime.LaunchedEffect(importStage, importSidecar) {
        val db = importStage ?: return@LaunchedEffect
        val sidecar = importSidecar ?: return@LaunchedEffect
        busy = true
        val report = withContext(Dispatchers.IO) { app.container.exportImportManager.runLadder(db, sidecar) }
        busy = false
        when (report) {
            is ExportImportManager.ImportReport.RungFailed ->
                snackbar.showSnackbar(context.getString(R.string.toast_import_failed, report.rung, report.detail))
            is ExportImportManager.ImportReport.Ready -> importPreview = report.preview
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- language ----
            Card(colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.label_settings_language), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = language == "ar", onClick = {
                            language = "ar"
                            prefs.edit().putString(MainActivity.KEY_LANGUAGE, "ar").apply()
                            (context as? Activity)?.recreate()
                        }, label = { Text(stringResource(R.string.label_language_ar)) })
                        FilterChip(selected = language == "en", onClick = {
                            language = "en"
                            prefs.edit().putString(MainActivity.KEY_LANGUAGE, "en").apply()
                            (context as? Activity)?.recreate()
                        }, label = { Text(stringResource(R.string.label_language_en)) })
                    }
                }
            }

            // ---- digits ----
            Card(colors = CardDefaults.cardColors()) {
                Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.label_settings_digits), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.label_settings_digits_summary), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = digits, onCheckedChange = { on ->
                        digits = on
                        scope.launch {
                            app.container.mastersSettingsRepository.setSetting(
                                ACTOR_ADMIN, "arabic_indic_digits", on.toString(), UUID.randomUUID().toString(),
                            )
                            app.container.digitsFlow.update { on }
                            snackbar.showSnackbar(context.getString(R.string.toast_setting_saved))
                        }
                    })
                }
            }

            // ---- letterhead ----
            Card(colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.label_letterhead), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = legalName, onValueChange = { legalName = it.take(120) }, label = { Text(stringResource(R.string.label_legal_name)) }, singleLine = true)
                    OutlinedTextField(value = address, onValueChange = { address = it.take(200) }, label = { Text(stringResource(R.string.label_address)) }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it.take(40) }, label = { Text(stringResource(R.string.label_phone)) }, singleLine = true)
                    OutlinedTextField(value = email, onValueChange = { email = it.take(80) }, label = { Text(stringResource(R.string.label_email)) }, singleLine = true)
                    OutlinedTextField(value = taxId, onValueChange = { taxId = it.take(40) }, label = { Text(stringResource(R.string.label_tax_id)) }, singleLine = true)
                    OutlinedTextField(value = logoUri, onValueChange = { logoUri = it.take(120) }, label = { Text(stringResource(R.string.label_logo_uri)) }, singleLine = true)
                    Text(stringResource(R.string.toast_letterhead_logo_hint), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        scope.launch {
                            val values = mapOf(
                                "profile.legal_name" to legalName, "profile.address" to address,
                                "profile.phone" to phone, "profile.email" to email,
                                "profile.tax_id" to taxId, "profile.logo_uri" to logoUri,
                            )
                            try {
                                values.forEach { (k, v) ->
                                    app.container.mastersSettingsRepository.setSetting(ACTOR_ADMIN, k, v.trim(), UUID.randomUUID().toString())
                                }
                                snackbar.showSnackbar(context.getString(R.string.toast_letterhead_saved))
                            } catch (t: Throwable) {
                                snackbar.showSnackbar(snackbarFor(t))
                            }
                        }
                    }) { Text(stringResource(R.string.action_save)) }
                }
            }

            // ---- export ----
            Card(colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.action_export_db), style = MaterialTheme.typography.titleMedium)
                    Text("alazhartex-*.db + .sha256 → files/exports/", style = MaterialTheme.typography.bodySmall)
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val result = withContext(Dispatchers.IO) { app.container.exportImportManager.exportDatabase() }
                                val uri = FileProvider.getUriForFile(context, "com.alazhartex.stock.fileprovider", result.file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, result.file.name))
                                snackbar.showSnackbar(context.getString(R.string.toast_export_done, result.file.name))
                            } catch (t: Throwable) {
                                snackbar.showSnackbar(context.getString(R.string.toast_export_failed))
                            } finally {
                                busy = false
                            }
                        }
                    }) { Text(stringResource(R.string.action_export_db)) }
                }
            }

            // ---- import ----
            Card(colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.action_import_db), style = MaterialTheme.typography.titleMedium)
                    Text("1) .db   2) .sha256 — five-rung integrity ladder runs before any overwrite", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = { pickDb.launch(arrayOf("*/*")) }) { Text(".db") }
                        Button(enabled = importStage != null && !busy, onClick = { pickSidecar.launch(arrayOf("*/*")) }) { Text(".sha256") }
                    }
                    importStage?.let { Text("db: ${it.name}", style = MaterialTheme.typography.bodySmall) }
                    importSidecar?.let { Text("sidecar: ${it.name}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }

    // ---- import confirm (explicit overwrite) ----
    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            title = { Text(stringResource(R.string.toast_overwrite_confirm)) },
            text = {
                Column {
                    Text(stringResource(R.string.toast_overwrite_body))
                    Text("tables: ${preview.counts.size} · schema v${preview.schemaVersion} · ${preview.appVersion}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val db = importStage ?: return@TextButton
                    scope.launch {
                        val current = context.getDatabasePath("alazhartex.db")
                        withContext(Dispatchers.IO) { app.container.exportImportManager.safetyBackupAndReplace(current, db) }
                        android.widget.Toast.makeText(context, context.getString(R.string.toast_import_done), android.widget.Toast.LENGTH_LONG).show()
                        val relaunch = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        context.startActivity(relaunch)
                        Runtime.getRuntime().exit(0)
                    }
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(onClick = { importPreview = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
