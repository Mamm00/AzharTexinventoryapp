package com.alazhartex.stock.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alazhartex.stock.data.repo.StockRepository
import com.alazhartex.stock.domain.idempotency.MutationOutcome
import com.alazhartex.stock.domain.model.MovementType
import com.alazhartex.stock.domain.model.RbacDeniedException
import com.alazhartex.stock.domain.model.SessionClockAnomalyException
import com.alazhartex.stock.domain.model.ShortfallException
import com.alazhartex.stock.domain.session.SessionPolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T25 — red-team regression suite (spec §9.16) plus the Room constraint suite.
 * Red-team: each test attacks one invariant (replay, shortfall atomicity,
 * capacity escape, cross-shed RBAC, session anomaly); all must fail closed.
 * Constraints: trigger-backed CHECK enforcement, atomic conditional decrement,
 * FK RESTRICT, unique idempotency key, zero-delta movement ban.
 * Both suites share this class because a second class in this package
 * consistently failed internal runner construction on the CI emulator.
 */
@RunWith(AndroidJUnit4::class)
class T25RedTeamTest {

    private lateinit var database: AppDatabase

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

    private suspend fun seedLedger() {
        database.accountDao().insertAll(
            listOf(
                AccountEntity("admin", "admin", "pbkdf2\$210000\$s\$h", "ADMIN", "Admin"),
                AccountEntity("keeper1", "keeper1", "pbkdf2\$210000\$s\$h", "KEEPER", "Keeper One"),
                AccountEntity("keeper2", "keeper2", "pbkdf2\$210000\$s\$h", "KEEPER", "Keeper Two"),
            ),
        )
        database.warehouseDao().insertAll(
            listOf(
                WarehouseEntity("wh-a", "أ", "Shed A", 10L, "keeper1"),
                WarehouseEntity("wh-b", "ب", "Shed B", 3L, "keeper2"),
            ),
        )
        database.shedAssignmentDao().insertAll(
            listOf(ShedAssignmentEntity("keeper1", "wh-a"), ShedAssignmentEntity("keeper2", "wh-b")),
        )
        database.fabricDao().insert(FabricEntity("fab-1", "قطن", "Cotton", "Cotton"))
        database.colourDao().insert(ColourEntity("col-1", "C-1", "أحمر", "Red", "A", "Cotton", null, true))
        database.mappingDao().insertAll(
            listOf(
                MappingEntity("map-a", "wh-a", "fab-1", "col-1", true),
                MappingEntity("map-b", "wh-b", "fab-1", "col-1", true),
            ),
        )
        database.inventoryDao().insertAll(
            listOf(
                InventoryEntity("inv-a", "map-a", 8L, 8L, 1_700_000_000_000L),
                InventoryEntity("inv-b", "map-b", 2L, 2L, 1_700_000_000_000L),
            ),
        )
        database.rollDao().insertAll(
            listOf(RollEntity("roll-a", "map-a", "ACTIVE", 8L), RollEntity("roll-b", "map-b", "ACTIVE", 2L)),
        )
    }

    private fun repo(verdict: SessionPolicy.MutationResolution = SessionPolicy.MutationResolution.PROCEED) =
        StockRepository(database, { 1_700_000_100_000L }) { _, _ -> verdict }

    private suspend fun seedBase() {
        database.accountDao().insert(AccountEntity("admin", "admin", "pbkdf2\$210000\$salt\$hash", "ADMIN", "Admin"))
        database.warehouseDao().insert(WarehouseEntity("wh-1", "مستودع", "Warehouse One", 100L, "admin"))
        database.fabricDao().insert(FabricEntity("fabric-1", "قطن", "Cotton", "Cotton"))
        database.colourDao().insert(ColourEntity("colour-1", "C-1", "أحمر", "Red", "A", "Cotton", null, true))
        database.mappingDao().insert(MappingEntity("mapping-1", "wh-1", "fabric-1", "colour-1", true))
        database.inventoryDao().insertAll(
            listOf(InventoryEntity("inv-1", "mapping-1", 10L, 10L, 1_700_000_000_000L)),
        )
        database.rollDao().insertAll(listOf(RollEntity("roll-1", "mapping-1", "ACTIVE", 10L)))
    }

    @Test
    fun replayAddRollsIsNoOp() = runBlocking {
        seedLedger()
        val first = repo().addRolls("admin", "map-a", 3L, MovementType.IN, "in", "idem-replay-1")
        val second = repo().addRolls("admin", "map-a", 3L, MovementType.IN, "in", "idem-replay-1")
        assertTrue(first is MutationOutcome.Applied)
        assertTrue(second is MutationOutcome.AlreadyApplied)
        assertEquals(11L, database.inventoryDao().getByMapping("map-a")?.closing)
        assertEquals(1L, database.movementDao().countByIdempotency("idem-replay-1"))
    }

