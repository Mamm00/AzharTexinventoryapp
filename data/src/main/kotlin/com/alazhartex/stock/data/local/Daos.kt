package com.alazhartex.stock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO contract (spec §4.7/§9.6, RES-014): DAO methods are pure SQL; the audited WriteContext
 * lives in the repository layer (TransactionRunner/SessionManager), which Room cannot receive.
 * the repository is the ONLY caller; every read-check-write path runs in one Room
 * withTransaction {} block that also performs the write. No AllowMainThreadQueries anywhere.
 */
@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity)
}

@Dao
interface WarehouseDao {
    @Query("SELECT * FROM warehouses ORDER BY id")
    suspend fun getAll(): List<WarehouseEntity>

    @Query("SELECT * FROM warehouses WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WarehouseEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<WarehouseEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: WarehouseEntity)

    @Query("UPDATE warehouses SET capacity_rolls = :capacity WHERE id = :id")
    suspend fun updateCapacity(id: String, capacity: Long): Int
}

@Dao
interface ShedAssignmentDao {
    @Query("SELECT warehouse_id FROM shed_assignments WHERE account_id = :accountId")
    suspend fun shedsFor(accountId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<ShedAssignmentEntity>)
}

@Dao
interface FabricDao {
    @Query("SELECT * FROM fabrics ORDER BY name_en")
    suspend fun getAll(): List<FabricEntity>

