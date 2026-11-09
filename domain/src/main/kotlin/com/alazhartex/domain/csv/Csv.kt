package com.alazhartex.domain.csv

/**
 * CSV export safety (spec §9.13, T10):
 *  - A single leading apostrophe is prefixed to any cell whose first character is one of
 *    '=', '+', '-', '@', '\t', '\r', '\n', ' ', U+FF1D, U+FF0B, U+FF0D, U+FF20.
 *  - U+FEFF BOM is prepended exactly once per document.
 *  - Standard RFC-4180 quoting applies for separators/quotes/newlines inside cells.
 * Round-trip contract: unescape strips exactly one leading apostrophe when the remainder
 * begins with a trigger character; a literal cell beginning with an apostrophe is never
 * re-interpreted because its first character is not itself a trigger. See ADR.md.
 */
object Csv {
    const val BOM: String = "\uFEFF"
    private val TRIGGER = setOf(
        '=', '+', '-', '@', '\t', '\r', '\n', ' ',
        '＝', '＋', '－', '＠',
    )

    fun escapeCell(cell: String): String {
        val escapedFormula = if (cell.isNotEmpty() && cell.first() in TRIGGER) "'$cell" else cell
        return quoteIfNeeded(escapedFormula)
    }

    fun unescapeCell(cell: String): String {
        if (cell.length >= 2 && cell.first() == '\'' && cell[1] in TRIGGER) {
            return cell.substring(1)
        }
        return cell
    }

    private fun quoteIfNeeded(cell: String): String {
        val mustQuote = cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (mustQuote) "\"" + cell.replace("\"", "\"\"") + "\"" else cell
    }

    /** Builds the full CSV document including the single leading BOM. */
    fun buildDocument(header: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder(BOM)
        sb.append(header.joinToString(",") { escapeCell(it) })
        for (row in rows) {
            sb.append('\n')
            sb.append(row.joinToString(",") { escapeCell(it) })
        }
        return sb.toString()
    }
}

/** Pinned CSV column sets per report (CONTENT.md §6). The ONLY source for column names. */
object CsvColumnSets {
    val stock = listOf("shed_id", "shed_name_en", "shed_name_ar", "capacity_rolls", "active_rolls", "utilisation_pct")
    val inventory = listOf("warehouse_id", "fabric_en", "fabric_ar", "colour_en", "colour_ar", "grade", "family", "active_rolls", "inactive_rolls", "opening", "closing")
    val movements = listOf("id", "type", "warehouse_id", "mapping_id", "qty_delta", "at_iso_utc", "transfer_pair_id", "note")
    val audit = listOf("id", "at_iso_utc", "actor", "action", "target", "result", "reason")
    val drift = listOf("warehouse_id", "mapping_id", "opening", "sum_deltas", "closing", "drift")

    fun forReport(kind: String): List<String> = when (kind) {
        "stock" -> stock
        "inventory" -> inventory
        "movements" -> movements
        "audit" -> audit
        "drift" -> drift
        else -> throw IllegalArgumentException("unknown report kind: $kind")
    }
}