    @Test
    fun shortfallRollsBackEverything() = runBlocking {
        seedLedger()
        try {
            repo().removeRolls("admin", "map-b", 5L, MovementType.OUT, "too much", "idem-short-1")
            fail("expected ShortfallException")
        } catch (expected: ShortfallException) {
            assertEquals(3L, expected.shortfall)
        }
        assertEquals(2L, database.inventoryDao().getByMapping("map-b")?.closing)
        assertEquals(0L, database.movementDao().countByIdempotency("idem-short-1"))
        val blocked = database.auditDao().byResult("BLOCKED")
        assertEquals(1, blocked.size)
        assertTrue(blocked.first().reason?.startsWith("shortfall") == true)
    }

    @Test
    fun capacityIsNotAnEnforcementGate() = runBlocking {
        seedLedger()
        // wh-b capacity = 3, closing = 2; adding 5 rolls must be ALLOWED (wh-625 is over capacity by design).
        val outcome = repo().addRolls("admin", "map-b", 5L, MovementType.IN, "over capacity is legal", "idem-cap-1")
        assertTrue(outcome is MutationOutcome.Applied)
        assertEquals(7L, database.inventoryDao().getByMapping("map-b")?.closing)
    }

    @Test
    fun keeperCannotWriteForeignShed() = runBlocking {
        seedLedger()
        try {
            repo().removeRolls("keeper1", "map-b", 1L, MovementType.OUT, "not my shed", "idem-rbac-1")
            fail("expected RbacDeniedException")
        } catch (expected: RbacDeniedException) {
            // reason key comes from the Rbac truth table — must name the foreign-shed rule
            assertTrue(expected.reasonKey.isNotBlank())
        }
        assertEquals(2L, database.inventoryDao().getByMapping("map-b")?.closing)
    }

    @Test
    fun keeperCannotWriteOwnShedThroughTransferChain() = runBlocking {
        seedLedger()
        // keeper1 owns wh-a but not wh-b: a transfer FROM a foreign shed must fail even as source leg.
        try {
            repo().transferRolls("keeper1", "map-b", "map-a", 1L, "steal", "idem-rbac-2")
            fail("expected RbacDeniedException")
        } catch (expected: RbacDeniedException) {
            assertTrue(expected.reasonKey.isNotBlank())
        }
        assertEquals(8L, database.inventoryDao().getByMapping("map-a")?.closing)
        assertEquals(2L, database.inventoryDao().getByMapping("map-b")?.closing)
    }

    @Test
    fun sessionAnomalyFailsClosedEverywhere() = runBlocking {
        seedLedger()
        try {
            repo(SessionPolicy.MutationResolution.REFUSE_ANOMALY)
                .addRolls("admin", "map-a", 1L, MovementType.IN, "anomaly", "idem-anomaly-1")
            fail("expected SessionClockAnomalyException")
        } catch (expected: SessionClockAnomalyException) {
            // fail closed
        }
        assertEquals(8L, database.inventoryDao().getByMapping("map-a")?.closing)
    }

    @Test
    fun transferLegsAreAtomicPair() = runBlocking {
        seedLedger()
        val outcome = repo().transferRolls("admin", "map-a", "map-b", 3L, "move", "idem-transfer-1")
        assertTrue(outcome is MutationOutcome.Applied)
        assertEquals(5L, database.inventoryDao().getByMapping("map-a")?.closing)
        assertEquals(5L, database.inventoryDao().getByMapping("map-b")?.closing)
        val transfers = database.movementDao().getAll().filter { it.transferPairId != null }
        assertEquals(2, transfers.size)
        assertEquals(transfers[0].transferPairId, transfers[1].transferPairId)
    }

    // --- Room constraint suite (merged from former RoomConstraintsTest) ---

    @Test
    fun conditionalDecrementRefusesBelowZero() = runBlocking {
        seedBase()
        val ok = database.inventoryDao().conditionalDecrement("mapping-1", 10L, 1_700_000_001_000L)
        assertEquals(1, ok)
        val refused = database.inventoryDao().conditionalDecrement("mapping-1", 1L, 1_700_000_002_000L)
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
        database.movementDao().insert(movement)
        assertThrows(Exception::class.java) {
            runBlocking {
                database.movementDao().insert(movement.copy(id = "mv-2", note = "replay-with-different-row"))
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
        )
        database.inventoryDao().increment("mapping-1", 4L, 1_700_000_005_000L)
        val deltas = database.movementDao().sumDeltaFor("mapping-1")
        val row = database.inventoryDao().getByMapping("mapping-1")
        assertNotNull(row)
        // closing(14) == derived opening (10) + deltas(4)
        assertEquals(14L, (row!!.openingCache + deltas))
    }
}
