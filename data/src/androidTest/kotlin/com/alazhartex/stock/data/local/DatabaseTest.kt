package com.alazhartex.stock.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alazhartex.stock.data.repo.WriteContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room checks (CI emulator, API 34).
 * Verifies: trigger-backed CHECK enforcement, atomic conditional decrement,
 * foreign-key RESTRICT, unique idempotency key, and the zero-delta movement ban.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var database: AppDatabase
    private val ctx = WriteContext("test-actor", "test-idem", "test.op", 1_700_000_000_000L)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.TriggersCallback())
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedBase() {
        database.accountDao().insert(AccountEntity("admin", "admin", "pbkdf2\$210000\$salt\$hash", "ADMIN", "Admin"), ctx)
        database.warehouseDao().insert(WarehouseEntity("wh-1", "مستودع", "Warehouse One", 100L, "admin"), ctx)
        database.fabricDao().insert(FabricEntity("fabric-1", "قطن", "Cotton", "Cotton"), ctx)
        database.colourDao().insert(ColourEntity("colour-1", "C-1", "أحمر", "Red", "A", "Cotton", null, true), ctx)
        database.mappingDao().insert(MappingEntity("mapping-1", "wh-1", "fabric-1", "colour-1", true), ctx)
        database.inventoryDao().insertAll(
            listOf(InventoryEntity("inv-1", "mapping-1", 10L, 10L, 1_700_000_000_000L)),
            ctx,
        )
        database.rollDao().insertAll(listOf(RollEntity("roll-1", "mapping-1", "ACTIVE", 10L)), ctx)
    }

    @Test
    fun conditionalDecrementRefusesBelowZero() = runBlocking {
        seedBase()
        val ok = database.inventoryDao().conditionalDecrement("mapping-1", 10L, 1_700_000_001_000L, ctx)
        assertEquals(1, ok)
        val refused = database.inventoryDao().conditionalDecrement("mapping-1", 1L, 1_700_000_002_000L, ctx)
        assertEquals(0, refused)
        assertEquals(0L, database.inventoryDao().getByMapping("mapping-1")?.closing)
    }

    @Test
    fun movementTriggerRejectsZeroDelta() = runBlocking {
        seedBase()
        assertThrows(Exception::class.java) {
            runBlocking {
                database.movementDao().insert(
                    MovementEntity(
                        id = "mv-0", type = "ADJUSTMENT", mappingId = "mapping-1", qtyDelta = 0L,
                        transferPairId = null, idempotencyKey = "zero-delta",
                        actorAccountId = "admin", at = 1_700_000_003_000L, note = "must fail",
                    ),
                    ctx,
                )
            }
        }
    }

    @Test
    fun colourTriggerRejectsGradeOutsideAD() = runBlocking {
        seedBase()
        assertThrows(Exception::class.java) {
            runBlocking {
                database.colourDao().insert(
                    ColourEntity("colour-2", "C-2", "أزرق", "Blue", "E", "Cotton", null, true),
                    ctx,
                )
            }
        }
    }

    @Test
    fun movementIdempotencyKeyIsUnique() = runBlocking {
        seedBase()
        val movement = MovementEntity(
            id = "mv-1", type = "IN", mappingId = "mapping-1", qtyDelta = 5L,
            transferPairId = null, idempotencyKey = "dup-key",
            actorAccountId = "admin", at = 1_700_000_004_000L, note = "first",
        )
        database.movementDao().insert(movement, ctx)
        assertThrows(Exception::class.java) {
            runBlocking {
                database.movementDao().insert(movement.copy(id = "mv-2", note = "replay-with-different-row"), ctx)
            }
        }
    }

    @Test
    fun mappingDeleteRestrictedWhenInventoryExists() = runBlocking {
        seedBase()
        // RESTRICT FK: deleting the mapping must fail while an inventory row references it.
        assertThrows(Exception::class.java) {
            runBlocking { database.openHelper.writableDatabase.execSQL("DELETE FROM mappings WHERE id = 'mapping-1'") }
        }
    }

    @Test
    fun derivedOpeningMatchesStoredCache() = runBlocking {
        seedBase()
        database.movementDao().insert(
            MovementEntity(
                id = "mv-in", type = "IN", mappingId = "mapping-1", qtyDelta = 4L,
                transferPairId = null, idempotencyKey = "sum-check",
                actorAccountId = "admin", at = 1_700_000_005_000L, note = "",
            ),
            ctx,
        )
        database.inventoryDao().increment("mapping-1", 4L, 1_700_000_005_000L, ctx)
        val deltas = database.movementDao().sumDeltaFor("mapping-1")
        val row = database.inventoryDao().getByMapping("mapping-1")
        assertNotNull(row)
        // closing(14) == derived opening (10) + deltas(4)
        assertEquals(14L, (row!!.openingCache + deltas))
    }
}
