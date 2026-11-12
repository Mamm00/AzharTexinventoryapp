package com.alazhartex.stock.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities for AlAzharTex (spec §4.3, snake_case columns, FKs RESTRICT, uniques per spec).
 * opening_cache is a transaction-bound cache (§9.7/RES-021); the domain opening is always derived.
 * No barcode / serial / fabric-width field exists anywhere in this file.
 */

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "password_pbkdf2_sha256") val passwordPbkdf2Sha256: String,
    // ADMIN | KEEPER | SALES_MANAGER
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "display_name") val displayName: String,
)

@Entity(
    tableName = "warehouses",
    foreignKeys = [ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["keeper_account_id"], onDelete = ForeignKey.RESTRICT)],
)
data class WarehouseEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "capacity_rolls") val capacityRolls: Long,
    @ColumnInfo(name = "keeper_account_id") val keeperAccountId: String,
)

@Entity(
    tableName = "shed_assignments",
    primaryKeys = ["account_id", "warehouse_id"],
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = WarehouseEntity::class, parentColumns = ["id"], childColumns = ["warehouse_id"], onDelete = ForeignKey.RESTRICT),
    ],
)
data class ShedAssignmentEntity(
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "warehouse_id") val warehouseId: String,
)

@Entity(tableName = "fabrics")
data class FabricEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "family") val family: String,
    // v1.3 "Fabric Information" (images: sample-booking form) — free text, may be NULL on seeded rows.
    @ColumnInfo(name = "notes") val notes: String? = null,
)

@Entity(
    tableName = "colours",
    indices = [Index(value = ["keeper_code"], unique = true)],
)
data class ColourEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "code") val code: String,
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    // A|B|C|D (DB trigger enforces)
    @ColumnInfo(name = "grade") val grade: String,
    @ColumnInfo(name = "family") val family: String,
    @ColumnInfo(name = "keeper_code") val keeperCode: String?,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    // v1.3 "color selection" — user-picked #RRGGBB for this colour, NULL for every seeded colour.
    @ColumnInfo(name = "custom_hex") val customHex: String? = null,
)

@Entity(
    tableName = "mappings",
    foreignKeys = [
        ForeignKey(entity = WarehouseEntity::class, parentColumns = ["id"], childColumns = ["warehouse_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = FabricEntity::class, parentColumns = ["id"], childColumns = ["fabric_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ColourEntity::class, parentColumns = ["id"], childColumns = ["colour_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["warehouse_id", "fabric_id", "colour_id"], unique = true)],
)
data class MappingEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "warehouse_id") val warehouseId: String,
    @ColumnInfo(name = "fabric_id") val fabricId: String,
    @ColumnInfo(name = "colour_id") val colourId: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
)

@Entity(
    tableName = "inventory",
    foreignKeys = [ForeignKey(entity = MappingEntity::class, parentColumns = ["id"], childColumns = ["mapping_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["mapping_id"], unique = true)],
)
data class InventoryEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "mapping_id") val mappingId: String,
    @ColumnInfo(name = "closing") val closing: Long,
    /** Transaction-bound cache; NEVER read for display without the invariant re-check. */
    @ColumnInfo(name = "opening_cache") val openingCache: Long,
    @ColumnInfo(name = "last_event_at") val lastEventAt: Long?,
)

@Entity(
    tableName = "movements",
    foreignKeys = [
        ForeignKey(entity = MappingEntity::class, parentColumns = ["id"], childColumns = ["mapping_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["actor_account_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index(value = ["idempotency_key"], unique = true),
        Index(value = ["transfer_pair_id", "type"], unique = true),
        Index(value = ["mapping_id"]),
    ],
)
data class MovementEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    // IN|OUT|TRANSFER_OUT|TRANSFER_IN|ADJUSTMENT
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "mapping_id") val mappingId: String,
    // DB trigger enforces <> 0
    @ColumnInfo(name = "qty_delta") val qtyDelta: Long,
    @ColumnInfo(name = "transfer_pair_id") val transferPairId: String?,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    @ColumnInfo(name = "actor_account_id") val actorAccountId: String,
    // epoch millis UTC
    @ColumnInfo(name = "at") val at: Long,
    @ColumnInfo(name = "note") val note: String,
)

@Entity(
    tableName = "rolls",
    foreignKeys = [ForeignKey(entity = MappingEntity::class, parentColumns = ["id"], childColumns = ["mapping_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["mapping_id", "state"])],
)
data class RollEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "mapping_id") val mappingId: String,
    // ACTIVE|INACTIVE
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "count") val count: Long,
)

@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "at") val at: Long,
    @ColumnInfo(name = "actor_id") val actorId: String,
    @ColumnInfo(name = "action") val action: String,
    @ColumnInfo(name = "target") val target: String,
    // ALLOWED|BLOCKED
    @ColumnInfo(name = "result") val result: String,
    @ColumnInfo(name = "reason") val reason: String?,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String?,
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "issued_at_wall") val issuedAtWall: Long,
    @ColumnInfo(name = "issued_at_elapsed") val issuedAtElapsed: Long,
    @ColumnInfo(name = "boot_id") val bootId: String,
    @ColumnInfo(name = "expires_at_wall") val expiresAtWall: Long,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String,
)

@Entity(tableName = "_manifest")
data class ManifestEntity(
    @PrimaryKey @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "row_count") val rowCount: Long,
    @ColumnInfo(name = "schema_version") val schemaVersion: Long,
    @ColumnInfo(name = "app_version") val appVersion: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
