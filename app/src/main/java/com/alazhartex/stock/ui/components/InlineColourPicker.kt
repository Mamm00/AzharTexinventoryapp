package com.alazhartex.stock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alazhartex.stock.R
import com.alazhartex.stock.ui.integerString

/**
 * Inline colour picker for the Add/Remove roll(s) dialog (v1.3.3).
 *
 * Deliberately SIMPLE and STATIC: a fixed-height list rendered directly inside the dialog — no
 * overlay, no popup, no Confirm button. One tap on a mapped row selects it (radio). A colour that
 * is in the master but not yet mapped to this fabric at this warehouse shows "Not in this fabric"
 * with an Add button; tapping Add creates the mapping on the spot (one transaction, one audit row)
 * and hands the row over as selected.
 *
 * Ordering is pinned: grade ASC (A→D), then code ASC. Session-only state (search text) dies with
 * the dialog.
 */
@Composable
fun InlineColourPicker(
    colours: List<com.alazhartex.stock.data.repo.ColourRowDto>,
    lines: List<com.alazhartex.stock.data.repo.InventoryLineDto>,
    selectedCode: String?,
    arabicIndic: Boolean,
    assigningCode: String?,
    onSelectLine: (com.alazhartex.stock.data.repo.InventoryLineDto) -> Unit,
    onAddColour: (com.alazhartex.stock.data.repo.ColourRowDto) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val gradeOrder = mapOf("A" to 0, "B" to 1, "C" to 2, "D" to 3)
    val mappedByCode = remember(lines) { lines.associateBy { it.colourCode.trim().uppercase() } }
    val filtered = remember(colours, query) {
        val q = query.trim().lowercase()
        colours
            .filter { c ->
                q.isEmpty() ||
                    c.code.lowercase().contains(q) ||
                    c.nameEn.lowercase().contains(q) ||
                    c.nameAr.contains(query.trim())
            }
            .sortedWith(compareBy({ gradeOrder[it.grade.uppercase()] ?: 9 }, { it.code }))
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it.take(60) },
        label = { Text(stringResource(R.string.picker_search_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("colour_search"),
    )
    androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
    if (filtered.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().height(230.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.picker_empty), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { query = "" }) { Text(stringResource(R.string.picker_clear_filters)) }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .padding(6.dp),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("colour_list"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items = filtered, key = { it.id }, contentType = { "colour_row" }) { colour ->
                    val key = colour.code.trim().uppercase()
                    val line = mappedByCode[key]
                    InlineColourRow(
                        colour = colour,
                        mappedLine = line,
                        selected = selectedCode != null && selectedCode.equals(line?.colourCode, ignoreCase = true),
                        arabicIndic = arabicIndic,
                        assigning = assigningCode == colour.code,
                        onSelect = { line?.let(onSelectLine) },
                        onAdd = { onAddColour(colour) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineColourRow(
    colour: com.alazhartex.stock.data.repo.ColourRowDto,
    mappedLine: com.alazhartex.stock.data.repo.InventoryLineDto?,
    selected: Boolean,
    arabicIndic: Boolean,
    assigning: Boolean,
    onSelect: () -> Unit,
    onAdd: () -> Unit,
) {
    val swatch = rememberAnySwatch(colour.family, colour.grade, colour.code, colour.customHex)
    val cd = paletteContentDescription(colour.code, colour.nameEn, colour.nameAr, colour.grade)
    val shape = RoundedCornerShape(12.dp)
    val mapped = mappedLine != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("colour_row_${colour.code}")
            .clip(shape)
            .clickable(enabled = mapped, onClick = onSelect)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            )
            .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
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
        if (mapped) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.picker_available, integerString(mappedLine!!.closing, arabicIndic)),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                RadioButton(selected = selected, onClick = onSelect)
            }
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.picker_not_mapped),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onAdd, enabled = !assigning, modifier = Modifier.testTag("colour_add_${colour.code}")) {
                    Text(stringResource(R.string.picker_add_here), maxLines = 1)
                }
            }
        }
    }
}
