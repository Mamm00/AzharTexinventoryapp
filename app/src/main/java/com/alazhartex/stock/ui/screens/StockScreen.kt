package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.domain.idempotency.MutationOutcome
import com.alazhartex.stock.domain.model.MovementType
import com.alazhartex.stock.ui.LocalArabicIndicDigits
import com.alazhartex.stock.ui.LocalSnackbar
import com.alazhartex.stock.ui.components.ColourSwatch
import com.alazhartex.stock.ui.components.LoadingBox
import com.alazhartex.stock.ui.components.SwatchSize
import com.alazhartex.stock.ui.components.UtilisationGauge
import com.alazhartex.stock.ui.components.bucketLabelFor
import com.alazhartex.stock.ui.components.paletteContentDescription
import com.alazhartex.stock.ui.components.rememberSwatch
import com.alazhartex.stock.ui.integerString
import com.alazhartex.stock.ui.percentDisplay
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(app: AlAzharApp) {
    val arabicIndic = LocalArabicIndicDigits.current
    val snackbar = LocalSnackbar.current
    val refresh = rememberRefresh()
    val tick = refresh.tick.intValue
    val cards by produceState(initialValue = null as List<com.alazhartex.stock.data.repo.ShedCardDto>?, tick) {
        value = app.container.readRepository.shedCards()
    }
    var activeDialog by remember { mutableStateOf<MutationDialog?>(null) }

    Scaffold { padding ->
        val loaded = cards
        if (loaded == null) {
            LoadingBox(stringResource(R.string.loading), Modifier.padding(padding))
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = loaded, key = { it.shedId }, contentType = { "shed" }) { card ->
                val bucketLabel = bucketLabelFor(
                    card.bucket,
                    stringResource(R.string.bucket_green),
                    stringResource(R.string.bucket_amber),
                    stringResource(R.string.bucket_red),
                    stringResource(R.string.bucket_over_capacity),
                    stringResource(R.string.label_empty_no_rows),
                )
                Card(colors = CardDefaults.cardColors()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(card.nameEn, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                        Text(card.nameAr, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                        Text(
                            "${stringResource(R.string.label_active_rolls)}: ${integerString(card.activeRolls, arabicIndic)} / ${integerString(card.capacityRolls, arabicIndic)}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        )
                        UtilisationGauge(
                            label = stringResource(R.string.label_utilisation),
                            percentDisplay = percentDisplay(card.percentDisplay, arabicIndic),
                            bucket = card.bucket,
                            bucketLabel = bucketLabel,
                            percentFraction = (card.percentTwoDp.toFloat() / 100f),
                            contentDescriptionPrefix = stringResource(R.string.label_utilisation_for, card.shedId),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { activeDialog = MutationDialog(card.shedId, MutationKind.ADD) }) {
                                Text(stringResource(R.string.action_add_roll))
                            }
                            OutlinedButton(onClick = { activeDialog = MutationDialog(card.shedId, MutationKind.REMOVE) }) {
                                Text(stringResource(R.string.action_remove_roll))
                            }
                            TextButton(onClick = { activeDialog = MutationDialog(card.shedId, MutationKind.ADJUST) }) {
                                Text(stringResource(R.string.action_adjustment))
                            }
                        }
                    }
                }
            }
        }
    }

    activeDialog?.let { dialog ->
        MutationDialogHost(app, dialog, snackbar) {
            activeDialog = null
            refresh.bump()
        }
    }
}

private enum class MutationKind { ADD, REMOVE, ADJUST }

