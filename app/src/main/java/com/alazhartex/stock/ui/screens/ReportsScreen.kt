package com.alazhartex.stock.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.data.csv.CsvExport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private data class ReportSpec(val key: String, val titleRes: Int)

private val REPORTS = listOf(
    ReportSpec("stock", R.string.label_report_stock),
    ReportSpec("inventory", R.string.label_report_inventory),
    ReportSpec("movements", R.string.label_report_movements),
    ReportSpec("audit", R.string.label_report_audit),
    ReportSpec("drift", R.string.label_report_drift),
)

@Composable
fun ReportsScreen(app: AlAzharApp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = com.alazhartex.stock.ui.LocalSnackbar.current
    val settings by produceState(initialValue = emptyMap(), rememberDbVersion()) {
        value = app.container.readRepository.settingsAll().associate { it.key to it.value }
    }
    var includeLetterhead by remember { mutableStateOf(true) }
    var preview by remember { mutableStateOf<Pair<String, String>?>(null) } // reportKey to csvText
    var busy by remember { mutableStateOf<String?>(null) }

    val legalName = settings["profile.legal_name"].orEmpty()
    val letterheadReady = legalName.isNotBlank()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                            Text(stringResource(R.string.label_letterhead), style = MaterialTheme.typography.titleMedium)
                            Switch(checked = includeLetterhead, onCheckedChange = { includeLetterhead = it })
                        }
                        if (includeLetterhead && letterheadReady) {
                            Text(legalName, style = MaterialTheme.typography.headlineSmall)
                            listOf("profile.address", "profile.phone", "profile.email", "profile.tax_id", "profile.logo_uri")
                                .mapNotNull { settings[it]?.takeIf { v -> v.isNotBlank() } }
                                .forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        } else {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                            Text(stringResource(R.string.empty_letterhead), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(stringResource(R.string.label_include_letterhead), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item { Text(stringResource(R.string.label_report_choose), style = MaterialTheme.typography.titleMedium) }
            items(count = REPORTS.size, key = { REPORTS[it].key }, contentType = { "report" }) { index ->
                val spec = REPORTS[index]
                Card(colors = CardDefaults.cardColors()) {
                    Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(spec.titleRes), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = {
                            scope.launch {
                                busy = spec.key
                                try {
                                    preview = spec.key to buildCsv(app, spec.key)
                                } catch (t: Throwable) {
                                    snackbar.showSnackbar(snackbarFor(context, t))
                                } finally {
                                    busy = null
                                }
                            }
                        }) { Text(stringResource(R.string.action_generate)) }
                        Button(enabled = busy == null, onClick = {
                            scope.launch {
                                busy = spec.key
                                try {
                                    val file = writeCsvFile(app, spec.key, buildCsv(app, spec.key))
                                    shareCsv(context, file)
                                    snackbar.showSnackbar(context.getString(R.string.toast_export_done, file.name))
                                } catch (t: Throwable) {
                                    snackbar.showSnackbar(context.getString(R.string.toast_export_failed))
                                } finally {
                                    busy = null
                                }
                            }
                        }) { Text(stringResource(R.string.action_share)) }
                    }
                }
            }
        }
    }

    preview?.let { (key, text) ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(key) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (includeLetterhead && letterheadReady) {
                        Text(legalName, style = MaterialTheme.typography.titleMedium)
                        Text("— · — · — · —", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(text.take(6000), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { preview = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private suspend fun buildCsv(app: AlAzharApp, key: String): String {
    val read = app.container.readRepository
    return when (key) {
        "stock" -> CsvExport.stock(read.shedCards())
        "inventory" -> CsvExport.inventory(read.inventoryLines())
        "movements" -> CsvExport.movements(read.movements(limit = Int.MAX_VALUE))
        "audit" -> CsvExport.audit(read.audit(limit = Int.MAX_VALUE))
        "drift" -> CsvExport.drift(read.driftRows())
        else -> error("unknown report $key")
    }
}

private fun writeCsvFile(app: AlAzharApp, key: String, csv: String): File {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val dir = File(app.applicationContext.filesDir, "exports").apply { mkdirs() }
    val file = File(dir, "alazhartex-$key-$stamp.csv")
    file.writeText(csv, Charsets.UTF_8)
    return file
}

private fun shareCsv(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "com.alazhartex.stock.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, file.name))
}
