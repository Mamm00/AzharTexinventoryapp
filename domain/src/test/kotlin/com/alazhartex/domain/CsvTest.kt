package com.alazhartex.domain

import com.alazhartex.domain.csv.Csv
import com.alazhartex.domain.csv.CsvColumnSets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T10 — CSV escaping + round-trip (spec §9.13). */
class CsvTest {
    val triggers = listOf("=1+1", "-3", "@cmd", "\tfoo", "'already-quoted", "=HYPERLINK(http://evil.example)", "＝x", "＋x", "－x", "＠x", " leading", "+plus", "\rcarriage", "\nnewline")

    @Test
    fun escapes_formula_injection_vectors() {
        for (v in triggers) {
            val escaped = Csv.escapeCell(v)
            assertTrue("cell '$v' must be prefixed or safe", escaped == v || escaped.startsWith("'"))
        }
        assertEquals("'=1+1", Csv.escapeCell("=1+1"))
        assertEquals("'-3", Csv.escapeCell("-3"))
        assertEquals("'@cmd", Csv.escapeCell("@cmd"))
        assertEquals("'\tfoo", Csv.escapeCell("\tfoo"))
    }

    @Test
    fun already_quoted_cell_not_double_prefixed() {
        // First char is apostrophe, not a trigger: stays verbatim.
        assertEquals("'already-quoted", Csv.escapeCell("'already-quoted"))
    }

    @Test
    fun bom_exactly_once() {
        val doc = Csv.buildDocument(listOf("a", "b"), listOf(listOf("1", "2")))
        assertEquals(1, Regex("\uFEFF").findAll(doc).count())
        assertTrue(doc.startsWith("\uFEFF"))
    }

    @Test
    fun round_trip_byte_equality_via_strict_parser() {
        val original = listOf(
            listOf("=1+1", "-3", "@cmd", "\tfoo", "'already-quoted", "=HYPERLINK(http://evil.example)"),
            listOf("plain", "with, comma", "say \"hi\"", "line\nbreak", "", "ends with space "),
        )
        val doc = Csv.buildDocument(CsvColumnSets.movements.take(6), original)
        val parsed = parseCsv(doc.removePrefix("\uFEFF"))
        val data = parsed.drop(1) // header
        original.zip(data).forEachIndexed { i, (expectedRow, actualRow) ->
            expectedRow.zip(actualRow).forEachIndexed { j, (expectedCell, actualCell) ->
                assertEquals("cell[$i][$j]", expectedCell, Csv.unescapeCell(actualCell))
            }
        }
    }

    @Test
    fun column_sets_pinned_from_single_constant() {
        assertEquals(listOf("id", "type", "warehouse_id", "mapping_id", "qty_delta", "at_iso_utc", "transfer_pair_id", "note"), CsvColumnSets.movements)
        assertEquals(11, CsvColumnSets.inventory.size)
        assertEquals(6, CsvColumnSets.stock.size)
        assertEquals(7, CsvColumnSets.audit.size)
        assertEquals(6, CsvColumnSets.drift.size)
    }

    @Test
    fun quoting_rules_rfc4180() {
        assertEquals("\"a,b\"", Csv.escapeCell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.escapeCell("say \"hi\""))
        assertEquals("\"x\ny\"", Csv.escapeCell("x\ny"))
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        fun endCell() { row.add(cell.toString()); cell.clear() }
        fun endRow() { endCell(); rows.add(row); row = mutableListOf() }
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when (c) {
                    '"' -> {
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            cell.append('"')
                            i += 2
                        } else {
                            inQuotes = false
                            i += 1
                        }
                        continue
                    }
                    else -> { cell.append(c); i += 1; continue }
                }
                c == '"' -> { inQuotes = true; i += 1; continue }
                c == ',' -> { endCell(); i += 1; continue }
                c == '\n' -> { endRow(); i += 1; continue }
                c == '\r' -> { i += 1; continue }
                else -> { cell.append(c); i += 1; continue }
            }
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
