package com.alazhartex.stock.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.Callback
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database (spec §4.3/§4.7, RES-007). Version 1; schema exported via KSP (G5 in CI).
 * No fallbackToDestructiveMigration — EVER. Triggers carry the CHECK constraints Room cannot
 * express in annotations (qty_delta <> 0, grade IN ('A','B','C','D')).
 */
@Database(
    entities = [
        AccountEntity::class,
        WarehouseEntity::class,
        ShedAssignmentEntity::class,
        FabricEntity::class,
        ColourEntity::class,
        MappingEntity::class,
        InventoryEntity::class,
        MovementEntity::class,
        RollEntity::class,
        AuditLogEntity::class,
        SessionEntity::class,
        AppSettingEntity::class,
        ManifestEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun warehouseDao(): WarehouseDao
    abstract fun shedAssignmentDao(): ShedAssignmentDao
    abstract fun fabricDao(): FabricDao
    abstract fun colourDao(): ColourDao
    abstract fun mappingDao(): MappingDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun movementDao(): MovementDao
    abstract fun rollDao(): RollDao
    abstract fun auditDao(): AuditDao
    abstract fun sessionDao(): SessionDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun manifestDao(): ManifestDao

    class TriggersCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS trg_movements_qty_nonzero
                   BEFORE INSERT ON movements
                   FOR EACH ROW WHEN NEW.qty_delta = 0
                   BEGIN SELECT RAISE(ABORT, 'qty_delta_must_be_nonzero'); END""",
            )
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS trg_colours_grade_check
                   BEFORE INSERT ON colours
                   FOR EACH ROW WHEN NEW.grade NOT IN ('A','B','C','D')
                   BEGIN SELECT RAISE(ABORT, 'grade_must_be_A_B_C_D'); END""",
            )
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS trg_colours_grade_update
                   BEFORE UPDATE OF grade ON colours
                   FOR EACH ROW WHEN NEW.grade NOT IN ('A','B','C','D')
                   BEGIN SELECT RAISE(ABORT, 'grade_must_be_A_B_C_D'); END""",
            )
            // application_id pins the file to this app for import R4 ('AZTH' = 0x415A5448).
            db.execSQL("PRAGMA application_id = 1096438856")
        }
    }
}
