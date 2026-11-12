package com.alazhartex.stock.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.ui.LocalArabicIndicDigits
import com.alazhartex.stock.ui.integerString

/**
 * Palette picker (delta contract §11), full-screen dialog.
 *
 * Filters: free-text search over code + names (AR/EN, case-insensitive), single-select grade chips
 * (All/A–D) and family chips (All + pinned families, pre-filtered when [fixedFamily] is given).
 * Ordering is pinned: grade ASC, then code ASC (documented artefact order).
 * Session-only state: search/filter/selection live in `remember` and die with the sheet — nothing
 * is persisted to the database.
 *
 * [availableByCode] shows live availability per colour code (rolls). Cells whose code is absent
 * from [selectableCodes] render disabled (alpha 0.4, tap ignored) — a mapped-pair guard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalettePickerSheet(
    app: AlAzharApp,
    fixedFamily: String?,
    selectableCodes: Set<String>?,
    availableByCode: Map<String, Long>,
    onDismiss: () -> Unit,
    onConfirm: (com.alazhartex.stock.data.repo.ColourRowDto) -> Unit,
) {
    val arabicIndic = LocalArabicIndicDigits.current
    var query by remember { mutableStateOf("") }
    var gradeFilter by remember { mutableStateOf("ALL") }
    var familyFilter by remember { mutableStateOf(fixedFamily ?: "ALL") }
    var selectedCode by remember { mutableStateOf<String?>(null) }

    val colours by produceState(initialValue = emptyList<com.alazhartex.stock.data.repo.ColourRowDto>(), Unit) {
        value = app.container.readRepository.colours()
    }

    val families = remember(colours) { colours.map { it.family }.distinct().sorted() }
    val gradeOrder = mapOf("A" to 0, "B" to 1, "C" to 2, "D" to 3)
    val filtered = remember(colours, query, gradeFilter, familyFilter) {
        val q = query.trim().lowercase()
        colours
            .filter { colour ->
                (gradeFilter == "ALL" || colour.grade == gradeFilter) &&
                    (familyFilter == "ALL" || colour.family == familyFilter) &&
                    (
                        q.isEmpty() ||
                            colour.code.lowercase().contains(q) ||
                            colour.nameEn.lowercase().contains(q) ||
                            colour.nameAr.contains(query.trim())
                        )
            }
            .sortedWith(compareBy({ gradeOrder[it.grade] ?: 9 }, { it.code }))
    }
    val selected = colours.firstOrNull { it.code == selectedCode }

    // Full-screen Box overlay instead of a second Dialog window. The picker previously hosted a
    // nested Dialog under the calling screen's Dialog, which is reported to crash on some devices
    // when the wheel is opened from the stock "Add roll(s)" sheet. Surface fills the host window.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
            Scaffold(
                topBar = {
                    androidx.compose.material3.TopAppBar(
                        title = { Text(stringResource(R.string.picker_title)) },
                        navigationIcon = {
                            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                        },
                    )
                },
                bottomBar = {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            text = selected?.let {
                                stringResource(R.string.palette_footer_selected, it.code, it.nameEn)
                            } ?: stringResource(R.string.picker_footer_none),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Button(
                            enabled = selected != null,
                            onClick = { selected?.let { onConfirm(it) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.picker_confirm)) }
                    }
                },
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(60) },
                        label = { Text(stringResource(R.string.picker_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = gradeFilter == "ALL",
                            onClick = { gradeFilter = "ALL" },
                            label = { Text(stringResource(R.string.picker_grade_all)) },
                        )
                        listOf("A", "B", "C", "D").forEach { grade ->
                            FilterChip(
                                selected = gradeFilter == grade,
                                onClick = { gradeFilter = grade },
                                label = { Text(grade) },
                            )
                        }
                    }
                    if (fixedFamily == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = familyFilter == "ALL",
                                onClick = { familyFilter = "ALL" },
                                label = { Text(stringResource(R.string.picker_family_all)) },
                            )
                            families.forEach { family ->
                                FilterChip(
                                    selected = familyFilter == family,
                                    onClick = { familyFilter = family },
                                    label = { Text(family) },
                                )
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(stringResource(R.string.picker_empty), style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = {
                                query = ""
                                gradeFilter = "ALL"
                                familyFilter = fixedFamily ?: "ALL"
                            }) { Text(stringResource(R.string.picker_clear_filters)) }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 108.dp),
                            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(items = filtered, key = { it.id }, contentType = { "picker_cell" }) { colour ->
                                val available = availableByCode[colour.code]
                                val selectable = selectableCodes == null || colour.code in selectableCodes
                                PickerCell(
                                    colour = colour,
                                    available = available,
                                    selected = selectedCode == colour.code,
                                    selectable = selectable,
                                    arabicIndic = arabicIndic,
                                    onClick = { if (selectable) selectedCode = colour.code },
                                )
                            }
                        }
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerCell(
    colour: com.alazhartex.stock.data.repo.ColourRowDto,
    available: Long?,
    selected: Boolean,
    selectable: Boolean,
    arabicIndic: Boolean,
    onClick: () -> Unit,
) {
    val swatch = rememberAnySwatch(colour.family, colour.grade, colour.code, colour.customHex)
    val cd = paletteContentDescription(colour.code, colour.nameEn, colour.nameAr, colour.grade)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (selectable) 1f else 0.38f),
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ColourSwatch(value = swatch.argb, onColour = swatch.onColour, contentDescription = cd, size = SwatchSize.Small)
                Text(colour.code, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
            Text(
                text = colour.nameEn,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.picker_available, integerString(available ?: 0L, arabicIndic)),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
