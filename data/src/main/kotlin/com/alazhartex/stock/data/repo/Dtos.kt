package com.alazhartex.stock.data.repo

import com.alazhartex.stock.domain.gauge.GaugeBucket
import java.math.BigDecimal

data class ShedCardDto(
    val shedId: String,
    val nameAr: String,
    val nameEn: String,
    val capacityRolls: Long,
    val activeRolls: Long,
    val percentTwoDp: BigDecimal,
    val percentDisplay: String?,
    val bucket: GaugeBucket?,
)

data class InventoryLineDto(
    val mappingId: String,
    val warehouseId: String,
    val fabricNameEn: String,
    val fabricNameAr: String,
    val colourCode: String,
    val colourNameEn: String,
    val colourNameAr: String,
    val grade: String,
    val family: String,
    val isActive: Boolean,
    val closing: Long,
    val openingCache: Long,
    val inactiveRolls: Long,
)

data class MovementRowDto(
    val id: String,
    val type: String,
    val warehouseId: String,
    val mappingId: String,
    val qtyDelta: Long,
    val atMillis: Long,
    val transferPairId: String?,
    val note: String,
    val colourCode: String,
    val colourNameEn: String,
    val colourNameAr: String,
    val grade: String,
    val family: String,
)

data class ColourRowDto(
    val id: String,
    val code: String,
    val nameEn: String,
    val nameAr: String,
    val grade: String,
    val family: String,
    val keeperCode: String?,
    val isActive: Boolean,
)

data class AuditRowDto(
    val id: String,
    val atMillis: Long,
    val actorId: String,
    val action: String,
    val target: String,
    val result: String,
    val reason: String?,
)

data class TopColourDto(
    val colourCode: String,
    val colourNameEn: String,
    val colourNameAr: String,
    val grade: String,
    val family: String,
    val rolls: Long,
)

data class DashboardDto(
    val totalActiveRolls: Long,
    val totalCapacity: Long,
    val percentDisplay: String?,
    val bucket: GaugeBucket?,
    val topSheds: List<ShedCardDto>,
    val driftViolations: Int,
    val inactiveRolls: Long,
    val rawRolls: Long,
    val topColours: List<TopColourDto>,
)

data class DriftRowDto(
    val warehouseId: String,
    val mappingId: String,
    val opening: Long,
    val sumDeltas: Long,
    val closing: Long,
    val drift: Long,
)

data class ProfileDto(
    val accountId: String,
    val username: String,
    val role: String,
    val displayName: String,
    val assignedSheds: List<String>,
    val sessionExpiresAtWall: Long?,
)

data class MastersSnapshot(
    val warehouses: List<com.alazhartex.stock.data.local.WarehouseEntity>,
    val fabrics: List<com.alazhartex.stock.data.local.FabricEntity>,
    val colours: List<com.alazhartex.stock.data.local.ColourEntity>,
    val mappingCount: Long,
)
