package com.alazhartex.stock.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.ui.LocalNavigate
import com.alazhartex.stock.BuildConfig
import com.alazhartex.stock.MainActivity
import com.alazhartex.stock.R
import com.alazhartex.stock.data.transfer.ExportImportManager
import com.alazhartex.stock.ui.LocalSnackbar
import java.io.File
import java.util.UUID
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.alazhartex.stock.permissions.AlAzharNotifier
import com.alazhartex.stock.permissions.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(app: AlAzharApp) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val navigate = LocalNavigate.current
    val prefs = context.getSharedPreferences(MainActivity.LOCALE_PREFS, Context.MODE_PRIVATE)
    var language by remember { mutableStateOf(prefs.getString(MainActivity.KEY_LANGUAGE, "ar") ?: "ar") }
    var digits by remember { mutableStateOf(app.container.digitsFlow.value) }
    val storedSettings by produceState(initialValue = emptyMap(), rememberDbVersion()) {
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

            // ---- masters catalogue shortcut (v1.1) ----
            Card(colors = CardDefaults.cardColors()) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_masters_entry), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Button(onClick = { navigate(com.alazhartex.stock.ui.navigation.Routes.FABRICS_EDIT) }) {
                        Text(stringResource(R.string.nav_fabrics_edit))
                    }
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
                                snackbar.showSnackbar(snackbarFor(context, t))
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
                                val result = withContext(Dispatchers.IO) {
                                    val exported = app.container.exportImportManager.exportDatabase()
                                    // Export checkpoint may swap WAL state; reload anyway. A rebuild
                                    // failure here must not fail the export the user just got.
                                    runCatching { com.alazhartex.stock.data.local.DbProvider.refresh(context) }
                                    exported
                                }
                                val uri = FileProvider.getUriForFile(context, "com.alazhartex.stock.fileprovider", result.file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, result.file.name))
                                snackbar.showSnackbar(context.getString(R.string.toast_export_done, result.file.name))
                                AlAzharNotifier.notify(context, AlAzharNotifier.ID_EXPORT, context.getString(R.string.notif_export_title), context.getString(R.string.notif_export_body, result.file.name))
                            } catch (t: Throwable) {
                                snackbar.showSnackbar(context.getString(R.string.toast_export_failed))
                            } finally {
                                busy = false
                            }
                        }
                    }) { Text(stringResource(R.string.action_export_db)) }
                }
            }

            // ---- app permissions (notifications + files/media) ----
            var permissionsTick by remember { mutableStateOf(0) }
            LifecycleResumeEffect(Unit) {
                permissionsTick++
                onDispose { }
            }
            val notifGranted = remember(permissionsTick) { Permissions.notificationGranted(context) }
            val mediaGranted = remember(permissionsTick) { Permissions.mediaGranted(context) }
            val allFilesGranted = remember(permissionsTick) { Permissions.allFilesGranted(context) }
            val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionsTick++ }
            val requestMedia = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsTick++ }
            Card(colors = CardDefaults.cardColors(), modifier = Modifier.testTag("permissions_card")) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.permissions_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PermissionRow(
                        title = stringResource(R.string.permissions_notifications),
                        summary = stringResource(R.string.permissions_notifications_summary),
                        status = if (Build.VERSION.SDK_INT >= 33) {
                            stringResource(if (notifGranted) R.string.permissions_status_granted else R.string.permissions_status_denied)
                        } else {
                            stringResource(R.string.permissions_not_required)
                        },
                        granted = notifGranted,
                        actionLabel = stringResource(R.string.permissions_grant),
                        actionVisible = Build.VERSION.SDK_INT >= 33 && !notifGranted,
                        onAction = { requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                        testTag = "permissions_notifications",
                    )
                    PermissionRow(
                        title = stringResource(R.string.permissions_media),
                        summary = stringResource(R.string.permissions_media_summary),
                        status = stringResource(if (mediaGranted) R.string.permissions_status_granted else R.string.permissions_status_denied),
                        granted = mediaGranted,
                        actionLabel = stringResource(R.string.permissions_grant),
                        actionVisible = !mediaGranted,
                        onAction = { requestMedia.launch(Permissions.mediaRequestPermissions()) },
                        testTag = "permissions_media",
                    )
                    PermissionRow(
                        title = stringResource(R.string.permissions_all_files),
                        summary = stringResource(R.string.permissions_all_files_summary),
                        status = if (Build.VERSION.SDK_INT >= 30) {
                            stringResource(if (allFilesGranted) R.string.permissions_status_granted else R.string.permissions_status_denied)
                        } else {
                            stringResource(R.string.permissions_not_required)
                        },
                        granted = allFilesGranted,
                        actionLabel = stringResource(R.string.permissions_open_settings),
                        actionVisible = Build.VERSION.SDK_INT >= 30 && !allFilesGranted,
                        onAction = { runCatching { context.startActivity(Permissions.manageAllFilesIntent(context)) } },
                        testTag = "permissions_all_files",
                    )
                }
            }

            // ---- v1.3.6 refresh database ----
            Card(colors = CardDefaults.cardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.action_refresh_db), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.summary_refresh_db), style = MaterialTheme.typography.bodySmall)
                    Button(
                        enabled = !busy,
                        modifier = Modifier.testTag("settings_refresh_db"),
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    withContext(Dispatchers.IO) { com.alazhartex.stock.data.local.DbProvider.refresh(context) }
                                    snackbar.showSnackbar(context.getString(R.string.toast_refresh_done))
                                } catch (t: Throwable) {
                                    snackbar.showSnackbar(context.getString(R.string.toast_refresh_failed))
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.action_refresh_db)) }
                }
            }

            // ---- v1.2.0 debug danger zone (no restrictions) ----
            DebugDangerZoneCard(app = app, actorId = ACTOR_ADMIN)

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

            // ---- about / brand ----
            Card(colors = CardDefaults.cardColors()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.alazhartex_logo),
                            // decorative — the company name text sits below it
                            contentDescription = null,
                            modifier = Modifier.height(88.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Text(stringResource(R.string.brand_company), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.brand_tagline), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_offline), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.about_copyright), style = MaterialTheme.typography.labelSmall)
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
                        withContext(Dispatchers.IO) {
                            app.container.exportImportManager.safetyBackupAndReplace(current, db)
                            // The file is replaced atomically; reload from disk so the rest of the app
                            // sees the imported data without a process restart. If the rebuild fails,
                            // the old instance is kept and the next cold start loads the imported file.
                            runCatching { com.alazhartex.stock.data.local.DbProvider.refresh(context) }
                                .onFailure { android.util.Log.w("SettingsScreen", "refresh after import failed", it) }
                        }
                        importStage = null
                        importSidecar = null
                        importPreview = null
                        snackbar.showSnackbar(context.getString(R.string.toast_import_done))
                        AlAzharNotifier.notify(context, AlAzharNotifier.ID_IMPORT, context.getString(R.string.notif_import_title), context.getString(R.string.notif_import_body))
                    }
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = { TextButton(onClick = { importPreview = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    summary: String,
    status: String,
    granted: Boolean,
    actionLabel: String,
    actionVisible: Boolean,
    onAction: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        if (actionVisible) {
            TextButton(onClick = onAction, modifier = Modifier.testTag("${'$'}{testTag}_grant")) {
                Text(actionLabel)
            }
        }
    }
}