private data class MutationDialog(val shedId: String, val kind: MutationKind)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MutationDialogHost(app: AlAzharApp, dialog: MutationDialog, snackbar: SnackbarHostState, onClose: () -> Unit) {
    val context = LocalContext.current
    val arabicIndic = LocalArabicIndicDigits.current
    var lines by remember { mutableStateOf<List<com.alazhartex.stock.data.repo.InventoryLineDto>>(emptyList()) }
    LaunchedEffect(dialog) {
        lines = app.container.readRepository.inventoryLines(dialog.shedId).filter { it.isActive }
    }
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<com.alazhartex.stock.data.repo.InventoryLineDto?>(null) }
    var qtyText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var sign by remember { mutableStateOf(1) } // ADJUST only: +1 add, -1 remove
    var busy by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose) {
        Card(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val title = when (dialog.kind) {
                    MutationKind.ADD -> stringResource(R.string.action_add_roll)
                    MutationKind.REMOVE -> stringResource(R.string.action_remove_roll)
                    MutationKind.ADJUST -> stringResource(R.string.action_adjustment)
                }
                Text("$title — ${dialog.shedId}", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        readOnly = true,
                        value = selected?.let { "${it.fabricNameEn} · ${it.colourCode} · ${it.grade} (${integerString(it.closing, arabicIndic)})" }
                            ?: stringResource(R.string.label_fabric),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.label_fabric)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        lines.forEach { line ->
                            DropdownMenuItem(
                                text = { Text("${line.fabricNameEn} · ${line.colourCode} · ${line.grade} — ${line.closing}") },
                                leadingIcon = {
                                    val itemSw = rememberSwatch(line.family, line.grade, line.colourCode)
                                    ColourSwatch(
                                        value = itemSw.argb,
                                        onColour = itemSw.onColour,
                                        contentDescription = paletteContentDescription(line.colourCode, line.colourNameEn, line.colourNameAr, line.grade),
                                        size = SwatchSize.Small,
                                    )
                                },
                                onClick = {
                                    selected = line
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                if (dialog.kind == MutationKind.ADJUST) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.FilterChip(
                            selected = sign == 1,
                            onClick = { sign = 1 },
                            label = { Text("+") },
                        )
                        androidx.compose.material3.FilterChip(
                            selected = sign == -1,
                            onClick = { sign = -1 },
                            label = { Text("-") },
                        )
                    }
                }

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { ch -> ch.isDigit() }.take(9) },
                    label = { Text(stringResource(R.string.label_qty)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(280) },
                    label = { Text(stringResource(R.string.label_note)) },
                    singleLine = true,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            val qty = qtyText.toLongOrNull()
                            val target = selected
                            if (qty == null || qty <= 0L) {
                                return@Button
                            }
                            if (target == null) return@Button
                            busy = true
                            val key = UUID.randomUUID().toString()
                            app.container.applicationScope.launch {
                                val message = try {
                                    val repo = app.container.stockRepository
                                    val outcome = when (dialog.kind) {
                                        MutationKind.ADD ->
                                            repo.addRolls(ACTOR_ADMIN, target.mappingId, qty, MovementType.IN, note.ifBlank { "ui.add" }, key)
                                        MutationKind.REMOVE ->
                                            repo.removeRolls(ACTOR_ADMIN, target.mappingId, qty, MovementType.OUT, note.ifBlank { "ui.remove" }, key)
                                        MutationKind.ADJUST ->
                                            if (sign == 1) {
                                                repo.addRolls(ACTOR_ADMIN, target.mappingId, qty, MovementType.ADJUSTMENT, note.ifBlank { "ui.adjust.plus" }, key)
                                            } else {
                                                repo.removeRolls(ACTOR_ADMIN, target.mappingId, qty, MovementType.ADJUSTMENT, note.ifBlank { "ui.adjust.minus" }, key)
                                            }
                                    }
                                    when (outcome) {
                                        is MutationOutcome.AlreadyApplied -> context.getString(R.string.toast_saved)
                                        is MutationOutcome.Applied ->
                                            if (dialog.kind == MutationKind.ADJUST) {
                                                context.resources.getQuantityString(R.plurals.toast_adjustment_done, kotlin.math.abs(qty * sign).toInt(), qty * sign)
                                            } else {
                                                context.getString(R.string.toast_movement_saved)
                                            }
                                    }
                                } catch (t: Throwable) {
                                    snackbarFor(context, t)
                                }
                                snackbar.showSnackbar(message)
                                onClose()
                            }
                        },
                    ) { Text(stringResource(R.string.action_save)) }
                    TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        }
    }
}
