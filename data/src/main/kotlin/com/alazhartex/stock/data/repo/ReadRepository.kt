package com.alazhartex.stock.data.repo

import com.alazhartex.stock.data.local.AppDatabase
import com.alazhartex.stock.domain.gauge.Utilisation
import com.alazhartex.stock.domain.stock.StockMath
import java.util.Locale

/** Read-side of the repository (UI queries). No writes here; writes go through TransactionRunner. */
class ReadRepository(private val database: AppDatabase) {

    suspend fun shedCards(): List<ShedCardDto> {
        val warehouses = database.warehouseDao().getAll()
        return warehouses.map { wh ->
            val active = database.rollDao().sumActiveForShed(wh.id)
            val pct = Utilisation.percentTwoDp(active, wh.capacityRolls)
            val display = Utilisation.percentOneDp(active, wh.capacityRolls)
                ?.stripTrailingZeros()?.toPlainString()
            ShedCardDto(
                shedId = wh.id,
                nameAr = wh.nameAr,
                nameEn = wh.nameEn,
                capacityRolls = wh.capacityRolls,
                activeRolls = active,
                percentTwoDp = pct,
                percentDisplay = display,
                bucket = Utilisation.bucket(active, wh.capacityRolls),
            )
        }
    }

    suspend fun inventoryLines(warehouseId: String? = null): List<InventoryLineDto> {
        val mappings = if (warehouseId == null) {
            database.mappingDao().getActive() + database.mappingDao().getInactive()
        } else {
            database.mappingDao().getActiveForShed(warehouseId) + database.mappingDao().getInactiveForShed(warehouseId)
        }
        if (mappings.isEmpty()) return emptyList()
        val fabrics = database.fabricDao().getAll().associateBy { it.id }
        val colours = database.colourDao().getAll().associateBy { it.id }
        val inventoryByMapping = database.inventoryDao().getAll().associateBy { it.mappingId }
        val inactiveRollByMapping = database.rollDao().getAll()
            .filter { it.state == "INACTIVE" }
            .associate { it.mappingId to it.count }
        val out = ArrayList<InventoryLineDto>(mappings.size)
        for (m in mappings) {
            val f = fabrics[m.fabricId] ?: continue
            val c = colours[m.colourId] ?: continue
            val closing = inventoryByMapping[m.id]?.closing ?: 0L
            val cache = inventoryByMapping[m.id]?.openingCache ?: 0L
            val inactiveRolls = inactiveRollByMapping[m.id] ?: 0L
            out += InventoryLineDto(
                mappingId = m.id,
                warehouseId = m.warehouseId,
                fabricNameEn = f.nameEn,
                fabricNameAr = f.nameAr,
                colourCode = c.code,
                colourNameEn = c.nameEn,
                colourNameAr = c.nameAr,
                grade = c.grade,
                family = c.family,
                isActive = m.isActive,
                closing = closing,
                openingCache = cache,
                inactiveRolls = inactiveRolls,
            )
        }
        return out.sortedWith(compareBy({ it.warehouseId }, { it.fabricNameEn }, { it.colourCode }))
    }

    suspend fun movements(limit: Int = 200): List<MovementRowDto> {
        val shedByMapping = database.mappingDao().getActive().associate { it.id to it.warehouseId } +
            database.mappingDao().getInactive().associate { it.id to it.warehouseId }
        return database.movementDao().getAll().sortedByDescending { it.at }.take(limit).map { mv ->
            MovementRowDto(mv.id, mv.type, shedByMapping[mv.mappingId] ?: "", mv.mappingId, mv.qtyDelta, mv.at, mv.transferPairId, mv.note)
        }
    }

    suspend fun colours(): List<ColourRowDto> =
        database.colourDao().getAll().map { ColourRowDto(it.id, it.code, it.nameEn, it.nameAr, it.grade, it.family, it.keeperCode, it.isActive) }

    suspend fun audit(limit: Int = 500, onlyBlocked: Boolean = false): List<AuditRowDto> {
        val rows = if (onlyBlocked) database.auditDao().byResult("BLOCKED") else database.auditDao().latest(limit)
        return rows.take(limit).map { AuditRowDto(it.id, it.at, it.actorId, it.action, it.target, it.result, it.reason) }
    }

    suspend fun dashboard(): DashboardDto {
        val cards = shedCards()
        val totalActive = cards.sumOf { it.activeRolls }
        val totalCapacity = cards.sumOf { it.capacityRolls }
        val inactive = database.rollDao().sumByState("INACTIVE")
        val display = Utilisation.percentOneDp(totalActive, totalCapacity)?.stripTrailingZeros()?.toPlainString()
        val drift = driftRows().size
        return DashboardDto(
            totalActiveRolls = totalActive,
            totalCapacity = totalCapacity,
            percentDisplay = display,
            bucket = Utilisation.bucket(totalActive, totalCapacity),
            topSheds = cards.sortedByDescending { it.activeRolls }.take(5),
            driftViolations = drift,
            inactiveRolls = inactive,
            rawRolls = totalActive + inactive,
        )
    }

    suspend fun driftRows(): List<DriftRowDto> {
        val out = ArrayList<DriftRowDto>()
        for (mapping in database.mappingDao().getActive()) {
            val row = database.inventoryDao().getByMapping(mapping.id) ?: continue
            val deltas = database.movementDao().sumDeltaFor(mapping.id)
            val opening = StockMath.derivedOpening(row.closing, deltas)
            val drift = kotlin.math.abs(opening - row.openingCache)
            if (drift != 0L) {
                out += DriftRowDto(mapping.warehouseId, mapping.id, opening, deltas, row.closing, drift)
            }
        }
        return out
    }

    suspend fun masters(): MastersSnapshot = MastersSnapshot(
        warehouses = database.warehouseDao().getAll(),
        fabrics = database.fabricDao().getAll(),
        colours = database.colourDao().getAll(),
        mappingCount = database.mappingDao().countAll(),
    )

    suspend fun accountById(id: String) = database.accountDao().getById(id)
    suspend fun shedAssignments(accountId: String) = database.shedAssignmentDao().shedsFor(accountId)
    suspend fun settingsAll() = database.appSettingsDao().getAll()
    suspend fun setting(key: String) = database.appSettingsDao().get(key)
}
