package com.alazhartex.stock.data.repo

import com.alazhartex.stock.data.local.AppDatabase
import com.alazhartex.stock.domain.gauge.Utilisation
import com.alazhartex.stock.domain.stock.StockMath

/** Read-side of the repository (UI queries). No writes here; writes go through TransactionRunner. */
class ReadRepository(db: () -> AppDatabase) {
    private val dbSupplier: () -> AppDatabase = db
    private val database get() = dbSupplier()

    /** Where per-shed active rolls come from. ROLLS_CACHE keeps the v1.3.6 invalidation contract
     * (raw external writes into `rolls` show up on the Stock grid after DbProvider.refresh());
     * INVENTORY_LEDGER is the dashboard's source of truth (matches Top colours). */
    private enum class ShedRollsSource { ROLLS_CACHE, INVENTORY_LEDGER }

    suspend fun shedCards(): List<ShedCardDto> = shedCardsFrom(ShedRollsSource.ROLLS_CACHE)

    private suspend fun shedCardsFrom(source: ShedRollsSource): List<ShedCardDto> {
        val warehouses = database.warehouseDao().getAll()
        return warehouses.map { wh ->
            val active = when (source) {
                ShedRollsSource.ROLLS_CACHE -> database.rollDao().sumActiveForShed(wh.id)
                ShedRollsSource.INVENTORY_LEDGER -> database.inventoryDao().sumClosingForShed(wh.id)
            }
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
                fabricId = m.fabricId,
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
                customHex = c.customHex,
            )
        }
        return out.sortedWith(compareBy({ it.warehouseId }, { it.fabricNameEn }, { it.colourCode }))
    }

    suspend fun movements(limit: Int = 200): List<MovementRowDto> {
        val mappings = database.mappingDao().getActive() + database.mappingDao().getInactive()
        val mappingById = mappings.associateBy { it.id }
        val colours = database.colourDao().getAll().associateBy { it.id }
        return database.movementDao().getAll().sortedByDescending { it.at }.take(limit).map { mv ->
            val mapping = mappingById[mv.mappingId]
            val colour = mapping?.let { colours[it.colourId] }
            MovementRowDto(
                mv.id, mv.type, mapping?.warehouseId ?: "", mv.mappingId, mv.qtyDelta, mv.at, mv.transferPairId, mv.note,
                colourCode = colour?.code ?: "?",
                colourNameEn = colour?.nameEn ?: "",
                colourNameAr = colour?.nameAr ?: "",
                grade = colour?.grade ?: "?",
                family = colour?.family ?: "unknown",
                customHex = colour?.customHex,
            )
        }
    }

    suspend fun colours(): List<ColourRowDto> =
        database.colourDao().getAll().map { ColourRowDto(it.id, it.code, it.nameEn, it.nameAr, it.grade, it.family, it.keeperCode, it.isActive, it.customHex) }

    suspend fun audit(limit: Int = 500, onlyBlocked: Boolean = false): List<AuditRowDto> {
        val rows = if (onlyBlocked) database.auditDao().byResult("BLOCKED") else database.auditDao().latest(limit)
        return rows.take(limit).map { AuditRowDto(it.id, it.at, it.actorId, it.action, it.target, it.result, it.reason) }
    }

    /**
     * Dashboard totals (v1.4). Aggregates the ledger of record, not the derived roll cache:
     *  - Active rolls  = SUM(inventory.closing)   — the same source Top colours/inventory lines read,
     *                     so the KPIs can never disagree with the colour ranking again.
     *  - Inactive rolls = SUM(rolls.count) WHERE state = 'INACTIVE'
     *  - Total rolls   = Active + Inactive         — "Active = Total − Inactive" holds by construction
     *  - Capacity      = SUM(warehouses.capacity_rolls) — shed (Stock) side, via [WarehouseDao.sumCapacity]
     *  - Utilisation   = (Total / Capacity) x 100, zero-capacity safe (null percent, null bucket)
     * Everything is a fresh SQL SUM, evaluated per call; committed writes bump `DbProvider.dataVersion`
     * so collectors re-query instantly.
     */
    suspend fun dashboard(): DashboardDto {
        val topColours = rankTopColours(5)
        val cards = shedCardsFrom(ShedRollsSource.INVENTORY_LEDGER)
        val active = database.inventoryDao().sumClosingAll()
        val inactive = database.rollDao().sumByState("INACTIVE")
        val total = active + inactive
        val totalCapacity = database.warehouseDao().sumCapacity()
        val display = Utilisation.percentOneDp(total, totalCapacity)?.stripTrailingZeros()?.toPlainString()
        val drift = driftRows().size
        return DashboardDto(
            totalRolls = total,
            activeRolls = active,
            inactiveRolls = inactive,
            totalCapacity = totalCapacity,
            percentDisplay = display,
            bucket = Utilisation.bucket(total, totalCapacity),
            topSheds = cards.sortedByDescending { it.activeRolls }.take(5),
            driftViolations = drift,
            topColours = topColours,
        )
    }

    /** v1.1 (delta §10.2): top colours by active closing rolls, stable tie-break by code. */
    suspend fun rankTopColours(limit: Int): List<TopColourDto> =
        inventoryLines()
            .filter { it.isActive }
            .groupBy { it.colourCode }
            .map { (code, lines) -> TopColourDto(code, lines.first().colourNameEn, lines.first().colourNameAr, lines.first().grade, lines.first().family, lines.sumOf { it.closing }, lines.first().customHex) }
            .sortedWith(compareByDescending<TopColourDto> { it.rolls }.thenBy { it.colourCode })
            .take(limit)

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

    /** v1.1: grouped mapping counts per fabric/colour (delta contract §12). */
    suspend fun mappingCountsByFabric(): Map<String, Long> =
        database.mappingDao().mappingCountsByFabric().associate { it.fabric_id to it.count }

    suspend fun mappingCountsByColour(): Map<String, Long> =
        database.mappingDao().mappingCountsByColour().associate { it.colour_id to it.count }

    /** v1.1: mappings + movements + rolls referencing a colour (delta contract §12 colour delete guard). */
    suspend fun colourUsage(colourId: String): Triple<Long, Long, Long> {
        val mappings = database.colourDao().mappingCountForColour(colourId)
        val movements = database.movementDao().countForColour(colourId)
        val rolls = database.rollDao().sumForColour(colourId, "ACTIVE") + database.rollDao().sumForColour(colourId, "INACTIVE")
        return Triple(mappings, movements, rolls)
    }

    suspend fun accountById(id: String) = database.accountDao().getById(id)
    suspend fun shedAssignments(accountId: String) = database.shedAssignmentDao().shedsFor(accountId)
    suspend fun settingsAll() = database.appSettingsDao().getAll()
    suspend fun setting(key: String) = database.appSettingsDao().get(key)
}

