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
