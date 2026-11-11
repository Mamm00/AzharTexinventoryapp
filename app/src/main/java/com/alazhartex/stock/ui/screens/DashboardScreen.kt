package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.ui.LocalArabicIndicDigits
import com.alazhartex.stock.ui.components.ColourSwatch
import com.alazhartex.stock.ui.components.LabeledRow
import com.alazhartex.stock.ui.components.LoadingBox
import com.alazhartex.stock.ui.components.SwatchSize
import com.alazhartex.stock.ui.components.UtilisationGauge
import com.alazhartex.stock.ui.components.bucketLabelFor
import com.alazhartex.stock.ui.components.paletteContentDescription
import com.alazhartex.stock.ui.components.rememberAnySwatch
import com.alazhartex.stock.ui.integerString
import com.alazhartex.stock.ui.percentDisplay

@Composable
fun DashboardScreen(app: AlAzharApp) {
    val arabicIndic = LocalArabicIndicDigits.current
    val dashboard by produceState(initialValue = null as com.alazhartex.stock.data.repo.DashboardDto?, Unit) {
        value = app.container.readRepository.dashboard()
    }
    val wh625 by produceState(initialValue = null as com.alazhartex.stock.data.repo.ShedCardDto?, Unit) {
        value = app.container.readRepository.shedCards().firstOrNull { it.shedId == "wh-625" }
    }

    Scaffold { padding ->
        val data = dashboard
        if (data == null) {
            LoadingBox(stringResource(R.string.loading), Modifier.padding(padding))
            return@Scaffold
        }
        val bucketLabel = bucketLabelFor(
            data.bucket,
            stringResource(R.string.bucket_green),
            stringResource(R.string.bucket_amber),
            stringResource(R.string.bucket_red),
            stringResource(R.string.bucket_over_capacity),
            stringResource(R.string.label_empty_no_rows),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.nav_dashboard), style = MaterialTheme.typography.headlineSmall)
                        LabeledRow(stringResource(R.string.label_total_rolls), integerString(data.totalActiveRolls, arabicIndic))
                        LabeledRow(stringResource(R.string.label_capacity), integerString(data.totalCapacity, arabicIndic))
                        UtilisationGauge(
                            label = stringResource(R.string.label_utilisation),
                            percentDisplay = percentDisplay(data.percentDisplay, arabicIndic),
                            bucket = data.bucket,
                            bucketLabel = bucketLabel,
                            percentFraction = ((data.percentDisplay?.toFloatOrNull() ?: 0f) / 100f),
                            contentDescriptionPrefix = stringResource(R.string.app_name),
                        )
                        LabeledRow(stringResource(R.string.label_inactive_rolls), integerString(data.inactiveRolls, arabicIndic))
                        LabeledRow(stringResource(R.string.label_raw_rolls), integerString(data.rawRolls, arabicIndic))
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.label_top_colours), style = MaterialTheme.typography.titleMedium)
                        data.topColours.forEach { tc ->
                            val tcSw = rememberAnySwatch(tc.family, tc.grade, tc.colourCode)
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ColourSwatch(
                                    value = tcSw.argb,
                                    onColour = tcSw.onColour,
                                    contentDescription = paletteContentDescription(tc.colourCode, tc.colourNameEn, tc.colourNameAr, tc.grade),
                                    size = SwatchSize.Small,
                                    grade = tc.grade,
                                )
                                Text(
                                    "${tc.colourCode} — ${tc.colourNameEn}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(integerString(tc.rolls, arabicIndic), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            item {
                val driftOk = data.driftViolations == 0
                Card(colors = CardDefaults.cardColors(containerColor = if (driftOk) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.label_drift_indicator), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (driftOk) stringResource(R.string.label_drift_ok) else "${stringResource(R.string.label_drift_detected)}: ${data.driftViolations}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            val scaleTest = wh625
            if (scaleTest != null) {
                item {
                    Card(colors = CardDefaults.cardColors()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.label_wh625_scale), style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${scaleTest.shedId} — ${scaleTest.percentDisplay ?: "—"}%",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text(
                                "${integerString(scaleTest.activeRolls, arabicIndic)} / ${integerString(scaleTest.capacityRolls, arabicIndic)} ${stringResource(R.string.label_active_rolls)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.label_top_sheds), style = MaterialTheme.typography.titleMedium)
            }
            items(items = data.topSheds, key = { it.shedId }, contentType = { "shed" }) { shed ->
                Card(colors = CardDefaults.cardColors()) {
                    Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(shed.nameEn, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(integerString(shed.activeRolls, arabicIndic), style = MaterialTheme.typography.bodyLarge)
                        Text("${shed.percentDisplay ?: "—"}%", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
