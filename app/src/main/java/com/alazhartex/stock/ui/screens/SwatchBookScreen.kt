package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.ui.LocalArabicIndicDigits
import com.alazhartex.stock.ui.LocalSnackbar
import com.alazhartex.stock.ui.components.LoadingBox
import com.alazhartex.stock.ui.components.PinkedSwatch
import com.alazhartex.stock.ui.components.paletteContentDescription
import com.alazhartex.stock.ui.components.rememberAnySwatch
import com.alazhartex.stock.ui.integerString
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Swatch book (v1.3) — the digital twin of the physical fabric sample cards.
 *
 * Per warehouse, one card per fabric: the card header is the fabric identity (EN/AR name, family,
 * free-text notes), and the body is a cut-pattern grid of EVERY active colour of the fabric's
 * family — each with its closing quantity at this warehouse, exactly like flipping through the
 * sample card in the store. Unstocked family colours render dimmed with a dash, so the keeper sees
 * the complete palette, not just stocked SKUs.
 *
 * "Load full palette" maps the fabric to every missing family colour in this warehouse with one
 * bulk, audited transaction (zero quantities) — loading a fabric now auto-generates the full card.
 */
@Composable
fun SwatchBookScreen(app: AlAzharApp) {
    val arabicIndic = LocalArabicIndicDigits.current
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refresh = rememberRefresh()
    val tick = refresh.tick.intValue

    val warehouses by produceState(initialValue = null as List<com.alazhartex.stock.data.local.WarehouseEntity>?, tick) {
        value = app.container.readRepository.masters().warehouses
    }
    val fabrics by produceState(initialValue = null as List<com.alazhartex.stock.data.local.FabricEntity>?, tick) {
        value = app.container.readRepository.masters().fabrics
    }
    val colours by produceState(initialValue = null as List<com.alazhartex.stock.data.repo.ColourRowDto>?, tick) {
        value = app.container.readRepository.colours()
    }

    var warehouseId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAllFabrics by rememberSaveable { mutableStateOf(false) }

    val loadedWarehouses = warehouses
    LaunchedEffect(loadedWarehouses) {
        if (warehouseId == null && !loadedWarehouses.isNullOrEmpty()) {
            warehouseId = loadedWarehouses.first().id
        }
    }

    val lines by produceState(initialValue = null as List<com.alazhartex.stock.data.repo.InventoryLineDto>?, warehouseId, tick) {
        value = warehouseId?.let { app.container.readRepository.inventoryLines(it) } ?: emptyList()
    }

    var loadTarget by remember { mutableStateOf<com.alazhartex.stock.data.local.FabricEntity?>(null) }
    var busy by remember { mutableStateOf(false) }

    Scaffold { padding ->
        val whList = warehouses
        val fabricList = fabrics
        val colourList = colours
        val lineList = lines
        if (whList == null || fabricList == null || colourList == null || lineList == null) {
            LoadingBox(stringResource(R.string.loading), Modifier.padding(padding))
            return@Scaffold
        }
        if (whList.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.swatchbook_no_warehouses),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }

        val selected = whList.firstOrNull { it.id == warehouseId } ?: whList.first()
        val activeLines = lineList.filter { it.isActive }
        val byFabricEn = activeLines.groupBy { it.fabricNameEn }

        // Cards: mapped fabrics by default; every fabric when the toggle is on.
        val cards = fabricList
            .filter { showAllFabrics || (byFabricEn[it.nameEn]?.isNotEmpty() == true) }
            .sortedBy { it.nameEn.lowercase() }
        val totalMappedRolls = activeLines.sumOf { it.closing }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.swatchbook_title), style = MaterialTheme.typography.titleLarge)
                // Warehouse selector — every warehouse gets its own sample card deck.
                Text(
                    stringResource(R.string.swatchbook_warehouse),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    whList.forEach { wh ->
                        FilterChip(
                            selected = wh.id == selected.id,
                            onClick = { warehouseId = wh.id },
                            label = { Text("${wh.nameEn} · ${wh.nameAr}") },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.swatchbook_total_rolls, integerString(totalMappedRolls, arabicIndic)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilterChip(
                        selected = showAllFabrics,
                        onClick = { showAllFabrics = !showAllFabrics },
                        label = {
                            val labelRes = if (showAllFabrics) R.string.swatchbook_mapped_only else R.string.swatchbook_show_all_fabrics
                            Text(stringResource(labelRes))
                        },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (cards.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.swatchbook_empty_no_mappings),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 32.dp),
                        )
                    }
                }
                items(items = cards, key = { it.id }, contentType = { "fabric_card" }) { fabric ->
                    FabricSampleCard(
                        fabric = fabric,
                        colours = colourList,
                        linesForFabric = byFabricEn[fabric.nameEn].orEmpty(),
                        arabicIndic = arabicIndic,
                        onLoadPalette = { loadTarget = fabric },
                    )
                }
                item { Box(modifier = Modifier.padding(bottom = 16.dp)) }
            }
        }
    }

    // Confirm → one audited bulk transaction.
    loadTarget?.let { fabric ->
        val familyColours = colours.orEmpty()
            .filter { it.isActive && it.family.equals(fabric.family, ignoreCase = true) }
        val mappedCodes = lines.orEmpty()
            .filter { it.isActive && it.fabricNameEn == fabric.nameEn }
            .map { it.colourCode }
            .toSet()
        val missing = familyColours.count { it.code !in mappedCodes }
        val wh = warehouses.orEmpty().firstOrNull { it.id == warehouseId }
        AlertDialog(
            onDismissRequest = { if (!busy) loadTarget = null },
            title = { Text(stringResource(R.string.swatchbook_load_confirm_title)) },
            text = {
                val body = stringResource(
                    R.string.swatchbook_load_confirm_body,
                    integerString(missing.toLong(), arabicIndic),
                    fabric.nameEn,
                    wh?.nameEn ?: "",
                )
                Text(body)
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val warehouseIdOrNull = wh?.id
                        if (warehouseIdOrNull == null) {
                            return@TextButton
                        }
                        busy = true
                        scope.launch {
                            val message = try {
                                val created = app.container.mastersSettingsRepository.loadFamilyPalette(
                                    ACTOR_ADMIN, warehouseIdOrNull, fabric.id, UUID.randomUUID().toString(),
                                )
                                if (created > 0) {
                                    context.getString(R.string.swatchbook_load_done, integerString(created.toLong(), arabicIndic))
                                }
                                else context.getString(R.string.swatchbook_load_nothing)
                            } catch (t: Throwable) {
                                snackbarFor(context, t)
                            }
                            busy = false
                            loadTarget = null
                            refresh.bump()
                            snackbar.showSnackbar(message)
                        }
                    },
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { loadTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun FabricSampleCard(
    fabric: com.alazhartex.stock.data.local.FabricEntity,
    colours: List<com.alazhartex.stock.data.repo.ColourRowDto>,
    linesForFabric: List<com.alazhartex.stock.data.repo.InventoryLineDto>,
    arabicIndic: Boolean,
    onLoadPalette: () -> Unit,
) {
    val gradeOrder = mapOf("A" to 0, "B" to 1, "C" to 2, "D" to 3)
    val familyColours = remember(colours, fabric.family) {
        colours
            .filter { it.isActive && it.family.equals(fabric.family, ignoreCase = true) }
            .sortedWith(compareBy({ gradeOrder[it.grade] ?: 9 }, { it.code }))
    }
    val qtyByCode = remember(linesForFabric) { linesForFabric.associate { it.colourCode to it.closing } }
    val mapped = familyColours.count { qtyByCode.containsKey(it.code) }
    val total = familyColours.sumOf { qtyByCode[it.code] ?: 0L }
    val missing = familyColours.size - mapped

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header — the printed fabric name on the physical card.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        fabric.nameEn,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        fabric.nameAr,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringResource(R.string.swatchbook_family, fabric.family)) },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
            if (!fabric.notes.isNullOrBlank()) {
                Text(
                    "${stringResource(R.string.swatchbook_notes)}: ${fabric.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                stringResource(
                    R.string.swatchbook_mapped_count,
                    integerString(mapped.toLong(), arabicIndic),
                    integerString(familyColours.size.toLong(), arabicIndic),
                ),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                stringResource(R.string.swatchbook_total_rolls, integerString(total, arabicIndic)),
                style = MaterialTheme.typography.bodyMedium,
            )

            // The cut-pattern grid — every family colour, stocked or not.
            familyColours.chunked(3).forEach { rowColours ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowColours.forEach { colour ->
                        PaletteCell(
                            colour = colour,
                            qty = qtyByCode[colour.code],
                            arabicIndic = arabicIndic,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - rowColours.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }

            if (missing > 0) {
                Button(onClick = onLoadPalette, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text(stringResource(R.string.swatchbook_load_palette, integerString(missing.toLong(), arabicIndic)))
                }
            }
        }
    }
}

@Composable
private fun PaletteCell(
    colour: com.alazhartex.stock.data.repo.ColourRowDto,
    qty: Long?,
    arabicIndic: Boolean,
    modifier: Modifier = Modifier,
) {
    val arabic = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val swatch = rememberAnySwatch(colour.family, colour.grade, colour.code, colour.customHex)
    val cd = paletteContentDescription(colour.code, colour.nameEn, colour.nameAr, colour.grade)
    val stocked = qty != null
    Column(
        modifier = modifier.alpha(if (stocked) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            PinkedSwatch(argb = swatch.argb, contentDescription = cd)
            // Quantity bubble, like the keeper counting rolls on the physical card.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 2.dp, top = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = if (stocked) integerString(qty ?: 0L, arabicIndic) else stringResource(R.string.swatchbook_unmapped),
                    color = MaterialTheme.colorScheme.surface,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Text(
            text = if (arabic) colour.nameAr else colour.nameEn,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
        Text(colour.code, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
