package com.alazhartex.stock.data.csv

import com.alazhartex.stock.data.repo.AuditRowDto
import com.alazhartex.stock.data.repo.DriftRowDto
import com.alazhartex.stock.data.repo.InventoryLineDto
import com.alazhartex.stock.data.repo.MovementRowDto
import com.alazhartex.stock.data.repo.ShedCardDto
import com.alazhartex.stock.domain.csv.Csv
import com.alazhartex.stock.domain.csv.CsvColumnSets
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * CSV report documents (spec §4.5/§9.13). Column sets come ONLY from CsvColumnSets (single constant).
 * Formula-injection escaping and exactly one BOM are applied by domain/Csv.
 */
object CsvExport {
    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    fun stock(cards: List<ShedCardDto>): String {
        val rows = cards.map { card ->
            listOf(
                card.shedId,
                card.nameEn,
                card.nameAr,
                card.capacityRolls.toString(),
                card.activeRolls.toString(),
                card.percentDisplay ?: "0",
            )
        }
        return Csv.buildDocument(CsvColumnSets.stock, rows)
    }

    fun inventory(lines: List<InventoryLineDto>): String {
        val rows = lines.map { line ->
            // opening is DERIVED (= closing - Σ deltas); CSV shows closing and cache for transparency, drift report pins the audit trail.
            listOf(
                line.warehouseId,
                line.fabricNameEn,
                line.fabricNameAr,
                line.colourNameEn,
                line.colourNameAr,
                line.grade,
                line.family,
                line.closing.toString(),
                line.inactiveRolls.toString(),
                line.openingCache.toString(),
                line.closing.toString(),
            )
        }
        return Csv.buildDocument(CsvColumnSets.inventory, rows)
    }

    fun movements(rows: List<MovementRowDto>): String {
        val out = rows.map { mv ->
            listOf(
                mv.id,
                mv.type,
                mv.warehouseId,
                mv.mappingId,
                mv.qtyDelta.toString(),
                ISO.format(Instant.ofEpochMilli(mv.atMillis)),
                mv.transferPairId ?: "",
                mv.note,
            )
        }
        return Csv.buildDocument(CsvColumnSets.movements, out)
    }

    fun audit(rows: List<AuditRowDto>): String {
        val out = rows.map { row ->
            listOf(
                row.id,
                ISO.format(Instant.ofEpochMilli(row.atMillis)),
                row.actorId,
                row.action,
                row.target,
                row.result,
                row.reason ?: "",
            )
        }
        return Csv.buildDocument(CsvColumnSets.audit, out)
    }

    fun drift(rows: List<DriftRowDto>): String {
        val out = rows.map { row ->
            listOf(
                row.warehouseId,
                row.mappingId,
                row.opening.toString(),
                row.sumDeltas.toString(),
                row.closing.toString(),
                row.drift.toString(),
            )
        }
        return Csv.buildDocument(CsvColumnSets.drift, out)
    }
}
