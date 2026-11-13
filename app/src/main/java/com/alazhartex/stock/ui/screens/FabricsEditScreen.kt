package com.alazhartex.stock.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.ui.LocalSnackbar
import com.alazhartex.stock.ui.components.CustomHexRow
import com.alazhartex.stock.ui.components.ColourSwatch
import com.alazhartex.stock.ui.components.LoadingBox
import com.alazhartex.stock.ui.components.SwatchSize
import com.alazhartex.stock.ui.components.paletteContentDescription
import com.alazhartex.stock.ui.components.rememberAnySwatch
import com.alazhartex.stock.ui.integerString
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Screens that edit the master catalogue (delta contract §12). ADMIN writes; KEEPER add-only; SALES_MANAGER read-only. */
private enum class FabricsTab { FABRICS, TYPES, COLOURS }

private data class CustomFamily(val id: String, val nameAr: String, val nameEn: String, val hueAnchor: Int)

private val PINNED_FAMILIES: List<Pair<String, Int>> = listOf(
    "cotton" to 0,
    "linen" to 20,
    "wool" to 45,
    "silk" to 90,
    "polyester" to 150,
    "viscose" to 190,
    "denim" to 220,
    "velvet" to 270,
    "lycra" to 300,
    "oxford" to 330,
    "poplin" to 15,
)

private fun parseCustomFamilies(raw: String?): List<CustomFamily> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            CustomFamily(item.getString("id"), item.getString("name_ar"), item.getString("name_en"), item.getInt("hue_anchor"))
        }
    }.getOrDefault(emptyList())
}

private fun serializeCustomFamilies(families: List<CustomFamily>): String {
    val array = JSONArray()
    families.forEach { family ->
        array.put(
            JSONObject()
                .put("id", family.id)
                .put("name_ar", family.nameAr)
                .put("name_en", family.nameEn)
                .put("hue_anchor", family.hueAnchor),
        )
    }
    return array.toString()
}

private fun hsvArgb(hue: Float, saturation: Float, value: Float): Int {
    val hsv = android.graphics.Color.HSVToColor(floatArrayOf(((hue % 360f) + 360f) % 360f, saturation, value))
    return hsv
}

