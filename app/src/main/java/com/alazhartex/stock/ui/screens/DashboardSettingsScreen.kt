package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.data.repo.DashboardDto
import com.alazhartex.stock.ui.LocalArabicIndicDigits
import com.alazhartex.stock.ui.LocalSnackbar
import com.alazhartex.stock.ui.integerString
import com.alazhartex.stock.ui.percentDisplay
import kotlinx.coroutines.launch

/**
 * Dashboard Settings ("Admin") — manual KPI overrides for the Dashboard.
 *
 * The "Enable Manual Overrides" toggle and the five values live in DataStore (Preferences),
 * never in Room and never in SharedPreferences. With the toggle OFF the Dashboard shows live
 * SQL sums; with it ON the Dashboard shows these manual values (per-field fallback to SQL).
 */
@Composable
fun DashboardSettingsScreen(app: AlAzharApp) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val arabicIndic = LocalArabicIndicDigits.current
    val scope = rememberCoroutineScope()
    val overrides by app.container.overrideStore.overrides.collectAsStateWithLifecycle(initialValue = null)

    // Field text state — seeded once from DataStore, edited locally, persisted on Save.
    var totalText by rememberSaveable { mutableStateOf<String?>(null) }
    var activeText by rememberSaveable { mutableStateOf<String?>(null) }
    var inactiveText by rememberSaveable { mutableStateOf<String?>(null) }
    var capacityText by rememberSaveable { mutableStateOf<String?>(null) }
    var utilisationText by rememberSaveable { mutableStateOf<String?>(null) }
    // null text + a loaded override snapshot == "not yet seeded" — seed from the first real DataStore emission.
    val loaded = overrides
    if (loaded != null && totalText == null && activeText == null && inactiveText == null && capacityText == null && utilisationText == null) {
        totalText = loaded.totalRolls?.toString().orEmpty()
        activeText = loaded.activeRolls?.toString().orEmpty()
        inactiveText = loaded.inactiveRolls?.toString().orEmpty()
        capacityText = loaded.capacityRolls?.toString().orEmpty()
        utilisationText = loaded.utilisationPercent?.let(::trimTrailingZeros).orEmpty()
    }

    val total = (totalText ?: "").toLongOrNull()
    val active = (activeText ?: "").toLongOrNull()
    val inactive = (inactiveText ?: "").toLongOrNull()
    val capacity = (capacityText ?: "").toLongOrNull()
    val utilisation = (utilisationText ?: "").toDoubleOrNull()

    val totalInvalid = (totalText ?: "").isNotEmpty() && total == null
    val activeInvalid = (activeText ?: "").isNotEmpty() && active == null
    val inactiveInvalid = (inactiveText ?: "").isNotEmpty() && inactive == null
    val capacityInvalid = (capacityText ?: "").isNotEmpty() && capacity == null
    val utilisationInvalid = (utilisationText ?: "").isNotEmpty() && (utilisation == null || utilisation < 0.0)
    val anyInvalid = totalInvalid || activeInvalid || inactiveInvalid || capacityInvalid || utilisationInvalid

    // Live SQL sums for reference — recomputed on every committed write.
    val live by produceState(initialValue = null as DashboardDto?, rememberDbVersion()) {
        value = runCatching { app.container.readRepository.dashboard() }.getOrNull()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.nav_dashboard_settings), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.overrides_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (loaded == null) {
                Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
                return@Scaffold
            }

            // ---- Toggle -------------------------------------------------------
            Card(colors = CardDefaults.cardColors()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.overrides_enable), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.overrides_enable_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = loaded.enabled,
                        onCheckedChange = { on ->
                            scope.launch {
                                app.container.overrideStore.setEnabled(on)
                                snackbar.showSnackbar(context.getString(R.string.toast_setting_saved))
                            }
                        },
                        modifier = Modifier.testTag("overrides_toggle"),
                    )
                }
            }

            // ---- Manual values --------------------------------------------------
            Card(colors = CardDefaults.cardColors()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.overrides_values_title), style = MaterialTheme.typography.titleMedium)
                    NumberField(
                        value = totalText.orEmpty(),
                        onValueChange = { totalText = it.filterDigits() },
                        label = stringResource(R.string.overrides_field_total),
                        invalid = totalInvalid,
                        testTag = "override_field_total",
                    )
                    NumberField(
                        value = activeText.orEmpty(),
                        onValueChange = { activeText = it.filterDigits() },
                        label = stringResource(R.string.overrides_field_active),
                        invalid = activeInvalid,
                        testTag = "override_field_active",
                    )
                    NumberField(
                        value = inactiveText.orEmpty(),
                        onValueChange = { inactiveText = it.filterDigits() },
                        label = stringResource(R.string.overrides_field_inactive),
                        invalid = inactiveInvalid,
                        testTag = "override_field_inactive",
                    )
                    NumberField(
                        value = capacityText.orEmpty(),
                        onValueChange = { capacityText = it.filterDigits() },
                        label = stringResource(R.string.overrides_field_capacity),
                        invalid = capacityInvalid,
                        testTag = "override_field_capacity",
                    )
                    NumberField(
                        value = utilisationText.orEmpty(),
                        onValueChange = { utilisationText = it.filterDecimal() },
                        label = stringResource(R.string.overrides_field_utilisation),
                        invalid = utilisationInvalid,
                        testTag = "override_field_utilisation",
                    )
                    Text(
                        stringResource(R.string.overrides_persistence_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    app.container.overrideStore.clearAll()
                                    totalText = ""; activeText = ""; inactiveText = ""; capacityText = ""; utilisationText = ""
                                    snackbar.showSnackbar(context.getString(R.string.overrides_cleared))
                                }
                            },
                            modifier = Modifier.testTag("overrides_reset"),
                        ) {
                            Text(stringResource(R.string.overrides_reset))
                        }
                        Button(
                            enabled = !anyInvalid,
                            onClick = {
                                scope.launch {
                                    app.container.overrideStore.setValues(
                                        totalRolls = total,
                                        activeRolls = active,
                                        inactiveRolls = inactive,
                                        capacityRolls = capacity,
                                        utilisationPercent = utilisation,
                                    )
                                    snackbar.showSnackbar(context.getString(R.string.overrides_saved))
                                }
                            },
                            modifier = Modifier.testTag("overrides_save"),
                        ) {
                            Text(stringResource(R.string.overrides_save))
                        }
                    }
                }
            }

            // ---- Live SQL snapshot (reference for the drift comparison) ---------
            Card(colors = CardDefaults.cardColors()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.overrides_live_title), style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    val snap = live
                    if (snap == null) {
                        Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LiveRow(stringResource(R.string.label_total_rolls), integerString(snap.totalRolls, arabicIndic))
                        LiveRow(stringResource(R.string.label_active_rolls), integerString(snap.activeRolls, arabicIndic))
                        LiveRow(stringResource(R.string.label_inactive_rolls), integerString(snap.inactiveRolls, arabicIndic))
                        LiveRow(stringResource(R.string.label_capacity), integerString(snap.totalCapacity, arabicIndic))
                        val pct = percentDisplay(snap.percentDisplay, arabicIndic)
                        LiveRow(stringResource(R.string.label_utilisation), if (pct == "—") pct else "$pct%")
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    invalid: Boolean,
    testTag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = {
            Text(
                if (invalid) stringResource(R.string.overrides_field_invalid) else stringResource(R.string.overrides_field_blank_hint),
                color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
}

@Composable
private fun LiveRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun String.filterDigits(): String = filter { it.isDigit() }.take(12)

private fun String.filterDecimal(): String {
    val filtered = filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot == -1) {
        filtered.take(6)
    } else {
        filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "").take(1)
    }
}

private fun trimTrailingZeros(value: Double): String =
    java.math.BigDecimal(value.toString()).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
