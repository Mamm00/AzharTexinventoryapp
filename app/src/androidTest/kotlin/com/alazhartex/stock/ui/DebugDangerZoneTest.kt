package com.alazhartex.stock.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alazhartex.stock.AlAzharApp
import com.alazhartex.stock.MainActivity
import com.alazhartex.stock.R
import com.alazhartex.stock.data.local.AppDatabase
import com.alazhartex.stock.data.local.InventoryEntity
import com.alazhartex.stock.data.local.MappingEntity
import com.alazhartex.stock.data.local.MovementEntity
import com.alazhartex.stock.data.local.RollEntity
import com.alazhartex.stock.di.AppContainer
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1.2.0 — deep instrumentation coverage of the settings "debug — full control" danger zone.
 * Every clickable element is exercised: the warehouses list (edit / delete cascades), the
 * add-warehouse form (validation, sanitizing, keeper chips), the wipe-all dialog (masters kept),
 * the reload-demo dialog, the safety-backup pruning, and the audit trail each action leaves.
 *
 * The suite resets the store to the pinned demo dataset in @BeforeClass and every destructive
 * test restores it afterwards, so tests are order-independent.
 */
@RunWith(AndroidJUnit4::class)
class DebugDangerZoneTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun res(id: Int): String = composeTestRule.activity.getString(id)

    private fun appName(): String = res(R.string.app_name)

    private fun app(): AlAzharApp =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AlAzharApp

    private fun container(): AppContainer = app().container

    private fun db(): AppDatabase = container().database

    private fun backupCount(prefix: String): Int =
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "backups")
            .listFiles { f -> f.name.startsWith("safety-$prefix-") && f.name.endsWith(".db") }
            ?.size ?: 0

    private fun auditCount(action: String, result: String? = null): Int = runBlocking {
        val rows = db().auditDao().byAction(action)
        if (result == null) rows.size else rows.count { it.result == result }
    }

    /** Poll the database until [predicate] holds; fails the test after [timeoutMs]. */
    private fun waitForDb(timeoutMs: Long = 30_000, description: String = "condition", predicate: suspend (AppContainer) -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                if (runBlocking { predicate(container()) }) return
            } catch (_: Throwable) {
                // DB may be mid-transaction; retry.
            }
            Thread.sleep(250)
        }
        fail("Timed out waiting for DB $description")
    }

    private fun openDrawer() {
        composeTestRule.onNodeWithContentDescription(appName(), useUnmergedTree = true).performClick()
    }

    private fun openSettings() {
        composeTestRule.waitUntilAtLeastOneExists(hasText(appName()), timeoutMillis = 90_000)
        openDrawer()
        composeTestRule.onNodeWithText(res(R.string.nav_settings)).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        // Make sure the danger-zone card exists on screen.
        composeTestRule.onNodeWithText(res(R.string.debug_zone_title)).performScrollTo().assertIsDisplayed()
    }

    private fun openAddWarehouseDialog() {
        openSettings()
        composeTestRule.onNodeWithText(res(R.string.action_debug_add_warehouse)).performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    private fun warehouseIdsInUiOrder(): List<String> = runBlocking { db().warehouseDao().getAll().map { it.id } }

    private fun unique(prefix: String): String =
        prefix + UUID.randomUUID().toString().replace("-", "").take(8)

    companion object {
        @BeforeClass @JvmStatic
        fun resetToDemoDataset() {
            val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AlAzharApp
            runBlocking {
                // Wipe any previous state and rebuild the pinned demo dataset from the seed.
                app.container.debugRepository.reloadDemoData("test-harness")
            }
        }
    }

    // ---------- render + navigation ----------

    @Test
    fun dangerZoneRendersEveryAction() {
        openSettings()
        composeTestRule.onNodeWithText(res(R.string.action_debug_add_warehouse)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(res(R.string.action_wipe_all)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(res(R.string.action_reload_demo)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(res(R.string.debug_zone_summary)).performScrollTo().assertIsDisplayed()
    }

    // ---------- add warehouse ----------

    @Test
    fun addWarehouseEndToEnd() {
        val suffix = unique("")
        val id = "dbg$suffix"
        val nameEn = "Dbg Depot $suffix"
        val nameAr = "مستودع $suffix"
        val keeper = runBlocking {
            db().accountDao().getAll().let { list -> list.firstOrNull { it.role == "KEEPER" } ?: list.first() }
        }

        openAddWarehouseDialog()
        composeTestRule.onNodeWithTag("debug_field_id").performTextClearance()
        composeTestRule.onNodeWithTag("debug_field_id").performTextInput(id)
        composeTestRule.onNodeWithTag("debug_field_name_ar").performTextInput(nameAr)
        composeTestRule.onNodeWithTag("debug_field_name_en").performTextInput(nameEn)
        composeTestRule.onNodeWithTag("debug_field_capacity").performTextClearance()
        composeTestRule.onNodeWithTag("debug_field_capacity").performTextInput("250")
        // Select the keeper chip explicitly.
        composeTestRule.onNodeWithText("${keeper.username} (${keeper.role})").performClick()
        composeTestRule.onNodeWithTag("debug_form_save").assertIsEnabled().performClick()

        waitForDb(description = "new warehouse persisted") { c ->
            c.database.warehouseDao().getById(id) != null
        }
        val warehouse = runBlocking { db().warehouseDao().getById(id) }!!
        assertEquals("250", warehouse.capacityRolls.toString())
        assertEquals(nameEn, warehouse.nameEn)
        assertEquals(nameAr, warehouse.nameAr)
        assertEquals(keeper.id, warehouse.keeperAccountId)
        // Keeper → warehouse assignment was created.
        val warehousesForKeeper = runBlocking { db().shedAssignmentDao().shedsFor(keeper.id) }
        assertTrue("keeper assignment for new warehouse missing", warehousesForKeeper.contains(id))
        // Audited.
        val allowed = runBlocking { db().auditDao().byAction("debug.warehouse.add").count { it.result == "ALLOWED" && it.target == "warehouse:$id" } }
        assertTrue("expected ALLOWED audit for debug.warehouse.add", allowed >= 1)
        // Cleanup via the same danger-zone repository path.
        runBlocking { container().debugRepository.deleteWarehouse("test-harness", id) }
        waitForDb(description = "cleanup delete") { c -> c.database.warehouseDao().getById(id) == null }
    }

    @Test
    fun addWarehouseRejectsInvalidIdCharacters() {
        openAddWarehouseDialog()
        composeTestRule.onNodeWithTag("debug_field_id").performTextClearance()
        composeTestRule.onNodeWithTag("debug_field_id").performTextInput("WH 01!!")
        // Sanitizer lowercases and strips everything except [a-z0-9_-].
        composeTestRule.onNodeWithTag("debug_field_id").assertTextEquals("wh01")
        composeTestRule.onNodeWithTag("debug_field_id").performTextClearance()
        composeTestRule.onNodeWithTag("debug_field_id").performTextInput("!!")
        composeTestRule.onNodeWithTag("debug_form_save").assertIsNotEnabled()
        composeTestRule.onNodeWithText(res(R.string.action_cancel)).performClick()
    }

    @Test
    fun addWarehouseRequiresNamesAndValidCapacity() {
        openAddWarehouseDialog()
        // Blank names: save must stay disabled.
        composeTestRule.onNodeWithTag("debug_field_name_ar").performTextClearance()
        composeTestRule.onNodeWithTag("debug_field_name_en").performTextClearance()
        composeTestRule.onNodeWithTag("debug_form_save").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("debug_field_name_ar").performTextInput("مستودع")
        composeTestRule.onNodeWithTag("debug_field_name_en").performTextInput("Depot")
        composeTestRule.onNodeWithTag("debug_form_save").assertIsEnabled()
        composeTestRule.onNodeWithText(res(R.string.action_cancel)).performClick()
    }

    @Test
    fun addDuplicateWarehouseIsBlocked() {
        val existingId = runBlocking { db().warehouseDao().getAll().first().id }
        val allowedBefore = auditCount("debug.warehouse.add", "BLOCKED")

        openAddWarehouseDialog()
        composeTestRule.onNodeWithTag("debug_field_id").performTextClearance()
        composeTestRule.onNodeWithTag("debug_field_id").performTextInput(existingId)
        composeTestRule.onNodeWithTag("debug_field_name_ar").performTextInput("مستودع مكرر")
        composeTestRule.onNodeWithTag("debug_field_name_en").performTextInput("Dup Depot")
        composeTestRule.onNodeWithTag("debug_form_save").assertIsEnabled().performClick()

        // The dialog stays open on failure and a BLOCKED audit row is written.
        waitForDb(description = "blocked audit row") { c ->
            c.database.auditDao().byAction("debug.warehouse.add").count { it.result == "BLOCKED" } > allowedBefore
        }
        composeTestRule.onNodeWithTag("debug_form_save").assertIsDisplayed()
        val stillOne = runBlocking { db().warehouseDao().getAll().count { it.id == existingId } }
        assertEquals(1, stillOne)
        composeTestRule.onNodeWithText(res(R.string.action_cancel)).performClick()
    }

    // ---------- edit warehouse ----------

    @Test
    fun editWarehouseUpdatesDetails() {
        // Create a throwaway warehouse through the same repository the UI uses.
        val suffix = unique("")
        val id = "dbg$suffix"
        val originalEn = "Dbg Before $suffix"
        val keeper = runBlocking { db().accountDao().getAll().let { l -> l.firstOrNull { it.role == "KEEPER" } ?: l.first() } }
        runBlocking { container().debugRepository.addWarehouse("test-harness", id, "مستودع قبل", originalEn, 100, keeper.id) }

        openSettings()
        composeTestRule.onNodeWithText(originalEn, substring = true).performScrollTo().assertIsDisplayed()
        val order = warehouseIdsInUiOrder()
        val index = order.indexOf(id)
        assertTrue("throwaway warehouse missing from UI order", index >= 0)
        composeTestRule.onAllNodesWithText(res(R.string.action_edit))[index].performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("debug_field_name_en").performTextClearance()
        composeTestRule.onNodeWithTag("debug_field_name_en").performTextInput("Dbg After $suffix")
        composeTestRule.onNodeWithTag("debug_field_capacity").performTextClearance()
        composeTestRule.onNodeWithTag("debug_field_capacity").performTextInput("777")
        composeTestRule.onNodeWithTag("debug_form_save").assertIsEnabled().performClick()

        waitForDb(description = "warehouse updated") { c ->
            c.database.warehouseDao().getById(id)?.nameEn == "Dbg After $suffix"
        }
        val updated = runBlocking { db().warehouseDao().getById(id) }!!
        assertEquals(777L, updated.capacityRolls)
        val allowed = runBlocking { db().auditDao().byAction("debug.warehouse.edit").count { it.result == "ALLOWED" && it.target == "warehouse:$id" } }
        assertTrue("expected ALLOWED audit for debug.warehouse.edit", allowed >= 1)
        runBlocking { container().debugRepository.deleteWarehouse("test-harness", id) }
    }

    @Test
    fun editWarehouseKeeperRepointsAssignmentOnlyOnChange() {
        val suffix = unique("")
        val id = "dbg$suffix"
        val keeper = runBlocking { db().accountDao().getAll().let { l -> l.firstOrNull { it.role == "KEEPER" } ?: l.first() } }
        val other = runBlocking { db().accountDao().getAll().firstOrNull { it.id != keeper.id } }!!
        runBlocking { container().debugRepository.addWarehouse("test-harness", id, "مستودع حارس", "Dbg Keeper $suffix", 100, keeper.id) }

        runBlocking { container().debugRepository.updateWarehouse("test-harness", id, "مستودع حارس", "Dbg Keeper $suffix", 100, keeper.id) }
        val unchanged = runBlocking { db().shedAssignmentDao().shedsFor(keeper.id) }
        assertEquals("keeper unchanged — assignment must not churn", listOf(id), unchanged.filter { it == id })

        runBlocking { container().debugRepository.updateWarehouse("test-harness", id, "مستودع حارس", "Dbg Keeper $suffix", 100, other.id) }
        val newKeeperSheds = runBlocking { db().shedAssignmentDao().shedsFor(other.id) }
        assertTrue("assignment not re-pointed to new keeper", newKeeperSheds.contains(id))
        val oldKeeperSheds = runBlocking { db().shedAssignmentDao().shedsFor(keeper.id) }
        assertTrue("assignment still points at old keeper", !oldKeeperSheds.contains(id))
        runBlocking { container().debugRepository.deleteWarehouse("test-harness", id) }
    }

    // ---------- delete warehouse with cascade preview ----------

    @Test
    fun deleteWarehouseCascadesWithAuditAndBackup() {
        val suffix = unique("")
        val id = "dbg$suffix"
        val nameEn = "Dbg Doomed $suffix"
        val keeper = runBlocking { db().accountDao().getAll().let { l -> l.firstOrNull { it.role == "KEEPER" } ?: l.first() } }
        runBlocking { container().debugRepository.addWarehouse("test-harness", id, "مستودع للحذف", nameEn, 100, keeper.id) }

        // Seed one full stock chain (mapping → inventory + roll + movement) so the cascade preview shows real counts.
        val delMapping = "map-$suffix"
        runBlocking {
            val fabric = db().fabricDao().getAll().first()
            val colour = db().colourDao().getAll().first()
            db().mappingDao().insert(MappingEntity(delMapping, id, fabric.id, colour.id, true))
            db().inventoryDao().insertAll(listOf(InventoryEntity("inv-$suffix", delMapping, 5, 5, null)))
            db().rollDao().insertAll(listOf(RollEntity("roll-$suffix", delMapping, "ACTIVE", 5)))
            db().movementDao().insert(
                MovementEntity(
                    id = "mov-$suffix",
                    type = "IN",
                    mappingId = delMapping,
                    qtyDelta = 5,
                    transferPairId = null,
                    idempotencyKey = "idk-$suffix",
                    actorAccountId = keeper.id,
                    at = System.currentTimeMillis(),
                    note = "debug-test",
                ),
            )
        }
        val countsBefore = runBlocking { container().debugRepository.warehouseCascadeCounts(id) }
        assertEquals(1L, countsBefore.mappings)
        assertEquals(1L, countsBefore.inventoryRows)
        assertEquals(1L, countsBefore.movements)
        assertEquals(1L, countsBefore.rolls)
        assertEquals(1L, countsBefore.assignments)

        val backupsBefore = backupCount("warehouse")
        openSettings()
        composeTestRule.onNodeWithText(nameEn, substring = true).performScrollTo().assertIsDisplayed()
        val order = warehouseIdsInUiOrder()
        val index = order.indexOf(id)
        assertTrue(index >= 0)
        composeTestRule.onAllNodesWithText(res(R.string.action_delete))[index].performClick()
        composeTestRule.waitForIdle()

        // Cascade preview shows the exact counts.
        val expectedBody = composeTestRule.activity.getString(R.string.dialog_delete_warehouse_body, 1, 1, 1, 1, 1)
        composeTestRule.onNodeWithText(expectedBody).assertIsDisplayed()
        composeTestRule.onNodeWithTag("debug_delete_confirm").performClick()

        waitForDb(description = "warehouse + cascade deleted") { c ->
            c.database.warehouseDao().getById(id) == null
        }
        runBlocking {
            assertEquals(0L, db().mappingDao().countForWarehouse(id))
            assertEquals(0L, db().inventoryDao().countForWarehouse(id))
            assertEquals(0L, db().movementDao().countForWarehouse(id))
            assertEquals(0L, db().rollDao().countForWarehouse(id))
            assertEquals(0L, db().shedAssignmentDao().countForWarehouse(id))
        }
        assertTrue("expected safety backup to be written", backupCount("warehouse") > backupsBefore)
        val auditTargetHit = runBlocking { db().auditDao().byAction("debug.warehouse.delete").any { it.result == "ALLOWED" && it.target.startsWith("warehouse:$id") && it.target.contains("map=1") } }
        assertTrue("expected ALLOWED audit row with cascade counts in target", auditTargetHit)
    }

    // ---------- wipe everything (keeps masters) ----------

    @Test
    fun wipeAllOperationalDataKeepsMastersAndAccounts() {
        openSettings()
        composeTestRule.onNodeWithText(res(R.string.action_wipe_all)).performScrollTo().performClick()
        composeTestRule.waitForIdle()

        val counts = runBlocking { container().debugRepository.operationalCounts() }
        val expectedBody = composeTestRule.activity.getString(
            R.string.dialog_wipe_all_body,
            counts.warehouses.toInt(),
            counts.assignments.toInt(),
            counts.mappings.toInt(),
            counts.inventoryRows.toInt(),
            counts.movements.toInt(),
            counts.rolls.toInt(),
            counts.fabrics.toInt(),
            counts.colours.toInt(),
        )
        composeTestRule.onNodeWithText(expectedBody).assertIsDisplayed()
        val backupsBefore = backupCount("wipe")
        composeTestRule.onNodeWithTag("debug_wipe_confirm").performClick()

        waitForDb(timeoutMs = 60_000, description = "operational data wiped") { c ->
            c.database.warehouseDao().countAll() == 0L &&
                c.database.mappingDao().countAll() == 0L &&
                c.database.inventoryDao().countAll() == 0L &&
                c.database.movementDao().countAll() == 0L &&
                c.database.rollDao().countAll() == 0L &&
                c.database.shedAssignmentDao().countAll() == 0L
        }
        runBlocking {
            // Masters, accounts and settings are kept.
            assertTrue("fabrics master must survive the wipe", db().fabricDao().getAll().isNotEmpty())
            assertTrue("colours master must survive the wipe", db().colourDao().getAll().isNotEmpty())
            assertTrue("accounts must survive the wipe", db().accountDao().getAll().isNotEmpty())
            assertEquals("true", db().appSettingsDao().get("seed_applied"))
        }
        assertTrue("expected safety backup to be written", backupCount("wipe") > backupsBefore)
        assertTrue("expected ALLOWED audit for wipe", auditCount("debug.wipe_operational", "ALLOWED") >= 1)

        // Restore the demo dataset for the other tests.
        runBlocking { container().debugRepository.reloadDemoData("test-harness") }
        waitForDb(timeoutMs = 120_000, description = "demo data reloaded") { c ->
            c.database.warehouseDao().countAll() > 0L && c.database.mappingDao().countAll() > 0L
        }
    }

    // ---------- reload demo data ----------

    @Test
    fun reloadDemoDataRestoresDemoDataset() {
        openSettings()
        composeTestRule.onNodeWithText(res(R.string.action_reload_demo)).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(res(R.string.dialog_reload_demo_title)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("debug_reload_confirm").performClick()

        waitForDb(timeoutMs = 120_000, description = "demo data reloaded via UI") { c ->
            c.database.warehouseDao().countAll() > 0L &&
                c.database.mappingDao().countAll() > 0L &&
                c.database.inventoryDao().countAll() > 0L &&
                c.database.movementDao().countAll() > 0L &&
                c.database.rollDao().countAll() > 0L
        }
        runBlocking {
            assertTrue(db().accountDao().getAll().isNotEmpty())
            assertTrue(db().fabricDao().getAll().isNotEmpty())
        }
        assertTrue("expected ALLOWED audit for demo reload", auditCount("debug.demo_reload", "ALLOWED") >= 1)
    }

    // ---------- safety backup pruning ----------

    @Test
    fun safetyBackupsArePrunedToThreePerPrefix() {
        runBlocking {
            repeat(4) { container().debugRepository.wipeOperationalData("test-harness") }
        }
        assertTrue("safety-wipe backups must be pruned to 3, found ${backupCount("wipe")}", backupCount("wipe") <= 3)
        // Restore the demo dataset for any later suite.
        runBlocking { container().debugRepository.reloadDemoData("test-harness") }
        waitForDb(timeoutMs = 120_000, description = "demo data reloaded after prune test") { c ->
            c.database.warehouseDao().countAll() > 0L
        }
    }
}
