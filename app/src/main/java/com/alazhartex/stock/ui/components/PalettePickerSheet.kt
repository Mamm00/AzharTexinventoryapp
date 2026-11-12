package com.alazhartex.stock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.R
import com.alazhartex.stock.ui.LocalArabicIndicDigits
import com.alazhartex.stock.ui.integerString

/**
 * Palette picker (delta contract §11 v1.3.x), full-screen overlay.
 *
 * Layout: scrollable ROWS (not a grid) — each row is a tappable hit target carrying a checkbox
 * at its trailing end, matching the app's stock-release UX. Multi-select: any number of rows
 * can be ticked, the list scrolls, and a single Confirm commits.
 *
 * Filters: free-text search over code + names (AR/EN, case-insensitive), grade chips
 * (All/A–D) and family chips (All + pinned families; [fixedFamily] only pre-selects the chip, it does not lock it).
 * Ordering is pinned: grade ASC, then code ASC (documented artefact order).
 *
 * [availableByCode] shows live availability per colour code (rolls). Codes listed in
 * [selectableCodes] are mapped for this fabric at this warehouse; others may be TICKED ANYWAY
 * and created on the spot via [onAssignColours] ("Add selected colours to fabric here") — the
 * fix for the v1.3.0/1.3.1 report where opening the sheet for a fabric with incomplete mappings
 * left the keeper with nothing selectable and no way to add colours.
 *
 * Session-only state: search/filter/selection live in `remember` and die with the sheet.
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
    onAssignColours: ((List<com.alazhartex.stock.data.repo.ColourRowDto>) -> Unit)? = null,
) {
    val arabicIndic = LocalArabicIndicDigits.current
    var query by remember { mutableStateOf("") }
    var gradeFilter by remember { mutableStateOf("ALL") }
    var familyFilter by remember { mutableStateOf(fixedFamily ?: "ALL") }
    var checked by remember { mutableStateOf(setOf<String>()) }

    val colours by produceState(initialValue = emptyList<com.alazhartex.stock.data.repo.ColourRowDto>(), Unit) {
        value = app.container.readRepository.colours()
    }

    val families = remember(colours) { colours.map { it.family }.distinct().sorted() }
    val gradeOrder = mapOf("A" to 0, "B" to 1, "C" to 2, "D" to 3)
    val isMapped: (com.alazhartex.stock.data.repo.ColourRowDto) -> Boolean = {
        selectableCodes == null || selectableCodes.contains(it.code.trim().uppercase())
    }
    val filtered = remember(colours, query, gradeFilter, familyFilter) {
        val q = query.trim().lowercase()
        colours
            .filter { colour ->
                (gradeFilter == "ALL" || colour.grade.equals(gradeFilter, ignoreCase = true)) &&
                    (familyFilter == "ALL" || colour.family.equals(familyFilter, ignoreCase = true)) &&
                    (
                        q.isEmpty() ||
                            colour.code.lowercase().contains(q) ||
                            colour.nameEn.lowercase().contains(q) ||
                            colour.nameAr.contains(query.trim())
                        )
            }
            .sortedWith(compareBy({ gradeOrder[it.grade.uppercase()] ?: 9 }, { it.code }))
    }
    val checkedColours = remember(checked, colours) {
        colours.filter { it.code in checked }
    }
    val missing = remember(checkedColours, selectableCodes) {
        checkedColours.filter { !isMapped(it) }
    }
    val confirmable = remember(checkedColours, selectableCodes) {
        // Add-rolls wants exactly ONE mapped colour.
        if (checkedColours.size == 1) checkedColours.firstOrNull(isMapped) else null
    }
    val toggle: (String) -> Unit = { code -> checked = if (code in checked) checked - code else checked + code }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Text(
                    stringResource(R.string.picker_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.width(56.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(60) },
                    label = { Text(stringResource(R.string.picker_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                // Always offer the family chips; fixedFamily only pre-selects. When a fabric's
                // stored family doesn't match any colour family, hiding the row would leave the
                // keeper staring at an empty list with no way to widen the filter.
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = familyFilter == "ALL",
                        onClick = { familyFilter = "ALL" },
                        label = { Text(stringResource(R.string.picker_family_all)) },
                    )
                    families.forEach { family ->
                        FilterChip(
                            selected = familyFilter.equals(family, ignoreCase = true),
                            onClick = { familyFilter = family },
                            label = { Text(family) },
                        )
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(items = filtered, key = { it.id }, contentType = { "picker_row" }) { colour ->
                            PickerRow(
                                colour = colour,
                                mapped = isMapped(colour),
                                available = availableByCode[colour.code.trim().uppercase()],
                                checked = colour.code in checked,
                                arabicIndic = arabicIndic,
                                onToggle = { toggle(colour.code) },
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(
                    text = confirmable?.let {
                        stringResource(R.string.palette_footer_selected, it.code, it.nameEn)
                    } ?: stringResource(R.string.picker_footer_none),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                if (onAssignColours != null) {
                    TextButton(
                        enabled = onAssignColours != null && missing.isNotEmpty(),
                        onClick = { onAssignColours?.invoke(missing) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                R.string.picker_assign_selected,
                                integerString(missing.size.toLong(), arabicIndic),
                            ),
                        )
                    }
                }
                Button(
                    enabled = confirmable != null,
                    onClick = { confirmable?.let { onConfirm(it) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.picker_confirm)) }
            }
        }
    }
}

@Composable
private fun PickerRow(
    colour: com.alazhartex.stock.data.repo.ColourRowDto,
    mapped: Boolean,
    available: Long?,
    checked: Boolean,
    arabicIndic: Boolean,
    onToggle: () -> Unit,
) {
    val swatch = rememberAnySwatch(colour.family, colour.grade, colour.code, colour.customHex)
    val cd = paletteContentDescription(colour.code, colour.nameEn, colour.nameAr, colour.grade)
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("picker_row")
            .clip(shape)
            .clickable(onClick = onToggle)
            .background(
                if (checked) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .then(
                if (checked) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ColourSwatch(value = swatch.argb, onColour = swatch.onColour, contentDescription = cd, size = SwatchSize.Small)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                colour.code,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                colour.nameEn,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${colour.grade} · ${colour.family}",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (mapped) {
                    stringResource(R.string.picker_available, integerString(available ?: 0L, arabicIndic))
                } else {
                    stringResource(R.string.picker_not_mapped)
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}
