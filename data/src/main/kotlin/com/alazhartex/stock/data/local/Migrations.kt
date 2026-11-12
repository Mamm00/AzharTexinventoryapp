package com.alazhartex.stock.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> 2 (v1.1, delta contract §17.10 / D30): UNIQUE pair on fabrics (name_ar, name_en).
 * Implemented as a raw UNIQUE index: the entity definitions are intentionally untouched so the
 * Room identity hash stays stable and no TableInfo drift is possible at open time.
 * The seed is unique on this pair (61 fabrics); v1.1 closes the race at both levels
 * (this index + an in-transaction duplicate check in MastersSettingsRepository).
 * IF NOT EXISTS keeps the fresh-install path (handled in TriggersCallback.onCreate) idempotent.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE UNIQUE INDEX IF NOT EXISTS fabrics_name_ar_name_en_unique
               ON fabrics(name_ar, name_en)""",
        )
    }
}

/**
 * v2 -> 3 (v1.3): nullable `colours.custom_hex` (user-picked swatch, NULL for the seed) and
 * nullable `fabrics.notes` (Fabric Information form free text). Both columns are additive with no
 * default — SQLite ALTER TABLE ADD COLUMN of a NULLABLE column without default is safe and leaves
 * every existing row NULL, so IGNORE-inserts and the deterministic palette for seeded colours are
 * untouched.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE colours ADD COLUMN custom_hex TEXT")
        db.execSQL("ALTER TABLE fabrics ADD COLUMN notes TEXT")
    }
}