/** Ten-bit hex to Compose Color — helper for the decorative gradient strips. */
private fun stripColor(hueDegrees: Int, lightnessStep: Float): Color {
    val v = (0.55f + lightnessStep).coerceIn(0.25f, 0.95f)
    return Color(hsvArgb(hueDegrees.toFloat(), 0.55f, v))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FabricsEditScreen(app: AlAzharApp) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val refresh = rememberRefresh()
    val tick = refresh.tick.intValue

    var tab by rememberSaveable { mutableStateOf(FabricsTab.FABRICS.name) }
    val snapshot by produceState(initialValue = null as com.alazhartex.stock.data.repo.MastersSnapshot?, tick, rememberDbVersion()) {
        value = app.container.readRepository.masters()
    }
    val counts by produceState(
        initialValue = null as Pair<Map<String, Long>, Map<String, Long>>?,
        tick,
        rememberDbVersion(),
    ) {
        value = app.container.readRepository.mappingCountsByFabric() to app.container.readRepository.mappingCountsByColour()
    }
    val account by produceState(initialValue = null as com.alazhartex.stock.data.local.AccountEntity?, rememberDbVersion()) {
        value = app.container.readRepository.accountById(ACTOR_ADMIN)
    }
    val customFamilies by produceState(initialValue = emptyList<CustomFamily>(), tick, rememberDbVersion()) {
        value = parseCustomFamilies(app.container.readRepository.setting("custom_families"))
    }

    val role = account?.role ?: "ADMIN"
    val canWriteMasters = role == "ADMIN" || role == "KEEPER"
    val isAdmin = role == "ADMIN"

    var showAddFabric by remember { mutableStateOf(false) }
    var editFabric by remember { mutableStateOf<com.alazhartex.stock.data.local.FabricEntity?>(null) }
    var deleteFabric by remember { mutableStateOf<com.alazhartex.stock.data.local.FabricEntity?>(null) }
    var deleteFabricStep2 by remember { mutableStateOf(false) }
    var deleteFabricTyped by remember { mutableStateOf("") }
    var showAddColour by remember { mutableStateOf(false) }
    var editColour by remember { mutableStateOf<com.alazhartex.stock.data.local.ColourEntity?>(null) }
    var deleteColour by remember { mutableStateOf<com.alazhartex.stock.data.local.ColourEntity?>(null) }
    var showAssign by remember { mutableStateOf(false) }
    var showAddFamily by remember { mutableStateOf(false) }
    var deleteFamilyId by remember { mutableStateOf<String?>(null) }

    Scaffold { padding ->
        val data = snapshot
        if (data == null) {
            LoadingBox(stringResource(R.string.loading), Modifier.padding(padding))
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            if (!canWriteMasters) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(
                        text = stringResource(R.string.sales_masters_readonly_tip),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else if (!isAdmin) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(
                        text = stringResource(R.string.keeper_masters_readonly_tip),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                FabricsTab.entries.forEach { entry ->
                    FilterChip(
                        selected = tab == entry.name,
                        onClick = { tab = entry.name },
                        label = {
                            Text(stringResource(when (entry) {
                                FabricsTab.FABRICS -> R.string.tab_fabrics
                                FabricsTab.TYPES -> R.string.tab_types
                                FabricsTab.COLOURS -> R.string.tab_colours
                            }))
                        },
                    )
                }
            }
            when (FabricsTab.valueOf(tab)) {
                FabricsTab.FABRICS -> FabricsTabContent(
                    data = data,
                    mappingCounts = counts?.first ?: emptyMap(),
                    isAdmin = isAdmin,
                    canWrite = canWriteMasters,
                    onAdd = { showAddFabric = true },
                    onEdit = { editFabric = it },
                    onDelete = { target ->
                        deleteFabric = target
                        deleteFabricStep2 = false
                        deleteFabricTyped = ""
                    },
                )
                FabricsTab.TYPES -> TypesTabContent(
                    data = data,
                    customFamilies = customFamilies,
                    isAdmin = isAdmin,
                    onAddFamily = { showAddFamily = true },
                    onDeleteFamily = { deleteFamilyId = it },
                )
                FabricsTab.COLOURS -> ColoursTabContent(
                    data = data,
                    colourCounts = counts?.second ?: emptyMap(),
                    isAdmin = isAdmin,
                    canWrite = canWriteMasters,
                    onAdd = { showAddColour = true },
                    onEdit = { editColour = it },
                    onDelete = { deleteColour = it },
                    onAssign = { showAssign = true },
                )
            }
        }
    }

    // ----- dialogs -----
    if (showAddFabric) {
        FabricFormDialog(
            title = stringResource(R.string.masters_add_fabric),
            initial = null,
            customFamilies = customFamilies,
            onDismiss = { showAddFabric = false },
            onSave = { nameEn, nameAr, family, notes ->
                scope.launch {
                    val message = try {
                        app.container.mastersSettingsRepository.addFabric(ACTOR_ADMIN, nameEn, nameAr, family, UUID.randomUUID().toString(), notes)
                        context.getString(R.string.toast_fabric_added)
                    } catch (t: Throwable) {
                        snackbarFor(context, t)
                    }
                    showAddFabric = false
                    refresh.bump()
                    snackbar.showSnackbar(message)
                }
            },
        )
    }
    editFabric?.let { target ->
        FabricFormDialog(
            title = stringResource(R.string.dialog_edit_fabric),
            initial = target,
            customFamilies = customFamilies,
            onDismiss = { editFabric = null },
            onSave = { nameEn, nameAr, family, notes ->
                scope.launch {
                    val message = try {
                        app.container.mastersSettingsRepository.editFabric(ACTOR_ADMIN, target.id, nameEn, nameAr, family, UUID.randomUUID().toString(), notes)
                        context.getString(R.string.toast_saved)
                    } catch (t: Throwable) {
                        snackbarFor(context, t)
                    }
                    editFabric = null
                    refresh.bump()
                    snackbar.showSnackbar(message)
                }
            },
        )
    }
    deleteFabric?.let { target ->
        val mappings = counts?.first?.get(target.id) ?: 0L
        if (!deleteFabricStep2) {
            AlertDialog(
                onDismissRequest = { deleteFabric = null },
                title = { Text(stringResource(R.string.confirm_delete_fabric)) },
                text = { Text("${target.nameEn} · ${stringResource(R.string.fabric_delete_counts, mappings)}") },
                confirmButton = {
                    TextButton(enabled = mappings == 0L, onClick = { deleteFabricStep2 = true }) { Text(stringResource(R.string.action_confirm)) }
                },
                dismissButton = { TextButton(onClick = { deleteFabric = null }) { Text(stringResource(R.string.action_cancel)) } },
            )
        } else {
            AlertDialog(
                onDismissRequest = { deleteFabric = null },
                title = { Text(stringResource(R.string.confirm_type_delete_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${target.nameEn} · ${target.nameAr}")
                        OutlinedTextField(
                            value = deleteFabricTyped,
                            onValueChange = { deleteFabricTyped = it.take(16) },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    TextButton(enabled = deleteFabricTyped == "DELETE", onClick = {
                        scope.launch {
                            val message = try {
                                app.container.mastersSettingsRepository.deleteFabric(ACTOR_ADMIN, target.id, UUID.randomUUID().toString())
                                context.getString(R.string.toast_saved)
                            } catch (t: Throwable) {
                                snackbarFor(context, t)
                            }
                            deleteFabric = null
                            refresh.bump()
                            snackbar.showSnackbar(message)
                        }
                    }) { Text(stringResource(R.string.action_confirm)) }
                },
                dismissButton = { TextButton(onClick = { deleteFabric = null }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
    if (showAddColour) {
        ColourFormDialog(
            title = stringResource(R.string.masters_add_colour),
            initial = null,
            customFamilies = customFamilies,
            onDismiss = { showAddColour = false },
            onSave = { code, nameEn, nameAr, grade, family, keeperCode, isActive, customHex ->
                scope.launch {
                    val message = try {
                        app.container.mastersSettingsRepository.addColour(
                            ACTOR_ADMIN, code, nameEn, nameAr, grade, family, keeperCode, UUID.randomUUID().toString(), customHex,
                        )
                        context.getString(R.string.toast_colour_added)
                    } catch (t: Throwable) {
                        snackbarFor(context, t)
                    }
                    showAddColour = false
                    refresh.bump()
                    snackbar.showSnackbar(message)
                }
            },
        )
    }
    editColour?.let { target ->
        ColourFormDialog(
            title = stringResource(R.string.dialog_edit_colour),
            initial = target,
            customFamilies = customFamilies,
            onDismiss = { editColour = null },
            onSave = { _, nameEn, nameAr, grade, family, _, isActive, customHex ->
                scope.launch {
                    val message = try {
                        app.container.mastersSettingsRepository.editColour(
                            ACTOR_ADMIN, target.id, nameEn, nameAr, grade, family, isActive, UUID.randomUUID().toString(), customHex,
                        )
                        context.getString(R.string.toast_saved)
                    } catch (t: Throwable) {
                        snackbarFor(context, t)
                    }
                    editColour = null
                    refresh.bump()
                    snackbar.showSnackbar(message)
                }
            },
        )
    }
    val deleteColourUsage by produceState(initialValue = null as Triple<Long, Long, Long>?, deleteColour, rememberDbVersion()) {
        value = deleteColour?.let { app.container.readRepository.colourUsage(it.id) }
    }
    deleteColour?.let { target ->
        val mappings = deleteColourUsage?.first ?: (counts?.second?.get(target.id) ?: 0L)
        val movements = deleteColourUsage?.second ?: 0L
        val rolls = deleteColourUsage?.third ?: 0L
        AlertDialog(
            onDismissRequest = { deleteColour = null },
            title = { Text(stringResource(R.string.dialog_edit_colour)) },
            text = { Text("${target.code} — ${target.nameEn}\n${stringResource(R.string.colour_delete_counts, mappings, movements, rolls)}") },
            confirmButton = {
                TextButton(enabled = mappings == 0L && movements == 0L && rolls == 0L, onClick = {
                    scope.launch {
                        val message = try {
                            app.container.mastersSettingsRepository.deleteColour(ACTOR_ADMIN, target.id, UUID.randomUUID().toString())
                            context.getString(R.string.toast_saved)
                        } catch (t: Throwable) {
                            snackbarFor(context, t)
                        }
                        deleteColour = null
                        refresh.bump()
                        snackbar.showSnackbar(message)
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteColour = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    val assignSnapshot = snapshot
    if (showAssign && assignSnapshot != null) {
        AssignToFabricDialog(app, assignSnapshot.fabrics, onDismiss = { showAssign = false }) { fabric, colourIds ->
            scope.launch {
                val message = try {
                    val created = app.container.mastersSettingsRepository.bulkAssignMapping(
                        ACTOR_ADMIN, fabric.id, colourIds, UUID.randomUUID().toString(),
                    )
                    withContext(Dispatchers.IO) { com.alazhartex.stock.data.local.DbProvider.refresh(context) }
                    context.getString(R.string.bulk_assign_done, created)
                } catch (t: Throwable) {
                    if (t is com.alazhartex.stock.domain.model.InvariantViolationException && t.message?.contains("bulk_assign_nothing_to_create") == true) {
                        context.getString(R.string.bulk_assign_nothing)
                    } else {
                        snackbarFor(context, t)
                    }
                }
                showAssign = false
                refresh.bump()
                snackbar.showSnackbar(message)
            }
        }
    }
    if (showAddFamily) {
        AddCustomFamilyDialog(
            existingAnchors = PINNED_FAMILIES.map { it.second } + customFamilies.map { it.hueAnchor },
            onDismiss = { showAddFamily = false },
            onSave = { nameEn, nameAr, anchor ->
                scope.launch {
                    val updated = customFamilies + CustomFamily(
                        id = "custom_" + UUID.randomUUID().toString().take(8),
                        nameAr = nameAr,
                        nameEn = nameEn,
                        hueAnchor = anchor,
                    )
                    val message = try {
                        app.container.mastersSettingsRepository.setSetting(
                            ACTOR_ADMIN, "custom_families", serializeCustomFamilies(updated), UUID.randomUUID().toString(),
                        )
                        context.getString(R.string.custom_family_added)
                    } catch (t: Throwable) {
                        snackbarFor(context, t)
                    }
                    showAddFamily = false
                    refresh.bump()
                    snackbar.showSnackbar(message)
                }
            },
        )
    }
    deleteFamilyId?.let { targetId ->
        val targetName = customFamilies.firstOrNull { it.id == targetId }?.nameEn ?: targetId
        AlertDialog(
            onDismissRequest = { deleteFamilyId = null },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(targetName) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val updated = customFamilies.filterNot { it.id == targetId }
                        val message = try {
                            app.container.mastersSettingsRepository.setSetting(
                                ACTOR_ADMIN, "custom_families", serializeCustomFamilies(updated), UUID.randomUUID().toString(),
                            )
                            context.getString(R.string.toast_setting_saved)
                        } catch (t: Throwable) {
                            snackbarFor(context, t)
                        }
                        deleteFamilyId = null
                        refresh.bump()
                        snackbar.showSnackbar(message)
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteFamilyId = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

// ---------------------------------------------------------------- tabs

@Composable
private fun familyLabel(key: String): String = when (key) {
    "cotton" -> stringResource(R.string.family_cotton)
    "linen" -> stringResource(R.string.family_linen)
    "wool" -> stringResource(R.string.family_wool)
    "silk" -> stringResource(R.string.family_silk)
    "polyester" -> stringResource(R.string.family_polyester)
    "viscose" -> stringResource(R.string.family_viscose)
    "denim" -> stringResource(R.string.family_denim)
    "velvet" -> stringResource(R.string.family_velvet)
    "lycra" -> stringResource(R.string.family_lycra)
    "oxford" -> stringResource(R.string.family_oxford)
    "poplin" -> stringResource(R.string.family_poplin)
    else -> key
}

@Composable
private fun FabricsTabContent(
    data: com.alazhartex.stock.data.repo.MastersSnapshot,
    mappingCounts: Map<String, Long>,
    isAdmin: Boolean,
    canWrite: Boolean,
    onAdd: () -> Unit,
    onEdit: (com.alazhartex.stock.data.local.FabricEntity) -> Unit,
    onDelete: (com.alazhartex.stock.data.local.FabricEntity) -> Unit,
) {
    Column {
        if (canWrite) {
            Button(onClick = onAdd, modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  " + stringResource(R.string.masters_add_fabric))
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items = data.fabrics, key = { it.id }, contentType = { "fabric_row" }) { fabric ->
                Card(colors = CardDefaults.cardColors()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fabric.nameEn, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${fabric.nameAr} · ${familyLabel(fabric.family)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(R.string.label_mappings) + ": " + (mappingCounts[fabric.id] ?: 0L),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (isAdmin) {
                            IconButton(onClick = { onEdit(fabric) }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                            }
                            IconButton(onClick = { onDelete(fabric) }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypesTabContent(
    data: com.alazhartex.stock.data.repo.MastersSnapshot,
    customFamilies: List<CustomFamily>,
    isAdmin: Boolean,
    onAddFamily: () -> Unit,
    onDeleteFamily: (String) -> Unit,
) {
    val fabricsByFamily = remember(data.fabrics) { data.fabrics.groupingBy { it.family }.eachCount() }
    val coloursByFamily = remember(data.colours) { data.colours.groupingBy { it.family }.eachCount() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.pinned_families_note), style = MaterialTheme.typography.bodySmall)
        if (isAdmin) {
            Button(onClick = onAddFamily) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  " + stringResource(R.string.dialog_add_family))
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items = PINNED_FAMILIES, key = { it.first }, contentType = { "family_pinned" }) { (key, anchor) ->
                FamilyRow(
                    displayName = familyLabel(key),
                    hueAnchor = anchor,
                    pinned = true,
                    fabrics = fabricsByFamily[key] ?: 0,
                    colours = coloursByFamily[key] ?: 0,
                    swatchFamily = key,
                    onDelete = null,
                )
            }
            items(items = customFamilies, key = { it.id }, contentType = { "family_custom" }) { family ->
                FamilyRow(
                    displayName = "${family.nameEn} · ${family.nameAr}",
                    hueAnchor = family.hueAnchor,
                    pinned = false,
                    fabrics = fabricsByFamily[customFamilyKey(family)] ?: 0,
                    colours = coloursByFamily[customFamilyKey(family)] ?: 0,
                    swatchFamily = customFamilyKey(family),
                    onDelete = if (isAdmin) ({ onDeleteFamily(family.id) }) else null,
                )
            }
        }
    }
}

/** Stable Room-safe key for a custom family (§12: custom families are stored on-device, never in seed). */
private fun customFamilyKey(family: CustomFamily): String = "custom-" + family.hueAnchor + "-" + family.id.substringAfter("custom_")

@Composable
private fun FamilyRow(
    displayName: String,
    hueAnchor: Int,
    pinned: Boolean,
    fabrics: Int,
    colours: Int,
    swatchFamily: String,
    onDelete: (() -> Unit)?,
) {
    val stops = remember(hueAnchor) { com.alazhartex.stock.domain.palette.PaletteResolver.gradientStopHues(hueAnchor) }
    Card(colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (pinned) stringResource(R.string.label_family_pinned) else stringResource(R.string.custom_family_label),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (pinned) {
                    Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.label_family_pinned))
                } else if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                stops.forEachIndexed { index, hue ->
                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(stripColor(hue, (index - 2) * 0.09f)),
                    )
                }
            }
            Text(
                stringResource(R.string.family_members_count, fabrics, colours),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.label_samples), style = MaterialTheme.typography.bodySmall)
                listOf("A", "B", "C").forEach { grade ->
                    val sampleCode = (swatchFamily + "-" + grade).uppercase().replace(Regex("[^A-Z0-9-]"), "-").take(16)
                    val swatch = if (pinned) {
                        rememberAnySwatch(swatchFamily, grade, sampleCode)
                    } else {
                        remember(hueAnchor, grade, sampleCode) { com.alazhartex.stock.domain.palette.PaletteFallback.resolveAnchored(hueAnchor, grade, sampleCode) }
                    }
                    ColourSwatch(
                        value = swatch.argb,
                        onColour = swatch.onColour,
                        contentDescription = paletteContentDescription(sampleCode, displayName, displayName, grade),
                        size = SwatchSize.Small,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColoursTabContent(
    data: com.alazhartex.stock.data.repo.MastersSnapshot,
    colourCounts: Map<String, Long>,
    isAdmin: Boolean,
    canWrite: Boolean,
    onAdd: () -> Unit,
    onEdit: (com.alazhartex.stock.data.local.ColourEntity) -> Unit,
    onDelete: (com.alazhartex.stock.data.local.ColourEntity) -> Unit,
    onAssign: () -> Unit,
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            if (canWrite) {
                Button(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  " + stringResource(R.string.masters_add_colour))
                }
            }
            if (isAdmin) {
                OutlinedButton(onClick = onAssign) { Text(stringResource(R.string.assign_to_fabric)) }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items = data.colours, key = { it.id }, contentType = { "colour_row" }) { colour ->
                val swatch = rememberAnySwatch(colour.family, colour.grade, colour.code, colour.customHex)
                Card(colors = CardDefaults.cardColors()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColourSwatch(
                            value = swatch.argb,
                            onColour = swatch.onColour,
                            contentDescription = paletteContentDescription(colour.code, colour.nameEn, colour.nameAr, colour.grade),
                            size = SwatchSize.Small,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${colour.code} — ${colour.nameEn}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${colour.nameAr} · ${familyLabel(colour.family)} · ${if (colour.isActive) stringResource(R.string.label_active) else stringResource(R.string.label_inactive)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                stringResource(R.string.label_mappings) + ": " + (colourCounts[colour.id] ?: 0L),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (isAdmin) {
                            IconButton(onClick = { onEdit(colour) }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                            }
                            IconButton(onClick = { onDelete(colour) }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- dialogs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FamilyDropdownField(
    label: String,
    selectedKey: String,
    customFamilies: List<CustomFamily>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = remember(customFamilies) {
        PINNED_FAMILIES.map { it.first } + customFamilies.map { customFamilyKey(it) }
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            readOnly = true,
            value = familyLabel(selectedKey),
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { key ->
                DropdownMenuItem(text = { Text(familyLabel(key)) }, onClick = {
                    onSelect(key)
                    expanded = false
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FabricFormDialog(
    title: String,
    initial: com.alazhartex.stock.data.local.FabricEntity?,
    customFamilies: List<CustomFamily>,
    onDismiss: () -> Unit,
    onSave: (nameEn: String, nameAr: String, family: String, notes: String?) -> Unit,
) {
    var nameEn by remember { mutableStateOf(initial?.nameEn ?: "") }
    var nameAr by remember { mutableStateOf(initial?.nameAr ?: "") }
    var family by remember { mutableStateOf(initial?.family ?: "cotton") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    val valid = nameEn.trim().isNotEmpty() && nameAr.trim().isNotEmpty()

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(value = nameEn, onValueChange = { nameEn = it.take(80) }, label = { Text(stringResource(R.string.label_name_en)) }, singleLine = true)
                OutlinedTextField(value = nameAr, onValueChange = { nameAr = it.take(80) }, label = { Text(stringResource(R.string.label_name_ar)) }, singleLine = true)
                FamilyDropdownField(stringResource(R.string.label_family), family, customFamilies) { family = it }
                OutlinedTextField(value = notes, onValueChange = { notes = it.take(500) }, label = { Text(stringResource(R.string.fabric_notes_label)) }, minLines = 2, maxLines = 4)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = valid, onClick = { onSave(nameEn.trim(), nameAr.trim(), family, notes.trim().ifBlank { null }) }) { Text(stringResource(R.string.action_save)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColourFormDialog(
    title: String,
    initial: com.alazhartex.stock.data.local.ColourEntity?,
    customFamilies: List<CustomFamily>,
    onDismiss: () -> Unit,
    onSave: (code: String, nameEn: String, nameAr: String, grade: String, family: String, keeperCode: String?, isActive: Boolean, customHex: String?) -> Unit,
) {
    val isEdit = initial != null
    var code by remember { mutableStateOf(initial?.code ?: "") }
    var nameEn by remember { mutableStateOf(initial?.nameEn ?: "") }
    var nameAr by remember { mutableStateOf(initial?.nameAr ?: "") }
    var grade by remember { mutableStateOf(initial?.grade ?: "A") }
    var family by remember { mutableStateOf(initial?.family ?: "cotton") }
    var keeperCode by remember { mutableStateOf(initial?.keeperCode ?: "") }
    var isActive by remember { mutableStateOf(initial?.isActive ?: true) }
    var customHex by remember { mutableStateOf(initial?.customHex) }
    val codeUpper = code.trim().uppercase()
    val codeValid = codeUpper.matches(Regex("[A-Z0-9-]{1,16}"))
    val valid = codeValid && nameEn.trim().isNotEmpty() && nameAr.trim().isNotEmpty()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = codeUpper,
                    onValueChange = { code = it.uppercase().take(16) },
                    label = { Text(stringResource(R.string.label_code)) },
                    singleLine = true,
                    enabled = !isEdit,
                    supportingText = {
                        Text(stringResource(if (isEdit) R.string.colour_code_immutable else R.string.code_format_hint))
                    },
                )
                OutlinedTextField(value = nameEn, onValueChange = { nameEn = it.take(80) }, label = { Text(stringResource(R.string.label_name_en)) }, singleLine = true)
                OutlinedTextField(value = nameAr, onValueChange = { nameAr = it.take(80) }, label = { Text(stringResource(R.string.label_name_ar)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("A", "B", "C", "D").forEach { option ->
                        FilterChip(selected = grade == option, onClick = { grade = option }, label = { Text(option) })
                    }
                }
                FamilyDropdownField(stringResource(R.string.label_family), family, customFamilies) { family = it }
                OutlinedTextField(
                    value = keeperCode,
                    onValueChange = { keeperCode = it.take(16) },
                    label = { Text(stringResource(R.string.label_keeper_code)) },
                    singleLine = true,
                    enabled = !isEdit,
                    supportingText = { Text(stringResource(R.string.keeper_code_keeper_only)) },
                )
                CustomHexRow(customHex = customHex, onCustomHexChange = { customHex = it })
                if (isEdit) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.label_active))
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = valid, onClick = { onSave(codeUpper, nameEn.trim(), nameAr.trim(), grade, family, keeperCode.trim().ifEmpty { null }, isActive, customHex) }) { Text(stringResource(R.string.action_save)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomFamilyDialog(
    existingAnchors: List<Int>,
    onDismiss: () -> Unit,
    onSave: (nameEn: String, nameAr: String, anchor: Int) -> Unit,
) {
    var nameEn by remember { mutableStateOf("") }
    var nameAr by remember { mutableStateOf("") }
    val anchor = remember(existingAnchors) { com.alazhartex.stock.domain.palette.CustomHue.nextAnchor(existingAnchors) }
    val valid = nameEn.trim().isNotEmpty() && nameAr.trim().isNotEmpty()

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.dialog_add_family), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(value = nameEn, onValueChange = { nameEn = it.take(80) }, label = { Text(stringResource(R.string.label_name_en)) }, singleLine = true)
                OutlinedTextField(value = nameAr, onValueChange = { nameAr = it.take(80) }, label = { Text(stringResource(R.string.label_name_ar)) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(stripColor(anchor, 0f)),
                    )
                    Text(stringResource(R.string.custom_family_hue, anchor))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = valid, onClick = { onSave(nameEn.trim(), nameAr.trim(), anchor) }) { Text(stringResource(R.string.action_save)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignToFabricDialog(
    app: AlAzharApp,
    fabrics: List<com.alazhartex.stock.data.local.FabricEntity>,
    onDismiss: () -> Unit,
    onAssign: (com.alazhartex.stock.data.local.FabricEntity, List<String>) -> Unit,
) {
    var selectedFabric by remember { mutableStateOf<com.alazhartex.stock.data.local.FabricEntity?>(null) }
    var fabricExpanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val colours by produceState(initialValue = emptyList<com.alazhartex.stock.data.repo.ColourRowDto>(), rememberDbVersion()) {
        value = app.container.readRepository.colours()
    }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val filtered = remember(colours, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            colours
        } else {
            colours.filter { it.code.lowercase().contains(q) || it.nameEn.lowercase().contains(q) || it.nameAr.contains(query.trim()) }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(modifier = Modifier.fillMaxSize()) {
            Scaffold { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
                    Text(stringResource(R.string.assign_to_fabric), style = MaterialTheme.typography.titleLarge)
                    ExposedDropdownMenuBox(expanded = fabricExpanded, onExpandedChange = { fabricExpanded = !fabricExpanded }) {
                        OutlinedTextField(
                            readOnly = true,
                            value = selectedFabric?.nameEn ?: "",
                            onValueChange = {},
                            label = { Text(stringResource(R.string.assign_fabric_placeholder)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fabricExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                        )
                        DropdownMenu(expanded = fabricExpanded, onDismissRequest = { fabricExpanded = false }) {
                            fabrics.forEach { fabric ->
                                DropdownMenuItem(text = { Text("${fabric.nameEn} · ${fabric.nameAr}") }, onClick = {
                                    selectedFabric = fabric
                                    fabricExpanded = false
                                })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(60) },
                        label = { Text(stringResource(R.string.picker_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(
                            checked = filtered.isNotEmpty() && filtered.all { it.id in selectedIds },
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + filtered.map { it.id } else selectedIds - filtered.map { it.id }.toSet()
                            },
                        )
                        Text(stringResource(R.string.assign_selected_count, selectedIds.size), modifier = Modifier.weight(1f))
                        TextButton(onClick = { selectedIds = emptySet() }) { Text(stringResource(R.string.clear_selection)) }
                    }
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(items = filtered, key = { it.id }, contentType = { "assign_row" }) { colour ->
                            val swatch = rememberAnySwatch(colour.family, colour.grade, colour.code, colour.customHex)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Checkbox(checked = colour.id in selectedIds, onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + colour.id else selectedIds - colour.id
                                })
                                ColourSwatch(
                                    value = swatch.argb,
                                    onColour = swatch.onColour,
                                    contentDescription = paletteContentDescription(colour.code, colour.nameEn, colour.nameAr, colour.grade),
                                    size = SwatchSize.Small,
                                )
                                Text("${colour.code} — ${colour.nameEn}", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Button(
                            enabled = selectedFabric != null && selectedIds.isNotEmpty(),
                            onClick = { selectedFabric?.let { onAssign(it, selectedIds.toList()) } },
                        ) { Text(stringResource(R.string.assign_create)) }
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    }
                }
            }
        }
    }
}
