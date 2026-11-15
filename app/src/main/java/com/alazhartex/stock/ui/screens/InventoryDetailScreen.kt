package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.ui.LocalArabicIndicDigits
import com.alazhartex.stock.ui.components.ColourSwatch
import com.alazhartex.stock.ui.components.LoadingBox
import com.alazhartex.stock.ui.components.SwatchSize
import com.alazhartex.stock.ui.components.paletteContentDescription
import com.alazhartex.stock.ui.components.rememberAnySwatch
import com.alazhartex.stock.ui.integerString
import com.alazhartex.stock.ui.navigation.LocalAnimatedVisibilityScope
import com.alazhartex.stock.ui.navigation.LocalSharedTransitionScope
import com.alazhartex.stock.ui.shared.DetailCardScaffold

private data class InventoryDetailState(val loaded: Boolean, val line: com.alazhartex.stock.data.repo.InventoryLineDto?)

/**
 * v1.8.0 shared-bounds inventory detail surface. Read-only view of one warehouse↔fabric↔colour
 * mapping with its opening/closing ledger facts; roll mutations stay on the stock screen.
 */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun InventoryDetailScreen(app: AlAzharApp, mappingId: String, onBack: () -> Unit) {
    val arabicIndic = LocalArabicIndicDigits.current
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val state by produceState(initialValue = InventoryDetailState(false, null), rememberDbVersion()) {
        // Inventory lines live across sheds; read the admin view and match the mapping.
        value = runCatching {
            app.container.readRepository.inventoryLines().firstOrNull { it.mappingId == mappingId }
        }.fold(
            onSuccess = { InventoryDetailState(true, it) },
            onFailure = { InventoryDetailState(true, null) },
        )
    }
    val body: @Composable () -> Unit = {
        val current = state
        when {
            !current.loaded -> LoadingBox(stringResource(R.string.loading))
            current.line == null -> Text(stringResource(R.string.error_generic), style = MaterialTheme.typography.bodyLarge)
            else -> {
                val line = current.line
                val sw = rememberAnySwatch(line.family, line.grade, line.colourCode, line.customHex)
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ColourSwatch(
                            value = sw.argb,
                            onColour = sw.onColour,
                            contentDescription = paletteContentDescription(line.colourCode, line.colourNameEn, line.colourNameAr, line.grade),
                            size = SwatchSize.Large,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(line.fabricNameEn, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(line.fabricNameAr, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(line.colourCode + " · " + line.grade, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    DetailFact(stringResource(R.string.nav_stock), line.warehouseId)
                    DetailFact(stringResource(R.string.label_active_rolls), integerString(line.closing, arabicIndic))
                    DetailFact(stringResource(R.string.label_inactive_rolls), integerString(line.inactiveRolls, arabicIndic))
                    DetailFact(stringResource(R.string.label_opening), integerString(line.openingCache, arabicIndic))
                    DetailFact(stringResource(R.string.label_closing), integerString(line.closing, arabicIndic))
                    DetailFact(stringResource(R.string.label_family), line.family)
                }
            }
        }
    }
    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        DetailCardScaffold(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            sharedKey = "inventory-$mappingId",
            title = stringResource(R.string.inventory_detail_title),
            backContentDescription = stringResource(R.string.action_back),
            onBack = onBack,
        ) { body() }
    } else {
        Scaffold { padding ->
            Column(Modifier.padding(padding).padding(16.dp)) { body() }
        }
    }
}

@Composable
private fun DetailFact(label: String, value: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
        }
    }
}