    @Query("SELECT * FROM fabrics WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FabricEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<FabricEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: FabricEntity)

    @Query("SELECT COUNT(*) FROM mappings WHERE fabric_id = :fabricId")
    suspend fun mappingCountForFab(fabricId: String): Long

    @Query("DELETE FROM fabrics WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("UPDATE fabrics SET name_ar = :nameAr, name_en = :nameEn, family = :family WHERE id = :id")
    suspend fun update(id: String, nameAr: String, nameEn: String, family: String): Int

    @Query("SELECT COUNT(*) FROM fabrics WHERE id != :excludeId AND name_ar = :nameAr AND name_en = :nameEn")
    suspend fun duplicateCount(excludeId: String, nameAr: String, nameEn: String): Long

    @Query("SELECT COUNT(*) FROM fabrics WHERE name_ar = :nameAr AND name_en = :nameEn")
    suspend fun duplicateCount(nameAr: String, nameEn: String): Long
}

@Dao
interface ColourDao {
    @Query("SELECT * FROM colours ORDER BY code")
    suspend fun getAll(): List<ColourEntity>

    @Query("SELECT * FROM colours WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ColourEntity?

    @Query("SELECT * FROM colours WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): ColourEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<ColourEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: ColourEntity)

    @Query("UPDATE colours SET grade = :grade WHERE id = :id")
    suspend fun updateGrade(id: String, grade: String): Int

    @Query("UPDATE colours SET is_active = :isActive WHERE id = :id")
    suspend fun updateActive(id: String, isActive: Boolean): Int

    @Query("UPDATE colours SET name_ar = :nameAr, name_en = :nameEn, grade = :grade, family = :family, is_active = :isActive WHERE id = :id")
    suspend fun updateDetails(id: String, nameAr: String, nameEn: String, grade: String, family: String, isActive: Boolean): Int

    @Query("SELECT COUNT(*) FROM colours WHERE keeper_code = :keeperCode")
    suspend fun countByKeeperCode(keeperCode: String): Long

    @Query("SELECT COUNT(*) FROM mappings WHERE colour_id = :colourId")
    suspend fun mappingCountForColour(colourId: String): Long

    @Query("SELECT COUNT(*) FROM colours WHERE id != :excludeId AND code = :code")
    suspend fun duplicateCodeCount(excludeId: String, code: String): Long

    @Query("DELETE FROM colours WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Dao
interface MappingDao {
    @Query("SELECT * FROM mappings WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MappingEntity?

    @Query("SELECT * FROM mappings WHERE is_active = 1")
    suspend fun getActive(): List<MappingEntity>

    @Query("SELECT * FROM mappings WHERE is_active = 0")
    suspend fun getInactive(): List<MappingEntity>

    @Query("SELECT * FROM mappings WHERE is_active = 0 AND warehouse_id = :warehouseId")
    suspend fun getInactiveForShed(warehouseId: String): List<MappingEntity>

    @Query("SELECT * FROM mappings WHERE warehouse_id = :warehouseId AND is_active = 1 ORDER BY fabric_id, colour_id")
    suspend fun getActiveForShed(warehouseId: String): List<MappingEntity>

    @Query("SELECT COUNT(*) FROM mappings")
    suspend fun countAll(): Long

    @Query("SELECT COUNT(*) FROM mappings WHERE is_active = 0")
    suspend fun countInactive(): Long

    @Query("SELECT COUNT(*) FROM mappings WHERE fabric_id = :fabricId")
    suspend fun mappingCountForFabric(fabricId: String): Long

    @Query("SELECT fabric_id, COUNT(*) AS count FROM mappings GROUP BY fabric_id")
    suspend fun mappingCountsByFabric(): List<FabricMappingCount>

    @Query("SELECT colour_id, COUNT(*) AS count FROM mappings GROUP BY colour_id")
    suspend fun mappingCountsByColour(): List<ColourMappingCount>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<MappingEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: MappingEntity)

    @Query("UPDATE mappings SET is_active = :isActive WHERE id = :id")
    suspend fun updateActive(id: String, isActive: Boolean): Int

    @Query("SELECT id FROM mappings WHERE warehouse_id = :warehouseId AND fabric_id = :fabricId AND colour_id = :colourId")
    suspend fun findIds(warehouseId: String, fabricId: String, colourId: String): List<String>
}

/** Grouped-count row for the Fabrics Edit delete confirmation (delta §12). */
data class FabricMappingCount(val fabric_id: String, val count: Long)

/** Grouped-count row for the Fabrics Edit colour usage strip (delta §12). */
data class ColourMappingCount(val colour_id: String, val count: Long)

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory WHERE mapping_id = :mappingId LIMIT 1")
    suspend fun getByMapping(mappingId: String): InventoryEntity?

    @Query("SELECT * FROM inventory ORDER BY mapping_id")
    suspend fun getAll(): List<InventoryEntity>

    @Query("SELECT COALESCE(SUM(closing), 0) FROM inventory i JOIN mappings m ON m.id = i.mapping_id WHERE m.warehouse_id = :warehouseId")
    suspend fun sumClosingForShed(warehouseId: String): Long

    @Query("SELECT COALESCE(SUM(closing), 0) FROM inventory")
    suspend fun sumClosingAll(): Long

    /** Atomic conditional decrement — the concurrency guarantee for negative-stock refusal (§9.6, T5). */
    @Query("UPDATE inventory SET closing = closing - :qty, last_event_at = :at WHERE mapping_id = :mappingId AND closing - :qty >= 0")
    suspend fun conditionalDecrement(mappingId: String, qty: Long, at: Long): Int

    @Query("UPDATE inventory SET closing = closing + :qty, last_event_at = :at WHERE mapping_id = :mappingId")
    suspend fun increment(mappingId: String, qty: Long, at: Long): Int

    @Query("UPDATE inventory SET opening_cache = :openingCache, last_event_at = :at WHERE mapping_id = :mappingId")
    suspend fun updateOpeningCache(mappingId: String, openingCache: Long, at: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<InventoryEntity>)
}

@Dao
interface MovementDao {
    @Query("SELECT * FROM movements ORDER BY at")
    suspend fun getAll(): List<MovementEntity>

    @Query("SELECT COALESCE(SUM(qty_delta), 0) FROM movements WHERE mapping_id = :mappingId")
    suspend fun sumDeltaFor(mappingId: String): Long

    @Query("SELECT COUNT(*) FROM movements mv JOIN mappings m ON m.id = mv.mapping_id WHERE m.colour_id = :colourId")
    suspend fun countForColour(colourId: String): Long

    @Query("SELECT COUNT(*) FROM movements WHERE idempotency_key = :key")
    suspend fun countByIdempotency(key: String): Long

    @Query("SELECT COUNT(*) FROM movements WHERE type = :type")
    suspend fun countByType(type: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: MovementEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<MovementEntity>)
}

@Dao
interface RollDao {
    @Query("SELECT * FROM rolls")
    suspend fun getAll(): List<RollEntity>

    @Query("SELECT * FROM rolls WHERE mapping_id = :mappingId AND state = :state LIMIT 1")
    suspend fun getByMappingAndState(mappingId: String, state: String): RollEntity?

    @Query("SELECT COALESCE(SUM(count), 0) FROM rolls WHERE state = :state")
    suspend fun sumByState(state: String): Long

    @Query("SELECT COALESCE(SUM(count), 0) FROM rolls r JOIN mappings m ON m.id = r.mapping_id WHERE r.state = 'ACTIVE' AND m.warehouse_id = :warehouseId")
    suspend fun sumActiveForShed(warehouseId: String): Long

    @Query("SELECT COALESCE(SUM(count), 0) FROM rolls r JOIN mappings m ON m.id = r.mapping_id WHERE r.state = :state AND m.colour_id = :colourId")
    suspend fun sumForColour(colourId: String, state: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<RollEntity>)

    @Query("UPDATE rolls SET count = :count WHERE mapping_id = :mappingId AND state = :state")
    suspend fun updateCount(mappingId: String, state: String, count: Long): Int
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_log ORDER BY at DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<AuditLogEntity>

    @Query("SELECT * FROM audit_log WHERE action = :action ORDER BY at DESC")
    suspend fun byAction(action: String): List<AuditLogEntity>

    @Query("SELECT * FROM audit_log WHERE result = :result ORDER BY at DESC")
    suspend fun byResult(result: String): List<AuditLogEntity>

    @Query("SELECT COUNT(*) FROM audit_log")
    suspend fun countAll(): Long

    @Query("SELECT COUNT(*) FROM audit_log WHERE idempotency_key = :key")
    suspend fun countByIdempotency(key: String): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: AuditLogEntity)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE account_id = :accountId AND is_active = 1 ORDER BY issued_at_wall DESC LIMIT 1")
    suspend fun latestActive(accountId: String): SessionEntity?

    @Query("UPDATE sessions SET is_active = 0 WHERE account_id = :accountId")
    suspend fun deactivateFor(accountId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: SessionEntity)
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings ORDER BY key")
    suspend fun getAll(): List<AppSettingEntity>

    @Query("SELECT value FROM app_settings WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: AppSettingEntity)
}

@Dao
interface ManifestDao {
    @Query("SELECT * FROM _manifest ORDER BY table_name")
    suspend fun getAll(): List<ManifestEntity>

    @Query("DELETE FROM _manifest")
    suspend fun clear(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ManifestEntity>)
}
